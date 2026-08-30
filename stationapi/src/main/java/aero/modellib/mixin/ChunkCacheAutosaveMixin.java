package aero.modellib.mixin;

import aero.modellib.Aero_IncrementalAutosave;
import aero.modellib.Aero_IncrementalAutosaveCursor;
import aero.modellib.optimization.OptimizationRef;
import java.util.List;
import net.minecraft.client.gui.screen.LoadingDisplay;
import net.minecraft.world.chunk.ChunkCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies Aero's opt-in limit to vanilla's non-forced chunk-save batch. */
@Mixin(ChunkCache.class)
@OptimizationRef({"aero.world.incremental-autosave"})
public abstract class ChunkCacheAutosaveMixin {
    @Unique
    private final Aero_IncrementalAutosaveCursor aero$autosaveCursor =
        new Aero_IncrementalAutosaveCursor();

    @Inject(
        method = "save(ZLnet/minecraft/client/gui/screen/LoadingDisplay;)Z",
        at = @At("HEAD")
    )
    private void aero$beginAutosave(boolean force, LoadingDisplay display,
                                    CallbackInfoReturnable<Boolean> callback) {
        aero$autosaveCursor.begin();
    }

    @Redirect(
        method = "save(ZLnet/minecraft/client/gui/screen/LoadingDisplay;)Z",
        at = @At(value = "INVOKE", target = "Ljava/util/List;get(I)Ljava/lang/Object;")
    )
    private Object aero$visitAutosaveChunk(List<?> chunks, int vanillaIndex,
                                           boolean force, LoadingDisplay display) {
        int index = aero$autosaveCursor.visit(vanillaIndex, chunks.size(), force);
        return chunks.get(index);
    }

    @ModifyConstant(
        method = "save(ZLnet/minecraft/client/gui/screen/LoadingDisplay;)Z",
        constant = @Constant(intValue = 24)
    )
    private int aero$autosaveChunkLimit(int vanillaLimit, boolean force) {
        return Aero_IncrementalAutosave.chunkLimit(vanillaLimit, force);
    }

    @Inject(
        method = "save(ZLnet/minecraft/client/gui/screen/LoadingDisplay;)Z",
        at = @At("RETURN")
    )
    private void aero$finishAutosave(boolean force, LoadingDisplay display,
                                     CallbackInfoReturnable<Boolean> callback) {
        aero$autosaveCursor.end(force);
    }
}
