package dev.kiyo.wallgun;

import net.minecraft.core.Direction;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DisplayPoseTest {
    @Test void everyFaceRollAndSideIsRigidAndKeepsContactDepth() {
        for(Direction face:Direction.values())for(int roll=0;roll<16;roll++)for(boolean flip:new boolean[]{false,true}) {
            var pose=new DisplayPose(face,roll,flip,.001F,.217F);
            Vector3f a=pose.point(-.6F,.12F,.001F),b=pose.point(1.6F,.88F,.217F);
            assertEquals(new Vector3f(-.6F,.12F,.001F).distance(1.6F,.88F,.217F),a.distance(b),.00001);
            var nx=pose.normal(1,0,0);var ny=pose.normal(0,1,0);var nz=pose.normal(0,0,1);
            assertEquals(1,nx.length(),.00001);assertEquals(0,nx.dot(ny),.00001);
            assertEquals(1,new Vector3f(nx).cross(ny).dot(nz),.00001,"Transform must not mirror UV/winding");
            float da=depth(face,a),db=depth(face,b);
            assertEquals(.001,Math.min(da,db),.00001);assertEquals(.217,Math.max(da,db),.00001);
        }
    }
    @Test void clockwiseAndFixedVerticalFlipHaveTheExpectedOrder() {
        var quarter=new DisplayPose(Direction.SOUTH,4,false,0,.1F);
        assertTrue(quarter.normal(0,1,0).distance(1,0,0)<.00001);
        for(int r=0;r<16;r++)for(boolean f:new boolean[]{false,true}) {
            var before=new DisplayPose(Direction.SOUTH,r,f,0,.1F).normal(.3F,.7F,.2F);
            var after=new DisplayPose(Direction.SOUTH,-r,!f,0,.1F).normal(.3F,.7F,.2F);
            assertTrue(after.distance(-before.x(),before.y(),-before.z())<.00001);
        }
    }
    @Test void floorAndCeilingHeadingDefinesTheFlipAxis() {
        for(Direction face:new Direction[]{Direction.UP,Direction.DOWN})for(int heading=0;heading<16;heading+=4)for(int r=0;r<16;r++) {
            var plate=new DisplayPose(face,heading,false,0,.1F);
            var local=new DisplayPose(Direction.SOUTH,-r,true,0,.1F).normal(.3F,.7F,.2F);
            var expected=plate.normal(local.x(),local.y(),local.z());
            var actual=new DisplayPose(face,heading-r,true,0,.1F).normal(.3F,.7F,.2F);
            assertTrue(actual.distance(expected)<.00001,"Heading must not reverse on flip");
        }
    }
    private float depth(Direction face,Vector3f p) {
        return switch(face){case SOUTH->p.z();case NORTH->1-p.z();case EAST->p.x();case WEST->1-p.x();case UP->p.y();case DOWN->1-p.y();};
    }
}
