# Courier: first model and small-gap navigation

The Courier follows the supplied lineup: weathered copper shell, small pack,
stubby arms/feet, amber eyes and a redstone parcel. No flag or pole. The friendly
face has simple glowing amber eyes, without goggles, pupils or a smile. The compact
8 × 6 × 6 head sits two model units farther back over the chest. The model has 26 cuboids
and seven articulated parts, with an independent head and alternating scurry cycle.

## Size and movement

The model is approximately 0.45 blocks tall, including its parcel. Its collision
box is 0.48 blocks wide and high, leaving clearance below an upper slab. This is
a ground mob, with no flight, block-burrowing, teleporting or silverfish infestation.
The head can turn 35 degrees sideways and tilt upward by 55 degrees toward nearby
players, with a more frequent player-look goal within five blocks. The helmet
stays below the parcel's top even when tilted up, preserving half-block height.
The shared authored geometry uses a 0.45 root scale in Minecraft and the viewer;
the Blockbench export has that scale baked into its coordinates with explicit UVs.

`CourierNavigation` retains vanilla ground pathfinding, with a narrow exception:
blocked slab/trapdoor nodes may be walked through when the actual mob bounding box
has no collision, the cell is dry, and the floor is solid and non-hazardous.
This targets half-block-high tunnels beneath upper slabs, not arbitrary sub-block
mazes, vertical shafts, or passages narrower than the collision box. Solid walls
remain blocked. No blocks are removed or changed by the Courier.

## Integration: pull code as well as models

Pull CourierEntity, CourierNavigation, SeedCityEntities registration, the client
model/mesh/renderer, SeedCityClient registration, textures and language entry together.
Summon with `/summon seedcity:courier`. There is no automatic city spawning yet.
The Courier has idle look goals and accepts ordinary navigation commands from code;
it does not randomly abandon its post or invent delivery orders.

**Phase 4 signal delivery is not implemented by this appearance pass.** The crystal
is a fixed visual parcel. Tabor still needs to bind an OUT/IN pair, latch the source
value, dispatch the walk from CityState, and write the destination for one tick
on arrival. A blocked route must not deliver, per the design document. The entity
does not mutate the grammar, program, ports or city state.

Also retain the earlier Builder merge notes: this fork includes the missing
BuildTask replacement and its build-directory ignore fix. Importing NPC textures
alone does not bring over those required code repairs.

## Editing and verification

Edit `tools/build_courier_assets.py`, then regenerate. Preview controls live in
`art/courier/preview.js` and `preview.template.html`. Generator checks reject
overlapping coplanar faces within rigid parts. All viewer assets are embedded;
the HTML works offline and includes the pinned Three.js dependency.

Game tests exercise actual traversal of an enclosed half-block tunnel without
suffocation, and rejection of a route sealed by a full block. Client checks bake
the mesh, verify idle/scurry and upward-looking poses and capture the mob beside an upper-slab gap.
Run `./gradlew build runClientGameTest` for the full suite.

The first run hit the previously observed city-growth boat timeout; the unchanged
city-growth code passed on rerun. See the current build log for final results.
Final verification: full build, all 27 server game tests and all three NPC client checks passed after the upward-looking face update.

Current revision removes the goggle rims, bridge and straps. The eyes and redstone
parcel seam are emissive. Head articulation, collision size and navigation are unchanged.
Goggle validation: compilation and both Courier traversal tests passed; the full server suite again hit the existing city_growth_tests_seed_grows_city_boat 9000-tick timeout. Goggle client rendering is checked separately; no gameplay code changed in this revision.
Final goggle verification: assemble and all three NPC client game tests passed.
Centered-head verification: assemble and all three NPC client checks passed.
