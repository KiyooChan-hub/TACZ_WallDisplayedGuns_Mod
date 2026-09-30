package dev.kiyo.wallgun;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.ChunkPos;

import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import java.util.*;

/** Main-server-thread state only. Network/terrain delivery continues while gameplay is suspended. */
public final class LoadingSessions {
    private static final Map<ServerPlayer,Session> WAITING=new IdentityHashMap<>();
    private static long nextId;
    public static final long TIMEOUT_NANOS=300_000_000_000L;
    private static final long OFFER_RETRY_NANOS=2_000_000_000L;
    private static final class Session {
        final long id=++nextId, started=System.nanoTime();
        long lastOffer;
        ServerGamePacketListenerImpl connection;
        String dimension;
        boolean offered,configured,enabled,sealed,verifying,failed,placementDisabled;
        int radius=8,cursor,revision,retries,bytes;
        long scanNanos;
        List<ChunkPos> chunks;
        final Map<ChunkPos,CompoundTag> manifest=new LinkedHashMap<>();
    }
    public static void init() {
        NeoForge.EVENT_BUS.addListener(LoadingSessions::tick);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event)->WAITING.clear());
    }
    public static void arm(ServerPlayer player) {
        WAITING.put(player,new Session());
        player.setDeltaMovement(Vec3.ZERO);player.fallDistance=0;
    }
    public static boolean waiting(ServerPlayer player) {return WAITING.containsKey(player);}
    public static void markPlacementDisabled(ServerPlayer player) {
        var session=WAITING.get(player);if(session!=null)session.placementDisabled=true;
    }
    public static void fail(ServerPlayer player,String reason) {
        // Keep the guard until disconnection is processed; never fail open.
        var session=WAITING.get(player);if(session!=null)session.failed=true;
        player.connection.disconnect(Component.literal("Wall gun loading failed: "+reason));
        WallGuns.LOG.error("Wall gun loading failed for {}: {}",player.getGameProfile().getName(),reason);
    }
    private static CompoundTag packet(Session s,String kind) {
        var t=LoadingPayloads.message(kind,s.id,s.dimension);t.putInt("revision",s.revision);return t;
    }
    public static void receive(ServerPlayer player,CompoundTag tag) {
        var s=WAITING.get(player);
        if(s==null || s.failed || !s.offered || s.connection!=player.connection || s.connection.player!=player
            || s.id!=tag.getLong("id") || !s.dimension.equals(tag.getString("dimension"))
            || !s.dimension.equals(player.level().dimension().location().toString()))return;
        switch(tag.getString("kind")) {
            case "preferences" -> {
                if(s.configured)return;
                s.configured=true;s.enabled=tag.getBoolean("enabled");s.radius=Math.clamp(tag.getInt("radius"),1,32);
                if(!s.enabled)release(player,s);
            }
            case "skip" -> {
                WallGuns.LOG.warn("Wall gun initial scene skipped for {}: {}",player.getGameProfile().getName(),tag.getString("reason"));
                release(player,s);
            }
            case "refresh" -> {if(s.sealed && tag.getInt("revision")==s.revision){if(++s.retries>8){fail(player,"initial scene kept changing");return;} s.revision++;s.cursor=0;s.sealed=false;s.verifying=false;s.manifest.clear();s.bytes=0;LoadingPayloads.send(player,packet(s,"begin"));}}
            case "ready" -> {if(!s.verifying && s.sealed && tag.getInt("revision")==s.revision){s.verifying=true;s.cursor=0;}}
            case "failure" -> fail(player,"client could not prepare the initial scene");
        }
    }
    private static void release(ServerPlayer player,Session s) {
        player.setDeltaMovement(Vec3.ZERO);player.fallDistance=0;player.resetLastActionTime();
        player.connection.teleport(player.getX(),player.getY(),player.getZ(),player.getYRot(),player.getXRot());
        player.connection.resetPosition();
        var release=packet(s,"release");release.putBoolean("placementDisabled",s.placementDisabled);
        LoadingPayloads.send(player,release);WAITING.remove(player);
        WallGuns.LOG.info("Wall gun activation id={} dimension={} chunks={} waitMs={} enabled={} revisions={} scanMs={}",s.id,s.dimension,s.manifest.size(),(System.nanoTime()-s.started)/1_000_000,s.enabled,s.revision,s.scanNanos/1_000_000);
    }
    private record GunState(long pos, GunSnapshot snapshot, int roll, int mount, boolean flip,
                            net.minecraft.world.level.block.state.BlockState state) {}
    private record Description(List<GunState> states, CompoundTag tag) {}
    private static final ThreadLocal<Map<net.minecraft.world.level.chunk.LevelChunk,Description>> DESCRIPTIONS=
        ThreadLocal.withInitial(WeakHashMap::new);
    public static CompoundTag describe(net.minecraft.world.level.chunk.LevelChunk chunk) {
        // Materialize only wall-gun entities whose NBT has not been promoted yet.
        for(var pos:chunk.getBlockEntitiesPos())if(chunk.getBlockState(pos).getBlock() instanceof WallGunBlock)chunk.getBlockEntity(pos);
        var states=chunk.getBlockEntities().values().stream().filter(e->e instanceof WallGunEntity)
            .map(e->{var g=(WallGunEntity)e;return new GunState(g.getBlockPos().asLong(),g.snapshot(),g.roll(),g.mountRoll(),g.flipped(),g.getBlockState());})
            .sorted(Comparator.comparingLong(GunState::pos)).toList();
        var cache=DESCRIPTIONS.get();var previous=cache.get(chunk);
        if(previous!=null && previous.states.equals(states))return previous.tag;
        var result=new CompoundTag();var list=new ListTag();
        chunk.getBlockEntities().values().stream().filter(e->e instanceof WallGunEntity).sorted(Comparator.comparingLong(e->e.getBlockPos().asLong())).forEach(e->{
            var gun=(WallGunEntity)e;var t=new CompoundTag();t.putLong("pos",gun.getBlockPos().asLong());
            t.putInt("roll",gun.roll());t.putInt("mount",gun.mountRoll());t.putBoolean("flip",gun.flipped());
            t.putString("facing",gun.getBlockState().getValue(WallGunBlock.FACING).getName());
            if(gun.snapshot()!=null)t.put("gun",gun.snapshot().copyGun().save(chunk.getLevel().registryAccess()));
            list.add(t);
        });
        result.putLong("chunk",chunk.getPos().toLong());result.put("guns",list);cache.put(chunk,new Description(states,result));return result;
    }
    private static void tick(ServerTickEvent.Post event) {
        for(var player:new ArrayList<>(WAITING.keySet())) {
            var s=WAITING.get(player);
            if(player.connection==null)continue;
            if(!player.connection.isAcceptingMessages() || player.connection.player!=player || player.isRemoved()) {WAITING.remove(player);continue;}
            if(s.failed)continue;
            long now=System.nanoTime();
            if(now-s.started>TIMEOUT_NANOS){fail(player,"300 second session deadline exceeded");continue;}
            player.setDeltaMovement(Vec3.ZERO);player.fallDistance=0;player.resetLastActionTime();
            if(!s.offered) {
                s.offered=true;s.connection=player.connection;s.dimension=player.level().dimension().location().toString();s.lastOffer=now;
                LoadingPayloads.send(player,packet(s,"offer"));continue;
            }
            if(!s.configured) {
                if(now-s.lastOffer>=OFFER_RETRY_NANOS){s.lastOffer=now;LoadingPayloads.send(player,packet(s,"offer"));}
                continue;
            }
            if(!s.enabled)continue;
            if(s.chunks==null) {
                if(!(player.getChunkTrackingView() instanceof ChunkTrackingView.Positioned view) || !view.center().equals(player.chunkPosition()))continue;
                s.chunks=new ArrayList<>();var center=player.chunkPosition();
                view.forEach(pos->{if(Math.abs(pos.x-center.x)<=s.radius && Math.abs(pos.z-center.z)<=s.radius)s.chunks.add(pos);});
                LoadingPayloads.send(player,packet(s,"begin"));
            }
            try {
                long scanStarted=System.nanoTime();
                int remaining=s.chunks.size()-s.cursor;
                while(s.cursor<s.chunks.size() && remaining-->0 && System.nanoTime()-scanStarted<4_000_000L && (!s.sealed || s.verifying)) {
                    var pos=s.chunks.get(s.cursor);
                    if(!player.getChunkTrackingView().contains(pos)){fail(player,"initial chunk subscription changed");break;}
                    var full=player.serverLevel().getChunkSource().getChunkNow(pos.x,pos.z);
                    if(full==null || player.connection.chunkSender.isPending(pos.toLong())) {
                        // Visit each pending position once per tick; a slow chunk must not block ready ones.
                        s.chunks.remove(s.cursor);s.chunks.add(pos);continue;
                    }
                    var description=describe(full);
                    if(s.verifying) {
                        if(!description.equals(s.manifest.get(pos))) {
                            if(++s.retries>8){fail(player,"initial scene kept changing");break;}
                            s.revision++;s.cursor=0;s.sealed=false;s.verifying=false;s.manifest.clear();s.bytes=0;
                            LoadingPayloads.send(player,packet(s,"begin"));break;
                        }
                    } else {
                        if(description.getList("guns",Tag.TAG_COMPOUND).size()>256) {fail(player,"more than 256 guns in one initial chunk");break;}
                        var buffer=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
                        try {buffer.writeNbt(description);s.bytes+=buffer.readableBytes();if(buffer.readableBytes()>524288 || s.bytes>33_554_432){fail(player,"initial description exceeds chunk/session byte limit");break;}}finally{buffer.release();}
                        s.manifest.put(pos,description);
                        var message=packet(s,"chunk");message.put("data",description);LoadingPayloads.send(player,message);
                    }
                    s.cursor++;
                }
                s.scanNanos+=System.nanoTime()-scanStarted;
                if(s.cursor==s.chunks.size()) {
                    if(s.verifying)release(player,s);
                    else if(!s.sealed){s.sealed=true;var done=packet(s,"seal");done.putInt("count",s.chunks.size());LoadingPayloads.send(player,done);}
                }
            }catch(RuntimeException ex){WallGuns.LOG.error("Could not prepare initial wall gun set",ex);fail(player,"invalid initial scene");}
        }
    }
}
