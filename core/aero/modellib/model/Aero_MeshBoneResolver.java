package aero.modellib.model;

import aero.modellib.animation.Aero_AnimationBundle;
import aero.modellib.animation.Aero_AnimationClip;

/** Resolves named mesh groups to animated bone ancestry. */
final class Aero_MeshBoneResolver {
    private static final int MAX_DEPTH = 32;
    private static final int[] EMPTY_INT = new int[0];
    private static final String[] EMPTY_STRING = new String[0];
    private static final float[][] EMPTY_PIVOTS = new float[0][];

    private Aero_MeshBoneResolver() {}

    static Aero_MeshModel.BoneRef[] resolve(Aero_MeshModel.NamedGroup[] groups,
            Aero_AnimationClip clip, Aero_AnimationBundle bundle) {
        Aero_MeshModel.BoneRef[] result = new Aero_MeshModel.BoneRef[groups.length];
        for (int index = 0; index < groups.length; index++)
            result[index] = resolve(groups[index].name, clip, bundle);
        return result;
    }

    private static Aero_MeshModel.BoneRef resolve(String group, Aero_AnimationClip clip,
            Aero_AnimationBundle bundle) {
        float[] pivot = bundle.pivotOrZero(group);
        int bone = -1;
        String name = null;
        if (clip != null) {
            bone = clip.indexOfBone(group);
            if (bone >= 0) name = group;
            else {
                Resolution resolution = resolveParent(group, clip, bundle);
                bone = resolution.index;
                name = resolution.name;
                if (bone >= 0) pivot = bundle.pivotOrZero(clip.boneNames[bone]);
            }
        }
        Ancestry ancestry = ancestry(bone, name, pivot, clip, bundle);
        return new Aero_MeshModel.BoneRef(bone, name, pivot,
            ancestry.indices, ancestry.names, ancestry.pivots);
    }

    private static Resolution resolveParent(String group, Aero_AnimationClip clip,
            Aero_AnimationBundle bundle) {
        String parent = bundle.getParentBoneName(group);
        for (int depth = 0; parent != null && depth < 2; depth++) {
            int index = clip.indexOfBone(parent);
            if (index >= 0) return new Resolution(index, parent);
            parent = bundle.getParentBoneName(parent);
        }
        int index = prefixParent(clip, group);
        return new Resolution(index, index < 0 ? null : clip.boneNames[index]);
    }

    private static Ancestry ancestry(int bone, String name, float[] pivot,
            Aero_AnimationClip clip, Aero_AnimationBundle bundle) {
        if (bone < 0 || clip == null) return new Ancestry(EMPTY_INT, EMPTY_STRING, EMPTY_PIVOTS);
        String[] names = new String[MAX_DEPTH];
        int[] indices = new int[MAX_DEPTH];
        float[][] pivots = new float[MAX_DEPTH][];
        names[0] = name;
        indices[0] = bone;
        pivots[0] = pivot;
        int depth = 1;
        String parent = bundle.getParentBoneName(name);
        while (parent != null && depth < MAX_DEPTH) {
            int index = clip.indexOfBone(parent);
            if (index >= 0) {
                names[depth] = parent;
                indices[depth] = index;
                pivots[depth] = bundle.pivotOrZero(parent);
                depth++;
            }
            parent = bundle.getParentBoneName(parent);
        }
        int[] orderedIndices = new int[depth];
        String[] orderedNames = new String[depth];
        float[][] orderedPivots = new float[depth][];
        for (int index = 0; index < depth; index++) {
            orderedIndices[index] = indices[depth - 1 - index];
            orderedNames[index] = names[depth - 1 - index];
            orderedPivots[index] = pivots[depth - 1 - index];
        }
        return new Ancestry(orderedIndices, orderedNames, orderedPivots);
    }

    private static int prefixParent(Aero_AnimationClip clip, String group) {
        int best = -1, longest = 0;
        for (int index = 0; index < clip.boneNames.length; index++) {
            String bone = clip.boneNames[index];
            int length = bone.length();
            if (length <= longest || group.length() <= length || group.charAt(length) != '_') continue;
            if (!group.regionMatches(0, bone, 0, length)) continue;
            best = index;
            longest = length;
        }
        return best;
    }

    private static final class Resolution {
        final int index;
        final String name;
        Resolution(int index, String name) { this.index = index; this.name = name; }
    }

    private static final class Ancestry {
        final int[] indices;
        final String[] names;
        final float[][] pivots;
        Ancestry(int[] indices, String[] names, float[][] pivots) {
            this.indices = indices; this.names = names; this.pivots = pivots;
        }
    }
}
