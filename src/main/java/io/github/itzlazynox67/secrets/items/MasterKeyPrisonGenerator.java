package io.github.itzlazynox67.secrets.items;

import org.bukkit.Material;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.Random;

/** Flat, bedrock-bottom terrain for the Master Key prison world. */
public final class MasterKeyPrisonGenerator extends ChunkGenerator {
    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData data) {
        int bottom = worldInfo.getMinHeight();
        data.setRegion(0, bottom, 0, 16, bottom + 1, 16, Material.BEDROCK);
        data.setRegion(0, bottom + 1, 0, 16, bottom + 4, 16, Material.DEEPSLATE);
        data.setRegion(0, bottom + 4, 0, 16, bottom + 6, 16, Material.DEEPSLATE_BRICKS);

        // Sparse cracked patches add texture without making the landscape uneven.
        Random details = new Random((((long) chunkX) << 32) ^ (chunkZ & 0xffffffffL) ^ 0x4D41535445524B45L);
        for (int patch = 0; patch < 5; patch++) {
            int x = details.nextInt(16);
            int z = details.nextInt(16);
            int radius = 1 + details.nextInt(2);
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dz * dz > radius * radius + 1) continue;
                    int px = x + dx;
                    int pz = z + dz;
                    if (px >= 0 && px < 16 && pz >= 0 && pz < 16) {
                        data.setBlock(px, bottom + 5, pz,
                                details.nextBoolean() ? Material.CRACKED_DEEPSLATE_BRICKS : Material.POLISHED_BLACKSTONE);
                    }
                }
            }
        }
    }

    @Override
    public boolean shouldGenerateNoise() { return true; }

    @Override
    public boolean shouldGenerateSurface() { return false; }

    @Override
    public boolean shouldGenerateCaves() { return false; }

    @Override
    public boolean shouldGenerateDecorations() { return false; }

    @Override
    public boolean shouldGenerateMobs() { return false; }

    @Override
    public boolean shouldGenerateStructures() { return false; }
}
