package dev.kiyo.wallgun.client;

import dev.kiyo.wallgun.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.client.event.*;
import java.util.*;

/** Only renderer-requested models; never scans chunks or delays gameplay. */
public final class RenderQueue {
    private static final Map<BlockPos,WallGunEntity> TRACKED=new LinkedHashMap<>();
    private static final Set<GunSnapshot> PENDING=new LinkedHashSet<>();
    private static final Set<GunSnapshot> DEMANDED=new LinkedHashSet<>();
    private static ClientLevel world;
    public static int frameBakes,frameUploads;
    public static int pendingModels(){return PENDING.size();}
    public static int tracked(){return TRACKED.size();}
    public static void reset(){TRACKED.clear();PENDING.clear();DEMANDED.clear();world=null;}
    private static void ensureWorld(){
        var current=Minecraft.getInstance().level;
        if(current==world)return;
        reset();world=current;WallBatches.switchWorld(current);
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
    public static void tick(ClientTickEvent.Post event){ensureWorld();}
    public static void frame(RenderFrameEvent.Pre event){
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
