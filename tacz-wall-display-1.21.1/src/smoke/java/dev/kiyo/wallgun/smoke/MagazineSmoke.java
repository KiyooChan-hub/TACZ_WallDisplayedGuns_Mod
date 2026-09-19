package dev.kiyo.wallgun.smoke;

import dev.kiyo.wallgun.client.*;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.*;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.resource.GunDisplayInstance;
import dev.kiyo.wallgun.*;
import dev.kiyo.wallgun.mixin.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.nio.file.*;
import java.util.*;

/** Real-pack regression; an explicit node mask is the independent reference, not production cleanup. */
public final class MagazineSmoke {
    private final boolean seedOld = Boolean.getBoolean("wallgun.magazineSeedOld");
    private final boolean reopen = Boolean.getBoolean("wallgun.magazineReopen");
    private final GunSnapshot[] snapshots = new GunSnapshot[4];
    private final StringBuilder report = new StringBuilder();
    private boolean opened, checked, done;
    private volatile boolean ready;
    private volatile Throwable failure;
    private int ticks;
    private final long deadline = System.nanoTime() + 240_000_000_000L;
    public MagazineSmoke() { NeoForge.EVENT_BUS.addListener(this::tick); }
    private Path out() { return Minecraft.getInstance().gameDirectory.toPath().resolve("magazine-verification"); }
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static BedrockPart node(BedrockPart root, String name) {
        if (root == null) return null;
        if (name.equals(root.name)) return root;
        for (var child : root.children) { var result = node(child, name); if (result != null) return result; }
        return null;
    }
    private static BlockPos position(int magazine, int side) { return new BlockPos(-4 + magazine * 3, -55 - side * 3, 1); }
    private ItemStack fixture(int magazine) {
        var mc = Minecraft.getInstance();
        var gun = GunItemBuilder.create().setId(ResourceLocation.parse("suffuse:n4"))
                .setAmmoCount(30).setAmmoInBarrel(true).build(mc.level.registryAccess());
        var api = IGun.getIGunOrNull(gun);
        var ids = new ArrayList<>(List.of("spearhead:sight_exp3_g33_fde", "spearhead:muzzle_rotex5c_fde", "spearhead:grip_rvg_fde", "tacz:stock_ripstock"));
        if (magazine > 0) ids.add("tacz:extended_mag_" + magazine);
        for (String id : ids) {
            var attachment = AttachmentItemBuilder.create().setId(ResourceLocation.parse(id)).build();
            require(api.allowAttachment(gun, attachment), "Invalid test attachment " + id);
            api.installAttachment(mc.level.registryAccess(), gun, attachment);
        }
        return gun;
    }
    private GunMeshes.Mesh reference(ItemStack gun, int magazine, boolean extraVisible) throws ReflectiveOperationException {
        var live = TimelessAPI.getGunDisplay(gun).orElseThrow();
        var access = (GunDisplayAccessor) live;
        var display = GunDisplayInstance.create(access.wallgun$displayId(), access.wallgun$display());
        var model = display.getGunModel();
        var extra = node(model.getRootNode(), "additional_magazine");
        require(extra != null, "N4 extra-magazine fixture absent");
        extra.visible = extraVisible;
        PoseStack pose = new PoseStack();
        pose.mulPose(Axis.YP.rotationDegrees(180)); pose.scale(.5F, .5F, .5F); pose.scale(-1, -1, 1);
        GunTransformInvoker.wallgun$position(ItemDisplayContext.FIXED, display.getTransform().getScale(), model, pose);
        GunTransformInvoker.wallgun$scale(ItemDisplayContext.FIXED, display.getTransform().getScale(), pose);
        var capture = new MeshCapture(); MeshCapture.begin(capture);
        try { model.render(pose, gun, ItemDisplayContext.FIXED, RenderType.entityCutout(display.getModelTexture()), 0, OverlayTexture.NO_OVERLAY); }
        finally { MeshCapture.end(); }
        for (int i = 0; i < 4; i++) {
            var part = node(model.getRootNode(), i == 0 ? "mag_standard" : "mag_extended_" + i);
            require(part != null && part.visible == (i == magazine), "Magazine variant mask " + magazine + "/" + i);
        }
        var raw = capture.finish();
        var normalize = GunMeshes.class.getDeclaredMethod("normalize", Map.class);
        normalize.setAccessible(true);
        return GunOrientation.canonical((GunMeshes.Mesh) normalize.invoke(null, raw), raw, access.wallgun$displayId(), model, pose);
    }
    private void checkMeshes() throws ReflectiveOperationException {
        GunMeshes.clear();
        for (int magazine = 0; magazine < 4; magazine++) {
            var gun = fixture(magazine); var before = gun.copy();
            var live = TimelessAPI.getGunDisplay(gun).orElseThrow().getGunModel();
            var liveExtra = node(live.getRootNode(), "additional_magazine");
            boolean liveVisible = liveExtra.visible;
            var snapshot = new GunSnapshot(gun); snapshots[magazine] = snapshot;
            var actual = GunMeshes.get(snapshot);
            var single = reference(gun, magazine, false);
            var duplicated = reference(gun, magazine, true);
            require(duplicated.vertices() > single.vertices(), "Reference did not reproduce duplicate geometry");
            require(actual.equals(seedOld ? duplicated : single), "Static mesh differs from " + (seedOld ? "legacy duplicate" : "single installed magazine") + " reference at level " + magazine);
            require(liveExtra.visible == liveVisible, "Capture modified live gun model");
            require(ItemStack.matches(before, gun) && ItemStack.matches(before, snapshot.copyGun()), "Original gun data changed");
            require(GunMeshes.get(snapshot) == actual, "Static mesh cache not reused");
            report.append("level=").append(magazine).append(" duplicateVertices=").append(duplicated.vertices()).append(" singleVertices=").append(single.vertices()).append(" actualVertices=").append(actual.vertices()).append('\n');
        }
    }
    private void tick(ClientTickEvent.Post event) {
        if (done) return;
        var mc = Minecraft.getInstance();
        try {
            require(System.nanoTime() < deadline, "Magazine regression timed out");
            if (failure != null) throw new AssertionError(failure);
            if (!opened && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
                opened = true; Files.createDirectories(out()); Files.deleteIfExists(out().resolve("FAILED.txt"));
                mc.options.pauseOnLostFocus = false; mc.options.hideGui = true; mc.options.renderDistance().set(5); mc.options.framerateLimit().set(60);
                if (reopen) mc.createWorldOpenFlows().openWorld(Files.readString(out().resolve("world.txt")).trim(), () -> mc.setScreen(new TitleScreen()));
                else {
                    String world = "magazine-" + System.currentTimeMillis(); Files.writeString(out().resolve("world.txt"), world);
                    var settings = new LevelSettings("N4 magazine regression", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
                    mc.createWorldOpenFlows().createFreshLevel(world, settings, new WorldOptions(42, false, false), r -> r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), mc.screen);
                }
            }
            if (mc.level == null || mc.player == null || mc.getOverlay() != null) return;
            if (mc.screen instanceof PauseScreen) mc.setScreen(null);
            if (++ticks < 120) return;
            if (!checked) {
                checked = true; mc.setScreen(null); checkMeshes();
                mc.getSingleplayerServer().execute(() -> { try {
                    var level = mc.getSingleplayerServer().overworld();
                    if (!reopen) {
                        for (int x = -6; x <= 7; x++) for (int y = -60; y <= -53; y++) level.setBlock(new BlockPos(x, y, 0), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
                        for (int mag = 0; mag < 4; mag++) for (int side = 0; side < 2; side++) {
                            var pos = position(mag, side);
                            level.setBlock(pos, WallGuns.BLOCK.get().defaultBlockState().setValue(WallGunBlock.FACING, Direction.SOUTH), 3);
                            var entity = (WallGunEntity) level.getBlockEntity(pos); entity.setSnapshot(snapshots[mag]); entity.setPose(0, side == 1);
                        }
                    }
                    for (int mag = 0; mag < 4; mag++) for (int side = 0; side < 2; side++) {
                        var entity = (WallGunEntity) level.getBlockEntity(position(mag, side));
                        require(entity != null && snapshots[mag].equals(entity.snapshot()) && entity.flipped() == (side == 1), "Saved fixture/flip changed");
                    }
                    var player = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst(); player.getAbilities().flying = true; player.onUpdateAbilities();
                    player.connection.teleport(.5, -57.4, 10.5, 180, 0); level.setDayTime(6000); level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, mc.getSingleplayerServer()); ready = true;
                } catch (Throwable ex) { failure = ex; } }); ticks = 0;
            } else if (ready && ticks > 180 && !WallWarmup.loading()) {
                require(WallBatches.lastGuns == 8, "Eight magazine/flip displays rendered: " + WallBatches.stats());
                require(GunMeshes.failures == 0, "Mesh capture failures");
                String name = seedOld ? "legacy" : reopen ? "reopened" : "fixed";
                try (var shot = Screenshot.takeScreenshot(mc.getMainRenderTarget())) { shot.writeToFile(out().resolve(name + ".png")); }
                Files.writeString(out().resolve(name + "-SUCCESS.txt"), report + "PASS: 4 magazine states x 2 sides; snapshots intact; cache reused; live model untouched.\n"); done = true; mc.stop();
            }
        } catch (Throwable ex) { done = true; ex.printStackTrace(); try { Files.createDirectories(out()); Files.writeString(out().resolve("FAILED.txt"), ex.toString()); } catch (Exception ignored) {} mc.stop(); }
    }
}
