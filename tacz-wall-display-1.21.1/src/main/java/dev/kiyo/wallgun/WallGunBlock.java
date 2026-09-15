package dev.kiyo.wallgun;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.shapes.*;

public final class WallGunBlock extends BaseEntityBlock {
    public static final MapCodec<WallGunBlock> CODEC = simpleCodec(WallGunBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public WallGunBlock(Properties properties) { super(properties); registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH)); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction face = ctx.getClickedFace();
        return WallGuns.snapshot(ctx.getItemInHand()) == null ? null : defaultBlockState().setValue(FACING, face);
    }
    @Override protected java.util.List<ItemStack> getDrops(BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder params) {
        var entity = params.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY);
        params.withDynamicDrop(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(WallGuns.ID, "original_gun"), output -> {
            if (entity instanceof WallGunEntity gun && gun.snapshot() != null) output.accept(gun.snapshot().copyGun());
        });
        return super.getDrops(state, params);
    }
    @Override protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        if (!stack.is(Items.STICK)) return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!(level.getBlockEntity(pos) instanceof WallGunEntity gun) || gun.snapshot() == null) return net.minecraft.world.ItemInteractionResult.FAIL;
        if (!level.isClientSide) {
            Direction face = state.getValue(FACING);
            var towardPlayer = player.getEyePosition().subtract(net.minecraft.world.phys.Vec3.atCenterOf(pos));
            boolean back = towardPlayer.dot(net.minecraft.world.phys.Vec3.atLowerCornerOf(face.getNormal())) < 0;
            gun.adjust(player.isShiftKeyDown(), back);
            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.ITEM_FRAME_ADD_ITEM, net.minecraft.sounds.SoundSource.BLOCKS, 1, 1);
        }
        return net.minecraft.world.ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.ENTITYBLOCK_ANIMATED; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        // A stable, thin mounting plate remains targetable at every roll and from either side.
        return switch (state.getValue(FACING)) {
            case SOUTH -> box(0, 0, 0, 16, 16, 5);
            case NORTH -> box(0, 0, 11, 16, 16, 16);
            case EAST -> box(0, 0, 0, 5, 16, 16);
            case WEST -> box(11, 0, 0, 16, 16, 16);
            case UP -> box(0, 0, 0, 16, 5, 16);
            case DOWN -> box(0, 11, 0, 16, 16, 16);
        };
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new WallGunEntity(pos, state); }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity entity, ItemStack stack) {
        super.setPlacedBy(level, pos, state, entity, stack);
        if (level.getBlockEntity(pos) instanceof WallGunEntity gun) {
            gun.setSnapshot(WallGuns.snapshot(stack));
            if (entity != null && state.getValue(FACING).getAxis().isVertical()) {
                int roll = switch (entity.getDirection()) { case EAST -> 4; case SOUTH -> 8; case WEST -> 12; default -> 0; };
                gun.setMountRoll(state.getValue(FACING) == Direction.DOWN ? -roll : roll);
            }
        }
    }
    @Override public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return level.getBlockEntity(pos) instanceof WallGunEntity gun ? WallGuns.stack(gun.snapshot()) : ItemStack.EMPTY;
    }
    @Override protected BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override protected BlockState mirror(BlockState state, Mirror mirror) { return state.rotate(mirror.getRotation(state.getValue(FACING))); }
}
