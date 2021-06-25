

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import org.apache.commons.lang.Validate;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/**
 * A whole class to create Guardian Beams by reflection </br>
 * Inspired by the API <a href="https://www.spigotmc.org/resources/guardianbeamapi.18329">GuardianBeamAPI</a></br>
 * <b>1.9 -> 1.17</b>
 *
 * @see <a href="https://github.com/SkytAsul/GuardianBeam">GitHub page</a>
 * @author SkytAsul
 */
public class Laser {
	private static int teamID = ThreadLocalRandom.current().nextInt(0, Integer.MAX_VALUE);
	
	private final int distanceSquared;
	private int duration;
	private boolean durationInTicks = false;
	private Location start;
	private Location end;

	private final Object createGuardianPacket;
	private final Object createSquidPacket;
	private final Object teamCreatePacket;
	private final Object[] destroyPackets;
	private final Object metadataPacketGuardian;
	private final Object metadataPacketSquid;
	private final Object fakeGuardianDataWatcher;

	private final Object squid;
	private final int squidID;
	private final UUID squidUUID;
	private final Object guardian;
	private final int guardianID;
	private final UUID guardianUUID;

	private Plugin plugin;
	private BukkitRunnable main;
	private BukkitTask startMove, endMove;
	private HashSet<Player> show = new HashSet<>();
	
	private List<Runnable> executeEnd = new ArrayList<>(1);

	/**
	 * Create a Laser instance
	 * @param start Location where laser will starts
	 * @param end Location where laser will ends
	 * @param duration Duration of laser in seconds (<i>-1 if infinite</i>)
	 * @param distance Distance where laser will be visible
	 */
	public Laser(Location start, Location end, int duration, int distance) throws ReflectiveOperationException {
		if (!Packets.enabled) throw new IllegalStateException("The Laser Beam API is disabled. An error has occured during initialization.");
		this.start = start;
		this.end = end;
		this.duration = duration;
		distanceSquared = distance * distance;

		if (Packets.version < 17) {
			squid = null;
			createSquidPacket = Packets.createPacketSquidSpawn(end);
		}else {
			squid = Packets.createSquid(end);
			createSquidPacket = Packets.createPacketEntitySpawn(squid);
		}
		squidID = (int) Packets.getField(createSquidPacket, "a");
		squidUUID = (UUID) Packets.getField(createSquidPacket, "b");
		metadataPacketSquid = Packets.createPacketMetadata(squidID, Packets.fakeSquidWatcher);
		Packets.setDirtyWatcher(Packets.fakeSquidWatcher);

		fakeGuardianDataWatcher = Packets.createFakeDataWatcher();
		Packets.initGuardianWatcher(fakeGuardianDataWatcher, squidID);
		if (Packets.version < 17) {
			guardian = null;
			createGuardianPacket = Packets.createPacketGuardianSpawn(start, fakeGuardianDataWatcher, squidID);
		}else {
			guardian = Packets.createGuardian(start);
			createGuardianPacket = Packets.createPacketEntitySpawn(guardian);
		}
		guardianID = (int) Packets.getField(createGuardianPacket, "a");
		guardianUUID = (UUID) Packets.getField(createGuardianPacket, "b");
		metadataPacketGuardian = Packets.createPacketMetadata(guardianID, fakeGuardianDataWatcher);

		teamCreatePacket = Packets.createPacketTeamCreate("noclip" + teamID++, squidUUID, guardianUUID);
		destroyPackets = Packets.createPacketsRemoveEntities(squidID, guardianID);
	}
	
	public Laser executeEnd(Runnable runnable) {
		executeEnd.add(runnable);
		return this;
	}

	public Laser durationInTicks() {
		duration *= 20;
		durationInTicks = true;
		return this;
	}
	
	public void start(Plugin plugin) {
		this.plugin = plugin;
		Validate.isTrue(main == null, "Task already started");
		main = new BukkitRunnable() {
			int time = 0;

			@Override
			public void run() {
				try {
					if (time == duration) {
						cancel();
						return;
					}
					if (!durationInTicks || time % 20 == 0) {
						for (Player p : start.getWorld().getPlayers()) {
							if (isCloseEnough(p.getLocation())) {
								if (!show.contains(p)) {
									sendStartPackets(p);
									show.add(p);
								}
							}else if (show.contains(p)) {
								Packets.sendPackets(p, destroyPackets);
								show.remove(p);
							}
						}
					}
					time++;
				}catch (ReflectiveOperationException e) {
					e.printStackTrace();
				}
			}

			@Override
			public synchronized void cancel() throws IllegalStateException {
				super.cancel();
				try {
					for (Player p : show) {
						Packets.sendPackets(p, destroyPackets);
					}
					executeEnd.forEach(Runnable::run);
				}catch (ReflectiveOperationException e) {
					e.printStackTrace();
				}
				main = null;
			}
		};
		main.runTaskTimerAsynchronously(plugin, 0L, durationInTicks ? 1L : 20L);
	}

