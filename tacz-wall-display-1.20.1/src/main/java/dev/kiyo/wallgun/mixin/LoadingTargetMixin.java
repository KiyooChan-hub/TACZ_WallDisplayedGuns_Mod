package dev.kiyo.wallgun.mixin;
import dev.kiyo.wallgun.LoadingSessions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(LivingEntity.class)
public abstract class LoadingTargetMixin {
    @Inject(method={"canBeSeenAsEnemy","isPushable","isPickable"},at=@At("HEAD"),cancellable=true)
    private void hiddenFromGameplay(CallbackInfoReturnable<Boolean> ci){if((Object)this instanceof ServerPlayer p && LoadingSessions.waiting(p))ci.setReturnValue(false);}
}
