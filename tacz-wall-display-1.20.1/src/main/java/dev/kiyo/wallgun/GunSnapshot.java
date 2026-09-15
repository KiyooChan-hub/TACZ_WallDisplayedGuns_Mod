package dev.kiyo.wallgun;
import com.tacz.guns.api.item.IGun;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
/** Immutable complete Forge stack serialization, including serializable capabilities. */
public final class GunSnapshot {
    private final CompoundTag data;
    private final int hash;
    public GunSnapshot(ItemStack gun) {
        if(gun.isEmpty() || IGun.getIGunOrNull(gun)==null)throw new IllegalArgumentException("Snapshot requires a TACZ gun");
        ItemStack copy=gun.copy();copy.setCount(1);data=copy.serializeNBT();hash=data.hashCode();
    }
    public ItemStack copyGun() { return ItemStack.of(data.copy()); }
    public CompoundTag save() { return data.copy(); }
    public static GunSnapshot read(CompoundTag data) {
        var stack=ItemStack.of(data);
        return stack.isEmpty() || IGun.getIGunOrNull(stack)==null ? null : new GunSnapshot(stack);
    }
    @Override public int hashCode(){return hash;}
    @Override public boolean equals(Object other){return this==other || other instanceof GunSnapshot snapshot && hash==snapshot.hash && data.equals(snapshot.data);}
}
