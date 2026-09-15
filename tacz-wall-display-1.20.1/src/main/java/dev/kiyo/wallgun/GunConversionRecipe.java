package dev.kiyo.wallgun;

import com.tacz.guns.api.item.IGun;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

/** Exactly one occupied slot, anywhere in either crafting grid. Vanilla owns consumption. */
public final class GunConversionRecipe extends CustomRecipe {
    public GunConversionRecipe(net.minecraft.resources.ResourceLocation id, CraftingBookCategory category) { super(id, category); }
    private ItemStack ingredient(net.minecraft.world.inventory.CraftingContainer input) {
        ItemStack found=ItemStack.EMPTY;
        for(int i=0;i<input.getContainerSize();i++)if(!input.getItem(i).isEmpty()) {
            if(!found.isEmpty())return ItemStack.EMPTY;found=input.getItem(i);
        }
        return found;
    }
    @Override public boolean matches(net.minecraft.world.inventory.CraftingContainer input, Level level) {
        ItemStack stack = ingredient(input);
        return !stack.isEmpty() && (IGun.getIGunOrNull(stack) != null || WallGuns.snapshot(stack) != null);
    }
    @Override public ItemStack assemble(net.minecraft.world.inventory.CraftingContainer input, net.minecraft.core.RegistryAccess lookup) {
        ItemStack stack = ingredient(input);
        if (stack.isEmpty()) return ItemStack.EMPTY;
        var snapshot = WallGuns.snapshot(stack);
        if (snapshot != null) return snapshot.copyGun();
        return IGun.getIGunOrNull(stack) != null ? WallGuns.decorate(stack) : ItemStack.EMPTY;
    }
    @Override public NonNullList<ItemStack> getRemainingItems(net.minecraft.world.inventory.CraftingContainer input) {
        // The input gun is transferred, never additionally returned as a container remainder.
        return NonNullList.withSize(input.getContainerSize(), ItemStack.EMPTY);
    }
    @Override public boolean canCraftInDimensions(int width, int height) { return width > 0 && height > 0; }
    @Override public RecipeSerializer<?> getSerializer() { return WallGuns.CONVERSION.get(); }
}
