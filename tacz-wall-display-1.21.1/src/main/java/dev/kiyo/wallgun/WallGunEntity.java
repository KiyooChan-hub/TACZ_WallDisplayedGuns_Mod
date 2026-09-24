package dev.kiyo.wallgun;

import net.minecraft.core.*;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtAccounter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class WallGunEntity extends BlockEntity {
    private GunSnapshot snapshot;
    private byte[] compactGun;
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
        snapshot = value; compactGun = null; setChanged();
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
        snapshot = null; compactGun = tag.contains("CompactGun") ? tag.getByteArray("CompactGun") : null;
        CompoundTag originalTag = tag.getCompound("OriginalGun");
        if (originalTag.isEmpty() && compactGun != null) {
            try { originalTag = NbtIo.readCompressed(new ByteArrayInputStream(compactGun), NbtAccounter.create(2_097_152L)); }
            catch (IOException | RuntimeException ex) { WallGuns.LOG.warn("Invalid compressed decorative gun at {}", worldPosition, ex); }
        }
        ItemStack original = ItemStack.parseOptional(lookup, originalTag);
        if (!original.isEmpty() && com.tacz.guns.api.item.IGun.getIGunOrNull(original) != null) snapshot = new GunSnapshot(original);
    }
    @Override protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input); snapshot = input.get(WallGuns.SNAPSHOT.get()); compactGun = null;
    }
    @Override protected void collectImplicitComponents(DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        if (snapshot != null) builder.set(WallGuns.SNAPSHOT.get(), snapshot);
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider lookup) {
        var tag = new CompoundTag();
        tag.putInt("DisplayRoll", roll); tag.putInt("DisplayMountRoll", mountRoll); tag.putBoolean("DisplayFlipped", flipped);
        if (snapshot != null) {
            if (compactGun == null) try (var bytes = new ByteArrayOutputStream()) {
                NbtIo.writeCompressed((CompoundTag) snapshot.copyGun().save(lookup), bytes);
                compactGun = bytes.toByteArray();
            } catch (IOException ex) {
                WallGuns.LOG.warn("Could not compact decorative gun at {}", worldPosition, ex);
                tag.put("OriginalGun", snapshot.copyGun().save(lookup));
            }
            if (compactGun != null) tag.putByteArray("CompactGun", compactGun);
        }
        return tag;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
