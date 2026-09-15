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
    public Vector3f point(float x,float y,float z) {
        Vector3f v=normal(x-.5F,y-.5F,z-depthSum/2);
        return switch(facing) {
            case SOUTH -> v.add(.5F,.5F,depthSum/2);
            case NORTH -> v.add(.5F,.5F,1-depthSum/2);
            case EAST -> v.add(depthSum/2,.5F,.5F);
            case WEST -> v.add(1-depthSum/2,.5F,.5F);
            case UP -> v.add(.5F,depthSum/2,.5F);
            case DOWN -> v.add(.5F,1-depthSum/2,.5F);
        };
    }
    public Vector3f normal(float x,float y,float z) {
        if(flipped) {x=-x;z=-z;}
        float rx=cos*x+sin*y, ry=-sin*x+cos*y;
        return switch(facing) {
            case SOUTH -> new Vector3f(rx,ry,z);
            case NORTH -> new Vector3f(-rx,ry,-z);
            case EAST -> new Vector3f(z,ry,-rx);
            case WEST -> new Vector3f(-z,ry,rx);
            case UP -> new Vector3f(rx,z,-ry);
            case DOWN -> new Vector3f(rx,-z,ry);
        };
    }
}
