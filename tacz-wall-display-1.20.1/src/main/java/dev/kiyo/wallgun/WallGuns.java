package dev.kiyo.wallgun;
import com.mojang.logging.LogUtils;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.Event;
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
    public static final RegistryObject<WallGunBlock> BLOCK=BLOCKS.register("decorative_gun",()->new WallGunBlock(BlockBehaviour.Properties.of().strength(.5F).noOcclusion().noCollission()));
    public static final RegistryObject<WallGunItem> ITEM=ITEMS.register("decorative_gun",()->new WallGunItem(BLOCK.get(),new Item.Properties().stacksTo(1)));
    public static final RegistryObject<BlockEntityType<WallGunEntity>> ENTITY=ENTITIES.register("decorative_gun",()->BlockEntityType.Builder.of(WallGunEntity::new,BLOCK.get()).build(null));
    public static final RegistryObject<SimpleCraftingRecipeSerializer<GunConversionRecipe>> CONVERSION=RECIPES.register("gun_conversion",()->new SimpleCraftingRecipeSerializer<>(GunConversionRecipe::new));
    public WallGuns() {
        var bus=FMLJavaModLoadingContext.get().getModEventBus();BLOCKS.register(bus);ITEMS.register(bus);ENTITIES.register(bus);RECIPES.register(bus);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock event)->{
            if(event.getItemStack().is(Items.STICK) && event.getLevel().getBlockState(event.getPos()).is(BLOCK.get()))event.setUseBlock(Event.Result.ALLOW);
        });
    }
    public static ItemStack stack(GunSnapshot snapshot) {
        if(snapshot==null)return ItemStack.EMPTY;
        var stack=new ItemStack(ITEM.get());stack.getOrCreateTag().put("OriginalGun",snapshot.save());return stack;
    }
    public static ItemStack decorate(ItemStack original){return stack(new GunSnapshot(original));}
    public static GunSnapshot snapshot(ItemStack stack){return stack.is(ITEM.get()) && stack.hasTag() ? GunSnapshot.read(stack.getTag().getCompound("OriginalGun")) : null;}
}
