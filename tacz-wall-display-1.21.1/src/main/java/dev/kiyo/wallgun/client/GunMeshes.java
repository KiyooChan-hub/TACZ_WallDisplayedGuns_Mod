package dev.kiyo.wallgun.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.resource.GunDisplayInstance;
import dev.kiyo.wallgun.WallGuns;
import dev.kiyo.wallgun.GunSnapshot;
import dev.kiyo.wallgun.mixin.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.world.item.ItemDisplayContext;
import java.util.*;
import static dev.kiyo.wallgun.client.MeshCapture.Vertex;

public final class GunMeshes {
    public static final float WALL_GAP=.001F;
    private static final WeightedCache<GunSnapshot,Mesh> CACHE = new WeightedCache<>(4_000_000,mesh->mesh.vertices(),mesh->{});
    public static int cacheHits, invalidations;
    public static int bakes, failures;
    public record Mesh(Map<RenderType, List<Vertex>> materials, int vertices, boolean missing, boolean canonicalFlipped, float maxDepth) {
        public Mesh(Map<RenderType,List<Vertex>> materials,int vertices,boolean missing,boolean canonicalFlipped) { this(materials,vertices,missing,canonicalFlipped,depth(materials)); }
        private static float depth(Map<RenderType,List<Vertex>> materials) { float depth=WALL_GAP;for(var list:materials.values())for(var vertex:list)depth=Math.max(depth,vertex.z());return depth; }
        public Mesh(Map<RenderType,List<Vertex>> materials,int vertices,boolean missing) { this(materials,vertices,missing,false); }
    }
    public static Mesh get(GunSnapshot snapshot) { if(snapshot==null)return MissingHolder.MESH;
        Mesh mesh=CACHE.get(snapshot);if(mesh!=null){cacheHits++;return mesh;}
        CACHE.pin(snapshot);mesh=bake(snapshot);CACHE.put(snapshot,mesh);return mesh; }
    /** Cache lookup only. World rendering must never synchronously capture a missing appearance. */
    public static Mesh peek(GunSnapshot snapshot) { return snapshot == null ? MissingHolder.MESH : CACHE.get(snapshot); }
    public static void retain(Set<GunSnapshot> active) {CACHE.pins(active);CACHE.limit(dev.kiyo.wallgun.WallGunConfig.modelCacheVertices());}
    public static long cachedVertices() {return CACHE.weight();}
    public static int cachedModels() {return CACHE.size();}
    public static void clear() { invalidations++;CACHE.clear(); GunOrientation.clear(); }
    private static Mesh bake(GunSnapshot snapshot) {
        long start = System.nanoTime();
        var stack = snapshot.copyGun();
        var id = com.tacz.guns.api.item.IGun.getIGunOrNull(stack).getGunId(stack);
        try {
            var original = TimelessAPI.getGunDisplay(stack).orElseThrow();
            // A separate display owns the mutable gun model; never capture the player's live animated model.
            // TACZ removes the lazy supplier before publishing the parsed model.
            // Its fast path can return null during that interval; join the manager lock.
            var models = ((ClientAssetsAccessor) (Object) com.tacz.guns.client.resource.ClientAssetsManager.INSTANCE).wallgun$models();
            var location = ((GunDisplayAccessor) original).wallgun$display().getModelLocation();
            if (ModelLoadBarrier.load(models, () -> models.getData(location)) == null)
                throw new IllegalStateException("Missing gun resource: " + location);
            var detached = GunDisplayInstance.create(((GunDisplayAccessor) original).wallgun$displayId(), ((GunDisplayAccessor) original).wallgun$display());
            var model = detached.getGunModel();
            if (model == null) throw new IllegalStateException("Missing gun model");
            // New detached models have not passed through TACZ's post-frame cleanup.
            // Reset animation state (including the extra reload magazine) before static capture.
            // Keep this before our pack-specific visibility overrides.
            model.cleanAnimationTransform();
            GunOrientation.prepareModel(((GunDisplayAccessor) original).wallgun$displayId(),model);
            PoseStack pose = new PoseStack();
            // ItemFrameRenderer applies these OUTSIDE the FIXED item renderer.
            // A south-facing frame turns the item 180 degrees and halves its size.
            pose.mulPose(Axis.YP.rotationDegrees(180));
            pose.scale(.5F,.5F,.5F);
            pose.scale(-1,-1,1);
            GunTransformInvoker.wallgun$position(ItemDisplayContext.FIXED, detached.getTransform().getScale(), model, pose);
            GunTransformInvoker.wallgun$scale(ItemDisplayContext.FIXED, detached.getTransform().getScale(), pose);
            MeshCapture capture = new MeshCapture();
            MeshCapture.begin(capture);
            try { model.render(pose, stack, ItemDisplayContext.FIXED, RenderType.entityCutout(detached.getModelTexture()), 0, OverlayTexture.NO_OVERLAY); }
            finally { MeshCapture.end(); }
            var raw = capture.finish();
            Mesh mesh = GunOrientation.canonical(normalize(raw), raw, ((GunDisplayAccessor) original).wallgun$displayId(), model, pose);
            bakes++;
            WallGuns.LOG.info("Wall gun baked {}: {} vertices, {} materials, {} ms", id, mesh.vertices, mesh.materials.size(), (System.nanoTime()-start)/1_000_000);
            return mesh;
        } catch (Exception | LinkageError failure) {
            failures++;
            WallGuns.LOG.error("Wall gun snapshot failed for {}; retaining visible missing marker", id, failure);
            return MissingHolder.MESH;
        }
    }
    static Mesh normalize(Map<RenderType,List<Vertex>> source) {
        source=MeshCapture.withoutDegenerateQuads(source);
        float minX=Float.POSITIVE_INFINITY,minY=minX,minZ=minX,maxX=-minX,maxY=-minX,maxZ=-minX;
        int count=0;
        for (var list:source.values()) for (Vertex v:list) {
            if (!Float.isFinite(v.x()+v.y()+v.z())) throw new IllegalStateException("Non-finite model position");
            minX=Math.min(minX,v.x());minY=Math.min(minY,v.y());minZ=Math.min(minZ,v.z());
            maxX=Math.max(maxX,v.x());maxY=Math.max(maxY,v.y());maxZ=Math.max(maxZ,v.z());count++;
        }
        if (count==0 || count>2_000_000) throw new IllegalStateException("Invalid vertex count: "+count);
        float cx=(minX+maxX)/2, cy=(minY+maxY)/2, front=minZ;
        Map<RenderType,List<Vertex>> result=new LinkedHashMap<>();
        // Translation only: retain each gun pack's item-frame scale, including large guns.
        source.forEach((type,list)->result.put(type,list.stream().map(v->v.at(.5F+v.x()-cx,.5F+v.y()-cy,WALL_GAP+v.z()-front)).toList()));
        return new Mesh(Collections.unmodifiableMap(result),count,false);
    }
    private static final class MissingHolder { private static final Mesh MESH = missing(); }
    private static Mesh missing() {
        RenderType type=RenderType.entityCutoutNoCull(MissingTextureAtlasSprite.getLocation());
        List<Vertex> vertices=List.of(new Vertex(.1F,.25F,.04F,-1,0,1,0,0,0,1),new Vertex(.9F,.25F,.04F,-1,1,1,0,0,0,1),new Vertex(.9F,.75F,.04F,-1,1,0,0,0,0,1),new Vertex(.1F,.75F,.04F,-1,0,0,0,0,0,1));
        return new Mesh(Map.of(type,vertices),4,true);
    }
}
