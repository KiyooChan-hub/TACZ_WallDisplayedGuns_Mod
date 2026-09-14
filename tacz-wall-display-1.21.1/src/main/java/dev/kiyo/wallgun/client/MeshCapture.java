package dev.kiyo.wallgun.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.RenderType;
import java.util.*;

public final class MeshCapture {
    private static final ThreadLocal<MeshCapture> ACTIVE = new ThreadLocal<>();
    private final Map<RenderType, Collector> collectors = new LinkedHashMap<>();
    public static MeshCapture active() { return ACTIVE.get(); }
    public static void begin(MeshCapture capture) { if (active() != null) throw new IllegalStateException("Nested snapshot"); ACTIVE.set(capture); }
    public static void end() { ACTIVE.remove(); }
    public VertexConsumer buffer(RenderType type) { return collectors.computeIfAbsent(type, ignored -> new Collector()); }
    public Map<RenderType, List<Vertex>> finish() {
        Map<RenderType, List<Vertex>> result = new LinkedHashMap<>();
        collectors.forEach((type, collector) -> {
            collector.flush();
            if (collector.vertices.size() % 4 != 0) throw new IllegalStateException("Non-quad material " + type);
            if (!collector.vertices.isEmpty()) result.put(type, List.copyOf(collector.vertices));
        });
        return result;
    }
    public record Vertex(float x, float y, float z, int color, float u, float v, int light, float nx, float ny, float nz) {
        public Vertex at(float x, float y, float z) { return new Vertex(x,y,z,color,u,v,light,nx,ny,nz); }
    }
    /** Zero-scale hidden parts emit quads too. They have no rasterized surface or physical bounds. */
    public static Map<RenderType,List<Vertex>> withoutDegenerateQuads(Map<RenderType,List<Vertex>> source) {
        Map<RenderType,List<Vertex>> result=new LinkedHashMap<>();
        source.forEach((type,vertices)->{
            if(vertices.size()%4!=0)throw new IllegalStateException("Incomplete quad");
            List<Vertex> kept=new ArrayList<>();
            for(int i=0;i<vertices.size();i+=4) {
                Vertex a=vertices.get(i),b=vertices.get(i+1),c=vertices.get(i+2),d=vertices.get(i+3);
                for(Vertex v:List.of(a,b,c,d))if(!Float.isFinite(v.x()+v.y()+v.z()))throw new IllegalStateException("Non-finite model position");
                if(areaSquared(a,b,c)>1e-20 || areaSquared(a,c,d)>1e-20)kept.addAll(vertices.subList(i,i+4));
            }
            if(!kept.isEmpty())result.put(type,List.copyOf(kept));
        });
        return result;
    }
    private static double areaSquared(Vertex a, Vertex b, Vertex c) {
        double ux=(double)b.x()-a.x(),uy=(double)b.y()-a.y(),uz=(double)b.z()-a.z();
        double vx=(double)c.x()-a.x(),vy=(double)c.y()-a.y(),vz=(double)c.z()-a.z();
        double x=uy*vz-uz*vy,y=uz*vx-ux*vz,z=ux*vy-uy*vx;
        return x*x+y*y+z*z;
    }
    private static final class Collector implements VertexConsumer {
        final List<Vertex> vertices = new ArrayList<>();
        boolean pending;
        float x,y,z,u,v,nx,ny,nz;
        int color = -1, light;
        void flush() { if (pending) { vertices.add(new Vertex(x,y,z,color,u,v,light,nx,ny,nz)); pending=false; } }
        @Override public VertexConsumer addVertex(float x,float y,float z) { flush(); this.x=x;this.y=y;this.z=z; pending=true;return this; }
        @Override public VertexConsumer setColor(int r,int g,int b,int a) { color=(a<<24)|(r<<16)|(g<<8)|b;return this; }
        @Override public VertexConsumer setUv(float u,float v) {this.u=u;this.v=v;return this;}
        @Override public VertexConsumer setUv1(int u,int v) {return this;}
        @Override public VertexConsumer setUv2(int u,int v) {light=(v<<16)|u;return this;}
        @Override public VertexConsumer setNormal(float x,float y,float z) {nx=x;ny=y;nz=z;return this;}
    }
}
