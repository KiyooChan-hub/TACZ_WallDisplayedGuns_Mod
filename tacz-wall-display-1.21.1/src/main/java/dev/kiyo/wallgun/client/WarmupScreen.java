package dev.kiyo.wallgun.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** A non-pausing gate: client packets, world ticks and world rendering keep advancing underneath. */
public final class WarmupScreen extends Screen {
    private final Screen previous;
    public WarmupScreen(Screen previous) { super(Component.literal("正在准备附近的装饰枪")); this.previous=previous; }
    @Override protected void init() { WallWarmup.beginGate(); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean shouldCloseOnEsc() { return false; }
    @Override public void tick() {
        if(WallWarmup.gateFinished())minecraft.setScreen(previous);
    }
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partialTick) {
        graphics.fill(0,0,width,height,0xFF20242B);
        graphics.drawCenteredString(font,title,width/2,height/2-24,0xFFFFFF);
        graphics.drawCenteredString(font,WallWarmup.progress(),width/2,height/2,0xCFD5DD);
        graphics.drawCenteredString(font,"准备完成后自动进入游戏",width/2,height/2+24,0xA5ADB8);
    }
}
