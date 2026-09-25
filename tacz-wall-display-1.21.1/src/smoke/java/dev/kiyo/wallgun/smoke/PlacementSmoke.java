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
                            int expectedDistance = Integer.getInteger("wallgun.expectedRenderDistance", 512);
                            if (dev.kiyo.wallgun.WallGunConfig.maxRenderDistance() != expectedDistance)
                                throw new AssertionError("Unexpected render distance: " + dev.kiyo.wallgun.WallGunConfig.maxRenderDistance());
                            if (mc.getSoundManager().getSoundEvent(dev.kiyo.wallgun.WallGuns.MODE_OPEN.getId()) == null
                                    || mc.getSoundManager().getSoundEvent(dev.kiyo.wallgun.WallGuns.MODE_CLOSE.getId()) == null)
                                throw new AssertionError("Placement mode sounds were not loaded");
                            if (com.tacz.guns.client.sound.SoundPlayManager.playClientSound(mc.player,
                                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tacz", "dry_fire"), 0.0F, 1.0F, 16) == null)
                                throw new AssertionError("AKM default dry-fire fallback was not loaded");
                            if (!dev.kiyo.wallgun.client.PlacementClient.interceptGun() || com.tacz.guns.util.InputExtraCheck.isInGame())
                                throw new AssertionError("TACZ inputs were not blocked after placement key toggle: intercept="
                                        + dev.kiyo.wallgun.client.PlacementClient.interceptGun() + " gun="
                                        + mc.player.getMainHandItem() + " screen=" + mc.screen + " input="
                                        + com.tacz.guns.util.InputExtraCheck.isInGame());
                            try (var image = net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                                image.writeToFile(mc.gameDirectory.toPath().resolve("verification/mode-hud.png"));
                            }
                            mc.hitResult = net.minecraft.world.phys.BlockHitResult.miss(new net.minecraft.world.phys.Vec3(0.5, -53.5, -1.5),
                                    net.minecraft.core.Direction.NORTH, new net.minecraft.core.BlockPos(0, -54, -2));
                            dev.kiyo.wallgun.client.PlacementClient.mouse(new net.neoforged.neoforge.client.event.InputEvent.MouseButton.Pre(0, 1, 0));
                            if (dev.kiyo.wallgun.client.PlacementClient.allowGunBlockInput())
                                throw new AssertionError("A miss must not be treated as a block attack");
                            var click = new net.neoforged.neoforge.client.event.InputEvent.MouseButton.Pre(1, 1, 0);
                            mc.hitResult = new net.minecraft.world.phys.BlockHitResult(new net.minecraft.world.phys.Vec3(0.5, -53.5, 0), net.minecraft.core.Direction.NORTH, new net.minecraft.core.BlockPos(0, -54, 0), false);
                            dev.kiyo.wallgun.client.PlacementClient.mouse(click);
                            if (!click.isCanceled()) throw new AssertionError("Right-click was not captured");
                            phase = 2; phaseTicks = 0;
                        } else if (phase == 2 && ++phaseTicks > 10) {
                            try (var image = net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                                image.writeToFile(mc.gameDirectory.toPath().resolve("verification/dry-fire-warning.png"));
                            }
                            mc.getSingleplayerServer().execute(() -> {
                                var world = mc.getSingleplayerServer().overworld();
                                var display = world.getBlockEntity(new net.minecraft.core.BlockPos(0, -54, -1));
                                networkPlaced = display instanceof dev.kiyo.wallgun.WallGunEntity gun && gun.snapshot() != null;
                            });
                            phase = 3; phaseTicks = 0;
                        } else if (phase == 3 && networkPlaced != null) {
                            if (!networkPlaced) throw new AssertionError("Client right-click packet did not place a gun");
                            mc.hitResult = new net.minecraft.world.phys.BlockHitResult(new net.minecraft.world.phys.Vec3(0.5, -53.5, -0.3),
                                    net.minecraft.core.Direction.NORTH, new net.minecraft.core.BlockPos(0, -54, -1), false);
                            var click = new net.neoforged.neoforge.client.event.InputEvent.MouseButton.Pre(1, 1, 0);
                            dev.kiyo.wallgun.client.PlacementClient.mouse(click);
                            if (!click.isCanceled()) throw new AssertionError("Gun-held neighbor placement was not captured");
                            phase = 4; phaseTicks = 0;
                        } else if (phase == 4 && ++phaseTicks > 10) {
                            mc.getSingleplayerServer().execute(() -> {
                                var display = mc.getSingleplayerServer().overworld().getBlockEntity(new net.minecraft.core.BlockPos(0, -54, -2));
                                neighborPlaced = display instanceof dev.kiyo.wallgun.WallGunEntity gun && gun.snapshot() != null;
                            });
                            phase = 5; phaseTicks = 0;
                        } else if (phase == 5 && neighborPlaced != null) {
                            if (!neighborPlaced) throw new AssertionError("Right-click did not place beside display");
                            net.neoforged.neoforge.network.PacketDistributor.sendToServer(new dev.kiyo.wallgun.PlacementPayloads.Adjust(
                                    new net.minecraft.core.BlockPos(0, -54, -2), 0));
                            phase = 6; phaseTicks = 0;
                        } else if (phase == 6 && ++phaseTicks > 10) {
                            mc.getSingleplayerServer().execute(() -> {
                                var display = mc.getSingleplayerServer().overworld().getBlockEntity(new net.minecraft.core.BlockPos(0, -54, -2));
                                networkFlipped = display instanceof dev.kiyo.wallgun.WallGunEntity gun && gun.flipped();
                            });
                            phase = 7; phaseTicks = 0;
                        } else if (phase == 7 && networkFlipped != null) {
                            if (!networkFlipped) throw new AssertionError("Flip packet did not flip display");
                            net.neoforged.neoforge.network.PacketDistributor.sendToServer(new dev.kiyo.wallgun.PlacementPayloads.Adjust(
                                    new net.minecraft.core.BlockPos(0, -54, -2), 1));
                            phase = 8; phaseTicks = 0;
                        } else if (phase == 8 && ++phaseTicks > 10) {
                            mc.getSingleplayerServer().execute(() -> {
                                var display = mc.getSingleplayerServer().overworld().getBlockEntity(new net.minecraft.core.BlockPos(0, -54, -2));
                                networkRoll = display instanceof dev.kiyo.wallgun.WallGunEntity gun ? gun.roll() : -1;
                            });
                            phase = 9; phaseTicks = 0;
                        } else if (phase == 9 && networkRoll != null) {
                            if (networkRoll == 0 || networkRoll == -1) throw new AssertionError("Up-scroll did not rotate display");
                            networkRoll = null;
                            net.neoforged.neoforge.network.PacketDistributor.sendToServer(new dev.kiyo.wallgun.PlacementPayloads.Adjust(
                                    new net.minecraft.core.BlockPos(0, -54, -2), -1));
                            phase = 17; phaseTicks = 0;
                        } else if (phase == 17 && ++phaseTicks > 10) {
                            mc.getSingleplayerServer().execute(() -> {
                                var display = mc.getSingleplayerServer().overworld().getBlockEntity(new net.minecraft.core.BlockPos(0, -54, -2));
                                networkRoll = display instanceof dev.kiyo.wallgun.WallGunEntity gun ? gun.roll() : -1;
                            });
                            phase = 18; phaseTicks = 0;
                        } else if (phase == 18 && networkRoll != null) {
                            if (networkRoll != 0) throw new AssertionError("Down-scroll did not restore rotation: " + networkRoll);
                            mc.player.getInventory().setItem(0, ConversionChecks.equipped(mc.level.registryAccess()));
                            mc.getSingleplayerServer().execute(() -> {
                                var serverPlayer = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                                serverPlayer.getInventory().setItem(0, ConversionChecks.equipped(serverPlayer.registryAccess()));
                                serverPlayer.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
                            });
                            phase = 10; phaseTicks = 0;
                        } else if (phase == 10 && ++phaseTicks > 4) {
                            mc.hitResult = new net.minecraft.world.phys.BlockHitResult(new net.minecraft.world.phys.Vec3(0.5, -53.5, -1.7),
                                    net.minecraft.core.Direction.NORTH, new net.minecraft.core.BlockPos(0, -54, -2), false);
                            var pick = new net.neoforged.neoforge.client.event.InputEvent.InteractionKeyMappingTriggered(
                                    2, mc.options.keyPickItem, net.minecraft.world.InteractionHand.MAIN_HAND);
                            com.tacz.guns.client.event.ClientPreventGunClick.onClickInput(pick);
                            if (pick.isCanceled()) throw new AssertionError("TACZ still blocks gun-held pick-block in placement mode");
                            var picked = mc.level.getBlockState(new net.minecraft.core.BlockPos(0, -54, -2))
                                    .getCloneItemStack(mc.hitResult, mc.level, new net.minecraft.core.BlockPos(0, -54, -2), mc.player);
                            if (com.tacz.guns.api.item.IGun.getIGunOrNull(picked) == null)
                                throw new AssertionError("Display pick-block did not return its original gun");
                            var attack = new net.neoforged.neoforge.client.event.InputEvent.InteractionKeyMappingTriggered(
                                    0, mc.options.keyAttack, net.minecraft.world.InteractionHand.MAIN_HAND);
                            com.tacz.guns.client.event.ClientPreventGunClick.onClickInput(attack);
                            if (attack.isCanceled()) throw new AssertionError("TACZ still blocks gun-held attack input in placement mode");
                            mc.gameMode.startDestroyBlock(new net.minecraft.core.BlockPos(0, -54, -2), net.minecraft.core.Direction.NORTH);
                            phase = 11; phaseTicks = 0;
                        } else if (phase == 11 && ++phaseTicks > 8) {
                            mc.getSingleplayerServer().execute(() -> instantBroken = mc.getSingleplayerServer().overworld()
                                    .getBlockState(new net.minecraft.core.BlockPos(0, -54, -2)).isAir());
                            phase = 12; phaseTicks = 0;
                        } else if (phase == 12 && instantBroken != null) {
                            if (!instantBroken) throw new AssertionError("Gun-held survival first attack did not instantly break display");
                            instantBroken = null;
                            mc.getSingleplayerServer().execute(() -> {
                                var serverPlayer = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                                serverPlayer.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
                                serverPlayer.serverLevel().setBlock(new net.minecraft.core.BlockPos(0, -54, -2),
                                        net.minecraft.world.level.block.Blocks.QUARTZ_BLOCK.defaultBlockState(), 3);
                            });
                            phase = 13; phaseTicks = 0;
                        } else if (phase == 13 && ++phaseTicks > 8) {
                            mc.hitResult = new net.minecraft.world.phys.BlockHitResult(new net.minecraft.world.phys.Vec3(0.5, -53.5, -1.7),
                                    net.minecraft.core.Direction.NORTH, new net.minecraft.core.BlockPos(0, -54, -2), false);
                            mc.gameMode.startDestroyBlock(new net.minecraft.core.BlockPos(0, -54, -2), net.minecraft.core.Direction.NORTH);
                            phase = 14; phaseTicks = 0;
                        } else if (phase == 14 && ++phaseTicks > 8) {
                            mc.getSingleplayerServer().execute(() -> instantBroken = mc.getSingleplayerServer().overworld()
                                    .getBlockState(new net.minecraft.core.BlockPos(0, -54, -2)).isAir());
                            phase = 15; phaseTicks = 0;
                        } else if (phase == 15 && instantBroken != null) {
                            if (!instantBroken) throw new AssertionError("Gun-held creative attack did not break ordinary block");
                            net.minecraft.client.KeyMapping.click(key.getKey());
                            phase = 16; phaseTicks = 0;
                        } else if (phase == 16 && ++phaseTicks > 3) {
                            if (dev.kiyo.wallgun.client.PlacementClient.interceptGun() || !com.tacz.guns.util.InputExtraCheck.isInGame())
                                throw new AssertionError("TACZ inputs did not resume after exiting placement mode: enabled="
                                        + dev.kiyo.wallgun.client.PlacementClient.interceptGun() + " screen=" + mc.screen
                                        + " level=" + mc.level + " player=" + mc.player);
                            var attack = new net.neoforged.neoforge.client.event.InputEvent.InteractionKeyMappingTriggered(
                                    0, mc.options.keyAttack, net.minecraft.world.InteractionHand.MAIN_HAND);
                            com.tacz.guns.client.event.ClientPreventGunClick.onClickInput(attack);
                            if (!attack.isCanceled()) throw new AssertionError("TACZ gun-click guard stayed bypassed after mode exit");
                            Files.writeString(mc.gameDirectory.toPath().resolve("verification/placement-client.txt"), "PASS: render config=" + dev.kiyo.wallgun.WallGunConfig.maxRenderDistance() + ", P key in TACZ category, TACZ gate, air-click warning, right-click placement and neighboring placement, flip and up/down rotation packets, gun-held pick-block returning original gun, survival gun-held first-hit break, creative gun-held ordinary-block break, and TACZ guard restoration.\n");
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
