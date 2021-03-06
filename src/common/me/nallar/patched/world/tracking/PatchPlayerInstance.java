package me.nallar.patched.world.tracking;

import java.util.HashSet;
import java.util.concurrent.ConcurrentLinkedQueue;

import me.nallar.tickthreading.patcher.Declare;
import net.minecraft.server.management.PlayerInstance;
import net.minecraft.server.management.PlayerManager;
import net.minecraft.tileentity.TileEntity;

public abstract class PatchPlayerInstance extends PlayerInstance {
	private ConcurrentLinkedQueue<TileEntity> tilesToUpdate;
	private static java.lang.reflect.Method getChunkWatcherWithPlayers;

	public PatchPlayerInstance(PlayerManager par1PlayerManager, int par2, int par3) {
		super(par1PlayerManager, par2, par3);
	}

	public void construct() {
		tilesToUpdate = new ConcurrentLinkedQueue<TileEntity>();
	}

	public void sendTiles() {
		HashSet<TileEntity> tileEntities = new HashSet<TileEntity>();
		for (TileEntity tileEntity = tilesToUpdate.poll(); tileEntity != null; tileEntity = tilesToUpdate.poll()) {
			tileEntities.add(tileEntity);
		}
		for (TileEntity tileEntity : tileEntities) {
			this.sendTileToAllPlayersWatchingChunk(tileEntity);
		}
		tileEntities.clear();
	}

	@Override
	@Declare
	public void updateTile(TileEntity tileEntity) {
		if (numberOfTilesToUpdate == 0) {
			this.myManager.playerUpdateLock.lock();
			try {
				this.myManager.getChunkWatcherWithPlayers().add(this);
			} finally {
				this.myManager.playerUpdateLock.unlock();
			}
			numberOfTilesToUpdate++;
		}
		tilesToUpdate.add(tileEntity);
	}
}
