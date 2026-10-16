package dev.kiyo.wallgun.client;

import dev.kiyo.wallgun.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraftforge.client.event.*;
import java.util.*;

/** Only renderer-requested models; never scans chunks or delays gameplay. */
public final class RenderQueue {
    private static final Map<BlockPos,WallGunEntity> TRACKED=new LinkedHashMap<>();
    private static final Set<GunSnapshot> PENDING=new LinkedHashSet<>();
    private static final Set<GunSnapshot> DEMANDED=new LinkedHashSet<>();
    private static ClientLevel world;
    private static final CachePolicy CACHE_POLICY=new CachePolicy();
    public static int frameBakes,frameUploads;
    public static int pendingModels(){return PENDING.size();}
    public static int tracked(){return TRACKED.size();}
    public static void reset(){TRACKED.clear();PENDING.clear();DEMANDED.clear();world=null;}
    private static void ensureWorld(){
        var current=Minecraft.getInstance().level;
        if(current!=null && CACHE_POLICY.release(WallGunConfig.cacheDecorativeGuns(),current!=world))releaseCaches();
        if(current==world)return;
        reset();world=current;WallBatches.switchWorld(current);
    }
    private static void releaseCaches(){
        WallBatches.clear();GunMeshes.clear();
        if(net.minecraftforge.fml.ModList.get().isLoaded("create"))CreateMovingGuns.clear();
        WallGuns.LOG.info("Wall gun cache released: connection retention disabled");
    }
    public static void request(WallGunEntity gun){
        ensureWorld();
        if(world==null || gun.getLevel()!=world || gun.isRemoved() || gun.snapshot()==null)return;
        TRACKED.put(gun.getBlockPos(),gun);
    }
    public static void demand(GunSnapshot snapshot){
        ensureWorld();
        if(snapshot!=null && world!=null)DEMANDED.add(snapshot);
    }
    public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent event){if(event.phase==net.minecraftforge.event.TickEvent.Phase.END)ensureWorld();}
    public static void frame(net.minecraftforge.event.TickEvent.RenderTickEvent event){
        if(event.phase!=net.minecraftforge.event.TickEvent.Phase.START)return;
        ensureWorld();frameBakes=0;frameUploads=0;
        var mc=Minecraft.getInstance();
        if(world==null || mc.player==null || mc.gameMode==null || mc.getOverlay()!=null)return;
        TRACKED.values().removeIf(g->g.isRemoved() || g.getLevel()!=world
            || !world.hasChunkAt(g.getBlockPos()) || world.getBlockEntity(g.getBlockPos())!=g
            || !RenderDistanceRules.keep(g.getBlockPos(),mc.player.getEyePosition(),WallGunConfig.maxRenderDistance()));
        Set<GunSnapshot> active=new LinkedHashSet<>(DEMANDED);DEMANDED.clear();
        for(var gun:TRACKED.values())if(gun.snapshot()!=null)active.add(gun.snapshot());
        PENDING.retainAll(active);
        for(var snapshot:active)if(GunMeshes.needsPreparation(snapshot,true))PENDING.add(snapshot);
        var budget=new WorkBudget(2_000_000);
        var iterator=PENDING.iterator();
        while(iterator.hasNext() && frameBakes<1 && budget.start()){
            GunMeshes.prepare(iterator.next(),true);iterator.remove();frameBakes++;
        }
        for(var gun:TRACKED.values())WallBatches.enqueue(gun,LevelRenderer.getLightColor(world,gun.getBlockPos()));
        int before=WallBatches.uploads;WallBatches.prepare(budget);frameUploads=WallBatches.uploads-before;
    }
}
