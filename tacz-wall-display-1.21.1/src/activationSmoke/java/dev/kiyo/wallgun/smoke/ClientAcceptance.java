package dev.kiyo.wallgun.smoke;
import dev.kiyo.wallgun.*;
import dev.kiyo.wallgun.client.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.*;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.nio.file.*;

final class ClientAcceptance {
    private static final String scenario=System.getProperty("wallgun.scenario","standard");
    private static boolean connected,installed;
    private static java.util.function.Consumer<CompoundTag> original;
    private static CompoundTag offer;
    private static int delay,completed,stable;
    private static long deadline=System.nanoTime()+600_000_000_000L;
    private static boolean wasWaiting;
    static void init(){NeoForge.EVENT_BUS.addListener(ClientAcceptance::tick);}
    private static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();
        try {
            if(System.nanoTime()>deadline)throw new AssertionError("client timeout "+WallBatches.stats());
            if(!installed && mc.screen instanceof TitleScreen && mc.getOverlay()==null){
                installed=true;original=LoadingPayloads.client;
                LoadingPayloads.client=tag->{if(!scenario.equals("observer") && tag.getString("kind").equals("offer")){offer=tag;delay=0;}else original.accept(tag);};
                WallGunConfig.PRELOAD_MODE.set(scenario.equals("off")?WallGunConfig.PreloadMode.OFF:WallGunConfig.PreloadMode.LOADING);
            }
            if(installed && !connected){connected=true;ConnectScreen.startConnecting(mc.screen,mc,ServerAddress.parseString("127.0.0.1:25586"),new ServerData("Acceptance","127.0.0.1:25586",ServerData.Type.OTHER),false,null);}
            if(offer!=null && !scenario.equals("timeout") && ++delay>=(scenario.equals("performance")?1:60)){original.accept(offer);var stale=offer.copy();stale.putString("kind","ready");stale.putLong("id",offer.getLong("id")-1);LoadingPayloads.send(stale);if(scenario.equals("failure")){var failure=offer.copy();failure.putString("kind","failure");LoadingPayloads.send(failure);}offer=null;}
            if(connected && !scenario.equals("standard") && !scenario.equals("performance") && mc.screen instanceof DisconnectedScreen) {
                if(!scenario.equals("observer") && completed!=0)throw new AssertionError("failed session was released");
                Files.writeString(mc.gameDirectory.toPath().resolve("SUCCESS-"+scenario+".txt"),"PASS explicit disconnection, no activation: "+scenario);mc.stop();return;
            }
            boolean waiting=LoadingClient.waiting();
            if(waiting && mc.level!=null && !(mc.screen instanceof ReceivingLevelScreen))throw new AssertionError("lost vanilla terrain screen during wait");
            if(wasWaiting && !waiting && mc.level!=null){
                if(!scenario.equals("off") && (WallBatches.pendingBatches()!=0 || WallWarmup.pendingModels()!=0))throw new AssertionError("released before GPU preparation");
                completed++;stable=0;
                if(completed==3 && (scenario.equals("standard") || scenario.equals("performance"))) {
                    if(scenario.equals("performance") && WallBatches.dimensionCacheHits==0)throw new AssertionError("return trip reused no GPU batches");
                    mc.reloadResourcePacks();
                }
                Files.writeString(mc.gameDirectory.toPath().resolve("progress.txt"),"sessions="+completed+" "+WallBatches.stats());
            }
            wasWaiting=waiting;
            if(scenario.equals("observer") && completed>=1 && !waiting && ++stable>1200){Files.writeString(mc.gameDirectory.toPath().resolve("SUCCESS-observer.txt"),"PASS independent client active while other player loads");mc.stop();return;}
            if(completed>=5 && !waiting && ++stable>100){
                try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(mc.gameDirectory.toPath().resolve("completed-scene.png"));}
                if(GunMeshes.failures!=0)throw new AssertionError("model capture failure");
                Files.writeString(mc.gameDirectory.toPath().resolve("SUCCESS.txt"),"PASS five sessions, vanilla screen, stale confirmations; scenario="+scenario+"; GPU readiness checked="+!scenario.equals("off")+"\n"+WallBatches.stats());mc.stop();
            }
        }catch(Throwable ex){ex.printStackTrace();try{Files.writeString(mc.gameDirectory.toPath().resolve("FAILED.txt"),ex.toString());}catch(Exception ignored){}mc.stop();}
    }
}
