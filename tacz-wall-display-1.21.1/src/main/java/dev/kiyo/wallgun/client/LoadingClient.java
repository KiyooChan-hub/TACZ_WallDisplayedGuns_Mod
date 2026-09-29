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
    private static boolean waiting,sealed,sent,fallbackScreen;
    private static long id,started;
    private static String dimension="";
    private static int revision,expected,failures;
    private static final Map<ChunkPos,CompoundTag> CHUNKS=new LinkedHashMap<>();
    public static boolean waiting(){return waiting;}
    public static void reset(){waiting=false;sealed=false;sent=false;id=0;CHUNKS.clear();}
    public static void entering(){reset();waiting=true;started=System.nanoTime();failures=GunMeshes.failures;}
    private static CompoundTag message(String kind){var t=LoadingPayloads.message(kind,id,dimension);t.putInt("revision",revision);return t;}
    public static void receive(CompoundTag tag) {
        var mc=Minecraft.getInstance();String kind=tag.getString("kind");
        if(kind.equals("offer")) {
            if(mc.level==null || !mc.level.dimension().location().toString().equals(tag.getString("dimension")))return;
            entering();id=tag.getLong("id");dimension=tag.getString("dimension");
            fallbackScreen=!(mc.screen instanceof ReceivingLevelScreen);
            if(fallbackScreen)mc.setScreen(new ReceivingLevelScreen(()->false,ReceivingLevelScreen.Reason.OTHER));
            var reply=message("preferences");reply.putBoolean("enabled",WallGunConfig.preloading());
            reply.putInt("radius",RenderDistanceRules.scanRadius(WallGunConfig.maxRenderDistance(),mc.options.getEffectiveRenderDistance()));
            LoadingPayloads.send(reply);return;
        }
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
    public static void rejectMissingModel() {
        if(id!=0)LoadingPayloads.send(message("failure"));
        var connection=Minecraft.getInstance().getConnection();
        if(connection!=null)connection.getConnection().disconnect(net.minecraft.network.chat.Component.literal("Wall gun initial scene contains a failed model; repair the gun pack or select OFF"));
    }
    public static void tick(ClientTickEvent.Post event) {
        if(!waiting)return;var mc=Minecraft.getInstance();
        if(System.nanoTime()-started>LoadingSessions.TIMEOUT_NANOS || GunMeshes.failures>failures) {
            if(id!=0)LoadingPayloads.send(message("failure"));
            if(mc.getConnection()!=null)mc.getConnection().getConnection().disconnect(net.minecraft.network.chat.Component.literal("Wall gun initial preparation failed or timed out"));
            return;
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
        if(sealed && CHUNKS.size()==expected && received && WallWarmup.pendingModels()==0 && WallBatches.pendingBatches()==0 && mc.levelRenderer.isSectionCompiled(mc.player.blockPosition())) {
            // Requesting a cached model still needs one frame to enqueue/verify its GPU batches.
            if(!WallWarmup.initialPrepared(CHUNKS.keySet()))return;
            com.mojang.blaze3d.systems.RenderSystem.assertOnRenderThread();
            org.lwjgl.opengl.GL11.glFinish(); // Complete queued GPU preparation before acknowledging activation.
            sent=true;LoadingPayloads.send(message("ready"));
        }
    }
}
