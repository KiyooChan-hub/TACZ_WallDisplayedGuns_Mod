package dev.kiyo.wallgun;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import java.io.IOException;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

/** Offline bytecode contracts, not a substitute for an applied-Mixin game test. */
class LoadingHookContractTest {
    private static MethodNode method(String owner,String name) throws IOException {
        try(var stream=LoadingHookContractTest.class.getClassLoader().getResourceAsStream(owner+".class")) {
            assertNotNull(stream,owner);
            var type=new ClassNode();new ClassReader(stream).accept(type,0);
            return type.methods.stream().filter(m->m.name.equals(name)).findFirst().orElseThrow();
        }
    }
    @Test void travelHookExistsAfterEarlyReturnsAndBeforeRespawnPacket() throws IOException {
        var method=method("net/minecraft/server/level/ServerPlayer","changeDimension");
        int writes=0,returns=0;boolean travelEvent=false,respawnPacket=false;
        for(var insn:method.instructions) {
            if(insn instanceof MethodInsnNode call && call.name.equals("onTravelToDimension"))travelEvent=true;
            if(insn.getOpcode()==Opcodes.ARETURN)returns++;
            if(insn instanceof TypeInsnNode type && type.desc.endsWith("/ClientboundRespawnPacket"))respawnPacket=true;
            if(insn instanceof FieldInsnNode field && field.getOpcode()==Opcodes.PUTFIELD && field.name.equals("isChangingDimension")) {
                writes++;assertTrue(travelEvent);assertTrue(returns>=3,"cancelled, removed and same-dimension returns");
                assertFalse(respawnPacket,"must protect before client gets respawn packet");
            }
        }
        assertEquals(1,writes);assertTrue(respawnPacket);
    }
    @Test void releaseRemovesExactSessionBeforeTeleportAndAcknowledgement() throws IOException {
        var method=method("dev/kiyo/wallgun/LoadingSessions","release");
        boolean removed=false,sent=false;
        for(var insn:method.instructions)if(insn instanceof MethodInsnNode call) {
            if(call.owner.equals("java/util/Map") && call.name.equals("remove")
                && call.desc.equals("(Ljava/lang/Object;Ljava/lang/Object;)Z"))removed=true;
            if(call.name.equals("teleport"))assertTrue(removed);
            if(call.owner.equals("dev/kiyo/wallgun/LoadingPayloads") && call.name.equals("send")) {
                assertTrue(removed);sent=true;
            }
        }
        assertTrue(removed);assertTrue(sent);
    }
    @Test void commandKillBranchRunsBeforeLoadingDamageGuard() throws IOException {
        for(var name:new String[]{"noDamage","protectedPlayer"}) {
            var method=method("dev/kiyo/wallgun/mixin/LoadingPlayerMixin",name);
            var calls=new ArrayList<String>();boolean genericKill=false;
            for(var insn:method.instructions) {
                if(insn instanceof FieldInsnNode field && field.name.equals("GENERIC_KILL"))genericKill=true;
                if(insn instanceof MethodInsnNode call && call.owner.equals("dev/kiyo/wallgun/LoadingSessions"))calls.add(call.name);
            }
            assertTrue(genericKill);assertEquals(java.util.List.of("commandKill","waiting"),calls);
        }
    }
}
