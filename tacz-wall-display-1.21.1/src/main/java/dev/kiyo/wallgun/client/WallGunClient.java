package dev.kiyo.wallgun.client;
import dev.kiyo.wallgun.WallGuns;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.extensions.common.*;
import net.neoforged.neoforge.common.NeoForge;

@EventBusSubscriber(modid=WallGuns.ID, value=Dist.CLIENT, bus=EventBusSubscriber.Bus.MOD)
public final class WallGunClient {
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(WallGuns.ENTITY.get(),WallGunRenderer::new);
        NeoForge.EVENT_BUS.addListener(WallBatches::render);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e)->{WallBatches.clear();GunMeshes.clear();});
        NeoForge.EVENT_BUS.addListener((RegisterClientCommandsEvent e)->e.getDispatcher().register(net.minecraft.commands.Commands.literal("wallgun_stats").executes(context->{context.getSource().sendSuccess(()->net.minecraft.network.chat.Component.literal(WallBatches.stats()),false);return 1;})));
    }
    @SubscribeEvent public static void extensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            @Override public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer(){return WallGunIcon.INSTANCE;}
        },WallGuns.ITEM.get());
    }
    @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resources -> {WallBatches.clear();GunMeshes.clear();});
    }
}
