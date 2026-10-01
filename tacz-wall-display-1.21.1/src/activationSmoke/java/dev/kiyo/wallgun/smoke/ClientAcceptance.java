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
    private static boolean connected,installed,firstOfferDropped,failureInjected,budgetInjected;
    private static java.util.function.Consumer<CompoundTag> original;
    private static CompoundTag offer;
    private static int delay,completed,releaseCount,stable;
    private static long deadline=System.nanoTime()+600_000_000_000L;
    private static boolean wasWaiting,placementClientDisabled,dimensionScreenshotPending;
    private static int dimensionScreenshotDelay;
    private static void placementEnabled(boolean value)throws ReflectiveOperationException{
        var field=PlacementClient.class.getDeclaredField("enabled");field.setAccessible(true);field.setBoolean(null,value);
    }
    private static boolean placementEnabled()throws ReflectiveOperationException{
        var field=PlacementClient.class.getDeclaredField("enabled");field.setAccessible(true);return field.getBoolean(null);
    }
    private static void exhaustPreloadBudget()throws ReflectiveOperationException{
        var field=LoadingClient.class.getDeclaredField("started");field.setAccessible(true);
        field.setLong(null,System.nanoTime()-(WallGunConfig.preloadTimeoutSeconds()+1L)*1_000_000_000L);
    }
    static void init(){NeoForge.EVENT_BUS.addListener(ClientAcceptance::tick);}
    private static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();
        try {
            if(System.nanoTime()>deadline)throw new AssertionError("client timeout "+WallBatches.stats());
            if(!installed && mc.screen instanceof TitleScreen && mc.getOverlay()==null){
                installed=true;original=LoadingPayloads.client;
                LoadingPayloads.client=tag->{
                    if(scenario.equals("off") && tag.getString("kind").equals("offer")){
                        if(!firstOfferDropped){firstOfferDropped=true;return;}
                        original.accept(tag);
                        if(!failureInjected){GunMeshes.failures++;failureInjected=true;}
                    }else if(!scenario.equals("observer") && tag.getString("kind").equals("offer")){
                        if(offer==null || offer.getLong("id")!=tag.getLong("id")){offer=tag;delay=0;}
                    }
                    else {original.accept(tag);if(scenario.equals("off") && tag.getString("kind").equals("release"))releaseCount++;}
                };
                if(scenario.equals("fallback") && WallGunConfig.preloadTimeoutSeconds()!=10)throw new AssertionError("unexpected default preload timeout");
                WallGunConfig.PRELOAD_MODE.set(scenario.equals("off")?WallGunConfig.PreloadMode.OFF:WallGunConfig.PreloadMode.LOADING);
            }
            if(installed && !connected){connected=true;ConnectScreen.startConnecting(mc.screen,mc,ServerAddress.parseString("127.0.0.1:25586"),new ServerData("Acceptance","127.0.0.1:25586",ServerData.Type.OTHER),false,null);}
            if(offer!=null && !scenario.equals("timeout") && ++delay>=(scenario.equals("performance")?1:60)){original.accept(offer);if(scenario.equals("fallback")){if(!failureInjected){GunMeshes.failures++;failureInjected=true;}else if(completed==1 && !budgetInjected){exhaustPreloadBudget();budgetInjected=true;}}var stale=offer.copy();stale.putString("kind","ready");stale.putLong("id",offer.getLong("id")-1);LoadingPayloads.send(stale);if(scenario.equals("failure")){var failure=offer.copy();failure.putString("kind","failure");LoadingPayloads.send(failure);}offer=null;}
            if(connected && (scenario.equals("failure") || scenario.equals("timeout")) && mc.screen instanceof DisconnectedScreen) {
                if(!scenario.equals("observer") && completed!=0)throw new AssertionError("failed session was released");
                Files.writeString(mc.gameDirectory.toPath().resolve("SUCCESS-"+scenario+".txt"),"PASS explicit disconnection, no activation: "+scenario);mc.stop();return;
            }
            boolean waiting=LoadingClient.waiting();
            if(waiting && mc.level!=null && !(mc.screen instanceof ReceivingLevelScreen))throw new AssertionError("lost vanilla terrain screen during wait");
            if(((wasWaiting && !waiting) || (scenario.equals("off") && releaseCount>completed && !waiting)) && mc.level!=null){
                if(!scenario.equals("off") && !(scenario.equals("fallback") && completed<2) && (WallBatches.pendingBatches()!=0 || WallWarmup.pendingModels()!=0))throw new AssertionError("released before GPU preparation");
                completed++;stable=0;
                if(completed==1 && scenario.equals("standard"))placementEnabled(true);
                if(completed==2 && scenario.equals("standard")){
                    if(placementEnabled())throw new AssertionError("client placement mode survived dimension release");
                    placementClientDisabled=true;
                    dimensionScreenshotPending=true;
                }
                if(completed==3 && (scenario.equals("standard") || scenario.equals("performance"))) {
                    if(scenario.equals("performance") && WallBatches.dimensionCacheHits==0)throw new AssertionError("return trip reused no GPU batches");
                    mc.reloadResourcePacks();
                }
                Files.writeString(mc.gameDirectory.toPath().resolve("progress.txt"),"sessions="+completed+" "+WallBatches.stats());
            }
            wasWaiting=waiting;
            if(dimensionScreenshotPending && !waiting && mc.level!=null && mc.screen==null && ++dimensionScreenshotDelay>5){
                try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(mc.gameDirectory.toPath().resolve("dimension-mode-off.png"));}
                dimensionScreenshotPending=false;
            }
            if(scenario.equals("observer") && completed>=1 && !waiting && ++stable>1200){Files.writeString(mc.gameDirectory.toPath().resolve("SUCCESS-observer.txt"),"PASS independent client active while other player loads");mc.stop();return;}
            if(completed>=5 && !waiting && ++stable>100){
                try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(mc.gameDirectory.toPath().resolve("completed-scene.png"));}
                if(GunMeshes.failures!=((scenario.equals("off") || scenario.equals("fallback"))?1:0))throw new AssertionError("unexpected model capture failure");
                if(scenario.equals("off") && (!firstOfferDropped || !failureInjected))throw new AssertionError("OFF retry/failure guard not exercised");
                if(scenario.equals("fallback") && (!failureInjected || !budgetInjected))throw new AssertionError("LOADING fallback paths not exercised");
                if(scenario.equals("standard") && !placementClientDisabled)throw new AssertionError("dimension placement reset was not observed");
                Files.writeString(mc.gameDirectory.toPath().resolve("SUCCESS.txt"),"PASS five sessions, vanilla screen, stale confirmations; placement mode disabled on dimension release="+placementClientDisabled+"; scenario="+scenario+"; GPU readiness checked="+!scenario.equals("off")+"\n"+WallBatches.stats());mc.stop();
            }
        }catch(Throwable ex){ex.printStackTrace();try{Files.writeString(mc.gameDirectory.toPath().resolve("FAILED.txt"),ex.toString());}catch(Exception ignored){}mc.stop();}
    }
}
