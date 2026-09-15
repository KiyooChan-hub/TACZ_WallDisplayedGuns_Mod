package dev.kiyo.wallgun.smoke;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.gun.FireMode;
import dev.kiyo.wallgun.*;
import dev.kiyo.wallgun.client.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.nio.file.*;
import java.util.*;

/** Verifies decorated-gun meshes, conversion, drops and rendering without exercising firearm gameplay. */
final class DisplayCompatibilitySmoke {
    private final String[] gunIds = System.getProperty("wallgun.compatibilityGuns", "tacz:hk416d,tacz:scar_l").split(",");
    private boolean opened, initialized;
    private volatile boolean ready;
    private volatile Throwable failure;
    private int ticks;
    private final StringBuilder report = new StringBuilder();

    DisplayCompatibilitySmoke() { NeoForge.EVENT_BUS.addListener(this::tick); }
    private Path out() { return Minecraft.getInstance().gameDirectory.toPath().resolve("display-compatibility-verification"); }
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private ItemStack gun(String name, int magazine, HolderLookup.Provider lookup) {
        var builder = GunItemBuilder.create().setId(ResourceLocation.parse(name)).setAmmoCount(5)
                .setAmmoInBarrel(true).setFireMode(FireMode.SEMI);
        if (magazine > 0) builder.putAttachment(AttachmentType.EXTENDED_MAG, ResourceLocation.parse("tacz:extended_mag_" + magazine));
        var gun = builder.build(lookup);
        require(!gun.isEmpty(), "Gun fixture missing: " + name);
        var api = IGun.getIGunOrNull(gun);
        for (String attachmentId : List.of("scope_acog_ta31", "muzzle_silencer_knight_qd", "grip_vertical_military", "laser_peq15")) {
            var attachment = AttachmentItemBuilder.create().setId(ResourceLocation.parse("tacz:" + attachmentId)).build();
            require(api.allowAttachment(gun, attachment), "Unsupported fixture attachment " + name + "/" + attachmentId);
            api.installAttachment(lookup, gun, attachment);
        }
        return gun;
    }
    private void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        try {
            if (!opened && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
                opened = true;
                require(gunIds.length == 2, "Supply exactly two fixture IDs with wallgun.compatibilityGuns");
                Files.createDirectories(out());
                Files.deleteIfExists(out().resolve("FAILED.txt"));
                Files.deleteIfExists(out().resolve("SUCCESS.txt"));
                mc.options.pauseOnLostFocus = false;
                mc.options.renderDistance().set(5);
                mc.options.framerateLimit().set(60);
                mc.options.hideGui = true;
                var settings = new LevelSettings("Display compatibility regression", GameType.CREATIVE, false,
                        Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel("display-compatibility-" + System.currentTimeMillis(), settings,
                        new WorldOptions(42, false, false), r -> r.registryOrThrow(Registries.WORLD_PRESET)
                                .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), mc.screen);
            }
            if (failure != null) throw new AssertionError(failure);
            if (mc.level == null || mc.player == null || mc.getOverlay() != null) return;
            if (mc.screen instanceof PauseScreen) mc.setScreen(null);
            if (!initialized) {
                if (++ticks < 100) return;
                initialized = true;
                mc.setScreen(null);
                for (String name : gunIds) for (int magazine = 0; magazine < 4; magazine++) {
                    var mesh = GunMeshes.get(new GunSnapshot(gun(name, magazine, mc.level.registryAccess())));
                    require(!mesh.missing(), "Missing equipped display mesh " + name + "/" + magazine);
                    report.append(name).append(" magazine=").append(magazine).append(" vertices=").append(mesh.vertices()).append('\n');
                }
                mc.getSingleplayerServer().execute(() -> {
                    try {
                        var player = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                        var level = player.serverLevel();
                        for (String name : gunIds) {
                            var original = gun(name, 3, level.registryAccess());
                            for (int size : new int[]{2, 3}) {
                                var input = new ArrayList<ItemStack>(Collections.nCopies(size * size, ItemStack.EMPTY));
                                input.set(size * size - 1, original);
                                var recipe = new GunConversionRecipe(CraftingBookCategory.MISC);
                                var decorated = recipe.assemble(CraftingInput.of(size, size, input), level.registryAccess());
                                require(decorated.is(WallGuns.ITEM.get()), "Forward conversion " + name);
                                input.set(size * size - 1, decorated);
                                require(ItemStack.matches(original, recipe.assemble(CraftingInput.of(size, size, input), level.registryAccess())), "Reverse conversion lost gun data " + name);
                            }
                        }
                        for (int x = -4; x <= 4; x++) for (int y = -60; y <= -54; y++)
                            level.setBlock(new BlockPos(x, y, 0), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
                        for (int i = 0; i < gunIds.length; i++) {
                            var original = gun(gunIds[i], 0, level.registryAccess());
                            var pos = new BlockPos(i == 0 ? -1 : 2, -57, 1);
                            level.setBlock(pos, WallGuns.BLOCK.get().defaultBlockState().setValue(WallGunBlock.FACING, Direction.SOUTH), 3);
                            var entity = (WallGunEntity) level.getBlockEntity(pos);
                            entity.setSnapshot(new GunSnapshot(original));
                            var drops = net.minecraft.world.level.block.Block.getDrops(entity.getBlockState(), level, pos, entity);
                            require(drops.size() == 1 && ItemStack.matches(original, drops.getFirst()), "Original gun drop " + gunIds[i]);
                        }
                        player.getAbilities().flying = true;
                        player.onUpdateAbilities();
                        player.connection.teleport(.5, -58, 6.5, 180, 0);
                        level.setDayTime(6000);
                        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, mc.getSingleplayerServer());
                        ready = true;
                    } catch (Throwable ex) { failure = ex; }
                });
                ticks = 0;
                return;
            }
            if (!ready || ++ticks < 160) return;
            require(WallBatches.lastGuns == 2, "Two equipped displays rendered: " + WallBatches.stats());
            try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) { image.writeToFile(out().resolve("equipped-displays.png")); }
            Files.writeString(out().resolve("SUCCESS.txt"), report + "PASS: four magazine appearances, 2x2/3x3 reversible conversion, original-gun drops, two equipped wall displays.\n");
            mc.stop();
        } catch (Throwable ex) {
            ex.printStackTrace();
            try { Files.createDirectories(out()); Files.writeString(out().resolve("FAILED.txt"), ex.toString()); } catch (Exception ignored) {}
            mc.stop();
        }
    }
}
