package dev.kiyo.wallgun.mixin;
import dev.kiyo.wallgun.LoadingSessions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(ServerPlayer.class)
public abstract class LoadingPlayerMixin {
    @Inject(method="restoreFrom",at=@At("HEAD"))
    private void newLife(ServerPlayer old,boolean keep,CallbackInfo ci){LoadingSessions.arm((ServerPlayer)(Object)this);}
    // This field is written only after the cancellation and same-dimension branches.
    @Inject(method="changeDimension",at=@At(value="FIELD",target="Lnet/minecraft/server/level/ServerPlayer;isChangingDimension:Z",opcode=181,shift=At.Shift.AFTER))
    private void travel(DimensionTransition transition,CallbackInfoReturnable<Entity> ci){LoadingSessions.arm((ServerPlayer)(Object)this,transition.newLevel().dimension().location().toString());}
    @Inject(method={"tick","doTick"},at=@At("HEAD"),cancellable=true)
    private void waitForScene(CallbackInfo ci){if(LoadingSessions.waiting((ServerPlayer)(Object)this))ci.cancel();}
    @Inject(method="hurt",at=@At("HEAD"),cancellable=true)
    private void noDamage(DamageSource source,float amount,CallbackInfoReturnable<Boolean> ci){
        var player=(ServerPlayer)(Object)this;
        if(source.is(DamageTypes.GENERIC_KILL)){LoadingSessions.commandKill(player);return;}
        if(LoadingSessions.waiting(player))ci.setReturnValue(false);
    }
    @Inject(method="isInvulnerableTo",at=@At("HEAD"),cancellable=true)
    private void protectedPlayer(DamageSource source,CallbackInfoReturnable<Boolean> ci){
        var player=(ServerPlayer)(Object)this;
        if(source.is(DamageTypes.GENERIC_KILL)){LoadingSessions.commandKill(player);return;}
        if(LoadingSessions.waiting(player))ci.setReturnValue(true);
    }
}
