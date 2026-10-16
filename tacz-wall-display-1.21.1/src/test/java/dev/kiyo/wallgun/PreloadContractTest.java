package dev.kiyo.wallgun;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;
class PreloadContractTest {
    private ClassNode type(String name) throws IOException {
        try(var in=getClass().getClassLoader().getResourceAsStream("dev/kiyo/wallgun/"+name+".class")) {
            assertNotNull(in);var node=new ClassNode();new ClassReader(in).accept(node,0);return node;
        }
    }
    @Test void renderingNeverSynchronouslyCapturesModels() throws IOException {
        for(var name:new String[]{"client/WallGunRenderer","client/CreateMovingGuns"})for(var m:type(name).methods)
            if(m.name.equals("render")||m.name.equals("renderIfVirtual"))for(var i:m.instructions)
                if(i instanceof MethodInsnNode call)assertFalse(call.owner.endsWith("/GunMeshes") && (call.name.equals("get")||call.name.equals("prepare")));
    }
    @Test void offerCannotResetBudgetAndClientDoesNotForceGpuFinish() throws IOException {
        for(var m:type("client/LoadingClient").methods)for(var i:m.instructions)if(i instanceof MethodInsnNode call) {
            assertNotEquals("glFinish",call.name);
            if(m.name.equals("acceptOffer"))assertNotEquals("startBudget",call.name);
        }
    }
    @Test void preparationTickNeverDisconnectsOrRequestsChunkGeneration() throws IOException {
        for(var m:type("LoadingSessions").methods)if(m.name.equals("tick"))for(var i:m.instructions)
            if(i instanceof MethodInsnNode call) {
                assertNotEquals("disconnect",call.name);assertNotEquals("fail",call.name);
                if(call.owner.contains("ServerChunkCache"))assertNotEquals("getChunk",call.name);
            }
    }
}
