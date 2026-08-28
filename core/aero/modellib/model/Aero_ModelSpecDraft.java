package aero.modellib.model;

import java.util.ArrayList;

import aero.modellib.animation.Aero_AnimationBundle;
import aero.modellib.animation.Aero_AnimationDefinition;
import aero.modellib.animation.Aero_AnimationSpec;
import aero.modellib.render.Aero_EntityModelTransform;
import aero.modellib.render.Aero_RenderDistanceCulling;
import aero.modellib.render.Aero_RenderOptions;

/** Mutable construction state kept outside the public immutable model contract. */
final class Aero_ModelSpecDraft {
    final Aero_ModelSpec.Kind kind;
    final String modelPath;
    final Aero_JsonModel jsonModel;
    final Aero_MeshModel meshModel;
    ArrayList meshLodPaths;
    ArrayList meshLodModels;
    ArrayList meshLodDistances;
    String texturePath;
    Aero_AnimationSpec animationSpec;
    Aero_AnimationSpec.Builder animationBuilder;
    Aero_EntityModelTransform.Builder transformBuilder = Aero_EntityModelTransform.builder();
    Aero_RenderOptions renderOptions = Aero_RenderOptions.DEFAULT;
    double animatedDistanceBlocks = Aero_RenderDistanceCulling.DEFAULT_SPECIAL_RENDER_RADIUS;

    Aero_ModelSpecDraft(Aero_ModelSpec.Kind kind, String path,
            Aero_JsonModel jsonModel, Aero_MeshModel meshModel) {
        if (kind == null) throw new IllegalArgumentException("kind must not be null");
        if (path == null && jsonModel == null && meshModel == null)
            throw new IllegalArgumentException("modelPath or model must be provided");
        this.kind = kind;
        this.modelPath = path;
        this.jsonModel = jsonModel;
        this.meshModel = meshModel;
    }

    void texture(String value) { texturePath = value; }
    void animations(String value) { animationSpec = null; animationBuilder = Aero_AnimationSpec.builder(value); }
    void animations(Aero_AnimationBundle value) {
        animationSpec = null;
        animationBuilder = Aero_AnimationSpec.builder(value);
    }

    void animations(Aero_AnimationSpec value) {
        if (value == null) throw new IllegalArgumentException("animationSpec must not be null");
        animationSpec = value;
        animationBuilder = null;
    }

    void state(int state, String clip) {
        requireAnimationBuilder("state()", true);
        animationBuilder.state(state, clip);
    }

    void definition(Aero_AnimationDefinition value) {
        requireAnimationBuilder("definition()", false);
        animationBuilder.definition(value);
    }

    void defaultTransitionTicks(int value) {
        requireAnimationBuilder("defaultTransitionTicks()", false);
        animationBuilder.defaultTransitionTicks(value);
    }

    void transform(Aero_EntityModelTransform value) {
        if (value == null) throw new IllegalArgumentException("transform must not be null");
        transformBuilder = value.toBuilder();
    }

    void offset(float x, float y, float z) { transformBuilder.offset(x, y, z); }
    void scale(float value) { transformBuilder.scale(value); }
    void yawOffset(float value) { transformBuilder.yawOffset(value); }
    void cullingRadius(float value) { transformBuilder.cullingRadius(value); }
    void maxRenderDistance(float value) { transformBuilder.maxRenderDistance(value); }

    void animatedDistance(double value) {
        requireNonNegativeFinite("animatedDistanceBlocks", value);
        animatedDistanceBlocks = value;
    }

    void meshLod(String path, double distance) {
        if (path == null) throw new IllegalArgumentException("modelPath must not be null");
        requireMeshLod(distance);
        meshLodPaths.add(path);
        meshLodModels.add(null);
        meshLodDistances.add(Double.valueOf(distance));
    }

    void meshLod(Aero_MeshModel model, double distance) {
        if (model == null) throw new IllegalArgumentException("model must not be null");
        requireMeshLod(distance);
        meshLodPaths.add(null);
        meshLodModels.add(model);
        meshLodDistances.add(Double.valueOf(distance));
    }

    void renderOptions(Aero_RenderOptions value) {
        if (value == null) throw new IllegalArgumentException("renderOptions must not be null");
        renderOptions = value;
    }

    void finishAnimation() {
        if (animationBuilder == null) return;
        animationSpec = animationBuilder.build();
        animationBuilder = null;
    }

    Aero_JsonModel resolveJsonModel() {
        if (kind != Aero_ModelSpec.Kind.JSON) return null;
        return jsonModel != null ? jsonModel : Aero_JsonModelLoader.load(modelPath);
    }

    Aero_MeshModel resolveMeshModel() {
        if (kind != Aero_ModelSpec.Kind.MESH) return null;
        return meshModel != null ? meshModel : loadMesh(modelPath);
    }

    Aero_MeshModel[] resolveMeshLodModels() {
        if (!hasMeshLods()) return null;
        Aero_MeshModel[] result = new Aero_MeshModel[meshLodDistances.size()];
        for (int index = 0; index < result.length; index++) {
            Aero_MeshModel model = (Aero_MeshModel) meshLodModels.get(index);
            String path = (String) meshLodPaths.get(index);
            result[index] = model != null ? model : loadMesh(path);
        }
        return result;
    }

    double[] resolveMeshLodDistanceSq() {
        if (!hasMeshLods()) return null;
        double[] result = new double[meshLodDistances.size()];
        for (int index = 0; index < result.length; index++) {
            double distance = ((Double) meshLodDistances.get(index)).doubleValue();
            result[index] = distance * distance;
        }
        return result;
    }

    private boolean hasMeshLods() {
        return kind == Aero_ModelSpec.Kind.MESH
            && meshLodDistances != null && !meshLodDistances.isEmpty();
    }

    private void requireAnimationBuilder(String operation, boolean distinguishSpec) {
        if (animationBuilder != null) return;
        if (distinguishSpec && animationSpec != null)
            throw new IllegalStateException(operation + " cannot be used after animations(Aero_AnimationSpec)");
        throw new IllegalStateException(operation + " requires animations(...) first");
    }

    private void requireMeshLod(double distance) {
        if (kind != Aero_ModelSpec.Kind.MESH) throw new IllegalStateException("meshLod() requires a mesh spec");
        requireNonNegativeFinite("fromDistanceBlocks", distance);
        if (meshLodDistances == null) {
            meshLodPaths = new ArrayList();
            meshLodModels = new ArrayList();
            meshLodDistances = new ArrayList();
        }
    }

    private static void requireNonNegativeFinite(String name, double value) {
        if (Double.isNaN(value) || Double.isInfinite(value))
            throw new IllegalArgumentException(name + " must be finite");
        if (value < 0.0d) throw new IllegalArgumentException(name + " must be >= 0");
    }

    private static Aero_MeshModel loadMesh(String path) {
        return path.toLowerCase(java.util.Locale.ROOT).endsWith(".three.json")
            ? Aero_ThreeJsonLoader.load(path)
            : Aero_ObjLoader.load(path);
    }
}
