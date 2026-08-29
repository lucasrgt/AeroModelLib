package aero.modellib.test.mixin;

import aero.modellib.test.AeroUltraStressState;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Drives the automated fresh-world ULTRA lifecycle from the client tick. */
@Mixin(Minecraft.class)
public abstract class UltraStressMinecraftMixin {
    @Inject(method = "tick()V", at = @At("HEAD"))
    private void aeroTest_driveUltraStress(CallbackInfo ci) {
        AeroUltraStressState.drive((Minecraft) (Object) this);
    }
}
