package fr.skytasul.guardianbeam;

import fr.skytasul.reflection.MappedReflectionAccessor;
import fr.skytasul.reflection.ReflectionAccessor;
import fr.skytasul.reflection.ReflectionAccessor.ClassAccessor;
import fr.skytasul.reflection.TransparentReflectionAccessor;
import fr.skytasul.reflection.Version;
import fr.skytasul.reflection.mappings.files.MappingFileReader;
import fr.skytasul.reflection.mappings.files.ProguardMapping;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * A whole class to create Guardian Lasers and Ender Crystal Beams using packets and reflection.<br>
 * Inspired by the API
 * <a href="https://www.spigotmc.org/resources/guardianbeamapi.18329">GuardianBeamAPI</a><br>
 * <b>1.17 -> 1.21.6</b>
 *
 * @see <a href="https://github.com/SkytAsul/GuardianBeam">GitHub repository</a>
 * @version 2.4.3
 * @author SkytAsul
 */
public abstract class Laser {

	protected final int distanceSquared;
	protected final int duration;
	protected boolean durationInTicks = false;
	protected Location start;
	protected Location end;

	protected Plugin plugin;
	protected BukkitRunnable main;

	protected BukkitTask startMove;
	protected BukkitTask endMove;

	protected Set<Player> show = ConcurrentHashMap.newKeySet();
	private Set<Player> seen = new HashSet<>();

	private List<Runnable> executeEnd = new ArrayList<>(1);

	protected Laser(Location start, Location end, int duration, int distance) {
		Packets.ensureInitialized();

		if (start.getWorld() != end.getWorld()) throw new IllegalArgumentException("Locations do not belong to the same worlds.");
		this.start = start.clone();
		this.end = end.clone();
		this.duration = duration;
		distanceSquared = distance < 0 ? -1 : distance * distance;
	}

	/**
	 * Adds a runnable to execute when the laser reaches its final duration
	 * @param runnable action to execute
	 * @return this {@link Laser} instance
	 */
	public Laser executeEnd(Runnable runnable) {
		executeEnd.add(runnable);
		return this;
	}

	/**
	 * Makes the duration provided in the constructor passed as ticks and not seconds
	 * @return this {@link Laser} instance
	 */
	public Laser durationInTicks() {
		durationInTicks = true;
		return this;
	}

