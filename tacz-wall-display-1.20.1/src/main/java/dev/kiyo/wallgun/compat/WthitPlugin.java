package dev.kiyo.wallgun.compat;

import dev.kiyo.wallgun.WallGunBlock;
import dev.kiyo.wallgun.WallGunEntity;
import mcp.mobius.waila.api.*;

/** Loaded only by WTHIT's client plugin loader; WTHIT remains optional. */
public final class WthitPlugin implements IWailaClientPlugin, IBlockComponentProvider {
    @Override
    public void register(IClientRegistrar registrar) {
        registrar.head(this, WallGunBlock.class);
    }

    @Override
    public void appendHead(ITooltip tooltip, IBlockAccessor accessor, IPluginConfig config) {
        if (!(accessor.getBlockEntity() instanceof WallGunEntity entity) || entity.snapshot() == null) return;
        var gun = entity.snapshot().copyGun();
        // Replace WTHIT's tagged title, preserving its formatter, icon and attribution.
        tooltip.setLine(WailaConstants.OBJECT_NAME_TAG,
                IWailaConfig.get().getFormatter().blockName(gun.getItem().getName(gun)));
    }
}
