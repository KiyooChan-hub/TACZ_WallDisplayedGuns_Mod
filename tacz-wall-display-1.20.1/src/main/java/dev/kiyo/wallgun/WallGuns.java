package dev.kiyo.wallgun;
import com.mojang.logging.LogUtils;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.*;
import org.slf4j.Logger;
@Mod(WallGuns.ID)
public final class WallGuns {
    public static final String ID="tacz_wall_display";
    public static final Logger LOG=LogUtils.getLogger();
    private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS,ID);
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES=DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES,ID);
    private static final DeferredRegister<RecipeSerializer<?>> RECIPES=DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS,ID);
    private static final DeferredRegister<SoundEvent> SOUNDS=DeferredRegister.create(ForgeRegistries.SOUND_EVENTS,ID);
    public static final RegistryObject<SoundEvent> MODE_OPEN=SOUNDS.register("mode_open",()->SoundEvent.createVariableRangeEvent(new ResourceLocation(ID,"mode_open")));
    public static final RegistryObject<SoundEvent> MODE_CLOSE=SOUNDS.register("mode_close",()->SoundEvent.createVariableRangeEvent(new ResourceLocation(ID,"mode_close")));
    public static final RegistryObject<WallGunBlock> BLOCK=BLOCKS.register("decorative_gun",()->new WallGunBlock(BlockBehaviour.Properties.of().strength(0.0F).sound(net.minecraft.world.level.block.SoundType.STONE).noOcclusion().noCollission()));
    public static final RegistryObject<WallGunItem> ITEM=ITEMS.register("decorative_gun",()->new WallGunItem(BLOCK.get(),new Item.Properties().stacksTo(1)));
    public static final RegistryObject<BlockEntityType<WallGunEntity>> ENTITY=ENTITIES.register("decorative_gun",()->BlockEntityType.Builder.of(WallGunEntity::new,BLOCK.get()).build(null));
    public static final RegistryObject<SimpleCraftingRecipeSerializer<GunConversionRecipe>> CONVERSION=RECIPES.register("gun_conversion",()->new SimpleCraftingRecipeSerializer<>(GunConversionRecipe::new));
    public WallGuns() {
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.CLIENT,WallGunConfig.SPEC);
        var bus=FMLJavaModLoadingContext.get().getModEventBus();BLOCKS.register(bus);ITEMS.register(bus);ENTITIES.register(bus);RECIPES.register(bus);SOUNDS.register(bus);
        PlacementPayloads.init();GunPlacement.init();LegacyGunMigration.init();
    }
    public static ItemStack stack(GunSnapshot snapshot) {
        if(snapshot==null)return ItemStack.EMPTY;
        var stack=new ItemStack(ITEM.get());stack.getOrCreateTag().put("OriginalGun",snapshot.save());return stack;
    }
    public static ItemStack decorate(ItemStack original){return stack(new GunSnapshot(original));}
    public static GunSnapshot snapshot(ItemStack stack){return stack.is(ITEM.get()) && stack.hasTag() ? GunSnapshot.read(stack.getTag().getCompound("OriginalGun")) : null;}
}
