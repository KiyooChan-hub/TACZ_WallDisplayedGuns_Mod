package dev.kiyo.wallgun.smoke;

import dev.kiyo.wallgun.*;
import dev.kiyo.wallgun.client.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.core.BlockPos;
import net.minecraftforge.event.TickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.client.event.*;
import net.minecraftforge.common.MinecraftForge;
import java.nio.file.*;

/** Uses a private copy of the existing 100 distinct, fully equipped gun save. */
final class WarmupSmoke {
    private String lastState="";
    private int phase, frame, cycle, bakes, uploads, gateCount;
    private boolean opened, positioned, loadingShot, finished;
    private long lastFrame, deadline=System.nanoTime()+300_000_000_000L;
    private volatile Throwable failure;
    private final StringBuilder frames=new StringBuilder("cycle,phase,frame,ms,screen,bakes,uploads,frameBakes,frameUploads,visible,pendingModels,pendingBatches\n");
    private final StringBuilder results=new StringBuilder();
    private Path out() { return Minecraft.getInstance().gameDirectory.toPath().resolve("warmup-verification"); }
    WarmupSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::tick);
        MinecraftForge.EVENT_BUS.addListener(this::frame);
    }
    private static void require(boolean test,String message) { if(!test)throw new AssertionError(message); }
    private void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        if(finished)return;
        var mc=Minecraft.getInstance();
        try {
            String state=(mc.screen==null?"play":mc.screen.getClass().getSimpleName())+" phase="+phase;
            if(!state.equals(lastState)){System.out.println("WARMUP_SMOKE "+state);lastState=state;}
            if(failure!=null)throw new AssertionError(failure);
            require(System.nanoTime()<deadline,"Warmup smoke timed out, screen="+mc.screen+", phase="+phase+" "+WallBatches.stats());
            if(!opened && mc.screen instanceof TitleScreen && mc.getOverlay()==null) {
                opened=true;positioned=false;Files.createDirectories(out());
                require(Files.isRegularFile(mc.gameDirectory.toPath().resolve("saves/warmup-stress/level.dat")),
                        "Missing warmup-stress test world in the isolated run-client directory");
                mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(6);mc.options.framerateLimit().set(60);
                com.tacz.guns.config.client.RenderConfig.GUN_LOD_RENDER_DISTANCE.set(999);
                mc.createWorldOpenFlows().loadLevel(mc.screen,"warmup-stress");return;
            }
            if(mc.screen instanceof BackupConfirmScreen) {
                for(var child:mc.screen.children())if(child instanceof net.minecraft.client.gui.components.Button button
                        && button.getMessage().getString().equals(Component.translatable("selectWorld.backupJoinSkipButton").getString())) {button.onPress();return;}
            }
            if(mc.level==null || mc.player==null)return;
            mc.player.getAbilities().flying=true;mc.player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            if(!positioned) {
                positioned=true;
                mc.getSingleplayerServer().execute(()->{
                    try { var player=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
                        player.getAbilities().flying=true;player.onUpdateAbilities();player.connection.teleport(0,-50.2,35,0,0);
                    }catch(Throwable ex){failure=ex;}
                });
            }
            if(phase==0) {
                mc.player.setYRot(0);mc.player.setXRot(0);
                if(WallWarmup.completed<=gateCount || mc.screen!=null || mc.getOverlay()!=null)return;
                require(WallWarmup.tracked()==100,"Load gate must discover all 100 guns behind player: "+WallWarmup.tracked());
                require(WallWarmup.pendingModels()==0 && WallBatches.pendingBatches()==0,"Load gate released with work remaining");
                require(GunMeshes.failures==0 && WallWarmup.timedOut==0,"No failed model or gate timeout");
                results.append("cycle ").append(cycle).append(" gateMs=").append(WallWarmup.lastGateMillis).append(' ').append(WallBatches.stats()).append('\n');
                bakes=GunMeshes.bakes;uploads=WallBatches.uploads;phase=1;frame=0;
            }
        }catch(Throwable ex){fail(ex);}
    }
    private void frame(net.minecraftforge.event.TickEvent.RenderTickEvent event) {
        if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        if(finished)return;
        var mc=Minecraft.getInstance();long now=System.nanoTime();
        try {
            if(mc.level!=null && lastFrame!=0)frames.append(cycle).append(',').append(phase).append(',').append(frame).append(',').append((now-lastFrame)/1_000_000.0)
                    .append(',').append(mc.screen==null?"play":mc.screen.getClass().getSimpleName()).append(',').append(GunMeshes.bakes).append(',').append(WallBatches.uploads)
                    .append(',').append(WallWarmup.frameBakes).append(',').append(WallWarmup.frameUploads).append(',').append(WallBatches.lastGuns)
                    .append(',').append(WallWarmup.pendingModels()).append(',').append(WallBatches.pendingBatches()).append('\n');
            lastFrame=now;
            if(mc.gameMode==null)require(!WallWarmup.loading(),"Disconnect must not reopen a loading gate");
            if(mc.level==null || mc.player==null)return;
            if(!loadingShot && mc.screen instanceof WarmupScreen && WallWarmup.tracked()==100) {
                screenshot("loading.png");loadingShot=true;
            }
            if(phase==1) {
                require(GunMeshes.bakes==bakes && WallBatches.uploads==uploads,"First turn performed deferred work: "+WallBatches.stats());
                frame++;
                if(frame==30){mc.player.setYRot(180);mc.player.yRotO=180;}
                if(frame>35)require(WallBatches.lastGuns==100,"All 100 equipped guns must be visible after first turn: "+WallBatches.stats());
                if(frame<210)return;
                if(cycle==0)screenshot("ready-0.png");
                results.append("PASS first turn cycle ").append(cycle).append(": no warm-frame exclusion; 210 frames; zero captures/uploads; 100 visible\n");
                gateCount=WallWarmup.completed;
                if(cycle==0) {
                    cycle++;phase=0;opened=false;mc.level.disconnect();mc.clearLevel(new TitleScreen());
                } else if(cycle==1) {
                    cycle++;phase=0;mc.player.setYRot(0);mc.reloadResourcePacks();
                } else {
                    phase=2;frame=0;
                    mc.getSingleplayerServer().execute(()->{
                        try {
                            var level=mc.getSingleplayerServer().overworld();
                            for(int x=-16;x<=16;x++)for(int y=-60;y<=-37;y++)level.setBlock(new BlockPos(x+256,y,0),Blocks.SMOOTH_STONE.defaultBlockState(),3);
                            for(int row=0;row<10;row++)for(int col=0;col<10;col++) {
                                var src=new BlockPos(-14+3*col,-39-2*row,1);var dst=src.offset(256,0,0);
                                var old=(WallGunEntity)level.getBlockEntity(src);var stack=old.snapshot().copyGun();
                                stack.setHoverName(Component.literal("New region "+row+"-"+col));
                                level.setBlock(dst,old.getBlockState(),3);((WallGunEntity)level.getBlockEntity(dst)).setSnapshot(new GunSnapshot(stack));
                            }
                            mc.getSingleplayerServer().getPlayerList().getPlayers().get(0).connection.teleport(256,-50.2,35,0,0);
                        }catch(Throwable ex){failure=ex;}
                    });
                }
            } else if(phase==2) {
                require(WallWarmup.frameBakes<=1 && WallWarmup.frameUploads<=2,"New area exceeded per-frame job cap");
                if(mc.player.getX()<200)return;
                mc.player.setYRot(0);mc.player.setXRot(0);frame++;
                if(WallWarmup.tracked()==100 && GunMeshes.bakes>=bakes+100 && WallWarmup.pendingModels()==0 && WallBatches.pendingBatches()==0) {
                    results.append("PASS new region behind camera: 100 new snapshots; max 1 capture and 2 uploads per frame; ready after ").append(frame).append(" frames\n");
                    bakes=GunMeshes.bakes;uploads=WallBatches.uploads;phase=3;frame=0;mc.player.setYRot(180);mc.player.yRotO=180;
                }
            } else if(phase==3) {
                require(GunMeshes.bakes==bakes && WallBatches.uploads==uploads,"New-region first turn rebuilt cached guns");
                if(++frame>5)require(WallBatches.lastGuns==100,"New-region 100 visible");
                if(frame<180)return;
                Files.writeString(out().resolve("frames.csv"),frames);
                finished=true;
                Files.writeString(out().resolve("SUCCESS.txt"),results+"PASS new-region first turn: zero captures/uploads; 100 visible\n"+WallBatches.stats());mc.stop();
            }
        }catch(Throwable ex){fail(ex);}
    }
    private void screenshot(String name)throws Exception {try(var image=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())){image.writeToFile(out().resolve(name));}}
    private void fail(Throwable ex) {finished=true;ex.printStackTrace();try{Files.createDirectories(out());Files.writeString(out().resolve("FAILED.txt"),ex.toString());Files.writeString(out().resolve("frames.csv"),frames);}catch(Exception ignored){}Minecraft.getInstance().stop();}
}
