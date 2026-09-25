package dev.kiyo.wallgun.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.tacz.guns.api.item.IGun;
import dev.kiyo.wallgun.PlacementPayloads;
import dev.kiyo.wallgun.WallGuns;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** Client-only control and entirely code-drawn HUD. */
public final class PlacementClient {
    private static KeyMapping toggle;
    private static boolean enabled;
    private static net.minecraft.core.BlockPos heldGun;
    private static int repeatDelay;
    private PlacementClient() {}
    public static boolean interceptGun() {
        var mc = Minecraft.getInstance();
        return enabled && mc.player != null && mc.screen == null && IGun.getIGunOrNull(mc.player.getMainHandItem()) != null;
    }
    public static boolean allowGunBlockInput() {
        var mc = Minecraft.getInstance();
        return interceptGun() && mc.hitResult instanceof BlockHitResult;
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
            heldGun = null;
            mc.getSoundManager().play(SimpleSoundInstance.forUI(
                    enabled ? WallGuns.MODE_OPEN.get() : WallGuns.MODE_CLOSE.get(), 1.0F, 1.0F));
            if (enabled && IGun.getIGunOrNull(mc.player.getMainHandItem()) != null) {
                var operator = com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator.fromLocalPlayer(mc.player);
                operator.aim(false);
                operator.chargeShoot(false);
            }
            PacketDistributor.sendToServer(new PlacementPayloads.Mode(enabled));
        }
        if (heldGun == null) return;
        if (!active() || !mc.isWindowActive()
                || GLFW.glfwGetMouseButton(mc.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) != GLFW.GLFW_PRESS
                || leftAltDown(mc) || !(mc.hitResult instanceof BlockHitResult hit)
                || !heldGun.equals(hit.getBlockPos())
                || !mc.level.getBlockState(heldGun).is(WallGuns.BLOCK.get())) {
            heldGun = null;
            return;
        }
        if (--repeatDelay <= 0) {
            PacketDistributor.sendToServer(new PlacementPayloads.Adjust(heldGun, false));
            repeatDelay = 4;
        }
    }
    public static void mouse(InputEvent.MouseButton.Pre event) {
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_RIGHT || !active()) return;
        if (event.getAction() == GLFW.GLFW_RELEASE) heldGun = null;
        var mc = Minecraft.getInstance();
        if (!(mc.hitResult instanceof BlockHitResult hit)) return;
        boolean holdingGun = IGun.getIGunOrNull(mc.player.getMainHandItem()) != null;
        boolean targetGun = mc.level.getBlockState(hit.getBlockPos()).is(WallGuns.BLOCK.get());
        if (!targetGun && !holdingGun) return;
        event.setCanceled(true);
        if (event.getAction() != GLFW.GLFW_PRESS) return;
        if (targetGun && !(holdingGun && leftAltDown(mc))) {
            PacketDistributor.sendToServer(new PlacementPayloads.Adjust(hit.getBlockPos(), false));
            heldGun = hit.getBlockPos();
            repeatDelay = 4;
        } else {
            heldGun = null;
            PacketDistributor.sendToServer(new PlacementPayloads.Place(hit.getBlockPos(), hit.getDirection()));
        }
    }
    public static void scroll(InputEvent.MouseScrollingEvent event) {
        if (!active() || event.getScrollDeltaY() == 0) return;
        var mc = Minecraft.getInstance();
        if (!leftAltDown(mc) || !(mc.hitResult instanceof BlockHitResult hit)
                || !mc.level.getBlockState(hit.getBlockPos()).is(WallGuns.BLOCK.get())) return;
        event.setCanceled(true);
        PacketDistributor.sendToServer(new PlacementPayloads.Adjust(hit.getBlockPos(), true));
    }
    private static boolean active() {
        var mc = Minecraft.getInstance();
        return enabled && mc.player != null && mc.level != null && mc.screen == null;
    }
    private static boolean leftAltDown(Minecraft mc) {
        return InputConstants.isKeyDown(mc.getWindow().getWindow(), GLFW.GLFW_KEY_LEFT_ALT);
    }
    public static void reset(ClientPlayerNetworkEvent.LoggingOut event) { enabled = false; heldGun = null; }
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
                Component.translatable("hud.tacz_wall_display.place"),
                Component.translatable("hud.tacz_wall_display.break"),
                Component.translatable("hud.tacz_wall_display.rotate"),
                Component.translatable("hud.tacz_wall_display.neighbor"),
                Component.translatable("hud.tacz_wall_display.flip"),
                Component.translatable("hud.tacz_wall_display.toggle", key));
        int padding = 8, line = mc.font.lineHeight + 3;
        float scale = 0.65F;
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
        int right = screenWidth - 18, left = right - boxWidth, top = 18;
        gui.fill(left, top, right, top + boxHeight, 0x80000000);
        gui.pose().pushPose();
        gui.pose().translate(left + padding, top + padding, 0);
        gui.pose().scale(scale, scale, 1);
        for (int index = 0; index < lines.size(); index++)
            gui.drawString(mc.font, lines.get(index), 0, line * index, 0xffffff);
        gui.pose().popPose();
    }
}
