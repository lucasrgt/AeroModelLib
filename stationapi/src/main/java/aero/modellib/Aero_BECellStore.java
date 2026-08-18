package aero.modellib;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

import net.minecraft.block.entity.BlockEntity;

import aero.modellib.render.Aero_CellRenderableBE;

/** Membership, dirty-state, and stale-entry storage behind the cell-index API. */
final class Aero_BECellStore {
    private static final IdentityHashMap<Object, HashMap<Long, Aero_BECellIndex.Cell>> WORLDS =
        new IdentityHashMap<Object, HashMap<Long, Aero_BECellIndex.Cell>>();
    private static final IdentityHashMap<BlockEntity, Entry> ENTRIES =
        new IdentityHashMap<BlockEntity, Entry>();
    private static int frames, dirty, moves, stale;

    private Aero_BECellStore() {}

    static void beginFrame() {
        frames++;
        if ((frames & 63) == 0) sweep();
    }

    static void track(BlockEntity blockEntity) {
        if (blockEntity == null || !(blockEntity instanceof Aero_CellRenderableBE)) return;
        Object world = blockEntity.world;
        if (world == null) { untrack(blockEntity); return; }
        Aero_CellRenderableBE renderable = (Aero_CellRenderableBE) blockEntity;
        int x = Math.floorDiv(blockEntity.x, Aero_BECellIndex.CELL_SIZE);
        int y = Math.floorDiv(blockEntity.y, Aero_BECellIndex.CELL_SIZE);
        int z = Math.floorDiv(blockEntity.z, Aero_BECellIndex.CELL_SIZE);
        long key = pack(x, y, z);
        Entry existing = ENTRIES.get(blockEntity);
        if (existing != null && existing.world == world && existing.key == key) {
            update(existing, renderable);
            return;
        }
        if (existing != null) { remove(blockEntity, existing, true); moves++; }
        Aero_BECellIndex.Cell cell = cell(world, key, x, y, z);
        Entry entry = new Entry(world, key, cell, renderable);
        cell.entries.add(blockEntity);
        ENTRIES.put(blockEntity, entry);
        dirty(cell);
    }

    static void untrack(BlockEntity blockEntity) {
        if (blockEntity == null) return;
        Entry entry = ENTRIES.get(blockEntity);
        if (entry != null) remove(blockEntity, entry, true);
    }

    static void markDirty(BlockEntity blockEntity) {
        Entry entry = ENTRIES.get(blockEntity);
        if (entry != null) dirty(entry.cell);
    }

    static ArrayList<Aero_BECellIndex.Cell> snapshot(boolean visibleOnly) {
        ArrayList<Aero_BECellIndex.Cell> result = new ArrayList<Aero_BECellIndex.Cell>();
        Iterator<HashMap<Long, Aero_BECellIndex.Cell>> maps = WORLDS.values().iterator();
        while (maps.hasNext()) {
            Iterator<Aero_BECellIndex.Cell> cells = maps.next().values().iterator();
            while (cells.hasNext()) {
                Aero_BECellIndex.Cell cell = cells.next();
                if (!visibleOnly || visible(cell)) result.add(cell);
            }
        }
        return result;
    }

    static int entryCount() { return ENTRIES.size(); }
    static int dirtyCount() { return dirty; }
    static int moveCount() { return moves; }
    static int staleRemovedCount() { return stale; }
    static int cellCount() {
        int result = 0;
        for (HashMap<Long, Aero_BECellIndex.Cell> cells : WORLDS.values()) result += cells.size();
        return result;
    }

    static void clearDirty(Aero_BECellIndex.Cell cell) {
        if (!cell.dirty) return;
        cell.dirty = false;
        dirty--;
    }

    private static void update(Entry entry, Aero_CellRenderableBE renderable) {
        int state = renderable.aeroRenderStateHash();
        int orientation = renderable.aeroOrientationHash();
        boolean page = renderable.aeroCanCellPage();
        boolean animation = renderable.aeroWantsAnimation();
        if (entry.state == state && entry.orientation == orientation
                && entry.page == page && entry.animation == animation) return;
        entry.state = state; entry.orientation = orientation;
        entry.page = page; entry.animation = animation;
        dirty(entry.cell);
    }

    private static Aero_BECellIndex.Cell cell(Object world, long key, int x, int y, int z) {
        HashMap<Long, Aero_BECellIndex.Cell> cells = WORLDS.get(world);
        if (cells == null) { cells = new HashMap<Long, Aero_BECellIndex.Cell>(); WORLDS.put(world, cells); }
        Long boxed = Long.valueOf(key);
        Aero_BECellIndex.Cell result = cells.get(boxed);
        if (result == null) { result = new Aero_BECellIndex.Cell(world, key, x, y, z); cells.put(boxed, result); }
        return result;
    }

    private static void sweep() {
        Iterator<Map.Entry<BlockEntity, Entry>> entries = ENTRIES.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<BlockEntity, Entry> item = entries.next();
            if (item.getKey().world != null && item.getKey().world == item.getValue().world) continue;
            remove(item.getKey(), item.getValue(), false);
            entries.remove();
            stale++;
        }
    }

    private static void remove(BlockEntity blockEntity, Entry entry, boolean removeEntry) {
        entry.cell.entries.remove(blockEntity);
        dirty(entry.cell);
        if (entry.cell.entries.isEmpty()) removeCell(entry.cell);
        if (removeEntry) ENTRIES.remove(blockEntity);
    }

    private static void removeCell(Aero_BECellIndex.Cell cell) {
        HashMap<Long, Aero_BECellIndex.Cell> cells = WORLDS.get(cell.world);
        if (cells == null) return;
        cells.remove(Long.valueOf(cell.key));
        clearDirty(cell);
        if (cells.isEmpty()) WORLDS.remove(cell.world);
    }

    private static void dirty(Aero_BECellIndex.Cell cell) {
        if (cell == null || cell.dirty) return;
        cell.dirty = true;
        dirty++;
    }

    private static boolean visible(Aero_BECellIndex.Cell cell) {
        return Aero_ChunkVisibility.isBlockAreaChunkVisible(
            cell.minBlockX(), cell.minBlockZ(), cell.maxBlockX(), cell.maxBlockZ());
    }

    private static long pack(int x, int y, int z) {
        return ((long) x & 0x3FFFFFL) << 42 | ((long) y & 0xFFFFFL) << 22 | ((long) z & 0x3FFFFFL);
    }

    private static final class Entry {
        final Object world;
        final long key;
        final Aero_BECellIndex.Cell cell;
        int state, orientation;
        boolean page, animation;
        Entry(Object world, long key, Aero_BECellIndex.Cell cell, Aero_CellRenderableBE renderable) {
            this.world = world; this.key = key; this.cell = cell;
            state = renderable.aeroRenderStateHash(); orientation = renderable.aeroOrientationHash();
            page = renderable.aeroCanCellPage(); animation = renderable.aeroWantsAnimation();
        }
    }
}
