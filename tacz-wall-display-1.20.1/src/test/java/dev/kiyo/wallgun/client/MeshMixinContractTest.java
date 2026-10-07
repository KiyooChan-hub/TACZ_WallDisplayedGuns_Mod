package dev.kiyo.wallgun.client;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

/** Offline ABI check against the installed TML. Does not claim to replace an in-game Mixin test. */
class MeshMixinContractTest {
    private static ClassNode read(String name) throws IOException {
        var node = new ClassNode();
        try (var in = MeshMixinContractTest.class.getResourceAsStream("/" + name.replace('.', '/') + ".class")) {
            assertNotNull(in, name);
            new ClassReader(in).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        return node;
    }

    @Test void optionalGunAndAttachmentTargetsMatchInjectedSignatures() throws Exception {
        for (String kind : new String[] {"Gun", "Attachment"}) {
            var mixin = read("dev.kiyo.wallgun.mixin.Mesh" + kind + "CaptureMixin");
            var target = read("com.example.taczmeshloader.tacz.TaczPolyMesh" + kind + "Model");
            assertEquals(target.superName, mixin.superName);
            assertTrue(mixin.invisibleAnnotations.stream().anyMatch(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/Pseudo;")));
            var injection = mixin.methods.stream().filter(m -> m.name.startsWith("wallgun$capture")).findFirst().orElseThrow();
            var descriptor = injection.desc.replace("Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;", "");
            assertTrue(target.methods.stream().anyMatch(m -> m.name.equals("render") && m.desc.equals(descriptor)));
            for (var field : mixin.fields) {
                if (field.visibleAnnotations == null || field.visibleAnnotations.stream().noneMatch(a -> a.desc.endsWith("/Shadow;"))) continue;
                assertTrue(target.fields.stream().anyMatch(f -> f.name.equals(field.name) && f.desc.equals(field.desc)), field.name);
            }
        }
    }

    @Test void consumerAndScopeStateApiPresent() throws Exception {
        var model = read("com.example.taczmeshloader.core.PolyMeshModel");
        assertTrue(model.methods.stream().anyMatch(m -> m.name.equals("renderWithTranslucentSplit")
                && m.desc.equals("(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/resources/ResourceLocation;IIZ)V")));
        for (String field : new String[] {"ocularBonesHidden", "ocularRingBonesHidden"})
            assertTrue(model.fields.stream().anyMatch(f -> f.name.equals(field) && f.desc.equals("Z")));
        var attachment = read("com.example.taczmeshloader.tacz.TaczPolyMeshAttachmentModel");
        assertTrue(attachment.methods.stream().anyMatch(m -> m.name.equals("restorePartVisibilityForPolyMesh") && m.desc.equals("()V")));
    }
}
