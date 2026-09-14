# Sentinel: first visual pass

Replaces the husk placeholder with a 49-cuboid iron guardian inspired by the supplied
reference: narrow red eyes, segmented chest power strip, layered armor and an upright
redstone spear. Nine articulated parts include an elbow and independently pivoting spear.
The revised face restores a short two-unit-wide villager-style nose and uses a
slightly rectangular 7-by-8 helmet front. Four red chest indicators match the reference.
The head is 2.63 blocks above the floor; collision is 1.10 wide by 2.63 high.
The spear extends beyond that body collider and is visual geometry, not an item or
an extended-reach weapon. Existing melee damage and targeting rules remain in place.

Signal 0 holds a grounded, bowed-head statue pose. Signals 1–14 allow walking and
head tracking with increasing head range and glow. At 15 the existing hostile AI
drives attacks; the model levels its spear, extends the elbow into a straight jab,
then retracts and returns upright during the vanilla attack cycle. The bent carrying
arm keeps the shaft ahead of the shoulder, with wrist compensation preserving its
upright direction throughout the walk cycle.
The offline viewer provides stand, walk and strike demonstrations and a 0–15 slider.

## Pull more than the artwork

Include SentinelMesh, SentinelModel, SentinelRenderState, SentinelRenderer, renderer
registration, textures (including all 16 glow levels), and the entity changes.
Alertness now synchronizes from server to client and persists as the clamped
`Alertness` field. Bound ports still update it through the original server behavior.
The collider change affects path clearance: this tall Sentinel needs three-block
headroom and more than a one-block-wide corridor.

The shared fork also contains the previously missing BuildTask.java and Collector
lighting/build-verification compatibility changes. See the other NPC merge notes;
copying only model files will not reproduce the complete fork.

Regenerate with `python tools/build_sentinel_assets.py`. Editable Blockbench source,
geometry JSON, atlas and standalone viewer are under art/sentinel. Client tests
cover dormant pose reset, forward spear rotation, alertness synchronization and
in-game screenshots; existing GuardianTests exercise the bound signal behavior.

## Optional walking and jogging

The grounded walk uses restrained 16-degree leg swings; the light jog uses 24-degree
swings with a slightly quicker cadence. Both track displacement rather than idle time,
and keep the spear upright. The preview has separate Walk and Run / jog buttons.
SentinelRenderState.jogging selects the variant; the renderer reads isSprinting().
Tabor can set the sprint flag in his movement controller or choose another condition
in extractRenderState. This revision does not alter movement attributes, navigation
speed or attack goals. Preview walk/jog demonstrate approximately 2 and 3 blocks/sec;
those are preview pacing choices, not new gameplay speeds.


Playtest revision: body, head, arms and legs have approximately 35% more front-to-back depth (rounded to whole texture pixels). Height, width, four chest indicators, separate walk/jog and signal behavior are unchanged. The spear retains its approved dimensions.
