package dev.kiyo.wallgun.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld;
import dev.kiyo.wallgun.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;

import org.joml.Matrix4f;

import java.util.*;

/** Optional Create bridge. Never loaded unless Create is installed. */
public final class CreateMovingGuns {
    private static final BufferBuilder BUILDER=new BufferBuilder(65536);
    private static final LinkedHashMap<Key, Bundle> BUFFERS = new LinkedHashMap<>(64, .75f, true);
    private static final LinkedHashMap<Key, Integer> PENDING = new LinkedHashMap<>();
    private static ClientLevel world;
    private static int cachedVertices;
    public static int uploads, draws, frameUploads, virtualCalls;
    private static long frameDrawNanos, lastFrameDrawNanos, peakFrameDrawNanos;
    private record Key(GunSnapshot snapshot, Direction facing, int roll, boolean flipped, int light) {}
    private static final class Bundle implements AutoCloseable {
        final Map<RenderType, VertexBuffer> materials = new LinkedHashMap<>();
        final int vertices;
        Bundle(int vertices) { this.vertices = vertices; }
        @Override public void close() { materials.values().forEach(VertexBuffer::close); }
    }
    private CreateMovingGuns() {}

    public static boolean renderIfVirtual(WallGunEntity gun, PoseStack pose, int light) {
        if (!(gun.getLevel() instanceof VirtualRenderWorld)) return false;
        virtualCalls++;
        if (gun.snapshot() == null) return true;
        var snapshot = gun.snapshot();
        var mesh = GunMeshes.peek(snapshot);
        RenderQueue.demand(snapshot);
        if (mesh == null) return true; // Captured by the on-demand render queue.
        var key = new Key(snapshot, gun.getBlockState().getValue(WallGunBlock.FACING),
                gun.mountRoll() + gun.roll(), gun.flipped(), light);
        var bundle = BUFFERS.get(key);
        if (bundle == null) {
            PENDING.putIfAbsent(key, light);
            return true;
        }
        long started=System.nanoTime();
        Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(pose.last().pose()); // Match MultiBufferSource: camera view, then Create contraption and block pose.
        for (var entry : bundle.materials.entrySet()) {
            var type = entry.getKey();
            type.setupRenderState();
            try {
                entry.getValue().bind();
                entry.getValue().drawWithShader(modelView, RenderSystem.getProjectionMatrix(), RenderSystem.getShader());
                draws++;
            } finally {
                VertexBuffer.unbind();
                type.clearRenderState();
            }
        }
        frameDrawNanos += System.nanoTime()-started;
        return true;
    }

    public static void frame(net.minecraftforge.event.TickEvent.RenderTickEvent event) {
        if(event.phase!=net.minecraftforge.event.TickEvent.Phase.START)return;
        lastFrameDrawNanos=frameDrawNanos; peakFrameDrawNanos=Math.max(peakFrameDrawNanos,frameDrawNanos); frameDrawNanos=0;
        frameUploads = 0;
        var current = Minecraft.getInstance().level;
        if (current != world) { PENDING.clear(); world = current; }
        if (current == null) return;
        int limit = 2;
        var budget = new WorkBudget(2_000_000);
        var it = PENDING.entrySet().iterator();
        while (it.hasNext() && frameUploads < limit && budget.start()) {
            var request = it.next();
            var mesh = GunMeshes.peek(request.getKey().snapshot());
            if (mesh == null) continue;
            if (!BUFFERS.containsKey(request.getKey())) {
                var bundle = build(request.getKey(), mesh, request.getValue());
                if (bundle != null) {
                    BUFFERS.put(request.getKey(), bundle);
                    cachedVertices += bundle.vertices;
                    frameUploads++; uploads++;
                }
            }
            it.remove();
        }
    }

    public static void invalidate(GunSnapshot snapshot) {
        var it=BUFFERS.entrySet().iterator();
        while(it.hasNext()){var entry=it.next();if(entry.getKey().snapshot().equals(snapshot)){cachedVertices-=entry.getValue().vertices;entry.getValue().close();it.remove();}}
    }
    private static Bundle build(Key key, GunMeshes.Mesh mesh, int light) {
        float maxDepth = mesh.materials().values().stream().flatMap(List::stream)
                .map(MeshCapture.Vertex::z).max(Float::compare).orElse(GunMeshes.WALL_GAP);
        var position = new DisplayPose(key.facing(), key.roll(), key.flipped(), GunMeshes.WALL_GAP, maxDepth);
        Bundle bundle = new Bundle(mesh.vertices());
        try {
            for (var material : mesh.materials().entrySet()) {
                {
                    var out=BUILDER;out.discard();out.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.NEW_ENTITY);
                    for (var vertex : material.getValue()) {
                        var point = position.point(vertex.x(), vertex.y(), vertex.z());
                        var normal = position.normal(vertex.nx(), vertex.ny(), vertex.nz());
                        out.vertex(point.x(), point.y(), point.z())
                                .color(vertex.color()).uv(vertex.u(), vertex.v())
                                .overlayCoords(OverlayTexture.NO_OVERLAY)
                                .uv2(vertex.light() == 0 ? light : vertex.light())
                                .normal(normal.x(), normal.y(), normal.z()).endVertex();
                    }
                    var data = out.end();
                    if (data != null) {
                        var gpu = new VertexBuffer(VertexBuffer.Usage.STATIC);
                        gpu.bind(); gpu.upload(data); VertexBuffer.unbind();
                        bundle.materials.put(material.getKey(), gpu);
                    }
                }
            }
            return bundle;
        } catch (RuntimeException ex) {
            bundle.close();
            WallGuns.LOG.error("Failed to upload moving decorative gun", ex);
            return null;
        }
    }

    public static void clear() {
        BUFFERS.values().forEach(Bundle::close);
        BUFFERS.clear(); PENDING.clear();
        cachedVertices = 0; world = null; frameDrawNanos=lastFrameDrawNanos=peakFrameDrawNanos=0;
    }
    public static String stats() {
        return "movingVirtualCalls=" + virtualCalls + ", movingDraws=" + draws + ", movingUploads=" + uploads
                + ", movingFrameUploads=" + frameUploads + ", movingCachedVertices=" + cachedVertices
                + ", movingPending=" + PENDING.size()+", movingCpuMs="+String.format(java.util.Locale.ROOT,"%.2f",lastFrameDrawNanos/1_000_000.0)+", peakMovingCpuMs="+String.format(java.util.Locale.ROOT,"%.2f",peakFrameDrawNanos/1_000_000.0);
    }
}
