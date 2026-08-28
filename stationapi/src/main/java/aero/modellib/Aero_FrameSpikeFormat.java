package aero.modellib;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import aero.modellib.render.Aero_AnimationRenderBudget;
import aero.modellib.render.Aero_AnimationTickBudget;
import aero.modellib.render.Aero_FrustumCull;
import aero.modellib.render.Aero_RenderLoadGovernor;

/**
 * Opt-in frame spike logger for dense BE scenes. It samples once per render
 * frame and prints a compact diagnostic line when the previous frame exceeded
 * the configured threshold.
 */
final class Aero_FrameSpikeFormat extends Aero_FrameSpikeState {
    private Aero_FrameSpikeFormat() {}

static String round1(double value) {
        return String.valueOf(Math.round(value * 10.0d) / 10.0d);
    }

static String round2(double value) {
        return String.valueOf(Math.round(value * 100.0d) / 100.0d);
    }

static double bytesToMb(long bytes) {
        return bytes >= 0L ? bytes / 1048576.0d : -1.0d;
    }

static double doubleProperty(String name, double fallback,
                                         double min, double max) {
        String raw = System.getProperty(name);
        if (raw == null) return fallback;
        try {
            double value = Double.parseDouble(raw.trim());
            if (Double.isNaN(value) || Double.isInfinite(value)) return fallback;
            if (value < min) return min;
            if (value > max) return max;
            return value;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
