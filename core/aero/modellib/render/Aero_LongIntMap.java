package aero.modellib.render;

/** Small allocation-free open-addressed map used by hot render identities. */
final class Aero_LongIntMap {
    private long[] keys; private int[] values; private boolean[] used;
    private int size, threshold;
    Aero_LongIntMap(int capacity) {
        int actual = 1; while (actual < capacity) actual <<= 1;
        keys = new long[actual]; values = new int[actual]; used = new boolean[actual]; threshold = actual * 2 / 3;
    }
    int get(long key, int fallback) {
        int mask = keys.length - 1, index = mix(key) & mask;
        while (used[index]) { if (keys[index] == key) return values[index]; index = index + 1 & mask; }
        return fallback;
    }
    void put(long key, int value) { if (size >= threshold) grow(); putInternal(key, value); }
    void clear() { if (size == 0) return; for (int index = 0; index < used.length; index++) used[index] = false; size = 0; }
    void removeValuesLessThan(int minimum) {
        for (int index = 0; index < used.length; index++) while (used[index] && values[index] < minimum) removeAt(index);
    }
    private void removeAt(int index) {
        int mask = used.length - 1; used[index] = false; size--; int next = index + 1 & mask;
        while (used[next]) { long key = keys[next]; int value = values[next]; used[next] = false; size--; putInternal(key, value); next = next + 1 & mask; }
    }
    private void putInternal(long key, int value) {
        int mask = keys.length - 1, index = mix(key) & mask;
        while (used[index]) { if (keys[index] == key) { values[index] = value; return; } index = index + 1 & mask; }
        used[index] = true; keys[index] = key; values[index] = value; size++;
    }
    private void grow() {
        long[] oldKeys = keys; int[] oldValues = values; boolean[] oldUsed = used;
        keys = new long[oldKeys.length * 2]; values = new int[oldValues.length * 2]; used = new boolean[oldUsed.length * 2]; threshold = keys.length * 2 / 3; size = 0;
        for (int index = 0; index < oldUsed.length; index++) if (oldUsed[index]) putInternal(oldKeys[index], oldValues[index]);
    }
    private static int mix(long value) { value ^= value >>> 33; value *= 0xff51afd7ed558ccdL; value ^= value >>> 33; return (int) value; }
}
