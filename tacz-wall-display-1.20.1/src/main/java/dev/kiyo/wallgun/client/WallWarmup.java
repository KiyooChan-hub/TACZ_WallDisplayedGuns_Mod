package dev.kiyo.wallgun.client;

import dev.kiyo.wallgun.*;
import net.minecraft.client.Minecraft;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraftforge.client.event.*;
import net.minecraftforge.fml.ModList;
import java.util.*;

/** Frame-budgeted preparation; initial discovery is driven by the server manifest. */
public final class WallWarmup {
    private static final Map<BlockPos,WallGunEntity> TRACKED=new LinkedHashMap<>();
    private static final Set<GunSnapshot> PENDING=new LinkedHashSet<>();
    private static ClientLevel world;

    public static int frameBakes,frameUploads;
    public static boolean loading() {return WallGunConfig.preloading() && LoadingClient.waiting() && !LoadingClient.skipping();}
    public static int pendingModels() {return PENDING.size();}
    public static int tracked() {return TRACKED.size();}
    public static void reset() {TRACKED.clear();PENDING.clear();world=null;}
    private static void ensureWorld() {
        var mc=Minecraft.getInstance();var current=mc.gameMode==null?null:mc.level;
        // A respawn packet briefly replaces ClientLevel while retaining the same connection.
        // Keep the departing renderer until the new level exists; it cannot render while mc.level is null.
        if(current==null && mc.getConnection()!=null)return;
        if(current==world)return;
        reset();WallBatches.switchWorld(current);world=current;
        GunMeshes.retain(Set.of());
    }
    public static void request(WallGunEntity gun) {
        ensureWorld();
        if(gun.getLevel()!=world || gun.isRemoved() || gun.snapshot()==null)return;
        TRACKED.put(gun.getBlockPos(),gun);
        if(loading() && GunMeshes.peek(gun.snapshot())==null)PENDING.add(gun.snapshot());
    }
    public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
        if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        ensureWorld();
    }
    private static boolean nearby(WallGunEntity gun) {
        var player=Minecraft.getInstance().player;
        return player!=null && RenderDistanceRules.keep(gun.getBlockPos(),player.getEyePosition(),WallGunConfig.maxRenderDistance());
    }
    public static boolean initialPrepared(java.util.Set<net.minecraft.world.level.ChunkPos> chunks) {
        if(world==null)return false;
        for(var pos:chunks) {
            var chunk=world.getChunkSource().getChunk(pos.x,pos.z,ChunkStatus.FULL,false);
            if(chunk==null)return false;
            for(var entity:chunk.getBlockEntities().values())if(entity instanceof WallGunEntity gun && nearby(gun) && gun.snapshot()!=null) {
                var mesh=GunMeshes.peek(gun.snapshot());
                if(mesh!=null && mesh.missing()){LoadingClient.rejectMissingModel();return false;}
                if(mesh==null || !WallBatches.prepared(gun))return false;
            }
        }
        return !ModList.get().isLoaded("create") || CreateMovingGuns.prepared();
    }
    public static void onDemand(WallGunEntity gun) {
        request(gun);
        if(!WallGunConfig.preloading() && gun.snapshot()!=null && GunMeshes.peek(gun.snapshot())==null) {GunMeshes.get(gun.snapshot());frameBakes++;}
    }
    public static void frame(net.minecraftforge.event.TickEvent.RenderTickEvent event) {
        if(event.phase!=net.minecraftforge.event.TickEvent.Phase.START)return;
        ensureWorld();frameBakes=0;frameUploads=0;var mc=Minecraft.getInstance();
        if(world==null || mc.level!=world || mc.gameMode==null || mc.player==null || mc.getOverlay()!=null)return;
        TRACKED.values().removeIf(g->g.isRemoved() || g.getLevel()!=world || !nearby(g)
                || !world.hasChunkAt(g.getBlockPos()) || world.getBlockEntity(g.getBlockPos())!=g);
        Set<GunSnapshot> active=new HashSet<>();
        for(var gun:TRACKED.values())if(gun.snapshot()!=null)active.add(gun.snapshot());
        if(ModList.get().isLoaded("create")) {
            active.addAll(CreateMovingGuns.retainedSnapshots());
            if(loading())active.addAll(CreateMovingGuns.discover(world));
        }
        var held=PlacementClient.prewarmSnapshot();if(held!=null)active.add(held);
        GunMeshes.retain(active);
        if(WallGunConfig.preloading() && !LoadingClient.skipping()) {
            PENDING.retainAll(active);
            for(var snapshot:active)if(GunMeshes.peek(snapshot)==null)PENDING.add(snapshot);
        } else PENDING.clear();
        var budget=new WorkBudget(loading()?12_000_000:2_000_000);
        var iterator=PENDING.iterator();
        while(iterator.hasNext() && frameBakes<(loading()?8:1) && budget.start()) {
            GunMeshes.get(iterator.next());iterator.remove();frameBakes++;
        }
        for(var gun:TRACKED.values())WallBatches.enqueue(gun,LevelRenderer.getLightColor(world,gun.getBlockPos()));
        int before=WallBatches.uploads;WallBatches.prepare(budget,loading()?8:2);frameUploads=WallBatches.uploads-before;
    }
}