	public void stop() {
		Validate.isTrue(main != null, "Task not started");
		main.cancel();
	}

	public void moveStart(Location location) throws ReflectiveOperationException {
		this.start = location;
		if (main != null) moveInternal(start, guardianID, guardian);
	}
	
	public void moveStart(Location location, int ticks, Runnable callback) {
		startMove = moveInternal(location, ticks, startMove, this::getStart, this::moveStart, callback);
	}

	public Location getStart() {
		return start;
	}

	public void moveEnd(Location location) throws ReflectiveOperationException {
		this.end = location;
		if (main != null) moveInternal(end, squidID, squid);
	}
	
	public void moveEnd(Location location, int ticks, Runnable callback) {
		endMove = moveInternal(location, ticks, endMove, this::getEnd, this::moveEnd, callback);
	}

	public Location getEnd() {
		return end;
	}
	
	private BukkitTask moveInternal(Location location, int ticks, BukkitTask oldTask, Supplier<Location> locationSupplier, ReflectiveConsumer<Location> moveConsumer, Runnable callback) {
		Validate.isTrue(ticks > 0);
		if (oldTask != null && !oldTask.isCancelled()) oldTask.cancel();
		return new BukkitRunnable() {
			double xPerTick = (location.getX() - locationSupplier.get().getX()) / ticks;
			double yPerTick = (location.getY() - locationSupplier.get().getY()) / ticks;
			double zPerTick = (location.getZ() - locationSupplier.get().getZ()) / ticks;
			int elapsed = 0;
			
			@Override
			public void run() {
				try {
					moveConsumer.accept(locationSupplier.get().add(xPerTick, yPerTick, zPerTick));
				}catch (ReflectiveOperationException e) {
					e.printStackTrace();
					cancel();
					return;
				}
				
				if (++elapsed == ticks) {
					cancel();
					if (callback != null) callback.run();
				}
			}
		}.runTaskTimer(plugin, 0L, 1L);
	}
	
	private void moveInternal(Location location, int entityId, Object fakeEntity) throws ReflectiveOperationException {
		Object packet;
		if (fakeEntity == null) {
			packet = Packets.createPacketMoveEntity(location, entityId);
		}else {
			Packets.moveFakeEntity(fakeEntity, location);
			packet = Packets.createPacketMoveEntity(fakeEntity);
		}
		for (Player p : show) {
			Packets.sendPackets(p, packet);
		}
	}

	public void callColorChange() throws ReflectiveOperationException{
		for (Player p : show) {
			Packets.sendPackets(p, metadataPacketGuardian);
		}
	}

	public boolean isStarted() {
		return main != null;
	}

	private void sendStartPackets(Player p) throws ReflectiveOperationException {
		Packets.sendPackets(p, createSquidPacket, createGuardianPacket);
		if (Packets.version > 14) {
			Packets.sendPackets(p, metadataPacketSquid, metadataPacketGuardian);
		}
		Packets.sendPackets(p, teamCreatePacket);
	}

	private boolean isCloseEnough(Location location) {
		return distanceSquared == -1 ||
				start.distanceSquared(location) <= distanceSquared ||
				end.distanceSquared(location) <= distanceSquared;
	}



	private static class Packets {
		private static int lastIssuedEID = 2000000000;

		static int generateEID() {
			return lastIssuedEID++;
		}

		private static String[] versions = Bukkit.getServer().getClass().getPackage().getName().replace(".", ",").split(",")[3].substring(1).split("_");
		private static int version = Integer.parseInt(versions[1]); // 1.X
		private static int versionMinor = Integer.parseInt(versions[2].substring(1)); // 1.X.Y
		private static String npack = "net.minecraft.server." + Bukkit.getServer().getClass().getPackage().getName().replace(".", ",").split(",")[3];
		private static String cpack = Bukkit.getServer().getClass().getPackage().getName() + ".";
		
		private static int squidID;
		private static int guardianID;
		
		private static Object watcherObject1; // invisilibity
		private static Object watcherObject2; // spikes
		private static Object watcherObject3; // attack id
		
