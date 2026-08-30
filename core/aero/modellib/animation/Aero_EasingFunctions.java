package aero.modellib.animation;

/** Groups easing families so each dispatch unit remains locally readable. */
final class Aero_EasingFunctions {
    private Aero_EasingFunctions() {}

    static float apply(Aero_Easing easing, float t) {
        int ordinal = easing.ordinal();
        if (ordinal <= Aero_Easing.STEP.ordinal()) return t;
        if (ordinal <= Aero_Easing.EASE_IN_OUT_QUAD.ordinal()) return sineAndQuad(easing, t);
        if (ordinal <= Aero_Easing.EASE_IN_OUT_QUINT.ordinal()) return polynomial(easing, t);
        if (ordinal <= Aero_Easing.EASE_IN_OUT_CIRC.ordinal()) return exponentialAndCircular(easing, t);
        return character(easing, t);
    }

    private static float sineAndQuad(Aero_Easing easing, float t) {
        switch (easing) {
            case EASE_IN_SINE:     return Aero_Easing.easeInSine(t);
            case EASE_OUT_SINE:    return Aero_Easing.easeOutSine(t);
            case EASE_IN_OUT_SINE: return Aero_Easing.easeInOutSine(t);
            case EASE_IN_QUAD:     return t * t;
            case EASE_OUT_QUAD:    return Aero_Easing.easeOutQuad(t);
            default:               return Aero_Easing.easeInOutQuad(t);
        }
    }

    private static float polynomial(Aero_Easing easing, float t) {
        switch (easing) {
            case EASE_IN_CUBIC:     return t * t * t;
            case EASE_OUT_CUBIC:    return Aero_Easing.easeOutCubic(t);
            case EASE_IN_OUT_CUBIC: return Aero_Easing.easeInOutCubic(t);
            case EASE_IN_QUART:     return t * t * t * t;
            case EASE_OUT_QUART:    return Aero_Easing.easeOutQuart(t);
            case EASE_IN_OUT_QUART: return Aero_Easing.easeInOutQuart(t);
            case EASE_IN_QUINT:     return t * t * t * t * t;
            case EASE_OUT_QUINT:    return Aero_Easing.easeOutQuint(t);
            default:                return Aero_Easing.easeInOutQuint(t);
        }
    }

    private static float exponentialAndCircular(Aero_Easing easing, float t) {
        switch (easing) {
            case EASE_IN_EXPO:
                return t == 0f ? 0f : (float) Math.pow(2.0, 10.0 * t - 10.0);
            case EASE_OUT_EXPO:
                return t == 1f ? 1f : 1f - (float) Math.pow(2.0, -10.0 * t);
            case EASE_IN_OUT_EXPO: return Aero_Easing.easeInOutExpo(t);
            case EASE_IN_CIRC: return 1f - (float) Math.sqrt(1.0 - t * t);
            case EASE_OUT_CIRC:
                float u = t - 1f;
                return (float) Math.sqrt(1.0 - u * u);
            default: return Aero_Easing.easeInOutCirc(t);
        }
    }

    private static float character(Aero_Easing easing, float t) {
        switch (easing) {
            case EASE_IN_BACK:        return Aero_Easing.easeInBack(t);
            case EASE_OUT_BACK:       return Aero_Easing.easeOutBack(t);
            case EASE_IN_OUT_BACK:    return Aero_Easing.easeInOutBack(t);
            case EASE_IN_ELASTIC:     return Aero_Easing.easeInElastic(t);
            case EASE_OUT_ELASTIC:    return Aero_Easing.easeOutElastic(t);
            case EASE_IN_OUT_ELASTIC: return Aero_Easing.easeInOutElastic(t);
            case EASE_IN_BOUNCE:      return 1f - Aero_Easing.bounceOut(1f - t);
            case EASE_OUT_BOUNCE:     return Aero_Easing.bounceOut(t);
            default:                  return Aero_Easing.easeInOutBounce(t);
        }
    }
}
