package dev.kiyo.wallgun;

import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Migrate legacy stacks on world decode and before a player accesses inventories. */
public final class LegacyGunMigration {
    private LegacyGunMigration() {}
    public static ItemStack restore(ItemStack stack) {
        if (!stack.is(WallGuns.ITEM.get())) return stack;
        GunSnapshot snapshot = WallGuns.snapshot(stack);
        return snapshot == null ? stack : snapshot.copyGun();
    }
    private static void restore(Container container) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack old = container.getItem(i), updated = restore(old);
            if (updated != old) container.setItem(i, updated);
        }
    }
    public static void init() {
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity().level().isClientSide) return;
            restore(event.getEntity().getInventory());
            restore(event.getEntity().getEnderChestInventory());
        });
        NeoForge.EVENT_BUS.addListener((PlayerContainerEvent.Open event) -> {
            if (event.getEntity().level().isClientSide) return;
            for (var slot : event.getContainer().slots) {
                ItemStack old = slot.getItem(), updated = restore(old);
                if (updated != old) slot.set(updated);
            }
        });
        NeoForge.EVENT_BUS.addListener((EntityJoinLevelEvent event) -> {
            if (event.getLevel().isClientSide) return;
            if (event.getEntity() instanceof ItemEntity item) {
                ItemStack old = item.getItem(), updated = restore(old);
                if (updated != old) item.setItem(updated);
            } else if (event.getEntity() instanceof ItemFrame frame) {
                ItemStack old = frame.getItem(), updated = restore(old);
                if (updated != old) frame.setItem(updated, false);
            }
        });
    }
}
