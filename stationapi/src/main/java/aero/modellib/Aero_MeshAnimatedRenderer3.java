package aero.modellib;

import net.minecraft.client.render.Tessellator;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;

import java.util.IdentityHashMap;

import aero.modellib.animation.Aero_AnimationBundle;
import aero.modellib.animation.Aero_AnimationClip;
import aero.modellib.animation.Aero_AnimationDefinition;
import aero.modellib.animation.Aero_AnimationPlayback;
import aero.modellib.animation.Aero_AnimationPoseResolver;
import aero.modellib.animation.Aero_AnimationStack;
import aero.modellib.animation.graph.Aero_AnimationGraph;
import aero.modellib.model.Aero_MeshBlendMode;
import aero.modellib.model.Aero_MeshModel;
import aero.modellib.render.Aero_AnimationTickLOD;
import aero.modellib.render.Aero_FrustumCull;
import aero.modellib.render.Aero_RenderOptions;
import aero.modellib.skeletal.Aero_BonePageLists;
import aero.modellib.skeletal.Aero_BoneRenderPose;
import aero.modellib.skeletal.Aero_CCDSolver;
import aero.modellib.skeletal.Aero_IkChain;
import aero.modellib.skeletal.Aero_MorphState;
import aero.modellib.skeletal.Aero_MorphTarget;
import aero.modellib.skeletal.Aero_ProceduralPose;
import aero.modellib.util.Aero_Profiler;

/**
 * AeroMesh Renderer (StationAPI/Yarn port). Same algorithm as the ModLoader
 * version, with Yarn-mapped Tessellator + World API.
 *
 * Performance:
 *   - Triangles pre-classified into 4 brightness groups at parse time.
 *   - Tessellator color called 4× per draw (vs N× naive).
 *   - Coordinate division by `sc` replaced with single multiplication.
 *   - Smooth-light path samples each (x,z) world column once per draw and
 *     bilinearly interpolates from the cache (vs 4 lookups per triangle).
 *   - renderAnimated batches GL state changes outside the named-group loop
 *     and iterates a precomputed entry array (no Iterator/Entry alloc).
 *   - Bone/pivot resolution memoized per (clip identity) on the model.
 */
