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

/** Non-blocking client scheduling. Never cancels a screen close or controls server simulation. */
public final class WallWarmup {
    private static final Map<BlockPos,WallGunEntity> TRACKED=new LinkedHashMap<>();
    private static final Set<GunSnapshot> PENDING=new LinkedHashSet<>();
    private static ClientLevel world;
    private static int scanCursor;
    public static int frameBakes,frameUploads;
    public static boolean loading() {return WallGunConfig.preloading() && Minecraft.getInstance().screen instanceof ReceivingLevelScreen;}
    public static int pendingModels() {return PENDING.size();}
    public static int tracked() {return TRACKED.size();}
    public static void reset() {TRACKED.clear();PENDING.clear();world=null;scanCursor=0;}
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
        if(WallGunConfig.preloading() && GunMeshes.peek(gun.snapshot())==null)PENDING.add(gun.snapshot());
    }
    public static void tick(ClientTickEvent.Post event) {
        ensureWorld();var mc=Minecraft.getInstance();
        if(!WallGunConfig.preloading() || world==null || mc.level!=world || mc.gameMode==null || mc.player==null || mc.getOverlay()!=null)return;
        // Only inspect a bounded slice of chunks already sent by the server. No tickets or generation.
        int radius=RenderDistanceRules.scanRadius(WallGunConfig.maxRenderDistance(),mc.options.getEffectiveRenderDistance());
        int cx=mc.player.chunkPosition().x,cz=mc.player.chunkPosition().z;
        int side=radius*2+1,total=side*side,count=Math.min(loading()?128:64,total);
        for(int i=0;i<count;i++) {
            int index=(scanCursor+i)%total;
            var chunk=world.getChunkSource().getChunk(cx-radius+index%side,cz-radius+index/side,ChunkStatus.FULL,false);
            if(chunk!=null)for(var entity:chunk.getBlockEntities().values())if(entity instanceof WallGunEntity gun && nearby(gun))request(gun);
        }
        scanCursor=(scanCursor+count)%total;
    }
    private static boolean nearby(WallGunEntity gun) {
        var player=Minecraft.getInstance().player;
        return player!=null && RenderDistanceRules.keep(gun.getBlockPos(),player.getEyePosition(),WallGunConfig.maxRenderDistance());
    }
    public static void onDemand(WallGunEntity gun) {
        request(gun);
        if(!WallGunConfig.preloading() && gun.snapshot()!=null && GunMeshes.peek(gun.snapshot())==null) {GunMeshes.get(gun.snapshot());frameBakes++;}
    }
    public static void frame(RenderFrameEvent.Pre event) {
        ensureWorld();frameBakes=0;frameUploads=0;var mc=Minecraft.getInstance();
        if(world==null || mc.level!=world || mc.gameMode==null || mc.player==null || mc.getOverlay()!=null)return;
        TRACKED.values().removeIf(g->g.isRemoved() || g.getLevel()!=world || !nearby(g)
                || !world.hasChunkAt(g.getBlockPos()) || world.getBlockEntity(g.getBlockPos())!=g);
        Set<GunSnapshot> active=new HashSet<>();
        for(var gun:TRACKED.values())if(gun.snapshot()!=null)active.add(gun.snapshot());
        if(ModList.get().isLoaded("create")) {
            active.addAll(CreateMovingGuns.retainedSnapshots());
            if(WallGunConfig.preloading())active.addAll(CreateMovingGuns.discover(world));
        }
        GunMeshes.retain(active);
        if(WallGunConfig.preloading()) {
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
