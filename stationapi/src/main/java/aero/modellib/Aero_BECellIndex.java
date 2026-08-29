package aero.modellib;

import java.util.ArrayList;

import net.minecraft.block.entity.BlockEntity;

/** Public spatial-index facade for Aero-managed block entities. */
@aero.modellib.optimization.OptimizationRef({"aero.render.be-cell-index"})
public final class Aero_BECellIndex {
    public static final boolean ENABLED =
        !"false".equalsIgnoreCase(System.getProperty("aero.becell"));
    public static final int CELL_SIZE = Math.max(1,
        Math.min(32, Integer.getInteger("aero.becell.size", 8).intValue()));

    private Aero_BECellIndex() {}

    public static void beginFrame() { if (ENABLED) Aero_BECellStore.beginFrame(); }
    public static void track(BlockEntity blockEntity) {
        if (ENABLED) Aero_BECellStore.track(blockEntity);
    }
    static void trackState(BlockEntity blockEntity, int state, int orientation,
            boolean page, boolean animation) {
        if (ENABLED) Aero_BECellStore.trackState(
            blockEntity, state, orientation, page, animation);
    }
    public static void untrack(BlockEntity blockEntity) { Aero_BECellStore.untrack(blockEntity); }
    public static void markDirty(BlockEntity blockEntity) { Aero_BECellStore.markDirty(blockEntity); }
    public static ArrayList<Cell> snapshotCells() { return Aero_BECellStore.snapshot(false); }
    public static ArrayList<Cell> snapshotVisibleCells() { return Aero_BECellStore.snapshot(true); }
    public static int entryCount() { return Aero_BECellStore.entryCount(); }
    public static int cellCount() { return Aero_BECellStore.cellCount(); }
    public static int dirtyCellCount() { return Aero_BECellStore.dirtyCount(); }
    public static int moveCount() { return Aero_BECellStore.moveCount(); }
    public static int staleRemovedCount() { return Aero_BECellStore.staleRemovedCount(); }

    public static final class Cell {
        public final Object world;
        public final long key;
        public final int cellX;
        public final int cellY;
        public final int cellZ;
        final ArrayList<BlockEntity> entries = new ArrayList<BlockEntity>();
        boolean dirty;

        Cell(Object world, long key, int cellX, int cellY, int cellZ) {
            this.world = world;
            this.key = key;
            this.cellX = cellX;
            this.cellY = cellY;
            this.cellZ = cellZ;
        }

        public int size() { return entries.size(); }
        public BlockEntity get(int index) { return entries.get(index); }
        public boolean isDirty() { return dirty; }
        public void clearDirty() { Aero_BECellStore.clearDirty(this); }
        public int minBlockX() { return cellX * CELL_SIZE; }
        public int minBlockY() { return cellY * CELL_SIZE; }
        public int minBlockZ() { return cellZ * CELL_SIZE; }
        public int maxBlockX() { return minBlockX() + CELL_SIZE - 1; }
        public int maxBlockZ() { return minBlockZ() + CELL_SIZE - 1; }
    }
}
