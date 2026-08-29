package aero.modellib.test.mixin;

import aero.modellib.test.AeroUltraStressCensus;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reads the just-completed frame before Aero resets its frame counters. */
@Mixin(value = GameRenderer.class, priority = 900)
public abstract class UltraStressFrameMixin {
    @Inject(method = "onFrameUpdate(F)V", at = @At("HEAD"))
    private void aeroTest_captureUltraFrame(float partialTick, CallbackInfo ci) {
        AeroUltraStressCensus.beforeFrame();
    }
}
