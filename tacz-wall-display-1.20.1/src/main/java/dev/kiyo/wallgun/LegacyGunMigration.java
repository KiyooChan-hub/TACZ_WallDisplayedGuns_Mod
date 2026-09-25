package dev.kiyo.wallgun;

import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

/** Convert old decorative item stacks back to their original TACZ guns. */
public final class LegacyGunMigration {
    private LegacyGunMigration() {}
    public static ItemStack restore(ItemStack stack) {
        if (!stack.is(WallGuns.ITEM.get())) return stack;
        GunSnapshot snapshot=WallGuns.snapshot(stack);
        return snapshot==null ? stack : snapshot.copyGun();
    }
    private static void restore(Container container) {
        for(int i=0;i<container.getContainerSize();i++){
            ItemStack old=container.getItem(i), updated=restore(old);
            if(updated!=old)container.setItem(i,updated);
        }
    }
    public static void init() {
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e)->{
            if(e.getEntity().level().isClientSide)return;
            restore(e.getEntity().getInventory());restore(e.getEntity().getEnderChestInventory());
        });
        MinecraftForge.EVENT_BUS.addListener((PlayerContainerEvent.Open e)->{
            if(e.getEntity().level().isClientSide)return;
            for(var slot:e.getContainer().slots){ItemStack old=slot.getItem(), updated=restore(old);if(updated!=old)slot.set(updated);}
        });
        MinecraftForge.EVENT_BUS.addListener((EntityJoinLevelEvent e)->{
            if(e.getLevel().isClientSide())return;
            if(e.getEntity() instanceof ItemEntity item){ItemStack old=item.getItem(),updated=restore(old);if(updated!=old)item.setItem(updated);}
            else if(e.getEntity() instanceof ItemFrame frame){ItemStack old=frame.getItem(),updated=restore(old);if(updated!=old)frame.setItem(updated,false);}
        });
    }
}
