package dev.kiyo.wallgun.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read the shared attachment flags so a snapshot cannot change a later live render. */
@Pseudo
@Mixin(targets = "com.example.taczmeshloader.core.PolyMeshModel", remap = false)
public interface PolyMeshStateAccessor {
    @Accessor("ocularBonesHidden") boolean wallgun$ocularHidden();
    @Accessor("ocularRingBonesHidden") boolean wallgun$ocularRingHidden();
}