final class Aero_MeshAnimatedRenderer3 extends Aero_MeshRendererState {
    private Aero_MeshAnimatedRenderer3() {}

static void renderAnimated(Aero_MeshModel model,
                                       Aero_AnimationGraph graph,
                                       Aero_AnimationBundle bundle,
                                       double x, double y, double z,
                                       float brightness, float partialTick,
                                       Aero_RenderOptions options) {
        if (graph == null) throw new IllegalArgumentException("graph must not be null");
        if (bundle == null) throw new IllegalArgumentException("bundle must not be null");
        if (Aero_MeshPoseRenderer.prepareAnimatedRender(model, x, y, z, brightness, options) != ANIMATED_RENDER_ACTIVE) return;
        Aero_Profiler.start("aero.mesh.renderAnimated");
        try {
            Aero_MeshModel.NamedGroup[] entries = model.getNamedGroupArray();
            if (Aero_MeshBonePageRenderer.renderGraphViaBonePages(model, graph, bundle, entries, x, y, z,
                    brightness, partialTick, options)) {
                return;
            }
            Tessellator tess = Tessellator.INSTANCE;
            GL11.glPushMatrix();
            try {
                GL11.glTranslated(x, y, z);
                Aero_MeshRenderer.beginMeshState(options);
                try {
                    Aero_MeshGeometryRenderer.drawGroups(tess, model.groups, model.invScale, brightness, options);

                    for (int e = 0; e < entries.length; e++) {
                        Aero_MeshModel.NamedGroup ng = entries[e];
                        String boneName = ng.name;

                        SCRATCH_POSE.reset();
                        bundle.getPivotInto(boneName, SCRATCH_PIVOT);
                        SCRATCH_POSE.setPivot(SCRATCH_PIVOT);
                        graph.samplePose(boneName, partialTick,
                            SCRATCH_ROT, SCRATCH_POS, SCRATCH_SCL);
                        SCRATCH_POSE.rotX = SCRATCH_ROT[0];
                        SCRATCH_POSE.rotY = SCRATCH_ROT[1];
                        SCRATCH_POSE.rotZ = SCRATCH_ROT[2];
                        SCRATCH_POSE.offsetX = SCRATCH_POS[0] / 16f;
                        SCRATCH_POSE.offsetY = SCRATCH_POS[1] / 16f;
                        SCRATCH_POSE.offsetZ = SCRATCH_POS[2] / 16f;
                        SCRATCH_POSE.scaleX = SCRATCH_SCL[0];
                        SCRATCH_POSE.scaleY = SCRATCH_SCL[1];
                        SCRATCH_POSE.scaleZ = SCRATCH_SCL[2];

                        GL11.glPushMatrix();
                        try {
                            Aero_MeshPoseRenderer.applyPose(SCRATCH_POSE);
                            Aero_MeshGeometryRenderer.drawGroups(tess, ng.tris, model.invScale, brightness, options);
                        } finally {
                            GL11.glPopMatrix();
                        }
                    }
                } finally {
                    Aero_MeshRenderer.endMeshState();
                }
            } finally {
                GL11.glPopMatrix();
            }
        } finally {
            Aero_Profiler.end("aero.mesh.renderAnimated");
        }
    }

static void renderAnimated(Aero_MeshModel model,
                                       Aero_AnimationStack stack,
                                       double x, double y, double z,
                                       float brightness, float partialTick,
                                       Aero_RenderOptions options,
                                       Aero_ProceduralPose proceduralPose) {
        if (stack == null) throw new IllegalArgumentException("stack must not be null");
        if (Aero_MeshPoseRenderer.prepareAnimatedRender(model, x, y, z, brightness, options) != ANIMATED_RENDER_ACTIVE) return;
        Aero_Profiler.start("aero.mesh.renderAnimated");
        try {
            Aero_MeshModel.NamedGroup[] entries = model.getNamedGroupArray();
            if (Aero_MeshBonePageRenderer.renderStackViaBonePages(model, stack, entries, x, y, z,
                    brightness, partialTick, options, proceduralPose)) {
                return;
            }

            Tessellator tess = Tessellator.INSTANCE;
            GL11.glPushMatrix();
            try {
                GL11.glTranslated(x, y, z);
                Aero_MeshRenderer.beginMeshState(options);
                try {
                    // Render the static (unnamed) geometry once at the BE origin.
                    Aero_MeshGeometryRenderer.drawGroups(tess, model.groups, model.invScale, brightness, options);

                    for (int e = 0; e < entries.length; e++) {
                        Aero_MeshModel.NamedGroup ng = entries[e];
                        String boneName = ng.name;

                        Aero_AnimationPoseResolver.resolveStack(stack, boneName, partialTick,
                            SCRATCH_PIVOT, SCRATCH_ROT, SCRATCH_POS, SCRATCH_SCL, SCRATCH_POSE);
                        if (proceduralPose != null) proceduralPose.apply(boneName, SCRATCH_POSE);

                        GL11.glPushMatrix();
                        try {
                            Aero_MeshPoseRenderer.applyPose(SCRATCH_POSE);
                            Aero_MeshGeometryRenderer.drawGroups(tess, ng.tris, model.invScale, brightness, options,
                                SCRATCH_POSE.uOffset, SCRATCH_POSE.vOffset,
                                SCRATCH_POSE.uScale,  SCRATCH_POSE.vScale);
                        } finally {
                            GL11.glPopMatrix();
                        }
                    }
                } finally {
                    Aero_MeshRenderer.endMeshState();
                }
            } finally {
                GL11.glPopMatrix();
            }
        } finally {
            Aero_Profiler.end("aero.mesh.renderAnimated");
        }
    }
}
