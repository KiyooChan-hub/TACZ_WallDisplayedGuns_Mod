package dev.kiyo.wallgun.smoke;
import dev.kiyo.wallgun.client.MeshCapture;
/** Independent oracle: observe actual buffer submission with production capture disabled. */
public final class ReferenceCapture {
    public static MeshCapture active;
    public static void begin(MeshCapture capture) {
        if (active != null || MeshCapture.active() != null) throw new IllegalStateException("Overlapping captures");
        active = capture;
    }
    public static void end() { active = null; }
}
