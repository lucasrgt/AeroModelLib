package aero.modellib.mixin;

import java.util.List;

import aero.modellib.Aero_ChunkCompileBudget;
import aero.modellib.Aero_FrameSpikeLogger;
import aero.modellib.optimization.OptimizationRef;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.entity.LivingEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds bounded speculative work after vanilla's dirty-chunk scheduler. */
@Mixin(WorldRenderer.class)
@OptimizationRef({"aero.chunk.compile-budget"})
public abstract class WorldRendererChunkSchedulerMixin {
    @Shadow private List<ChunkBuilder> dirtyChunks;

    @Inject(method = "setWorld(Lnet/minecraft/world/World;)V", at = @At("HEAD"))
    private void aeroModelLib_releaseChunkWork(World world, CallbackInfo callback) {
        Aero_ChunkCompileBudget.reset();
    }

    @Inject(method = "compileChunks(Lnet/minecraft/entity/LivingEntity;Z)Z",
        at = @At("HEAD"), require = 0, expect = 0)
    private void aeroModelLib_beginChunkWork(LivingEntity camera, boolean forced,
            CallbackInfoReturnable<Boolean> callback) {
        Aero_FrameSpikeLogger.beginChunkCompile();
    }

    @Inject(method = "compileChunks(Lnet/minecraft/entity/LivingEntity;Z)Z",
        at = @At("RETURN"), require = 0, expect = 0)
    private void aeroModelLib_addSpeculativeChunkWork(LivingEntity camera, boolean forced,
            CallbackInfoReturnable<Boolean> callback) {
        try {
            if (Aero_ChunkCompileBudget.handles(forced))
                Aero_ChunkCompileBudget.schedule(dirtyChunks, camera);
        } finally {
            Aero_FrameSpikeLogger.endChunkCompile();
        }
    }
}
