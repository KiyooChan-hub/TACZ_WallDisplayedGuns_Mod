package dev.kiyo.wallgun;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class PlacementPayloads {
    public static Runnable clientModeDisabled=()->{};
    public record ModeDisabled(boolean disabled) implements CustomPacketPayload {
        public static final Type<ModeDisabled> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(WallGuns.ID,"mode_disabled"));
        public static final StreamCodec<ByteBuf,ModeDisabled> CODEC=StreamCodec.composite(ByteBufCodecs.BOOL,ModeDisabled::disabled,ModeDisabled::new);
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public static void disableAfterDimensionChange(ServerPlayer player){
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,new ModeDisabled(true));
    }
    private PlacementPayloads() {}
    public record Mode(boolean enabled) implements CustomPacketPayload {
        public static final Type<Mode> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(WallGuns.ID, "placement_mode"));
        public static final StreamCodec<ByteBuf, Mode> CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, Mode::enabled, Mode::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record Place(BlockPos pos, Direction face) implements CustomPacketPayload {
        public static final Type<Place> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(WallGuns.ID, "place_gun"));
        public static final StreamCodec<ByteBuf, Place> CODEC = StreamCodec.composite(BlockPos.STREAM_CODEC, Place::pos, Direction.STREAM_CODEC, Place::face, Place::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    /** 0 flips; +1 turns the muzzle upward; -1 turns it downward. */
    public record Adjust(BlockPos pos, int operation) implements CustomPacketPayload {
        public static final Type<Adjust> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(WallGuns.ID, "adjust_gun"));
        public static final StreamCodec<ByteBuf, Adjust> CODEC = StreamCodec.composite(BlockPos.STREAM_CODEC, Adjust::pos, ByteBufCodecs.VAR_INT, Adjust::operation, Adjust::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("2");
        registrar.playToClient(ModeDisabled.TYPE,ModeDisabled.CODEC,(payload,context)->clientModeDisabled.run());
        registrar.playToServer(Mode.TYPE, Mode.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) GunPlacement.set(player, payload.enabled());
        });
        registrar.playToServer(Place.TYPE, Place.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player)
                GunPlacement.place(player, new BlockHitResult(Vec3.atCenterOf(payload.pos()), payload.face(), payload.pos(), false));
        });
        registrar.playToServer(Adjust.TYPE, Adjust.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) GunPlacement.adjust(player, payload.pos(), payload.operation());
        });
    }
}
