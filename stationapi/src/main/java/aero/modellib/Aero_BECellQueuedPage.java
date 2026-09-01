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
final class Aero_BECellQueuedPage {
        Aero_BECellPageKey key;
        double[] worldXs = new double[16];
        double[] worldYs = new double[16];
        double[] worldZs = new double[16];
        float[] brightnesses = new float[16];
        int[] identityHashes = new int[16];
        long[] sortKeys = new long[16];
        Aero_BECellCachedPage replayCached;
        int[] replayModelIds;
        boolean replayDirect;
        int count;

        Aero_BECellQueuedPage(Aero_BECellPageKey key) {
            reset(key);
        }

        void reset(Aero_BECellPageKey pageKey) {
            if (pageKey == null) throw new IllegalArgumentException("pageKey must not be null");
            key = pageKey;
        }

        void add(BlockEntity be, double worldX, double worldY, double worldZ,
                 float brightness) {
            ensureCapacity();
            worldXs[count] = worldX;
            worldYs[count] = worldY;
            worldZs[count] = worldZ;
            brightnesses[count] = brightness;
            int identity = System.identityHashCode(be);
            identityHashes[count] = identity;
            sortKeys[count] = Aero_BECellGeometry.stableSortKey(worldX, worldY, worldZ, identity);
            count++;
        }

        int membershipHash() {
            if (Aero_BECellRenderState.STABLE_MEMBERSHIP && count > 1) {
                sortEntries(0, count - 1);
            }
            int hash = 1;
            for (int i = 0; i < count; i++) {
                hash = 31 * hash + identityHashes[i];
                hash = 31 * hash + Aero_BECellGeometry.floorToInt(worldXs[i] * 16.0d);
                hash = 31 * hash + Aero_BECellGeometry.floorToInt(worldYs[i] * 16.0d);
                hash = 31 * hash + Aero_BECellGeometry.floorToInt(worldZs[i] * 16.0d);
                if (Aero_BECellRenderState.PER_INSTANCE_LIGHT) {
                    hash = 31 * hash + Float.floatToIntBits(brightnesses[i]);
                }
            }
            return hash;
        }

        void clear() {
            replayCached = null;
            replayModelIds = null;
            replayDirect = false;
            count = 0;
        }

        void releaseReferences() {
            clear();
            key = null;
        }

        int capacity() {
            return worldXs.length;
        }

        void prepareReplay(Aero_BECellCachedPage cached, int[] modelIds) {
            replayCached = cached;
            replayModelIds = modelIds;
            replayDirect = false;
        }

        void prepareDirectReplay() {
            replayCached = null;
            replayModelIds = null;
            replayDirect = true;
        }

        private void ensureCapacity() {
            if (count < worldXs.length) return;
            int n = worldXs.length * 2;
            double[] newXs = new double[n];
            double[] newYs = new double[n];
            double[] newZs = new double[n];
            float[] newBrightnesses = new float[n];
            int[] newHashes = new int[n];
            long[] newSortKeys = new long[n];
            System.arraycopy(worldXs, 0, newXs, 0, worldXs.length);
            System.arraycopy(worldYs, 0, newYs, 0, worldYs.length);
            System.arraycopy(worldZs, 0, newZs, 0, worldZs.length);
            System.arraycopy(brightnesses, 0, newBrightnesses, 0, brightnesses.length);
            System.arraycopy(identityHashes, 0, newHashes, 0, identityHashes.length);
            System.arraycopy(sortKeys, 0, newSortKeys, 0, sortKeys.length);
            worldXs = newXs;
            worldYs = newYs;
            worldZs = newZs;
            brightnesses = newBrightnesses;
            identityHashes = newHashes;
            sortKeys = newSortKeys;
        }

        private void sortEntries(int left, int right) {
            int i = left;
            int j = right;
            long pivot = sortKeys[(left + right) >>> 1];
            while (i <= j) {
                while (sortKeys[i] < pivot) i++;
                while (sortKeys[j] > pivot) j--;
                if (i <= j) {
                    swap(i, j);
                    i++;
                    j--;
                }
            }
            if (left < j) sortEntries(left, j);
            if (i < right) sortEntries(i, right);
        }

        private void swap(int a, int b) {
            if (a == b) return;
            double dx = worldXs[a];
            double dy = worldYs[a];
            double dz = worldZs[a];
            float br = brightnesses[a];
            int ih = identityHashes[a];
            long sk = sortKeys[a];
            worldXs[a] = worldXs[b];
            worldYs[a] = worldYs[b];
            worldZs[a] = worldZs[b];
            brightnesses[a] = brightnesses[b];
            identityHashes[a] = identityHashes[b];
            sortKeys[a] = sortKeys[b];
            worldXs[b] = dx;
            worldYs[b] = dy;
            worldZs[b] = dz;
            brightnesses[b] = br;
            identityHashes[b] = ih;
            sortKeys[b] = sk;
        }
    }
