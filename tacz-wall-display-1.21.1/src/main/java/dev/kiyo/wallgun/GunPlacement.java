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
import net.minecraft.world.level.GameType;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundSource;
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
        if (!enabled(player) || !canEdit(player)) return;
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
        if (WallGuns.ITEM.get().place(context).consumesAction()) {
            // BlockItem broadcasts to everyone except its player. This server-only placement
            // has no client prediction, so send the missing local sound only to that player.
            var soundType = player.level().getBlockState(target).getSoundType(player.level(), target, player);
            player.connection.send(new ClientboundSoundPacket(
                    BuiltInRegistries.SOUND_EVENT.wrapAsHolder(soundType.getPlaceSound()), SoundSource.BLOCKS,
                    target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5,
                    (soundType.getVolume() + 1.0F) / 2.0F, soundType.getPitch() * 0.8F,
                    player.getRandom().nextLong()));
        }
    }
    public static void adjust(ServerPlayer player, BlockPos requested, int operation) {
        if (!enabled(player) || !canEdit(player) || operation < -1 || operation > 1) return;
        if (!(player.pick(player.blockInteractionRange(), 0, false) instanceof BlockHitResult hit)
                || !hit.getBlockPos().equals(requested)) return;
        if (!player.level().getBlockState(requested).is(WallGuns.BLOCK.get())
                || !player.level().mayInteract(player, requested)
                || !player.mayUseItemAt(requested, hit.getDirection(), player.getMainHandItem())) return;
        if (!(player.level().getBlockEntity(requested) instanceof WallGunEntity gun) || gun.snapshot() == null) return;
        Direction face = gun.getBlockState().getValue(WallGunBlock.FACING);
        Vec3 towardPlayer = player.getEyePosition().subtract(Vec3.atCenterOf(requested));
        boolean back = towardPlayer.dot(Vec3.atLowerCornerOf(face.getNormal())) < 0;
        gun.adjust(operation, back);
        player.level().playSound(null, requested, net.minecraft.sounds.SoundEvents.ITEM_FRAME_ADD_ITEM,
                net.minecraft.sounds.SoundSource.BLOCKS, 1, 1);
    }
    private static boolean canEdit(ServerPlayer player) {
        GameType mode = player.gameMode.getGameModeForPlayer();
        return mode == GameType.SURVIVAL || mode == GameType.CREATIVE;
    }
}
