package aero.modellib.model;

import aero.modellib.Aero_AnimationState;


import aero.modellib.animation.Aero_AnimationBundle;
import aero.modellib.animation.Aero_AnimationDefinition;
import aero.modellib.animation.Aero_AnimationPlayback;
import aero.modellib.animation.Aero_AnimationSpec;
import aero.modellib.animation.Aero_AnimationStateRouter;
import aero.modellib.render.Aero_EntityModelTransform;
import aero.modellib.render.Aero_RenderDistanceCulling;
import aero.modellib.render.Aero_RenderLod;
import aero.modellib.render.Aero_RenderOptions;

/**
 * Declarative model contract shared by ModLoader and StationAPI integrations.
 *
 * The lower-level loaders/renderers stay available for custom code, but a
 * spec lets normal integrations keep one static description for the model,
 * texture path, animation wiring, entity transform and render options.
 */
public final class Aero_ModelSpec {

    public enum Kind {
        JSON,
        MESH
    }

    private final Kind kind;
    private final String modelPath;
    private final String texturePath;
    private final Aero_JsonModel jsonModel;
    private final Aero_MeshModel meshModel;
    private final Aero_MeshModel[] meshLodModels;
    private final double[] meshLodDistanceSq;
    private final Aero_AnimationSpec animationSpec;
    private final Aero_EntityModelTransform entityTransform;
    private final Aero_RenderOptions renderOptions;
    private final double animatedDistanceBlocks;

    public static Builder json(String modelPath) {
        return new Builder(new Aero_ModelSpecDraft(Kind.JSON, modelPath, null, null));
    }

    public static Builder json(Aero_JsonModel model) {
        return new Builder(new Aero_ModelSpecDraft(Kind.JSON, null, model, null));
    }

    public static Builder mesh(String modelPath) {
        return new Builder(new Aero_ModelSpecDraft(Kind.MESH, modelPath, null, null));
    }

    public static Builder mesh(Aero_MeshModel model) {
        return new Builder(new Aero_ModelSpecDraft(Kind.MESH, null, null, model));
    }

    private Aero_ModelSpec(Builder builder) {
        Aero_ModelSpecDraft draft = builder.finish();
        this.kind = draft.kind;
        this.modelPath = draft.modelPath;
        this.texturePath = draft.texturePath;
        this.jsonModel = draft.resolveJsonModel();
        this.meshModel = draft.resolveMeshModel();
        this.meshLodModels = draft.resolveMeshLodModels();
        this.meshLodDistanceSq = draft.resolveMeshLodDistanceSq();
        this.animationSpec = draft.animationSpec;
        this.entityTransform = draft.transformBuilder.build();
        this.renderOptions = draft.renderOptions;
        this.animatedDistanceBlocks = draft.animatedDistanceBlocks;

        if (animationSpec != null && kind != Kind.MESH) {
            throw new IllegalStateException("animations are supported only for mesh specs");
        }
    }

    public Kind getKind() {
        return kind;
    }

    public boolean isJson() {
        return kind == Kind.JSON;
    }

    public boolean isMesh() {
        return kind == Kind.MESH;
    }

    public boolean isAnimated() {
        return animationSpec != null;
    }

    public String getModelPath() {
        return modelPath;
    }

    public String getTexturePath() {
        return texturePath;
    }

    public Aero_JsonModel getJsonModel() {
        if (jsonModel == null) throw new IllegalStateException("spec is not a JSON model");
        return jsonModel;
    }

    public Aero_MeshModel getMeshModel() {
        if (meshModel == null) throw new IllegalStateException("spec is not a mesh model");
        return meshModel;
    }

    public Aero_MeshModel getMeshModelForDistanceSq(double distanceSq) {
        Aero_MeshModel selected = getMeshModel();
        if (meshLodModels == null || meshLodModels.length == 0) return selected;
        double selectedDistanceSq = -1.0d;
        for (int i = 0; i < meshLodModels.length; i++) {
            if (distanceSq >= meshLodDistanceSq[i]
                && meshLodDistanceSq[i] >= selectedDistanceSq) {
                selected = meshLodModels[i];
                selectedDistanceSq = meshLodDistanceSq[i];
            }
        }
        return selected;
    }

