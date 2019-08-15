# GuardianBeam
An util to create easily Guardians Lasers via Packets and Reflection. No ProtocolLib, compatible from Minecraft 1.9 to Minecraft 1.14!

Inspired by the plugin GuardianBeamAPI by [Jaxon A Brown](https://www.spigotmc.org/resources/authors/merpg.33142/), who uses ProtocolLib (https://www.spigotmc.org/resources/guardianbeamapi.18329/)

There is a [tutorial on SpigotMC](https://www.spigotmc.org/threads/tutorial-laser-guardian-beam.348901/)

![Static laser image](https://github.com/SkytAsul/GuardianBeam/blob/master/Beam.gif?raw=true)

## How to use ?
First, copy the [Laser.java class](https://github.com/SkytAsul/GuardianBeam/blob/master/Laser.java) to your project.

Then, it's extremely simple:

1. Create Location objects of where do you want your laser starts and ends.
2. Create a Laser instance: `new Laser(locationStart, locationEnd, duration, visibleDistance)` - duration is the time (in seconds) when laser will be visible (if you set it to -1, the laser will exists infinitely), and visibleDistance is the amount of blocks where your laser will be visible.
3. After this, call the method `laser.start(plugin);` - where "plugin" parameter is the instance of your JavaPlugin class.
4. TA-DAAAM ! Your laser is created and shown to near players !
5. You can move the laser with the methods `laser.moveStart(newLocation);` and `laser.moveEnd(newLocation);
6. To remove your laser before his end duration, just call `laser.stop();`

## Demo
Here is something I quickly made to show what you can do with this API: a ray-gun.

![Moving laser animation](https://github.com/SkytAsul/GuardianBeam/blob/master/Moving%20Beam.gif?raw=true)

You can see the system in action [on this video](https://youtu.be/NSYMKsPBdMM), and the class is available [here](https://github.com/SkytAsul/GuardianBeam/blob/master/LaserDemo.java).
