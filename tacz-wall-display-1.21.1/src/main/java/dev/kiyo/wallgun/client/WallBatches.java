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
    private static final Map<BlockPos, Entry> QUEUE = new HashMap<>();
    private static final Map<Key, Batch> BATCHES = new HashMap<>();
    private static ClientLevel world;
    private static int frame;
    public static int uploads, lastDraws, lastGuns;
    private record Entry(BlockPos pos, Direction facing, GunMeshes.Mesh mesh, int light) {}
    private record Key(long section, RenderType type) {}
    private static final class Batch implements AutoCloseable {
        VertexBuffer buffer;
        List<Entry> entries = List.of();
        int lastFrame;
        @Override public void close() { if (buffer!=null) { buffer.close();buffer=null; } }
    }
    public static void enqueue(WallGunEntity gun, int light) {
        if (world!=gun.getLevel()) { clear();world=(ClientLevel)gun.getLevel(); }
        // Metadata only until the view frustum is checked at flush time.
        QUEUE.put(gun.getBlockPos(), new Entry(gun.getBlockPos(),gun.getBlockState().getValue(WallGunBlock.FACING),GunMeshes.get(gun.gunId()),light));
    }
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage()!=RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) return;
        if (world!=Minecraft.getInstance().level) { clear();world=Minecraft.getInstance().level; }
        frame++;lastDraws=0;lastGuns=0;
        Map<Key,List<Entry>> groups=new HashMap<>();
        for (Entry entry:QUEUE.values()) {
            if (!event.getFrustum().isVisible(new AABB(entry.pos).inflate(1))) continue;
            lastGuns++;
            for (RenderType type:entry.mesh.materials().keySet()) groups.computeIfAbsent(new Key(SectionPos.asLong(entry.pos),type),ignored->new ArrayList<>()).add(entry);
        }
        QUEUE.clear();
        var camera=event.getCamera().getPosition();
        for (var group:groups.entrySet()) {
            Key key=group.getKey();List<Entry> entries=group.getValue();
            entries.sort(Comparator.comparingLong(e->e.pos.asLong()));
            Batch batch=BATCHES.computeIfAbsent(key,ignored->new Batch());batch.lastFrame=frame;
            if (!batch.entries.equals(entries)) rebuild(key,batch,entries);
            if (batch.buffer==null) continue;
            var section=SectionPos.of(key.section);
            Matrix4f modelView=new Matrix4f(event.getModelViewMatrix()).translate((float)(section.minBlockX()-camera.x),(float)(section.minBlockY()-camera.y),(float)(section.minBlockZ()-camera.z));
            key.type.setupRenderState();
            try {
                batch.buffer.bind();
                batch.buffer.drawWithShader(modelView,event.getProjectionMatrix(),RenderSystem.getShader());
                lastDraws++;
            } finally { VertexBuffer.unbind();key.type.clearRenderState(); }
        }
        var iterator=BATCHES.values().iterator();
        while(iterator.hasNext()) { Batch batch=iterator.next();if(frame-batch.lastFrame>120){batch.close();iterator.remove();} }
    }
    private static void rebuild(Key key, Batch batch, List<Entry> entries) {
        batch.close();
        try (ByteBufferBuilder storage=new ByteBufferBuilder(65536)) {
            BufferBuilder out=new BufferBuilder(storage,VertexFormat.Mode.QUADS,DefaultVertexFormat.NEW_ENTITY);
            for (Entry entry:entries) {
                for (MeshCapture.Vertex v:entry.mesh.materials().get(key.type)) {
                    float x=v.x(),z=v.z(),nx=v.nx(),nz=v.nz();
                    switch(entry.facing) {
                        case NORTH -> { x=1-v.x();z=1-v.z();nx=-v.nx();nz=-v.nz(); }
                        case EAST -> { x=v.z();z=1-v.x();nx=v.nz();nz=-v.nx(); }
                        case WEST -> { x=1-v.z();z=v.x();nx=-v.nz();nz=v.nx(); }
                        default -> {}
                    }
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
        batch.entries=List.copyOf(entries);
    }
    public static void clear() {
        BATCHES.values().forEach(Batch::close);BATCHES.clear();QUEUE.clear();world=null;
    }
    public static String stats() { return "bakes="+GunMeshes.bakes+", failures="+GunMeshes.failures+", uploads="+uploads+", draws="+lastDraws+", visible="+lastGuns+", cachedBatches="+BATCHES.size(); }
}
