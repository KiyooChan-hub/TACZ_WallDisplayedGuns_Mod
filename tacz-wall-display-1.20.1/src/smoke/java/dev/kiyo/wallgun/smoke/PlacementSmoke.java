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
    private int ticks,clientPhase,clientTicks,dimensionTicks;
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
                            if (Boolean.getBoolean("wallgun.firstPersonSmoke")) {
                                var cold = ConversionChecks.equipped(mc.level.registryAccess());
                                var display = com.tacz.guns.api.TimelessAPI.getGunDisplay(cold).orElseThrow();
                                var machine = display.getAnimationStateMachine();
                                if (machine.isInitialized()) throw new AssertionError("Fixture must have a cold animation");
                                Object renderer = net.minecraftforge.client.extensions.common.IClientItemExtensions.of(cold.getItem()).getCustomRenderer();
                                var render = Class.forName("com.tacz.guns.client.renderer.item.AnimateGeoItemRenderer").getMethod("renderFirstPerson",
                                        net.minecraft.client.player.LocalPlayer.class, net.minecraft.world.item.ItemStack.class,
                                        net.minecraft.world.item.ItemDisplayContext.class, com.mojang.blaze3d.vertex.PoseStack.class,
                                        net.minecraft.client.renderer.MultiBufferSource.class, int.class, float.class);
                                // Null rendering arguments deliberately prove the stale frame exits before rendering.
                                render.invoke(renderer, mc.player, cold, net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                                        null, null, 15728880, 0.0F);
                                if (machine.isInitialized()) throw new AssertionError("Stale stack must not initialize with the wrong hand");
                                var saved = mc.player.getMainHandItem();
                                mc.player.getInventory().setItem(mc.player.getInventory().selected, cold);
                                try {
                                    render.invoke(renderer, mc.player, cold, net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                                            new com.mojang.blaze3d.vertex.PoseStack(), mc.renderBuffers().bufferSource(), 15728880, 0.0F);
                                    if (!machine.isInitialized()) throw new AssertionError("Matching first frame must initialize the animation");
                                } finally { mc.player.getInventory().setItem(mc.player.getInventory().selected, saved); }
                                Files.writeString(mc.gameDirectory.toPath().resolve("verification/first-person-initialization.txt"),
                                        "PASS: cold stale frame canceled before drawing; matching first frame initialized and rendered through TACZ.\n");
                            }
                    var modifierField = dev.kiyo.wallgun.client.PlacementClient.class.getDeclaredField("adjustmentModifier");
                    modifierField.setAccessible(true);
                    var modifier = (net.minecraft.client.KeyMapping) modifierField.get(null);
                    if (modifier == null || !modifier.getCategory().equals("key.category.tacz")
                            || modifier.getKey().getValue() != org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT)
                        throw new AssertionError("Default Alt modifier missing from TACZ controls");
                    modifier.setKey(com.mojang.blaze3d.platform.InputConstants.getKey(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL, 0));
                    if (modifier.getKey().getValue() != org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL)
                        throw new AssertionError("Modifier rebinding failed");
                    var label = net.minecraft.network.chat.Component.translatable("hud.tacz_wall_display.flip", modifier.getTranslatedKeyMessage()).getString();
                    if (!label.contains(modifier.getTranslatedKeyMessage().getString()) || label.contains("%s"))
                        throw new AssertionError("HUD did not use rebound modifier: " + label);
                    java.nio.file.Files.writeString(mc.gameDirectory.toPath().resolve("verification/modifier.txt"),
                            "PASS: default Left Alt in TACZ controls; rebound Left Control; localized HUD uses the bound key.\n" + label);
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
                    var server=mc.getSingleplayerServer();var player=server.getPlayerList().getPlayers().get(0);
                    if(!dev.kiyo.wallgun.GunPlacement.enabled(player))throw new AssertionError("Server placement mode was not enabled before travel");
                    server.execute(()->player.teleportTo(server.getLevel(net.minecraft.world.level.Level.NETHER),.5,100,8.5,180,0));
                    clientPhase=3;clientTicks=0;
                }else if(clientPhase==3){
                    if(++dimensionTicks>1200)throw new AssertionError("Dimension placement reset timed out");
                    if(mc.level.dimension()!=net.minecraft.world.level.Level.NETHER || dev.kiyo.wallgun.client.LoadingClient.waiting() || mc.screen!=null)return;
                    if(++clientTicks<5)return;
                    var serverPlayer=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
                    if(dev.kiyo.wallgun.GunPlacement.enabled(serverPlayer))throw new AssertionError("Server placement mode survived dimension change");
                    var enabled=PlacementClient.class.getDeclaredField("enabled");enabled.setAccessible(true);
                    if(enabled.getBoolean(null))throw new AssertionError("Client placement mode survived dimension release");
                    if(!com.tacz.guns.util.InputExtraCheck.isInGame())throw new AssertionError("TACZ input did not resume after dimension release");
                    try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){
                        image.writeToFile(output.resolve("dimension-mode-off.png"));
                    }
                    Files.writeString(output.resolve("client.txt"),"PASS: TACZ P key, placement input gate, HUD, dry-fire warning, and synchronized dimension auto-close.\n");
                    finished=true;mc.stop();
                }
            }
        }catch(Throwable ex){ex.printStackTrace();
            try{Files.writeString(mc.gameDirectory.toPath().resolve("SMOKE_FAILED.txt"),ex.toString());}catch(Exception ignored){}
            finished=true;mc.stop();
        }
    }
}
