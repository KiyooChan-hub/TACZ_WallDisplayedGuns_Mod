package dev.kiyo.wallgun;

import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

/** Kept registered so existing worlds load; no crafting conversion remains. */
public final class GunConversionRecipe extends CustomRecipe {
    public GunConversionRecipe(ResourceLocation id, CraftingBookCategory category) { super(id, category); }
    @Override public boolean matches(CraftingContainer input, Level level) { return false; }
    @Override public ItemStack assemble(CraftingContainer input, net.minecraft.core.RegistryAccess registry) { return ItemStack.EMPTY; }
    @Override public NonNullList<ItemStack> getRemainingItems(CraftingContainer input) { return NonNullList.withSize(input.getContainerSize(), ItemStack.EMPTY); }
    @Override public boolean canCraftInDimensions(int width, int height) { return width > 0 && height > 0; }
    @Override public RecipeSerializer<?> getSerializer() { return WallGuns.CONVERSION.get(); }
}
