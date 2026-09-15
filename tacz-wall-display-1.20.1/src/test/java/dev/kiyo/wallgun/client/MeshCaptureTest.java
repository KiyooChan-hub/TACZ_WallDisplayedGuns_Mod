package dev.kiyo.wallgun.client;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MeshCaptureTest {
    @Test void preservesArbitraryRotationUvNormalAndEmissiveLight() {
        MeshCapture capture=new MeshCapture();var out=capture.buffer(null);
        float angle=(float)Math.toRadians(13.7),x=(float)Math.cos(angle),y=(float)Math.sin(angle);
        for(int i=0;i<4;i++) out.vertex(x+i,y,-.8F).color(11,22,33,255).uv(.123F,.987F).uv2(240,240).normal(x,y,0);
        var vertices=capture.finish().get(null);
        assertEquals(4,vertices.size());var v=vertices.get(0);
        assertEquals(x,v.x());assertEquals(y,v.y());assertEquals(x,v.nx());assertEquals(.123F,v.u());assertEquals(0x00F000F0,v.light());assertEquals(0xFF0B1621,v.color());
    }
    @Test void normalizesWallGapWithoutQuantizingAnglesOrUv() {
        MeshCapture capture=new MeshCapture();var out=capture.buffer(null);
        for(int i=0;i<4;i++) {int x=i==1||i==2?1:0,y=i>=2?1:0;out.vertex(-.63F+x*.31F,-.42F+y*.21F,-.25F+x*.015F).color(-1).uv(.12F,.78F).uv2(0,0).normal(.3F,.4F,.866F);}
        var mesh=GunMeshes.normalize(capture.finish());var vertices=mesh.materials().get(null);
        assertEquals(GunMeshes.WALL_GAP,vertices.get(0).z(),.00001);
        assertEquals(.31F,vertices.get(1).x()-vertices.get(0).x(),.00001);
        assertEquals(.12F,vertices.get(0).u());assertEquals(.866F,vertices.get(0).nz());
    }
    @Test void rejectsIncompleteQuadsAndUnwindsCaptureScope() {
        MeshCapture capture=new MeshCapture();MeshCapture.begin(capture);
        try { assertSame(capture,MeshCapture.active());capture.buffer(null).vertex(1,2,3);assertThrows(IllegalStateException.class,capture::finish); }
        finally {MeshCapture.end();}
        assertNull(MeshCapture.active());
    }
    @Test void retainsGunPackScaleForOversizedFrameModels() {
        MeshCapture capture=new MeshCapture();var out=capture.buffer(null);
        for(int i=0;i<4;i++)out.vertex(i==1||i==2?6:0,i>=2?3:0,0).normal(0,0,1);
        var vertices=GunMeshes.normalize(capture.finish()).materials().get(null);
        assertEquals(6F,vertices.get(1).x()-vertices.get(0).x(),.00001);
        assertEquals(3F,vertices.get(vertices.size()-1).y()-vertices.get(0).y(),.00001);
    }
    @Test void zeroScaleHiddenPartsCannotPushTheVisibleGunAwayFromTheWall() {
        MeshCapture capture=new MeshCapture();var out=capture.buffer(null);
        for(int i=0;i<4;i++)out.vertex(100,-50,-20).uv(0,0).normal(0,0,1);
        for(int i=0;i<4;i++)out.vertex(i==1||i==2?2:0,i>=2?1:0,.25F).uv(.2F,.3F).normal(0,0,1);
        var mesh=GunMeshes.normalize(capture.finish());var v=mesh.materials().get(null);
        assertEquals(4,mesh.vertices());
        assertEquals(-.5F,v.get(0).x(),.00001);assertEquals(0F,v.get(0).y(),.00001);
        for(var vertex:v)assertEquals(.001F,vertex.z(),.00001);
        assertEquals(.2F,v.get(0).u());
    }
    @Test void triangularQuadRetainsItsNonzeroSecondTriangle() {
        MeshCapture capture=new MeshCapture();var out=capture.buffer(null);
        out.vertex(0,0,0);out.vertex(0,0,0);out.vertex(1,0,0);out.vertex(0,1,0);
        assertEquals(4,MeshCapture.withoutDegenerateQuads(capture.finish()).get(null).size());
    }
}
