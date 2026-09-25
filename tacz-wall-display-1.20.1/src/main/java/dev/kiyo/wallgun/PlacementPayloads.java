package dev.kiyo.wallgun;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Server validates every placement and adjustment against the player's current ray trace. */
public final class PlacementPayloads {
    private static final String PROTOCOL="1";
    private static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(
            new ResourceLocation(WallGuns.ID,"placement"),()->PROTOCOL,PROTOCOL::equals,PROTOCOL::equals);
    public record Mode(boolean enabled) {}
    public record Place(BlockPos pos, Direction face) {}
    /** 0 flips; +1 raises the muzzle; -1 lowers it. */
    public record Adjust(BlockPos pos, int operation) {}
    private PlacementPayloads() {}
    public static void init() {
        CHANNEL.registerMessage(0,Mode.class,(m,b)->b.writeBoolean(m.enabled()),b->new Mode(b.readBoolean()),(m,s)->{
            var ctx=s.get();ctx.enqueueWork(()->{ServerPlayer p=ctx.getSender();if(p!=null)GunPlacement.set(p,m.enabled());});ctx.setPacketHandled(true);
        });
        CHANNEL.registerMessage(1,Place.class,(m,b)->{b.writeBlockPos(m.pos());b.writeEnum(m.face());},
                b->new Place(b.readBlockPos(),b.readEnum(Direction.class)),(m,s)->{
                    var ctx=s.get();ctx.enqueueWork(()->{ServerPlayer p=ctx.getSender();if(p!=null)GunPlacement.place(p,
                            new BlockHitResult(Vec3.atCenterOf(m.pos()),m.face(),m.pos(),false));});ctx.setPacketHandled(true);
                });
        CHANNEL.registerMessage(2,Adjust.class,(m,b)->{b.writeBlockPos(m.pos());b.writeVarInt(m.operation());},
                b->new Adjust(b.readBlockPos(),b.readVarInt()),(m,s)->{
                    var ctx=s.get();ctx.enqueueWork(()->{ServerPlayer p=ctx.getSender();if(p!=null)GunPlacement.adjust(p,m.pos(),m.operation());});ctx.setPacketHandled(true);
                });
    }
    public static void send(Object message){CHANNEL.sendToServer(message);}
}
