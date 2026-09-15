package dev.kiyo.wallgun.smoke;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.util.RenderDistance;
import dev.kiyo.wallgun.*;
import dev.kiyo.wallgun.client.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.nio.file.*;

/** Production-jar regression: a low-detail-first capture must match a cold high-detail reference. */
final class LodSmoke {
    private boolean opened,checked;
    private int ticks;
    private volatile boolean placed;
    private volatile Throwable failure;
    private String report;
    LodSmoke(){NeoForge.EVENT_BUS.addListener(this::tick);}
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private Path out(){return Minecraft.getInstance().gameDirectory.toPath().resolve("lod-verification");}
    private void tick(ClientTickEvent.Post event) {
        var mc=Minecraft.getInstance();
        try {
            if(!opened&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null){
                opened=true;Files.createDirectories(out());
                mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;mc.options.renderDistance().set(5);mc.options.framerateLimit().set(60);
                RenderConfig.GUN_LOD_RENDER_DISTANCE.set(0);
                var settings=new LevelSettings("Scope LOD regression",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel("lod-smoke-"+System.currentTimeMillis(),settings,new WorldOptions(42,false,false),r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),mc.screen);
            }
            if(failure!=null)throw new AssertionError(failure);
            if(mc.level==null||mc.player==null||mc.getOverlay()!=null)return;
            if(mc.screen instanceof PauseScreen)mc.setScreen(null);
            if(++ticks<100)return;
            if(!checked){
                checked=true;mc.setScreen(null);
                // Remove TACZ's recent-GUI grace period so this test really exercises its LOD branch.
                var timestamp=RenderDistance.class.getDeclaredField("GUI_RENDER_TIMESTAMP");timestamp.setAccessible(true);timestamp.setLong(null,0L);
                PoseStack pose=new PoseStack();pose.translate(100000,0,0);
                require(!RenderDistance.inRenderHighPolyModelDistance(pose),"Ordinary distant guns should still use low detail");
                MeshCapture.begin(new MeshCapture());
                try{require(RenderDistance.inRenderHighPolyModelDistance(pose),"Capture must override low-detail selection");}finally{MeshCapture.end();}
                require(!RenderDistance.inRenderHighPolyModelDistance(pose),"Capture override leaked into ordinary rendering");
                var text=new StringBuilder("PASS: ordinary TACZ LOD preserved before/after capture.\n");
                String[] scopes={"sight_exp3","scope_acog_ta31","scope_elcan_4x"};
                GunSnapshot[] snapshots=new GunSnapshot[scopes.length];
                for(int i=0;i<scopes.length;i++){
                    String scope=scopes[i]; var gun=ConversionChecks.equipped(mc.level.registryAccess());
                    var attachment=AttachmentItemBuilder.create().setId(ResourceLocation.parse("tacz:"+scopes[i])).build();
                    IGun api=IGun.getIGunOrNull(gun);require(api.allowAttachment(gun,attachment),"Fixture compatibility");api.installAttachment(mc.level.registryAccess(),gun,attachment);
                    var snapshot=new GunSnapshot(gun);snapshots[i]=snapshot;
                    GunMeshes.Mesh first=null;
                    for(int distance:new int[]{0,8,9999}){
                        RenderConfig.GUN_LOD_RENDER_DISTANCE.set(distance);timestamp.setLong(null,0L);GunMeshes.clear();
                        var mesh=GunMeshes.get(snapshot);
                        require(!mesh.missing(),"Missing mesh "+scopes[i]);
                        require(mesh.materials().keySet().stream().noneMatch(t->t.toString().contains("/lod/")),"LOD texture captured "+scopes[i]);
                        require(mesh.materials().keySet().stream().anyMatch(t->t.toString().contains("/uv/"+scope)),"High-detail scope texture missing "+scopes[i]);
                        require(GunMeshes.get(snapshot)==mesh,"Static mesh should be reused");
                        require(MeshCapture.active()==null,"Capture context leaked");
                        if(first==null)first=mesh;else require(first.equals(mesh),"Geometry/materials differ with TACZ distance "+distance);
                        text.append(scopes[i]).append(" distance=").append(distance).append(" vertices=").append(mesh.vertices()).append(" materials=").append(mesh.materials().size()).append('\n');
                    }
                }
                RenderConfig.GUN_LOD_RENDER_DISTANCE.set(0);GunMeshes.clear();report=text.toString();ticks=0;
                mc.getSingleplayerServer().execute(()->{try{
                    var level=mc.getSingleplayerServer().overworld();
                    for(int x=-4;x<=4;x++)for(int y=-60;y<=-54;y++)level.setBlock(new BlockPos(x,y,0),Blocks.SMOOTH_STONE.defaultBlockState(),3);
                    for(int i=0;i<3;i++){var pos=new BlockPos((i-1)*3,-57,1);level.setBlock(pos,WallGuns.BLOCK.get().defaultBlockState().setValue(WallGunBlock.FACING,Direction.SOUTH),3);((WallGunEntity)level.getBlockEntity(pos)).setSnapshot(snapshots[i]);}
                    var player=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);player.getAbilities().flying=true;player.onUpdateAbilities();player.connection.teleport(.5,-58,7.5,180,0);
                    level.setDayTime(6000);level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,mc.getSingleplayerServer());placed=true;
                }catch(Throwable ex){failure=ex;}});
            }else if(placed&&ticks>160){
                require(WallBatches.lastGuns==3,"Three equipped displays rendered: "+WallBatches.stats());
                try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(out().resolve("high-detail-at-lod-zero.png"));}
                Files.writeString(out().resolve("SUCCESS.txt"),report+"PASS: three placed equipped displays rendered with TACZ LOD distance zero.\n");mc.stop();
            }
        }catch(Throwable ex){ex.printStackTrace();try{Files.createDirectories(out());Files.writeString(out().resolve("FAILED.txt"),ex.toString());}catch(Exception ignored){}mc.stop();}
    }
}
