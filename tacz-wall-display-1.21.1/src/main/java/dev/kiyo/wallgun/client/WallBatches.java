package dev.kiyo.wallgun.client;

import dev.kiyo.wallgun.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.*;

/** Live-world references are transient; immutable local-space GPU meshes belong to the connection. */
public final class WallBatches {
    private record Entry(WallGunEntity gun,GunMeshes.Mesh mesh,int light) {}
    private static final Map<BlockPos,Entry> RESIDENTS=new LinkedHashMap<>();
    private static final Map<BlockPos,Integer> DRAWN_POSES=new HashMap<>();
    private static ClientLevel world;
    private static net.minecraft.client.renderer.culling.Frustum frustum;
    public static int uploads,lastDraws,lastGuns,lastUploadedVertices;
    public static Integer drawnPose(BlockPos pos){return DRAWN_POSES.get(pos);}
    public static boolean visible(AABB bounds){return frustum!=null && frustum.isVisible(bounds);}
    public static void switchWorld(ClientLevel next){
        if(world==next)return;
        RESIDENTS.clear();DRAWN_POSES.clear();frustum=null;EditMeshes.begin();world=next;
        WallGuns.LOG.info("Wall gun session cache: destination={}, models={}, modelVertices={}, gpuBundles={}, gpuVertices={}",
            next==null?"none":next.dimension().location(),GunMeshes.cachedModels(),GunMeshes.cachedVertices(),
            EditMeshes.cachedBundles(),EditMeshes.cachedVertices());
    }
    private static boolean current(BlockPos pos,WallGunEntity gun){
        return world!=null && world==Minecraft.getInstance().level && gun.getLevel()==world
            && !gun.isRemoved() && world.hasChunkAt(pos) && world.getBlockEntity(pos)==gun
            && gun.getBlockState().is(WallGuns.BLOCK.get());
    }
    public static void enqueue(WallGunEntity gun,int light){
        if(gun.getLevel()!=Minecraft.getInstance().level || gun.snapshot()==null)return;
        switchWorld(Minecraft.getInstance().level);
        var pos=gun.getBlockPos();
        if(!current(pos,gun))return;
        var mesh=GunMeshes.peek(gun.snapshot());
        if(mesh==null){RESIDENTS.remove(pos);RenderQueue.request(gun);return;}
        RESIDENTS.put(pos,new Entry(gun,mesh,light));
    }
    public static void prepare(WorkBudget budget){
        switchWorld(Minecraft.getInstance().level);
        EditMeshes.begin();lastUploadedVertices=0;
        if(world==null)return;
        var player=Minecraft.getInstance().player;
        var iterator=RESIDENTS.entrySet().iterator();
        while(iterator.hasNext()){
            var item=iterator.next();var gun=item.getValue().gun();var pos=item.getKey();
            if(!current(pos,gun) || player==null || gun.snapshot()==null
                || !RenderDistanceRules.keep(pos,player.getEyePosition(),WallGunConfig.maxRenderDistance())){
                iterator.remove();continue;
            }
            var mesh=GunMeshes.peek(gun.snapshot());
            if(mesh==null){iterator.remove();continue;}
            int light=LevelRenderer.getLightColor(world,pos);
            item.setValue(new Entry(gun,mesh,light));EditMeshes.request(mesh,light);
        }
        EditMeshes.prepare(budget);uploads=EditMeshes.uploads;lastUploadedVertices=EditMeshes.uploadedVertices;
    }
    public static int pendingBatches(){
        return (int)RESIDENTS.values().stream().filter(e->!EditMeshes.ready(e.mesh(),e.light())).count();
    }
    public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS)return;
        if(world!=Minecraft.getInstance().level)switchWorld(Minecraft.getInstance().level);
        frustum=event.getFrustum();lastDraws=0;lastGuns=0;DRAWN_POSES.clear();
        if(world==null)return;
        var camera=event.getCamera().getPosition();
        for(var item:RESIDENTS.entrySet()){
            var pos=item.getKey();var entry=item.getValue();var gun=entry.gun();
            // Packets may replace/remove/update a block after frame preparation.
            if(!current(pos,gun) || gun.snapshot()==null || GunMeshes.peek(gun.snapshot())!=entry.mesh())continue;
            var bounds=new AABB(pos).inflate(entry.mesh().radius());
            if(!visible(bounds) || !RenderDistanceRules.draw(bounds,camera,WallGunConfig.maxRenderDistance()))continue;
            int light=LevelRenderer.getLightColor(world,pos);
            int roll=gun.mountRoll()+gun.roll();
            var pose=new DisplayPose(gun.getBlockState().getValue(WallGunBlock.FACING),roll,gun.flipped(),
                GunMeshes.WALL_GAP,entry.mesh().maxDepth());
            if(EditMeshes.render(entry.mesh(),light,pose,pos,event)){
                lastGuns++;lastDraws+=entry.mesh().materials().size();DRAWN_POSES.put(pos,roll);
            }
        }
    }
    public static void clear(){
        RESIDENTS.clear();DRAWN_POSES.clear();EditMeshes.clear();world=null;frustum=null;
    }
    public static String stats(){
        return "sessionModels="+GunMeshes.cachedModels()+", modelVertices="+GunMeshes.cachedVertices()
            +", gpuBundles="+EditMeshes.cachedBundles()+", gpuVertices="+EditMeshes.cachedVertices()
            +", bakes="+GunMeshes.bakes+", failures="+GunMeshes.failures+", uploads="+uploads
            +", draws="+lastDraws+", visible="+lastGuns+", pendingModels="+RenderQueue.pendingModels()
            +", pendingGuns="+pendingBatches();
    }
}
