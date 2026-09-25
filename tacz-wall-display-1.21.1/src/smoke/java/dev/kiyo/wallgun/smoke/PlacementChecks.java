package dev.kiyo.wallgun.smoke;

import dev.kiyo.wallgun.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import java.nio.file.*;
import java.util.*;

final class PlacementChecks {
    static void run(ServerPlayer player, Path output) throws Exception {
        var world = player.serverLevel();
        var lookup = world.registryAccess();
        ItemStack original = ConversionChecks.equipped(lookup);
        ItemStack legacy = WallGuns.decorate(original);
        var input = CraftingInput.of(1, 1, List.of(original));
        check(world.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, world).isEmpty(), "Forward recipe still exists");
        check(world.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, CraftingInput.of(1, 1, List.of(legacy)), world).isEmpty(), "Reverse recipe still exists");
        same(LegacyGunMigration.restore(legacy), original, "Direct migration");
        CompoundTag tag = (CompoundTag) legacy.save(lookup);
        same(ItemStack.parseOptional(lookup, tag), original, "NBT migration at load");
        check(WallGuns.snapshot(legacy) != null, "Legacy registry lost gun data");
        check(WallGuns.BLOCK.get().defaultBlockState().getDestroySpeed(world, player.blockPosition()) == 0.0F,
                "Decorative gun is not instant-break hardness");
        for (Direction direction : Direction.values()) {
            BlockPos wall = new BlockPos(70 + direction.get3DDataValue() * 6, -56, 10);
            BlockPos pos = wall.relative(direction);
            world.setBlock(wall, Blocks.QUARTZ_BLOCK.defaultBlockState(), 3);
            var hit = new BlockHitResult(Vec3.atCenterOf(wall), direction, wall, false);
            var oldContext = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, legacy.copy(), hit);
            check(!WallGuns.ITEM.get().place(oldContext).consumesAction(), "Old item placed " + direction);
            GunPlacement.set(player, true);
            if (direction == Direction.DOWN) player.gameMode.changeGameModeForPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            ItemStack held = original.copy();
            var context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, held, hit);
            check(WallGuns.ITEM.get().place(context).consumesAction(), "Real gun did not place " + direction);
            if (direction == Direction.DOWN) {
                check(held.isEmpty(), "Survival placement failed to consume the real gun");
                player.gameMode.changeGameModeForPlayer(net.minecraft.world.level.GameType.CREATIVE);
            }
            var entity = (WallGunEntity) world.getBlockEntity(pos);
            check(entity != null && entity.snapshot() != null, "Missing snapshot " + direction);
            same(entity.snapshot().copyGun(), original, "Placed gun data " + direction);
            same(WallGuns.BLOCK.get().getCloneItemStack(world, pos, entity.getBlockState()), original, "Creative pick " + direction);
            var drops = net.minecraft.world.level.block.Block.getDrops(entity.getBlockState(), world, pos, entity);
            check(drops.size() == 1, "Wrong drop count " + direction);
            same(drops.getFirst(), original, "Breaking drop " + direction);
            world.destroyBlock(pos, false);
            GunPlacement.set(player, false);
            var disabledContext = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, original.copy(), hit);
            check(!WallGuns.ITEM.get().place(disabledContext).consumesAction(), "Placed while mode off " + direction);
            world.destroyBlock(wall, false);
        }
        Files.createDirectories(output);
        Files.writeString(output.resolve("placement.txt"), "PASS: instant-break hardness, recipes removed, legacy item NBT restored, direct legacy placement denied, six-face real-gun placement, data, creative pick, drops, mode-off rejection.\n");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void same(ItemStack actual, ItemStack expected, String message) { check(ItemStack.matches(actual, expected), message + ": " + actual + " != " + expected); }
}
