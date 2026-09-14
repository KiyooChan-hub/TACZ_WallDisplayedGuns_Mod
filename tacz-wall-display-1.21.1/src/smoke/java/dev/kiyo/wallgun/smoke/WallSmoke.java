package dev.kiyo.wallgun.smoke;

import com.tacz.guns.api.TimelessAPI;
import dev.kiyo.wallgun.*;
import dev.kiyo.wallgun.client.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.nio.file.*;
import java.util.*;

@Mod("wall_display_smoke")
public class WallSmoke {
    private boolean opened,placed,reloading;
    private int ticks,phase,stableUploads,initialTicks;
    private volatile Throwable serverFailure;
    private static final String[] GUNS={"tacz:ak47","tacz:m249","tacz:ak47","tacz:scar_h","mk16:m4urgi10","suffuse:l119a2","tacz:hk416d","tacz:m4a1","tacz:m16a4","tacz:scar_l","ghost:arx160","tacz:hk416d"};
    public WallSmoke() { NeoForge.EVENT_BUS.addListener(this::tick); }
    private void tick(ClientTickEvent.Post event) {
        var mc=Minecraft.getInstance();
        try {
            if(!opened && mc.screen instanceof TitleScreen && mc.getOverlay()==null) {
                opened=true;
                Files.deleteIfExists(mc.gameDirectory.toPath().resolve("SMOKE_FAILED.txt"));
                Files.deleteIfExists(mc.gameDirectory.toPath().resolve("verification/SUCCESS.txt"));
                mc.options.pauseOnLostFocus=false;
                mc.options.renderDistance().set(5);mc.options.framerateLimit().set(60);
                var settings=new LevelSettings("Wall gun prototype verification",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel("wallgun-smoke-"+System.currentTimeMillis(),settings,new WorldOptions(42,false,false),
                        registries->registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),mc.screen);
            }
            if(!opened || mc.level==null || mc.player==null || mc.getOverlay()!=null || reloading)return;
            if(mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen)mc.setScreen(null);
            mc.player.getAbilities().flying=true;
            mc.player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            if(serverFailure!=null)throw new AssertionError(serverFailure);
            if(!placed) {
                if(++initialTicks<100)return;
                placed=true;mc.setScreen(null);
                mc.options.hideGui=true;mc.options.renderDistance().set(5);mc.options.framerateLimit().set(60);
                mc.getSingleplayerServer().execute(()->{
                    try {
                        var server=mc.getSingleplayerServer();var world=server.overworld();
                        world.setDayTime(6000);world.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);
                        for(int x=-6;x<=6;x++)for(int y=-60;y<=-53;y++)world.setBlock(new BlockPos(x,y,0),Blocks.QUARTZ_BLOCK.defaultBlockState(),3);
                        for(int i=0;i<GUNS.length;i++) {
                            var pos=new BlockPos(-4+(i%4)*3,-54-(i/4)*2,1);
                            var id=ResourceLocation.parse(GUNS[i]);
                            if(TimelessAPI.getCommonGunIndex(id).isEmpty())throw new AssertionError("Missing fixture gun "+id);
                            var state=WallGuns.BLOCK.get().defaultBlockState().setValue(WallGunBlock.FACING,Direction.SOUTH);
                            world.setBlock(pos,state,3);var gun=(WallGunEntity)world.getBlockEntity(pos);gun.setGunId(id);
                            var saved=gun.saveWithFullMetadata(world.registryAccess());
                            var restored=new WallGunEntity(pos,state);restored.loadWithComponents(saved,world.registryAccess());
                            if(!restored.gunId().equals(id))throw new AssertionError("Gun ID save/load failed");
                            if(!WallGuns.gunId(WallGuns.BLOCK.get().getCloneItemStack(world,pos,state)).equals(id))throw new AssertionError("Pick block ID failed");
                            if(!state.canSurvive(world,pos))throw new AssertionError("Wall support failed");
                        }
                        var player=server.getPlayerList().getPlayers().getFirst();
                        for(Direction direction:Direction.Plane.HORIZONTAL) {
                            var wall=new BlockPos(30+direction.get2DDataValue()*4,-56,10);
                            var target=wall.relative(direction);
                            world.setBlock(wall,Blocks.QUARTZ_BLOCK.defaultBlockState(),3);
                            var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(wall),direction,wall,false);
                            var context=new net.minecraft.world.item.context.BlockPlaceContext(player,net.minecraft.world.InteractionHand.MAIN_HAND,WallGuns.stack(ResourceLocation.parse("tacz:ak47")),hit);
                            var state=WallGuns.BLOCK.get().getStateForPlacement(context);
                            if(state==null || state.getValue(WallGunBlock.FACING)!=direction)throw new AssertionError("Placement orientation "+direction);
                            world.setBlock(target,state,3);
                            world.setBlock(wall,Blocks.AIR.defaultBlockState(),3);
                            if(!world.getBlockState(target).isAir())throw new AssertionError("Wall removal left gun behind "+direction);
                        }
                        player.getAbilities().flying=true;player.onUpdateAbilities();player.teleportTo(.5,-57.2,12.5);player.setYRot(180);player.setXRot(0);
                        player.connection.teleport(.5,-57.2,12.5,180,0);
                    }catch(Throwable e){serverFailure=e;}
                });
                return;
            }
            if(++ticks<180)return;
            if(GunMeshes.failures!=0)throw new AssertionError("Model capture failures: "+GunMeshes.failures);
            Path output=mc.gameDirectory.toPath().resolve("verification");Files.createDirectories(output);
            if(phase==0) {
                if(WallBatches.lastGuns<12)throw new AssertionError("Expected 12 visible wall guns: "+WallBatches.stats()+" camera="+mc.player.position()+" yaw="+mc.player.getYRot()+" pitch="+mc.player.getXRot()+" fixture="+mc.level.getBlockState(new BlockPos(-4,-54,1))+" BE="+mc.level.getBlockEntity(new BlockPos(-4,-54,1)));
                screenshot("front.png");stableUploads=WallBatches.uploads;phase=1;ticks=0;
            } else if(phase==1) {
                if(WallBatches.uploads!=stableUploads)throw new AssertionError("Static scene rebuilt mesh: "+WallBatches.stats());
                Files.writeString(output.resolve("stable.txt"),WallBatches.stats()+"\nNo uploads during 180 stable ticks; 12 NBT round trips and pick-block identities verified.\n");
                // Oblique view reveals whether the result is a real 3D mesh close to the wall.
                mc.getSingleplayerServer().execute(()->mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst().connection.teleport(8,-57.2,8,135,0));
                phase=2;ticks=0;
            } else if(phase==2) {
                screenshot("oblique.png");reloading=true;phase=3;ticks=0;
                mc.reloadResourcePacks().thenRun(()->reloading=false);
            } else if(phase==3) {
                if(WallBatches.lastGuns==0)throw new AssertionError("No geometry after reload");
                screenshot("reloaded.png");
                // Validate the creative tab contents against the live, successfully loaded common index.
                var tab=WallGuns.TAB.get();tab.buildContents(new net.minecraft.world.item.CreativeModeTab.ItemDisplayParameters(mc.level.enabledFeatures(),true,mc.level.registryAccess()));
                Set<ResourceLocation> ids=new HashSet<>();for(var stack:tab.getDisplayItems())ids.add(WallGuns.gunId(stack));
                Set<ResourceLocation> expected=new HashSet<>();TimelessAPI.getAllCommonGunIndex().forEach(e->expected.add(e.getKey()));
                if(!ids.equals(expected))throw new AssertionError("Creative catalog mismatch");
                Files.writeString(output.resolve("catalog.txt"),"Catalog="+ids.size()+"; "+WallBatches.stats()+"\nReload, static cache stability, NBT identity, wall support and pick block verified.\n");
                mc.getSingleplayerServer().execute(()->{
                    var world=mc.getSingleplayerServer().overworld();
                    for(int x=-6;x<=12;x++)for(int y=-61;y<=-49;y++) {
                        world.setBlock(new BlockPos(x,y,1),Blocks.AIR.defaultBlockState(),3);
                        world.setBlock(new BlockPos(x,y,0),Blocks.QUARTZ_BLOCK.defaultBlockState(),3);
                    }
                    for(int x=1;x<=10;x++)for(int y=-60;y<=-51;y++) {
                        var pos=new BlockPos(x,y,1);world.setBlock(pos,WallGuns.BLOCK.get().defaultBlockState(),3);
                        ((WallGunEntity)world.getBlockEntity(pos)).setGunId(ResourceLocation.parse("tacz:ak47"));
                    }
                    mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst().connection.teleport(5.5,-57.2,19.5,180,0);
                });
                phase=4;ticks=0;
            } else if(phase==4) {
                if(WallBatches.lastGuns!=100 || WallBatches.lastDraws!=1)throw new AssertionError("Expected 100 same-material guns in one draw: "+WallBatches.stats());
                screenshot("100-guns.png");stableUploads=WallBatches.uploads;phase=5;ticks=0;
            } else if(phase==5) {
                if(WallBatches.uploads!=stableUploads)throw new AssertionError("Dense static scene rebuilt mesh");
                Files.writeString(output.resolve("checks.txt"),Files.readString(output.resolve("catalog.txt"))+"100 same-material guns, one section: "+WallBatches.stats()+"\nNo uploads during 180 stable ticks. Four placement orientations and support removal verified.\n");
                phase=6;mc.setScreen(new Icons());
            }
        } catch(Throwable failure) {
            failure.printStackTrace();try{screenshot("failure.png");Files.writeString(mc.gameDirectory.toPath().resolve("SMOKE_FAILED.txt"),failure.toString());}catch(Exception ignored){}mc.stop();
        }
    }
    private void screenshot(String name)throws Exception {
        var mc=Minecraft.getInstance();try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(mc.gameDirectory.toPath().resolve("verification").resolve(name));}
    }
    private final class Icons extends net.minecraft.client.gui.screens.Screen {
        private int frames;
        Icons(){super(net.minecraft.network.chat.Component.literal("Wall gun inventory verification"));}
        @Override public void render(net.minecraft.client.gui.GuiGraphics graphics,int mouseX,int mouseY,float partial) {
            graphics.fill(0,0,width,height,0xFF202831);
            graphics.drawString(font,"TACZ wall guns - original slot textures",12,12,-1);
            for(int i=0;i<GUNS.length;i++) {
                int x=12+(i%4)*(width/4), y=42+(i/4)*80;
                var stack=WallGuns.stack(ResourceLocation.parse(GUNS[i]));
                graphics.pose().pushPose();graphics.pose().translate(x,y,0);graphics.pose().scale(3,3,3);graphics.renderItem(stack,0,0);graphics.pose().popPose();
                graphics.drawString(font,GUNS[i],x,y+54,-1);
                if(stack.getHoverName().getString().isBlank())throw new AssertionError("Missing item name");
            }
            graphics.flush();
            if(++frames==40) {
                try {
                    screenshot("inventory.png");
                    var dir=minecraft.gameDirectory.toPath().resolve("verification");
                    Files.writeString(dir.resolve("SUCCESS.txt"),Files.readString(dir.resolve("checks.txt"))+"12 source-pack item icons rendered.\n");
                }catch(Exception e){throw new RuntimeException(e);}
                minecraft.stop();
            }
        }
        @Override public boolean isPauseScreen(){return true;}
    }
}
