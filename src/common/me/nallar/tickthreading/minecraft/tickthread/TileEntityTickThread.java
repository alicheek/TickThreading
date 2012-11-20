package me.nallar.tickthreading.minecraft.tickthread;

import java.util.ArrayList;
import java.util.List;

import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.minecraft.ThreadManager;
import net.minecraft.src.Chunk;
import net.minecraft.src.TileEntity;
import net.minecraft.src.World;

public class TileEntityTickThread extends TickThread {
	private final List<TileEntity> tileEntityList = new ArrayList<TileEntity>();

	public TileEntityTickThread(World world, String identifier, ThreadManager manager, int hashCode) {
		super(world, identifier, manager, hashCode);
	}

	@Override
	public void run() {
		try {
			Log.fine("Started tick thread");
			while (tileEntityList.size() > 0 && manager.waitForTileEntityTick()) {
				for (TileEntity tileEntity : tileEntityList) {
					if (tileEntity.isInvalid()) {
						Log.fine("Invalid tile!");
						manager.remove(tileEntity);
						Log.warning("Removed invalid tile: " + tileEntity.xCoord + ", " + tileEntity.yCoord + ", " + tileEntity.zCoord + "\ttype:" + tileEntity.getClass().toString());//yes, it's blank...
						if (world.getChunkProvider().chunkExists(tileEntity.xCoord >> 4, tileEntity.zCoord >> 4)) {
							Chunk chunk = world.getChunkFromChunkCoords(tileEntity.xCoord >> 4, tileEntity.zCoord >> 4);
							if (chunk != null) {
								chunk.cleanChunkBlockTileEntity(tileEntity.xCoord & 0xf, tileEntity.yCoord, tileEntity.zCoord & 0xf);
							}
						}
					} else {
						tileEntity.updateEntity();
					}
					if (manager.getHashCode(tileEntity) != hashCode) {
						manager.remove(tileEntity);
						manager.add(tileEntity);
					}
				}
				manager.endTileEntityTick();
			}
		} catch (Exception exception) {
			Log.severe("Exception in tile entity tick thread " + identifier + ":", exception);
		}
		tileEntityList.clear();
	}

	public void add(TileEntity tileEntity) {
		if (!tileEntityList.contains(tileEntity)) {
			tileEntityList.add(tileEntity);
		}
	}

	public boolean remove(TileEntity tileEntity) {
		return tileEntityList.remove(tileEntity);
	}
}