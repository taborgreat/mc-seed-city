# Rectifier: integration notes

## Java playtest correction: grounded by default

WardenEntity now extends PathfinderMob directly. It uses gravity, ordinary ground
navigation and the iron golem's movement attribute (0.25) and stroll multiplier (0.6),
without inheriting IronGolem or any village-defense/combat goals. The Builder still flies.
Unbound Rectifiers wander; assigned workers keep district patrol, damage scanning and
BuildTask repair. City spawns require clear supported floor outside the Core pillars.
Repair waypoints use reachable standing positions and wait for the worker to be within
four blocks before placing; unreachable work times out instead of being repaired remotely.
Follow range is 48 to cover district routes. Existing saved NoGravity/movement values
are migrated when loaded. Grounded landing/walking and city repair tests pass.
The optional fly pose remains a presentation-only exhibit, not default movement.
Restart the Java client/server to apply this code update to an existing showcase save.

The former Warden now displays as **Rectifier** and uses a custom stone-and-copper
repair guardian model inspired by the second character in the supplied lineup.
Builder's approved assets remain unchanged.

## Take together

- `src/client/java/net/tabor/seedcity/client/RectifierMesh.java`, `RectifierModel.java`,
  `RectifierRenderState.java`, `RectifierRenderer.java` and updated `SeedCityClient.java`.
- `src/main/resources/assets/seedcity/textures/entity/rectifier.png` and
  `rectifier_glow.png`, plus `assets/seedcity/lang/en_us.json` for the display name.
- `src/main/java/net/tabor/seedcity/entity/WardenEntity.java`: one synchronized
  presentation flag exposes the existing REPAIR phase to clients. It does not
  replace the existing patrol, damage detection, BuildTask repair or respawn logic.
- `src/gametest/java/net/tabor/seedcity/gametest/RectifierClientTest.java` and the
  client test entrypoint in `src/gametest/resources/fabric.mod.json`.

The registry ID **`seedcity:warden`**, Java entity class, configuration keys, district
data remain unchanged for save and code compatibility. The revised hitbox is
1.4 by 2.95 blocks to accommodate the taller model.
Use `/summon seedcity:warden ~ ~ ~` to spawn the Rectifier. Minecraft's own Warden
is untouched. The unused old placeholder WardenRenderer remains available in source;
the client entrypoint now registers RectifierRenderer instead.

**This fork still contains more than models.** Upstream was missing BuildTask.java;
the separate replacement and `.gitignore` fix are commit `0406d80`. Read
`npc-builder-merge-notes.md` and compare Tabor's original local BuildTask implementation
before merging that prerequisite. Use matching client/server builds for the new flag.

## Model and animation

86 cuboids in eleven parts, approximately 2.94 blocks tall. Revision 4 follows the
close-up reference silhouette: long armored legs, compact inset chest/core with
thick flanking plates, exposed copper shoulder blocks, larger gauntlets and a
narrow tabard. Earlier broad slab-like chest and shoulder lids have been removed.
The user's requested club hammer replaces the wrench visible in the reference.
Its striking faces run front-to-back in the repair arm's swing plane. Following
explicit clarification, revision 5 holds the hammer head-up above the closed fist,
ready to strike, with no independent sideways roll. A separate tool-side forearm
joint bends the elbow and gives the raised hammer clearance from the gauntlet. Added details include chest
edge plates, waist fittings/rivets, gauntlet ridges, fingers and thumbs, shin panels,
boot trim and a stepped gold tabard motif. Copper patina uses smaller patches.
The articulated forward-held lantern and player-height marker remain.

The palette uses stone armor, weathered
copper shoulders and greaves, a red-brown tabard, an amber visor and chest core,
a hanging lantern and a club hammer. The base/emission atlases are 1024 square pixels
with a logical 256-square UV layout. Armor offsets avoid coplanar surface fighting.

The head independently follows Minecraft's look state. The existing repair code
already looks toward the damaged block. Repair mode moves the hammer arm; the
lantern counter-rotates with both arm joints and gently swings. Emission remains visible at
night but does not cast light, need shaders, or brighten dynamically during repairs.
Tool motion is continuous during REPAIR, not timed to individual block placements.

Existing flight is preserved. The optional grounded walk cycle is implemented in
the model, but no ground navigation was added. Preview Look around demonstrates the
head joint; it does not add random-looking or nearby-player attention AI to the mob.

## Editing, preview and checks

