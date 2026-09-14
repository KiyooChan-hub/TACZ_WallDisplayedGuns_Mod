package dev.kiyo.wallgun.smoke;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.List;
public final class ScopeFixtures {
    public static final String[] SCOPES={"tacz:sight_exp3","mk16:553_g43","tacz:scope_elcan_4x"};
    public static ItemStack gun(HolderLookup.Provider lookup, String scope) {
        var gun=GunItemBuilder.create().setId(ResourceLocation.parse("suffuse:n4")).setAmmoCount(30).setAmmoInBarrel(true)
            .setFireMode(com.tacz.guns.api.item.gun.FireMode.SEMI).build(lookup);
        for(String id:List.of(scope,"nmw2:muzzle_silencer_fss_covert_v_ug","suffuse:grip_td","tacz:stock_ripstock")) {
            var attachment=AttachmentItemBuilder.create().setId(ResourceLocation.parse(id)).build();
            IGun.getIGunOrNull(gun).installAttachment(lookup,gun,attachment);
        }
        return gun;
    }
}
