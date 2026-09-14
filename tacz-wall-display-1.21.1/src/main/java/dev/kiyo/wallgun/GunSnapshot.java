package dev.kiyo.wallgun;

import com.mojang.serialization.Codec;
import com.tacz.guns.api.item.IGun;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/** Immutable identity and ownership snapshot. Never expose its mutable ItemStack. */
public final class GunSnapshot {
    public static final Codec<GunSnapshot> CODEC = ItemStack.SINGLE_ITEM_CODEC.xmap(GunSnapshot::new, GunSnapshot::copyGun);
    public static final StreamCodec<RegistryFriendlyByteBuf, GunSnapshot> STREAM_CODEC = ItemStack.STREAM_CODEC.map(GunSnapshot::new, GunSnapshot::copyGun);
    private final ItemStack gun;
    private final int hash;
    public GunSnapshot(ItemStack gun) {
        if (gun.isEmpty() || IGun.getIGunOrNull(gun) == null) throw new IllegalArgumentException("Snapshot requires a TACZ gun");
        this.gun = gun.copyWithCount(1);
        this.hash = ItemStack.hashItemAndComponents(this.gun);
    }
    public ItemStack copyGun() { return gun.copy(); }
    @Override public int hashCode() { return hash; }
    @Override public boolean equals(Object other) {
        return this == other || other instanceof GunSnapshot snapshot && hash == snapshot.hash && ItemStack.isSameItemSameComponents(gun, snapshot.gun);
    }
}
