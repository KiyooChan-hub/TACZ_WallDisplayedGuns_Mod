package dev.kiyo.wallgun.mixin;
import dev.kiyo.wallgun.LoadingSessions;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;

import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(PlayerList.class)
public abstract class LoadingLoginMixin {
    @Inject(method="placeNewPlayer",at=@At("HEAD"))
    private void login(Connection connection,ServerPlayer player,CallbackInfo ci){LoadingSessions.arm(player);}
}
