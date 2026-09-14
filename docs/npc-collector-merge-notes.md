# Collector: miner model, actual tools and portable cave light

The reference-inspired Collector has weathered copper armor, stone gauntlets,
work boots, an apron, a loaded pack and a miner's helmet with a warm headlamp.
The model is approximately 1.70 blocks tall including cargo; collision dimensions
are 0.95 wide by 1.80 high. It walks through a one-wide, two-high cave passage.
There are 55 cuboids and eight articulated parts. The separate torso lets the
shoulders and pack turn into a strike while the legs remain independent. The face
uses a square 8×8×8 head, a lower helmet crown and widely spaced, flush square
amber eyes in a continuous weathered copper faceplate matching the armor, following the reference's simple expression.

The swing follows Minecraft HumanoidModel's attack curve: square-root torso wind-up,
quartic arm easing, head-pitch adjustment and lateral follow-through. The equipped
item follows that same animated arm. The browser demonstrates a normal six-tick
player swing, repeating while Mine is selected.

Normal navigation at speed multiplier 1 uses the player's 0.1 movement attribute
and full forward input (Mob normally scales forward input by speed again). On
ordinary flat ground it travels approximately 4.317 blocks/second, verified over a
40-tick measurement window. Terrain, effects and navigation speed multipliers still
affect travel normally. Walking uses Minecraft's displacement-driven walk state,
vanilla phase and arm/leg amplitudes; it stops when movement stops. The preview
simulates the same ordinary-ground pace and walk-state smoothing.

## Actual Minecraft tools

The model contains **no pickaxe or axe geometry**. CollectorEntity equips an actual
`Items.IRON_PICKAXE` in MAINHAND on spawn. CollectorRenderer uses the vanilla
ItemInHandLayer and item resolver, so item models and resource-pack replacements
render normally. `CollectorModel.translateToHand` attaches the item to the moving
arm. An actual iron axe is also supported. The browser uses the vanilla item sprite
for illustration; the Blockbench mesh intentionally contains no tool.

`selectToolFor(BlockState)` equips the axe for logs and the pickaxe otherwise.
A gather task should call this before mining its authorized target and trigger
the mob's standard main-hand swing when it works. While alive, the mob restores
a pickaxe if its hand contains neither permitted iron tool. Default tool drops
are disabled to avoid spawning tools as loot on every Collector death.

## Portable lamp: server code is required

Pull CollectorEntity, CollectorLightBlock, SeedCityBlocks registration and the light
blockstate asset as well as the entity registration, client renderer/model/mesh,
textures and language entry. This feature **changes world block light**, not just
the appearance of the headlamp; it requires no shader or dynamic-light dependency.

Also pull the small BuildTask/Integrity compatibility changes: Builders may replace
our temporary lamp blocks as if they were air, and blueprint air checks ignore them.
A lamp never substitutes for an expected solid circuit block, so real faults remain visible.

Every five ticks a live Collector checks an air cell near helmet height and places
an invisible `seedcity:collector_light` there, emitting level 12 light. Solid blocks,
plants, fluids and existing player light blocks are never overwritten. This is
omnidirectional block lighting rather than a directional flashlight beam. If that
cell is occupied, no light is placed there. Normal placement and cleanup use client
and known-shape update flags to avoid neighbor notifications into nearby redstone.

Each light checks every ten ticks whether any live Collector still occupies that
lamp position. It expires when none does, including after movement, death, removal
or teleport. Nearby Collectors can share a light. Scheduled cleanup ticks persist
with chunk saves; unloaded chunks resume cleanup on load. If a player waterlogs a
temporary light, cleanup preserves the water. No item is registered for this block.
Light naturally affects Minecraft's light-dependent rules, including mob spawning.

## Gameplay boundary

Summon with `/summon seedcity:collector`. This pass supplies the appearance,
equipped tools, movement foundation and portable light. It does **not** implement
terrain mining, storage deposits, protection/ownership checks, cargo inventory,
automatic city spawning, or gather-order dispatch. The displayed cargo is decorative.
Those should consume CityState-owned orders and obey the design's source restrictions,
warding boundary and player-block protection before any real blocks are mined.

The fork also includes the Builder's missing BuildTask replacement and root build
ignore fix. See `npc-builder-merge-notes.md`; importing only art will omit those fixes.

## Editing and validation

Edit `tools/build_collector_assets.py` and regenerate the shared Java mesh, atlas,
geometry JSON, Blockbench model and offline HTML. The authored mesh uses a 0.8 root
scale in Minecraft/preview; the Blockbench export bakes in that scale with explicit
UVs. Preview runtime and controls live in `art/collector`.

CollectorTests checks actual iron tools, switching to an axe for logs, traversal
of a one-wide/two-high tunnel, level-12 light emission, cleanup after removal and
preservation of a solid block, plus measured player walking pace. CollectorClientTest
checks multi-axis swing and return to idle, and captures both actual item types
and the illuminated enclosed cave. Run `./gradlew build runClientGameTest`.
Local motion revision validation: Collector movement, tunnel, equipment and light tests passed, including the measured player-pace test. The unrelated city-growth boat test timed out again. Separate assemble and all four NPC client checks passed. Collector changes remain uncommitted and unpushed pending user approval.

Backpack lantern revision: a small brass-capped, dark-framed glowing lantern hangs from a fixed side bracket, matching the Rectifier lantern design. CollectorLanternMotion runs a damped spring once per client tick, driven by displacement changes, footfalls and attack starts. It interpolates between ticks and settles when stopped; it resets on teleport and clears unloaded entities. Include CollectorRenderState and its client tick registration as well as the model/assets. Existing cave illumination remains in place.

Lantern refinement: riveted copper mounting plate, stepped support brace and loop handle. Use the Rectifier-sized glass and cage proportions at 70 percent scale. The Java lantern part carries the scale to preserve vanilla box UVs; preview and Blockbench bake the same scale into their geometry. Existing pendulum motion is preserved.
