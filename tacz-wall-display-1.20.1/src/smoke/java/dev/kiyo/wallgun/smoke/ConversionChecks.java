package dev.kiyo.wallgun.smoke;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.*;
import dev.kiyo.wallgun.*;
import net.minecraft.core.*;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;

import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import java.nio.file.*;
import java.util.*;

final class ConversionChecks {
    static CraftingContainer input(int w,int h,List<ItemStack> items) {
        var menu=new AbstractContainerMenu(null,0) {
            @Override public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player p,int i){return ItemStack.EMPTY;}
            @Override public boolean stillValid(net.minecraft.world.entity.player.Player p){return true;}
        };
        var input=new TransientCraftingContainer(menu,w,h);
        for(int i=0;i<items.size();i++)input.setItem(i,items.get(i));return input;
    }

    static ItemStack equipped(net.minecraft.core.RegistryAccess lookup) {
        ItemStack gun=GunItemBuilder.create().setId(new ResourceLocation("tacz:scar_l")).setAmmoCount(7).setAmmoInBarrel(true)
                .setFireMode(com.tacz.guns.api.item.gun.FireMode.SEMI).build();
        IGun api=IGun.getIGunOrNull(gun);
        for(String id:List.of("scope_acog_ta31","muzzle_silencer_knight_qd","grip_vertical_military")) {
            ItemStack attachment=AttachmentItemBuilder.create().setId(new ResourceLocation("tacz:"+id)).build();
            require(api.allowAttachment(gun,attachment),"Invalid attachment fixture "+id);
            api.installAttachment(gun,attachment);
        }
        gun.setHoverName(Component.literal("回归测试 · SCAR / 7+1"));
        gun.getOrCreateTag().putString("wallgun_unknown_test","preserve me");gun.getOrCreateTag().putIntArray("wallgun_array_test",new int[]{1,7,42});
        return gun;
    }
    static void run(ServerPlayer player,Path output)throws Exception {
        var world=player.serverLevel();var lookup=world.registryAccess();
        ItemStack original=equipped(lookup),decor=WallGuns.decorate(original);
        require(decor.getMaxStackSize()==1,"Decoration must not stack");
        var recipe=new GunConversionRecipe(new ResourceLocation("tacz_wall_display:gun_conversion"),CraftingBookCategory.MISC);
        for(int size:List.of(2,3))for(int slot=0;slot<size*size;slot++) {
            var items=new ArrayList<ItemStack>(Collections.nCopies(size*size,ItemStack.EMPTY));items.set(slot,original.copy());
            var input=input(size,size,items);
            var holder=world.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,world).orElseThrow();
            require(holder instanceof GunConversionRecipe,"Dynamic recipe not selected");
            same(holder.assemble(input,lookup),decor,"Forward grid position "+size+"/"+slot);
            items.set(slot,decor.copy());input=input(size,size,items);
            same(recipe.assemble(input,lookup),original,"Reverse grid position "+size+"/"+slot);
        }
        require(!recipe.matches(input(2,2,List.of(original,original,ItemStack.EMPTY,ItemStack.EMPTY)),world),"Multiple guns accepted");
        require(!recipe.matches(input(1,1,List.of(new ItemStack(WallGuns.ITEM.get()))),world),"Empty decoration minted a gun");
        require(!recipe.matches(input(1,1,List.of(new ItemStack(Items.STONE))),world),"Unrelated item accepted");
        // Mutation isolation and persistent + packet serialization of all nested components.
        GunSnapshot snapshot=new GunSnapshot(original);ItemStack mutated=snapshot.copyGun();IGun.getIGunOrNull(mutated).setCurrentAmmoCount(mutated,0);
        same(snapshot.copyGun(),original,"Snapshot defensive copy");
        same(ItemStack.of(decor.serializeNBT()),decor,"Item disk codec");
        var buffer=new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {buffer.writeItem(decor);same(buffer.readItem(),decor,"Item network codec");}finally{buffer.release();}
        player.getInventory().clearContent();
        // Exercise vanilla result-slot consumption for both menus, not only recipe.assemble().
        for(boolean table:List.of(false,true)) {
            AbstractContainerMenu menu=table?new CraftingMenu(83,player.getInventory(),ContainerLevelAccess.create(world,new BlockPos(0,-60,0))):player.inventoryMenu;
            int last=table?9:4;
            menu.getSlot(last).set(original.copy());same(menu.getSlot(0).getItem(),decor,"Live preview");
            // Swap same gun ID but different persistent data; old result must not survive.
            ItemStack different=original.copy();IGun.getIGunOrNull(different).setCurrentAmmoCount(different,2);
            menu.getSlot(last).set(different);same(menu.getSlot(0).getItem(),WallGuns.decorate(different),"Preview input swap");
            menu.getSlot(last).set(original.copy());
            menu.clicked(0,0,ClickType.PICKUP,player);same(menu.getCarried(),decor,"Take decoration");
            require(menu.getSlot(last).getItem().isEmpty()&&menu.getSlot(0).getItem().isEmpty(),"Forward input not consumed once");
            menu.setCarried(ItemStack.EMPTY);menu.getSlot(last).set(decor.copy());
            menu.clicked(0,0,ClickType.PICKUP,player);same(menu.getCarried(),original,"Take restored gun");
            require(menu.getSlot(last).getItem().isEmpty()&&menu.getSlot(0).getItem().isEmpty(),"Reverse input not consumed once");menu.setCarried(ItemStack.EMPTY);
            for(ItemStack input:List.of(original,decor)) {
                player.getInventory().clearContent();menu.getSlot(last).set(input.copy());
                ItemStack expected=input.is(WallGuns.ITEM.get())?original:decor;
                menu.clicked(0,0,ClickType.QUICK_MOVE,player);
                require(menu.getSlot(last).getItem().isEmpty(),"Shift craft left ingredient");
                var result=player.getInventory().items.stream().filter(s->!s.isEmpty()).toList();
                require(result.size()==1,"Shift craft duplication/loss");same(result.get(0),expected,"Shift craft output");
            }
            // A full destination must leave the ingredient in place and consume nothing.
            for(int i=0;i<36;i++)player.getInventory().setItem(i,new ItemStack(Items.STONE,64));
            menu.getSlot(last).set(original.copy());menu.clicked(0,0,ClickType.QUICK_MOVE,player);
            same(menu.getSlot(last).getItem(),original,"Full inventory ingredient");same(menu.getSlot(0).getItem(),decor,"Full inventory preview");
            player.getInventory().clearContent();menu.removed(player);
            var cancelled=player.getInventory().items.stream().filter(s->!s.isEmpty()).toList();
            require(cancelled.size()==1,"Cancel duplicated or lost gun");same(cancelled.get(0),original,"Cancel returns input only");player.getInventory().clearContent();
        }
        // Place using the actual BlockItem path, then save/load, clone and harvest through vanilla loot.
        for(Direction direction:Direction.values()) {
            BlockPos wall=new BlockPos(60+direction.get3DDataValue()*6,-56,10),pos=wall.relative(direction);
            world.setBlock(wall,Blocks.QUARTZ_BLOCK.defaultBlockState(),3);
            var hit=new BlockHitResult(Vec3.atCenterOf(wall),direction,wall,false);
            var context=new BlockPlaceContext(player,InteractionHand.MAIN_HAND,decor.copy(),hit);
            require(WallGuns.ITEM.get().place(context).consumesAction(),"Actual BlockItem placement "+direction);
            var entity=(WallGunEntity)world.getBlockEntity(pos);require(entity!=null,"No placed BE");same(entity.snapshot().copyGun(),original,"Placed snapshot");
            var restored=new WallGunEntity(pos,entity.getBlockState());restored.load(entity.saveWithFullMetadata());
            same(restored.snapshot().copyGun(),original,"BE disk round trip");
            same(WallGuns.BLOCK.get().getCloneItemStack(world,pos,entity.getBlockState()),decor,"Creative clone");
            var drops=net.minecraft.world.level.block.Block.getDrops(entity.getBlockState(),world,pos,entity);require(drops.size()==1,"Loot count");same(drops.get(0),original,"Loot original gun snapshot");
            // Removing the support preserves the display, then a normal destruction yields exactly the original gun.
            world.destroyBlock(wall,false,player);
            require(world.getBlockState(pos).is(WallGuns.BLOCK.get()),"Floating gun lost support");
            world.destroyBlock(pos,true,player);
            require(world.getBlockState(pos).isAir(),"Break/support left block");
            var entities=world.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(2));require(entities.size()==1,"Break duplicated/lost drop "+direction+" count="+entities.size());
            same(entities.get(0).getItem(),original,"Dropped original after removal");entities.forEach(ItemEntity::discard);
        }
        Files.createDirectories(output);
        Files.writeString(output.resolve("conversion.txt"),"PASS: 13 grid positions in 2x2/3x3; complete gun/components/3 attachments/7+1 rounds/name/custom tag round trips; item disk + network codecs; immutable snapshots; live output swap, normal take, forward/reverse shift craft, full inventory, cancel; actual placement on six faces, BE disk persistence, clone, loot, original-gun breaking drops and survival without support. No preset registry migration.\n");
    }
    private static void same(ItemStack actual,ItemStack expected,String label) {require(ItemStack.matches(actual,expected),label+" mismatch: "+actual+" expected "+expected);}
    private static void require(boolean ok,String message) {if(!ok)throw new AssertionError(message);}
}
