package dev.kiyo.wallgun.client;

import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/** Pair the server's deferred draw with a local animation/cooldown, without echoing another draw. */
public final class LoadingGunClient {
    private static boolean localOnly;
    public static boolean localOnly(){return localOnly;}
    public static void restoredDraw() {
        var player=Minecraft.getInstance().player;if(player==null)return;
        localOnly=true;
        try{IClientPlayerGunOperator.fromLocalPlayer(player).draw(ItemStack.EMPTY);}
        finally{localOnly=false;}
    }
    private LoadingGunClient() {}
}
