package dev.kiyo.wallgun;

import com.tacz.guns.api.item.IGun;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Server-owned, transient mode. Client requests never supply the gun snapshot. */
public final class GunPlacement {
    private static final Set<UUID> ENABLED = ConcurrentHashMap.newKeySet();
    private GunPlacement() {}
    public static void init() {
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> ENABLED.remove(e.getEntity().getUUID()));
    }
    public static boolean enabled(Player player) { return player != null && ENABLED.contains(player.getUUID()); }
    public static void set(ServerPlayer player, boolean enabled) {
        if (enabled) ENABLED.add(player.getUUID()); else ENABLED.remove(player.getUUID());
    }
    public static void place(ServerPlayer player, BlockHitResult requested) {
        if (!enabled(player) || player.isSpectator()) return;
        ItemStack gun = player.getMainHandItem();
        if (IGun.getIGunOrNull(gun) == null) return;
        // Re-raytrace on the server; never trust a client-supplied target or distant position.
        double reach = player.blockInteractionRange();
        if (!(player.pick(reach, 0, false) instanceof BlockHitResult actual)
                || !actual.getBlockPos().equals(requested.getBlockPos())
                || actual.getDirection() != requested.getDirection()) return;
        BlockPlaceContext context = new BlockPlaceContext(new UseOnContext(player, InteractionHand.MAIN_HAND, actual));
        BlockPos target = context.getClickedPos();
        if (!player.level().mayInteract(player, target) || !player.mayUseItemAt(target, actual.getDirection(), gun)) return;
        WallGuns.ITEM.get().place(context);
    }
}
