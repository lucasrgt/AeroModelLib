package aero.modellib.mixin;

import aero.modellib.Aero_IncrementalAutosave;
import aero.modellib.optimization.OptimizationRef;
import net.minecraft.world.chunk.ChunkCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Applies Aero's opt-in limit to vanilla's non-forced chunk-save batch. */
@Mixin(ChunkCache.class)
@OptimizationRef({"aero.world.incremental-autosave"})
public abstract class ChunkCacheAutosaveMixin {
    @ModifyConstant(
        method = "save(ZLnet/minecraft/client/gui/screen/LoadingDisplay;)Z",
        constant = @Constant(intValue = 24)
    )
    private int aero$autosaveChunkLimit(int vanillaLimit, boolean force) {
        return Aero_IncrementalAutosave.chunkLimit(vanillaLimit, force);
    }
}
