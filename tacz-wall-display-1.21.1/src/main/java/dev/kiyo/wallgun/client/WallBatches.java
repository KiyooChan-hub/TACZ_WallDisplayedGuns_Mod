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
    private static final Map<Key, Batch> BATCHES = new LinkedHashMap<>();
    private static Map<Key,List<Entry>> groups=Map.of();
    private static final Map<Key, Batch> STAGING = new LinkedHashMap<>();
    private static final Map<Key, Batch> SPARES = new LinkedHashMap<>();
    private static ClientLevel world;
    private static net.minecraft.client.renderer.culling.Frustum frustum;
    private static final LinkedHashMap<net.minecraft.resources.ResourceLocation,Map<Key,Batch>> DIMENSIONS=new LinkedHashMap<>();
    private static final Map<Key,Batch> RESTORED=new LinkedHashMap<>();
    private static long restoreDeadline;
    public static int dimensionCacheHits;
    private record Signature(BlockPos pos,Direction facing,int roll,boolean flipped,GunMeshes.Mesh mesh,int light) {}
    private static List<Signature> signatures(List<Entry> entries) {return entries.stream().map(e->new Signature(e.pos,e.facing,e.roll,e.flipped,e.mesh,e.light)).toList();}
    public static boolean visible(AABB bounds) {return frustum!=null && frustum.isVisible(bounds);}
    private static long weight(Map<Key,Batch> batches) {return batches.values().stream().mapToLong(b->b.vertices).sum();}
    private static void release(Map<Key,Batch> batches) {batches.values().forEach(Batch::close);batches.clear();}
    private static void trimDimensions() {
        long weight=DIMENSIONS.values().stream().mapToLong(WallBatches::weight).sum();
        var iterator=DIMENSIONS.entrySet().iterator();
        while(iterator.hasNext() && (DIMENSIONS.size()>2 || weight>WallGunConfig.dimensionCacheVertices())) {
            var cached=iterator.next();weight-=weight(cached.getValue());release(cached.getValue());iterator.remove();
        }
    }
    public static void switchWorld(ClientLevel next) {
        if(world==next)return;
        // Take the destination out first: retaining the departing scene must not evict it.
        var cached=next==null?null:DIMENSIONS.remove(next.dimension().location());
        if(cached!=null && WallGunConfig.dimensionCacheVertices()==0) {release(cached);cached=null;}
        release(STAGING);release(SPARES);
        if(world!=null) {
            BATCHES.putAll(RESTORED);RESTORED.clear();
            for(var batch:BATCHES.values()) {batch.entries=List.of();batch.chunks.clear();}
            var old=DIMENSIONS.put(world.dimension().location(),new LinkedHashMap<>(BATCHES));
            if(old!=null)release(old);
        } else release(RESTORED);
        BATCHES.clear();RESIDENTS.clear();groups=Map.of();frustum=null;world=next;
        trimDimensions();
        WallGuns.LOG.info("Wall gun dimension cache: destination={}, reusable batches={}, retained vertices={}",
                next==null?"none":next.dimension().location(),cached==null?0:cached.size(),DIMENSIONS.values().stream().mapToLong(WallBatches::weight).sum());
        if(next!=null) {
            if(cached!=null)RESTORED.putAll(cached);
            restoreDeadline=System.nanoTime()+10_000_000_000L;
        }
    }
    private static int primedThisFrame;
    private static WorkBudget primeBudget;
    public static int uploads, lastDraws, lastGuns;
    public static int lastUploadedVertices, maxBatchGuns;
    private record Entry(WallGunEntity gun, BlockPos pos, Direction facing, int roll, boolean flipped, GunMeshes.Mesh mesh, int light) {}
    private record Key(long section, int cell, RenderType type) {}
    private record Cell(long section,int cell) {}
    private static final class Batch implements AutoCloseable {
        VertexBuffer buffer;
        List<Entry> entries = List.of();
        AABB bounds;
        boolean primed;
        int vertices;
        List<Signature> signature=List.of();
        final Map<BlockPos, Chunk> chunks = new HashMap<>();
        @Override public void close() { if (buffer!=null) { buffer.close();buffer=null; } }
    }
    private record Chunk(Entry entry, float[] transformed) {}
    private static boolean poseOnly(List<Entry> old, List<Entry> next) {
        if(old.size()!=next.size())return false;
        for(int i=0;i<old.size();i++) {
            Entry a=old.get(i),b=next.get(i);
            if(a.gun!=b.gun || !a.pos.equals(b.pos) || a.mesh!=b.mesh || a.light!=b.light)return false;
        }
        return true;
    }
    public static void enqueue(WallGunEntity gun, int light) {
        if (world!=gun.getLevel())switchWorld((ClientLevel)gun.getLevel());
        var mesh=GunMeshes.peek(gun.snapshot());
        if(mesh==null) { RESIDENTS.remove(gun.getBlockPos());WallWarmup.request(gun);return; }
        RESIDENTS.put(gun.getBlockPos(), new Entry(gun,gun.getBlockPos(),gun.getBlockState().getValue(WallGunBlock.FACING),gun.mountRoll()+gun.roll(),gun.flipped(),mesh,light));
    }
    public static void prepare(WorkBudget budget,int maxUploads) {
        if(world!=Minecraft.getInstance().level)switchWorld(Minecraft.getInstance().level);
        if(world==null)return;
        trimDimensions();
        if(System.nanoTime()>restoreDeadline)release(RESTORED);
        primedThisFrame=0;primeBudget=null;lastUploadedVertices=0;
        groups=new LinkedHashMap<>();
        RESIDENTS.values().removeIf(entry -> entry.gun.isRemoved() || !world.hasChunkAt(entry.pos)
                || world.getBlockEntity(entry.pos)!=entry.gun || Minecraft.getInstance().player==null
                || !RenderDistanceRules.keep(entry.pos, Minecraft.getInstance().player.getEyePosition(), WallGunConfig.maxRenderDistance()));
        for (Entry entry:RESIDENTS.values()) {
            for (RenderType type:entry.mesh.materials().keySet()) groups.computeIfAbsent(new Key(SectionPos.asLong(entry.pos),BatchLayout.cell(entry.pos),type),ignored->new ArrayList<>()).add(entry);
        }
        STAGING.entrySet().removeIf(e -> {
            if(!groups.containsKey(e.getKey())) {e.getValue().close();return true;}
            return false;
        });
        SPARES.entrySet().removeIf(e->{if(!groups.containsKey(e.getKey())){e.getValue().close();return true;}return false;});
        int rebuilt=0;
        for(var group:groups.entrySet()) {
            var entries=group.getValue();entries.sort(Comparator.comparingLong(e->e.pos.asLong()));
            Batch active=BATCHES.get(group.getKey());
            if(active==null) {
                Batch cached=RESTORED.remove(group.getKey());
                if(cached!=null) {
                    if(cached.signature.equals(signatures(entries))) {
                        cached.entries=List.copyOf(entries);BATCHES.put(group.getKey(),cached);active=cached;dimensionCacheHits++;
                    } else {Batch old=SPARES.put(group.getKey(),cached);if(old!=null)old.close();}
                }
            }
            if(active!=null && active.entries.equals(entries))continue;
            Batch staged=STAGING.computeIfAbsent(group.getKey(),key->{Batch spare=SPARES.remove(key);return spare==null?new Batch():spare;});
            if(!staged.entries.equals(entries) && rebuilt<maxUploads && budget.start()) {
                if(active!=null && staged.chunks.isEmpty())staged.chunks.putAll(active.chunks);
                rebuild(group.getKey(),staged,entries);
                staged.primed=active!=null && active.primed && poseOnly(active.entries,entries);rebuilt++;
            }
        }
        // Publish a coherent revision: every material of a gun changes in the same frame.
        Set<Cell> waiting=new HashSet<>();
        for(var group:groups.entrySet()) {
            Batch active=BATCHES.get(group.getKey()), staged=STAGING.get(group.getKey());
            if((active==null || !active.entries.equals(group.getValue()))
                    && (staged==null || !staged.entries.equals(group.getValue())))
                waiting.add(new Cell(group.getKey().section,group.getKey().cell));
        }
        for(var group:groups.entrySet()) {
            if(waiting.contains(new Cell(group.getKey().section,group.getKey().cell)))continue;
            Batch staged=STAGING.remove(group.getKey());
            if(staged==null)continue;
            if(!staged.entries.equals(group.getValue())) {
                Batch discarded=SPARES.put(group.getKey(),staged);if(discarded!=null)discarded.close();continue;
            }
            Batch old=BATCHES.put(group.getKey(),staged);
            if(old!=null) { Batch discarded=SPARES.put(group.getKey(),old);if(discarded!=null)discarded.close(); }
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
        // Draw before other mods' block-entity overlays can switch the world framebuffer.
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS)return;
        frustum=event.getFrustum();
        lastDraws=0;lastGuns=0;
        if(world==null || world!=Minecraft.getInstance().level)return;
        var camera=event.getCamera().getPosition();
        Set<BlockPos> drawnGuns=new HashSet<>();
        if(primeBudget==null)primeBudget=new WorkBudget(WallWarmup.loading()?8_000_000:1_000_000);
        for(var group:groups.entrySet()) {
            Key key=group.getKey();var entries=group.getValue();var batch=BATCHES.get(key);
            // A pending edit must not keep drawing a destroyed gun or an obsolete attachment.
            if(batch==null || batch.buffer==null || (!batch.entries.equals(entries) && !poseOnly(batch.entries,entries)))continue;
            boolean visible=event.getFrustum().isVisible(batch.bounds)
                    && RenderDistanceRules.draw(batch.bounds, camera, WallGunConfig.maxRenderDistance());
            if(!batch.primed && batch.entries.equals(entries)) {
                if(primedThisFrame>=(WallWarmup.loading()?8:2) || !primeBudget.start())continue;
                primedThisFrame++;
            } else if(!visible)continue;
            // First draw also happens for batches behind the camera, in the normal world shader pass.
            // This prepares material textures and shader state without switching Iris framebuffers.
            var section=SectionPos.of(key.section);
            Matrix4f modelView=new Matrix4f(event.getModelViewMatrix()).translate((float)(section.minBlockX()-camera.x),(float)(section.minBlockY()-camera.y),(float)(section.minBlockZ()-camera.z));
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
        batch.primed=false;
        double minX=Double.POSITIVE_INFINITY,minY=minX,minZ=minX,maxX=-minX,maxY=-minX,maxZ=-minX;
        try (ByteBufferBuilder storage=new ByteBufferBuilder(65536)) {
            BufferBuilder out=new BufferBuilder(storage,VertexFormat.Mode.QUADS,DefaultVertexFormat.NEW_ENTITY);
            for (Entry entry:entries) {
                float maxDepth=entry.mesh.maxDepth();
                DisplayPose pose=new DisplayPose(entry.facing,entry.roll,entry.flipped,GunMeshes.WALL_GAP,maxDepth);
                var vertices=entry.mesh.materials().get(key.type);
                Chunk previous=batch.chunks.get(entry.pos);
                float[] transformed;
                if(previous!=null && previous.entry.equals(entry))transformed=previous.transformed;
                else {
                    transformed=new float[vertices.size()*6];int cursor=0;
                    var pointScratch=new org.joml.Vector3f();var normalScratch=new org.joml.Vector3f();
                    for(var vertex:vertices) {
                        var point=pose.point(vertex.x(),vertex.y(),vertex.z(),pointScratch);
                        var normal=pose.normal(vertex.nx(),vertex.ny(),vertex.nz(),normalScratch);
                        transformed[cursor++]=point.x();transformed[cursor++]=point.y();transformed[cursor++]=point.z();
                        transformed[cursor++]=normal.x();transformed[cursor++]=normal.y();transformed[cursor++]=normal.z();
                    }
                    batch.chunks.put(entry.pos,new Chunk(entry,transformed));
                }
                int cursor=0;
                for (MeshCapture.Vertex v:vertices) {
                    lastUploadedVertices++;
                    float x=transformed[cursor++], y=transformed[cursor++], z=transformed[cursor++];
                    float nx=transformed[cursor++],ny=transformed[cursor++],nz=transformed[cursor++];
                    minX=Math.min(minX,(double)x+entry.pos.getX());maxX=Math.max(maxX,(double)x+entry.pos.getX());
                    minY=Math.min(minY,(double)y+entry.pos.getY());maxY=Math.max(maxY,(double)y+entry.pos.getY());
                    minZ=Math.min(minZ,(double)z+entry.pos.getZ());maxZ=Math.max(maxZ,(double)z+entry.pos.getZ());
                    out.addVertex(x+(entry.pos.getX()&15),y+(entry.pos.getY()&15),z+(entry.pos.getZ()&15))
                            .setColor(v.color()).setUv(v.u(),v.v()).setOverlay(OverlayTexture.NO_OVERLAY)
                            .setLight(v.light()==0?entry.light:v.light()).setNormal(nx,ny,nz);
                }
            }
            MeshData mesh=out.build();
            if(mesh!=null) {
                if(batch.buffer==null)batch.buffer=new VertexBuffer(VertexBuffer.Usage.STATIC);
                batch.buffer.bind();batch.buffer.upload(mesh);VertexBuffer.unbind();uploads++;
            }
        }
        batch.chunks.keySet().retainAll(entries.stream().map(Entry::pos).toList());
        batch.bounds=new AABB(minX,minY,minZ,maxX,maxY,maxZ);
        batch.entries=List.copyOf(entries);batch.signature=signatures(entries);
        batch.vertices=entries.stream().mapToInt(e->e.mesh.materials().get(key.type).size()).sum();
    }
    public static void clear() {
        DIMENSIONS.values().forEach(WallBatches::release);DIMENSIONS.clear();release(RESTORED);frustum=null;
        SPARES.values().forEach(Batch::close);SPARES.clear();STAGING.values().forEach(Batch::close);STAGING.clear();BATCHES.values().forEach(Batch::close);BATCHES.clear();RESIDENTS.clear();groups=Map.of();world=null;
    }
    public static String stats() { return "preload="+WallGunConfig.PRELOAD_MODE.get()+", modelCache="+GunMeshes.cachedModels()+", modelVertices="+GunMeshes.cachedVertices()+", dimensionCacheHits="+dimensionCacheHits+", retainedDimensions="+DIMENSIONS.size()+", bakes="+GunMeshes.bakes+", failures="+GunMeshes.failures+", uploads="+uploads+", draws="+lastDraws+", visible="+lastGuns+", cachedBatches="+BATCHES.size()+", uploadedVertices="+lastUploadedVertices+", maxBatchGuns="+maxBatchGuns+", pendingModels="+WallWarmup.pendingModels()+", pendingBatches="+pendingBatches()+", warming="+WallWarmup.loading(); }
}
