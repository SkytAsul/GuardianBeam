# GuardianBeam
An util to create easily Guardians Lasers via Packets and Reflection. No ProtocolLib, compatible Minecraft 1.9 -> 1.12

Inspired by the plugin GuardianBeamAPI by Jaxon A Brown, who uses ProtocolLib (https://www.spigotmc.org/resources/guardianbeamapi.18329/)

## How to use ?

It's extremely simple:

1. Create Location objects of where do you want your laser starts and ends.
2. Create a Laser instance: new Laser(locationStart, locationEnd, duration, visibleDistance) - duration is the time (in seconds) when laser will be visible (if you set it to -1, the laser will exists infinitely), and visibleDistance is the amount of blocks where your laser will be visible.
3. After this, call the method laser.start(plugin); - where "plugin" parameter is the instance of your JavaPlugin class.
4. TA-DAAAM ! Your laser is created and showed to near players !
5. To remove your laser before his end duration, just call laser.stop();
