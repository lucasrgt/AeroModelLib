package aero.modellib.model;

import java.util.HashMap;
import java.util.Map;

import aero.modellib.skeletal.Aero_MorphTarget;

/** Lazily allocated morph-target registry for immutable mesh topology. */
final class Aero_MorphTargetRegistry {
    private Map targets;

    void attach(Aero_MorphTarget target) {
        if (target == null) throw new IllegalArgumentException("morph target must not be null");
        if (targets == null) targets = new HashMap();
        targets.put(target.name, target);
    }

    Aero_MorphTarget get(String name) {
        return targets == null ? null : (Aero_MorphTarget) targets.get(name);
    }

    boolean hasTargets() { return targets != null && !targets.isEmpty(); }
}
