package me.nallar.tickthreading.minecraft;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import cpw.mods.fml.common.FMLCommonHandler;
import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.collections.ConcurrentIterableArrayList;
import net.minecraft.profiler.Profiler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ThreadMinecraftServer;
import net.minecraft.util.AxisAlignedBB;

public final class ThreadManager {
	private static final Profiler profiler = MinecraftServer.getServer().theProfiler;
	private final boolean isServer = FMLCommonHandler.instance().getEffectiveSide().isServer();
	private final BlockingQueue<Runnable> taskQueue = new LinkedBlockingQueue<Runnable>();
	private final String namePrefix;
	private final Set<Thread> workThreads = new HashSet<Thread>();
	private final Object readyLock = new Object();
	private final AtomicInteger waiting = new AtomicInteger();
	private final Runnable killTask = new KillRunnable();
	public long endTime = 0;
	private final Runnable workerTask = new Runnable() {
		@SuppressWarnings ("FieldRepeatedlyAccessedInMethod")
		@Override
		public void run() {
			while (true) {
				try {
					Runnable runnable;
					synchronized (taskQueue) {
						runnable = taskQueue.take();
					}
					if (runnable == killTask) {
						workThreads.remove(Thread.currentThread());
						return;
					}
					runnable.run();
				} catch (InterruptedException ignored) {
				} catch (Exception e) {
					Log.severe("Unhandled exception in worker thread " + Thread.currentThread().getName(), e);
				}
				if (waiting.decrementAndGet() == 0) {
					endTime = System.nanoTime();
					synchronized (readyLock) {
						readyLock.notify();
					}
				}
			}
		}
	};

	private void newThread(String name) {
		Thread workThread = isServer ? new ServerWorkThread() : new Thread(workerTask);
		workThread.setName(name);
		workThread.setDaemon(true);
		workThread.start();
		workThreads.add(workThread);
	}

	public ThreadManager(int threads, String name) {
		namePrefix = name;
		addThreads(threads);
	}

	public long waitForCompletion() {
		profiler.startSection(namePrefix);
		synchronized (readyLock) {
			while (waiting.get() > 0) {
				try {
					readyLock.wait(1L);
				} catch (InterruptedException ignored) {
				}
			}
		}
		profiler.endSection();
		return endTime;
	}

	public void runList(final ConcurrentIterableArrayList<? extends Runnable> tasks) {
		tasks.reset();
		Runnable arrayRunnable = new Runnable() {
			@Override
			public void run() {
				Runnable r;
				while ((r = tasks.next()) != null) {
					r.run();
				}
				AxisAlignedBB.getAABBPool().cleanPool();
			}
		};
		for (int i = 0, len = workThreads.size(); i < len; i++) {
			run(arrayRunnable);
		}
	}

	public void run(Iterable<? extends Runnable> tasks) {
		for (Runnable runnable : tasks) {
			run(runnable);
		}
	}

	public void runAll(Runnable runnable) {
		for (int i = 0; i < size(); i++) {
			run(runnable);
		}
	}

	public void run(Runnable runnable) {
		if (taskQueue.add(runnable)) {
			waiting.incrementAndGet();
		} else {
			Log.severe("Failed to add " + runnable);
		}
	}

	private void addThreads(int number) {
		number += workThreads.size();
		for (int i = workThreads.size() + 1; i <= number; i++) {
			newThread(namePrefix + " - " + i);
		}
	}

	private void killThreads(int number) {
		for (int i = 0; i < number; i++) {
			taskQueue.add(killTask);
		}
	}

	public int size() {
		return workThreads.size();
	}

	public void stop() {
		killThreads(workThreads.size());
	}

	private static class KillRunnable implements Runnable {
		KillRunnable() {
		}

		@Override
		public void run() {
		}
	}

	private class ServerWorkThread extends ThreadMinecraftServer {
		public ServerWorkThread() {
			super(null, "");
		}

		@Override
		public void run() {
			workerTask.run();
		}
	}
}
