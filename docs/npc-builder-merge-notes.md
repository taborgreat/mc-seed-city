# Builder fork: merge notes for Tabor

**This branch contains more than NPC models.** The public upstream checkout at
`0e420e33f638bb9bc01ac2392f345ed2edcbd9e9` could not compile: its callers import
`net.tabor.seedcity.build.BuildTask`, but that Java source was missing from all
published history. The unanchored `build/` ignore rule hid the Java package as
well as Gradle output. This fork supplies replacement source and fixes the ignore.

## 1. Construction repair — separate commit `0406d80`

- `.gitignore`: changes `build/` to `/build/` so the source package can be committed.
- `src/main/java/net/tabor/seedcity/build/BuildTask.java`: reconstructs the existing
  constructor, cost, progress, next-position, placement, slot and repair contracts.
- `src/gametest/java/net/tabor/seedcity/gametest/BuildTaskTests.java` and its entrypoint:
  regression checks for incremental placement, all four rotated fault positions,
  repair, obstruction protection, live-state preservation, costs and empty tasks.
- `DECISIONS.md`: records why reconstruction was necessary.

**Compare this replacement with your original local BuildTask.java before merging.**
We did not recover the original implementation. If your local version is complete,
keep it, commit it with the ignore fix, and use these tests to check compatibility.
The replacement skips already-correct blocks, writes at most one changed block per
step, rotates states/positions, omits the designated fault, protects new foreign
blocks above a growth slot's floor and restores repair blueprints. Repair ignores
dynamic piston positions, piston heads/moving pistons and verifier probes. Placement
sound uses the block's sound type. CityState still owns the material ledger, task
priority and verification queue. Like Cell.blocks(), it does not carry arbitrary
block-entity NBT; a future blueprint needing inventory/custom block-entity data needs
an explicit extension of that existing contract.

## 2. Builder appearance — take these files together

| Files | Purpose |
| --- | --- |
| `client/BuilderMesh.java` | Generated cuboid geometry and UV layout |
| `client/BuilderModel.java` | Hover, carrying, construction and optional grounded walking animation |
| `client/BuilderRenderState.java` | Render snapshot with work/cargo flags, grounded state and speed |
| `client/BuilderRenderer.java` | Custom texture and selectively emissive layer |
| `client/SeedCityClient.java` | Registers the Builder model layer before its renderer |
| `assets/seedcity/textures/entity/builder.png`, `builder_glow.png` | Base and emission textures |
| `entity/BuilderEntity.java` | Synchronizes two presentation flags from the actual server task phase |
| `entity/SeedCityEntities.java` | Changes Builder dimensions from 0.6 x 0.7 to 0.8 x 1.5 blocks |

`client/` above means `src/client/java/net/tabor/seedcity/client/`; `entity/` means
`src/main/java/net/tabor/seedcity/entity/`; assets are under `src/main/resources/`.

**Copying only the PNGs or model class will not install this Builder.** The layer
registration, renderer, render-state class and synchronized entity flags belong
together. The larger hitbox matches the taller body and may change how it clears
tight spaces; it passed the existing city-growth test. Use matching updated client
and server builds. The registry identifier remains `seedcity:builder`, so existing
Builders receive the new appearance without a new mob type.

Existing hover navigation and construction scheduling remain. This is not a
conversion to a walking Builder. The held stone is a visual material-load symbol,
not the precise block currently being placed. The final Builder has no held tablet. Work arm motion is a
continuous animation during the BUILD phase, not synchronized to each individual
placement sound. Existing block placement sounds come from BuildTask; there is no
new sound pack or particle system in this version. Emission stays visible in the
dark but does not cast dynamic light or require shaders. No other mob was redesigned
or renamed; the replacement name for Warden is still undecided.

An optional alternating-leg walking cycle runs only when the render snapshot
is grounded, using Minecraft's walk position and speed. Flight AI is unchanged;
the preview's Walk button demonstrates the cycle independently so Tabor can choose
whether to use it. The grounded pose removes the hover bob and keeps the feet at
floor level. No ground navigation or walking behavior has been added.

