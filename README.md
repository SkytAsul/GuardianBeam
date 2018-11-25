# GuardianBeam
An util to create easily Guardians Lasers via Packets and Reflection. No ProtocolLib, compatible Minecraft 1.9 -> 1.13

Inspired by the plugin GuardianBeamAPI by [Jaxon A Brown](https://www.spigotmc.org/resources/authors/merpg.33142/), who uses ProtocolLib (https://www.spigotmc.org/resources/guardianbeamapi.18329/)

There is a [tutorial on SpigotMC](https://www.spigotmc.org/threads/tutorial-laser-guardian-beam.348901/)

## How to use ?
First, copy the [Laser.java class](https://github.com/SkytAsul/GuardianBeam/blob/master/Laser.java) to your project.

Then, it's extremely simple:

1. Create Location objects of where do you want your laser starts and ends.
2. Create a Laser instance: new Laser(locationStart, locationEnd, duration, visibleDistance) - duration is the time (in seconds) when laser will be visible (if you set it to -1, the laser will exists infinitely), and visibleDistance is the amount of blocks where your laser will be visible.
3. After this, call the method laser.start(plugin); - where "plugin" parameter is the instance of your JavaPlugin class.
4. TA-DAAAM ! Your laser is created and showed to near players !
5. To remove your laser before his end duration, just call laser.stop();
