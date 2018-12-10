import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.UUID;
import javax.annotation.Nullable;
import org.apache.commons.lang3.Validate;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
/**
 * A whole class to create Guardian Beams by reflection </br>
 * Inspired by the API <a href="https://www.spigotmc.org/resources/guardianbeamapi.18329">GuardianBeamAPI</a></br>
 * <b>1.9 -> 1.13</b>
 *
 * @see <a href="https://github.com/SkytAsul/GuardianBeam">GitHub page</a>
 * @author SkytAsul
 */
public class Laser {
    private final int duration;
    private final int distanceSquared;
    private final Location start;
    private final Location end;
 
    private final Object createGuardianPacket;
    private final Object createSquidPacket;
    private final Object destroyPacket;
 
    private final int squid;
    private final int guardian;
 
    private BukkitRunnable run;
 
    /**
    * Create a Laser instance
    * @param start Location where laser will starts
    * @param end Location where laser will ends
    * @param duration Duration of laser in seconds (<i>-1 if infinite</i>)
    * @param distance Distance where laser will be visible
    */
    public Laser(Location start, Location end, int duration, int distance) throws ReflectiveOperationException{
        this.start = start;
        this.end = end;
        this.duration = duration;
        distanceSquared = distance*distance;
    
        createSquidPacket = Packets.createPacketSquidSpawn(end);
        squid = (int) Packets.getField(Packets.packetSpawn, "a", createSquidPacket);
        createGuardianPacket = Packets.createPacketGuardianSpawn(start, squid);
        guardian = (int) Packets.getField(Packets.packetSpawn, "a", createGuardianPacket);
    
        destroyPacket = Packets.createPacketRemoveEntities(squid, guardian);
    }
    
    public void start(Plugin plugin){
        Validate.isTrue(run == null, "Task already started");
        run = new BukkitRunnable() {
            int time = duration;
            HashSet<Player> show = new HashSet<>();
            @Override
            public void run() {
                try {
                    if (time == 0){
                        cancel();
                        return;
                    }
                    for (Player p : start.getWorld().getPlayers()){
                        if (isCloseEnough(p.getLocation())){
                            if (!show.contains(p)){
                                sendStartPackets(p);
                                show.add(p);
                            }
                        }else if (show.contains(p)){
                            Packets.sendPacket(p, destroyPacket);
                            show.remove(p);
                        }
                    }
                    if (time != -1) time--;
                }catch (ReflectiveOperationException e) {
                    e.printStackTrace();
                }
            }
        
            @Override
            public synchronized void cancel() throws IllegalStateException {
                super.cancel();
                try {
                    for (Player p : show){
                        Packets.sendPacket(p, destroyPacket);
                    }
                }catch (ReflectiveOperationException e) {
                    e.printStackTrace();
                }
                run = null;
            }
        };
        run.runTaskTimer(plugin, 0L, 20L);
    }
 
    public void stop(){
        Validate.isTrue(run != null, "Task not started");
        run.cancel();
    }
 
    public boolean isStarted(){
        return run != null;
    }
 
    private void sendStartPackets(Player p) throws ReflectiveOperationException{
        Packets.sendPacket(p, createSquidPacket);
        Packets.sendPacket(p, createGuardianPacket);
    }
 
    private boolean isCloseEnough(Location location) {
        return start.distanceSquared(location) <= distanceSquared ||
                end.distanceSquared(location) <= distanceSquared;
    }
 
 
 
    private static class Packets{
        private static int lastIssuedEID = 2000000000;
        static int generateEID() {
            return lastIssuedEID++;
        }
        private static int version = Integer.parseInt(Bukkit.getServer().getClass().getPackage().getName().replace(".", ",").split(",")[3].substring(1).split("_")[1]);
        private static String npack = "net.minecraft.server." + Bukkit.getServer().getClass().getPackage().getName().replace(".", ",").split(",")[3] + ".";
        private static String cpack = Bukkit.getServer().getClass().getPackage().getName() + ".";
        private static Object fakeSquid;
        private static Method watcherSet;
        private static Method watcherRegister;
        private static Class<?> packetSpawn;
        static{
            try {
                fakeSquid = Class.forName(cpack + "entity.CraftSquid").getDeclaredConstructors()[0].newInstance(
                        null, Class.forName(npack + "EntitySquid").getDeclaredConstructors()[0].newInstance(
                                new Object[]{null}));
                watcherSet = getMethod(Class.forName(npack + "DataWatcher"), "set");
                watcherRegister = getMethod(Class.forName(npack + "DataWatcher"), "register");
                packetSpawn = Class.forName(npack + "PacketPlayOutSpawnEntityLiving");
            }catch (ReflectiveOperationException e) {
                e.printStackTrace();
            }
        }
    
