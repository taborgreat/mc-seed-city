# Installable NPC builds

- `java/seedcity-0.0.1.jar`: compiled Java mod, including all six NPCs, Creative spawn eggs and showcase helpers.
- `java/seedcity-0.0.1-sources.jar`: matching Java source archive for integration.
- `Seed-City-NPCs.mcaddon`: separate Bedrock resource/behavior add-on. Server-tested integration candidate; client visuals still need review.

Java requires Minecraft 26.2, Fabric Loader 0.19.5 and Fabric API 0.160.0+26.2.
Install only the compiled mod JAR in `mods`, not the sources JAR. Obtain Fabric API
from its [official releases](https://github.com/FabricMC/fabric/releases).

Read the [integration notes](../docs/mob-showcase-v1.md) and
[five-page PDF](../docs/Tabor-Integration-Guide.pdf). Tabor must review the complete
integration code, including BuildTask.java, not only copy model assets.

Includes the Java playtest correction: Rectifier walks with gravity at iron-golem
stroll speed, retaining repair logic and no protector AI. Saved flying Rectifiers
migrate on load. Restart Minecraft to apply the updated JAR. All 34 server tests passed.

Also includes connected Rectifier shoulders and corrected leg seams, a deeper
Sentinel body, player-style build/repair swings, quiet silverfish Rat sounds about
two minutes apart, and stronger Java creeper avoidance. Java integration now requires
the Rat avoidance goal and creeper mixin registration. Bedrock cloth uses native
Molang animations with no additional cloth mod. All seven Java client tests passed;
the Bedrock server avoidance check reached 16.93 blocks of separation.
