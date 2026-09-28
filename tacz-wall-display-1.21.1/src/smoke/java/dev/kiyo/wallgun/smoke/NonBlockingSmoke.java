package dev.kiyo.wallgun.smoke;

import dev.kiyo.wallgun.*;
import dev.kiyo.wallgun.client.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.common.NeoForge;
import java.nio.file.*;
import java.util.concurrent.CompletableFuture;

/** Private stress save; tests world access, dimension reuse, invalidation and deliberate on-demand stutters. */
final class NonBlockingSmoke {
    private int phase,ticks,bakes,uploads,hits,epoch;
    private boolean opened,positioned,finished,playBeforeReady;
    private volatile Throwable failure;
    private final long deadline=System.nanoTime()+300_000_000_000L;
    private CompletableFuture<Void> reload;
    private final StringBuilder results=new StringBuilder();
    private Path out(){return Minecraft.getInstance().gameDirectory.toPath().resolve("nonblocking-verification");}
    NonBlockingSmoke(){NeoForge.EVENT_BUS.addListener(this::tick);}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static boolean ready(){return WallWarmup.tracked()==100 && WallWarmup.pendingModels()==0 && WallBatches.pendingBatches()==0;}
    private void teleport(String dimension,double y,boolean setup,boolean edit) {
        var mc=Minecraft.getInstance();
        mc.getSingleplayerServer().execute(()->{
            try {
                var server=mc.getSingleplayerServer();var source=server.overworld();var target=server.getLevel(Level.NETHER);
                if(setup) {
                    for(int x=-16;x<=16;x++)for(int yy=132;yy<=154;yy++)target.setBlock(new BlockPos(x,yy,0),Blocks.SMOOTH_STONE.defaultBlockState(),3);
                    for(int row=0;row<10;row++)for(int col=0;col<10;col++) {
                        var old=(WallGunEntity)source.getBlockEntity(new BlockPos(-14+3*col,-39-2*row,1));
                        var pos=old.getBlockPos().offset(0,190,0);target.setBlock(pos,old.getBlockState(),3);
                        var gun=(WallGunEntity)target.getBlockEntity(pos);gun.setSnapshot(old.snapshot());gun.setMountRoll(old.mountRoll());gun.setPose(old.roll(),old.flipped());
                    }
                }
                if(edit)((WallGunEntity)source.getBlockEntity(new BlockPos(-14,-39,1))).adjust(1,false);
                var player=server.getPlayerList().getPlayers().getFirst();
                player.getAbilities().flying=true;player.onUpdateAbilities();
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"execute in "+dimension+" run tp "+player.getGameProfile().getName()+" 0 "+y+" 35 0 0");
            }catch(Throwable ex){failure=ex;}
        });
    }
    private void tick(ClientTickEvent.Post event) {
        if(finished)return;var mc=Minecraft.getInstance();
        try {
            if(failure!=null)throw new AssertionError(failure);
            require(System.nanoTime()<deadline,"Timed out in phase "+phase+" "+WallBatches.stats());
            if(!opened && mc.screen instanceof TitleScreen && mc.getOverlay()==null) {
                opened=true;Files.createDirectories(out());if(phase==0){Files.deleteIfExists(out().resolve("FAILED.txt"));Files.deleteIfExists(out().resolve("SUCCESS.txt"));}
                WallGunConfig.PRELOAD_MODE.set(WallGunConfig.PreloadMode.BACKGROUND);
                mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(6);mc.options.framerateLimit().set(60);
                mc.createWorldOpenFlows().openWorld("warmup-stress",()->mc.setScreen(new TitleScreen()));return;
            }
            if(mc.screen instanceof BackupConfirmScreen) {
                for(var child:mc.screen.children())if(child instanceof net.minecraft.client.gui.components.Button button && button.getMessage().getString().equals(Component.translatable("selectWorld.backupJoinSkipButton").getString())){button.onPress();return;}
            }
            if(mc.level==null || mc.player==null)return;
            mc.player.getAbilities().flying=true;mc.player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            if(!positioned){positioned=true;teleport("minecraft:overworld",-50.2,false,false);}
            if(phase==0) {
                if(mc.screen==null && !ready())playBeforeReady=true;
                if(mc.screen!=null || mc.getOverlay()!=null || !ready())return;
                require(GunMeshes.failures==0,"No failed models");
                results.append("PASS normal world entry; playable before gun preparation complete=").append(playBeforeReady).append('\n');
                bakes=GunMeshes.bakes;uploads=WallBatches.uploads;phase=1;ticks=0;mc.player.setYRot(180);mc.player.yRotO=180;
            } else if(phase==1) {
                require(GunMeshes.bakes==bakes,"First turn recaptured guns");
                if(++ticks<30)return;require(WallBatches.lastGuns==100,"100 guns visible");
                results.append("PASS background preparation behind camera; first turn zero model captures\n");
                phase=2;ticks=0;teleport("minecraft:the_nether",139.8,true,false);
            } else if(phase==2) {
                if(!mc.level.dimension().equals(Level.NETHER) || mc.screen!=null || !ready())return;
                require(GunMeshes.bakes==bakes,"Dimension travel must reuse model meshes");
                results.append("PASS nether: reused all 100 gun models without extra captures\n");
                uploads=WallBatches.uploads;hits=WallBatches.dimensionCacheHits;phase=3;ticks=0;
            } else if(phase==3) {
                if(++ticks<30)return;phase=4;teleport("minecraft:overworld",-50.2,false,true);
            } else if(phase==4) {
                if(!mc.level.dimension().equals(Level.OVERWORLD) || mc.screen!=null || !ready())return;
                require(GunMeshes.bakes==bakes,"Return must reuse model meshes");
                require(WallBatches.dimensionCacheHits>hits,"Return must reuse verified GPU batches");
                int delta=WallBatches.uploads-uploads;require(delta>0 && delta<16,"Changed gun must invalidate only its materials: "+delta);
                results.append("PASS return: ").append(WallBatches.dimensionCacheHits-hits).append(" verified GPU hits, ").append(delta).append(" uploads for modified pose\n");
                phase=5;reload=mc.reloadResourcePacks();
            } else if(phase==5) {
                if(!reload.isDone() || mc.getOverlay()!=null || !ready() || GunMeshes.bakes<bakes+100)return;
                results.append("PASS resource reload invalidated appearance/GPU caches without blocking entry\n");
                WallGunConfig.PRELOAD_MODE.set(WallGunConfig.PreloadMode.OFF);mc.player.setYRot(0);mc.player.yRotO=0;
                bakes=GunMeshes.bakes;phase=6;ticks=0;reload=mc.reloadResourcePacks();
            } else if(phase==6) {
                if(!reload.isDone() || mc.getOverlay()!=null)return;
                require(GunMeshes.bakes==bakes,"OFF must not capture guns behind camera");
                if(++ticks<40)return;
                require(WallWarmup.tracked()==0,"OFF must not scan unseen guns");
                results.append("PASS OFF: no scan or captures behind camera after cold resource reload\n");
                mc.player.setYRot(180);mc.player.yRotO=180;phase=7;ticks=0;
            } else if(phase==7) {
                if(!ready() || WallBatches.lastGuns!=100)return;
                require(GunMeshes.bakes>=bakes+100,"OFF must capture on first visible use");
                results.append("PASS OFF: first visible view captured and displayed 100 guns on demand\n");
                try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(out().resolve("ready.png"));}
                WallGunConfig.PRELOAD_MODE.set(WallGunConfig.PreloadMode.BACKGROUND);
                bakes=GunMeshes.bakes;epoch=GunMeshes.invalidations;phase=8;opened=false;positioned=false;
                mc.level.disconnect();mc.disconnect(new TitleScreen());
            } else if(phase==8) {
                if(mc.screen!=null || !ready())return;
                if(GunMeshes.invalidations==epoch) {
                    require(GunMeshes.bakes==bakes,"Re-enter same resource session must reuse gun models");
                    results.append("PASS re-enter save: zero extra model captures in unchanged resource session\n");
                } else {
                    require(GunMeshes.bakes>=bakes+100,"Changed resource generation must recapture");
                    results.append("PASS re-enter save: resource reload occurred; stale cache invalidated\n");
                }
                Files.writeString(out().resolve("SUCCESS.txt"),results+WallBatches.stats());finished=true;mc.stop();
            }
        }catch(Throwable ex){finished=true;ex.printStackTrace();try{Files.createDirectories(out());Files.writeString(out().resolve("FAILED.txt"),"phase="+phase+" "+ex+"\n"+results+WallBatches.stats());}catch(Exception ignored){}mc.stop();}
    }
}