`tools/build_rectifier_assets.py` is the authoring source. Run it with Python and
Pillow to regenerate Java, PNGs, geometry JSON, Blockbench mesh and offline HTML.
It checks for coplanar overlapping outward faces within each rigid part. The
Blockbench file embeds the texture; animation remains in RectifierModel.java and
is mirrored by `art/rectifier/preview.js`. The preview uses the existing vendored
Three.js bundle, embeds every dependency, and runs offline without Minecraft.

`gradlew.bat build runClientGameTest` runs the existing server tests and both NPC
client checks. The Rectifier test checks head rotation, repair-tool pose and grounded
walking/rest, then captures actual day/night Minecraft screenshots. Screenshots are
stored under `art/rectifier/screenshots/` after visual review.

The packaged JAR is the whole Seed City mod with Builder and Rectifier, replacing
the original JAR. It is not a standalone resource pack. Versions remain Minecraft
26.2, Fabric Loader 0.19.5, Fabric API 0.160.0+26.2 and Java 25.
`build runClientGameTest` passed for revision 4 on rerun. The first run timed out in the existing city-growth test; the unchanged rerun passed all 25 server tests and both NPC client checks. Day/night captures were inspected.
Revision 6 sets the hammer to exactly 90 degrees relative to the hand/forearm and speeds repair arm motion to four cycles per second (five game ticks per cycle). Builder construction uses the same visual cadence. Actual block-placement timing and task scheduling are unchanged.

Revision 6 also removes the added fingers/thumb from the lantern hand and extends
the wooden hammer shaft eight model units below its previous end, producing a
long sledgehammer-style handle below the fist. The lantern uses one plain central metal handle rather than the old fork-like loop.


## Cape and shared animation update

The matching red-brown/gold back cape attaches at two shoulder clasps and has its
own model part. Integrate **RectifierCapeMotion.java**, the new cape render-state
fields and the ClientTickEvents registration in SeedCityClient together with the
model/renderer and regenerated assets. This is client-only; no mob task or server
physics changes are involved.

RectifierCapeMotion reuses Minecraft 26.2 ClientAvatarState directly for cloak lag
(25% position catch-up per tick and vanilla teleport reset) and walking bob.
AvatarRenderer's flap/forward/lateral factors and clamps feed the model; the cape
uses a reduced three-degree resting tilt, 15% forward lean and 30% flap, clamped
to 1–18 degrees, with 25% lateral sway to suit the heavy cloth. These artistic
angle gains differ from vanilla; the underlying cloak lag is vanilla. Tick updates
are independent of frame rate, and state is
separate for each entity, cleared on world change/removal. Stationary capes settle.
Like vanilla, this is a hinged cape panel, not a multi-segment cloth simulation or
cloth collision solver. There is no cape mod dependency.

The cape has gold circuit-board embroidery on weathered red cloth. Both clasps
extend into the torso/back plate, eliminating the former attachment gap. The
front tabard, trim and hem now share an independent waist pivot (12 model parts
total), with gentle idle sway plus movement-driven pitch and lateral motion.
Pull the updated RectifierModel and regenerated RectifierMesh together: the model
now requires the `tabard` child part. Front cloth motion is an authored animation,
not a vanilla player-cape simulation.

The standalone preview uses a virtual movement path with the same 20 Hz catch-up
and angle equations. Patrol/Walk demonstrate movement; Rest/Repair allow it to settle.
Its motion is illustrative because the preview character remains in place.
Client checks exercise bounded cape lift, lateral sway, return to rest and independent
front cloth sway, and capture the
cape from behind as well as the usual day/night views.
The hammer shaft is inset through the fist so its rotated face cannot coincide with the hand bottom and cause z-fighting.
Grounded poses are the preview default: Rest and Repair plant both feet, Patrol uses the walking cycle, and Walk remains available. Uncheck Grounded poses to preview optional flight. Java gameplay now uses PathfinderMob, ground navigation, gravity, movement attribute 0.25 and a 0.6 walking multiplier. This matches the iron golem's strolling pace without inheriting its protector behavior. Existing saved flight flags migrate on load.

Playtest fixes: both arm assemblies move inward one model unit and connect through
visible shoulder axles. The hip no longer shares outer faces with the moving thighs;
greave bands meet the lower armor at a seam rather than overlaying it. Repair uses
a six-tick player-style eased stroke, cross-body sweep and recovery, with the hammer
remaining rigid in its grip. Pull the regenerated mesh, texture and model together.
Bedrock uses native Molang hinge animation for the cape and separate tabard sway;
no external cloth mod is required. This is not a cloth collision simulation.
Validation note: the existing city_growth_tests_seed_grows_city_boat test intermittently timed out at 9000 ticks during this pass; the unchanged full rerun passed. No city-growth gameplay was modified.
