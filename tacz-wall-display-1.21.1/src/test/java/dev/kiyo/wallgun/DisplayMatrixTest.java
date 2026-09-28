package dev.kiyo.wallgun;
import net.minecraft.core.Direction;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DisplayMatrixTest {
    @Test void matrixMatchesCpuGeometryOnEveryFaceAndPose(){
        for(var face:Direction.values())for(int roll=0;roll<16;roll++)for(boolean flip:new boolean[]{false,true}){
            var pose=new DisplayPose(face,roll,flip,.001F,.93F);
            var matrix=pose.matrix();
            for(var input:new Vector3f[]{new Vector3f(-.7F,1.8F,.9F),new Vector3f(.2F,.6F,.04F)}){
                assertTrue(pose.point(input.x,input.y,input.z).distance(matrix.transformPosition(new Vector3f(input)))<.00001F);
                assertTrue(pose.normal(input.x,input.y,input.z).distance(matrix.transformDirection(new Vector3f(input)))<.00001F);
            }
            var normal=new Vector3f(.2F,.7F,.5F).normalize();var light=new Vector3f(.4F,1,.2F).normalize();
            var worldNormal=pose.normal(normal.x,normal.y,normal.z);
            var localLight=new org.joml.Matrix3f(matrix).transpose().transform(new Vector3f(light));
            assertEquals(worldNormal.dot(light),normal.dot(localLight),.00001F);
            assertEquals(1,matrix.determinant(),.00001F);
        }
    }
}
