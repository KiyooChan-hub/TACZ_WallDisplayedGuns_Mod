package dev.kiyo.wallgun.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.tacz.guns.api.item.IGun;
import dev.kiyo.wallgun.PlacementPayloads;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** Client-only control and entirely code-drawn HUD. */
public final class PlacementClient {
    private static KeyMapping toggle;
    private static boolean enabled;
    private PlacementClient() {}
    public static boolean interceptGun() {
        var mc = Minecraft.getInstance();
        return enabled && mc.player != null && mc.screen == null && IGun.getIGunOrNull(mc.player.getMainHandItem()) != null;
    }
    public static void register(RegisterKeyMappingsEvent event) {
        InputConstants.Key key;
        try { key = InputConstants.getKey(PlacementClientConfig.TOGGLE_KEY.get()); }
        catch (RuntimeException ex) { key = InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_P); }
        toggle = new KeyMapping("key.tacz_wall_display.placement", key.getType(), key.getValue(), "key.categories.tacz_wall_display");
        event.register(toggle);
    }
    public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (toggle == null || mc.player == null || mc.level == null) return;
        while (toggle.consumeClick()) {
            enabled = !enabled;
            if (enabled && IGun.getIGunOrNull(mc.player.getMainHandItem()) != null) {
                var operator = com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator.fromLocalPlayer(mc.player);
                operator.aim(false);
                operator.chargeShoot(false);
            }
            PacketDistributor.sendToServer(new PlacementPayloads.Mode(enabled));
        }
    }
    public static void mouse(InputEvent.MouseButton.Pre event) {
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_RIGHT || !interceptGun()) return;
        event.setCanceled(true);
        if (event.getAction() == GLFW.GLFW_PRESS && Minecraft.getInstance().hitResult instanceof BlockHitResult hit)
            PacketDistributor.sendToServer(new PlacementPayloads.Place(hit.getBlockPos(), hit.getDirection()));
    }
    public static void reset(ClientPlayerNetworkEvent.LoggingOut event) { enabled = false; }
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
            gui.fill(i, edge, i + 1, screenHeight - edge, color);
            gui.fill(screenWidth - i - 1, edge, screenWidth - i, screenHeight - edge, color);
        }
        Component key = toggle == null ? Component.literal("P") : toggle.getTranslatedKeyMessage();
        Component title = Component.translatable("hud.tacz_wall_display.placement_title");
        Component action = Component.translatable("hud.tacz_wall_display.placement_action");
        Component exit = Component.translatable("hud.tacz_wall_display.placement_exit", key);
        int padding = 12, line = mc.font.lineHeight + 5;
        int textWidth = Math.max(mc.font.width(title), Math.max(mc.font.width(action), mc.font.width(exit)));
        int available = Math.max(1, screenWidth - 24 - padding * 2);
        float scale = Math.min(1f, available / (float) Math.max(1, textWidth));
        int boxWidth = (int) Math.ceil(textWidth * scale) + padding * 2;
        int boxHeight = (int) Math.ceil((line * 3 + 4) * scale) + padding * 2;
        int right = screenWidth - 18, left = right - boxWidth, top = 18;
        gui.fill(left, top, right, top + boxHeight, 0xb8000000);
        gui.pose().pushPose();
        gui.pose().translate(right - padding - textWidth * scale, top + padding, 0);
        gui.pose().scale(scale, scale, 1);
        gui.drawString(mc.font, title, (textWidth - mc.font.width(title)) / 2, 0, 0xffffff);
        gui.drawString(mc.font, action, (textWidth - mc.font.width(action)) / 2, line, 0xffffff);
        gui.drawString(mc.font, exit, (textWidth - mc.font.width(exit)) / 2, line * 2, 0xffffff);
        gui.pose().popPose();
    }
}
