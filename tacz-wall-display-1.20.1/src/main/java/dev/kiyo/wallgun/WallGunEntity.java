package dev.kiyo.wallgun;

import net.minecraft.core.*;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
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
    public void adjust(int operation, boolean fromBack) {
        // Pose = R(roll) * F. A flip around the mounting plate's fixed vertical axis is F * R = R(-roll) * F.
        if (operation==0) setPose(-roll, !flipped);
        else if (operation==1 || operation==-1) setPose(roll + ((flipped ^ fromBack) ? 1 : -1) * operation, flipped);
    }
    public WallGunEntity(BlockPos pos, BlockState state) { super(WallGuns.ENTITY.get(), pos, state); }
    public GunSnapshot snapshot() { return snapshot; }
    public void setSnapshot(GunSnapshot value) {
        snapshot = value; compactGun=null; setChanged();
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
        compactGun=tag.contains("CompactGun") ? tag.getByteArray("CompactGun") : null;
        CompoundTag original=tag.getCompound("OriginalGun");
        if (original.isEmpty() && compactGun!=null) try { original=NbtIo.readCompressed(new ByteArrayInputStream(compactGun)); }
        catch (IOException | RuntimeException ex) { WallGuns.LOG.warn("Invalid compressed decorative gun at {}",worldPosition,ex); }
        snapshot=GunSnapshot.read(original);
    }
    @Override public net.minecraft.world.phys.AABB getRenderBoundingBox(){return new net.minecraft.world.phys.AABB(worldPosition).inflate(1);}
    @Override public CompoundTag getUpdateTag(){
        CompoundTag tag=new CompoundTag();
        tag.putInt("DisplayRoll",roll);tag.putInt("DisplayMountRoll",mountRoll);tag.putBoolean("DisplayFlipped",flipped);
        if(snapshot!=null){
            if(compactGun==null) try(var bytes=new ByteArrayOutputStream()){
                NbtIo.writeCompressed(snapshot.save(),bytes);compactGun=bytes.toByteArray();
            }catch(IOException ex){WallGuns.LOG.warn("Could not compact decorative gun at {}",worldPosition,ex);tag.put("OriginalGun",snapshot.save());}
            if(compactGun!=null)tag.putByteArray("CompactGun",compactGun);
        }
        return tag;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
}
