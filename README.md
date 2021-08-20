# GuardianBeam
An util to create easily Guardians and Ender Crystal Lasers via Packets and Reflection. No ProtocolLib, compatible from Minecraft 1.9 to Minecraft 1.17.1!

Inspired by the plugin GuardianBeamAPI by [Jaxon A Brown](https://www.spigotmc.org/resources/authors/merpg.33142/), which uses ProtocolLib (https://www.spigotmc.org/resources/guardianbeamapi.18329/)

There is a [tutorial on SpigotMC](https://www.spigotmc.org/threads/tutorial-laser-guardian-beam.348901/)

![Static laser gif](https://github.com/SkytAsul/GuardianBeam/blob/master/Beam.gif?raw=true)

## How to use ?
First, copy the [Laser.java class](https://github.com/SkytAsul/GuardianBeam/blob/master/src/main/java/fr/skytasul/guardianbeam/Laser.java) to your project.

Then, it's extremely simple:

1. Create Location objects of where do you want your laser starts and ends.
2. Create a Laser instance: `new GuardianLaser(locationStart, locationEnd, duration, visibleDistance)` - duration is the time (in seconds) when laser will be visible (if you set it to -1, the laser will exist infinitely), and visibleDistance is the amount of blocks where your laser will be visible. You can also use `new CrystalLaser(...)` to create an Ender Crystal laser.
3. After this, call the method `laser.start(plugin);` - where "plugin" parameter is the instance of your JavaPlugin class.
4. TA-DAAAM ! Your laser is created and shown to near players !
5. You can move the laser with the methods `laser.moveStart(newLocation);` and `laser.moveEnd(newLocation);`
6. To remove your laser before his end duration, just call `laser.stop();`

## Demo
Here is something I quickly made to show what you can do with this API: a ray-gun.

![Moving laser animation](https://github.com/SkytAsul/GuardianBeam/blob/master/Moving%20Beam.gif?raw=true)

You can see the system in action [on this video](https://youtu.be/NSYMKsPBdMM), and the class is available [here](https://github.com/SkytAsul/GuardianBeam/blob/master/LaserDemo.java).

## Advanced usage
### Animations
The `Laser#moveStart(Location location, int ticks, Runnable callback` and `Laser#moveEnd(Location location, int ticks, Runnable callback)` methods can be used to make the laser move smoothly from one point to another.

Quick preview of the smooth movement:

![Smooth laser animation](https://github.com/SkytAsul/GuardianBeam/blob/master/Smooth%20Moving%20Beam.gif?raw=true)

### End runnable
If you want to execute some actions when the laser comes to its end, use the `Laser#executeEnd(Runnable runnable)` method.

i.e.:
```java
new Laser(start, end, 10, 60).executeEnd(() -> Bukkit.broadcastMessage("Laser ended!")).start(plugin);
```
This will start a laser for 10 seconds, after that the message "Laser ended!" will be broadcasted to users.

### Duration in ticks
The duration passed into the `new Laser(Location start, Location end, int duration, int distance)` constructor is in seconds. If you want it to be in ticks, call `Laser#durationInTicks()`.

i.e.:
```java
new Laser(start, end, 10, 60).durationInTicks().start(plugin);
```
This will start a laser for 10 ticks.

## Troubleshooting
Sometimes, Guardian beams only renders as bubbles, the moving color part is invisible.
It is not caused by this util but by a [Minecraft bug](https://bugs.mojang.com/browse/MC-165595).

It happens when your world gets too old (when its game time value reaches 2800000).
The only way to fix it is to open the `level.dat` file with a [NBT editor](https://github.com/jaquadro/NBTExplorer), and edit manually the `Data.Time` field to a lower value. Save the file, and start your server.