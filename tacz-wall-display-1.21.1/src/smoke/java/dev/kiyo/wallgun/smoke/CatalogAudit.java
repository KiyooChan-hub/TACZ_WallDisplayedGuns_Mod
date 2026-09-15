package dev.kiyo.wallgun.smoke;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import dev.kiyo.wallgun.*;
import dev.kiyo.wallgun.client.*;
import dev.kiyo.wallgun.mixin.GunDisplayAccessor;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.nio.file.*;
import java.util.*;

final class CatalogAudit {
    private boolean opened,ready;
    private int ticks;
    private final List<Map<String,Object>> records=new ArrayList<>();
    CatalogAudit(){NeoForge.EVENT_BUS.addListener(this::tick);}
    private Path out(){return Minecraft.getInstance().gameDirectory.toPath().resolve(System.getProperty("wallgun.auditOnly")==null?"catalog-audit":"catalog-focused");}
    private void tick(ClientTickEvent.Post e) {
        var mc=Minecraft.getInstance();
        try {
            if(!opened&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null) {
                opened=true;Files.createDirectories(out());mc.options.pauseOnLostFocus=false;mc.options.guiScale().set(1);
                mc.options.framerateLimit().set(60);com.tacz.guns.config.client.RenderConfig.GUN_LOD_RENDER_DISTANCE.set(999);
                var settings=new LevelSettings("Read-only pack orientation audit",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel("orientation-audit-"+System.currentTimeMillis(),settings,new WorldOptions(42,false,false),r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),mc.screen);
            }
            if(!ready&&mc.player!=null&&mc.level!=null&&mc.getOverlay()==null&&++ticks>100) {
                ready=true;
                var ids=TimelessAPI.getAllCommonGunIndex().stream().map(Map.Entry::getKey).filter(id->System.getProperty("wallgun.auditOnly")==null||java.util.Arrays.asList(System.getProperty("wallgun.auditOnly").split(",")).contains(id.toString())).sorted(Comparator.comparing(ResourceLocation::toString)).toList();
                mc.setScreen(new Catalog(ids));
            }
        }catch(Throwable ex){fail(ex);}
    }
    private void fail(Throwable ex){ex.printStackTrace();try{Files.writeString(out().resolve("FAILED.txt"),ex.toString());}catch(Exception ignored){}Minecraft.getInstance().stop();}
    private final class Catalog extends Screen {
        private static final int COLS=4,ROWS=5,SIZE=COLS*ROWS;
        private final List<ResourceLocation> ids;
        private final List<GunMeshes.Mesh> meshes=new ArrayList<>();
        private int page,frames;
        Catalog(List<ResourceLocation> ids){super(net.minecraft.network.chat.Component.literal("Orientation audit"));this.ids=ids;}
        private void load() {
            GunMeshes.clear();meshes.clear();
            for(int n=page*SIZE;n<Math.min(ids.size(),(page+1)*SIZE);n++) {
                var id=ids.get(n);ItemStack stack=GunItemBuilder.create().setId(id).build(minecraft.level.registryAccess());
                var mesh=GunMeshes.get(new GunSnapshot(stack));meshes.add(mesh);
                var display=TimelessAPI.getGunDisplay(stack).orElseThrow();
                var r=new LinkedHashMap<String,Object>();r.put("number",n+1);r.put("gun",id.toString());r.put("display",((GunDisplayAccessor)display).wallgun$displayId().toString());
                r.put("flipped",mesh.canonicalFlipped());r.put("missing",mesh.missing());r.put("vertices",mesh.vertices());records.add(r);
            }
        }
        @Override public void render(GuiGraphics g,int mx,int my,float partial) {
            try {
                if(meshes.isEmpty())load();
                g.fill(0,0,width,height,0xFFCED4D9);g.drawString(font,"DEFAULT GUN ORIENTATION - "+(page+1)+" / "+((ids.size()+SIZE-1)/SIZE),8,5,0xFF172330,false);
                int cw=width/COLS,ch=(height-24)/ROWS;
                for(int i=0;i<meshes.size();i++) {
                    int x=(i%COLS)*cw,y=24+(i/COLS)*ch;var mesh=meshes.get(i);
                    g.fill(x+2,y+2,x+cw-2,y+ch-2,0xFFEDF0F3);
                    g.drawString(font,(page*SIZE+i+1)+" "+ids.get(page*SIZE+i),x+8,y+ch-22,0xFF172330,false);
                    g.drawString(font,mesh.canonicalFlipped()?"SIDE CORRECTED":"SOURCE SIDE",x+8,y+ch-11,0xFF40586E,false);
                    g.flush();
                    float minX=Float.POSITIVE_INFINITY,maxX=-minX,minY=minX,maxY=-minX;
                    for(var list:mesh.materials().values())for(var v:list){minX=Math.min(minX,v.x());maxX=Math.max(maxX,v.x());minY=Math.min(minY,v.y());maxY=Math.max(maxY,v.y());}
                    float scale=Math.min((cw-20)/(maxX-minX),(ch-48)/(maxY-minY));
                    g.pose().pushPose();g.pose().translate(x+cw*.5,y+(ch-26)*.5,100);g.pose().scale(scale,-scale,scale);g.pose().translate(-.5,-.5,0);
                    com.mojang.blaze3d.platform.Lighting.setupFor3DItems();
                    var pose=g.pose().last();
                    for(var entry:mesh.materials().entrySet()) {
                        var buffer=g.bufferSource().getBuffer(entry.getKey());
                        for(var v:entry.getValue())buffer.addVertex(pose.pose(),v.x(),v.y(),v.z()).setColor(v.color()).setUv(v.u(),v.v()).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(pose,v.nx(),v.ny(),v.nz());
                    }
                    g.flush();g.pose().popPose();
                }
                if(++frames==8) {
                    try(var image=Screenshot.takeScreenshot(minecraft.getMainRenderTarget())){image.writeToFile(out().resolve(String.format("page-%02d.png",page+1)));}
                    Files.writeString(out().resolve("catalog.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(records));
                    System.out.println("CATALOG_PAGE "+(page+1)+" records="+records.size());
                    page++;frames=0;meshes.clear();
                    if(page*SIZE>=ids.size()){Files.writeString(out().resolve("SUCCESS.txt"),"Rendered "+records.size()+" gun prototypes; failures="+GunMeshes.failures);minecraft.stop();}
                }
            }catch(Throwable ex){fail(ex);}
        }
        @Override public boolean isPauseScreen(){return true;}
    }
}
