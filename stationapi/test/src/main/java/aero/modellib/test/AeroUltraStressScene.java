package aero.modellib.test;

import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntity;
import net.modificationstation.stationapi.api.event.world.gen.WorldGenEvent;

/** Fills qualifying chunks with a vertically dense mix of every expensive path. */
final class AeroUltraStressScene {
    private static boolean announced;
    private static int centralBaseY;

    private AeroUltraStressScene() {}

    static void populate(WorldGenEvent.ChunkDecoration event) {
        int stride = AeroUltraStressConfig.verticalStride();
        int surfaceY = event.world.getTopSolidBlockY(event.x + 8, event.z + 8) + 1;
        int lastLayerOffset = (AeroUltraStressConfig.LAYERS - 1) * stride;
        int lastFloorOffset = AeroUltraStressConfig.SOLID_FLOORS ? 1 : 0;
        int baseY = Math.max(4, Math.min(surfaceY, 127 - lastLayerOffset - lastFloorOffset));
        if (event.x == 0 && event.z == 0) centralBaseY = baseY;
        for (int layer = 0; layer < AeroUltraStressConfig.LAYERS; layer++) {
            int y = baseY + layer * stride;
            for (int dx = 0; dx < 16; dx++) {
                for (int dz = 0; dz < 16; dz++) {
                    placeMachine(event, dx, y, dz, (dx + dz * 3 + layer * 5) % 12);
                }
            }
            if (AeroUltraStressConfig.SOLID_FLOORS) fillFloor(event, y + 1);
        }
        if (!announced) {
            announced = true;
            System.out.println("[AeroUltraStress] active machinesPerChunk="
                + AeroUltraStressConfig.machinesPerChunk() + " layers="
                + AeroUltraStressConfig.LAYERS + " spacingChunks="
                + AeroUltraStressConfig.SPACING_CHUNKS + " solidFloors="
                + AeroUltraStressConfig.SOLID_FLOORS + " phaseSpread="
                + AeroUltraStressConfig.PHASE_SPREAD);
        }
    }

    static int centralBaseY() {
        return centralBaseY;
    }

    private static void placeMachine(WorldGenEvent.ChunkDecoration event,
                                     int dx, int y, int dz, int kind) {
        switch (kind) {
            case 0: place(event, dx, y, dz, AeroTestMod.motorBlock.id,
                new MotorBlockEntity()); break;
            case 1: place(event, dx, y, dz, AeroTestMod.animatedMegaModelBlock.id,
                new AnimatedMegaModelBlockEntity()); break;
            case 2: place(event, dx, y, dz, AeroTestMod.pumpBlock.id,
                new PumpBlockEntity()); break;
            case 3: place(event, dx, y, dz, AeroTestMod.conveyorBlock.id,
                new ConveyorBlockEntity()); break;
            case 4: place(event, dx, y, dz, AeroTestMod.crystalChaosBlock.id,
                new CrystalChaosBlockEntity()); break;
            case 5: place(event, dx, y, dz, AeroTestMod.spellCircleBlock.id,
                new SpellCircleBlockEntity()); break;
            case 6: place(event, dx, y, dz, AeroTestMod.turretIkBlock.id,
                new TurretIKBlockEntity()); break;
            case 7: place(event, dx, y, dz, AeroTestMod.morphCrystalBlock.id,
                new MorphCrystalBlockEntity()); break;
            case 8: place(event, dx, y, dz, AeroTestMod.graphPoweredBlock.id,
                new GraphPoweredBlockEntity()); break;
            case 9: place(event, dx, y, dz, AeroTestMod.easingShowcaseBlock.id,
                new EasingShowcaseBlockEntity()); break;
            case 10: place(event, dx, y, dz, AeroTestMod.plasmaCrystalBlock.id,
                new PlasmaCrystalBlockEntity()); break;
            default: place(event, dx, y, dz, AeroTestMod.crystalBlock.id,
                new CrystalBlockEntity()); break;
        }
    }

    private static void place(WorldGenEvent.ChunkDecoration event, int dx, int y, int dz,
                              int blockId, BlockEntity blockEntity) {
        int x = event.x + dx;
        int z = event.z + dz;
        event.world.setBlockWithoutNotifyingNeighbors(x, y, z, blockId);
        event.world.removeBlockEntity(x, y, z);
        event.world.setBlockEntity(x, y, z, blockEntity);
    }

    private static void fillFloor(WorldGenEvent.ChunkDecoration event, int y) {
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                event.world.setBlockWithoutNotifyingNeighbors(
                    event.x + dx, y, event.z + dz, Block.COBBLESTONE.id);
            }
        }
    }
}