	/**
	 * Starts this laser.
	 * <p>
	 * It will make the laser visible for nearby players and start the countdown to the final duration.
	 * <p>
	 * Once finished, it will destroy the laser and execute all runnables passed with {@link Laser#executeEnd}.
	 * @param plugin plugin used to start the task
	 */
	public void start(Plugin plugin) {
		if (main != null) throw new IllegalStateException("Task already started");
		this.plugin = plugin;
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
							if (isCloseEnough(p)) {
								if (show.add(p)) {
									sendStartPackets(p, !seen.add(p));
								}
							}else if (show.remove(p)) {
								sendDestroyPackets(p);
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
				main = null;
				try {
					for (Player p : show) {
						sendDestroyPackets(p);
					}
					show.clear();
					executeEnd.forEach(Runnable::run);
				}catch (ReflectiveOperationException e) {
					e.printStackTrace();
				}
			}
		};
		main.runTaskTimerAsynchronously(plugin, 0L, durationInTicks ? 1L : 20L);
	}

	/**
	 * Stops this laser.
	 * <p>
	 * This will destroy the laser for every player and start execute all runnables passed with {@link Laser#executeEnd}
	 */
	public void stop() {
		if (main == null) throw new IllegalStateException("Task not started");
		main.cancel();
	}

	/**
	 * Gets laser status.
	 * @return	<code>true</code> if the laser is currently running
	 * 			(i.e. {@link #start} has been called and the duration is not over)
	 */
	public boolean isStarted() {
		return main != null;
	}

	/**
	 * Gets laser type.
	 * @return LaserType enum constant of this laser
	 */
	public abstract LaserType getLaserType();

	/**
	 * Instantly moves the start of the laser to the location provided.
	 * @param location New start location
	 * @throws ReflectiveOperationException if a reflection exception occurred during laser moving
	 */
	public abstract void moveStart(Location location) throws ReflectiveOperationException;

	/**
	 * Instantly moves the end of the laser to the location provided.
	 * @param location New end location
	 * @throws ReflectiveOperationException if a reflection exception occurred during laser moving
	 */
	public abstract void moveEnd(Location location) throws ReflectiveOperationException;

	/**
	 * Gets the start location of the laser.
	 * @return where exactly is the start position of the laser located
	 */
	public Location getStart() {
		return start.clone();
	}

	/**
	 * Gets the end location of the laser.
	 * @return where exactly is the end position of the laser located
	 */
	public Location getEnd() {
		return end.clone();
	}

	/**
	 * Moves the start of the laser smoothly to the new location, within a given time.
	 * @param location New start location to go to
	 * @param ticks Duration (in ticks) to make the move
	 * @param callback {@link Runnable} to execute at the end of the move (nullable)
	 */
	public void moveStart(Location location, int ticks, Runnable callback) {
		startMove = moveInternal(location, ticks, startMove, getStart(), this::moveStart, callback);
	}

	/**
	 * Moves the end of the laser smoothly to the new location, within a given time.
	 * @param location New end location to go to
	 * @param ticks Duration (in ticks) to make the move
	 * @param callback {@link Runnable} to execute at the end of the move (nullable)
	 */
	public void moveEnd(Location location, int ticks, Runnable callback) {
		endMove = moveInternal(location, ticks, endMove, getEnd(), this::moveEnd, callback);
	}

	private BukkitTask moveInternal(Location location, int ticks, BukkitTask oldTask, Location from,
			ReflectiveConsumer<Location> moveConsumer, Runnable callback) {
		if (ticks <= 0)
			throw new IllegalArgumentException("Ticks must be a positive value");
		if (plugin == null)
			throw new IllegalStateException("The laser must have been started a least once");
		if (oldTask != null && !oldTask.isCancelled())
			oldTask.cancel();
		return new BukkitRunnable() {
			double xPerTick = (location.getX() - from.getX()) / ticks;
			double yPerTick = (location.getY() - from.getY()) / ticks;
			double zPerTick = (location.getZ() - from.getZ()) / ticks;
			Location loc = from.clone();
			int elapsed = 0;

			@Override
			public void run() {
				try {
					loc.add(xPerTick, yPerTick, zPerTick);
					moveConsumer.accept(loc);
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

	protected void moveFakeEntity(Location location, Object fakeEntity) throws ReflectiveOperationException {
		if (fakeEntity != null) Packets.moveFakeEntity(fakeEntity, location);
		if (main == null) return;

		Object packet = Packets.createPacketMoveEntity(fakeEntity);
		for (Player p : show) {
			Packets.sendPackets(p, packet);
		}
	}

	protected abstract void sendStartPackets(Player p, boolean hasSeen) throws ReflectiveOperationException;

	protected abstract void sendDestroyPackets(Player p) throws ReflectiveOperationException;

	protected boolean isCloseEnough(Player player) {
		if (distanceSquared == -1) return true;
		Location location = player.getLocation();
		return	getStart().distanceSquared(location) <= distanceSquared ||
				getEnd().distanceSquared(location) <= distanceSquared;
	}

	public static class GuardianLaser extends Laser {
		private static AtomicInteger teamID = new AtomicInteger(ThreadLocalRandom.current().nextInt(0, Integer.MAX_VALUE));

		private final UUID squidUUID = UUID.randomUUID();
		private final int squidID = Packets.generateEID();
		private Object squid;
		private Object squidData;
		private Object createSquidPacket;
		private Object metadataPacketSquid;

		private final UUID guardianUUID = UUID.randomUUID();
		private final int guardianID = Packets.generateEID();
		private Object guardian;
		private Object guardianData;
		private Object createGuardianPacket;
		private Object metadataPacketGuardian;

		private int targetID;
		private UUID targetUUID;

		private Object teamCreatePacket;
		private Object[] destroyPackets;

		protected LivingEntity endEntity;

		private Location correctStart;
		private Location correctEnd;

		/**
		 * Creates a new Guardian Laser instance
		* @param start Location where laser will starts
		* @param end Location where laser will ends
		* @param duration Duration of laser in seconds (<i>-1 if infinite</i>)
		* @param distance Distance where laser will be visible (<i>-1 if infinite</i>)
		* @throws ReflectiveOperationException if a reflection exception occurred during Laser creation
		* @see Laser#start(Plugin) to start the laser
		* @see #durationInTicks() to make the duration in ticks
		* @see #executeEnd(Runnable) to add Runnable-s to execute when the laser will stop
		* @see #GuardianLaser(Location, LivingEntity, int, int) to create a laser which follows an entity
		 */
		public GuardianLaser(Location start, Location end, int duration, int distance) throws ReflectiveOperationException {
			super(start, end, duration, distance);

			initSquid();
			initLaser();
			setTargetEntity(squidUUID, squidID);
		}

		/**
		 * Creates a new Guardian Laser instance
		* @param start Location where laser will starts
		* @param endEntity Entity who the laser will follow
		* @param duration Duration of laser in seconds (<i>-1 if infinite</i>)
		* @param distance Distance where laser will be visible (<i>-1 if infinite</i>)
		* @throws ReflectiveOperationException if a reflection exception occurred during Laser creation
		* @see Laser#start(Plugin) to start the laser
		* @see #durationInTicks() to make the duration in ticks
		* @see #executeEnd(Runnable) to add Runnable-s to execute when the laser will stop
		* @see #GuardianLaser(Location, Location, int, int) to create a laser with a specific end location
		 */
		public GuardianLaser(Location start, LivingEntity endEntity, int duration, int distance) throws ReflectiveOperationException {
			super(start, endEntity.getLocation(), duration, distance);

			initLaser();
			setTargetEntity(endEntity.getUniqueId(), endEntity.getEntityId());
		}

		private void initLaser() throws ReflectiveOperationException {
			guardian = Packets.createGuardian(getCorrectStart(), guardianUUID, guardianID);
			guardianData = Packets.getEntityData(guardian);

			teamCreatePacket = Packets.createPacketTeamCreate("noclip" + teamID.getAndIncrement(), squidUUID, guardianUUID);
			destroyPackets = Packets.createPacketsRemoveEntities(squidID, guardianID);
		}

		private void initSquid() throws ReflectiveOperationException {
			squid = Packets.createSquid(getCorrectEnd(), squidUUID, squidID);
			squidData = Packets.getEntityData(squid);
			metadataPacketSquid = Packets.createPacketMetadata(squidID, squidData);
		}

		private Object getGuardianSpawnPacket() throws ReflectiveOperationException {
			if (createGuardianPacket == null)
				createGuardianPacket = Packets.createPacketEntitySpawnLiving(guardian);
			return createGuardianPacket;
		}

		private Object getSquidSpawnPacket() throws ReflectiveOperationException {
			if (createSquidPacket == null)
				createSquidPacket = Packets.createPacketEntitySpawnLiving(squid);
			return createSquidPacket;
		}

		@Override
		public LaserType getLaserType() {
			return LaserType.GUARDIAN;
		}

		/**
		 * Makes the laser follow an entity (moving end location).
		 *
		 * This is done client-side by making the fake guardian follow the existing entity.
		 * Hence, there is no consuming of server resources.
		 *
		 * @param entity living entity the laser will follow
		 * @throws ReflectiveOperationException if a reflection operation fails
		 */
		public void attachEndEntity(LivingEntity entity) throws ReflectiveOperationException {
			if (entity.getWorld() != start.getWorld()) throw new IllegalArgumentException("Attached entity is not in the same world as the laser.");
			this.endEntity = entity;
			setTargetEntity(entity.getUniqueId(), entity.getEntityId());
		}

		public Entity getEndEntity() {
			return endEntity;
		}

		private void setTargetEntity(UUID uuid, int id) throws ReflectiveOperationException {
			targetUUID = uuid;
			targetID = id;

			Packets.setGuardianTarget(guardianData, targetID);
			metadataPacketGuardian = Packets.createPacketMetadata(guardianID, guardianData);

			for (Player p : show) {
				Packets.sendPackets(p, metadataPacketGuardian);
			}
		}

		@Override
		public Location getEnd() {
			return endEntity == null ? end : endEntity.getLocation();
		}

		protected Location getCorrectStart() {
			if (correctStart == null) {
				correctStart = start.clone();
				correctStart.subtract(0, 0.5, 0);
			}
			return correctStart;
		}

		protected Location getCorrectEnd() {
			if (correctEnd == null) {
				correctEnd = end.clone();
				correctEnd.subtract(0, 0.5, 0);

				Vector corrective = correctEnd.toVector().subtract(getCorrectStart().toVector()).normalize();
				if (Double.isNaN(corrective.getX())) corrective.setX(0);
				if (Double.isNaN(corrective.getY())) corrective.setY(0);
				if (Double.isNaN(corrective.getZ())) corrective.setZ(0);
				// coordinates can be NaN when start and end are stricly equals
				correctEnd.subtract(corrective);

			}
			return correctEnd;
		}

		@Override
		protected boolean isCloseEnough(Player player) {
			return player == endEntity || super.isCloseEnough(player);
		}

		@Override
		protected void sendStartPackets(Player p, boolean hasSeen) throws ReflectiveOperationException {
			if (squid == null) {
				Packets.sendPackets(p,
						getGuardianSpawnPacket(),
						metadataPacketGuardian);
			}else {
				Packets.sendPackets(p,
						getGuardianSpawnPacket(),
						getSquidSpawnPacket(),
						metadataPacketGuardian,
						metadataPacketSquid);
			}

			if (!hasSeen) Packets.sendPackets(p, teamCreatePacket);
		}

		@Override
		protected void sendDestroyPackets(Player p) throws ReflectiveOperationException {
			Packets.sendPackets(p, destroyPackets);
		}

		@Override
		public void moveStart(Location location) throws ReflectiveOperationException {
			this.start = location.clone();
			correctStart = null;

			createGuardianPacket = null; // will force re-generation of spawn packet
			moveFakeEntity(getCorrectStart(), guardian);

			if (squid != null) {
				correctEnd = null;
				createSquidPacket = null;
				moveFakeEntity(getCorrectEnd(), squid);
			}
		}

		@Override
		public void moveEnd(Location location) throws ReflectiveOperationException {
			this.end = location.clone();
			createSquidPacket = null; // will force re-generation of spawn packet
			correctEnd = null;

			if (squid == null) {
				initSquid();
				for (Player p : show) {
					Packets.sendPackets(p, getSquidSpawnPacket(), metadataPacketSquid);
				}
			}else {
				moveFakeEntity(getCorrectEnd(), squid);
			}
			if (targetUUID != squidUUID) {
				endEntity = null;
				setTargetEntity(squidUUID, squidID);
			}
		}

		/**
		 * Asks viewers' clients to change the color of this laser
		 * @throws ReflectiveOperationException
		 */
		public void callColorChange() throws ReflectiveOperationException {
			for (Player p : show) {
				Packets.sendPackets(p, metadataPacketGuardian);
			}
		}

	}

	public static class CrystalLaser extends Laser {

		private Object createCrystalPacket;
		private Object metadataPacketCrystal;
		private Object[] destroyPackets;

		private final Object crystal;
		private final int crystalID = Packets.generateEID();
		private final Object crystalWatcher;

		/**
		 * Creates a new Ender Crystal Laser instance
		* @param start Location where laser will starts. The Crystal laser do not handle decimal number, it will be rounded to blocks.
		* @param end Location where laser will ends. The Crystal laser do not handle decimal number, it will be rounded to blocks.
		* @param duration Duration of laser in seconds (<i>-1 if infinite</i>)
		* @param distance Distance where laser will be visible (<i>-1 if infinite</i>)
		* @throws ReflectiveOperationException if a reflection exception occurred during Laser creation
		* @see #start(Plugin) to start the laser
		* @see #durationInTicks() to make the duration in ticks
		* @see #executeEnd(Runnable) to add Runnable-s to execute when the laser will stop
		 */
		public CrystalLaser(Location start, Location end, int duration, int distance) throws ReflectiveOperationException {
			super(start, new Location(end.getWorld(), end.getBlockX(), end.getBlockY(), end.getBlockZ()), duration,
					distance);

			crystal = Packets.createCrystal(start, UUID.randomUUID(), crystalID);
			crystalWatcher = Packets.getEntityData(crystal);
			Packets.setCrystalTarget(crystalWatcher, end);
			metadataPacketCrystal = Packets.createPacketMetadata(crystalID, crystalWatcher);

			destroyPackets = Packets.createPacketsRemoveEntities(crystalID);
		}

		private Object getCrystalSpawnPacket() throws ReflectiveOperationException {
			if (createCrystalPacket == null)
				createCrystalPacket = Packets.createPacketEntitySpawnNormal(crystal);
			return createCrystalPacket;
		}

		@Override
		public LaserType getLaserType() {
			return LaserType.ENDER_CRYSTAL;
		}

		@Override
		protected void sendStartPackets(Player p, boolean hasSeen) throws ReflectiveOperationException {
			Packets.sendPackets(p, getCrystalSpawnPacket());
			Packets.sendPackets(p, metadataPacketCrystal);
		}

		@Override
		protected void sendDestroyPackets(Player p) throws ReflectiveOperationException {
			Packets.sendPackets(p, destroyPackets);
		}

		@Override
		public void moveStart(Location location) throws ReflectiveOperationException {
			this.start = location.clone();
			createCrystalPacket = null; // will force re-generation of spawn packet
			moveFakeEntity(start, crystal);
		}

		@Override
		public void moveEnd(Location location) throws ReflectiveOperationException {
			location = new Location(location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ());

			if (end.equals(location))
				return;

			this.end = location;
			if (main != null) {
				Packets.setCrystalTarget(crystalWatcher, location);
				metadataPacketCrystal = Packets.createPacketMetadata(crystalID, crystalWatcher);
				for (Player p : show) {
					Packets.sendPackets(p, metadataPacketCrystal);
				}
			}
		}

	}

	public enum LaserType {
		/**
		 * Represents a laser from a Guardian entity.
		 * <p>
		 * It can be pointed to precise locations and
		 * can track entities smoothly using {@link GuardianLaser#attachEndEntity(LivingEntity)}
		 */
		GUARDIAN,

		/**
		 * Represents a laser from an Ender Crystal entity.
		 * <p>
		 * Start and end locations are automatically rounded to integers (block locations).
		 */
		ENDER_CRYSTAL;

		/**
		 * Creates a new Laser instance, {@link GuardianLaser} or {@link CrystalLaser} depending on this enum value.
		* @param start Location where laser will starts
		* @param end Location where laser will ends
		* @param duration Duration of laser in seconds (<i>-1 if infinite</i>)
		* @param distance Distance where laser will be visible
		* @throws ReflectiveOperationException if a reflection exception occurred during Laser creation
		* @see Laser#start(Plugin) to start the laser
		* @see Laser#durationInTicks() to make the duration in ticks
		* @see Laser#executeEnd(Runnable) to add Runnable-s to execute when the laser will stop
		 */
		public Laser create(Location start, Location end, int duration, int distance) throws ReflectiveOperationException {
			switch (this) {
			case ENDER_CRYSTAL:
				return new CrystalLaser(start, end, duration, distance);
			case GUARDIAN:
				return new GuardianLaser(start, end, duration, distance);
			}
			throw new IllegalStateException();
		}
	}

	protected static class Packets {
		private static AtomicInteger lastIssuedEID = new AtomicInteger(2000000000);

		static int generateEID() {
			return lastIssuedEID.getAndIncrement();
		}

		private static Logger logger;
		private static String cpack;
		private static Version version;

		private static boolean isEnabled = false;
		private static boolean hasInitialized = false;
		private static Throwable initializationError = null;

		private static Object squidType;
		private static Object guardianType;

		private static Constructor<?> crystalConstructor;
		private static Constructor<?> squidConstructor;
		private static Constructor<?> guardianConstructor;

		private static Object dataAccessorFlags;
		private static Object dataAccessorGuardianMoving;
		private static Object dataAccessorGuardianTarget;
		private static Object dataAccessorCrystalTarget;
		private static Object dataAccessorCrystalBottom;

		private static ClassAccessor dataWatcherClass;
		private static ClassAccessor dataAccessorClass;
		private static Method watcherSet;
		private static Method watcherDirty;
		private static Method watcherPack;

		private static Constructor<?> blockPositionConstructor;

		private static Constructor<?> packetSpawnLiving;
		private static Constructor<?> packetSpawnNormal;
		private static Constructor<?> packetRemove;
		private static Constructor<?> packetTeleport;
		private static Method packetTeleportOf;
		private static Constructor<?> packetMetadata;
		private static ClassAccessor packetTeam;

		private static Method createTeamPacket;
		private static Constructor<?> createTeam;
		private static Constructor<?> createScoreboard;
		private static Method setTeamPush;
		private static Object pushNever;
		private static Method getTeamPlayers;

		private static Method getPlayerHandle;
		private static Field playerConnection;
		private static Method sendPacket;

		private static Method getData;
		private static Field entityBlockPosition;
		private static Method setLocation;
		private static Method setUUID;
		private static Method setID;

		private static Object nmsWorld;

		protected static void ensureInitialized() {
			if (!hasInitialized)
				initialize();

			if (!isEnabled)
				throw new IllegalStateException(
						"The GuardianBeam API is disabled. An error has occured during first initialization.",
						initializationError);
		}

		private static void initialize() {
			hasInitialized = true;
			try {
				logger = new Logger("GuardianBeam", null) {
					@Override
					public void log(LogRecord logRecord) {
						logRecord.setMessage("[GuardianBeam] " + logRecord.getMessage());
						super.log(logRecord);
					}
				};
				logger.setParent(Bukkit.getServer().getLogger());
				logger.setLevel(Level.ALL);

				// e.g. Bukkit.getBukkitVersion() -> 1.17.1-R0.1-SNAPSHOT
				var versionString = Bukkit.getBukkitVersion().split("-R")[0];
				var serverVersion = Version.parse(versionString);
				logger.info("Found server version " + serverVersion);

				cpack = Bukkit.getServer().getClass().getPackage().getName() + ".";

				boolean remapped = Bukkit.getServer().getClass().getPackage().getName().split("\\.").length == 3;
				ReflectionAccessor reflection;

				if (remapped) {
					version = serverVersion;
					reflection = new TransparentReflectionAccessor();
					logger.info("Loaded transparent mappings.");
				} else {
					var mappingsFile =
							new String(Laser.class.getResourceAsStream("mappings/spigot.txt").readAllBytes());
					var mappingsReader = new MappingFileReader(new ProguardMapping(false), mappingsFile.lines().toList());
					mappingsReader.readAvailableVersions();
					var foundVersion = mappingsReader.keepBestMatchedVersion(serverVersion);

					if (foundVersion.isEmpty())
						throw new UnsupportedOperationException("Cannot find mappings to match server version");

					if (!foundVersion.get().is(serverVersion))
						logger.warning("Loaded not matching version of the mappings for your server version");

					version = foundVersion.get();
					mappingsReader.parseMappings();
					var mappings = mappingsReader.getParsedMappings(foundVersion.get());
					logger.info("Loaded mappings for " + version);
					reflection = new MappedReflectionAccessor(mappings);
				}

				loadReflection(reflection, version);

				isEnabled = true;
			} catch (Exception ex) {
				initializationError = ex;

				String errorMsg =
						"Lasers reflection failed to initialize. The util is disabled. Please ensure your version ("
								+ Bukkit.getBukkitVersion() + ") is supported.";
				if (logger == null) {
					ex.printStackTrace();
					System.err.println(errorMsg);
				} else {
					logger.log(Level.SEVERE, errorMsg, ex);
				}
			}
		}

		protected static void loadReflection(@NotNull ReflectionAccessor reflection, @NotNull Version version)
				throws ReflectiveOperationException {
			var entityTypesClass = getNMSClass(reflection, "world.entity", "EntityType");
			var entityClass = getNMSClass(reflection, "world.entity", "Entity");
			var crystalClass = getNMSClass(reflection, "world.entity.boss.enderdragon", "EndCrystal");
			var squidClass = getNMSClass(reflection, "world.entity.animal", "Squid");
			var guardianClass = getNMSClass(reflection, "world.entity.monster", "Guardian");
			var blockPosClass = getNMSClass(reflection, "core", "BlockPos");
			dataAccessorFlags = entityClass.getField("DATA_SHARED_FLAGS_ID").get(null);
			dataAccessorGuardianMoving = guardianClass.getField("DATA_ID_MOVING").get(null);
			dataAccessorGuardianTarget = guardianClass.getField("DATA_ID_ATTACK_TARGET").get(null);
			dataAccessorCrystalTarget = crystalClass.getField("DATA_BEAM_TARGET").get(null);
			dataAccessorCrystalBottom = crystalClass.getField("DATA_SHOW_BOTTOM").get(null);

			squidType = entityTypesClass.getField("SQUID").get(null);
			guardianType = entityTypesClass.getField("GUARDIAN").get(null);

			dataWatcherClass = getNMSClass(reflection, "network.syncher", "SynchedEntityData");
			dataAccessorClass = getNMSClass(reflection, "network.syncher", "EntityDataAccessor");
			if (version.isAfter(1, 19, 4)) {
				watcherSet = dataWatcherClass.getMethodInstance("set", dataAccessorClass, Object.class, boolean.class);
				watcherPack = dataWatcherClass.getMethodInstance("packDirty");
			} else {
				watcherSet = dataWatcherClass.getMethodInstance("set", dataAccessorClass, Object.class);
				if (cpack != null)
					watcherDirty = dataWatcherClass.getClassInstance().getDeclaredMethod("markDirty",
							dataAccessorClass.getClassInstance());
			}
			packetSpawnNormal = getNMSClass(reflection, "network.protocol.game", "ClientboundAddEntityPacket")
					.getConstructorInstance(
							version.isBefore(1, 21, 0) ? new Type[] {entityClass}
									: new Type[] {entityClass, int.class, blockPosClass});
			if (version.isBefore(1, 19, 0))
				packetSpawnLiving = getNMSClass(reflection, "network.protocol.game", "ClientboundAddMobPacket")
						.getConstructorInstance(getNMSClass(reflection, "world.entity", "LivingEntity"));
			packetRemove = version.is(1, 17, 0)
					? getNMSClass(reflection, "network.protocol.game", "ClientboundRemoveEntityPacket")
							.getConstructorInstance(int.class)
					: getNMSClass(reflection, "network.protocol.game", "ClientboundRemoveEntitiesPacket")
							.getConstructorInstance(int[].class);
			packetMetadata = getNMSClass(reflection, "network.protocol.game", "ClientboundSetEntityDataPacket")
					.getConstructorInstance(version.isBefore(1, 19, 3)
							? new Type[] {int.class, dataWatcherClass, boolean.class}
							: new Type[] {int.class, List.class});
			if (version.isBefore(1, 21, 2)) {
				packetTeleport = getNMSClass(reflection, "network.protocol.game", "ClientboundTeleportEntityPacket")
						.getConstructorInstance(entityClass);
			} else {
				packetTeleportOf = getNMSClass(reflection, "network.protocol.game", "ClientboundEntityPositionSyncPacket")
						.getMethodInstance("of", entityClass);
			}

			blockPositionConstructor =
					getNMSClass(reflection, "core", "BlockPos").getConstructorInstance(int.class, int.class, int.class);

			var levelClass = getNMSClass(reflection, "world.level", "Level");

			squidConstructor = squidClass.getConstructorInstance(entityTypesClass, levelClass);
			guardianConstructor = guardianClass.getConstructorInstance(entityTypesClass, levelClass);
			crystalConstructor = crystalClass.getConstructorInstance(levelClass, double.class, double.class, double.class);

			playerConnection = getNMSClass(reflection, "server.level", "ServerPlayer").getFieldInstance("connection");
			var packetListenerClass = getNMSClass(reflection, "server.network", version.isAfter(1, 20, 2)
					? "ServerCommonPacketListenerImpl"
					: "ServerGamePacketListenerImpl");
			sendPacket =
					packetListenerClass.getMethodInstance("send", getNMSClass(reflection, "network.protocol", "Packet"));

			getData = entityClass.getMethodInstance("getEntityData");
			setLocation = entityClass.getMethodInstance(version.isAfter(1, 21, 5) ? "absSnapTo" : "absMoveTo", double.class,
					double.class, double.class, float.class, float.class);
			entityBlockPosition = entityClass.getFieldInstance("blockPosition");
			setUUID = entityClass.getMethodInstance("setUUID", UUID.class);
			setID = entityClass.getMethodInstance("setId", int.class);

			var scoreboardClass = getNMSClass(reflection, "world.scores", "Scoreboard");
			var teamClass = getNMSClass(reflection, "world.scores", "PlayerTeam");
			var pushClass = getNMSClass(reflection, "world.scores", "Team$CollisionRule");
			packetTeam = getNMSClass(reflection, "network.protocol.game", "ClientboundSetPlayerTeamPacket");
			createTeamPacket = packetTeam.getMethodInstance("createAddOrModifyPacket", teamClass, boolean.class);
			createTeam = teamClass.getConstructorInstance(scoreboardClass, String.class);
			createScoreboard = scoreboardClass.getConstructorInstance();
			setTeamPush = teamClass.getMethodInstance("setCollisionRule", pushClass);
			pushNever = pushClass.getField("NEVER").get(null);
			getTeamPlayers = teamClass.getMethodInstance("getPlayers");

			if (cpack != null) {
				getPlayerHandle = Class.forName(cpack + "entity.CraftPlayer").getDeclaredMethod("getHandle");

				nmsWorld = Class.forName(cpack + "CraftWorld").getDeclaredMethod("getHandle")
						.invoke(Bukkit.getWorlds().get(0));
			}
		}

		public static void sendPackets(Player p, Object... packets) throws ReflectiveOperationException {
			Object connection = playerConnection.get(getPlayerHandle.invoke(p));
			for (Object packet : packets) {
				if (packet == null) continue;
				sendPacket.invoke(connection, packet);
			}
		}

		public static Object createSquid(Location location, UUID uuid, int id) throws ReflectiveOperationException {
			Object entity = squidConstructor.newInstance(squidType, nmsWorld);
			setEntityIDs(entity, uuid, id);
			moveFakeEntity(entity, location);

			Object data = getEntityData(entity);
			setEntityData(data, dataAccessorFlags, (byte) 32);

			return entity;
		}

		public static Object createGuardian(Location location, UUID uuid, int id) throws ReflectiveOperationException {
			Object entity = guardianConstructor.newInstance(guardianType, nmsWorld);
			setEntityIDs(entity, uuid, id);
			moveFakeEntity(entity, location);
			return entity;
		}

		public static Object createCrystal(Location location, UUID uuid, int id) throws ReflectiveOperationException {
			Object entity = crystalConstructor.newInstance(nmsWorld, location.getX(), location.getY(), location.getZ());
			setEntityIDs(entity, uuid, id);
			return entity;
		}

		public static Object getEntityData(Object entity) throws ReflectiveOperationException {
			return getData.invoke(entity);
		}

		public static Object createPacketEntitySpawnLiving(Object entity) throws ReflectiveOperationException {
			if (packetSpawnLiving == null) // after 1.19
				return createPacketEntitySpawnNormal(entity);
			return packetSpawnLiving.newInstance(entity);
		}

		public static Object createPacketEntitySpawnNormal(Object entity) throws ReflectiveOperationException {
			if (version.isAfter(1, 21, 0)) {
				Object entityPos = entityBlockPosition.get(entity);
				return packetSpawnNormal.newInstance(entity, 0, entityPos);
			}
			return packetSpawnNormal.newInstance(entity);
		}

		public static void setGuardianTarget(Object watcher, int targetId) throws ReflectiveOperationException {
			setEntityData(watcher, dataAccessorGuardianTarget, targetId);
			// yes we re-set the values for other watchers EVERY TIME
			// otherwise packets would miss data and players that come
			// in range to see the entity would not see the base options
			setEntityData(watcher, dataAccessorFlags, (byte) 32);
			setEntityData(watcher, dataAccessorGuardianMoving, Boolean.FALSE);
		}

		public static void setCrystalTarget(Object watcher, Location target) throws ReflectiveOperationException {
			Object blockPosition =
					blockPositionConstructor.newInstance(target.getBlockX(), target.getBlockY(), target.getBlockZ());
			setEntityData(watcher, dataAccessorCrystalTarget, Optional.of(blockPosition));
			// same as above
			setEntityData(watcher, dataAccessorCrystalBottom, Boolean.FALSE);
		}

		public static Object[] createPacketsRemoveEntities(int... entitiesId) throws ReflectiveOperationException {
			Object[] packets;
			if (version.is(1, 17, 0)) {
				packets = new Object[entitiesId.length];
				for (int i = 0; i < entitiesId.length; i++) {
					packets[i] = packetRemove.newInstance(entitiesId[i]);
				}
			}else {
				packets = new Object[] { packetRemove.newInstance(entitiesId) };
			}
			return packets;
		}

		public static void setEntityIDs(Object entity, UUID uuid, int id) throws ReflectiveOperationException {
			setUUID.invoke(entity, uuid);
			setID.invoke(entity, id);
		}

		public static void moveFakeEntity(Object entity, Location location) throws ReflectiveOperationException {
			setLocation.invoke(entity, location.getX(), location.getY(), location.getZ(), location.getPitch(), location.getYaw());
		}

		public static Object createPacketMoveEntity(Object entity) throws ReflectiveOperationException {
			if (version.isBefore(1, 21, 2))
				return packetTeleport.newInstance(entity);
			else
				return packetTeleportOf.invoke(null, entity);
		}

		public static Object createPacketTeamCreate(String teamName, UUID... entities) throws ReflectiveOperationException {
			Object team = createTeam.newInstance(createScoreboard.newInstance(), teamName);
			setTeamPush.invoke(team, pushNever);
			@SuppressWarnings("unchecked")
			var players = (Collection<String>) getTeamPlayers.invoke(team);
			for (UUID entity : entities)
				players.add(entity.toString());
			return createTeamPacket.invoke(null, team, true);
		}

		private static Object createPacketMetadata(int entityId, Object watcher) throws ReflectiveOperationException {
			if (version.isBefore(1, 19, 3)) {
				return packetMetadata.newInstance(entityId, watcher, false);
			} else {
				return packetMetadata.newInstance(entityId, watcherPack.invoke(watcher));
			}
		}

		private static void setEntityData(Object watcher, Object watcherObject, Object watcherData)
				throws ReflectiveOperationException {
			if (version.isAfter(1, 19, 3)) {
				watcherSet.invoke(watcher, watcherObject, watcherData, true);
			} else {
				watcherSet.invoke(watcher, watcherObject, watcherData);
				watcherDirty.invoke(watcher, watcherObject);
			}
		}

		private static @NotNull ClassAccessor getNMSClass(@NotNull ReflectionAccessor reflection, @NotNull String className)
				throws ClassNotFoundException {
			return reflection.getClass("net.minecraft." + className);
		}

		private static @NotNull ClassAccessor getNMSClass(@NotNull ReflectionAccessor reflection, @NotNull String nmPackage,
				@NotNull String className) throws ClassNotFoundException {
			return reflection.getClass("net.minecraft." + nmPackage + "." + className);
		}
	}

	@FunctionalInterface
	public static interface ReflectiveConsumer<T> {
		abstract void accept(T t) throws ReflectiveOperationException;
	}

}
