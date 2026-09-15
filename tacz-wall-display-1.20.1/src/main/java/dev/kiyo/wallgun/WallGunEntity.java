package dev.kiyo.wallgun;

import net.minecraft.core.*;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class WallGunEntity extends BlockEntity {
    private GunSnapshot snapshot;
    private int roll;
    private int mountRoll;
    private boolean flipped;
    public int roll() { return roll; }
    public int mountRoll() { return mountRoll; }
    public void setMountRoll(int steps) { mountRoll=Math.floorMod(steps,16)/4*4; setPose(roll,flipped); }
    public boolean flipped() { return flipped; }
    public void setPose(int steps, boolean otherSide) {
        roll = Math.floorMod(steps, 16); flipped = otherSide; setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
    public void adjust(boolean flip, boolean fromBack) {
        // Pose = R(roll) * F. A flip around the mounting plate's fixed vertical axis is F * R = R(-roll) * F.
        if (flip) setPose(-roll, !flipped); else setPose(roll + (fromBack ? -1 : 1), flipped);
    }
    public WallGunEntity(BlockPos pos, BlockState state) { super(WallGuns.ENTITY.get(), pos, state); }
    public GunSnapshot snapshot() { return snapshot; }
    public void setSnapshot(GunSnapshot value) {
        snapshot = value; setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("DisplayRoll", roll); tag.putInt("DisplayMountRoll",mountRoll); tag.putBoolean("DisplayFlipped", flipped);
        if (snapshot != null) tag.put("OriginalGun", snapshot.save());
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag);
        roll=Math.floorMod(tag.getInt("DisplayRoll"),16);mountRoll=Math.floorMod(tag.getInt("DisplayMountRoll"),16)/4*4;flipped=tag.getBoolean("DisplayFlipped");
        snapshot=GunSnapshot.read(tag.getCompound("OriginalGun"));
    }
    @Override public net.minecraft.world.phys.AABB getRenderBoundingBox(){return new net.minecraft.world.phys.AABB(worldPosition).inflate(1);}
    @Override public CompoundTag getUpdateTag(){return saveWithoutMetadata();}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
}