    public Aero_MeshModel getMeshModelForRelative(double x, double y, double z) {
        return getMeshModelForDistanceSq(x * x + y * y + z * z);
    }

    public boolean hasMeshLods() {
        return meshLodModels != null && meshLodModels.length > 0;
    }

    public Aero_AnimationSpec getAnimationSpec() {
        if (animationSpec == null) throw new IllegalStateException("spec has no animations");
        return animationSpec;
    }

    public Aero_AnimationBundle getAnimationBundle() {
        return getAnimationSpec().getBundle();
    }

    public Aero_AnimationDefinition getAnimationDefinition() {
        return getAnimationSpec().getDefinition();
    }

    public Aero_EntityModelTransform getEntityTransform() {
        return entityTransform;
    }

    public Aero_RenderOptions getRenderOptions() {
        return renderOptions;
    }

    public double getAnimatedDistanceBlocks() {
        return animatedDistanceBlocks;
    }

    public Aero_RenderLod lodRelative(double x, double y, double z, int viewDistance) {
        return Aero_RenderDistanceCulling.lodRelative(
            x, y, z, viewDistance,
            entityTransform.cullingRadius,
            animatedDistanceBlocks,
            entityTransform.maxRenderDistance
        );
    }

    public Aero_AnimationPlayback createPlayback() {
        return getAnimationSpec().createPlayback();
    }

    public Aero_AnimationState createState() {
        return getAnimationSpec().createState();
    }

    /** Creates a state with a custom NBT key prefix. */
    public Aero_AnimationState createState(String nbtKeyPrefix) {
        return getAnimationSpec().createState(nbtKeyPrefix);
    }

    /**
     * Sets {@code playback}'s state honoring the configured default transition.
     * Equivalent to {@link Aero_AnimationSpec#applyState}; provided here so the
     * model spec can be the single declarative entry point for callers.
     */
    public void applyState(Aero_AnimationPlayback playback, int stateId) {
        getAnimationSpec().applyState(playback, stateId);
    }

    /** Routes the playback via {@code router} honoring the spec's defaultTransitionTicks. */
    public void applyState(Aero_AnimationPlayback playback, Aero_AnimationStateRouter router) {
        getAnimationSpec().applyState(playback, router);
    }

    public int getDefaultTransitionTicks() {
        return isAnimated() ? animationSpec.getDefaultTransitionTicks() : 0;
    }

    public static final class Builder {
        private final Aero_ModelSpecDraft draft;
        private Builder(Aero_ModelSpecDraft draft) { this.draft = draft; }
        public Builder texture(String value) { draft.texture(value); return this; }
        public Builder animations(String value) { draft.animations(value); return this; }
        public Builder animations(Aero_AnimationBundle value) { draft.animations(value); return this; }
        public Builder animations(Aero_AnimationSpec value) { draft.animations(value); return this; }
        public Builder state(int state, String clip) { draft.state(state, clip); return this; }
        public Builder definition(Aero_AnimationDefinition value) { draft.definition(value); return this; }
        public Builder defaultTransitionTicks(int value) { draft.defaultTransitionTicks(value); return this; }
        public Builder transform(Aero_EntityModelTransform value) { draft.transform(value); return this; }
        public Builder offset(float x, float y, float z) { draft.offset(x, y, z); return this; }
        public Builder scale(float value) { draft.scale(value); return this; }
        public Builder yawOffset(float value) { draft.yawOffset(value); return this; }
        public Builder cullingRadius(float value) { draft.cullingRadius(value); return this; }
        public Builder maxRenderDistance(float value) { draft.maxRenderDistance(value); return this; }
        public Builder animatedDistance(double value) { draft.animatedDistance(value); return this; }
        public Builder meshLod(String path, double distance) { draft.meshLod(path, distance); return this; }
        public Builder meshLod(Aero_MeshModel model, double distance) { draft.meshLod(model, distance); return this; }
        public Builder renderOptions(Aero_RenderOptions value) { draft.renderOptions(value); return this; }
        public Builder tint(float r, float g, float b) { return renderOptions(Aero_RenderOptions.tint(r, g, b)); }
        public Aero_ModelSpec build() { return new Aero_ModelSpec(this); }
        private Aero_ModelSpecDraft finish() { draft.finishAnimation(); return draft; }
    }
}
