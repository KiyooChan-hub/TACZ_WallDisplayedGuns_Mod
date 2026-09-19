package dev.kiyo.wallgun.smoke;

import dev.kiyo.wallgun.*;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import mcp.mobius.waila.api.WailaConstants;
import mcp.mobius.waila.gui.hud.*;
import java.nio.file.*;

/** Exercises plugin discovery and the real crosshair tooltip, not a direct provider call. */
final class WthitSmoke {
    private boolean opened, placed;
    private int ticks, phase, bootTicks;
    private volatile Throwable failure;
    private String expected;
    private final StringBuilder report = new StringBuilder();
    private final long deadline = System.nanoTime() + 240_000_000_000L;
    WthitSmoke() { NeoForge.EVENT_BUS.addListener(this::tick); }
    private Path out() { return Minecraft.getInstance().gameDirectory.toPath().resolve("wthit-verification"); }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private String text(net.minecraft.resources.ResourceLocation tag) throws Exception {
        var field = TooltipRenderer.class.getDeclaredField("TOOLTIP"); field.setAccessible(true);
        var line = ((Tooltip)field.get(null)).getLine(tag);
        if (line == null) return "";
        StringBuilder text = new StringBuilder();
        for (var part : line.components) {
            for (var f : part.getClass().getDeclaredFields()) {
                if (net.minecraft.network.chat.Component.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true); text.append(((net.minecraft.network.chat.Component)f.get(part)).getString());
                }
            }
        }
        return text.toString();
    }
    private void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        try {
            if (++bootTicks % 100 == 0) { System.out.println("WTHIT screen=" + mc.screen + " overlay=" + mc.getOverlay()); }
            if (mc.screen instanceof AccessibilityOnboardingScreen) mc.setScreen(new TitleScreen());
            require(System.nanoTime() < deadline, "WTHIT test timed out");
            if (failure != null) throw new AssertionError(failure);
            if (!opened && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
                opened = true; Files.createDirectories(out()); mc.options.pauseOnLostFocus = false;
                mc.options.hideGui = false; mc.options.renderDistance().set(5); mc.options.framerateLimit().set(60);
                var settings = new LevelSettings("WTHIT names", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel("wthit-" + System.currentTimeMillis(), settings, new WorldOptions(42,false,false), r -> r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), mc.screen);
            }
            if (mc.level == null || mc.player == null || mc.getOverlay() != null) return;
            if (mc.screen instanceof PauseScreen) mc.setScreen(null);
            if (++ticks < 100) return;
            if (!placed) {
                placed = true; ticks = 0; mc.setScreen(null);
                var gun = GunItemBuilder.create().setId(ResourceLocation.parse(phase == 0 ? "suffuse:n4" : "tacz:ak47")).build(mc.level.registryAccess());
                expected = gun.getItem().getName(gun).getString();
                mc.getSingleplayerServer().execute(() -> { try {
                    var server = mc.getSingleplayerServer(); var level = server.overworld();
                    for (int x=-2; x<=2; x++) for (int y=-60; y<=-54; y++) level.setBlock(new BlockPos(x,y,0),Blocks.SMOOTH_STONE.defaultBlockState(),3);
                    var pos = new BlockPos(0,-57,1);
                    level.setBlock(pos,WallGuns.BLOCK.get().defaultBlockState().setValue(WallGunBlock.FACING,Direction.SOUTH),3);
                    ((WallGunEntity)level.getBlockEntity(pos)).setSnapshot(new GunSnapshot(gun));
                    var player = server.getPlayerList().getPlayers().get(0);
                    player.getAbilities().flying = true; player.onUpdateAbilities();
                    player.connection.teleport(.5,-58.12,4,180,0); level.setDayTime(6000);
                } catch (Throwable ex) { failure = ex; } });
            } else if (ticks > 160) {
                try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) { image.writeToFile(out().resolve("gun-" + phase + ".png")); }
                var actual = text(WailaConstants.OBJECT_NAME_TAG);
                require(actual.contains(expected) && !actual.contains("装饰枪") && !actual.contains("Decorative"), "Expected " + expected + "; WTHIT showed " + actual);
                require(text(WailaConstants.MOD_NAME_TAG).contains("TACZ Wall Display"), "Mod attribution missing");
                report.append("PASS real WTHIT crosshair title: ").append(actual).append('\n');
                try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) { image.writeToFile(out().resolve("gun-" + phase + ".png")); }
                if (++phase == 2) { Files.writeString(out().resolve("SUCCESS.txt"),report); mc.stop(); }
                else { placed = false; ticks = 0; }
            }
        } catch (Throwable ex) { ex.printStackTrace(); try { Files.createDirectories(out()); Files.writeString(out().resolve("FAILED.txt"),ex.toString()); } catch (Exception ignored) {} mc.stop(); }
    }
}
