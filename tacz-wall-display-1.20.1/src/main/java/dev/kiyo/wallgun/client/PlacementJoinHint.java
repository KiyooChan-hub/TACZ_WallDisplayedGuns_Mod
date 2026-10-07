package dev.kiyo.wallgun.client;

import dev.kiyo.wallgun.WallGunConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;

/** Local chat only; this feature never changes activation or placement state. */
public final class PlacementJoinHint {
    private static boolean pending;
    public static void login(ClientPlayerNetworkEvent.LoggingIn event){pending=true;}
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event){pending=false;}
    public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();
        if(!pending || mc.player==null || mc.level==null || mc.screen!=null
            || mc.getOverlay()!=null || !LoadingClient.readyForGameplay())return;
        pending=false;
        if(!WallGunConfig.SHOW_PLACEMENT_HINT_ON_JOIN.get())return;
        var text=PlacementClient.hasToggleKey()
            ?Component.translatable("chat.tacz_wall_display.join_hint",PlacementClient.toggleKeyName())
            :Component.translatable("chat.tacz_wall_display.join_hint_unbound");
        mc.gui.getChat().addMessage(text.append("\n").append(Component.translatable("chat.tacz_wall_display.join_hint_config",
            "config/tacz_wall_display-client.toml","showPlacementHintOnJoin = false")));
    }
    private PlacementJoinHint() {}
}
