package dev.kiyo.wallgun.smoke;

import dev.kiyo.wallgun.client.PlacementClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import java.nio.file.*;

/** Creates its own isolated world; does not require any pre-existing save. */
final class PlacementSmoke {
    private boolean opened,started,finished;
    private int ticks,clientPhase,clientTicks;
    private volatile Throwable failure;
    PlacementSmoke(){MinecraftForge.EVENT_BUS.addListener(this::tick);}
    private void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END || finished)return;
        Minecraft mc=Minecraft.getInstance();
        try{
            Path output=mc.gameDirectory.toPath().resolve("verification");
            if(!opened && mc.screen instanceof TitleScreen && mc.getOverlay()==null){
                opened=true;Files.createDirectories(output);Files.deleteIfExists(output.resolve("placement.txt"));
                Files.deleteIfExists(mc.gameDirectory.toPath().resolve("SMOKE_FAILED.txt"));
                var settings=new LevelSettings("Wall gun placement verification",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel("wallgun-placement-smoke-"+System.currentTimeMillis(),settings,
                        new WorldOptions(42,false,false),registries->registries.registryOrThrow(Registries.WORLD_PRESET)
                                .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
                return;
            }
            if(failure!=null)throw new AssertionError(failure);
            if(!opened||mc.level==null||mc.player==null||mc.getOverlay()!=null)return;
            if(!started && ++ticks>80){
                started=true;
                mc.getSingleplayerServer().execute(()->{
                    try{PlacementChecks.run(mc.getSingleplayerServer().getPlayerList().getPlayers().get(0),output);}
                    catch(Throwable ex){failure=ex;}
                });
            }
            if(started && Files.exists(output.resolve("placement.txt"))){
                if(clientPhase==0){
                    mc.player.getInventory().selected=0;
                    mc.player.getInventory().setItem(0,ConversionChecks.equipped(mc.level.registryAccess()));
                    var field=PlacementClient.class.getDeclaredField("toggle");field.setAccessible(true);
                    var key=(net.minecraft.client.KeyMapping)field.get(null);
                    if(key==null || !"key.category.tacz".equals(key.getCategory()))throw new AssertionError("P key missing from TACZ controls");
                    net.minecraft.client.KeyMapping.click(key.getKey());clientPhase=1;
                }else if(clientPhase==1 && ++clientTicks>5){
                    if(!PlacementClient.interceptGun() || com.tacz.guns.util.InputExtraCheck.isInGame())
                        throw new AssertionError("TACZ gun input not suppressed in placement mode");
                    try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){
                        image.writeToFile(output.resolve("mode-hud.png"));
                    }
                    mc.hitResult=net.minecraft.world.phys.BlockHitResult.miss(new net.minecraft.world.phys.Vec3(0,-50,0),
                            net.minecraft.core.Direction.NORTH,new net.minecraft.core.BlockPos(0,-50,0));
                    PlacementClient.mouse(new net.minecraftforge.client.event.InputEvent.MouseButton.Pre(0,1,0));
                    clientPhase=2;clientTicks=0;
                }else if(clientPhase==2 && ++clientTicks>5){
                    try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){
                        image.writeToFile(output.resolve("dry-fire-warning.png"));
                    }
                    Files.writeString(output.resolve("client.txt"),"PASS: TACZ P key, placement input gate, HUD and dry-fire warning.\n");
                    finished=true;mc.stop();
                }
            }
        }catch(Throwable ex){
            try{Files.writeString(mc.gameDirectory.toPath().resolve("SMOKE_FAILED.txt"),ex.toString());}catch(Exception ignored){}
            finished=true;mc.stop();
        }
    }
}
