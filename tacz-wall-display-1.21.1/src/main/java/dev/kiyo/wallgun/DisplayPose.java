package dev.kiyo.wallgun;

import net.minecraft.core.Direction;
import org.joml.Vector3f;

/** Rigid transforms only; texture coordinates and triangle winding are preserved. */
public final class DisplayPose {
    private final float cos, sin;
    private final boolean flipped;
    private final Direction facing;
    private final float depthSum;
    public DisplayPose(Direction facing, int roll, boolean flipped, float minDepth, float maxDepth) {
        this.facing=facing; this.flipped=flipped; depthSum=minDepth+maxDepth;
        double angle=Math.floorMod(roll,16)*Math.PI/8;
        cos=(float)Math.cos(angle); sin=(float)Math.sin(angle);
    }
    /** Build the same affine transform as point/normal, including all six mounting faces. */
    public org.joml.Matrix4f matrix() {
        var x=normal(1,0,0);var y=normal(0,1,0);var z=normal(0,0,1);var p=point(0,0,0);
        return new org.joml.Matrix4f().set(
            x.x,x.y,x.z,0, y.x,y.y,y.z,0, z.x,z.y,z.z,0, p.x,p.y,p.z,1);
    }
    public Vector3f point(float x,float y,float z) { return point(x,y,z,new Vector3f()); }
    public Vector3f point(float x,float y,float z,Vector3f target) {
        Vector3f v=normal(x-.5F,y-.5F,z-depthSum/2,target);
        return switch(facing) {
            case SOUTH -> v.add(.5F,.5F,depthSum/2);
            case NORTH -> v.add(.5F,.5F,1-depthSum/2);
            case EAST -> v.add(depthSum/2,.5F,.5F);
            case WEST -> v.add(1-depthSum/2,.5F,.5F);
            case UP -> v.add(.5F,depthSum/2,.5F);
            case DOWN -> v.add(.5F,1-depthSum/2,.5F);
        };
    }
    public Vector3f normal(float x,float y,float z) { return normal(x,y,z,new Vector3f()); }
    public Vector3f normal(float x,float y,float z,Vector3f target) {
        if(flipped) {x=-x;z=-z;}
        float rx=cos*x+sin*y, ry=-sin*x+cos*y;
        return switch(facing) {
            case SOUTH -> target.set(rx,ry,z);
            case NORTH -> target.set(-rx,ry,-z);
            case EAST -> target.set(z,ry,-rx);
            case WEST -> target.set(-z,ry,rx);
            case UP -> target.set(rx,z,-ry);
            case DOWN -> target.set(rx,-z,ry);
        };
    }
}
