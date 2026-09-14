# Mob art

Models, textures and glow layers by Mcdrizzy (pull request #1, September 2026). One folder per
mob, each with the editable Blockbench file (`*.bbmodel`) and the shared geometry (`*.geometry.json`).
The game reads none of this directly: the geometry becomes `src/client/.../<Mob>Mesh.java` and
the textures land in `src/main/resources/assets/seedcity/textures/entity/`.

| Folder | In game | Entity |
| --- | --- | --- |
| `builder/` | Builder | `seedcity:builder` |
| `rectifier/` | Rectifier (the design doc's Warden) | `seedcity:warden` |
| `courier/` | Courier | `seedcity:courier` |
| `sentinel/` | Sentinel, eyes lit by alertness 0-15 | `seedcity:sentinel` |
| `collector/` | not wired yet; the Collector entity comes with Phase 5 | `seedcity:collector` |
| `redstone-rat/` | Redstone Rat, an extra outside the five mobs | `seedcity:redstone_rat` |

## Regenerating

`tools/build_<mob>_assets.py` is the source of truth for cuboids and UVs. Edit it, then:

```
pip install pillow
python tools/build_builder_assets.py
```

It rewrites the Blockbench file, the geometry JSON, the textures and the Java mesh together, so
the three never disagree. Animations are hand-written in `<Mob>Model.java`.

## Exhibit poses

Give a mob the custom name `SC:<pose>` (walk, run, look, fly, build, carry, repair, sleep,
alert, attack) and it loops that pose on the client, for screenshots. Ordinary city mobs are
never affected (`ShowcaseAnimation`).

## Not taken from the pull request

The preview HTML viewers (6 MB each, with three.js embedded), the screenshots, the Bedrock packs,
the showcase world and the fork's server-side rewrites. See DECISIONS.md, 2026-09-14.
