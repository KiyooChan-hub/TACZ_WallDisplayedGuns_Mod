package dev.kiyo.wallgun.smoke;
import dev.kiyo.wallgun.*;
import dev.kiyo.wallgun.client.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.*;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;
import java.nio.file.*;
import java.util.*;

/** Exercise the real server placement/adjustment packets with equipped guns and per-frame visibility checks. */
final class EditAcceptance {
    private static boolean connected,mode;
    private static int ticks,phase,frames,baseUploads,baseEditUploads,baseBakes,visibleFrames,poseFrames;
    private static int lastPose=-1,transitions,cycles,placeBakes;
    private static long last,deadline=System.nanoTime()+600_000_000_000L;
    private static final BlockPos TARGET=new BlockPos(0,101,1);
    private static final List<Double> durations=new ArrayList<>();
    private static final StringBuilder csv=new StringBuilder("phase,ms,uploads,editUploads,pose\n");
    static void init(){NeoForge.EVENT_BUS.addListener(EditAcceptance::tick);NeoForge.EVENT_BUS.addListener(EditAcceptance::frame);}
    private static void require(boolean value,String text){if(!value)throw new AssertionError(text);}
    private static void finish(Throwable error){
        var mc=Minecraft.getInstance();
        try{
            var dir=mc.gameDirectory.toPath().resolve("edit-verification");Files.createDirectories(dir);
            Files.writeString(dir.resolve("frames.csv"),csv);
            if(error!=null){error.printStackTrace();Files.writeString(dir.resolve("FAILED.txt"),error.toString());}
            else {
                durations.sort(Double::compare);
                Files.writeString(dir.resolve("SUCCESS.txt"),"PASS real placement packet; 8 adjust packets/tick; all neighbors visible; live pose follows client state; rotation adds zero static/model/edit uploads after migration; idle merge; removal.\n"+
                    "visibleFrames="+visibleFrames+", poseFrames="+poseFrames+", p99Ms="+durations.get((int)(durations.size()*.99))+", maxMs="+durations.getLast()+"\n"+WallBatches.stats());
            }
        }catch(Exception ex){ex.printStackTrace();}
        mc.stop();
    }
    private static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();
        try{
            if(System.nanoTime()>deadline)throw new AssertionError("edit test timeout phase="+phase);
            if(!connected && mc.screen instanceof TitleScreen && mc.getOverlay()==null){
                connected=true;mc.options.framerateLimit().set(120);
                ConnectScreen.startConnecting(mc.screen,mc,ServerAddress.parseString("127.0.0.1:25586"),
                    new ServerData("Edit test","127.0.0.1:25586",ServerData.Type.OTHER),false,null);
            }
            if(mc.level==null || mc.player==null || mc.screen!=null || LoadingClient.waiting())return;
            if(!mode){
                var f=PlacementClient.class.getDeclaredField("enabled");f.setAccessible(true);f.setBoolean(null,true);
                PacketDistributor.sendToServer(new PlacementPayloads.Mode(true));mode=true;
            }
            ticks++;
            if(phase==0 && ticks>100){
                for(int x=0;x<2;x++)for(int y=100;y<102;y++)for(int z=0;z<2;z++){
                    var p=new BlockPos(x,y,z);if(p.equals(TARGET))continue;
                    require(WallBatches.drawnPose(p)!=null,"fixture not visible "+p);
                }
                placeBakes=GunMeshes.bakes;var click=new InputEvent.MouseButton.Pre(1,1,0);PlacementClient.mouse(click);
                require(click.isCanceled(),"placement mouse not captured: "+mc.hitResult);phase=1;ticks=0;
            } else if(phase==1 && ticks>40){
                require(mc.level.getBlockEntity(TARGET) instanceof WallGunEntity,"server did not place equipped gun");
                require(WallBatches.drawnPose(TARGET)!=null,"new gun invisible");
                require(GunMeshes.bakes==placeBakes,"placement repeated capture despite held prewarm");
                phase=2;ticks=0;
            } else if(phase==2){
                for(int i=0;i<8;i++)PacketDistributor.sendToServer(new PlacementPayloads.Adjust(TARGET,i==7 && ticks%23==0?0:1));
                if(ticks==30){baseUploads=WallBatches.uploads;baseEditUploads=EditMeshes.uploads;baseBakes=GunMeshes.bakes;}
                if(ticks>30){
                    require(WallBatches.uploads==baseUploads,"rotation rebuilt static batches");
                    require(EditMeshes.uploads==baseEditUploads,"rotation uploaded local geometry");
                    require(GunMeshes.bakes==baseBakes,"rotation recaptured model");
                }
                if(ticks>180){require(transitions>40,"server rotation was not exercised: "+transitions);phase=3;ticks=0;}
            } else if(phase==3 && ticks>60){
                require(WallBatches.pendingBatches()==0,"idle merge pending");
                phase=4;ticks=0;
                // Ordinary player break action goes through server validation too.
                mc.gameMode.startDestroyBlock(TARGET,Direction.SOUTH);
            } else if(phase==4 && ticks>30){
                require(mc.level.getBlockEntity(TARGET)==null,"removal failed");
                if(++cycles>=3){
                    phase=99;
                    var dir=mc.gameDirectory.toPath().resolve("edit-verification");Files.createDirectories(dir);
                    try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(dir.resolve("editing.png"));}
                    finish(null);
                }else {
                    PlacementClient.mouse(new InputEvent.MouseButton.Pre(1,1,0));phase=6;ticks=0;
                }
            } else if(phase==6 && ticks>30){
                require(mc.level.getBlockEntity(TARGET) instanceof WallGunEntity,"repeat placement failed");
                mc.gameMode.startDestroyBlock(TARGET,Direction.SOUTH);phase=4;ticks=0;
            }
        }catch(Throwable ex){finish(ex);phase=99;}
    }
    private static void frame(RenderFrameEvent.Post event){
        var mc=Minecraft.getInstance();long now=System.nanoTime();double ms=last==0?0:(now-last)/1e6;last=now;
        if(phase<1 || phase==99 || mc.level==null || mc.screen!=null)return;
        try{
            durations.add(ms);frames++;
            for(int x=0;x<2;x++)for(int y=100;y<102;y++)for(int z=0;z<2;z++){
                var p=new BlockPos(x,y,z);if(p.equals(TARGET))continue;
                require(WallBatches.drawnPose(p)!=null,"neighbor flickered phase="+phase+" pos="+p);
            }
            visibleFrames++;
            if(phase==2 && ticks>30){
                var gun=(WallGunEntity)mc.level.getBlockEntity(TARGET);
                require(Objects.equals(WallBatches.drawnPose(TARGET),gun.mountRoll()+gun.roll()),"rendered pose lagged client state: drawn="+WallBatches.drawnPose(TARGET)+" actual="+gun.roll()+" "+WallBatches.stats());
                if(lastPose!=gun.roll()){lastPose=gun.roll();transitions++;}
                poseFrames++;
            }
            csv.append(phase).append(',').append(ms).append(',').append(WallBatches.uploads).append(',').append(EditMeshes.uploads).append(',').append(WallBatches.drawnPose(TARGET)).append('\n');

        }catch(Throwable ex){finish(ex);phase=99;}
    }
}
