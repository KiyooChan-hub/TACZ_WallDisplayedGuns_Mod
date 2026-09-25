package dev.kiyo.wallgun.smoke;

import dev.kiyo.wallgun.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import java.nio.file.*;

/** Forge-side feature parity checks that do not depend on legacy conversion recipes. */
final class PlacementChecks {
    static void run(ServerPlayer player, Path output) throws Exception {
        var level=player.serverLevel();
        ItemStack original=ConversionChecks.equipped(level.registryAccess());
        ItemStack legacy=WallGuns.decorate(original);
        check(WallGuns.snapshot(legacy)!=null,"Legacy snapshot missing");
        same(LegacyGunMigration.restore(legacy),original,"Legacy direct migration");
        same(ItemStack.of(legacy.serializeNBT()),original,"Legacy NBT decode migration");
        check(WallGuns.BLOCK.get().defaultBlockState().getDestroySpeed(level,player.blockPosition())==0.0F,"Not instant break");
        for(Direction face:Direction.values()){
            BlockPos support=new BlockPos(70+face.get3DDataValue()*6,-56,10);
            BlockPos target=support.relative(face);
            level.setBlock(support,Blocks.QUARTZ_BLOCK.defaultBlockState(),3);
            var hit=new BlockHitResult(Vec3.atCenterOf(support),face,support,false);
            GunPlacement.set(player,true);
            var oldContext=new BlockPlaceContext(player,InteractionHand.MAIN_HAND,legacy.copy(),hit);
            check(!WallGuns.ITEM.get().place(oldContext).consumesAction(),"Legacy stack placed on "+face);
            check(!WallGuns.ITEM.get().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,hit)).consumesAction(),"Legacy item use placed");
            ItemStack held=original.copy();
            var context=new BlockPlaceContext(player,InteractionHand.MAIN_HAND,held,hit);
            check(WallGuns.ITEM.get().place(context).consumesAction(),"Real gun did not place on "+face);
            var entity=(WallGunEntity)level.getBlockEntity(target);
            check(entity!=null && entity.snapshot()!=null,"No gun snapshot on "+face);
            same(entity.snapshot().copyGun(),original,"Snapshot on "+face);
            same(WallGuns.BLOCK.get().getCloneItemStack(level,target,entity.getBlockState()),original,"Pick block on "+face);
            var drops=net.minecraft.world.level.block.Block.getDrops(entity.getBlockState(),level,target,entity);
            check(drops.size()==1,"Incorrect drop count on "+face);
            same(drops.get(0),original,"Drop on "+face);
            entity.adjust(0,false);check(entity.flipped(),"Flip on "+face);
            for(boolean flipped:new boolean[]{false,true}){
                entity.setPose(0,flipped);
                entity.adjust(1,false);check(entity.roll()==15,"Up must turn counterclockwise on "+face+", flipped="+flipped);
                entity.adjust(-1,false);check(entity.roll()==0,"Down must undo front rotation on "+face+", flipped="+flipped);
                entity.adjust(1,true);check(entity.roll()==1,"Up must follow back view on "+face+", flipped="+flipped);
                entity.adjust(-1,true);check(entity.roll()==0,"Down must undo back rotation on "+face+", flipped="+flipped);
            }
            level.destroyBlock(target,false);
            GunPlacement.set(player,false);
            var disabled=new BlockPlaceContext(player,InteractionHand.MAIN_HAND,original.copy(),hit);
            check(!WallGuns.ITEM.get().place(disabled).consumesAction(),"Placed with mode off on "+face);
            level.destroyBlock(support,false);
        }
        Files.createDirectories(output);
        Files.writeString(output.resolve("placement.txt"),"PASS: legacy stack migration, six-face placement, snapshots, pick-block, drops, flip, scroll rotation, mode gate and instant-break hardness.\n");
    }
    private static void check(boolean okay,String message){if(!okay)throw new AssertionError(message);}
    private static void same(ItemStack actual,ItemStack expected,String message){check(ItemStack.matches(actual,expected),message+": "+actual+" != "+expected);}
}
