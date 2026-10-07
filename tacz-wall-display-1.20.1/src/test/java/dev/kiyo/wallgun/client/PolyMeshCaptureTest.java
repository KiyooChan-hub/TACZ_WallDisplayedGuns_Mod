package dev.kiyo.wallgun.client;

import com.example.taczmeshloader.core.PolyMesh;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import org.junit.jupiter.api.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import static org.junit.jupiter.api.Assertions.*;

class PolyMeshCaptureTest {
    @TestFactory Stream<DynamicTest> capturesExistingWorldGunGeometryWithoutGpu() {
        return Stream.of("ar15_anime", "ak_enact", "mcx_virtus", "mp5un")
                .map(id -> DynamicTest.dynamicTest(id, () -> {
                    Path pack = Path.of(System.getProperty("wallgun.meshPack"));
                    Assumptions.assumeTrue(Files.isRegularFile(pack), "Local gun pack is not installed");
                    try (var zip = new ZipFile(pack.toFile()); var reader = new InputStreamReader(
                            zip.getInputStream(zip.getEntry("assets/duyupack/geo_models/gun/" + id + ".json")), StandardCharsets.UTF_8)) {
                        var geometry = JsonParser.parseReader(reader).getAsJsonObject()
                                .getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
                        var description = geometry.getAsJsonObject("description");
                        MeshCapture capture = new MeshCapture();
                        int count = 0;
                        for (var element : geometry.getAsJsonArray("bones")) {
                            var bone = element.getAsJsonObject();
                            if (!bone.has("poly_mesh")) continue;
                            var pivot = bone.getAsJsonArray("pivot");
                            var mesh = new PolyMesh(bone.getAsJsonObject("poly_mesh"),
                                    description.get("texture_width").getAsFloat(),
                                    description.get("texture_height").getAsFloat(),
                                    pivot == null ? new float[3] : new float[] {pivot.get(0).getAsFloat(), pivot.get(1).getAsFloat(), pivot.get(2).getAsFloat()});
                            mesh.compileConsumer(new PoseStack().last(), capture.buffer(null), 0xF000F0, 0, 1, 1, 1, 1);
                            count += mesh.getVertexCount();
                        }
                        var raw = capture.finish();
                        assertTrue(count > 0);
                        assertEquals(count, raw.get(null).size());
                        assertEquals(0, count % 4);
                        var normalized = GunMeshes.normalize(raw);
                        assertTrue(normalized.vertices() > 0);
                        assertFalse(normalized.missing());
                        for (var v : normalized.materials().get(null)) {
                            assertTrue(Float.isFinite(v.x() + v.y() + v.z() + v.u() + v.v() + v.nx() + v.ny() + v.nz()));
                            assertEquals(0xF000F0, v.light());
                        }
                        System.out.println("Mesh capture " + id + ": " + count + " raw / " + normalized.vertices() + " retained vertices");
                    }
                }));
    }

    @Test void triangularMeshUsesCompatibleQuadTopology() {
        var json = JsonParser.parseString("""
                {"normalized_uvs":true,"positions":[[0,0,0],[16,0,0],[0,16,0]],
                 "normals":[[0,0,1]],"uvs":[[0,0],[1,0],[0,1]],
                 "polys":[[[0,0,0],[1,0,1],[2,0,2]]]}
                """).getAsJsonObject();
        var mesh = new PolyMesh(json, 16, 16, new float[3]);
        MeshCapture capture = new MeshCapture();
        mesh.compileConsumer(new PoseStack().last(), capture.buffer(null), 0, 0, 1, 1, 1, 1);
        assertEquals(4, mesh.getVertexCount());
        assertEquals(4, GunMeshes.normalize(capture.finish()).vertices());
    }
}
