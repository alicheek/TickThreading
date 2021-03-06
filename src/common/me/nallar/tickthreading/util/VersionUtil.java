package me.nallar.tickthreading.util;

import net.minecraft.server.MinecraftServer;

public enum VersionUtil {
	;

	public static String versionString() {
		String version = "";
		try {
			MinecraftServer minecraftServer = MinecraftServer.getServer();
			if (minecraftServer != null) {
				version = " on " + minecraftServer.getMinecraftVersion() + ' ' + minecraftServer.getServerModName();
			}
		} catch (NoClassDefFoundError ignored) {
		}
		return TTVersionString() + version;
	}

	public static String TTVersionString() {
		return "@MOD_NAME@ v@MOD_VERSION@ for MC@MC_VERSION@";
	}
}
