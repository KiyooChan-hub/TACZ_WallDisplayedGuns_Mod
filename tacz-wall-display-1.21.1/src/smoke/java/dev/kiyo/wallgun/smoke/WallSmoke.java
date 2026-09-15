package dev.kiyo.wallgun.smoke;

import com.tacz.guns.api.TimelessAPI;
import dev.kiyo.wallgun.*;
import dev.kiyo.wallgun.client.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.nio.file.*;
import java.util.*;

@Mod("wall_display_smoke")
public class WallSmoke {
    private boolean opened,placed,reloading;
    private int ticks,phase,stableUploads,initialTicks;
    private volatile Throwable serverFailure;
    private long lastFrame;
    private final StringBuilder equippedFrames=new StringBuilder("tick,frameMs,uploads,draws,guns\n");
    private final StringBuilder editFrames=new StringBuilder("tick,frameMs,uploads,draws,guns,uploadedVertices\n");
    private final StringBuilder motionFrames=new StringBuilder("tick,frameMs,uploads,draws,guns\n");
    private static final String[] GUNS={"tacz:ak47","tacz:m249","tacz:ak47","tacz:scar_h","mk16:m4urgi10","suffuse:l119a2","tacz:hk416d","tacz:m4a1","tacz:m16a4","tacz:scar_l","ghost:arx160","tacz:hk416d"};
    public WallSmoke() {
        if (Boolean.getBoolean("wallgun.lodSmoke")) {new LodSmoke();return;}
        if (Boolean.getBoolean("wallgun.toolSmoke")) {new PoseSmoke();return;}
        if (Boolean.getBoolean("wallgun.warmupSmoke")) {new WarmupSmoke();return;}
        if (Boolean.getBoolean("wallgun.pose")) {new PoseSmoke();return;}
        if (Boolean.getBoolean("wallgun.audit")) {new CatalogAudit();return;}
        NeoForge.EVENT_BUS.addListener(this::tick);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.RenderLevelStageEvent e)->{
            if(e.getStage()!=net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_LEVEL)return;
            long now=System.nanoTime();
            if(phase==12 && lastFrame!=0)equippedFrames.append(ticks).append(',').append((now-lastFrame)/1_000_000.0).append(',').append(WallBatches.uploads).append(',').append(WallBatches.lastDraws).append(',').append(WallBatches.lastGuns).append('\n');
            if(phase==6 && lastFrame!=0)motionFrames.append(ticks).append(',').append((now-lastFrame)/1_000_000.0).append(',').append(WallBatches.uploads).append(',').append(WallBatches.lastDraws).append(',').append(WallBatches.lastGuns).append('\n');
            if(phase==9 && lastFrame!=0)editFrames.append(ticks).append(',').append((now-lastFrame)/1_000_000.0).append(',').append(WallBatches.uploads).append(',').append(WallBatches.lastDraws).append(',').append(WallBatches.lastGuns).append(',').append(WallBatches.lastUploadedVertices).append('\n');
            lastFrame=now;
        });
    }
    private void tick(ClientTickEvent.Post event) {
        var mc=Minecraft.getInstance();
        try {
            if(!opened && mc.screen instanceof TitleScreen && mc.getOverlay()==null) {
                opened=true;
                Files.createDirectories(mc.gameDirectory.toPath().resolve("verification"));
                Files.deleteIfExists(mc.gameDirectory.toPath().resolve("SMOKE_FAILED.txt"));
                Files.deleteIfExists(mc.gameDirectory.toPath().resolve("verification/SUCCESS.txt"));
                mc.options.pauseOnLostFocus=false;
                // Match the user's 999-block high-detail setting; defaults use low-poly LOD even at zero distance.
                com.tacz.guns.config.client.RenderConfig.GUN_LOD_RENDER_DISTANCE.set(999);
                mc.options.renderDistance().set(5);mc.options.framerateLimit().set(60);
                var settings=new LevelSettings("Wall gun prototype verification",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel("wallgun-smoke-"+System.currentTimeMillis(),settings,new WorldOptions(42,false,false),
                        registries->registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),mc.screen);
            }
            if(!opened || mc.level==null || mc.player==null || mc.getOverlay()!=null || reloading)return;
            if(mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen)mc.setScreen(null);
            mc.player.getAbilities().flying=true;
            mc.player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            if(serverFailure!=null)throw new AssertionError(serverFailure);
            if(!placed) {
                if(++initialTicks<100)return;
                placed=true;mc.setScreen(null);
                mc.options.hideGui=true;mc.options.renderDistance().set(5);mc.options.framerateLimit().set(60);
                mc.getSingleplayerServer().execute(()->{
                    try {
                        var server=mc.getSingleplayerServer();var world=server.overworld();
                        world.setDayTime(6000);world.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);
                        for(int x=-6;x<=6;x++)for(int y=-60;y<=-53;y++)world.setBlock(new BlockPos(x,y,0),Blocks.QUARTZ_BLOCK.defaultBlockState(),3);
                        for(int i=0;i<GUNS.length;i++) {
                            var pos=new BlockPos(-4+(i%4)*3,-54-(i/4)*2,1);
                            var id=ResourceLocation.parse(GUNS[i]);
                            if(TimelessAPI.getCommonGunIndex(id).isEmpty())throw new AssertionError("Missing fixture gun "+id);
                            var state=WallGuns.BLOCK.get().defaultBlockState().setValue(WallGunBlock.FACING,Direction.SOUTH);
                            world.setBlock(pos,state,3);var gun=(WallGunEntity)world.getBlockEntity(pos);gun.setSnapshot(new GunSnapshot(sourceStack(id)));
                            var saved=gun.saveWithFullMetadata(world.registryAccess());
                            var restored=new WallGunEntity(pos,state);restored.loadWithComponents(saved,world.registryAccess());
                            if(!restored.snapshot().equals(gun.snapshot()))throw new AssertionError("Gun ID save/load failed");
                            if(!WallGuns.snapshot(WallGuns.BLOCK.get().getCloneItemStack(world,pos,state)).equals(gun.snapshot()))throw new AssertionError("Pick block ID failed");
                            if(!state.canSurvive(world,pos))throw new AssertionError("Wall support failed");
                        }
                        var player=server.getPlayerList().getPlayers().getFirst();
                        ConversionChecks.run(player, mc.gameDirectory.toPath().resolve("verification"));
                        InteractionChecks.run(player, mc.gameDirectory.toPath().resolve("verification"));
                        for(Direction direction:Direction.values()) {
                            var wall=new BlockPos(30+direction.get3DDataValue()*4,-56,10);
                            var target=wall.relative(direction);
                            world.setBlock(wall,Blocks.QUARTZ_BLOCK.defaultBlockState(),3);
                            var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(wall),direction,wall,false);
                            var context=new net.minecraft.world.item.context.BlockPlaceContext(player,net.minecraft.world.InteractionHand.MAIN_HAND,WallGuns.decorate(sourceStack(ResourceLocation.parse("tacz:ak47"))),hit);
                            var state=WallGuns.BLOCK.get().getStateForPlacement(context);
                            if(state==null || state.getValue(WallGunBlock.FACING)!=direction)throw new AssertionError("Placement orientation "+direction);
                            world.setBlock(target,state,3);
                            world.setBlock(wall,Blocks.AIR.defaultBlockState(),3);
                            if(!world.getBlockState(target).is(WallGuns.BLOCK.get()))throw new AssertionError("Support removal destroyed floating gun "+direction);
                            world.setBlock(target,Blocks.AIR.defaultBlockState(),3);
                        }
                        player.getAbilities().flying=true;player.onUpdateAbilities();player.teleportTo(.5,-57.2,12.5);player.setYRot(180);player.setXRot(0);
                        player.connection.teleport(.5,-57.2,12.5,180,0);
                    }catch(Throwable e){serverFailure=e;}
                });
                return;
            }
            if(phase==12) {
                mc.player.setPos(5.5+3*Math.sin(ticks*.045),-57.2,3.5);
                mc.player.setYRot((float)(180+65*Math.sin(ticks*.065)));mc.player.setXRot(0);
                if(++ticks<360)return;
                if(WallBatches.uploads!=stableUploads)throw new AssertionError("Equipped wall motion rebuilt geometry");
                var dir=mc.gameDirectory.toPath().resolve("verification");
                Files.writeString(dir.resolve("equipped-frames.csv"),equippedFrames);
                Files.writeString(dir.resolve("equipped-stress.txt"),"100 equipped SCAR-L guns, 4 materials each, 120 bounded batches; stable and 360-tick near motion upload delta=0; "+WallBatches.stats()+"\n");
                mc.getSingleplayerServer().execute(()->{
                    try {
                        var world=mc.getSingleplayerServer().overworld();
                        for(int x=1;x<=10;x++)for(int y=-60;y<=-51;y++)world.setBlock(new BlockPos(x,y,1),Blocks.AIR.defaultBlockState(),3);
                        world.getEntitiesOfClass(net.minecraft.world.entity.decoration.ItemFrame.class,new net.minecraft.world.phys.AABB(0,-64,0,12,-48,3)).forEach(net.minecraft.world.entity.Entity::discard);
                        for(int i=0;i<ScopeFixtures.SCOPES.length;i++) {
                            var gun=ScopeFixtures.gun(world.registryAccess(),ScopeFixtures.SCOPES[i]);
                            var pos=new BlockPos(3,-54-i*2,1);world.setBlock(pos,WallGuns.BLOCK.get().defaultBlockState(),3);
                            ((WallGunEntity)world.getBlockEntity(pos)).setSnapshot(new GunSnapshot(gun));
                            var frame=new net.minecraft.world.entity.decoration.ItemFrame(world,new BlockPos(7,-54-i*2,1),Direction.SOUTH);
                            frame.setItem(gun);world.addFreshEntity(frame);
                        }
                        mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst().connection.teleport(5.5,-55.2,5.8,180,0);
                    }catch(Throwable e){serverFailure=e;}
                });
                phase=13;ticks=0;return;
            }
            if(phase==6) {
                mc.player.setPos(5.5+3*Math.sin(ticks*.045),-57.2,3.5);
                mc.player.setYRot((float)(180+65*Math.sin(ticks*.065)));mc.player.setXRot(0);
                if(++ticks<360)return;
                Path output=mc.gameDirectory.toPath().resolve("verification");
                Files.writeString(output.resolve("motion-frames.csv"),motionFrames);
                // Screenshot readback is intentionally outside the sampled motion interval.
                screenshot("near-motion.png");
                Files.writeString(output.resolve("motion.txt"),"360 ticks near-wall translation and yaw: upload delta="+(WallBatches.uploads-stableUploads)+"; "+WallBatches.stats());
                if(WallBatches.uploads!=stableUploads)throw new AssertionError("Camera motion rebuilt static geometry: "+(WallBatches.uploads-stableUploads));
                phase=9;ticks=0;stableUploads=WallBatches.uploads;
                mc.getSingleplayerServer().execute(()->mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst().connection.teleport(5.5,-57.2,3.5,180,0));
                return;
            }
            if(phase==9) {
                if(ticks>=20 && ticks<400 && ticks%20==0) {
                    boolean remove=ticks%40==20;
                    mc.getSingleplayerServer().execute(()->{
                        var world=mc.getSingleplayerServer().overworld();var pos=new BlockPos(5,-56,1);
                        world.setBlock(pos,remove?Blocks.AIR.defaultBlockState():WallGuns.BLOCK.get().defaultBlockState(),3);
                        if(!remove)((WallGunEntity)world.getBlockEntity(pos)).setSnapshot(new GunSnapshot(sourceStack(ResourceLocation.parse("tacz:scar_l"))));
                    });
                }
                if(WallBatches.maxBatchGuns>4)throw new AssertionError("Wall edit rebuilt more than four guns: "+WallBatches.stats());
                if(WallBatches.lastUploadedVertices>4*GunMeshes.get(new GunSnapshot(sourceStack(ResourceLocation.parse("tacz:scar_l")))).vertices())throw new AssertionError("Edit upload exceeded one local cell: "+WallBatches.stats());
                if(++ticks<420)return;
                Path output=mc.gameDirectory.toPath().resolve("verification");
                Files.writeString(output.resolve("edit-frames.csv"),editFrames);
                Files.writeString(output.resolve("edits.txt"),"19 alternating edits among 100 SCAR-L guns; upload delta="+(WallBatches.uploads-stableUploads)+"; "+WallBatches.stats());
                mc.getSingleplayerServer().execute(()->{
                    var world=mc.getSingleplayerServer().overworld();
                    for(int x=-6;x<=12;x++)for(int y=-61;y<=-49;y++)world.setBlock(new BlockPos(x,y,1),Blocks.AIR.defaultBlockState(),3);
                    var pos=new BlockPos(3,-56,1);world.setBlock(pos,WallGuns.BLOCK.get().defaultBlockState(),3);
                    ((WallGunEntity)world.getBlockEntity(pos)).setSnapshot(new GunSnapshot(ConversionChecks.equipped(world.registryAccess())));
                    var frame=new net.minecraft.world.entity.decoration.ItemFrame(world,new BlockPos(7,-56,1),Direction.SOUTH);
                    frame.setItem(ConversionChecks.equipped(world.registryAccess()));world.addFreshEntity(frame);
                    mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst().connection.teleport(5.5,-57.2,8,180,0);
                });
                phase=7;ticks=0;return;
            }
            if(++ticks<180)return;
            if(GunMeshes.failures!=0)throw new AssertionError("Model capture failures: "+GunMeshes.failures);
            Path output=mc.gameDirectory.toPath().resolve("verification");Files.createDirectories(output);
            if(phase==0) {
                if(WallBatches.lastGuns<12)throw new AssertionError("Expected 12 visible wall guns: "+WallBatches.stats()+" camera="+mc.player.position()+" yaw="+mc.player.getYRot()+" pitch="+mc.player.getXRot()+" fixture="+mc.level.getBlockState(new BlockPos(-4,-54,1))+" BE="+mc.level.getBlockEntity(new BlockPos(-4,-54,1)));
                screenshot("front.png");stableUploads=WallBatches.uploads;phase=1;ticks=0;
            } else if(phase==1) {
                if(WallBatches.uploads!=stableUploads)throw new AssertionError("Static scene rebuilt mesh: "+WallBatches.stats());
                Files.writeString(output.resolve("stable.txt"),WallBatches.stats()+"\nNo uploads during 180 stable ticks; 12 NBT round trips and pick-block identities verified.\n");
                // Oblique view reveals whether the result is a real 3D mesh close to the wall.
                mc.getSingleplayerServer().execute(()->mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst().connection.teleport(8,-57.2,8,135,0));
                phase=2;ticks=0;
            } else if(phase==2) {
                screenshot("oblique.png");reloading=true;phase=3;ticks=0;
                mc.reloadResourcePacks().thenRun(()->reloading=false);
            } else if(phase==3) {
                if(WallBatches.lastGuns==0)throw new AssertionError("No geometry after reload");
                screenshot("reloaded.png");
                Files.writeString(output.resolve("catalog.txt"),"Generic snapshot block; no preset creative catalog. " + WallBatches.stats()+"\n");
                verifyFrameGeometry(output);
                mc.getSingleplayerServer().execute(()->{
                    var world=mc.getSingleplayerServer().overworld();
                    for(int x=-6;x<=12;x++)for(int y=-61;y<=-49;y++) {
                        world.setBlock(new BlockPos(x,y,1),Blocks.AIR.defaultBlockState(),3);
                        world.setBlock(new BlockPos(x,y,0),Blocks.QUARTZ_BLOCK.defaultBlockState(),3);
                    }
                    for(int x=1;x<=10;x++)for(int y=-60;y<=-51;y++) {
                        var pos=new BlockPos(x,y,1);world.setBlock(pos,WallGuns.BLOCK.get().defaultBlockState(),3);
                        ((WallGunEntity)world.getBlockEntity(pos)).setSnapshot(new GunSnapshot(sourceStack(ResourceLocation.parse("tacz:scar_l"))));
                    }
                    mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst().connection.teleport(5.5,-57.2,19.5,180,0);
                });
                phase=4;ticks=0;
            } else if(phase==4) {
                if(WallBatches.lastGuns!=100 || WallBatches.lastDraws!=30)throw new AssertionError("Expected 100 guns in 30 bounded cells: "+WallBatches.stats());
                screenshot("100-guns.png");stableUploads=WallBatches.uploads;phase=5;ticks=0;
            } else if(phase==5) {
                if(WallBatches.uploads!=stableUploads)throw new AssertionError("Dense static scene rebuilt mesh");
                Files.writeString(output.resolve("checks.txt"),Files.readString(output.resolve("catalog.txt"))+"100 same-material guns, one section: "+WallBatches.stats()+"\nNo uploads during 180 stable ticks. Six placement faces and survival without support verified.\n");
                phase=6;ticks=0;
                mc.getSingleplayerServer().execute(()->mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst().connection.teleport(5.5,-57.2,3.5,180,0));
            } else if(phase==7) {
                if(WallBatches.lastGuns!=1)throw new AssertionError("Removed guns retained in batches: "+WallBatches.stats());
                screenshot("item-frame-comparison.png");
                mc.getSingleplayerServer().execute(()->mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst().connection.teleport(1.9,-57.1,1.3,-90,0));
                phase=10;ticks=0;
            } else if(phase==10) {
                screenshot("wall-contact-side.png");
                mc.getSingleplayerServer().execute(()->{
                    try {
                        var world=mc.getSingleplayerServer().overworld();
                        var snapshot=new GunSnapshot(ConversionChecks.equipped(world.registryAccess()));
                        for(int x=1;x<=10;x++)for(int y=-60;y<=-51;y++) {
                            var pos=new BlockPos(x,y,1);world.setBlock(pos,WallGuns.BLOCK.get().defaultBlockState(),3);
                            ((WallGunEntity)world.getBlockEntity(pos)).setSnapshot(snapshot);
                        }
                        mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst().connection.teleport(5.5,-57.2,19.5,180,0);
                    }catch(Throwable e){serverFailure=e;}
                });
                phase=11;ticks=0;
            } else if(phase>=13 && phase<=15) {
                screenshot("scope-"+ScopeFixtures.SCOPES[phase-13].replace(':','-')+".png");
                if(phase==15) {phase=8;mc.setScreen(new Icons());}
                else {
                    int index=phase-12;
                    mc.getSingleplayerServer().execute(()->mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst().connection.teleport(5.5,-55.2-index*2,5.8,180,0));
                    phase++;ticks=0;
                }
            } else if(phase==11) {
                if(WallBatches.lastGuns!=100 || WallBatches.lastDraws!=120)throw new AssertionError("Equipped stress material batches: "+WallBatches.stats());
                screenshot("100-equipped.png");stableUploads=WallBatches.uploads;phase=12;ticks=0;
            }
        } catch(Throwable failure) {
            failure.printStackTrace();try{screenshot("failure.png");Files.writeString(mc.gameDirectory.toPath().resolve("SMOKE_FAILED.txt"),failure.toString());}catch(Exception ignored){}mc.stop();
        }
    }
    private net.minecraft.world.item.ItemStack sourceStack(ResourceLocation id) {
        var data=TimelessAPI.getCommonGunIndex(id).orElseThrow().getGunData();
        return com.tacz.guns.api.item.builder.GunItemBuilder.create().setId(id).setAmmoCount(data.getAmmoAmount()).setAmmoInBarrel(true)
                .setFireMode(data.getFireModeSet().getFirst()).build(Minecraft.getInstance().level.registryAccess());
    }
    private void verifyFrameGeometry(Path output)throws Exception {
        var mc=Minecraft.getInstance();StringBuilder result=new StringBuilder();
        for(String name:new String[]{"tacz:scar_l","tacz:ak47","mk16:m4urgi10","equipped","scope/tacz:sight_exp3","scope/mk16:553_g43","scope/tacz:scope_elcan_4x"}) {
            var id=ResourceLocation.parse(name.startsWith("scope/")?"suffuse:n4":name.equals("equipped")?"tacz:scar_l":name);
            var referenceStack=name.startsWith("scope/")?ScopeFixtures.gun(mc.level.registryAccess(),name.substring(6)):name.equals("equipped")?ConversionChecks.equipped(mc.level.registryAccess()):sourceStack(id);
            var capture=new MeshCapture();var pose=new com.mojang.blaze3d.vertex.PoseStack();
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180));pose.scale(.5F,.5F,.5F);
            ReferenceCapture.begin(capture);
            try {mc.getItemRenderer().renderStatic(referenceStack,net.minecraft.world.item.ItemDisplayContext.FIXED,0,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,pose,mc.renderBuffers().bufferSource(),mc.level,0);}
            finally {ReferenceCapture.end();}
            var referenceMaterials=MeshCapture.withoutDegenerateQuads(capture.finish());
            if(!referenceMaterials.keySet().equals(GunMeshes.get(new GunSnapshot(referenceStack)).materials().keySet()))throw new AssertionError(name+" material/texture mismatch");
            var reference=referenceMaterials.values().stream().flatMap(List::stream).toList();
            var product=GunMeshes.get(new GunSnapshot(referenceStack));
            var actual=product.materials().values().stream().flatMap(List::stream).toList();
            if(product.canonicalFlipped()) {
                float depth=actual.stream().map(MeshCapture.Vertex::z).max(Float::compare).orElseThrow()+GunMeshes.WALL_GAP;
                actual=actual.stream().map(v->new MeshCapture.Vertex(1-v.x(),v.y(),depth-v.z(),v.color(),v.u(),v.v(),v.light(),-v.nx(),v.ny(),-v.nz())).toList();
            }
            if(name.equals("equipped")) {
                var plain=GunMeshes.get(new GunSnapshot(sourceStack(id)));
                if(plain.vertices()==actual.size())throw new AssertionError("Equipped gun has no added attachment geometry");
                var snap=new GunSnapshot(referenceStack);int bakes=GunMeshes.bakes;
                if(GunMeshes.get(snap)!=GunMeshes.get(new GunSnapshot(referenceStack.copy())) || GunMeshes.bakes!=bakes)throw new AssertionError("Equivalent equipment does not share mesh");
            }
            double nearest=actual.stream().mapToDouble(MeshCapture.Vertex::z).min().orElseThrow();
            if(Math.abs(nearest-.001)>.00001)throw new AssertionError(name+" visible wall gap "+nearest);
            if(name.equals("tacz:scar_l")) {
                StringBuilder meshCsv=new StringBuilder("x,y,z,u,v,nx,ny,nz\n");
                for(var a:actual)meshCsv.append(a.x()).append(',').append(a.y()).append(',').append(a.z()).append(',').append(a.u()).append(',').append(a.v()).append(',').append(a.nx()).append(',').append(a.ny()).append(',').append(a.nz()).append('\n');
                Files.writeString(output.resolve("scar-l-vertices.csv"),meshCsv);
            }
            if(reference.size()!=actual.size())throw new AssertionError(name+" item-frame vertex count "+reference.size()+" != "+actual.size());
            // A wall placement may translate the mesh. Every relative position, normal and UV must match the real item renderer.
            var r0=reference.getFirst();var a0=actual.getFirst();double maxError=0;
            for(int i=0;i<reference.size();i++) {
                var r=reference.get(i);var a=actual.get(i);
                maxError=Math.max(maxError,Math.abs((r.x()-r0.x())-(a.x()-a0.x())));
                maxError=Math.max(maxError,Math.abs((r.y()-r0.y())-(a.y()-a0.y())));
                maxError=Math.max(maxError,Math.abs((r.z()-r0.z())-(a.z()-a0.z())));
                if(Math.abs(r.nx()-a.nx())>.0001 || Math.abs(r.ny()-a.ny())>.0001 || Math.abs(r.nz()-a.nz())>.0001 || r.u()!=a.u() || r.v()!=a.v())throw new AssertionError(name+" frame normal/UV mismatch "+i);
            }
            if(maxError>.0001)throw new AssertionError(name+" item-frame geometry mismatch "+maxError);
            result.append(name).append(": ").append(actual.size()).append(" vertices; maximum relative position error=").append(maxError).append("; visible wall gap=").append(nearest).append('\n');
        }
        Files.writeString(output.resolve("frame-geometry.txt"),result);
    }
    private void screenshot(String name)throws Exception {
        var mc=Minecraft.getInstance();try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(mc.gameDirectory.toPath().resolve("verification").resolve(name));}
    }
    private final class Icons extends net.minecraft.client.gui.screens.Screen {
        private int frames;
        Icons(){super(net.minecraft.network.chat.Component.literal("Wall gun inventory verification"));}
        @Override public void render(net.minecraft.client.gui.GuiGraphics graphics,int mouseX,int mouseY,float partial) {
            graphics.fill(0,0,width,height,0xFF202831);
            graphics.drawString(font,"TACZ wall guns - original slot textures",12,12,-1);
            for(int i=0;i<GUNS.length;i++) {
                int x=12+(i%4)*(width/4), y=42+(i/4)*80;
                var stack=WallGuns.decorate(sourceStack(ResourceLocation.parse(GUNS[i])));
                graphics.pose().pushPose();graphics.pose().translate(x,y,0);graphics.pose().scale(3,3,3);graphics.renderItem(stack,0,0);graphics.pose().popPose();
                graphics.drawString(font,GUNS[i],x,y+54,-1);
                if(stack.getHoverName().getString().isBlank())throw new AssertionError("Missing item name");
            }
            graphics.flush();
            if(++frames==40) {
                try {
                    screenshot("inventory.png");
                    var dir=minecraft.gameDirectory.toPath().resolve("verification");
                    Files.writeString(dir.resolve("SUCCESS.txt"),Files.readString(dir.resolve("conversion.txt"))+Files.readString(dir.resolve("equipped-stress.txt"))+Files.readString(dir.resolve("checks.txt"))+Files.readString(dir.resolve("frame-geometry.txt"))+Files.readString(dir.resolve("motion.txt"))+"\n"+Files.readString(dir.resolve("edits.txt"))+"\nRemoved 99 guns without stale geometry; 12 source-pack item icons rendered.\n");
                }catch(Exception e){throw new RuntimeException(e);}
                minecraft.stop();
            }
        }
        @Override public boolean isPauseScreen(){return true;}
    }
}
