package dev.kiyo.wallgun.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.input.RefitKey;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.sound.SoundPlayManager;
import com.tacz.guns.sound.SoundManager;
import dev.kiyo.wallgun.PlacementPayloads;
import dev.kiyo.wallgun.WallGuns;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** Client-only control and entirely code-drawn HUD. */
public final class PlacementClient {
    private static KeyMapping toggle;
    private static boolean enabled;
    private static final DryFireWarning WARNING = new DryFireWarning();
    private static final ResourceLocation DEFAULT_DRY_FIRE = ResourceLocation.fromNamespaceAndPath("tacz", "dry_fire");
    private PlacementClient() {}
    public static boolean interceptGun() {
        var mc = Minecraft.getInstance();
        return enabled && mc.player != null && mc.screen == null && IGun.getIGunOrNull(mc.player.getMainHandItem()) != null;
    }
    public static boolean allowGunBlockInput() {
        var mc = Minecraft.getInstance();
        return interceptGun() && mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK;
    }
    public static void register(RegisterKeyMappingsEvent event) {
        toggle = new KeyMapping("key.tacz_wall_display.placement", InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_P, "key.category.tacz");
        event.register(toggle);
    }
    public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (toggle == null || mc.player == null || mc.level == null) return;
        while (toggle.consumeClick()) {
            enabled = !enabled;
            WARNING.clear();
            mc.getSoundManager().play(SimpleSoundInstance.forUI(
                    enabled ? WallGuns.MODE_OPEN.get() : WallGuns.MODE_CLOSE.get(), 1.0F, 1.0F));
            if (enabled && IGun.getIGunOrNull(mc.player.getMainHandItem()) != null) {
                var operator = com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator.fromLocalPlayer(mc.player);
                operator.aim(false);
                operator.chargeShoot(false);
            }
            PacketDistributor.sendToServer(new PlacementPayloads.Mode(enabled));
        }
    }
    public static void mouse(InputEvent.MouseButton.Pre event) {
        if (!active() || event.getAction() != GLFW.GLFW_PRESS) return;
        var mc = Minecraft.getInstance();
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (interceptGun() && (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK)) {
                if (WARNING.trigger(System.nanoTime())) playDryFire(mc);
            }
            return;
        }
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return;
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        boolean holdingGun = IGun.getIGunOrNull(mc.player.getMainHandItem()) != null;
        boolean targetGun = mc.level.getBlockState(hit.getBlockPos()).is(WallGuns.BLOCK.get());
        boolean alt = leftAltDown(mc);
        if (alt ? !targetGun : !holdingGun) return;
        event.setCanceled(true);
        if (alt) {
            PacketDistributor.sendToServer(new PlacementPayloads.Adjust(hit.getBlockPos(), 0));
        } else {
            PacketDistributor.sendToServer(new PlacementPayloads.Place(hit.getBlockPos(), hit.getDirection()));
        }
    }
    public static void scroll(InputEvent.MouseScrollingEvent event) {
        if (!active() || event.getScrollDeltaY() == 0) return;
        var mc = Minecraft.getInstance();
        if (!leftAltDown(mc) || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK
                || !mc.level.getBlockState(hit.getBlockPos()).is(WallGuns.BLOCK.get())) return;
        event.setCanceled(true);
        PacketDistributor.sendToServer(new PlacementPayloads.Adjust(hit.getBlockPos(), event.getScrollDeltaY() > 0 ? 1 : -1));
    }
    private static void playDryFire(Minecraft mc) {
        var display = TimelessAPI.getGunDisplay(mc.player.getMainHandItem()).orElse(null);
        ResourceLocation sound = display == null ? null : display.getSounds(SoundManager.DRY_FIRE_SOUND);
        var played = sound == null ? null : SoundPlayManager.playClientSound(mc.player, sound, 1.0F, 1.0F, 16);
        if (played == null) SoundPlayManager.playClientSound(mc.player, DEFAULT_DRY_FIRE, 1.0F, 1.0F, 16);
    }
    private static boolean active() {
        var mc = Minecraft.getInstance();
        return enabled && mc.player != null && mc.level != null && mc.screen == null;
    }
    private static boolean leftAltDown(Minecraft mc) {
        return InputConstants.isKeyDown(mc.getWindow().getWindow(), GLFW.GLFW_KEY_LEFT_ALT);
    }
    public static void reset(ClientPlayerNetworkEvent.LoggingOut event) { enabled = false; WARNING.clear(); }
    public static void render(RenderGuiEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (!enabled || mc.player == null || mc.options.hideGui || mc.screen != null) return;
        GuiGraphics gui = event.getGuiGraphics();
        int screenWidth = gui.guiWidth(), screenHeight = gui.guiHeight();
        int edge = Math.max(12, Math.min(28, Math.min(screenWidth, screenHeight) / 35));
        for (int i = 0; i < edge; i++) {
            int alpha = (int) (110.0 * (1.0 - i / (double) edge) * (1.0 - i / (double) edge));
            int color = alpha << 24 | 0xff9900;
            gui.fill(0, i, screenWidth, i + 1, color);
            gui.fill(0, screenHeight - i - 1, screenWidth, screenHeight - i, color);
            gui.fill(i, 0, i + 1, screenHeight, color);
            gui.fill(screenWidth - i - 1, 0, screenWidth - i, screenHeight, color);
        }
        Component key = toggle == null ? Component.literal("P") : toggle.getTranslatedKeyMessage();
        var labels = java.util.List.of(
                Component.translatable("hud.tacz_wall_display.placement_title"),
                Component.literal("--------------------------------"),
                Component.translatable("hud.tacz_wall_display.place"),
                Component.translatable("hud.tacz_wall_display.break"),
                Component.translatable("hud.tacz_wall_display.flip"),
                Component.translatable("hud.tacz_wall_display.rotate_up"),
                Component.translatable("hud.tacz_wall_display.rotate_down"),
                Component.translatable("hud.tacz_wall_display.pick"),
                Component.translatable("hud.tacz_wall_display.refit", RefitKey.REFIT_KEY.getTranslatedKeyMessage()),
                Component.translatable("hud.tacz_wall_display.toggle", key));
        int padding = 6, line = mc.font.lineHeight + 3;
        float scale = 0.52F;
        int maxWidth = Math.max(1, screenWidth / 3);
        int contentLimit = Math.max(1, (int) ((maxWidth - padding * 2) / scale));
        var lines = new java.util.ArrayList<net.minecraft.util.FormattedCharSequence>();
        int textWidth = 0;
        for (Component label : labels) {
            var wrapped = mc.font.split(label, contentLimit);
            lines.addAll(wrapped);
            for (var segment : wrapped) textWidth = Math.max(textWidth, mc.font.width(segment));
        }
        int boxWidth = Math.min(maxWidth, (int) Math.ceil(textWidth * scale) + padding * 2);
        int boxHeight = (int) Math.ceil((line * lines.size()) * scale) + padding * 2;
        int right = screenWidth - 13, left = right - boxWidth;
        int warningY = Math.max(14, screenHeight / 20);
        int top = warningY + 5;
        gui.fill(left, top, right, top + boxHeight, 0x80000000);
        gui.pose().pushPose();
        gui.pose().translate(left + padding, top + padding, 0);
        gui.pose().scale(scale, scale, 1);
        for (int index = 0; index < lines.size(); index++)
            gui.drawString(mc.font, lines.get(index), 0, line * index, 0xffffff);
        gui.pose().popPose();
        Component warning = Component.translatable("hud.tacz_wall_display.cannot_fire");
        float warningBaseScale = Math.min(1.0F, (maxWidth - 8.0F) / (mc.font.width(warning) * 1.3F));
        for (DryFireWarning.Frame frame : WARNING.frames(System.nanoTime())) {
            gui.pose().pushPose();
            float warningScale = warningBaseScale * (float) frame.scale();
            gui.pose().translate(right + 3 - mc.font.width(warning) * warningScale / 2.0, warningY, 0);
            gui.pose().scale(warningScale, warningScale, 1);
            int color = ((int) Math.round(255 * frame.opacity()) << 24) | 0xff3030;
            gui.drawString(mc.font, warning, -mc.font.width(warning) / 2, -mc.font.lineHeight / 2, color, true);
            gui.pose().popPose();
        }
    }
}
