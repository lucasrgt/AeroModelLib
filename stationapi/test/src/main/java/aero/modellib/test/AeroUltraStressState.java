package aero.modellib.test;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.InteractionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.SingleplayerInteractionManager;

/** Automated fresh-world lifecycle for repeatable ULTRA runs. */
public final class AeroUltraStressState {
    private static final boolean AUTO_START =
        Boolean.parseBoolean(System.getProperty("aero.ultra.autoStart", "false"));
    private static final String WORLD =
        System.getProperty("aero.ultra.world", "AeroUltraStress");
    private static final long SEED = Long.getLong("aero.ultra.seed", 17320110707L);
    private static int stage, waitingTicks;
    private static boolean ready;

    private AeroUltraStressState() {}

    public static void drive(Minecraft game) {
        if (!AeroUltraStressConfig.ENABLED || !AUTO_START) return;
        prepareDisplay(game);
        if (stage == 0 && game.world == null) {
            stage = 1;
            game.interactionManager = new SingleplayerInteractionManager(game);
            System.out.println("[AeroUltraStress] creating world=" + WORLD + " seed=" + SEED);
            game.startGame(WORLD, "Aero Ultra Stress", SEED);
            return;
        }
        if (game.world == null || game.player == null || ready) {
            if (ready) AeroUltraJourney.drive(game);
            return;
        }
        game.world.getChunk(0, 0);
        int count = centralMachineCount(game);
        if (++waitingTicks % 100 == 0) {
            System.out.println("[AeroUltraStress] loading central tower machines=" + count
                + "/" + AeroUltraStressConfig.machinesPerChunk());
        }
        if (count < AeroUltraStressConfig.machinesPerChunk()) return;
        ready = true;
        AeroUltraJourney.prepare(game);
        startExitTimer();
        System.out.println("[AeroUltraStress] measurement-ready centralMachines=" + count
            + " warmupSec=" + AeroUltraStressConfig.WARMUP_SECONDS
            + " durationSec=" + AeroUltraStressConfig.DURATION_SECONDS
            + " journey=" + AeroUltraStressConfig.JOURNEY
            + " worldMode=" + AeroUltraStressConfig.WORLD_MODE);
    }

    public static boolean ready() {
        return ready || !AUTO_START;
    }

    private static int centralMachineCount(Minecraft game) {
        int count = 0;
        for (Object value : game.world.blockEntities) {
            BlockEntity blockEntity = (BlockEntity) value;
            if (!blockEntity.isRemoved() && blockEntity.x >= 0 && blockEntity.x < 16
                    && blockEntity.z >= 0 && blockEntity.z < 16) count++;
        }
        return count;
    }

    private static void prepareDisplay(Minecraft game) {
        game.currentScreen = null;
        game.paused = false;
        game.skipGameRender = false;
        if (game.options != null) {
            game.options.hideHud = true;
            game.options.bobView = false;
            game.options.viewDistance = 0;
            game.options.fpsLimit = 0;
        }
    }

    private static void startExitTimer() {
        Thread timer = new Thread(new Runnable() {
            public void run() {
                try {
                    Thread.sleep((AeroUltraStressConfig.WARMUP_SECONDS
                        + AeroUltraStressConfig.DURATION_SECONDS + 120L) * 1000L);
                } catch (InterruptedException ignored) {
                    return;
                }
                System.err.println("[AeroUltraStress] watchdog elapsed; forcing exit");
                System.exit(0);
            }
        }, "aero-ultra-exit");
        timer.setDaemon(true);
        timer.start();
    }
}