		private static Constructor<?> watcherConstructor;
		private static Method watcherSet;
		private static Method watcherRegister;
		private static Method watcherDirty;
		
		private static Constructor<?> packetSpawn;
		private static Constructor<?> packetRemove;
		private static Constructor<?> packetTeleport;
		private static Constructor<?> packetMetadata;
		private static Class<?> packetTeam;
		
		private static Method createTeamPacket;
		private static Constructor<?> createTeam;
		private static Constructor<?> createScoreboard;
		private static Method setTeamPush;
		private static Object pushNever;
		private static Method getTeamPlayers;
		
		private static Method getHandle;
		private static Field playerConnection;
		private static Method sendPacket;
		private static Method setLocation;

		private static Object fakeSquid;
		private static Object fakeSquidWatcher;
		
		private static Object nmsWorld;
		
		public static boolean enabled = false;

		static {
			try {
				String watcherName1 = null, watcherName2 = null, watcherName3 = null;
				if (version < 13) {
					watcherName1 = "Z";
					watcherName2 = "bA";
					watcherName3 = "bB";
					squidID = 94;
					guardianID = 68;
				}else if (version == 13) {
					watcherName1 = "ac";
					watcherName2 = "bF";
					watcherName3 = "bG";
					squidID = 70;
					guardianID = 28;
				}else if (version == 14) {
					watcherName1 = "W";
					watcherName2 = "b";
					watcherName3 = "bD";
					squidID = 73;
					guardianID = 30;
				}else if (version == 15) {
					watcherName1 = "T";
					watcherName2 = "b";
					watcherName3 = "bA";
					squidID = 74;
					guardianID = 31;
				}else if (version == 16) {
					if (versionMinor < 2) {
						watcherName1 = "T";
						watcherName2 = "b";
						watcherName3 = "d";
						squidID = 74;
						guardianID = 31;
					}else {
						watcherName1 = "S";
						watcherName2 = "b";
						watcherName3 = "d";
						squidID = 81;
						guardianID = 31;
					}
				}else { // 1.17
					watcherName1 = "Z";
					watcherName2 = "b";
					watcherName3 = "e";
					squidID = 86;
					guardianID = 35;
				}
				Class<?> entityClass = getNMSClass("world.entity", "Entity");
				watcherObject1 = getField(entityClass, watcherName1, null);
				watcherObject2 = getField(getNMSClass("world.entity.monster", "EntityGuardian"), watcherName2, null);
				watcherObject3 = getField(getNMSClass("world.entity.monster", "EntityGuardian"), watcherName3, null);

				Class<?> dataWatcherClass = getNMSClass("network.syncher", "DataWatcher");
				watcherConstructor = dataWatcherClass.getDeclaredConstructor(entityClass);
				watcherSet = getMethod(dataWatcherClass, "set");
				watcherRegister = getMethod(dataWatcherClass, "register");
				if (version >= 15) watcherDirty = getMethod(dataWatcherClass, "markDirty");
				packetSpawn = getNMSClass("network.protocol.game", "PacketPlayOutSpawnEntityLiving").getDeclaredConstructor(version < 17 ? new Class<?>[0] : new Class<?>[] { getNMSClass("world.entity", "EntityLiving") });
				packetRemove = getNMSClass("network.protocol.game", "PacketPlayOutEntityDestroy").getDeclaredConstructor(version < 17 ? int[].class : int.class);
				packetMetadata = getNMSClass("network.protocol.game", "PacketPlayOutEntityMetadata").getDeclaredConstructor(int.class, dataWatcherClass, boolean.class);
				packetTeleport = getNMSClass("network.protocol.game", "PacketPlayOutEntityTeleport").getDeclaredConstructor(version < 17 ? new Class<?>[0] : new Class<?>[] { entityClass });
				packetTeam = getNMSClass("network.protocol.game", "PacketPlayOutScoreboardTeam");

				nmsWorld = Class.forName(cpack + "CraftWorld").getDeclaredMethod("getHandle").invoke(Bukkit.getWorlds().get(0));
				Object[] entityConstructorParams = version < 14 ? new Object[] { nmsWorld } : new Object[] { getNMSClass("world.entity", "EntityTypes").getDeclaredField(version < 17 ? "SQUID" : "aJ").get(null), nmsWorld };
				fakeSquid = getNMSClass("world.entity.animal", "EntitySquid").getDeclaredConstructors()[0].newInstance(entityConstructorParams);
				fakeSquidWatcher = createFakeDataWatcher();
				tryWatcherSet(fakeSquidWatcher, watcherObject1, (byte) 32);
				
				getHandle = Class.forName(cpack + "entity.CraftPlayer").getDeclaredMethod("getHandle");
				playerConnection = getNMSClass("server.level", "EntityPlayer").getDeclaredField(version < 17 ? "playerConnection" : "b");
				sendPacket = getNMSClass("server.network", "PlayerConnection").getMethod("sendPacket", getNMSClass("network.protocol", "Packet"));
				
				if (version >= 17) {
					setLocation = entityClass.getDeclaredMethod("setLocation", double.class, double.class, double.class, float.class, float.class);
					
					createTeamPacket = packetTeam.getMethod("a", getNMSClass("world.scores", "ScoreboardTeam"), boolean.class);
					
					Class<?> scoreboardClass = getNMSClass("world.scores", "Scoreboard");
					Class<?> teamClass = getNMSClass("world.scores", "ScoreboardTeam");
					Class<?> pushClass = getNMSClass("world.scores", "ScoreboardTeamBase$EnumTeamPush");
					createTeam = teamClass.getDeclaredConstructor(scoreboardClass, String.class);
					createScoreboard = scoreboardClass.getDeclaredConstructor();
					setTeamPush = teamClass.getDeclaredMethod("setCollisionRule", pushClass);
					pushNever = pushClass.getDeclaredField("b").get(null);
					getTeamPlayers = teamClass.getDeclaredMethod("getPlayerNameSet");
				}
				
				enabled = true;
			}catch (ReflectiveOperationException e) {
				e.printStackTrace();
				System.err.println("Laser Beam reflection failed to initialize. The util is disabled. Please ensure your version (" + Bukkit.getServer().getClass().getPackage().getName() + ") is supported.");
			}
		}

