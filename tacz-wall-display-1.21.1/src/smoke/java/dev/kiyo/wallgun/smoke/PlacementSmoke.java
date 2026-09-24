package dev.kiyo.wallgun.smoke;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.nio.file.*;

final class PlacementSmoke {
    private boolean opened, started;
    private int ticks;
    private volatile Throwable failure;
    PlacementSmoke() { NeoForge.EVENT_BUS.addListener(this::tick); }
    private void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        try {
            if (!opened && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
                opened = true;
                var settings = new LevelSettings("Wall gun placement verification", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel("wallgun-placement-smoke-" + System.currentTimeMillis(), settings, new WorldOptions(42, false, false),
                        registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), mc.screen);
            }
            if (failure != null) throw new AssertionError(failure);
            if (!opened || mc.level == null || mc.player == null || mc.getOverlay() != null || started || ++ticks < 80) return;
            started = true;
            mc.getSingleplayerServer().execute(() -> {
                try { PlacementChecks.run(mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst(), mc.gameDirectory.toPath().resolve("verification")); }
                catch (Throwable ex) { failure = ex; }
            });
            NeoForge.EVENT_BUS.addListener(new java.util.function.Consumer<ClientTickEvent.Post>() {
                private int phase, phaseTicks;
                private net.minecraft.client.KeyMapping key;
                private volatile Boolean networkPlaced;
                @Override public void accept(ClientTickEvent.Post ignored) {
                    if (failure != null) {
                        try { Files.writeString(mc.gameDirectory.toPath().resolve("SMOKE_FAILED.txt"), failure.toString()); } catch (Exception ignoredException) {}
                        mc.stop();
                    } else try {
                        if (phase == 0 && Files.exists(mc.gameDirectory.toPath().resolve("verification/placement.txt"))) {
                            mc.player.getInventory().selected = 0;
                            mc.player.getInventory().setItem(0, ConversionChecks.equipped(mc.level.registryAccess()));
                            var field = dev.kiyo.wallgun.client.PlacementClient.class.getDeclaredField("toggle");
                            field.setAccessible(true);
                            key = (net.minecraft.client.KeyMapping) field.get(null);
                            if (key == null) throw new AssertionError("Placement key was not registered");
                            mc.getSingleplayerServer().execute(() -> {
                                var serverPlayer = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                                serverPlayer.getInventory().selected = 0;
                                serverPlayer.getInventory().setItem(0, ConversionChecks.equipped(serverPlayer.registryAccess()));
                                serverPlayer.serverLevel().setBlock(new net.minecraft.core.BlockPos(0, -54, 0), net.minecraft.world.level.block.Blocks.QUARTZ_BLOCK.defaultBlockState(), 3);
                                serverPlayer.teleportTo(serverPlayer.serverLevel(), 0.5, -55, -2.5, 0, 0);
                            });
                            net.minecraft.client.KeyMapping.click(key.getKey());
                            phase = 1;
                        } else if (phase == 1 && ++phaseTicks > 3) {
                            if (!dev.kiyo.wallgun.client.PlacementClient.interceptGun() || com.tacz.guns.util.InputExtraCheck.isInGame())
                                throw new AssertionError("TACZ inputs were not blocked after placement key toggle");
                            var click = new net.neoforged.neoforge.client.event.InputEvent.MouseButton.Pre(1, 1, 0);
                            mc.hitResult = new net.minecraft.world.phys.BlockHitResult(new net.minecraft.world.phys.Vec3(0.5, -53.5, 0), net.minecraft.core.Direction.NORTH, new net.minecraft.core.BlockPos(0, -54, 0), false);
                            dev.kiyo.wallgun.client.PlacementClient.mouse(click);
                            if (!click.isCanceled()) throw new AssertionError("Right-click was not captured");
                            phase = 2; phaseTicks = 0;
                        } else if (phase == 2 && ++phaseTicks > 10) {
                            mc.getSingleplayerServer().execute(() -> {
                                var world = mc.getSingleplayerServer().overworld();
                                var display = world.getBlockEntity(new net.minecraft.core.BlockPos(0, -54, -1));
                                networkPlaced = display instanceof dev.kiyo.wallgun.WallGunEntity gun && gun.snapshot() != null;
                            });
                            phase = 3; phaseTicks = 0;
                        } else if (phase == 3 && networkPlaced != null) {
                            if (!networkPlaced) throw new AssertionError("Client right-click packet did not place a gun");
                            net.minecraft.client.KeyMapping.click(key.getKey());
                            phase = 4; phaseTicks = 0;
                        } else if (phase == 4 && ++phaseTicks > 3) {
                            if (dev.kiyo.wallgun.client.PlacementClient.interceptGun() || !com.tacz.guns.util.InputExtraCheck.isInGame())
                                throw new AssertionError("TACZ inputs did not resume after exiting placement mode");
                            Files.writeString(mc.gameDirectory.toPath().resolve("verification/placement-client.txt"), "PASS: configurable key registered; mode toggles; TACZ shared input gate blocked in mode; client right-click packet placed gun; normal TACZ input restored on exit.\n");
                            mc.stop();
                        }
                    } catch (Throwable ex) {
                        failure = ex;
                    }
                }
            });
        } catch (Throwable ex) {
            try { Files.writeString(mc.gameDirectory.toPath().resolve("SMOKE_FAILED.txt"), ex.toString()); } catch (Exception ignored) {}
            mc.stop();
        }
    }
}
