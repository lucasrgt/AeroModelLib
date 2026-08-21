package aero.modellib.worldline;

import aero.modellib.model.Aero_MeshBlendMode;
import aero.modellib.render.Aero_RenderOptions;
import aero.modellib.skeletal.Aero_Quaternion;
import worldline.test.WorldlineSpec;
import static worldline.test.Expect.expect;
import static worldline.test.Worldline.describe;
import static worldline.test.Worldline.test;

/** External TestKit consumer compiled against the packaged Java 8 API. */
public final class AeroModelLibWorldlineTest extends WorldlineSpec {
    @Override protected void define() {
        describe("AeroModelLib math and rendering", () -> {
            test("creates a unit quaternion", context -> {
                float[] value = new float[4]; Aero_Quaternion.fromEulerDegrees(30F, -45F, 60F, value);
                double norm = value[0] * value[0] + value[1] * value[1]
                        + value[2] * value[2] + value[3] * value[3];
                expect(Math.round(norm * 10_000D)).toEqual(10_000L);
            });
            test("uses alpha blending for translucent models", context ->
                    expect(Aero_RenderOptions.translucent(0.4F).blend)
                            .toEqual(Aero_MeshBlendMode.ALPHA));
            test("round-trips render options", context -> {
                Aero_RenderOptions source = Aero_RenderOptions.builder().tint(0.2F, 0.3F, 0.4F)
                        .alpha(0.5F).depthTest(false).build();
                Aero_RenderOptions copy = source.toBuilder().build();
                expect(copy.alpha).toEqual(source.alpha); expect(copy.depthTest).toBeFalse();
            });
        });
    }
}
