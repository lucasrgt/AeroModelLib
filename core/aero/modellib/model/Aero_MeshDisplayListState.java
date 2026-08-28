package aero.modellib.model;

import aero.modellib.skeletal.Aero_BonePageLists;

/** Platform-neutral holder for renderer-owned display-list cache state. */
@aero.modellib.optimization.OptimizationRef({"aero.render.at-rest-display-lists"})
final class Aero_MeshDisplayListState {
    private int[] atRestIds;
    private boolean atRestFailed;
    private Aero_BonePageLists bonePages;
    private boolean bonePagesFailed;

    int[] atRestIds() { return atRestIds; }
    void atRestIds(int[] value) { atRestIds = value; }
    boolean atRestFailed() { return atRestFailed; }
    void markAtRestFailed() { atRestFailed = true; }

    int[] clearAtRest() {
        int[] result = atRestIds;
        atRestIds = null;
        atRestFailed = false;
        return result;
    }

    Aero_BonePageLists bonePages() { return bonePages; }
    void bonePages(Aero_BonePageLists value) { bonePages = value; }
    boolean bonePagesFailed() { return bonePagesFailed; }
    void markBonePagesFailed() { bonePagesFailed = true; }

    Aero_BonePageLists clearBonePages() {
        Aero_BonePageLists result = bonePages;
        bonePages = null;
        bonePagesFailed = false;
        return result;
    }
}
