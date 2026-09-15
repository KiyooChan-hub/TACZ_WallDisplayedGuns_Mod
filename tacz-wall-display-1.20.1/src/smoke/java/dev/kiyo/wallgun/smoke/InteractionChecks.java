package dev.kiyo.wallgun.smoke;

import dev.kiyo.wallgun.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.*;
import java.nio.file.*;

final class InteractionChecks {
    private static void require(boolean ok,String what){if(!ok)throw new AssertionError(what);}
    static void run(ServerPlayer player,Path output)throws Exception {
        var level=player.serverLevel();var original=ConversionChecks.equipped(level.registryAccess());
        var snapshot=new GunSnapshot(original);var stick=new ItemStack(Items.STICK);
        for(Direction face:new Direction[]{Direction.UP,Direction.DOWN})for(Direction heading:Direction.Plane.HORIZONTAL) {
            var pos=new BlockPos(0,-54,4);var support=pos.relative(face.getOpposite());
            level.setBlock(support,net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState(),3);
            player.setYRot(heading.toYRot());
            var hit=new BlockHitResult(Vec3.atCenterOf(support),face,support,false);
            var context=new net.minecraft.world.item.context.BlockPlaceContext(player,InteractionHand.MAIN_HAND,WallGuns.decorate(original),hit);
            require(WallGuns.ITEM.get().place(context).consumesAction(),"Floor/ceiling actual placement");
            var gun=(WallGunEntity)level.getBlockEntity(pos);
            int expected=switch(heading){case EAST->4;case SOUTH->8;case WEST->12;default->0;};
            if(face==Direction.DOWN)expected=Math.floorMod(-expected,16);
            require(gun.mountRoll()==expected&&gun.roll()==0,"Initial view heading "+face+"/"+heading);
            gun.setPose(3,false);gun.adjust(true,false);
            require(gun.mountRoll()==expected&&gun.roll()==13&&gun.flipped(),"Flip keeps initial placement heading");
            level.setBlock(pos,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
            level.setBlock(support,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
        }
        for(Direction face:Direction.values()) {
            var pos=new BlockPos(0,-54,4);
            var state=WallGuns.BLOCK.get().defaultBlockState().setValue(WallGunBlock.FACING,face);
            level.setBlock(pos,state,3);var gun=(WallGunEntity)level.getBlockEntity(pos);gun.setSnapshot(snapshot);
            require(state.getDestroySpeed(level,pos)==.5F&&!state.requiresCorrectToolForDrops(),"Hand breaking properties");
            player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);player.getAbilities().flying=true;
            var front=Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(face.getNormal()).scale(2));
            player.setPos(front.x,front.y-player.getEyeHeight(),front.z);
            player.setItemInHand(InteractionHand.MAIN_HAND,stick);player.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.COBBLESTONE));
            var hit=new BlockHitResult(Vec3.atCenterOf(pos),face,pos,false);
            player.setShiftKeyDown(false);
            for(int i=1;i<=16;i++) {
                require(player.gameMode.useItemOn(player,level,stick,InteractionHand.MAIN_HAND,hit).consumesAction(),"Stick interaction "+face);
                require(gun.roll()==i%16&&!gun.flipped(),"Clockwise step "+face+" "+i);
            }
            gun.setMountRoll(face.getAxis().isVertical()?4:0);gun.setPose(3,false);player.setShiftKeyDown(true);
            require(!player.isCrouching(),"Fixture must be flying without crouching pose");
            require(player.gameMode.useItemOn(player,level,stick,InteractionHand.MAIN_HAND,hit).consumesAction(),"Shift bypass hook");
            require(gun.roll()==13&&gun.flipped(),"Flip fixed vertical axis");
            var restored=new WallGunEntity(pos,state);restored.load(gun.saveWithFullMetadata());
            require(restored.roll()==13&&restored.mountRoll()==gun.mountRoll()&&restored.flipped()&&restored.snapshot().equals(snapshot),"Pose disk round trip");
            var network=new WallGunEntity(pos,state);network.load(gun.getUpdateTag());
            require(network.roll()==13&&network.mountRoll()==gun.mountRoll()&&network.flipped()&&network.snapshot().equals(snapshot),"Pose update tag");
            player.gameMode.useItemOn(player,level,stick,InteractionHand.MAIN_HAND,hit);
            require(gun.roll()==3&&!gun.flipped(),"Double flip identity");
            player.setShiftKeyDown(false);var back=Vec3.atCenterOf(pos).subtract(Vec3.atLowerCornerOf(face.getNormal()).scale(2));
            player.setPos(back.x,back.y-player.getEyeHeight(),back.z);
            player.gameMode.useItemOn(player,level,stick,InteractionHand.MAIN_HAND,hit);
            require(gun.roll()==2,"Backside clockwise");require(gun.snapshot().equals(snapshot),"Interaction mutated original gun");
            player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            require(player.gameMode.destroyBlock(pos),"Hand survival break");
            var drops=level.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(2));
            require(drops.size()==1&&ItemStack.matches(drops.get(0).getItem(),original),"Exactly one intact original gun drop face="+face+" harvest="+state.canHarvestBlock(level,pos,player)+" count="+drops.size()+" actual="+drops.stream().map(d->d.getItem().serializeNBT().toString()).toList()+" expected="+original.serializeNBT());drops.forEach(ItemEntity::discard);
            level.setBlock(pos,state,3);((WallGunEntity)level.getBlockEntity(pos)).setSnapshot(snapshot);
            player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            require(player.gameMode.destroyBlock(pos),"Creative break");
            require(level.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(2)).isEmpty(),"Creative must not drop");
        }
        player.setShiftKeyDown(false);player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
        Files.writeString(output.resolve("interactions.txt"),"PASS: six faces; 16 clockwise steps each; flying Shift (without crouching pose), occupied offhand, two flips identity; fixed plate vertical flip after rotation; backside clockwise; pose disk + update-tag persistence; unchanged original gun; hardness 0.5/no tool requirement; six real survival player breaks return exactly one original gun; six creative player breaks drop nothing.\n");
    }
}
