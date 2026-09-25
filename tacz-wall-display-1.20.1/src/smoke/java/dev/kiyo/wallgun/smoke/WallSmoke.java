package dev.kiyo.wallgun.smoke;

import net.minecraftforge.fml.common.Mod;

/** Dispatches the current placement smoke and optional focused render checks. */
@Mod("wall_display_smoke")
public class WallSmoke {
    public WallSmoke() {
        if (Boolean.getBoolean("wallgun.lodSmoke")) new LodSmoke();
        else if (Boolean.getBoolean("wallgun.warmupSmoke")) new WarmupSmoke();
        else if (Boolean.getBoolean("wallgun.audit")) new CatalogAudit();
        else new PlacementSmoke();
    }
}
