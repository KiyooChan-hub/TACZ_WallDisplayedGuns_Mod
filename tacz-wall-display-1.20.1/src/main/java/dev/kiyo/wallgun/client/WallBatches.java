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
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import java.util.*;

public final class WallBatches {
    // Residency must outlive a frame. Frustum/third-party BER culling is not removal.
    private static final Map<BlockPos, Entry> RESIDENTS = new HashMap<>();
    private static final Map<Key, Batch> BATCHES = new LinkedHashMap<>();
    private static Map<Key,List<Entry>> groups=Map.of();
    private static final BufferBuilder BUILDER=new BufferBuilder(65536);
    private static ClientLevel world;
    private static int primedThisFrame;
    private static WorkBudget primeBudget;
    public static int uploads, lastDraws, lastGuns;
    public static int lastUploadedVertices, maxBatchGuns;
    private record Entry(WallGunEntity gun, BlockPos pos, Direction facing, int roll, boolean flipped, GunMeshes.Mesh mesh, int light) {}
    private record Key(long section, int cell, RenderType type) {}
    private static final class Batch implements AutoCloseable {
        VertexBuffer buffer;
        List<Entry> entries = List.of();
        AABB bounds;
        boolean primed;
        @Override public void close() { if (buffer!=null) { buffer.close();buffer=null; } }
    }
    public static void enqueue(WallGunEntity gun, int light) {
        if (world!=gun.getLevel()) { clear();world=(ClientLevel)gun.getLevel(); }
        var mesh=GunMeshes.peek(gun.snapshot());
        if(mesh==null) { RESIDENTS.remove(gun.getBlockPos());WallWarmup.request(gun);return; }
        RESIDENTS.put(gun.getBlockPos(), new Entry(gun,gun.getBlockPos(),gun.getBlockState().getValue(WallGunBlock.FACING),gun.mountRoll()+gun.roll(),gun.flipped(),mesh,light));
    }
    public static void prepare(WorkBudget budget,int maxUploads) {
        if(world!=Minecraft.getInstance().level) { clear();world=Minecraft.getInstance().level; }
        if(world==null)return;
        primedThisFrame=0;primeBudget=null;lastUploadedVertices=0;
        groups=new LinkedHashMap<>();
        RESIDENTS.values().removeIf(entry -> entry.gun.isRemoved() || !world.hasChunkAt(entry.pos)
                || world.getBlockEntity(entry.pos)!=entry.gun || Minecraft.getInstance().player==null
                || !RenderDistanceRules.keep(entry.pos,Minecraft.getInstance().player.getEyePosition(),WallGunConfig.maxRenderDistance()));
        for (Entry entry:RESIDENTS.values()) {
            for (RenderType type:entry.mesh.materials().keySet()) groups.computeIfAbsent(new Key(SectionPos.asLong(entry.pos),BatchLayout.cell(entry.pos),type),ignored->new ArrayList<>()).add(entry);
        }
        int rebuilt=0;
        for(var group:groups.entrySet()) {
            var entries=group.getValue();entries.sort(Comparator.comparingLong(e->e.pos.asLong()));
            Batch batch=BATCHES.computeIfAbsent(group.getKey(),ignored->new Batch());
            if(!batch.entries.equals(entries) && rebuilt<maxUploads && budget.start()) {
                rebuild(group.getKey(),batch,entries);rebuilt++;
            }
        }
        var iterator=BATCHES.entrySet().iterator();
        while(iterator.hasNext()) { var entry=iterator.next();if(!groups.containsKey(entry.getKey())){entry.getValue().close();iterator.remove();} }
    }
    public static int pendingBatches() {
        int n=0;
        for(var group:groups.entrySet()) {
            var batch=BATCHES.get(group.getKey());
            if(batch==null || !batch.entries.equals(group.getValue()) || !batch.primed)n++;
        }
        return n;
    }
    public static void render(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES)return;
        lastDraws=0;lastGuns=0;
        if(world==null || world!=Minecraft.getInstance().level)return;
        var camera=event.getCamera().getPosition();
        Set<BlockPos> drawnGuns=new HashSet<>();
        if(primeBudget==null)primeBudget=new WorkBudget(WallWarmup.loading()?8_000_000:1_000_000);
        for(var group:groups.entrySet()) {
            Key key=group.getKey();var entries=group.getValue();var batch=BATCHES.get(key);
            // A pending edit must not keep drawing a destroyed gun or an obsolete attachment.
            if(batch==null || batch.buffer==null || !batch.entries.equals(entries))continue;
            boolean visible=event.getFrustum().isVisible(batch.bounds)
                    && RenderDistanceRules.draw(batch.bounds,camera,WallGunConfig.maxRenderDistance());
            if(!batch.primed) {
                if(primedThisFrame>=(WallWarmup.loading()?8:2) || !primeBudget.start())continue;
                primedThisFrame++;
            } else if(!visible)continue;
            // First draw also happens for batches behind the camera, in the normal world shader pass.
            // This prepares material textures and shader state without switching Iris framebuffers.
            var section=SectionPos.of(key.section);
            Matrix4f modelView=new Matrix4f(event.getPoseStack().last().pose()).translate((float)(section.minBlockX()-camera.x),(float)(section.minBlockY()-camera.y),(float)(section.minBlockZ()-camera.z));
            key.type.setupRenderState();
            try {
                batch.buffer.bind();
                batch.buffer.drawWithShader(modelView,event.getProjectionMatrix(),RenderSystem.getShader());
                batch.primed=true;
                if(visible) {lastDraws++;for(Entry entry:entries)drawnGuns.add(entry.pos);}
            } finally { VertexBuffer.unbind();key.type.clearRenderState(); }
        }
        lastGuns=drawnGuns.size();
    }
    private static void rebuild(Key key, Batch batch, List<Entry> entries) {
        if(entries.size()>BatchLayout.MAX_GUNS)throw new IllegalStateException("Oversized wall gun batch");
        maxBatchGuns=Math.max(maxBatchGuns,entries.size());
        batch.close();batch.primed=false;
        double minX=Double.POSITIVE_INFINITY,minY=minX,minZ=minX,maxX=-minX,maxY=-minX,maxZ=-minX;
        {
            BufferBuilder out=BUILDER;out.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.NEW_ENTITY);
            for (Entry entry:entries) {
                float maxDepth=entry.mesh.materials().values().stream().flatMap(List::stream).map(v->v.z()).max(Float::compare).orElse(GunMeshes.WALL_GAP);
                DisplayPose pose=new DisplayPose(entry.facing,entry.roll,entry.flipped,GunMeshes.WALL_GAP,maxDepth);
                for (MeshCapture.Vertex v:entry.mesh.materials().get(key.type)) {
                    lastUploadedVertices++;
                    var point=pose.point(v.x(),v.y(),v.z());
                    var normal=pose.normal(v.nx(),v.ny(),v.nz());
                    float x=point.x(), y=point.y(), z=point.z();
                    minX=Math.min(minX,(double)x+entry.pos.getX());maxX=Math.max(maxX,(double)x+entry.pos.getX());
                    minY=Math.min(minY,(double)y+entry.pos.getY());maxY=Math.max(maxY,(double)y+entry.pos.getY());
                    minZ=Math.min(minZ,(double)z+entry.pos.getZ());maxZ=Math.max(maxZ,(double)z+entry.pos.getZ());
                    out.vertex(x+(entry.pos.getX()&15),y+(entry.pos.getY()&15),z+(entry.pos.getZ()&15))
                            .color(v.color()).uv(v.u(),v.v()).overlayCoords(OverlayTexture.NO_OVERLAY)
                            .uv2(v.light()==0?entry.light:v.light()).normal(normal.x(),normal.y(),normal.z()).endVertex();
                }
            }
            BufferBuilder.RenderedBuffer mesh=out.end();
            if(mesh!=null) {
                batch.buffer=new VertexBuffer(VertexBuffer.Usage.STATIC);
                batch.buffer.bind();batch.buffer.upload(mesh);VertexBuffer.unbind();uploads++;
            }
        }
        batch.bounds=new AABB(minX,minY,minZ,maxX,maxY,maxZ);
        batch.entries=List.copyOf(entries);
    }
    public static void clear() {
        BATCHES.values().forEach(Batch::close);BATCHES.clear();RESIDENTS.clear();groups=Map.of();world=null;
    }
    public static String stats() { return "bakes="+GunMeshes.bakes+", failures="+GunMeshes.failures+", uploads="+uploads+", draws="+lastDraws+", visible="+lastGuns+", cachedBatches="+BATCHES.size()+", uploadedVertices="+lastUploadedVertices+", maxBatchGuns="+maxBatchGuns+", pendingModels="+WallWarmup.pendingModels()+", pendingBatches="+pendingBatches()+", warming="+WallWarmup.loading(); }
}
