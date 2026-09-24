package dev.kiyo.wallgun;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

/** Exactly one occupied slot, anywhere in either crafting grid. Vanilla owns consumption. */
public final class GunConversionRecipe extends CustomRecipe {
    public GunConversionRecipe(CraftingBookCategory category) { super(category); }
    private ItemStack ingredient(CraftingInput input) {
        if (input.ingredientCount() != 1) return ItemStack.EMPTY;
        for (ItemStack stack : input.items()) if (!stack.isEmpty()) return stack;
        return ItemStack.EMPTY;
    }
    @Override public boolean matches(CraftingInput input, Level level) {
        return false;
    }
    @Override public ItemStack assemble(CraftingInput input, HolderLookup.Provider lookup) {
        ItemStack stack = ingredient(input);
        return ItemStack.EMPTY;
    }
    @Override public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        // The input gun is transferred, never additionally returned as a container remainder.
        return NonNullList.withSize(input.size(), ItemStack.EMPTY);
    }
    @Override public boolean canCraftInDimensions(int width, int height) { return width > 0 && height > 0; }
    @Override public RecipeSerializer<?> getSerializer() { return WallGuns.CONVERSION.get(); }
}
