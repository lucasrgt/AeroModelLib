package aero.modellib;

import aero.modellib.optimization.OptimizationRef;
import aero.modellib.util.Aero_SoundCoalesce;
import net.minecraft.world.World;

/**
 * Reusable synchronous adapter between sound coalescing and the active world.
 *
 * <p>The world reference is held only while the render-thread flush runs. The
 * adapter lives outside the mixin package because Mixin forbids transformed
 * classes from directly referencing helper classes declared inside it.
 */
@OptimizationRef({"aero.audio.sound-coalescing"})
public final class Aero_SoundDispatcher implements Aero_SoundCoalesce.Dispatcher {
    private World world;

    public void setWorld(World world) {
        this.world = world;
    }

    public void clearWorld() {
        world = null;
    }

    @Override
    public void play(double x, double y, double z, String name, float volume, float pitch) {
        World current = world;
        if (current != null) current.playSound(x, y, z, name, volume, pitch);
    }
}
