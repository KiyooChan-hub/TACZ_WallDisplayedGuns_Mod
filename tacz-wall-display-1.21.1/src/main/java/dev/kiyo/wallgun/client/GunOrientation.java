package dev.kiyo.wallgun.client;

import com.google.gson.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.model.BedrockGunModel;
import dev.kiyo.wallgun.WallGuns;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class GunOrientation {
    private static Map<String,Boolean> overrides;
    public static void prepareModel(ResourceLocation displayId, BedrockGunModel model) {
        // This pack authors its aiming ray as a 100-pixel cube rather than BeamRenderer geometry.
        // Hide that exact helper on the detached display only; the PEQ device remains visible.
        if(displayId.toString().equals("ccrp:hk416_sopmod_display")) hideHelper(model.getRootNode(),"laser_illuminated");
        // TACZ's additional_magazine function renders a second copy of magazine geometry.
        // These CAG models moved the main magazine by +0.2 Z but retain the reload helper
        // at its original pivot. Only suppress the duplicate on this detached wall model;
        // the player's animated model and its reload helper must remain untouched.
        if (switch (displayId.toString()) {
            case "spearhead:hk416d_cag_display", "spearhead:hk416d_cag_fde_display",
                 "spearhead:hk416d_cag_two_tone_display" -> true;
            default -> false;
        }) hideHelper(model.getRootNode(), "additional_magazine");

    }
    private static void hideHelper(com.tacz.guns.client.model.bedrock.BedrockPart part,String name) {
        if(part==null)return;
        if(name.equals(part.name))part.visible=false;
        for(var child:part.children)hideHelper(child,name);
    }
    public static void clear() { overrides=null; }
    public static GunMeshes.Mesh canonical(GunMeshes.Mesh mesh, Map<RenderType,List<MeshCapture.Vertex>> raw,
            ResourceLocation displayId, BedrockGunModel model, PoseStack pose) {
        if(overrides==null) {
            var values=new HashMap<String,Boolean>();
            var resource=Minecraft.getInstance().getResourceManager().getResource(ResourceLocation.fromNamespaceAndPath(WallGuns.ID,"orientation.json"));
            if(resource.isPresent())try(var reader=new InputStreamReader(resource.get().open(),StandardCharsets.UTF_8)) {
                JsonParser.parseReader(reader).getAsJsonObject().entrySet().forEach(e->values.put(e.getKey(),e.getValue().getAsBoolean()));
            }catch(Exception e){WallGuns.LOG.warn("Could not read decorative gun orientation overrides",e);}
            overrides=Map.copyOf(values);
        }
        Boolean flip=overrides.get(displayId.toString());
        if(flip==null) {
            flip=false;
            var muzzle=model.getMuzzleFlashPosPath();
            if(muzzle!=null&&!muzzle.isEmpty()) {
                float min=Float.POSITIVE_INFINITY,max=Float.NEGATIVE_INFINITY;
                for(var list:raw.values())for(var vertex:list){min=Math.min(min,vertex.x());max=Math.max(max,vertex.x());}
                pose.pushPose();
                try {
                    for(var part:muzzle)part.translateAndRotateAndScale(pose);
                    float x=pose.last().pose().transformPosition(new Vector3f()).x();
                    flip=Float.isFinite(x) && x<(min+max)*.5F-.0001F;
                } finally {pose.popPose();}
            }
        }
        if(!flip)return mesh;
        float maxDepth=mesh.materials().values().stream().flatMap(List::stream).map(MeshCapture.Vertex::z).max(Float::compare).orElseThrow();
        var output=new LinkedHashMap<RenderType,List<MeshCapture.Vertex>>();
        mesh.materials().forEach((type,list)->output.put(type,list.stream().map(v->new MeshCapture.Vertex(1-v.x(),v.y(),GunMeshes.WALL_GAP+maxDepth-v.z(),v.color(),v.u(),v.v(),v.light(),-v.nx(),v.ny(),-v.nz())).toList()));
        return new GunMeshes.Mesh(Collections.unmodifiableMap(output),mesh.vertices(),mesh.missing(),true);
    }
}
