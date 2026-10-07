package dev.kiyo.wallgun;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import java.io.IOException;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class LoadingGunPacketContractTest {
    private static ClassNode type(String name) throws IOException {
        try(var stream=LoadingGunPacketContractTest.class.getClassLoader().getResourceAsStream(name+".class")) {
            assertNotNull(stream,name);var node=new ClassNode();new ClassReader(stream).accept(node,0);return node;
        }
    }
    @Test void recoveryRedirectMatchesExactlyOneRealTaczDrawSend() throws IOException {
        var mixin=type("dev/kiyo/wallgun/mixin/LoadingGunDrawMixin");
        var m=mixin.methods.stream().filter(n->n.name.equals("noRecoveryEcho")).findFirst().orElseThrow();
        var annotations=new ArrayList<AnnotationNode>();
        if(m.visibleAnnotations!=null)annotations.addAll(m.visibleAnnotations);
        if(m.invisibleAnnotations!=null)annotations.addAll(m.invisibleAnnotations);
        var redirect=annotations.stream().filter(n->n.desc.endsWith("/Redirect;")).findFirst().orElseThrow();
        AnnotationNode at=(AnnotationNode)redirect.values.get(redirect.values.indexOf("at")+1);
        String target=(String)at.values.get(at.values.indexOf("target")+1);
        int matches=0;
        for(var method:type("com/tacz/guns/client/gameplay/LocalPlayerDraw").methods)
            if(method.name.equals("draw"))for(var insn:method.instructions)if(insn instanceof MethodInsnNode call
                && target.equals("L"+call.owner+";"+call.name+call.desc))matches++;
        assertEquals(1,matches);
    }
    @Test void recoveryUsesDrawOnlyAndNeverShoots() throws IOException {
        int draws=0;
        for(var method:type("dev/kiyo/wallgun/client/LoadingGunClient").methods)
            if(method.name.equals("restoredDraw"))for(var insn:method.instructions)if(insn instanceof MethodInsnNode call) {
                assertNotEquals("shoot",call.name);assertNotEquals("melee",call.name);
                if(call.name.equals("draw"))draws++;
            }
        assertEquals(1,draws);
    }
    @Test void forgeAdapterUsesRegisteredCodecAndDoesNotConsumeIncomingBytes() throws IOException {
        boolean codec=false,peek=false,defer=false;
        for(var method:type("dev/kiyo/wallgun/LoadingGunPackets").methods)for(var insn:method.instructions)
            if(insn instanceof MethodInsnNode call) {
                if(method.name.equals("encodedId") && call.name.equals("encodeMessage"))codec=true;
                if(method.name.equals("block")) {
                    assertNotEquals("readUnsignedByte",call.name);assertNotEquals("readByte",call.name);
                    if(call.name.equals("getUnsignedByte"))peek=true;
                    if(call.name.equals("deferGunDraw"))defer=true;
                }
            }
        assertTrue(codec && peek && defer);
    }
}
