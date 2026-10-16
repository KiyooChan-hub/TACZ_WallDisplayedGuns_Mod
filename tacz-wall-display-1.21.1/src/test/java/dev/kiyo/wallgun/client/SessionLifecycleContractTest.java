package dev.kiyo.wallgun.client;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Structural regression guards; no claim of a real OpenGL/gameplay test. */
class SessionLifecycleContractTest {
    private static ClassNode read(String name)throws Exception{
        var node=new ClassNode();
        try(var in=getClassResource(name)){
            assertNotNull(in,name);new ClassReader(in).accept(node,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
        }
        return node;
    }
    private static java.io.InputStream getClassResource(String name){
        return SessionLifecycleContractTest.class.getResourceAsStream("/dev/kiyo/wallgun/"+name+".class");
    }
    private static List<MethodInsnNode> calls(String owner,String method)throws Exception{
        var result=new ArrayList<MethodInsnNode>();
        for(var m:read(owner).methods)if(m.name.equals(method))
            for(var i:m.instructions)if(i instanceof MethodInsnNode c)result.add(c);
        assertTrue(read(owner).methods.stream().anyMatch(m->m.name.equals(method)),method);
        return result;
    }
    @Test void worldChangesNeverReleaseReusableGeometry()throws Exception{
        for(var target:List.of(new String[]{"client/WallBatches","switchWorld"},new String[]{"client/RenderQueue","ensureWorld"},new String[]{"client/CreateMovingGuns","frame"}))
            for(var call:calls(target[0],target[1]))
                assertFalse((call.name.equals("clear") || call.name.equals("close")) &&
                    (call.owner.endsWith("/EditMeshes") || call.owner.endsWith("/GunMeshes")
                    || call.owner.endsWith("/CreateMovingGuns") || call.owner.endsWith("/SessionCache")
                    || call.owner.endsWith("/VertexBuffer")),call.owner+"."+call.name);
    }
    @Test void noIdleOrCapacityEvictionInGpuPreparation()throws Exception{
        for(var call:calls("client/EditMeshes","prepare"))
            assertFalse(call.name.equals("remove") || (call.name.equals("close") && call.owner.endsWith("EditMeshes$Bundle")));
        var key=read("client/CreateMovingGuns$Key");
        assertTrue(key.fields.stream().anyMatch(f->f.name.equals("light")));
    }
    @Test void noRetiredGameplayGateOrConfigRemains()throws Exception{
        for(var type:List.of("LoadingSessions","LoadingPayloads","LoadingGunPackets","DeferredGunDraw","PreloadScope","PreparationWindow","client/LoadingClient","client/LoadingGunClient","client/ActivationGate","mixin/LoadingPlayerMixin","mixin/LoadingTerrainMixin"))
            assertNull(getClassResource(type),type);
        for(var field:read("WallGunConfig").fields)
            assertFalse(field.name.contains("PRELOAD") || field.name.contains("CACHE_VERTICES"));
        String mixins=Files.readString(Path.of("src/main/resources/tacz_wall_display.mixins.json"));
        assertFalse(mixins.contains("Loading"));
    }
    @Test void teardownAndResourceReloadReleaseAllCaches()throws Exception{
        var owner=read("client/WallGunClient");int clearingCallbacks=0;
        for(var method:owner.methods){
            Set<String> cleared=new HashSet<>();
            for(var instruction:method.instructions)if(instruction instanceof MethodInsnNode c && c.name.equals("clear"))cleared.add(c.owner);
            if(cleared.contains("dev/kiyo/wallgun/client/GunMeshes")
                && cleared.contains("dev/kiyo/wallgun/client/WallBatches")
                && cleared.contains("dev/kiyo/wallgun/client/CreateMovingGuns"))clearingCallbacks++;
        }
        assertEquals(2,clearingCallbacks);
        assertTrue(calls("client/WallBatches","clear").stream().anyMatch(c->c.owner.endsWith("/EditMeshes")&&c.name.equals("clear")));
    }
    @Test void drawChecksLiveWorldAndSnapshotBeforeUsingCache()throws Exception{
        var calls=calls("client/WallBatches","render");
        int validation=-1,lookup=-1,draw=-1;
        for(int i=0;i<calls.size();i++){
            var c=calls.get(i);
            if(c.name.equals("current"))validation=i;
            if(c.owner.endsWith("/GunMeshes")&&c.name.equals("peek"))lookup=i;
            if(c.owner.endsWith("/EditMeshes")&&c.name.equals("render"))draw=i;
        }
        assertTrue(validation>=0&&lookup>validation&&draw>lookup);
        assertTrue(calls("client/WallBatches","current").stream().anyMatch(c->c.name.equals("getBlockEntity")));
    }
    @Test void placementNotificationIndependentOfLoading()throws Exception{
        assertTrue(read("GunPlacement").methods.stream().anyMatch(m->{
            for(var i:m.instructions)if(i instanceof MethodInsnNode c&&c.owner.endsWith("/PlacementPayloads")&&c.name.equals("disableAfterDimensionChange"))return true;
            return false;
        }));
        assertNotNull(getClassResource("PlacementPayloads$ModeDisabled"));
    }
}
