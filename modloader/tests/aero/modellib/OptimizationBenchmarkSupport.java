package aero.modellib;

import java.lang.management.ManagementFactory;
import java.util.Arrays;

/** Small counterbalanced benchmark utility with optional thread allocation census. */
public final class OptimizationBenchmarkSupport {
    private static final com.sun.management.ThreadMXBean ALLOC = allocationBean();
    private static volatile long sink;

    private OptimizationBenchmarkSupport() {}

    public interface Work {
        long run();
    }

    public static void compare(String id, int warmup, int iterations, int logicalOps,
                               Work optimized, Work oracle) {
        for (int i = 0; i < warmup; i++) {
            sink ^= optimized.run();
            sink ^= oracle.run();
        }
        double[] optimizedNanos = new double[7], oracleNanos = new double[7];
        double[] optimizedBytes = new double[7], oracleBytes = new double[7];
        long optimizedChecksum = 0L, oracleChecksum = 0L;
        for (int round = 0; round < 7; round++) {
            Sample first, second;
            if ((round & 1) == 0) {
                first = measure(optimized, iterations, logicalOps);
                second = measure(oracle, iterations, logicalOps);
                optimizedChecksum = first.checksum; oracleChecksum = second.checksum;
                optimizedNanos[round] = first.nanos; optimizedBytes[round] = first.bytes;
                oracleNanos[round] = second.nanos; oracleBytes[round] = second.bytes;
            } else {
                first = measure(oracle, iterations, logicalOps);
                second = measure(optimized, iterations, logicalOps);
                oracleChecksum = first.checksum; optimizedChecksum = second.checksum;
                oracleNanos[round] = first.nanos; oracleBytes[round] = first.bytes;
                optimizedNanos[round] = second.nanos; optimizedBytes[round] = second.bytes;
            }
        }
        if (optimizedChecksum != oracleChecksum) {
            throw new AssertionError(id + " checksum mismatch "
                + optimizedChecksum + " != " + oracleChecksum);
        }
        double optNs = median(optimizedNanos), oldNs = median(oracleNanos);
        double optBytes = median(optimizedBytes), oldBytes = median(oracleBytes);
        System.out.println("MICRO," + id + ',' + number(optNs) + ',' + number(oldNs)
            + ',' + percent(oldNs, optNs) + ',' + number(optBytes) + ',' + number(oldBytes)
            + ',' + percent(oldBytes, optBytes) + ',' + optimizedChecksum);
    }

    public static void header() {
        System.out.println("MICRO_HEADER,id,optimized_ns_per_op,oracle_ns_per_op,time_saved_pct,"
            + "optimized_bytes_per_op,oracle_bytes_per_op,allocation_saved_pct,checksum");
    }

    public static long sink() { return sink; }

    private static Sample measure(Work work, int iterations, int logicalOps) {
        long thread = Thread.currentThread().getId();
        long beforeBytes = allocated(thread);
        long start = System.nanoTime();
        long checksum = 0L;
        for (int i = 0; i < iterations; i++) checksum += work.run();
        long nanos = System.nanoTime() - start;
        long bytes = allocated(thread) - beforeBytes;
        sink ^= checksum;
        double divisor = (double) iterations * logicalOps;
        return new Sample(nanos / divisor, bytes < 0L ? -1.0d : bytes / divisor, checksum);
    }

    private static long allocated(long thread) {
        return ALLOC == null ? -1L : ALLOC.getThreadAllocatedBytes(thread);
    }

    private static com.sun.management.ThreadMXBean allocationBean() {
        java.lang.management.ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        if (!(bean instanceof com.sun.management.ThreadMXBean)) return null;
        com.sun.management.ThreadMXBean result = (com.sun.management.ThreadMXBean) bean;
        if (!result.isThreadAllocatedMemorySupported()) return null;
        if (!result.isThreadAllocatedMemoryEnabled()) result.setThreadAllocatedMemoryEnabled(true);
        return result;
    }

    private static double median(double[] values) {
        double[] copy = values.clone();
        Arrays.sort(copy);
        return copy[copy.length / 2];
    }

    private static String number(double value) {
        return String.valueOf(Math.round(value * 100.0d) / 100.0d);
    }

    private static String percent(double oracle, double optimized) {
        if (oracle == 0.0d) return "0.0";
        return number((1.0d - optimized / oracle) * 100.0d);
    }

    private static final class Sample {
        final double nanos, bytes;
        final long checksum;
        Sample(double nanos, double bytes, long checksum) {
            this.nanos = nanos; this.bytes = bytes; this.checksum = checksum;
        }
    }
}