		public static void sendPackets(Player p, Object... packets) throws ReflectiveOperationException {
			Object connection = playerConnection.get(getHandle.invoke(p));
			for (Object packet : packets) {
				sendPacket.invoke(connection, packet);
			}
		}

		public static Object createFakeDataWatcher() throws ReflectiveOperationException {
			Object watcher = watcherConstructor.newInstance(fakeSquid);
			if (version > 13) setField(watcher, "registrationLocked", false);
			return watcher;
		}

		public static void setDirtyWatcher(Object watcher) throws ReflectiveOperationException {
			if (version >= 15) watcherDirty.invoke(watcher, watcherObject1);
		}
		
		public static Object createSquid(Location location) throws ReflectiveOperationException {
			Object entity = getNMSClass("world.entity.animal", "EntitySquid").getDeclaredConstructors()[0].newInstance(getNMSClass("world.entity", "EntityTypes").getDeclaredField("aJ").get(null), nmsWorld);
			moveFakeEntity(entity, location);
			return entity;
		}
		
		public static Object createGuardian(Location location) throws ReflectiveOperationException {
			Object entity = getNMSClass("world.entity.monster", "EntityGuardian").getDeclaredConstructors()[0].newInstance(getNMSClass("world.entity", "EntityTypes").getDeclaredField("K").get(null), nmsWorld);
			moveFakeEntity(entity, location);
			return entity;
		}

		public static Object createPacketSquidSpawn(Location location) throws ReflectiveOperationException {
			Object packet = packetSpawn.newInstance();
			setField(packet, "a", generateEID());
			setField(packet, "b", UUID.randomUUID());
			setField(packet, "c", squidID);
			setField(packet, "d", location.getX());
			setField(packet, "e", location.getY());
			setField(packet, "f", location.getZ());
			setField(packet, "j", (byte) (location.getYaw() * 256.0F / 360.0F));
			setField(packet, "k", (byte) (location.getPitch() * 256.0F / 360.0F));
			if (version <= 14) setField(packet, "m", fakeSquidWatcher);
			return packet;
		}
		
		public static Object createPacketEntitySpawn(Object entity) throws ReflectiveOperationException {
			return packetSpawn.newInstance(entity);
		}
		
		public static void initGuardianWatcher(Object watcher, int squidId) throws ReflectiveOperationException {
			tryWatcherSet(watcher, watcherObject1, (byte) 32);
			tryWatcherSet(watcher, watcherObject2, false);
			tryWatcherSet(watcher, watcherObject3, squidId);
		}

