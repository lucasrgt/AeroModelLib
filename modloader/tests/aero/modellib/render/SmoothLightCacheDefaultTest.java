package aero.modellib.render;

import org.junit.Test;

import static org.junit.Assert.*;

/** Verifies the qualified default-on policy and its explicit rollback. */
public class SmoothLightCacheDefaultTest {

    @Test
    public void absentSettingEnablesCache() {
        assertTrue(Aero_SmoothLightCache.enabled(null));
    }

    @Test
    public void explicitFalseDisablesCacheCaseInsensitively() {
        assertFalse(Aero_SmoothLightCache.enabled("false"));
        assertFalse(Aero_SmoothLightCache.enabled("FALSE"));
    }

    @Test
    public void otherValuesKeepQualifiedDefault() {
        assertTrue(Aero_SmoothLightCache.enabled("true"));
        assertTrue(Aero_SmoothLightCache.enabled("invalid"));
    }
}
