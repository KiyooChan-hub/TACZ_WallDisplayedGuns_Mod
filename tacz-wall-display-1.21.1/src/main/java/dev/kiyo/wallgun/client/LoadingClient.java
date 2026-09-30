package dev.kiyo.wallgun.client;

import dev.kiyo.wallgun.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.nbt.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.util.*;

/** The terrain screen is released only by the server's matching activation response. */
public final class LoadingClient {
    private static boolean waiting,sealed,sent,fallbackScreen,skipRequested;
    private static long id,started,lastSkip;
    private static String dimension="";
    private static String skipReason="";
    private static int revision,expected,failures;
    private static CompoundTag pendingOffer;
    private static final long PRELOAD_BUDGET_NANOS=90_000_000_000L;
    private static final long SKIP_RETRY_NANOS=2_000_000_000L;
    private static final Map<ChunkPos,CompoundTag> CHUNKS=new LinkedHashMap<>();
    public static boolean waiting(){return waiting;}
    public static boolean skipping(){return skipRequested;}
    public static void reset(){waiting=false;sealed=false;sent=false;skipRequested=false;id=0;lastSkip=0;skipReason="";pendingOffer=null;CHUNKS.clear();}
    public static void entering(){reset();waiting=true;started=System.nanoTime();failures=GunMeshes.failures;}
    private static CompoundTag message(String kind){var t=LoadingPayloads.message(kind,id,dimension);t.putInt("revision",revision);return t;}
    public static void receive(CompoundTag tag) {
        var mc=Minecraft.getInstance();String kind=tag.getString("kind");
        if(kind.equals("offer")){pendingOffer=tag.copy();acceptOffer(mc);return;}
        if(!waiting || id!=tag.getLong("id") || !dimension.equals(tag.getString("dimension")))return;
        if(kind.equals("begin")){revision=tag.getInt("revision");CHUNKS.clear();sealed=false;sent=false;return;}
        if(tag.getInt("revision")!=revision)return;
        switch(kind) {
            case "chunk" -> {var data=tag.getCompound("data");CHUNKS.put(new ChunkPos(data.getLong("chunk")),data);}
            case "seal" -> {expected=tag.getInt("count");sealed=true;}
            case "release" -> {
                WallGuns.LOG.info("Wall gun client prepared id={} chunks={} bakes={} uploads={} waitMs={}",id,CHUNKS.size(),GunMeshes.bakes,WallBatches.uploads,(System.nanoTime()-started)/1_000_000);
                if(tag.getBoolean("placementDisabled"))PlacementClient.disableAfterDimensionChange();
                boolean close=fallbackScreen;reset();if(close && mc.screen instanceof ReceivingLevelScreen screen)screen.onClose();
            }
        }
    }
    private static void acceptOffer(Minecraft mc) {
        if(pendingOffer==null || mc.level==null || !mc.level.dimension().location().toString().equals(pendingOffer.getString("dimension")))return;
        var offer=pendingOffer;pendingOffer=null;
        if(!waiting || id!=offer.getLong("id") || !dimension.equals(offer.getString("dimension"))) {
            entering();id=offer.getLong("id");dimension=offer.getString("dimension");
            fallbackScreen=!(mc.screen instanceof ReceivingLevelScreen);
            if(fallbackScreen)mc.setScreen(new ReceivingLevelScreen(()->false,ReceivingLevelScreen.Reason.OTHER));
        }
        var reply=message("preferences");reply.putBoolean("enabled",WallGunConfig.preloading());
        reply.putInt("radius",RenderDistanceRules.scanRadius(WallGunConfig.maxRenderDistance(),mc.options.getEffectiveRenderDistance()));
        LoadingPayloads.send(reply);
        WallGuns.LOG.info("Wall gun activation offer id={} dimension={} preload={}",id,dimension,WallGunConfig.PRELOAD_MODE.get());
    }
    private static void skip(String reason) {
        if(id==0)return;
        long now=System.nanoTime();
        if(!skipRequested){skipRequested=true;skipReason=reason;WallGuns.LOG.warn("Wall gun preloading skipped id={} dimension={}: {}",id,dimension,reason);}
        if(lastSkip==0 || now-lastSkip>=SKIP_RETRY_NANOS){
            var request=message("skip");request.putString("reason",skipReason);LoadingPayloads.send(request);lastSkip=now;
        }
    }
    public static void rejectMissingModel() {
        skip("initial scene contains a failed model");
    }
    public static void tick(ClientTickEvent.Post event) {
        if(!waiting)return;var mc=Minecraft.getInstance();acceptOffer(mc);
        long elapsed=System.nanoTime()-started;
        if(elapsed>LoadingSessions.TIMEOUT_NANOS) {
            if(mc.getConnection()!=null)mc.getConnection().getConnection().disconnect(net.minecraft.network.chat.Component.literal("Wall gun activation timed out (stage="+(id==0?"offer":skipRequested?"skip release":sent?"server verification":"initial scene")+")"));
            return;
        }
        if(id!=0 && WallGunConfig.preloading() && (GunMeshes.failures>failures || elapsed>PRELOAD_BUDGET_NANOS))
            skip(GunMeshes.failures>failures?"model capture failed":"preparation exceeded 90 seconds");
        if(skipRequested){skip("waiting for server release");return;}
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
        if(sealed && CHUNKS.size()==expected && received && WallWarmup.pendingModels()==0 && WallBatches.pendingBatches()==0 && mc.levelRenderer.isSectionCompiled(mc.player.blockPosition())) {
            // Requesting a cached model still needs one frame to enqueue/verify its GPU batches.
            if(!WallWarmup.initialPrepared(CHUNKS.keySet()))return;
            com.mojang.blaze3d.systems.RenderSystem.assertOnRenderThread();
            org.lwjgl.opengl.GL11.glFinish(); // Complete queued GPU preparation before acknowledging activation.
            sent=true;LoadingPayloads.send(message("ready"));
        }
    }
}