		public static Object createPacketGuardianSpawn(Location location, Object watcher, int squidId) throws ReflectiveOperationException {
			Object packet = packetSpawn.newInstance();
			setField(packet, "a", generateEID());
			setField(packet, "b", UUID.randomUUID());
			setField(packet, "c", guardianID);
			setField(packet, "d", location.getX());
			setField(packet, "e", location.getY());
			setField(packet, "f", location.getZ());
			setField(packet, "j", (byte) (location.getYaw() * 256.0F / 360.0F));
			setField(packet, "k", (byte) (location.getPitch() * 256.0F / 360.0F));
			if (version <= 14) setField(packet, "m", watcher);
			return packet;
		}

		public static Object[] createPacketsRemoveEntities(int... entitiesId) throws ReflectiveOperationException {
			Object[] packets;
			if (version < 17) {
				packets = new Object[] { packetRemove.newInstance(entitiesId) };
			}else {
				packets = new Object[entitiesId.length];
				for (int i = 0; i < entitiesId.length; i++) {
					packets[i] = packetRemove.newInstance(entitiesId[i]);
				}
			}
			return packets;
		}

		public static Object createPacketMoveEntity(Location location, int entityId) throws ReflectiveOperationException {
			Object packet = packetTeleport.newInstance();
			setField(packet, "a", entityId);
			setField(packet, "b", location.getX());
			setField(packet, "c", location.getY());
			setField(packet, "d", location.getZ());
			setField(packet, "e", (byte) (location.getYaw() * 256.0F / 360.0F));
			setField(packet, "f", (byte) (location.getPitch() * 256.0F / 360.0F));
			setField(packet, "g", true);
			return packet;
		}
		
		public static void moveFakeEntity(Object entity, Location location) throws ReflectiveOperationException {
			setLocation.invoke(entity, location.getX(), location.getY(), location.getZ(), location.getPitch(), location.getYaw());
		}
		
		public static Object createPacketMoveEntity(Object entity) throws ReflectiveOperationException {
			return packetTeleport.newInstance(entity);
		}
		
		public static Object createPacketTeamCreate(String teamName, UUID squidUUID, UUID guardianUUID) throws ReflectiveOperationException {
			Object packet;
			if (version < 17) {
				packet = packetTeam.newInstance();
				setField(packet, "a", teamName);
				setField(packet, "i", 0);
				setField(packet, "f", "never");
				Collection<String> players = (Collection<String>) getField(packetTeam, "h", packet);
				players.add(squidUUID.toString());
				players.add(guardianUUID.toString());
			}else {
				Object team = createTeam.newInstance(createScoreboard.newInstance(), teamName);
				setTeamPush.invoke(team, pushNever);
				Collection<String> players = (Collection<String>) getTeamPlayers.invoke(team);
				players.add(squidUUID.toString());
				players.add(guardianUUID.toString());
				packet = createTeamPacket.invoke(null, team, true);
			}
			return packet;
		}

		private static Object createPacketMetadata(int entityId, Object watcher) throws ReflectiveOperationException {
			return packetMetadata.newInstance(entityId, watcher, false);
		}

		private static void tryWatcherSet(Object watcher, Object watcherObject, Object watcherData) throws ReflectiveOperationException {
			try {
				watcherSet.invoke(watcher, watcherObject, watcherData);
			}catch (InvocationTargetException ex) {
				watcherRegister.invoke(watcher, watcherObject, watcherData);
				if (version >= 15) watcherDirty.invoke(watcher, watcherObject);
			}
		}

		/* Reflection utils */
		private static Method getMethod(Class<?> clazz, String name) {
			for (Method m : clazz.getDeclaredMethods()) {
				if (m.getName().equals(name)) return m;
			}
			return null;
		}

		private static void setField(Object instance, String name, Object value) throws ReflectiveOperationException {
			Validate.notNull(instance);
			Field field = instance.getClass().getDeclaredField(name);
			field.setAccessible(true);
			field.set(instance, value);
		}

		private static Object getField(Class<?> clazz, String name, Object instance) throws ReflectiveOperationException {
			Field field = clazz.getDeclaredField(name);
			field.setAccessible(true);
			return field.get(instance);
		}
		
		private static Object getField(Object instance, String name) throws ReflectiveOperationException {
			Field field = instance.getClass().getDeclaredField(name);
			field.setAccessible(true);
			return field.get(instance);
		}
		
		private static Class<?> getNMSClass(String package17, String className) throws ClassNotFoundException {
			return Class.forName((version < 17 ? npack : "net.minecraft." + package17) + "." + className);
		}
	}
	
	@FunctionalInterface
	public static interface ReflectiveConsumer<T> {
		abstract void accept(T t) throws ReflectiveOperationException;
	}
}
