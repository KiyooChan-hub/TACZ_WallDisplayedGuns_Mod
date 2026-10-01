package dev.kiyo.wallgun.smoke;

import dev.kiyo.wallgun.*;
import dev.kiyo.wallgun.client.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import java.nio.file.*;
import java.util.function.Consumer;

/** Isolated integrated-server acceptance for activation retries and safe fallback. */
final class ActivationSmoke {
    private boolean opened,firstOfferDropped,failureInjected,budgetInjected,travelRequested,finished;
    private int releases,stable;
    private final long deadline=System.nanoTime()+240_000_000_000L;

    ActivationSmoke(){MinecraftForge.EVENT_BUS.addListener(this::tick);}

    private static void exhaustPreloadBudget() throws ReflectiveOperationException {
        var field=LoadingClient.class.getDeclaredField("started");field.setAccessible(true);
        field.setLong(null,System.nanoTime()-(WallGunConfig.preloadTimeoutSeconds()+1L)*1_000_000_000L);
    }

    private void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END || finished)return;
        var mc=Minecraft.getInstance();
        var output=mc.gameDirectory.toPath().resolve("verification");
        try {
            if(System.nanoTime()>deadline)throw new AssertionError("activation acceptance timed out; releases="+releases);
            if(!opened && mc.screen instanceof TitleScreen && mc.getOverlay()==null) {
                opened=true;Files.createDirectories(output);Files.deleteIfExists(output.resolve("activation.txt"));
                Files.deleteIfExists(output.resolve("activation-progress.txt"));
                Files.deleteIfExists(mc.gameDirectory.toPath().resolve("SMOKE_FAILED.txt"));
                if(WallGunConfig.preloadTimeoutSeconds()!=10)throw new AssertionError("unexpected default preload timeout");
                WallGunConfig.PRELOAD_MODE.set(WallGunConfig.PreloadMode.OFF);
                Consumer<net.minecraft.nbt.CompoundTag> original=LoadingPayloads.client;
                LoadingPayloads.client=tag->{
                    try {
                        if(tag.getString("kind").equals("offer")) {
                            if(!firstOfferDropped){firstOfferDropped=true;return;}
                            original.accept(tag);
                            if(releases==0 && !failureInjected){GunMeshes.failures++;failureInjected=true;}
                            if(releases==1 && !budgetInjected){exhaustPreloadBudget();budgetInjected=true;}
                        }else {
                            original.accept(tag);
                            if(tag.getString("kind").equals("release"))releases++;
                        }
                    }catch(ReflectiveOperationException ex){throw new RuntimeException(ex);}
                };
                var settings=new LevelSettings("Wall gun activation verification",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel("wallgun-activation-smoke-"+System.currentTimeMillis(),settings,
                    new WorldOptions(42,false,false),registries->registries.registryOrThrow(Registries.WORLD_PRESET)
                        .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
                return;
            }
            if(releases==1 && !travelRequested && mc.level!=null && mc.screen==null && !LoadingClient.waiting()) {
                if(!firstOfferDropped || !failureInjected)throw new AssertionError("OFF retry/failure guard not exercised");
                Files.writeString(output.resolve("activation-progress.txt"),"OFF released after dropped offer and model failure\n");
                WallGunConfig.PRELOAD_MODE.set(WallGunConfig.PreloadMode.LOADING);
                travelRequested=true;
                mc.getSingleplayerServer().execute(()->{
                    var player=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
                    player.teleportTo(mc.getSingleplayerServer().getLevel(Level.NETHER),.5,100,8.5,180,0);
                });
            }
            if(releases>=2 && !LoadingClient.waiting() && mc.level!=null && mc.level.dimension()==Level.NETHER && mc.screen==null && ++stable>10) {
                if(!budgetInjected)throw new AssertionError("LOADING timeout fallback not exercised");
                Files.writeString(output.resolve("activation.txt"),"PASS: OFF recovered from lost offer/model failure; LOADING 10-second timeout skipped with server release; sessions="+releases+"\n");
                finished=true;mc.stop();
            }
        }catch(Throwable ex){ex.printStackTrace();
            try{Files.writeString(mc.gameDirectory.toPath().resolve("SMOKE_FAILED.txt"),ex.toString());}catch(Exception ignored){}
            finished=true;mc.stop();
        }
    }
}
