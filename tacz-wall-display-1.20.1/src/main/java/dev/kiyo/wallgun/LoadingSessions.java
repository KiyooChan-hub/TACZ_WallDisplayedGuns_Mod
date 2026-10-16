package dev.kiyo.wallgun;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.ChunkPos;

import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import java.util.*;

/** Main-server-thread state only. Network/terrain delivery continues while gameplay is suspended. */
public final class LoadingSessions {
    private static final Map<ServerPlayer,Session> WAITING=new IdentityHashMap<>();
    private record Completion(ServerGamePacketListenerImpl connection,CompoundTag packet) {}
    private static final Map<ServerPlayer,Completion> COMPLETED=new IdentityHashMap<>();
    private static long nextId;
    public static final long ACTIVATION_SKIP_NANOS=60_000_000_000L;
    private static final long OFFER_RETRY_NANOS=2_000_000_000L;
    private static final class Session {
        final long id=++nextId, started=System.nanoTime();
        final DeferredGunDraw gunDraw=new DeferredGunDraw();
        boolean endingForKill,restoredDraw;
        long lastOffer,lastSkipInstruction;
        ServerGamePacketListenerImpl connection;
        String dimension;
        boolean offered,configured,enabled,sealed,verifying,failed,placementDisabled,deadlineReached;
        int radius=2,cursor,revision,retries,bytes,unavailable;
        long lastDiagnostic;
        String fallbackReason="";
        long scanNanos;
        List<ChunkPos> chunks;
        final Map<ChunkPos,CompoundTag> manifest=new LinkedHashMap<>();
    }
    public static void init() {
        MinecraftForge.EVENT_BUS.addListener(LoadingSessions::tick);
        MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent event)->{WAITING.clear();COMPLETED.clear();});
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event)->{
            if(event.getEntity() instanceof ServerPlayer player){WAITING.remove(player);COMPLETED.remove(player);}
        });
    }
    public static void arm(ServerPlayer player) {
        arm(player,player.level().dimension().location().toString());
    }
    public static void arm(ServerPlayer player,String destination) {
        var session=new Session();session.dimension=destination;
        var previous=WAITING.put(player,session);
        if(previous!=null){session.placementDisabled=previous.placementDisabled;if(previous.gunDraw.pending())session.gunDraw.request();}
        WallGuns.LOG.info("Wall gun activation armed id={} destination={} player={} position={} replaced={}",session.id,destination,player.getGameProfile().getName(),player.blockPosition(),previous==null?0:previous.id);
        player.setDeltaMovement(Vec3.ZERO);player.fallDistance=0;
    }
    public static void commandKill(ServerPlayer player) {
        var session=WAITING.get(player);
        if(session==null)return;
        if(!session.offered && player.connection!=null) {
            session.offered=true;session.connection=player.connection;
            LoadingPayloads.send(player,packet(session,"offer"));
        }
        session.endingForKill=true;release(player,session);
        // Vanilla also protects a player until the dimension teleport is acknowledged.
        player.hasChangedDimension();
    }
    public static boolean waiting(ServerPlayer player) {return WAITING.containsKey(player);}
    public static void deferGunDraw(ServerPlayer player) {
        var session=WAITING.get(player);if(session!=null)session.gunDraw.request();
    }
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
        if(tag.getString("kind").equals("status")) {
            String dimension=player.level().dimension().location().toString();
            if(!dimension.equals(tag.getString("dimension")))return;
            boolean placementDisabled=false,restoredDraw=false;
            if(s!=null && !s.failed && s.dimension.equals(dimension) && s.id<=tag.getLong("retired")) {
                placementDisabled=s.placementDisabled;
                release(player,s);restoredDraw=s.restoredDraw;s=WAITING.get(player);
            }
            if(s==null) {
                var active=LoadingPayloads.message("active",0,dimension);
                active.putBoolean("placementDisabled",placementDisabled);
                active.putBoolean("restoredDraw",restoredDraw);
                active.putLong("generation",tag.getLong("generation"));LoadingPayloads.send(player,active);
            } else if(s.offered && s.connection==player.connection && s.dimension.equals(dimension)) {
                LoadingPayloads.send(player,packet(s,"offer"));
            }
            return;
        }
        var completed=COMPLETED.get(player);
        if(completed!=null && completed.connection==player.connection
            && completed.packet.getLong("id")==tag.getLong("id")
            && completed.packet.getString("dimension").equals(tag.getString("dimension"))) {
            LoadingPayloads.send(player,completed.packet.copy());return;
        }
        if(s==null || s.failed || !s.offered || s.connection!=player.connection || s.connection.player!=player
            || s.id!=tag.getLong("id") || !s.dimension.equals(tag.getString("dimension"))
            || !s.dimension.equals(player.level().dimension().location().toString()))return;
        switch(tag.getString("kind")) {
            case "preferences" -> {
                if(s.configured)return;
                s.configured=true;s.enabled=tag.getBoolean("enabled");s.radius=Math.max(0,Math.min(8,tag.getInt("radius")));
                if(!s.enabled || s.deadlineReached || System.nanoTime()-s.started>ACTIVATION_SKIP_NANOS)release(player,s);
            }
            case "skip", "resume" -> {
                WallGuns.LOG.warn("Wall gun initial scene skipped for {}: {}",player.getGameProfile().getName(),tag.getString("reason"));
                release(player,s);
            }
            case "refresh" -> {if(s.sealed && tag.getInt("revision")==s.revision){if(++s.retries>8){defer(player,s,"initial scene kept changing");return;} s.revision++;s.cursor=0;s.sealed=false;s.verifying=false;s.manifest.clear();s.bytes=0;LoadingPayloads.send(player,packet(s,"begin"));}}
            case "ready" -> {if(!s.verifying && s.sealed && tag.getInt("revision")==s.revision){s.verifying=true;s.chunks=new ArrayList<>(s.manifest.keySet());s.cursor=0;}}
            case "failure" -> release(player,s);
        }
    }
    private static void defer(ServerPlayer player,Session s,String reason) {
        s.deadlineReached=true;s.fallbackReason=reason;
        WallGuns.LOG.warn("Wall gun preparation deferred id={} dimension={} reason={}",s.id,s.dimension,reason);
        var skip=packet(s,"skip");skip.putString("reason",reason);LoadingPayloads.send(player,skip);
        s.lastSkipInstruction=System.nanoTime();
    }
    private static void release(ServerPlayer player,Session s) {
        if(WAITING.get(player)!=s)return;
        if(player.connection!=null && !s.endingForKill)try {
            var operator=com.tacz.guns.api.entity.IGunOperator.fromLivingEntity(player);
            boolean missing=operator.getDataHolder().currentGunItem==null
                && com.tacz.guns.api.item.IGun.getIGunOrNull(player.getMainHandItem())!=null;
            // Match TaCZ's draw handler: bind the final server-selected slot, not a saved stack.
            if(s.gunDraw.finish(missing,()->{
                var inventory=player.getInventory();int slot=inventory.selected;
                operator.draw(()->inventory.getItem(slot));
            })){s.restoredDraw=true;WallGuns.LOG.info("Wall gun restored held-gun initialization id={} slot={}",s.id,player.getInventory().selected);}
        }catch(RuntimeException ex){
            WallGuns.LOG.error("Could not restore held-gun initialization",ex);
            fail(player,"held-gun initialization failed");return;
        }
        // Retire all protection before any acknowledgement can restore client controls.
        if(!WAITING.remove(player,s))return;
        if(player.connection==null){COMPLETED.remove(player);return;}
        var release=packet(s,"release");release.putBoolean("placementDisabled",s.placementDisabled);
        release.putBoolean("restoredDraw",s.restoredDraw);
        COMPLETED.put(player,new Completion(player.connection,release.copy()));
        player.setDeltaMovement(Vec3.ZERO);player.fallDistance=0;player.resetLastActionTime();
        player.connection.teleport(player.getX(),player.getY(),player.getZ(),player.getYRot(),player.getXRot());
        player.connection.resetPosition();
        LoadingPayloads.send(player,release);
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
            if(gun.snapshot()!=null)t.put("gun",gun.snapshot().copyGun().save(new CompoundTag()));
            list.add(t);
        });
        result.putLong("chunk",chunk.getPos().toLong());result.put("guns",list);cache.put(chunk,new Description(states,result));return result;
    }
    private static void tick(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        COMPLETED.entrySet().removeIf(entry->entry.getKey().isRemoved()
            || entry.getKey().connection!=entry.getValue().connection
            || entry.getValue().connection.player!=entry.getKey()
            || !entry.getValue().connection.isAcceptingMessages());
        for(var player:new ArrayList<>(WAITING.keySet())) {
            var s=WAITING.get(player);
            if(player.connection==null)continue;
            if(!player.connection.isAcceptingMessages() || player.connection.player!=player || player.isRemoved()) {WAITING.remove(player);COMPLETED.remove(player);continue;}
            if(s.failed)continue;
            if(!s.dimension.equals(player.level().dimension().location().toString())
                || (s.offered && s.connection!=player.connection)) {
                WallGuns.LOG.warn("Replacing stale wall gun activation id={} for {}",s.id,player.getGameProfile().getName());
                arm(player);continue;
            }
            long now=System.nanoTime();
            player.setDeltaMovement(Vec3.ZERO);player.fallDistance=0;player.resetLastActionTime();
            if(!s.deadlineReached && now-s.started>ACTIVATION_SKIP_NANOS) {
                s.deadlineReached=true;s.fallbackReason="activation exceeded 60 seconds";
                WallGuns.LOG.warn("Wall gun activation exceeded 60 seconds for {}; waiting for client before safe release",player.getGameProfile().getName());
            }
            if(now-s.lastDiagnostic>=5_000_000_000L) {
                s.lastDiagnostic=now;
                WallGuns.LOG.info("Wall gun server progress id={} dimension={} stage={} elapsedMs={} cursor={} chunks={} pending={} revision={}",
                    s.id,s.dimension,!s.configured?"preferences":s.deadlineReached?"client-release-ack":s.verifying?"verification":s.sealed?"client-ready":"scan",
                    (now-s.started)/1_000_000,s.cursor,s.chunks==null?0:s.chunks.size(),s.chunks==null?0:s.chunks.size()-s.cursor,s.revision);
            }
            if(!s.offered) {
                s.offered=true;s.connection=player.connection;s.lastOffer=now;
                LoadingPayloads.send(player,packet(s,"offer"));continue;
            }
            if(!s.configured) {
                if(now-s.lastOffer>=OFFER_RETRY_NANOS){s.lastOffer=now;LoadingPayloads.send(player,packet(s,"offer"));}
                continue;
            }
            if(s.deadlineReached) {
                if(now-s.lastSkipInstruction>=OFFER_RETRY_NANOS) {
                    s.lastSkipInstruction=now;var skip=packet(s,"skip");skip.putString("reason",s.fallbackReason);LoadingPayloads.send(player,skip);
                }
                continue;
            }
            if(!s.enabled)continue;
            if(s.chunks==null) {
                s.chunks=new ArrayList<>();var center=player.chunkPosition();
                for(int z=center.z-s.radius;z<=center.z+s.radius;z++)for(int x=center.x-s.radius;x<=center.x+s.radius;x++)
                    s.chunks.add(new ChunkPos(x,z));
                WallGuns.LOG.info("Wall gun scan id={} dimension={} center={} radius={} candidates={}",s.id,s.dimension,center,s.radius,s.chunks.size());
                LoadingPayloads.send(player,packet(s,"begin"));
            }
            try {
                long scanStarted=System.nanoTime();
                int remaining=s.chunks.size()-s.cursor;
                while(s.cursor<s.chunks.size() && remaining-->0 && System.nanoTime()-scanStarted<4_000_000L && (!s.sealed || s.verifying)) {
                    var pos=s.chunks.get(s.cursor);

                    var full=player.serverLevel().getChunkSource().getChunkNow(pos.x,pos.z);
                    if(full==null || !player.serverLevel().getChunkSource().chunkMap.getPlayers(pos,false).contains(player)) {
                        s.unavailable++;ChunkScan.retryLater(s.chunks,s.cursor);continue; // Unknown is not empty; retry without generating chunks.
                    }
                    var description=describe(full);
                    if(!s.verifying && description.getList("guns",Tag.TAG_COMPOUND).isEmpty()){s.cursor++;continue;}
                    if(s.verifying) {
                        if(!description.equals(s.manifest.get(pos))) {
                            if(++s.retries>8){defer(player,s,"initial scene kept changing");break;}
                            s.revision++;s.cursor=0;s.sealed=false;s.verifying=false;s.manifest.clear();s.bytes=0;
                            LoadingPayloads.send(player,packet(s,"begin"));break;
                        }
                    } else {
                        if(description.getList("guns",Tag.TAG_COMPOUND).size()>256) {defer(player,s,"more than 256 guns in one initial chunk");break;}
                        var buffer=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
                        try {buffer.writeNbt(description);s.bytes+=buffer.readableBytes();if(buffer.readableBytes()>524288 || s.bytes>33_554_432){defer(player,s,"initial description exceeds chunk/session byte limit");break;}}finally{buffer.release();}
                        s.manifest.put(pos,description);
                        var message=packet(s,"chunk");message.put("data",description);LoadingPayloads.send(player,message);
                    }
                    s.cursor++;
                }
                s.scanNanos+=System.nanoTime()-scanStarted;
                if(!s.deadlineReached && ChunkScan.complete(s.chunks,s.cursor)) {
                    if(s.verifying)release(player,s);
                    else if(!s.sealed){s.sealed=true;var done=packet(s,"seal");done.putInt("count",s.manifest.size());WallGuns.LOG.info("Wall gun manifest id={} chunks={} notReadyChecks={} bytes={} scanMs={}",s.id,s.manifest.size(),s.unavailable,s.bytes,s.scanNanos/1_000_000);LoadingPayloads.send(player,done);}
                }
            }catch(RuntimeException ex){WallGuns.LOG.error("Could not prepare initial wall gun set",ex);defer(player,s,"invalid initial scene");}
        }
    }
}
