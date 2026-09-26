package dev.kiyo.wallgun.mixin;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.pojo.model.BedrockModelPOJO;
import com.tacz.guns.resource.manager.LazyJsonDataManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(value=ClientAssetsManager.class, remap=false)
public interface ClientAssetsAccessor {
    @Accessor("bedrockModel") LazyJsonDataManager<BedrockModelPOJO> wallgun$models();
}
