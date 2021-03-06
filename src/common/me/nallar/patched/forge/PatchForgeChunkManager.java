package me.nallar.patched.forge;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimap;

import cpw.mods.fml.common.FMLLog;
import me.nallar.tickthreading.collections.ForcedChunksRedirectMap;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.MathHelper;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeChunkManager;

public abstract class PatchForgeChunkManager extends ForgeChunkManager {
	public static void staticConstruct() {
		forcedChunks = new ForcedChunksRedirectMap();
	}

	public static ImmutableSetMultimap<ChunkCoordIntPair, Ticket> getPersistentChunksFor(World world) {
		return world.forcedChunks;
	}

	static void saveWorld(World world) {
		// only persist persistent worlds
		if (!(world instanceof WorldServer)) {
			return;
		}
		WorldServer worldServer = (WorldServer) world;
		File chunkDir = worldServer.getChunkSaveLocation();
		File chunkLoaderData = new File(chunkDir, "forcedchunks.dat");

		NBTTagCompound forcedChunkData = new NBTTagCompound();
		NBTTagList ticketList = new NBTTagList();
		forcedChunkData.setTag("TicketList", ticketList);

		Multimap<String, Ticket> ticketSet = tickets.get(worldServer);
		for (String modId : ticketSet.keySet()) {
			NBTTagCompound ticketHolder = new NBTTagCompound();
			ticketList.appendTag(ticketHolder);

			ticketHolder.setString("Owner", modId);
			NBTTagList tickets = new NBTTagList();
			ticketHolder.setTag("Tickets", tickets);

			for (Ticket tick : ticketSet.get(modId)) {
				if (tick == null) {
					continue;
				}
				NBTTagCompound ticket = new NBTTagCompound();
				ticket.setByte("Type", (byte) tick.getType().ordinal());
				ticket.setByte("ChunkListDepth", (byte) tick.getChunkListDepth());
				if (tick.isPlayerTicket()) {
					ticket.setString("ModId", tick.getModId());
					ticket.setString("Player", tick.getPlayerName());
				}
				if (tick.getModData() != null) {
					ticket.setCompoundTag("ModData", tick.getModData());
				}
				Entity e = tick.getType() == Type.ENTITY ? tick.getEntity() : null;
				if (e != null && e.addEntityID(new NBTTagCompound())) {
					ticket.setInteger("chunkX", MathHelper.floor_double(e.chunkCoordX));
					ticket.setInteger("chunkZ", MathHelper.floor_double(e.chunkCoordZ));
					ticket.setLong("PersistentIDMSB", e.getPersistentID().getMostSignificantBits());
					ticket.setLong("PersistentIDLSB", e.getPersistentID().getLeastSignificantBits());
					tickets.appendTag(ticket);
				} else if (tick.getType() != Type.ENTITY) {
					tickets.appendTag(ticket);
				}
			}
		}
		try {
			CompressedStreamTools.write(forcedChunkData, chunkLoaderData);
		} catch (IOException e) {
			FMLLog.log(Level.WARNING, e, "Unable to write forced chunk data to %s - chunkloading won't work", chunkLoaderData.getAbsolutePath());
		}
	}
}
