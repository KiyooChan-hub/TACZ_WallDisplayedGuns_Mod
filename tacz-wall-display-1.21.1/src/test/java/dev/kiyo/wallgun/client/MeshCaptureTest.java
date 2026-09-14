package dev.kiyo.wallgun.client;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MeshCaptureTest {
    @Test void preservesArbitraryRotationUvNormalAndEmissiveLight() {
        MeshCapture capture=new MeshCapture();var out=capture.buffer(null);
        float angle=(float)Math.toRadians(13.7),x=(float)Math.cos(angle),y=(float)Math.sin(angle);
        for(int i=0;i<4;i++) out.addVertex(x+i,y,-.8F).setColor(11,22,33,255).setUv(.123F,.987F).setUv2(240,240).setNormal(x,y,0);
        var vertices=capture.finish().get(null);
        assertEquals(4,vertices.size());var v=vertices.getFirst();
        assertEquals(x,v.x());assertEquals(y,v.y());assertEquals(x,v.nx());assertEquals(.123F,v.u());assertEquals(0x00F000F0,v.light());assertEquals(0xFF0B1621,v.color());
    }
    @Test void normalizesWallGapWithoutQuantizingAnglesOrUv() {
        MeshCapture capture=new MeshCapture();var out=capture.buffer(null);
        for(int i=0;i<4;i++) {int x=i==1||i==2?1:0,y=i>=2?1:0;out.addVertex(-.63F+x*.31F,-.42F+y*.21F,-.25F+x*.015F).setColor(-1).setUv(.12F,.78F).setUv2(0,0).setNormal(.3F,.4F,.866F);}
        var mesh=GunMeshes.normalize(capture.finish());var vertices=mesh.materials().get(null);
        assertEquals(GunMeshes.WALL_GAP,vertices.getFirst().z(),.00001);
        assertEquals(.31F,vertices.get(1).x()-vertices.get(0).x(),.00001);
        assertEquals(.12F,vertices.getFirst().u());assertEquals(.866F,vertices.getFirst().nz());
    }
    @Test void rejectsIncompleteQuadsAndUnwindsCaptureScope() {
        MeshCapture capture=new MeshCapture();MeshCapture.begin(capture);
        try { assertSame(capture,MeshCapture.active());capture.buffer(null).addVertex(1,2,3);assertThrows(IllegalStateException.class,capture::finish); }
        finally {MeshCapture.end();}
        assertNull(MeshCapture.active());
    }
    @Test void retainsGunPackScaleForOversizedFrameModels() {
        MeshCapture capture=new MeshCapture();var out=capture.buffer(null);
        for(int i=0;i<4;i++)out.addVertex(i==1||i==2?6:0,i>=2?3:0,0).setNormal(0,0,1);
        var vertices=GunMeshes.normalize(capture.finish()).materials().get(null);
        assertEquals(6F,vertices.get(1).x()-vertices.getFirst().x(),.00001);
        assertEquals(3F,vertices.getLast().y()-vertices.getFirst().y(),.00001);
    }
    @Test void zeroScaleHiddenPartsCannotPushTheVisibleGunAwayFromTheWall() {
        MeshCapture capture=new MeshCapture();var out=capture.buffer(null);
        for(int i=0;i<4;i++)out.addVertex(100,-50,-20).setUv(0,0).setNormal(0,0,1);
        for(int i=0;i<4;i++)out.addVertex(i==1||i==2?2:0,i>=2?1:0,.25F).setUv(.2F,.3F).setNormal(0,0,1);
        var mesh=GunMeshes.normalize(capture.finish());var v=mesh.materials().get(null);
        assertEquals(4,mesh.vertices());
        assertEquals(-.5F,v.getFirst().x(),.00001);assertEquals(0F,v.getFirst().y(),.00001);
        for(var vertex:v)assertEquals(.001F,vertex.z(),.00001);
        assertEquals(.2F,v.getFirst().u());
    }
    @Test void triangularQuadRetainsItsNonzeroSecondTriangle() {
        MeshCapture capture=new MeshCapture();var out=capture.buffer(null);
        out.addVertex(0,0,0);out.addVertex(0,0,0);out.addVertex(1,0,0);out.addVertex(0,1,0);
        assertEquals(4,MeshCapture.withoutDegenerateQuads(capture.finish()).get(null).size());
    }
}