Revision 3 fixes coplanar armor/detail faces that could cause depth flicker in both
the preview and Minecraft. Small CubeDeformation offsets preserve the original UVs;
the same offsets are exported to the viewer and Blockbench mesh. Regeneration now
rejects overlapping coplanar outward faces within each rigid part.
The final revision removes the blueprint tablet and grip entirely,
relaxes the empty left hand and gives it a natural opposing swing during walking.
Take the generated mesh, model animation and both updated textures together.


## Editable source and reproduction

- `art/builder/builder.bbmodel`: editable Blockbench model with embedded base texture.
- `art/builder/builder.geometry.json`: shared cuboids, pivots and face UVs.
- `art/builder/builder-preview.html`: offline, rotatable preview with hover/carry/build/
  walk poses, front/side/back controls and night lighting. Uses the same geometry and
  UVs. Send this single HTML file to a friend; Download viewer saves a portable copy,
  and Save image exports the current view as a PNG. No Minecraft installation needed.
- `tools/build_builder_assets.py`: authoritative geometry/texture authoring source.
  Run `python tools/build_builder_assets.py` with Pillow installed to regenerate
  the Java mesh, two textures, geometry JSON, Blockbench file and offline preview.
- `art/builder/preview.template.html`, `preview.js`: preview layout and renderer.
- `tools/vendor/three-0.180.0/`: pinned MIT-licensed Three.js for the preview only;
  no added Minecraft mod dependency and no runtime network requests from the preview.

The texture has 1024 x 1024 pixels with a logical 256 x 256 UV grid (four texels per
model unit). It is deliberately pixel-filtered. The model has 52 cuboids in seven
groups. Blockbench edits are not automatically imported into Java: mirror geometry
changes back into the authoring script before regenerating. Animations live in
BuilderModel.java; the Blockbench file is an editable mesh, not an animation export.
The preview uses Minecraft's ZYX
part rotation order to match poses involving more than one axis.

## Validation and trying it

Validated on 2026-09-13: `build runClientGameTest` passed. All 25 required server
game tests passed, the existing JUnit test passed, and the client test completed.
Day/night captures were visually inspected. The actual screenshots are committed
under `art/builder/screenshots/`. `.gitattributes` marks generated and vendored
preview files so GitHub can collapse them during review.

- `gradlew.bat build`: compiles both environments, builds the mod, and runs the
  existing unit test plus 25 server game tests, including four new BuildTask tests.
- `gradlew.bat runClientGameTest`: added Builder client test bakes the actual model,
  asserts construction/idle cargo visibility, opposing walking legs and the grounded
  resting pose, spawns `seedcity:builder` in a
  disposable world, and captures daylight/night screenshots. Requires a graphics
  device; it is separate from the normal headless build.
- Client screenshots: `build/run/clientGameTest/screenshots/`.
- `gradlew.bat runClient`, then `/summon seedcity:builder ~ ~ ~` for the appearance;
  `/seedcity platform 48` and `/seedcity plant` to see it perform real construction.

The fork's JAR is the **whole Seed City mod**, not a standalone resource pack. Use it
in place of the original Seed City JAR, not alongside it. Target remains Minecraft
26.2, Fabric Loader 0.19.5, Fabric API 0.160.0+26.2 and Java 25. No pinned version was
changed. The portable Java runtime and Gradle cache used for these checks are outside
the repository and are not part of this merge.
The later shared animation update accelerates construction arm motion to four cycles per second (five game ticks per cycle), matching the Rectifier repair cadence. This is visual motion; placement timing remains configuration-driven.


Playtest revision: the build loop now uses a six-tick eased forward stroke, cross-body arm sweep and recovery, with head aim compensated for body twist. Flight is unchanged. Java and Bedrock showcase work loops and the offline preview are updated.
