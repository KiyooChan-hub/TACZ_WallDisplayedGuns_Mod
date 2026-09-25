package dev.kiyo.wallgun;

import com.mojang.logging.LogUtils;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.*;
import org.slf4j.Logger;

@Mod(WallGuns.ID)
public final class WallGuns {
    public static final String ID = "tacz_wall_display";
    public static final Logger LOG = LogUtils.getLogger();
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ID);
    private static final DeferredRegister<DataComponentType<?>> COMPONENTS = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, ID);
    private static final DeferredRegister<RecipeSerializer<?>> RECIPES = DeferredRegister.create(Registries.RECIPE_SERIALIZER, ID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, ID);
    public static final DeferredHolder<SoundEvent, SoundEvent> MODE_OPEN = SOUNDS.register("mode_open", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(ID, "mode_open")));
    public static final DeferredHolder<SoundEvent, SoundEvent> MODE_CLOSE = SOUNDS.register("mode_close", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(ID, "mode_close")));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GunSnapshot>> SNAPSHOT = COMPONENTS.register("original_gun", () -> DataComponentType.<GunSnapshot>builder().persistent(GunSnapshot.CODEC).networkSynchronized(GunSnapshot.STREAM_CODEC).build());
    // Deliberately new registry IDs: old gun-id-only blocks cannot own or return a real gun.
    public static final DeferredBlock<WallGunBlock> BLOCK = BLOCKS.register("decorative_gun", () -> new WallGunBlock(BlockBehaviour.Properties.of().strength(0.0F).sound(net.minecraft.world.level.block.SoundType.STONE).noOcclusion().noCollission()));
    public static final DeferredItem<WallGunItem> ITEM = ITEMS.register("decorative_gun", () -> new WallGunItem(BLOCK.get(), new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WallGunEntity>> ENTITY = ENTITIES.register("decorative_gun", () -> BlockEntityType.Builder.of(WallGunEntity::new, BLOCK.get()).build(null));
    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<GunConversionRecipe>> CONVERSION = RECIPES.register("gun_conversion", () -> new SimpleCraftingRecipeSerializer<>(GunConversionRecipe::new));
    public WallGuns(IEventBus bus, net.neoforged.fml.ModContainer container) {
        COMPONENTS.register(bus); BLOCKS.register(bus); ITEMS.register(bus); ENTITIES.register(bus); RECIPES.register(bus); SOUNDS.register(bus);
        bus.addListener(PlacementPayloads::register);
        GunPlacement.init();
        LegacyGunMigration.init();
    }
    public static ItemStack stack(GunSnapshot snapshot) {
        if (snapshot == null) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(ITEM.get()); stack.set(SNAPSHOT.get(), snapshot); return stack;
    }
    public static ItemStack decorate(ItemStack original) { return stack(new GunSnapshot(original)); }
    public static GunSnapshot snapshot(ItemStack stack) { return stack.is(ITEM.get()) ? stack.get(SNAPSHOT.get()) : null; }
}
