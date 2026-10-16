package dev.kiyo.wallgun.client;

import dev.kiyo.wallgun.*;
import net.minecraft.client.Minecraft;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.fml.ModList;
import java.util.*;

/** Frame-budgeted preparation; initial discovery is driven by the server manifest. */
public final class WallWarmup {
    private static final Map<BlockPos,WallGunEntity> TRACKED=new LinkedHashMap<>();
    private static final Set<GunSnapshot> PENDING=new LinkedHashSet<>();
    private static ClientLevel world;
    private static final Set<GunSnapshot> DEMANDED=new LinkedHashSet<>();

    public static int frameBakes,frameUploads;
    public static boolean loading() {return WallGunConfig.preloading() && LoadingClient.waiting() && !LoadingClient.skipping();}
    public static int pendingModels() {return PENDING.size();}
    public static int tracked() {return TRACKED.size();}
    public static void reset() {TRACKED.clear();PENDING.clear();DEMANDED.clear();world=null;}
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
    }
    public static void tick(ClientTickEvent.Post event) {
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
    public static boolean inInitialRange(BlockPos pos) {
        var player=Minecraft.getInstance().player;
        return player!=null && dev.kiyo.wallgun.PreloadScope.contains(player.chunkPosition().x,player.chunkPosition().z,
            pos.getX()>>4,pos.getZ()>>4,WallGunConfig.preloadRadiusChunks());
    }
    public static void demand(GunSnapshot snapshot) {if(snapshot!=null)DEMANDED.add(snapshot);}
    public static void onDemand(WallGunEntity gun) {
        request(gun);
    }
    public static void frame(RenderFrameEvent.Pre event) {
        LoadingClient.pulse();
        ensureWorld();frameBakes=0;frameUploads=0;var mc=Minecraft.getInstance();
        if(world==null || mc.level!=world || mc.gameMode==null || mc.player==null || mc.getOverlay()!=null)return;
        TRACKED.values().removeIf(g->g.isRemoved() || g.getLevel()!=world || !nearby(g)
                || !world.hasChunkAt(g.getBlockPos()) || world.getBlockEntity(g.getBlockPos())!=g);
        Set<GunSnapshot> active=new LinkedHashSet<>();
        for(var gun:TRACKED.values())if(gun.snapshot()!=null && (!LoadingClient.waiting() || inInitialRange(gun.getBlockPos())))active.add(gun.snapshot());
        if(ModList.get().isLoaded("create")) {
            if(!LoadingClient.waiting())active.addAll(CreateMovingGuns.retainedSnapshots());
            if(loading())active.addAll(CreateMovingGuns.discover(world));
        }
        if(!LoadingClient.waiting()){active.addAll(DEMANDED);var held=PlacementClient.prewarmSnapshot();if(held!=null)active.add(held);}
        DEMANDED.clear();
        GunMeshes.retain(active);
        if(!LoadingClient.waiting() || loading()) {
            PENDING.retainAll(active);
            for(var snapshot:active)if(GunMeshes.needsPreparation(snapshot,!LoadingClient.waiting()))PENDING.add(snapshot);
        } else PENDING.clear();
        var budget=new WorkBudget(loading()?12_000_000:2_000_000);
        var iterator=PENDING.iterator();
        while(iterator.hasNext() && frameBakes<(loading()?8:1) && budget.start()) {
            GunMeshes.prepare(iterator.next(),!LoadingClient.waiting());iterator.remove();frameBakes++;
            LoadingClient.pulse();if(LoadingClient.skipping())break;
        }
        for(var gun:TRACKED.values())if(!LoadingClient.waiting() || (loading() && inInitialRange(gun.getBlockPos())))WallBatches.enqueue(gun,LevelRenderer.getLightColor(world,gun.getBlockPos()));
        int before=WallBatches.uploads;WallBatches.prepare(budget,loading()?8:2);frameUploads=WallBatches.uploads-before;
    }
}
