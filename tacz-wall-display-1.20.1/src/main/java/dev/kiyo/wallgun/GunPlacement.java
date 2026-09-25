package dev.kiyo.wallgun;

import com.tacz.guns.api.item.IGun;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Server-owned transient placement mode; packets never contain the source gun. */
public final class GunPlacement {
    private static final Set<UUID> ENABLED=ConcurrentHashMap.newKeySet();
    private GunPlacement() {}
    public static void init(){MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e)->ENABLED.remove(e.getEntity().getUUID()));}
    public static boolean enabled(Player player){return player!=null && ENABLED.contains(player.getUUID());}
    public static void set(ServerPlayer player,boolean enabled){if(enabled)ENABLED.add(player.getUUID());else ENABLED.remove(player.getUUID());}
    public static void place(ServerPlayer player,BlockHitResult requested){
        if(!enabled(player)||!canEdit(player))return;
        var gun=player.getMainHandItem();if(IGun.getIGunOrNull(gun)==null)return;
        double reach=player.gameMode.getGameModeForPlayer()==GameType.CREATIVE?5.0:4.5;
        if(!(player.pick(reach,0,false) instanceof BlockHitResult actual)
                || !actual.getBlockPos().equals(requested.getBlockPos()) || actual.getDirection()!=requested.getDirection())return;
        BlockPlaceContext context=new BlockPlaceContext(new UseOnContext(player,InteractionHand.MAIN_HAND,actual));
        BlockPos target=context.getClickedPos();
        if(!player.level().mayInteract(player,target)||!player.mayUseItemAt(target,actual.getDirection(),gun))return;
        if(WallGuns.ITEM.get().place(context).consumesAction()){
            var sound=player.level().getBlockState(target).getSoundType(player.level(),target,player);
            player.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound.getPlaceSound()),
                    SoundSource.BLOCKS,target.getX()+.5,target.getY()+.5,target.getZ()+.5,
                    (sound.getVolume()+1)/2,sound.getPitch()*.8F,player.getRandom().nextLong()));
        }
    }
    public static void adjust(ServerPlayer player,BlockPos requested,int operation){
        if(!enabled(player)||!canEdit(player)||operation< -1||operation>1)return;
        double reach=player.gameMode.getGameModeForPlayer()==GameType.CREATIVE?5.0:4.5;
        if(!(player.pick(reach,0,false) instanceof BlockHitResult hit)||!hit.getBlockPos().equals(requested))return;
        if(!player.level().getBlockState(requested).is(WallGuns.BLOCK.get())
                ||!player.level().mayInteract(player,requested)
                ||!player.mayUseItemAt(requested,hit.getDirection(),player.getMainHandItem()))return;
        if(!(player.level().getBlockEntity(requested) instanceof WallGunEntity gun)||gun.snapshot()==null)return;
        Direction face=gun.getBlockState().getValue(WallGunBlock.FACING);
        Vec3 toward=player.getEyePosition().subtract(Vec3.atCenterOf(requested));
        boolean back=toward.dot(Vec3.atLowerCornerOf(face.getNormal()))<0;
        gun.adjust(operation,back);
        player.level().playSound(null,requested,net.minecraft.sounds.SoundEvents.ITEM_FRAME_ADD_ITEM,SoundSource.BLOCKS,1,1);
    }
    private static boolean canEdit(ServerPlayer player){GameType mode=player.gameMode.getGameModeForPlayer();return mode==GameType.SURVIVAL||mode==GameType.CREATIVE;}
}