        public static void sendPacket(Player p, Object packet) throws ReflectiveOperationException{
            Object entityPlayer = Class.forName(cpack + "entity.CraftPlayer").getDeclaredMethod("getHandle").invoke(p);
            Object playerConnection = entityPlayer.getClass().getDeclaredField("playerConnection").get(entityPlayer);
            playerConnection.getClass().getDeclaredMethod("sendPacket", Class.forName(npack + "Packet")).invoke(playerConnection, packet);
        }
        public static Object createPacketSquidSpawn(Location location) throws ReflectiveOperationException {
            Object packet = packetSpawn.newInstance();
            setField(packet, "a", generateEID());
            setField(packet, "b", UUID.randomUUID());
            setField(packet, "c", version < 13 ? 94 : 70);
            setField(packet, "d", location.getX());
            setField(packet, "e", location.getY());
            setField(packet, "f", location.getZ());
            setField(packet, "j", (byte) (location.getYaw() * 256.0F / 360.0F));
            setField(packet, "k", (byte) (location.getPitch() * 256.0F / 360.0F));
            Object nentity = fakeSquid.getClass().getDeclaredMethod("getHandle").invoke(fakeSquid);
            Object watcher = Class.forName(npack + "Entity").getDeclaredMethod("getDataWatcher").invoke(nentity);
            watcherSet.invoke(watcher, getField(Class.forName(npack + "Entity"), version < 13 ? "Z" : "ac", null), (byte) 32);
            setField(packet, "m", watcher);
            return packet;
        }
        public static Object createPacketGuardianSpawn(Location location, int entityId) throws ReflectiveOperationException {
            Object packet = packetSpawn.newInstance();
            setField(packet, "a", generateEID());
            setField(packet, "b", UUID.randomUUID());
            setField(packet, "c", version < 13 ? 68 : 28);
            setField(packet, "d", location.getX());
            setField(packet, "e", location.getY());
            setField(packet, "f", location.getZ());
            setField(packet, "j", (byte) (location.getYaw() * 256.0F / 360.0F));
            setField(packet, "k", (byte) (location.getPitch() * 256.0F / 360.0F));
            Object nentity = fakeSquid.getClass().getDeclaredMethod("getHandle").invoke(fakeSquid);
            Object watcher = Class.forName(npack + "Entity").getDeclaredMethod("getDataWatcher").invoke(nentity);
            watcherSet.invoke(watcher, getField(Class.forName(npack + "Entity"), version < 13 ? "Z" : "ac", null), (byte) 32);
            try{
                watcherSet.invoke(watcher, getField(Class.forName(npack + "EntityGuardian"), version < 13 ? "bA" : "bF", null), false);
            }catch (InvocationTargetException ex){
                watcherRegister.invoke(watcher, getField(Class.forName(npack + "EntityGuardian"), version < 13 ? "bA" : "bF", null), false);
            }
            try{
                watcherSet.invoke(watcher, getField(Class.forName(npack + "EntityGuardian"), version < 13 ? "bB" : "bG", null), entityId);
            }catch (InvocationTargetException ex){
                watcherRegister.invoke(watcher, getField(Class.forName(npack + "EntityGuardian"), version < 13 ? "bB" : "bG", null), entityId);
            }
            setField(packet, "m", watcher);
            return packet;
        }
        public static Object createPacketRemoveEntities(int squidId, int guardianId) throws ReflectiveOperationException {
            Object packet = Class.forName(npack + "PacketPlayOutEntityDestroy").newInstance(); //new PacketPlayOutEntityDestroy();
            setField(packet, "a", new int[]{squidId, guardianId});
            return packet;
        }
        private static Method getMethod(Class<?> clazz, String name){
            for (Method m : clazz.getDeclaredMethods()){
                if (m.getName().equals(name)) return m;
            }
            return null;
        }
        private static void setField(Object instance, String name, Object value) throws ReflectiveOperationException{
            Validate.notNull(instance);
            Field field = instance.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(instance, value);
        }
        private static Object getField(Class<?> clazz, String name, @Nullable Object instance) throws ReflectiveOperationException{
            Field field = clazz.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(instance);
        }
    }
}
