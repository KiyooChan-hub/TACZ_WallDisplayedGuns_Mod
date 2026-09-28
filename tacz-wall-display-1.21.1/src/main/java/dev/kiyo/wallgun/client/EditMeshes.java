package dev.kiyo.wallgun.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.kiyo.wallgun.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import java.util.*;

/** Reusable local-space geometry. Editing changes the draw matrix, never the vertex data. */
public final class EditMeshes {
    private record Key(GunMeshes.Mesh mesh,int light) {
        @Override public int hashCode(){return 31*System.identityHashCode(mesh)+light;}
        @Override public boolean equals(Object value){return value instanceof Key k && k.mesh==mesh && k.light==light;}
    }
    private static final Map<Key,Bundle> CACHE=new LinkedHashMap<>();
    private static final Set<Key> REQUESTED=new HashSet<>();
    private static final class Bundle {
        final Map<RenderType,VertexBuffer> buffers=new LinkedHashMap<>();
        boolean primed;
        long used=System.nanoTime();
        void close(){buffers.values().forEach(VertexBuffer::close);}
    }
    public static int uploads,draws;
    public static int cachedBundles(){return CACHE.size();}
    public static long cachedVertices(){return CACHE.keySet().stream().mapToLong(k->k.mesh.vertices()).sum();}
    public static void begin(){REQUESTED.clear();draws=0;}
    public static void request(GunMeshes.Mesh mesh,int light){
        var key=new Key(mesh,light);REQUESTED.add(key);CACHE.computeIfAbsent(key,k->new Bundle()).used=System.nanoTime();
    }
    public static boolean ready(GunMeshes.Mesh mesh,int light){
        var b=CACHE.get(new Key(mesh,light));return b!=null && b.primed;
    }
    public static void prepare(WorkBudget budget) {
        int count=0;long now=System.nanoTime();long weight=0;
        for(var key:CACHE.keySet())weight+=key.mesh.vertices();
        var it=CACHE.entrySet().iterator();
        while(it.hasNext()){
            var entry=it.next();var key=entry.getKey();var bundle=entry.getValue();
            if(!REQUESTED.contains(key) && (now-bundle.used>10_000_000_000L || weight>4_000_000)){
                weight-=key.mesh.vertices();bundle.close();it.remove();continue;
            }
            if(!REQUESTED.contains(key))continue;
            for(var material:key.mesh.materials().entrySet()){
                if(bundle.buffers.containsKey(material.getKey()))continue;
                if(count>=2 || !budget.start())return;
                try(var storage=new ByteBufferBuilder(65536)){
                    var out=new BufferBuilder(storage,VertexFormat.Mode.QUADS,DefaultVertexFormat.NEW_ENTITY);
                    for(var v:material.getValue())out.addVertex(v.x(),v.y(),v.z()).setColor(v.color())
                        .setUv(v.u(),v.v()).setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(v.light()==0?key.light:v.light()).setNormal(v.nx(),v.ny(),v.nz());
                    var data=out.build();
                    if(data!=null){
                        var buffer=new VertexBuffer(VertexBuffer.Usage.STATIC);
                        try {buffer.bind();buffer.upload(data);bundle.buffers.put(material.getKey(),buffer);}
                        catch(RuntimeException ex){buffer.close();throw ex;}
                        finally {VertexBuffer.unbind();}
                        uploads++;count++;
                    }
                }
            }
        }
    }
    public static void prime(RenderLevelStageEvent event){
        var budget=new WorkBudget(1_000_000);int count=0;
        for(var key:REQUESTED){
            var bundle=CACHE.get(key);
            if(bundle.primed || bundle.buffers.size()!=key.mesh.materials().size())continue;
            if(count++>=2 || !budget.start())break;
            // All materials become visible together, after the real shader pass has been exercised.
            draw(bundle,new Matrix4f(event.getModelViewMatrix()).translate(0,-100000,0),event);
            bundle.primed=true;
        }
    }
    public static boolean render(GunMeshes.Mesh mesh,int light,DisplayPose pose,
                              net.minecraft.core.BlockPos pos,RenderLevelStageEvent event){
        var bundle=CACHE.get(new Key(mesh,light));if(bundle==null || !bundle.primed)return false;
        var camera=event.getCamera().getPosition();
        var matrix=new Matrix4f(event.getModelViewMatrix()).translate((float)(pos.getX()-camera.x),
            (float)(pos.getY()-camera.y),(float)(pos.getZ()-camera.z)).mul(pose.matrix());
        // Vanilla entity shaders light local normals before applying ModelViewMat.
        // Inverse-rotate their light vectors so lighting matches the CPU-transformed static batches.
        var lights=dev.kiyo.wallgun.mixin.RenderLightsAccessor.wallgun$lights();
        var first=lights[0];var second=lights[1];
        var inverse=new org.joml.Matrix3f(pose.matrix()).transpose();
        RenderSystem.setShaderLights(inverse.transform(new org.joml.Vector3f(first)),inverse.transform(new org.joml.Vector3f(second)));
        try {draw(bundle,matrix,event);draws+=bundle.buffers.size();return true;}
        finally {RenderSystem.setShaderLights(first,second);}
    }
    private static void draw(Bundle bundle,Matrix4f matrix,RenderLevelStageEvent event){
        for(var entry:bundle.buffers.entrySet()){
            entry.getKey().setupRenderState();
            try {entry.getValue().bind();entry.getValue().drawWithShader(matrix,event.getProjectionMatrix(),RenderSystem.getShader());}
            finally {VertexBuffer.unbind();entry.getKey().clearRenderState();}
        }
    }
    public static void clear(){CACHE.values().forEach(Bundle::close);CACHE.clear();REQUESTED.clear();}
}
