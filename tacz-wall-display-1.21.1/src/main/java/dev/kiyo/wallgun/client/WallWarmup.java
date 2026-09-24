package dev.kiyo.wallgun.client;

import dev.kiyo.wallgun.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.fml.ModList;
import java.util.*;

/** Client-thread scheduling, independent of the camera's visible block entities. */
public final class WallWarmup {
    private static final Map<BlockPos,WallGunEntity> TRACKED=new LinkedHashMap<>();
    private static final Set<GunSnapshot> PENDING=new LinkedHashSet<>();
    private static ClientLevel world;
    private static boolean loading;
    private static int tick, scans, loadedChunks, expectedChunks;
    private static long lastDiscovery, gateStarted;
    public static int completed, timedOut, frameBakes, frameUploads;
    public static double lastGateMillis;
    private static int startBakes, startUploads;

    public static boolean loading() { return loading; }
    public static int pendingModels() { return PENDING.size(); }
    public static int tracked() { return TRACKED.size(); }
    public static String progress() {
        return "装饰枪外观：" +(TRACKED.size()-missingGuns())+" / "+TRACKED.size();
    }
    private static int missingGuns() {
        int n=0; for(var gun:TRACKED.values()) if(GunMeshes.peek(gun.snapshot())==null)n++; return n;
    }
    public static void reset() {
        TRACKED.clear();PENDING.clear();world=null;loading=false;gateStarted=0;scans=0;
    }
    private static void ensureWorld() {
        var mc=Minecraft.getInstance();
        // Minecraft still renders frames while waiting for the integrated server to stop.
        var current=mc.gameMode==null?null:mc.level;
        if(current==world)return;
        reset();WallBatches.clear();world=current;
        if(world!=null) {
            loading=true;lastDiscovery=System.nanoTime();startBakes=GunMeshes.bakes;startUploads=WallBatches.uploads;
        }
    }
    public static void request(WallGunEntity gun) {
        if(gun.getLevel()!=world || gun.isRemoved() || gun.snapshot()==null)return;
        var previous=TRACKED.put(gun.getBlockPos(),gun);
        if(previous!=gun)lastDiscovery=System.nanoTime();
        if(GunMeshes.peek(gun.snapshot())==null && PENDING.add(gun.snapshot()))lastDiscovery=System.nanoTime();
    }
    public static void tick(ClientTickEvent.Post event) {
        ensureWorld();
        var mc=Minecraft.getInstance();
        if(world==null || mc.player==null || mc.getOverlay()!=null)return;
        // Scan existing client chunks only: never cause disk IO, generation or server chunk tickets.
        if(!loading && ++tick%5!=0)return;
        int radius=Math.min(7,mc.options.getEffectiveRenderDistance());
        int cx=mc.player.chunkPosition().x, cz=mc.player.chunkPosition().z;
        loadedChunks=0;expectedChunks=(radius*2+1)*(radius*2+1);
        for(int x=cx-radius;x<=cx+radius;x++)for(int z=cz-radius;z<=cz+radius;z++) {
            var chunk=world.getChunkSource().getChunk(x,z,ChunkStatus.FULL,false);
            if(chunk==null)continue;
            loadedChunks++;
            for(var entity:chunk.getBlockEntities().values())if(entity instanceof WallGunEntity gun && nearby(gun))request(gun);
        }
        scans++;
    }
    private static boolean nearby(WallGunEntity gun) {
        var player=Minecraft.getInstance().player;
        return player!=null && gun.getBlockPos().distToCenterSqr(player.getEyePosition())<=112*112;
    }
    public static void opening(ScreenEvent.Opening event) {
        if(event.getNewScreen()==null && event.getCurrentScreen() instanceof ReceivingLevelScreen) {
            ensureWorld();
            if(loading)event.setNewScreen(new WarmupScreen(null));
        }
    }
    public static void frame(RenderFrameEvent.Pre event) {
        ensureWorld();frameBakes=0;frameUploads=0;
        var mc=Minecraft.getInstance();
        if(world==null || mc.player==null || mc.getOverlay()!=null)return;
        // Also covers resource reloads and clients that replace the vanilla receiving screen.
        if(loading && !(mc.screen instanceof ReceivingLevelScreen) && !(mc.screen instanceof WarmupScreen))
            mc.setScreen(new WarmupScreen(mc.screen));
        TRACKED.values().removeIf(g -> g.isRemoved() || g.getLevel()!=world || !nearby(g)
                || !world.hasChunkAt(g.getBlockPos()) || world.getBlockEntity(g.getBlockPos())!=g);
        Set<GunSnapshot> needed=new HashSet<>();
        for(var gun:TRACKED.values())if(gun.snapshot()!=null && GunMeshes.peek(gun.snapshot())==null)needed.add(gun.snapshot());
        if (ModList.get().isLoaded("create")) for (var snapshot:CreateMovingGuns.discover(world))
            if (GunMeshes.peek(snapshot)==null) needed.add(snapshot);
        PENDING.retainAll(needed);
        PENDING.addAll(needed);
        WorkBudget budget=new WorkBudget(loading?12_000_000:2_000_000);
        var iterator=PENDING.iterator();
        while(iterator.hasNext() && frameBakes<(loading?8:1) && budget.start()) {
            GunMeshes.get(iterator.next());iterator.remove();frameBakes++;
        }
        for(var gun:TRACKED.values())WallBatches.enqueue(gun,LevelRenderer.getLightColor(world,gun.getBlockPos()));
        int before=WallBatches.uploads;
        WallBatches.prepare(budget,loading?8:2);
        frameUploads=WallBatches.uploads-before;
    }
    public static void beginGate() {
        if(gateStarted==0)gateStarted=System.nanoTime();
    }
    public static boolean gateFinished() {
        if(!loading || world==null)return true;
        long now=System.nanoTime(), elapsed=now-gateStarted;
        boolean settled=scans>0 && now-lastDiscovery>=750_000_000L
                && (loadedChunks==expectedChunks || elapsed>=2_500_000_000L);
        boolean ready=settled && PENDING.isEmpty() && missingGuns()==0 && WallBatches.pendingBatches()==0;
        if(!ready && elapsed<30_000_000_000L)return false;
        loading=false;lastGateMillis=elapsed/1_000_000.0;
        if(ready)completed++;else timedOut++;
        WallGuns.LOG.info("Wall gun warmup {}: {} ms, {} guns, {} models, {} uploads, pending models={}, batches={}",
                ready?"ready":"timeout; continuing incrementally",(long)lastGateMillis,TRACKED.size(),
                GunMeshes.bakes-startBakes,WallBatches.uploads-startUploads,PENDING.size(),WallBatches.pendingBatches());
        return true;
    }
}
