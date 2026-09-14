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
    public WallGunEntity(BlockPos pos, BlockState state) { super(WallGuns.ENTITY.get(), pos, state); }
    public GunSnapshot snapshot() { return snapshot; }
    public void setSnapshot(GunSnapshot value) {
        snapshot = value; setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.saveAdditional(tag, lookup);
        if (snapshot != null) tag.put("OriginalGun", snapshot.copyGun().save(lookup));
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.loadAdditional(tag, lookup);
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
