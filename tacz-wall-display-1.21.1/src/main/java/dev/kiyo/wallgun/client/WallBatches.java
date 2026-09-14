package dev.kiyo.wallgun.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.kiyo.wallgun.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.*;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import java.util.*;

public final class WallBatches {
    // Residency must outlive a frame. Frustum/third-party BER culling is not removal.
    private static final Map<BlockPos, Entry> RESIDENTS = new HashMap<>();
    private static final Map<Key, Batch> BATCHES = new HashMap<>();
    private static ClientLevel world;
    private static int frame;
    public static int uploads, lastDraws, lastGuns;
    public static int lastUploadedVertices, maxBatchGuns;
    private record Entry(WallGunEntity gun, BlockPos pos, Direction facing, GunMeshes.Mesh mesh, int light) {}
    private record Key(long section, int cell, RenderType type) {}
    private static final class Batch implements AutoCloseable {
        VertexBuffer buffer;
        List<Entry> entries = List.of();
        AABB bounds;
        int lastFrame;
        @Override public void close() { if (buffer!=null) { buffer.close();buffer=null; } }
    }
    public static void enqueue(WallGunEntity gun, int light) {
        if (world!=gun.getLevel()) { clear();world=(ClientLevel)gun.getLevel(); }
        RESIDENTS.put(gun.getBlockPos(), new Entry(gun,gun.getBlockPos(),gun.getBlockState().getValue(WallGunBlock.FACING),GunMeshes.get(gun.gunId()),light));
    }
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage()!=RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) return;
        if (world!=Minecraft.getInstance().level) { clear();world=Minecraft.getInstance().level; }
        frame++;lastDraws=0;lastGuns=0;lastUploadedVertices=0;
        Map<Key,List<Entry>> groups=new HashMap<>();
        RESIDENTS.values().removeIf(entry -> entry.gun.isRemoved() || !world.hasChunkAt(entry.pos)
                || world.getBlockEntity(entry.pos)!=entry.gun);
        for (Entry entry:RESIDENTS.values()) {
            for (RenderType type:entry.mesh.materials().keySet()) groups.computeIfAbsent(new Key(SectionPos.asLong(entry.pos),BatchLayout.cell(entry.pos),type),ignored->new ArrayList<>()).add(entry);
        }
        var camera=event.getCamera().getPosition();
        Set<BlockPos> drawnGuns=new HashSet<>();
        for (var group:groups.entrySet()) {
            Key key=group.getKey();List<Entry> entries=group.getValue();
            entries.sort(Comparator.comparingLong(e->e.pos.asLong()));
            Batch batch=BATCHES.computeIfAbsent(key,ignored->new Batch());batch.lastFrame=frame;
            if (!batch.entries.equals(entries)) rebuild(key,batch,entries);
            if (batch.buffer==null) continue;
            // Cull the complete immutable batch, never rebuild a camera-dependent subset.
            if (!event.getFrustum().isVisible(batch.bounds) || batch.bounds.distanceToSqr(camera)>96*96) continue;
            for (Entry entry:entries) drawnGuns.add(entry.pos);
            var section=SectionPos.of(key.section);
            Matrix4f modelView=new Matrix4f(event.getModelViewMatrix()).translate((float)(section.minBlockX()-camera.x),(float)(section.minBlockY()-camera.y),(float)(section.minBlockZ()-camera.z));
            key.type.setupRenderState();
            try {
                batch.buffer.bind();
                batch.buffer.drawWithShader(modelView,event.getProjectionMatrix(),RenderSystem.getShader());
                lastDraws++;
            } finally { VertexBuffer.unbind();key.type.clearRenderState(); }
        }
        lastGuns=drawnGuns.size();
        var iterator=BATCHES.values().iterator();
        while(iterator.hasNext()) { Batch batch=iterator.next();if(frame-batch.lastFrame>120){batch.close();iterator.remove();} }
    }
    private static void rebuild(Key key, Batch batch, List<Entry> entries) {
        if(entries.size()>BatchLayout.MAX_GUNS)throw new IllegalStateException("Oversized wall gun batch");
        maxBatchGuns=Math.max(maxBatchGuns,entries.size());
        batch.close();
        double minX=Double.POSITIVE_INFINITY,minY=minX,minZ=minX,maxX=-minX,maxY=-minX,maxZ=-minX;
        try (ByteBufferBuilder storage=new ByteBufferBuilder(65536)) {
            BufferBuilder out=new BufferBuilder(storage,VertexFormat.Mode.QUADS,DefaultVertexFormat.NEW_ENTITY);
            for (Entry entry:entries) {
                for (MeshCapture.Vertex v:entry.mesh.materials().get(key.type)) {
                    lastUploadedVertices++;
                    float x=v.x(),z=v.z(),nx=v.nx(),nz=v.nz();
                    switch(entry.facing) {
                        case NORTH -> { x=1-v.x();z=1-v.z();nx=-v.nx();nz=-v.nz(); }
                        case EAST -> { x=v.z();z=1-v.x();nx=v.nz();nz=-v.nx(); }
                        case WEST -> { x=1-v.z();z=v.x();nx=-v.nz();nz=v.nx(); }
                        default -> {}
                    }
                    minX=Math.min(minX,(double)x+entry.pos.getX());maxX=Math.max(maxX,(double)x+entry.pos.getX());
                    minY=Math.min(minY,(double)v.y()+entry.pos.getY());maxY=Math.max(maxY,(double)v.y()+entry.pos.getY());
                    minZ=Math.min(minZ,(double)z+entry.pos.getZ());maxZ=Math.max(maxZ,(double)z+entry.pos.getZ());
                    out.addVertex(x+(entry.pos.getX()&15),v.y()+(entry.pos.getY()&15),z+(entry.pos.getZ()&15))
                            .setColor(v.color()).setUv(v.u(),v.v()).setOverlay(OverlayTexture.NO_OVERLAY)
                            .setLight(v.light()==0?entry.light:v.light()).setNormal(nx,v.ny(),nz);
                }
            }
            MeshData mesh=out.build();
            if(mesh!=null) {
                batch.buffer=new VertexBuffer(VertexBuffer.Usage.STATIC);
                batch.buffer.bind();batch.buffer.upload(mesh);VertexBuffer.unbind();uploads++;
            }
        }
        batch.bounds=new AABB(minX,minY,minZ,maxX,maxY,maxZ);
        batch.entries=List.copyOf(entries);
    }
    public static void clear() {
        BATCHES.values().forEach(Batch::close);BATCHES.clear();RESIDENTS.clear();world=null;
    }
    public static String stats() { return "bakes="+GunMeshes.bakes+", failures="+GunMeshes.failures+", uploads="+uploads+", draws="+lastDraws+", visible="+lastGuns+", cachedBatches="+BATCHES.size()+", uploadedVertices="+lastUploadedVertices+", maxBatchGuns="+maxBatchGuns; }
}
