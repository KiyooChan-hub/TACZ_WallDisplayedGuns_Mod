package dev.kiyo.wallgun.client;

import dev.kiyo.wallgun.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.util.*;

/** The terrain screen is released only by the server's matching activation response. */
public final class LoadingClient {
    private static final ActivationGate GATE=new ActivationGate();
    private static final PreparationWindow WINDOW=new PreparationWindow();
    private static boolean sealed,sent,skipRequested,resumeRequested;
    private static Screen loadingScreen;
    private static long id,started,lastSkip,lastStatus,lastDiagnostic,lastReady,preloadBudgetNanos;
    private static String dimension="";
    private static String skipReason="";
    private static int revision,expected,failures;
    private static CompoundTag pendingOffer;
    private static final long SKIP_RETRY_NANOS=2_000_000_000L;
    private static final Map<ChunkPos,CompoundTag> CHUNKS=new LinkedHashMap<>();
    public static String stage() {
        if(skipRequested)return "server_release";
        if(id==0)return "handshake";
        if(!sealed)return "manifest";
        if(sent)return "server_verification";
        if(WallWarmup.pendingModels()>0)return "models";
        return "chunks_and_batches";
    }
    public static boolean waiting(){return GATE.waiting();}
    public static boolean readyForGameplay(){return !waiting() && id==0 && pendingOffer==null;}
    public static boolean skipping(){return skipRequested;}
    private static void clearScene(){sealed=false;sent=false;skipRequested=false;resumeRequested=false;id=0;lastSkip=lastStatus=lastDiagnostic=lastReady=0;revision=expected=0;dimension="";skipReason="";pendingOffer=null;CHUNKS.clear();}
    public static void reset(){GATE.disconnect();clearScene();loadingScreen=null;}
    public static void entering(){GATE.entering();clearScene();loadingScreen=null;startBudget();}
    private static void startBudget(){started=System.nanoTime();preloadBudgetNanos=WallGunConfig.preloadTimeoutSeconds()*1_000_000_000L;WINDOW.enter(started,preloadBudgetNanos);failures=GunMeshes.failures;}
    public static void screenOpened(){loadingScreen=Minecraft.getInstance().screen;}
    private static CompoundTag message(String kind){var t=LoadingPayloads.message(kind,id,dimension);t.putInt("revision",revision);return t;}
    public static void receive(CompoundTag tag) {
        var mc=Minecraft.getInstance();String kind=tag.getString("kind");
        if(kind.equals("active")) {
            if(mc.level!=null && mc.level.dimension().location().toString().equals(tag.getString("dimension"))
                && GATE.confirmActive(tag.getLong("generation"))) {
                clearScene();loadingScreen=null; // Vanilla decides when terrain is ready.
                if(tag.getBoolean("restoredDraw"))LoadingGunClient.restoredDraw();
                if(tag.getBoolean("placementDisabled"))PlacementClient.disableAfterDimensionChange();
            }
            return;
        }
        if(kind.equals("offer")){
            if(pendingOffer==null || tag.getLong("id")>=pendingOffer.getLong("id"))pendingOffer=tag.copy();
            acceptOffer(mc);return;
        }
        if(id==0 || id!=tag.getLong("id") || !dimension.equals(tag.getString("dimension")))return;
        // Terminal messages end the session even if the scene revision changed in flight.
        if(kind.equals("release")) {
            if(!GATE.finish(id))return;
            if(tag.getBoolean("restoredDraw"))LoadingGunClient.restoredDraw();
            WallGuns.LOG.info("Wall gun client released id={} waitMs={}",id,(System.nanoTime()-started)/1_000_000);
            boolean disablePlacement=tag.getBoolean("placementDisabled");
            clearScene();loadingScreen=null; // Do not bypass vanilla terrain readiness.
            if(disablePlacement)PlacementClient.disableAfterDimensionChange();
            return;
        }
        if(kind.equals("begin")){revision=tag.getInt("revision");CHUNKS.clear();sealed=false;sent=false;return;}
        if(kind.equals("skip")){skip(tag.getString("reason").isEmpty()?"server requested preparation fallback":tag.getString("reason"));return;}
        if(tag.getInt("revision")!=revision)return;
        switch(kind) {
            case "chunk" -> {var data=tag.getCompound("data");CHUNKS.put(new ChunkPos(data.getLong("chunk")),data);}
            case "seal" -> {expected=tag.getInt("count");sealed=true;}
        }
    }
    private static void acceptOffer(Minecraft mc) {
        if(pendingOffer==null || mc.level==null || mc.player==null || !mc.level.dimension().location().toString().equals(pendingOffer.getString("dimension")))return;
        var offer=pendingOffer;pendingOffer=null;
        long offeredId=offer.getLong("id");
        var decision=GATE.offer(offeredId);
        if(decision==ActivationGate.Offer.STALE) {
            LoadingPayloads.send(LoadingPayloads.message("resume",offeredId,offer.getString("dimension")));return;
        }
        if(id!=offeredId) {
            boolean resume=resumeRequested;clearScene();resumeRequested=resume;
            id=offeredId;dimension=offer.getString("dimension");revision=offer.getInt("revision");
        }
        if(decision==ActivationGate.Offer.RESUME || resumeRequested || (loadingScreen!=null && mc.screen!=loadingScreen)) {
            resumeRequested=true;skip("client has left the loading screen");return;
        }
        if(WINDOW.activationExpired(System.nanoTime())){skip("activation exceeded 60 seconds before preparation");return;}
        if(WINDOW.begin(System.nanoTime()))
            WallGuns.LOG.info("Wall gun preparation started id={} handshakeMs={} budgetMs={} radius={}",id,(System.nanoTime()-started)/1_000_000,preloadBudgetNanos/1_000_000,WallGunConfig.preloadRadiusChunks());
        var reply=message("preferences");reply.putBoolean("enabled",WallGunConfig.preloading());
        reply.putInt("radius",WallGunConfig.preloadRadiusChunks());
        LoadingPayloads.send(reply);
        WallGuns.LOG.info("Wall gun activation offer id={} dimension={} preload={}",id,dimension,WallGunConfig.PRELOAD_MODE.get());
    }
    private static void skip(String reason) {
        if(id==0)return;
        long now=System.nanoTime();
        if(!skipRequested){skipRequested=true;skipReason=reason;WallGuns.LOG.warn("Wall gun preloading skipped id={} dimension={}: {}",id,dimension,reason);}
        if(lastSkip==0 || now-lastSkip>=SKIP_RETRY_NANOS){
            var request=message(resumeRequested?"resume":"skip");request.putString("reason",skipReason);LoadingPayloads.send(request);lastSkip=now;
        }
    }
    public static void rejectMissingModel() {
        skip("initial scene contains a failed model");
    }
    public static void tick(ClientTickEvent.Post event) {
        pulse();
    }
    /** Also runs before render work when client ticks are delayed by a loading screen. */
    public static void pulse() {
        var mc=Minecraft.getInstance();acceptOffer(mc);
        if(!waiting() && id==0)return;
        long diagnosticNow=System.nanoTime();
        if(diagnosticNow-lastDiagnostic>=5_000_000_000L) {
            lastDiagnostic=diagnosticNow;
            WallGuns.LOG.info("Wall gun client progress id={} generation={} dimension={} stage={} elapsedMs={} prepareMs={} chunks={}/{} models={} batches={} screen={}",
                id,GATE.generation(),mc.level==null?"none":mc.level.dimension().location(),stage(),(diagnosticNow-started)/1_000_000,WINDOW.preparationElapsed(diagnosticNow)/1_000_000,
                CHUNKS.size(),expected,WallWarmup.pendingModels(),WallBatches.pendingBatches(),mc.screen==null?"none":mc.screen.getClass().getSimpleName());
        }
        if(waiting() && id==0 && mc.level!=null && mc.player!=null) {
            long now=System.nanoTime();
            if(lastStatus==0 || now-lastStatus>=SKIP_RETRY_NANOS) {
                var request=LoadingPayloads.message("status",0,mc.level.dimension().location().toString());
                request.putLong("generation",GATE.generation());request.putLong("retired",GATE.retired());
                LoadingPayloads.send(request);lastStatus=now;
            }
        }
        if(waiting() && mc.player!=null && mc.level!=null && mc.getOverlay()==null
            && (mc.screen==null || (loadingScreen!=null && mc.screen!=loadingScreen))) {
            resumeRequested=true;skip("client has left the loading screen");
        }
        if(id!=0 && WINDOW.activationExpired(System.nanoTime()))skip("activation exceeded 60 seconds");
        if(id!=0 && !sent && WallGunConfig.preloading() && (GunMeshes.failures>failures || WINDOW.preparationExpired(System.nanoTime())))
            skip(GunMeshes.failures>failures?"model capture failed":"preparation exceeded "+preloadBudgetNanos/1_000_000_000L+" seconds (stage="+stage()+")");
        if(skipRequested){skip("waiting for server release");return;}
        if(sent && sealed && System.nanoTime()-lastReady>=SKIP_RETRY_NANOS) {
            LoadingPayloads.send(message("ready"));lastReady=System.nanoTime();
        }
        if(id==0 || sent || mc.level==null || mc.player==null || mc.getOverlay()!=null)return;
        boolean received=true,changed=false;
        for(var entry:CHUNKS.entrySet()) {
            var pos=entry.getKey();var chunk=mc.level.getChunkSource().getChunk(pos.x,pos.z,ChunkStatus.FULL,false);
            if(chunk==null){received=false;continue;}
            if(!LoadingSessions.describe(chunk).equals(entry.getValue())){changed=true;received=false;continue;}
            for(var entity:chunk.getBlockEntities().values())if(entity instanceof WallGunEntity gun
                && RenderDistanceRules.keep(gun.getBlockPos(),mc.player.getEyePosition(),WallGunConfig.maxRenderDistance()))WallWarmup.request(gun);
        }
        if(changed && sealed){sent=true;LoadingPayloads.send(message("refresh"));return;}
        if(sealed && CHUNKS.size()==expected && received && WallWarmup.pendingModels()==0 && WallBatches.pendingBatches()==0) {
            // Requesting a cached model still needs one frame to enqueue/verify its GPU batches.
            if(!WallWarmup.initialPrepared(CHUNKS.keySet()))return;
            com.mojang.blaze3d.systems.RenderSystem.assertOnRenderThread();
            WallGuns.LOG.info("Wall gun preparation ready id={} chunks={} prepareMs={} result={}",id,CHUNKS.size(),WINDOW.preparationElapsed(System.nanoTime())/1_000_000,CHUNKS.isEmpty()?"empty-static-scene":"models-and-batches-ready");
            sent=true;lastReady=System.nanoTime();LoadingPayloads.send(message("ready"));
        }
    }
}
