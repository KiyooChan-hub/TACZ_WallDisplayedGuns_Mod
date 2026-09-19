package dev.kiyo.wallgun;

import net.minecraft.core.*;
import net.minecraft.core.component.DataComponentMap;
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
        // Default side turns counterclockwise; flipped side clockwise, as seen by the player.
        if (flip) setPose(-roll, !flipped); else setPose(roll + ((flipped ^ fromBack) ? 1 : -1), flipped);
    }
    public WallGunEntity(BlockPos pos, BlockState state) { super(WallGuns.ENTITY.get(), pos, state); }
    public GunSnapshot snapshot() { return snapshot; }
    public void setSnapshot(GunSnapshot value) {
        snapshot = value; setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.saveAdditional(tag, lookup);
        tag.putInt("DisplayRoll", roll); tag.putInt("DisplayMountRoll",mountRoll); tag.putBoolean("DisplayFlipped", flipped);
        if (snapshot != null) tag.put("OriginalGun", snapshot.copyGun().save(lookup));
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.loadAdditional(tag, lookup);
        roll = Math.floorMod(tag.getInt("DisplayRoll"), 16); mountRoll=Math.floorMod(tag.getInt("DisplayMountRoll"),16)/4*4; flipped = tag.getBoolean("DisplayFlipped");
        snapshot = null;
        ItemStack original = ItemStack.parseOptional(lookup, tag.getCompound("OriginalGun"));
        if (!original.isEmpty() && com.tacz.guns.api.item.IGun.getIGunOrNull(original) != null) snapshot = new GunSnapshot(original);
    }
    @Override protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input); snapshot = input.get(WallGuns.SNAPSHOT.get());
    }
    @Override protected void collectImplicitComponents(DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        if (snapshot != null) builder.set(WallGuns.SNAPSHOT.get(), snapshot);
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider lookup) { return saveWithoutMetadata(lookup); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
