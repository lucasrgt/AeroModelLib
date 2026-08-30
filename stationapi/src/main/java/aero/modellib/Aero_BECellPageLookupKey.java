package aero.modellib;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.minecraft.block.entity.BlockEntity;
import org.lwjgl.opengl.GL11;

import aero.modellib.model.Aero_MeshBlendMode;
import aero.modellib.model.Aero_MeshModel;
import aero.modellib.render.Aero_CellRenderableBE;
import aero.modellib.render.Aero_RenderOptions;
import aero.modellib.util.Aero_PerfConfig;
import aero.modellib.util.Aero_Profiler;

/**
 * At-rest BlockEntity cell pages. Renderers can queue static/LOD-overflow
 * meshes here instead of drawing each BE immediately; the flush compiles one
 * small display-list page per visible cell/render key and replays that page
 * while preserving the existing direct-render fallback.
 */
final class Aero_BECellPageLookupKey {
        Object world;
        Aero_MeshModel model;
        String texturePath;
        Aero_RenderOptions options;
        int cellX;
        int cellY;
        int cellZ;
        int rotationBits;
        int brightnessBits;
        int stateHash;
        int orientationHash;
        int tintRBits;
        int tintGBits;
        int tintBBits;
        int alphaBits;
        int alphaClipBits;
        int hash;

        Aero_BECellPageLookupKey set(Object world, Aero_MeshModel model, String texturePath,
                          Aero_RenderOptions options, int cellX, int cellY, int cellZ,
                          float rotation, float brightness,
                          int stateHash, int orientationHash) {
            this.world = world;
            this.model = model;
            this.texturePath = texturePath;
            this.options = options;
            this.cellX = cellX;
            this.cellY = cellY;
            this.cellZ = cellZ;
            this.rotationBits = Float.floatToIntBits(rotation);
            this.brightnessBits = Float.floatToIntBits(brightness);
            this.stateHash = stateHash;
            this.orientationHash = orientationHash;
            this.tintRBits = Float.floatToIntBits(options.tintR);
            this.tintGBits = Float.floatToIntBits(options.tintG);
            this.tintBBits = Float.floatToIntBits(options.tintB);
            this.alphaBits = Float.floatToIntBits(options.alpha);
            this.alphaClipBits = Float.floatToIntBits(options.alphaClip);
            this.hash = computeHash();
            return this;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        private int computeHash() {
            int result = System.identityHashCode(world);
            result = 31 * result + System.identityHashCode(model);
            result = 31 * result + (texturePath != null ? texturePath.hashCode() : 0);
            result = 31 * result + cellX;
            result = 31 * result + cellY;
            result = 31 * result + cellZ;
            result = 31 * result + rotationBits;
            result = 31 * result + brightnessBits;
            result = 31 * result + stateHash;
            result = 31 * result + orientationHash;
            result = 31 * result + tintRBits;
            result = 31 * result + tintGBits;
            result = 31 * result + tintBBits;
            result = 31 * result + alphaBits;
            result = 31 * result + alphaClipBits;
            result = 31 * result + options.blend.hashCode();
            result = 31 * result + (options.depthTest ? 1 : 0);
            result = 31 * result + (options.cullFaces ? 1 : 0);
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Aero_BECellPageKey)) return false;
            Aero_BECellPageKey other = (Aero_BECellPageKey) obj;
            if (!sameOwner(other)) return false;
            if (!sameCell(other)) return false;
            if (!sameTransform(other)) return false;
            if (!sameAppearance(other)) return false;
            if (!sameOptions(other)) return false;
            return sameTexture(other);
        }

        private boolean sameOwner(Aero_BECellPageKey other) {
            return world == other.world && model == other.model;
        }

        private boolean sameCell(Aero_BECellPageKey other) {
            return cellX == other.cellX && cellY == other.cellY && cellZ == other.cellZ;
        }

        private boolean sameTransform(Aero_BECellPageKey other) {
            return rotationBits == other.rotationBits
                && brightnessBits == other.brightnessBits
                && stateHash == other.stateHash
                && orientationHash == other.orientationHash;
        }

        private boolean sameAppearance(Aero_BECellPageKey other) {
            return tintRBits == other.tintRBits && tintGBits == other.tintGBits
                && tintBBits == other.tintBBits && alphaBits == other.alphaBits
                && alphaClipBits == other.alphaClipBits;
        }

        private boolean sameOptions(Aero_BECellPageKey other) {
            return options.blend == other.options.blend
                && options.depthTest == other.options.depthTest
                && options.cullFaces == other.options.cullFaces;
        }

        private boolean sameTexture(Aero_BECellPageKey other) {
            if (texturePath == other.texturePath) return true;
            return texturePath != null && texturePath.equals(other.texturePath);
        }
    }
