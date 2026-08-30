package aero.modellib.mixin;

import java.util.List;

import aero.modellib.Aero_ChunkCompileBudget;
import aero.modellib.Aero_FrameSpikeLogger;
import aero.modellib.optimization.OptimizationRef;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Owns the optional replacement of vanilla's dirty-chunk scheduler. */
@Mixin(WorldRenderer.class)
@OptimizationRef({"aero.chunk.compile-budget"})
public abstract class WorldRendererChunkSchedulerMixin {
    @Shadow private List<ChunkBuilder> dirtyChunks;

    @Inject(method = "compileChunks(Lnet/minecraft/entity/LivingEntity;Z)Z",
        at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
    private void aeroModelLib_scheduleChunkWork(LivingEntity camera, boolean forced,
            CallbackInfoReturnable<Boolean> callback) {
        Aero_FrameSpikeLogger.beginChunkCompile();
        if (!Aero_ChunkCompileBudget.handles(forced)) return;
        try {
            callback.setReturnValue(Boolean.valueOf(
                Aero_ChunkCompileBudget.schedule(dirtyChunks, camera)));
        } finally {
            Aero_FrameSpikeLogger.endChunkCompile();
        }
    }

    @Inject(method = "compileChunks(Lnet/minecraft/entity/LivingEntity;Z)Z",
        at = @At("TAIL"), require = 0, expect = 0)
    private void aeroModelLib_endVanillaChunkWork(LivingEntity camera, boolean forced,
            CallbackInfoReturnable<Boolean> callback) {
        if (!Aero_ChunkCompileBudget.handles(forced))
            Aero_FrameSpikeLogger.endChunkCompile();
    }
}
