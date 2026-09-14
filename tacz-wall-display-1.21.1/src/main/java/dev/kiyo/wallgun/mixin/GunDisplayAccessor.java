package dev.kiyo.wallgun.mixin;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.pojo.display.gun.GunDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(value = GunDisplayInstance.class, remap = false)
public interface GunDisplayAccessor {
    @Accessor("displayId") net.minecraft.resources.ResourceLocation wallgun$displayId();
    @Accessor("display") GunDisplay wallgun$display();
}
