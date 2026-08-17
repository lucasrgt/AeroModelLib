package aero.modellib;

import org.junit.BeforeClass;
import org.junit.Test;

import aero.modellib.render.Aero_AnimationRenderBudget;
import aero.modellib.render.Aero_RenderLod;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AnimationRenderBudgetTest {

    @BeforeClass
    public static void configureFiniteBudgetPolicy() {
        // Production defaults to unlimited animation admission. These tests
        // exercise the finite-cap priority, reserve, hysteresis, and cache
        // branches explicitly, so configure that policy before class init.
        System.setProperty("aero.maxAnimatedBE", "128");
        System.setProperty("aero.animBudget.hardCap", "false");
        assertEquals(128, Aero_AnimationRenderBudget.MAX_ANIMATED);
    }

    @Test
    public void lowPriorityObjectsStopConsumingBudgetEarly() {
        Aero_AnimationRenderBudget.updateFromDisplayHeight(1080);
        Aero_AnimationRenderBudget.beginFrame();

        for (int i = 0; i < 64; i++) {
            assertEquals(Aero_RenderLod.ANIMATED,
                Aero_AnimationRenderBudget.apply(Aero_RenderLod.ANIMATED,
                    80d, 0d, 0d, 2d));
        }

        assertEquals(Aero_RenderLod.STATIC,
            Aero_AnimationRenderBudget.apply(Aero_RenderLod.ANIMATED,
                200d, 0d, 0d, 0.5d));
        assertEquals(1, Aero_AnimationRenderBudget.priorityRejectedThisFrame());
    }

    @Test
    public void criticalNearObjectsCanUseReserveAfterNormalBudgetIsFull() {
        Aero_AnimationRenderBudget.updateFromDisplayHeight(1080);
        Aero_AnimationRenderBudget.beginFrame();

        for (int i = 0; i < Aero_AnimationRenderBudget.MAX_ANIMATED; i++) {
            assertEquals(Aero_RenderLod.ANIMATED,
                Aero_AnimationRenderBudget.apply(Aero_RenderLod.ANIMATED,
                    80d, 0d, 0d, 2d));
        }

        assertEquals(Aero_RenderLod.ANIMATED,
            Aero_AnimationRenderBudget.apply(Aero_RenderLod.ANIMATED,
                4d, 0d, 0d, 2d));
        assertTrue(Aero_AnimationRenderBudget.criticalAcceptedThisFrame() > 0);
    }

    @Test
    public void recentlyAnimatedObjectsGetShortHysteresisReserve() {
        Object key = new Object();
        Aero_AnimationRenderBudget.updateFromDisplayHeight(1080);
        Aero_AnimationRenderBudget.beginFrame();

        assertEquals(Aero_RenderLod.ANIMATED,
            Aero_AnimationRenderBudget.apply(Aero_RenderLod.ANIMATED,
                80d, 0d, 0d, 2d, key));

        Aero_AnimationRenderBudget.beginFrame();
        for (int i = 0; i < Aero_AnimationRenderBudget.MAX_ANIMATED; i++) {
            assertEquals(Aero_RenderLod.ANIMATED,
                Aero_AnimationRenderBudget.apply(Aero_RenderLod.ANIMATED,
                    80d, 0d, 0d, 2d));
        }

        assertEquals(Aero_RenderLod.ANIMATED,
            Aero_AnimationRenderBudget.apply(Aero_RenderLod.ANIMATED,
                80d, 0d, 0d, 2d, key));
        assertEquals(1, Aero_AnimationRenderBudget.hysteresisAcceptedThisFrame());
    }

    @Test
    public void keyedDecisionIsReusedWithinFrame() {
        Object key = new Object();
        Aero_AnimationRenderBudget.updateFromDisplayHeight(1080);
        Aero_AnimationRenderBudget.beginFrame();

        assertEquals(Aero_RenderLod.ANIMATED,
            Aero_AnimationRenderBudget.apply(Aero_RenderLod.ANIMATED,
                4d, 0d, 0d, 2d, key));
        assertEquals(Aero_RenderLod.ANIMATED,
            Aero_AnimationRenderBudget.apply(Aero_RenderLod.ANIMATED,
                4d, 0d, 0d, 2d, key));

        assertEquals(1, Aero_AnimationRenderBudget.acceptedThisFrame());
    }

    @Test
    public void primitiveKeyedDecisionIsReusedWithinFrame() {
        long key = 0x1234ABCDL;
        Aero_AnimationRenderBudget.updateFromDisplayHeight(1080);
        Aero_AnimationRenderBudget.beginFrame();

        assertEquals(Aero_RenderLod.ANIMATED,
            Aero_AnimationRenderBudget.apply(Aero_RenderLod.ANIMATED,
                4d, 0d, 0d, 2d, key));
        assertEquals(Aero_RenderLod.ANIMATED,
            Aero_AnimationRenderBudget.apply(Aero_RenderLod.ANIMATED,
                4d, 0d, 0d, 2d, key));

        assertEquals(1, Aero_AnimationRenderBudget.acceptedThisFrame());
    }
}
