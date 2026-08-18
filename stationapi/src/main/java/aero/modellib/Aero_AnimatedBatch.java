package aero.modellib;

import java.util.Arrays;

import aero.modellib.animation.Aero_AnimationBundle;
import aero.modellib.animation.Aero_AnimationDefinition;
import aero.modellib.animation.Aero_AnimationPlayback;
import aero.modellib.model.Aero_MeshModel;
import aero.modellib.render.Aero_RenderOptions;

/** Reusable structure-of-arrays storage for one compatible animated batch. */
class Aero_AnimatedBatch {
    private static final int INITIAL_CAPACITY = 256;
    final Aero_AnimatedBatchKey key;
    final Aero_MeshModel model;
    final String texturePath;
    Aero_AnimationBundle[] bundles = new Aero_AnimationBundle[INITIAL_CAPACITY];
    Aero_AnimationDefinition[] defs = new Aero_AnimationDefinition[INITIAL_CAPACITY];
    Aero_AnimationPlayback[] states = new Aero_AnimationPlayback[INITIAL_CAPACITY];
    double[] xs = new double[INITIAL_CAPACITY], ys = new double[INITIAL_CAPACITY];
    double[] zs = new double[INITIAL_CAPACITY];
    float[] brightnesses = new float[INITIAL_CAPACITY], partialTicks = new float[INITIAL_CAPACITY];
    Aero_RenderOptions[] options = new Aero_RenderOptions[INITIAL_CAPACITY];
    int count;

    Aero_AnimatedBatch(Aero_AnimatedBatchKey key) {
        this.key = key;
        model = key.model;
        texturePath = key.texturePath;
    }

    void add(Aero_AnimationBundle bundle, Aero_AnimationDefinition definition,
            Aero_AnimationPlayback state, double x, double y, double z,
            float brightness, float partialTick, Aero_RenderOptions renderOptions) {
        ensureCapacity();
        bundles[count] = bundle; defs[count] = definition; states[count] = state;
        xs[count] = x; ys[count] = y; zs[count] = z;
        brightnesses[count] = brightness; partialTicks[count] = partialTick;
        options[count] = renderOptions;
        count++;
    }

    void clear() {
        for (int index = 0; index < count; index++) {
            bundles[index] = null; defs[index] = null; states[index] = null; options[index] = null;
        }
        count = 0;
    }

    private void ensureCapacity() {
        if (count < bundles.length) return;
        int size = bundles.length * 2;
        bundles = Arrays.copyOf(bundles, size); defs = Arrays.copyOf(defs, size);
        states = Arrays.copyOf(states, size); options = Arrays.copyOf(options, size);
        xs = Arrays.copyOf(xs, size); ys = Arrays.copyOf(ys, size); zs = Arrays.copyOf(zs, size);
        brightnesses = Arrays.copyOf(brightnesses, size);
        partialTicks = Arrays.copyOf(partialTicks, size);
    }
}
