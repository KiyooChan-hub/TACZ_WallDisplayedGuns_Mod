package dev.kiyo.wallgun.client;
import dev.kiyo.wallgun.WallGuns;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.*;
import net.minecraftforge.client.extensions.common.*;
import net.minecraftforge.common.MinecraftForge;

@EventBusSubscriber(modid=WallGuns.ID, value=Dist.CLIENT, bus=EventBusSubscriber.Bus.MOD)
public final class WallGunClient {
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(WallGuns.ENTITY.get(),WallGunRenderer::new);
        MinecraftForge.EVENT_BUS.addListener(WallBatches::render);
        MinecraftForge.EVENT_BUS.addListener(WallWarmup::tick);
        MinecraftForge.EVENT_BUS.addListener(WallWarmup::frame);
        MinecraftForge.EVENT_BUS.addListener(WallWarmup::opening);
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e)->{WallBatches.clear();GunMeshes.clear();WallWarmup.reset();});
        MinecraftForge.EVENT_BUS.addListener((RegisterClientCommandsEvent e)->e.getDispatcher().register(net.minecraft.commands.Commands.literal("wallgun_stats").executes(context->{context.getSource().sendSuccess(()->net.minecraft.network.chat.Component.literal(WallBatches.stats()),false);return 1;})));
    }
    @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resources -> {WallBatches.clear();GunMeshes.clear();WallWarmup.reset();});
    }
}
