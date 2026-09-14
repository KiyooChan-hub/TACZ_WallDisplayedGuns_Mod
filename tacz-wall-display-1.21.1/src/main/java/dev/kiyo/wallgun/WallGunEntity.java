package dev.kiyo.wallgun;

import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class WallGunEntity extends BlockEntity {
    private ResourceLocation gunId = ResourceLocation.parse("tacz:ak47");
    public WallGunEntity(BlockPos pos, BlockState state) { super(WallGuns.ENTITY.get(), pos, state); }
    public ResourceLocation gunId() { return gunId; }
    public void setGunId(ResourceLocation id) {
        gunId = id; setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider lookup) { super.saveAdditional(tag, lookup); tag.putString("GunId", gunId.toString()); }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.loadAdditional(tag, lookup);
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("GunId"));
        if (id != null) gunId = id;
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider lookup) { return saveWithoutMetadata(lookup); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
