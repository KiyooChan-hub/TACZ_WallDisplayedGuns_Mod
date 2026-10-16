package dev.kiyo.wallgun;

/** A square of chunks, inclusive of the center; never a request to generate chunks. */
public final class PreloadScope {
    private PreloadScope() {}
    public static boolean contains(int centerX,int centerZ,int x,int z,int radius) {
        return radius>=0 && Math.abs((long)x-centerX)<=radius && Math.abs((long)z-centerZ)<=radius;
    }
}
