package dev.kiyo.wallgun;

import com.mojang.logging.LogUtils;
import com.tacz.guns.api.TimelessAPI;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
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
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ID);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceLocation>> GUN_ID = COMPONENTS.register("gun_id", () -> DataComponentType.<ResourceLocation>builder().persistent(ResourceLocation.CODEC).networkSynchronized(ResourceLocation.STREAM_CODEC).build());
    public static final DeferredBlock<WallGunBlock> BLOCK = BLOCKS.register("wall_gun", () -> new WallGunBlock(BlockBehaviour.Properties.of().strength(0.5F).noOcclusion().noCollission()));
    public static final DeferredItem<WallGunItem> ITEM = ITEMS.register("wall_gun", () -> new WallGunItem(BLOCK.get(), new Item.Properties()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WallGunEntity>> ENTITY = ENTITIES.register("wall_gun", () -> BlockEntityType.Builder.of(WallGunEntity::new, BLOCK.get()).build(null));
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("guns", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.tacz_wall_display"))
            .icon(() -> stack(ResourceLocation.parse("tacz:ak47")))
            .displayItems((params, output) -> TimelessAPI.getAllCommonGunIndex().stream()
                    .sorted(java.util.Comparator.comparing(e -> e.getKey().toString()))
                    .forEach(e -> output.accept(stack(e.getKey())))).build());
    public WallGuns(IEventBus bus) {
        COMPONENTS.register(bus); BLOCKS.register(bus); ITEMS.register(bus); ENTITIES.register(bus); TABS.register(bus);
    }
    public static ItemStack stack(ResourceLocation id) { ItemStack stack = new ItemStack(ITEM.get()); stack.set(GUN_ID.get(), id); return stack; }
    public static ResourceLocation gunId(ItemStack stack) { return stack.getOrDefault(GUN_ID.get(), ResourceLocation.parse("tacz:ak47")); }
}
