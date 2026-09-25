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
                Files.deleteIfExists(mc.gameDirectory.toPath().resolve("SMOKE_FAILED.txt"));
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
                private volatile Integer networkRoll;
                private volatile Boolean neighborPlaced;
                private volatile Boolean networkFlipped;
                private volatile Boolean instantBroken;
                private volatile String networkInfo;
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
                            if (!key.getCategory().equals("key.category.tacz")) throw new AssertionError("Placement key is not in TACZ controls");
                            mc.getSingleplayerServer().execute(() -> {
                                var serverPlayer = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                                serverPlayer.getInventory().selected = 0;
                                serverPlayer.getInventory().setItem(0, ConversionChecks.equipped(serverPlayer.registryAccess()));
                                serverPlayer.serverLevel().setBlock(new net.minecraft.core.BlockPos(0, -54, 0), net.minecraft.world.level.block.Blocks.QUARTZ_BLOCK.defaultBlockState(), 3);
                                for (int x = -1; x <= 1; x++) for (int z = -4; z <= -2; z++)
                                    serverPlayer.serverLevel().setBlock(new net.minecraft.core.BlockPos(x, -56, z),
                                            net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState(), 3);
                                serverPlayer.teleportTo(serverPlayer.serverLevel(), 0.5, -55, -2.5, 0, 0);
                            });
                            net.minecraft.client.KeyMapping.click(key.getKey());
                            phase = 1;
                        } else if (phase == 1 && ++phaseTicks > 3) {
                            if (!dev.kiyo.wallgun.client.PlacementClient.interceptGun() || com.tacz.guns.util.InputExtraCheck.isInGame())
                                throw new AssertionError("TACZ inputs were not blocked after placement key toggle");
                            try (var image = net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                                image.writeToFile(mc.gameDirectory.toPath().resolve("verification/mode-hud.png"));
                            }
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
                            mc.player.getInventory().setItem(0, net.minecraft.world.item.ItemStack.EMPTY);
                            mc.getSingleplayerServer().execute(() -> mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst()
                                    .getInventory().setItem(0, net.minecraft.world.item.ItemStack.EMPTY));
                            mc.hitResult = new net.minecraft.world.phys.BlockHitResult(new net.minecraft.world.phys.Vec3(0.5, -53.5, -0.3),
                                    net.minecraft.core.Direction.NORTH, new net.minecraft.core.BlockPos(0, -54, -1), false);
                            var click = new net.neoforged.neoforge.client.event.InputEvent.MouseButton.Pre(1, 1, 0);
                            dev.kiyo.wallgun.client.PlacementClient.mouse(click);
                            if (!click.isCanceled()) throw new AssertionError("Empty-hand display rotation was not captured");
                            phase = 4; phaseTicks = 0;
                        } else if (phase == 4 && ++phaseTicks > 10) {
                            mc.getSingleplayerServer().execute(() -> {
                                var serverPlayer = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                                var display = mc.getSingleplayerServer().overworld().getBlockEntity(new net.minecraft.core.BlockPos(0, -54, -1));
                                networkRoll = display instanceof dev.kiyo.wallgun.WallGunEntity gun ? gun.roll() : -1;
                                var picked = serverPlayer.pick(serverPlayer.blockInteractionRange(), 0, false);
                                networkInfo = "pick=" + picked.getType() + "/" + (picked instanceof net.minecraft.world.phys.BlockHitResult block ? block.getBlockPos() + "/" + block.getDirection() : "")
                                        + " player=" + serverPlayer.position() + "/" + serverPlayer.getYRot() + "/" + serverPlayer.getXRot()
                                        + " mode=" + dev.kiyo.wallgun.GunPlacement.enabled(serverPlayer)
                                        + " allowed=" + serverPlayer.mayUseItemAt(new net.minecraft.core.BlockPos(0, -54, -1),
                                        net.minecraft.core.Direction.NORTH, serverPlayer.getMainHandItem());
                            });
                            phase = 5; phaseTicks = 0;
                        } else if (phase == 5 && networkRoll != null) {
                            if (networkRoll == 0 || networkRoll == -1) throw new AssertionError("Empty-hand right-click did not rotate display: " + networkInfo);
                            mc.player.getInventory().setItem(0, ConversionChecks.equipped(mc.level.registryAccess()));
                            mc.getSingleplayerServer().execute(() -> mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst()
                                    .getInventory().setItem(0, ConversionChecks.equipped(mc.getSingleplayerServer().registryAccess())));
                            net.neoforged.neoforge.network.PacketDistributor.sendToServer(new dev.kiyo.wallgun.PlacementPayloads.Place(
                                    new net.minecraft.core.BlockPos(0, -54, -1), net.minecraft.core.Direction.NORTH));
                            phase = 6; phaseTicks = 0;
                        } else if (phase == 6 && ++phaseTicks > 10) {
                            mc.getSingleplayerServer().execute(() -> {
                                var display = mc.getSingleplayerServer().overworld().getBlockEntity(new net.minecraft.core.BlockPos(0, -54, -2));
                                neighborPlaced = display instanceof dev.kiyo.wallgun.WallGunEntity gun && gun.snapshot() != null;
                            });
                            phase = 7; phaseTicks = 0;
                        } else if (phase == 7 && neighborPlaced != null) {
                            if (!neighborPlaced) throw new AssertionError("Click-face neighbor placement failed");
                            net.neoforged.neoforge.network.PacketDistributor.sendToServer(new dev.kiyo.wallgun.PlacementPayloads.Adjust(
                                    new net.minecraft.core.BlockPos(0, -54, -2), true));
                            phase = 8; phaseTicks = 0;
                        } else if (phase == 8 && ++phaseTicks > 10) {
                            mc.getSingleplayerServer().execute(() -> {
                                var display = mc.getSingleplayerServer().overworld().getBlockEntity(new net.minecraft.core.BlockPos(0, -54, -2));
                                networkFlipped = display instanceof dev.kiyo.wallgun.WallGunEntity gun && gun.flipped();
                            });
                            phase = 9; phaseTicks = 0;
                        } else if (phase == 9 && networkFlipped != null) {
                            if (!networkFlipped) throw new AssertionError("Scroll flip packet did not flip neighboring display");
                            mc.player.getInventory().setItem(0, net.minecraft.world.item.ItemStack.EMPTY);
                            mc.getSingleplayerServer().execute(() -> {
                                var serverPlayer = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                                serverPlayer.getInventory().setItem(0, net.minecraft.world.item.ItemStack.EMPTY);
                                serverPlayer.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
                            });
                            phase = 10; phaseTicks = 0;
                        } else if (phase == 10 && ++phaseTicks > 4) {
                            mc.gameMode.startDestroyBlock(new net.minecraft.core.BlockPos(0, -54, -2), net.minecraft.core.Direction.NORTH);
                            phase = 11; phaseTicks = 0;
                        } else if (phase == 11 && ++phaseTicks > 8) {
                            mc.getSingleplayerServer().execute(() -> instantBroken = mc.getSingleplayerServer().overworld()
                                    .getBlockState(new net.minecraft.core.BlockPos(0, -54, -2)).isAir());
                            phase = 12; phaseTicks = 0;
                        } else if (phase == 12 && instantBroken != null) {
                            if (!instantBroken) throw new AssertionError("Empty-hand first attack did not instantly break display");
                            net.minecraft.client.KeyMapping.click(key.getKey());
                            phase = 13; phaseTicks = 0;
                        } else if (phase == 13 && ++phaseTicks > 3) {
                            if (dev.kiyo.wallgun.client.PlacementClient.interceptGun() || !com.tacz.guns.util.InputExtraCheck.isInGame())
                                throw new AssertionError("TACZ inputs did not resume after exiting placement mode");
                            Files.writeString(mc.gameDirectory.toPath().resolve("verification/placement-client.txt"), "PASS: P key in TACZ category, TACZ gate, right-click placement, empty-hand rotation, clicked-face neighbor placement, flip packet, survival empty-hand first-hit break and mode exit.\n");
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
