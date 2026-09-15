package dev.kiyo.wallgun.smoke;
import net.minecraftforge.event.TickEvent;

import dev.kiyo.wallgun.*;
import dev.kiyo.wallgun.client.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.event.*;
import java.nio.file.*;

/** Actual client/server input plus rendered pose and bounded-edit checks, isolated from user saves. */
final class PoseSmoke {
    private boolean opened,initialized;
    private volatile boolean ready;
    private volatile Throwable failure;
    private int ticks,phase,faceIndex,uploads,bakes,edits,sounds,maxEditVertices;
    private long lastFrame;
    private final StringBuilder frames=new StringBuilder("phase,tick,frame_ms,uploads,vertices,bakes\n");
    private final BlockPos target=new BlockPos(0,-54,4);
    PoseSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::tick);
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.PlayLevelSoundEvent.AtPosition e)->{
            if(!e.getLevel().isClientSide&&e.getSound()!=null&&e.getSound().value()==net.minecraft.sounds.SoundEvents.ITEM_FRAME_ADD_ITEM)sounds++;
        });
        MinecraftForge.EVENT_BUS.addListener((RenderLevelStageEvent e)->{
            if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL)return;
            long now=System.nanoTime();
            if(phase==2&&lastFrame!=0)frames.append(phase).append(',').append(ticks).append(',').append((now-lastFrame)/1e6).append(',').append(WallBatches.uploads).append(',').append(WallBatches.lastUploadedVertices).append(',').append(GunMeshes.bakes).append('\n');
            lastFrame=now;
        });
    }
    private Path out(){return Minecraft.getInstance().gameDirectory.toPath().resolve("pose-verification");}
    private void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private void server(Runnable action) {
        ready=false;
        Minecraft.getInstance().getSingleplayerServer().execute(()->{try{action.run();ready=true;}catch(Throwable ex){failure=ex;}});
    }
    private void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();
        try {
            if(!opened&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null) {
                opened=true;Files.createDirectories(out());Files.deleteIfExists(out().resolve("FAILED.txt"));Files.deleteIfExists(out().resolve("SUCCESS.txt"));
                mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(6);mc.options.framerateLimit().set(60);mc.options.hideGui=true;
                com.tacz.guns.config.client.RenderConfig.GUN_LOD_RENDER_DISTANCE.set(999);
                var settings=new LevelSettings("Pose regression",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel("pose-smoke-"+System.currentTimeMillis(),settings,new WorldOptions(42,false,false),r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
            }
            if(failure!=null)throw new AssertionError(failure);
            if(mc.level==null||mc.player==null||mc.getOverlay()!=null)return;
            if(mc.screen instanceof PauseScreen)mc.setScreen(null);
            mc.player.getAbilities().flying=true;mc.player.setDeltaMovement(Vec3.ZERO);
            if(!initialized) {
                if(++ticks<100)return;
                initialized=true;ticks=0;mc.setScreen(null);
                server(()->{
                    var level=mc.getSingleplayerServer().overworld();var original=ConversionChecks.equipped(level.registryAccess());
                    try {InteractionChecks.run(mc.getSingleplayerServer().getPlayerList().getPlayers().get(0),out());}catch(Exception ex){throw new RuntimeException(ex);}
                    sounds=0;
                    for(int x=-2;x<=13;x++)for(int y=-62;y<=-48;y++)level.setBlock(new BlockPos(x,y,0),Blocks.SMOOTH_STONE.defaultBlockState(),3);
                    for(int x=1;x<=10;x++)for(int y=-60;y<=-51;y++){
                        var pos=new BlockPos(x,y,1);level.setBlock(pos,WallGuns.BLOCK.get().defaultBlockState(),3);
                        var gun=(WallGunEntity)level.getBlockEntity(pos);gun.setSnapshot(new GunSnapshot(original));gun.setPose((x+y)&15,(x+y)%2==0);
                    }
                    var player=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);player.getAbilities().flying=true;player.onUpdateAbilities();player.connection.teleport(5.5,-57.2,19.5,180,0);
                    level.setDayTime(6000);level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,mc.getSingleplayerServer());
                });return;
            }
            if(!ready)return;
            if(phase==0) {
                if(++ticks<180)return;
                require(WallBatches.lastGuns==100,"100 rotated equipped guns visible: "+WallBatches.stats());
                maxEditVertices=4*GunMeshes.get(((WallGunEntity)mc.level.getBlockEntity(new BlockPos(5,-56,1))).snapshot()).vertices();
                screenshot("100-poses.png");uploads=WallBatches.uploads;bakes=GunMeshes.bakes;phase=1;ticks=0;
            } else if(phase==1) {
                if(++ticks<180)return;
                require(WallBatches.uploads==uploads&&GunMeshes.bakes==bakes,"Static poses must share unchanged mesh");phase=2;ticks=0;
            } else if(phase==2) {
                mc.player.setPos(5.5+2*Math.sin(ticks*.03),-57.2,4.5);mc.player.setYRot((float)(180+30*Math.sin(ticks*.03)));mc.player.setXRot(0);
                if(ticks>=30&&ticks<=340&&ticks%10==0) {
                    boolean flip=ticks%20==0;edits++;
                    server(()->((WallGunEntity)mc.getSingleplayerServer().overworld().getBlockEntity(new BlockPos(5,-56,1))).adjust(flip,false));
                }
                require(GunMeshes.bakes==bakes,"Pose edit rebaked TACZ geometry");
                require(WallBatches.lastUploadedVertices<=maxEditVertices,"Pose edit rebuilt beyond local cell: "+WallBatches.stats());
                if(++ticks<380)return;
                require(edits==32,"Edit count");Files.writeString(out().resolve("frames.csv"),frames);
                Files.writeString(out().resolve("stress.txt"),"100 complete three-attachment guns with mixed roll/flip; 180 stable ticks zero uploads; 32 pose edits without TACZ rebakes; max upload bound 4 guns. "+WallBatches.stats()+"\n");
                phase=3;ticks=0;prepareFace();
            } else if(phase==3) {
                var face=Direction.values()[faceIndex];var eye=Vec3.atCenterOf(target).add(Vec3.atLowerCornerOf(face.getNormal()).scale(3));
                mc.player.setPos(eye.x,eye.y-mc.player.getEyeHeight(),eye.z);mc.player.setDeltaMovement(Vec3.ZERO);
                if(ticks==40){click(face);}
                if(ticks==60){checkPose(1,false);mc.options.keyShift.setDown(true);}
                if(ticks==70){require(mc.player.isShiftKeyDown()&&!mc.player.isCrouching(),"Client flight Shift input versus pose");click(face);}
                if(ticks==90){checkPose(15,true);screenshot("face-"+face.getName()+"-flipped.png");}
                if(ticks==100)click(face);
                if(ticks==120){checkPose(1,false);mc.options.keyShift.setDown(false);}
                if(ticks==140){screenshot("face-"+face.getName()+".png");}
                if(++ticks<150)return;
                require(sounds==(faceIndex+1)*3,"Exactly one sound per real client action: "+sounds);
                if(++faceIndex<6){ticks=0;prepareFace();}
                else {
                    Files.writeString(out().resolve("SUCCESS.txt"),Files.readString(out().resolve("stress.txt"))+"PASS: all six faces received actual client use-item packets; creative flight Shift worked without crouching; occupied offhand; 18 actions produced exactly 18 item-frame insertion sounds; client block entities synchronized expected poses; all face screenshots captured.\n");mc.stop();
                }
            }
        }catch(Throwable ex){ex.printStackTrace();mc.options.keyShift.setDown(false);try{Files.writeString(out().resolve("FAILED.txt"),ex.toString());}catch(Exception ignored){}mc.stop();}
    }
    private void prepareFace() {
        var mc=Minecraft.getInstance();var face=Direction.values()[faceIndex];
        server(()->{
            var level=mc.getSingleplayerServer().overworld();
            for(int x=1;x<=10;x++)for(int y=-60;y<=-51;y++)level.setBlock(new BlockPos(x,y,1),Blocks.AIR.defaultBlockState(),3);
            for(Direction d:Direction.values())level.setBlock(target.relative(d),Blocks.AIR.defaultBlockState(),3);
            level.setBlock(target.relative(face.getOpposite()),Blocks.SMOOTH_STONE.defaultBlockState(),3);
            level.setBlock(target,Blocks.AIR.defaultBlockState(),3);level.setBlock(target,WallGuns.BLOCK.get().defaultBlockState().setValue(WallGunBlock.FACING,face),3);
            var display=(WallGunEntity)level.getBlockEntity(target);display.setSnapshot(new GunSnapshot(ConversionChecks.equipped(level.registryAccess())));
            if(face.getAxis().isVertical())display.setMountRoll(face==Direction.UP?4:12);
            var player=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STICK));player.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.STONE));
            var eye=Vec3.atCenterOf(target).add(Vec3.atLowerCornerOf(face.getNormal()).scale(3));
            float yaw=switch(face){case SOUTH->180;case NORTH->0;case EAST->90;case WEST->-90;default->-90;};
            player.connection.teleport(eye.x,eye.y-player.getEyeHeight(),eye.z,yaw,face==Direction.UP?90:face==Direction.DOWN?-90:0);
        });
    }
    private void checkPose(int roll,boolean flipped) {
        var gun=(WallGunEntity)Minecraft.getInstance().level.getBlockEntity(target);
        require(gun.roll()==roll&&gun.flipped()==flipped,"Network pose face="+Direction.values()[faceIndex]+" actual="+gun.roll()+"/"+gun.flipped());
    }
    private void click(Direction face) {
        var mc=Minecraft.getInstance();var hit=new BlockHitResult(Vec3.atCenterOf(target),face,target,false);
        require(mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,hit).consumesAction(),"Client stick use consumes action");
    }
    private void screenshot(String name)throws Exception {try(var image=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())){image.writeToFile(out().resolve(name));}}
}
