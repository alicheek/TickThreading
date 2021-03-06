package me.nallar.patched;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.patcher.Declare;
import net.minecraft.block.Block;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.profiler.Profiler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.SpawnerAnimals;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.SpawnListEntry;
import net.minecraftforge.event.Event;
import net.minecraftforge.event.ForgeEventFactory;

public abstract class PatchSpawnerAnimals extends SpawnerAnimals {
	private static long hash(int x, int z) {
		return (((long) x) << 32) | (z & 0xffffffffL);
	}

	private static final int closeRange = 1;
	private static final int farRange = 5;
	private static final int spawnVariance = 6;
	private static final int clumping = 4;

	@Declare
	public static int spawnMobsQuickly(WorldServer worldServer, boolean peaceful, boolean hostile, boolean animal) {
		if (worldServer.tickCount % clumping != 0) {
			return 0;
		}
		final Profiler profiler = worldServer.theProfiler;
		profiler.startSection("creatureTypes");
		float loadFactor = 1 - (float) (MinecraftServer.getTickTime() / MinecraftServer.getTargetTickTime());
		if (loadFactor < 0.2f || loadFactor > 1f) {
			loadFactor = 0.2f;
		}
		float entityMultiplier = worldServer.playerEntities.size() * loadFactor; // TODO: Make this configurable
		if (entityMultiplier == 0) {
			profiler.endSection();
			return 0;
		}
		float mobMultiplier = entityMultiplier * (worldServer.isDaytime() ? 1 : 2);
		Map<EnumCreatureType, Integer> requiredSpawns = new EnumMap<EnumCreatureType, Integer>(EnumCreatureType.class);
		for (EnumCreatureType creatureType : EnumCreatureType.values()) {
			int count = (int) ((creatureType.getPeacefulCreature() ? entityMultiplier : mobMultiplier) * creatureType.getMaxNumberOfCreature());
			if (!(creatureType.getPeacefulCreature() && !peaceful || creatureType.getAnimal() && !animal || !creatureType.getPeacefulCreature() && !hostile) && count > worldServer.countEntities(creatureType.getCreatureClass())) {
				requiredSpawns.put(creatureType, count);
			}
		}
		profiler.endSection();

		if (requiredSpawns.isEmpty()) {
			return 0;
		}

		profiler.startSection("spawnableChunks");
		int attemptedSpawnedMobs = 0;
		Set<Long> closeChunks = new HashSet<Long>();
		List<Long> spawnableChunks = new ArrayList<Long>();
		for (Object entityPlayer_ : worldServer.playerEntities) {
			EntityPlayer entityPlayer = (EntityPlayer) entityPlayer_;
			int pX = entityPlayer.chunkCoordX;
			int pZ = entityPlayer.chunkCoordZ;
			int x = pX - closeRange;
			int maxX = pX + closeRange;
			int startZ = pZ - closeRange;
			int maxZ = pZ + closeRange;
			for (; x <= maxX; x++) {
				for (int z = startZ; z <= maxZ; z++) {
					closeChunks.add(hash(x, z));
				}
			}
		}
		for (Object entityPlayer_ : worldServer.playerEntities) {
			EntityPlayer entityPlayer = (EntityPlayer) entityPlayer_;
			int pX = entityPlayer.chunkCoordX;
			int pZ = entityPlayer.chunkCoordZ;
			int x = pX - farRange;
			int maxX = pX + farRange;
			int startZ = pZ - farRange;
			int maxZ = pZ + farRange;
			for (; x <= maxX; x++) {
				for (int z = startZ; z <= maxZ; z++) {
					long hash = hash(x, z);
					if (!closeChunks.contains(hash)) {
						spawnableChunks.add(hash);
					}
				}
			}
		}
		profiler.endStartSection("spawnMobs");

		int size = spawnableChunks.size();
		if (size < 1) {
			return 0;
		}

		SpawnLoop:
		for (Map.Entry<EnumCreatureType, Integer> entry : requiredSpawns.entrySet()) {
			EnumCreatureType creatureType = entry.getKey();
			long hash = spawnableChunks.get(worldServer.rand.nextInt(size));
			int x = (int) (hash >> 32);
			int z = (int) hash;
			int sX = x * 16 + worldServer.rand.nextInt(16);
			int sZ = z * 16 + worldServer.rand.nextInt(16);
			int sY = worldServer.getHeightValue(sX, sZ);
			if (creatureType == EnumCreatureType.waterCreature) {
				String biomeName = worldServer.getBiomeGenForCoords(sX, sZ).biomeName;
				if (!"Ocean".equals(biomeName) && !"River".equals(biomeName)) {
					continue;
				}
				sY -= 2;
			}
			if (worldServer.getBlockMaterial(sX, sY, sZ) == creatureType.getCreatureMaterial()) {
				for (int i = 0; i < 6; i++) {
					int ssX = sX + (worldServer.rand.nextInt(spawnVariance) - spawnVariance / 2);
					int ssZ = sZ + (worldServer.rand.nextInt(spawnVariance) - spawnVariance / 2);
					int ssY;

					if (creatureType == EnumCreatureType.waterCreature) {
						ssY = sY;
					} else if (creatureType == EnumCreatureType.ambient) {
						ssY = worldServer.rand.nextInt(63) + 1;
					} else {
						ssY = worldServer.getHeightValue(ssX, ssZ);
						if (!worldServer.getBlockMaterial(ssX, ssY - 1, ssZ).isOpaque() ||
								!Block.blocksList[worldServer.getBlockId(ssX, ssY - 1, ssZ)].canCreatureSpawn(creatureType, worldServer, ssX, ssY - 1, ssZ)) {
							continue;
						}
					}

					if (creatureType == EnumCreatureType.waterCreature || (!worldServer.getBlockMaterial(ssX, ssY - 1, ssZ).isLiquid())) {
						SpawnListEntry creatureClass = worldServer.spawnRandomCreature(creatureType, ssX, ssY, ssZ);
						if (creatureClass == null) {
							break;
						}

						EntityLiving spawnedEntity;
						try {
							spawnedEntity = (EntityLiving) creatureClass.entityClass.getConstructor(World.class).newInstance(worldServer);
							spawnedEntity.setLocationAndAngles((double) ssX, (double) ssY, (double) ssZ, worldServer.rand.nextFloat() * 360.0F, 0.0F);

							Event.Result canSpawn = ForgeEventFactory.canEntitySpawn(spawnedEntity, worldServer, ssX, ssY, ssZ);
							if (canSpawn == Event.Result.ALLOW || (canSpawn == Event.Result.DEFAULT && spawnedEntity.getCanSpawnHere())) {
								worldServer.spawnEntityInWorld(spawnedEntity);
								creatureSpecificInit(spawnedEntity, worldServer, ssX, ssY, ssZ);
							}
							attemptedSpawnedMobs++;
						} catch (Exception e) {
							Log.warning("Failed to spawn entity " + creatureClass, e);
							break SpawnLoop;
						}
					}
				}
			}
			if (attemptedSpawnedMobs >= 24) {
				break;
			}
		}
		profiler.endSection();
		return attemptedSpawnedMobs;
	}
}
