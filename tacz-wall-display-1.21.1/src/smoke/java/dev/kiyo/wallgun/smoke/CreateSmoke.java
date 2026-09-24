package dev.kiyo.wallgun.smoke;

import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import com.simibubi.create.content.contraptions.bearing.BearingBlock;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import dev.kiyo.wallgun.*;
import dev.kiyo.wallgun.client.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.*;
import java.nio.file.*;

/** Real Create assembly, virtual BER rendering, rotation, and block-entity restoration. */
final class CreateSmoke {
    private boolean opened, positioned, placed, assembled, disassembling;
    private final int width=Integer.getInteger("wallgun.createWidth",5), height=Integer.getInteger("wallgun.createHeight",8);
    private int ticks, initialBakes, initialUploads, stableUploads;
    private volatile Throwable failure;
    private volatile MechanicalBearingBlockEntity bearing;
    private volatile int captured; private volatile net.minecraft.world.item.ItemStack originalGun;
    private int clientJoins, clientLeaves, joinAt, leaveAt; private String leaveReason="";
    private final long deadline=System.nanoTime()+300_000_000_000L;
    CreateSmoke(){NeoForge.EVENT_BUS.addListener(this::tick); NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.EntityJoinLevelEvent e)->{if(e.getLevel().isClientSide() && e.getEntity() instanceof com.simibubi.create.content.contraptions.AbstractContraptionEntity){clientJoins++;joinAt=ticks;}}); NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent e)->{if(e.getLevel().isClientSide() && e.getEntity() instanceof com.simibubi.create.content.contraptions.AbstractContraptionEntity){clientLeaves++;leaveAt=ticks;leaveReason=String.valueOf(e.getEntity().getRemovalReason())+" id="+e.getEntity().getId(); try{var trace=new java.io.StringWriter();new Throwable("client contraption leave").printStackTrace(new java.io.PrintWriter(trace));Files.writeString(out().resolve("leave-stack.txt"),trace.toString());}catch(Exception ignored){}}});}
    private Path out(){return Minecraft.getInstance().gameDirectory.toPath().resolve("create-verification");}
    private static void require(boolean condition,String reason){if(!condition)throw new AssertionError(reason);}
    private void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();
        try {
            require(System.nanoTime()<deadline,"Create test timed out");
            if(failure!=null)throw new AssertionError(failure);
            if(!opened&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null){
                opened=true;Files.createDirectories(out());mc.options.pauseOnLostFocus=false;
                mc.options.hideGui=true;mc.options.renderDistance().set(6);mc.options.framerateLimit().set(60);
                var settings=new LevelSettings("Moving decorative guns",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel("create-smoke-"+System.currentTimeMillis(),settings,new WorldOptions(42,false,false),r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),mc.screen);
            }
            if(mc.level==null||mc.player==null||mc.getOverlay()!=null)return;
            if(mc.screen instanceof PauseScreen)mc.setScreen(null);
            if(++ticks<100)return;
            if(!positioned){
                positioned=true;ticks=0;
                mc.getSingleplayerServer().execute(()->{var player=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();player.getAbilities().flying=true;player.onUpdateAbilities();player.connection.teleport(width/2.0,-54,20,180,0);});
                return;
            }
            if(!placed){
                placed=true;ticks=0;mc.setScreen(null);
                initialBakes=GunMeshes.bakes;initialUploads=CreateMovingGuns.uploads;
                mc.getSingleplayerServer().execute(()->{try{
                    var server=mc.getSingleplayerServer();var level=server.overworld();
                    var base=new BlockPos(0,-60,0);
                    level.setBlock(base,net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.parse("create:mechanical_bearing")).defaultBlockState().setValue(BearingBlock.FACING,Direction.UP),3);
                    bearing=(MechanicalBearingBlockEntity)level.getBlockEntity(base);
                    var gun=ConversionChecks.equipped(level.registryAccess()); originalGun=gun.copy();
                    for(int y=0;y<height;y++)for(int x=0;x<width;x++){
                        var support=new BlockPos(x,-59+y,0);var display=support.south();
                        level.setBlock(support,Blocks.SMOOTH_STONE.defaultBlockState(),3);
                        level.setBlock(display,WallGuns.BLOCK.get().defaultBlockState().setValue(WallGunBlock.FACING,Direction.SOUTH),3);
                        ((WallGunEntity)level.getBlockEntity(display)).setSnapshot(new GunSnapshot(gun));
                    }
                    level.addFreshEntity(new SuperGlueEntity(level,new AABB(0,-59,0,width,-59+height,2)));
                    level.setDayTime(6000);
                }catch(Throwable ex){failure=ex;}});
            } else if(!assembled) {
                if(ticks<100)return;
                assembled=true;ticks=0;
                mc.getSingleplayerServer().execute(()->{try{
                    var server=mc.getSingleplayerServer();var level=server.overworld();
                    var base=new BlockPos(0,-60,0);
                    level.setBlock(base.below(),net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.parse("create:creative_motor")).defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING,Direction.UP),3);
                    var speed = Class.forName("com.simibubi.create.content.kinetics.base.KineticBlockEntity").getDeclaredField("speed");
                    speed.setAccessible(true); speed.setFloat(bearing, 16); bearing.assemble();
                    require(bearing.isRunning(),"Bearing failed to assemble: "+bearing.getLastAssemblyException());
                    var c=bearing.getMovedContraption().getContraption();
                    try(var nbtOut=new java.io.DataOutputStream(Files.newOutputStream(out().resolve("contraption-"+(width*height)+".nbt")))) { net.minecraft.nbt.NbtIo.write(c.writeNBT(level.registryAccess(),true),nbtOut); }
                    captured=(int)c.getBlocks().values().stream().filter(info->info.state().is(WallGuns.BLOCK.get())).count();
                    require(captured==width*height,"Glue captured "+captured+" / "+(width*height)+" guns");
                    require(c.getBlocks().values().stream().filter(info->info.state().is(WallGuns.BLOCK.get()))
                            .allMatch(info->info.nbt()!=null&&info.nbt().contains("OriginalGun")),"Original gun data absent in Create");
                    var player=server.getPlayerList().getPlayers().getFirst();player.getAbilities().flying=true;player.onUpdateAbilities();
                }catch(Throwable ex){failure=ex;}});
            } else if(!disassembling) {
                if(ticks==80 || ticks==150 || ticks==200) try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(out().resolve("rotating-"+ticks+".png"));}
                if(ticks<280)return;
                var moving=bearing.getMovedContraption();
                require(moving!=null&&moving.getContraption()!=null,"Moving entity absent");
                var view=moving.getContraption().getOrCreateClientContraptionLazy();
                int virtualGuns=(int)view.renderedBlockEntityView.stream().filter(be->be instanceof WallGunEntity).count();
                int renderBits=view.getAndAdjustShouldRenderBlockEntities().cardinality();
                int virtualSnapshots=(int)view.renderedBlockEntityView.stream().filter(be->be instanceof WallGunEntity gun && gun.snapshot()!=null).count();
                long clientData=moving.getContraption().getBlocks().values().stream().filter(info->info.state().is(WallGuns.BLOCK.get())&&info.nbt()!=null&&info.nbt().contains("OriginalGun")).count();
                int discovered=CreateMovingGuns.discover(mc.level).size();
                int clientEntities=0, clientSnapshots=0;
                for (var entity : mc.level.entitiesForRendering()) if (entity instanceof com.simibubi.create.content.contraptions.AbstractContraptionEntity ce) {
                    clientEntities++;
                    var cc=ce.getContraption();
                    if(cc!=null) for(var be:cc.getOrCreateClientContraptionLazy().renderedBlockEntityView)
                        if(be instanceof WallGunEntity gun && gun.snapshot()!=null && net.minecraft.world.item.ItemStack.isSameItemSameComponents(gun.snapshot().copyGun(),originalGun)) clientSnapshots++;
                }
                try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(out().resolve("precheck.png"));}
                require(clientSnapshots==width*height,"Client restored "+clientSnapshots+" / "+(width*height)+" exact original guns");
                require(CreateMovingGuns.draws>0,"Moving gun GPU draw never executed: "+CreateMovingGuns.stats()
                        +" virtualGuns="+virtualGuns+" renderBits="+renderBits+" discovered="+discovered
                        +" virtualSnapshots="+virtualSnapshots+" clientJoins="+clientJoins+" joinAt="+joinAt+" clientLeaves="+clientLeaves+" leaveAt="+leaveAt+" leaveReason="+leaveReason+" clientEntities="+clientEntities+" clientSnapshots="+clientSnapshots+" clientEntityById="+mc.level.getEntity(moving.getId())+" clientAllEntities="+java.util.stream.StreamSupport.stream(mc.level.entitiesForRendering().spliterator(),false).count()+" clientEntityTypes="+java.util.stream.StreamSupport.stream(mc.level.entitiesForRendering().spliterator(),false).map(e->e.getType().toString()).distinct().toList()+" clientData="+clientData
                        +" movingBlocks="+moving.getContraption().getBlocks().size()+" bearingRunning="+bearing.isRunning()+" movingAlive="+moving.isAlive()+" movingRemoved="+moving.isRemoved()+" movingId="+moving.getId()+" movingTick="+moving.tickCount+" movingPos="+moving.position()+" playerPos="+mc.player.position()+" serverEntities="+mc.getSingleplayerServer().overworld().getEntitiesOfClass(com.simibubi.create.content.contraptions.AbstractContraptionEntity.class,new AABB(-32,-80,-32,32,0,32)).size()+" bakes="+GunMeshes.bakes);
                require(GunMeshes.bakes-initialBakes<=1,"Unexpected repeated TACZ capture: "+(GunMeshes.bakes-initialBakes));
                stableUploads=CreateMovingGuns.uploads;
                try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(out().resolve("rotating-guns.png"));}
                disassembling=true;ticks=0;
            } else if(ticks>100){
                require(CreateMovingGuns.uploads==stableUploads,"Rotating guns caused repeat GPU uploads");
                mc.getSingleplayerServer().execute(()->{try{
                    var level=mc.getSingleplayerServer().overworld();
                    bearing.getMovedContraption().setAngle(0);
                    bearing.disassemble();
                    int restored=0;
                    for(int y=0;y<height;y++)for(int x=0;x<width;x++){
                        var be=level.getBlockEntity(new BlockPos(x,-59+y,1));
                        if(be instanceof WallGunEntity gun&&gun.snapshot()!=null && net.minecraft.world.item.ItemStack.isSameItemSameComponents(gun.snapshot().copyGun(),originalGun))restored++;
                    }
                    require(restored==width*height,"Restored "+restored+" / "+(width*height)+" original guns");
                    Files.writeString(out().resolve("SUCCESS.txt"),"PASS: "+(width*height)+" glued guns assembled, rendered under Create rotation, no repeated TACZ capture or GPU upload, and restored intact. "+CreateMovingGuns.stats()+"\n");
                    mc.execute(mc::stop);
                }catch(Throwable ex){failure=ex;}});
                ticks=-100000;
            }
        }catch(Throwable ex){ex.printStackTrace();try{Files.createDirectories(out());Files.writeString(out().resolve("FAILED.txt"),ex.toString());}catch(Exception ignored){}mc.stop();}
    }
}
