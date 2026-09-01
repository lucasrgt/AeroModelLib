package aero.modellib;

import java.util.ArrayDeque;

import aero.modellib.optimization.OptimizationRef;
import aero.modellib.util.Aero_PerfConfig;

/** Bounded reuse for transient Cell Page storage, with no retained owner keys. */
@OptimizationRef({"aero.render.be-cell-queue-reuse"})
final class Aero_BECellQueuePool {
    static final boolean ENABLED = Aero_PerfConfig.booleanProperty(
        "aero.becell.queueReuse", true, true);
    private static final int MAX_PAGES = Aero_PerfConfig.intProperty(
        "aero.becell.queueReuseMaxPages", 256, 512, 0, 4096);
    private static final int MAX_RETAINED_INSTANCES = Aero_PerfConfig.intProperty(
        "aero.becell.queueReuseMaxInstances", 256, 512, 16, 4096);
    private static final ArrayDeque<Aero_BECellQueuedPage> PAGES =
        new ArrayDeque<Aero_BECellQueuedPage>();
    private static int allocatedPages;
    private static int reusedPages;
    private static int discardedPages;

    private Aero_BECellQueuePool() {}

    static Aero_BECellQueuedPage acquire(Aero_BECellPageKey key) {
        Aero_BECellQueuedPage page = ENABLED ? PAGES.pollLast() : null;
        if (page == null) {
            allocatedPages++;
            return new Aero_BECellQueuedPage(key);
        }
        reusedPages++;
        page.reset(key);
        return page;
    }

    static void release(Aero_BECellQueuedPage page) {
        int capacity = page.capacity();
        page.releaseReferences();
        if (!ENABLED) return;
        if (PAGES.size() >= MAX_PAGES || capacity > MAX_RETAINED_INSTANCES) {
            discardedPages++;
            return;
        }
        PAGES.addLast(page);
    }

    static int pooledPages() { return PAGES.size(); }
    static int allocatedPages() { return allocatedPages; }
    static int reusedPages() { return reusedPages; }
    static int discardedPages() { return discardedPages; }
}
