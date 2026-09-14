# Seed City

Fabric mod for Minecraft 26.2, Java 25, mod id `seedcity`, packages under `net.tabor.seedcity`.

## Spec of record

`Seed_City_Design_Doc.pdf` at the repo root is the complete specification. Read Part III
(implementation reference) before writing code; it wins over Parts I and II. Where the doc is
silent, choose the simplest option that keeps every phase independently shippable and add one
dated line to `DECISIONS.md`.

## Rules that must not break

- **Five interfaces.** Every piece of code belongs to exactly one of Cell (`cell`), Grammar
  (`grammar`), BuildTask (`build`), Verify (`verify`), Card (`card`). If a task is not work on
  one of those, it is a cell for Tabor to hand-build in-game or it is out of scope.
- **Entities only consume.** `entity` reads tasks from `CityState` in `core`. It never mutates
  the grammar or the program.
- **Determinism.** All generation randomness comes from one `Random` seeded by (world seed,
  seed block pos). Never `level.random` for generation decisions.
- **Never crash a city.** Bad cards, bad NBT, unreachable tasks, missing cells: log, report on
  the Reader wall, degrade.
- **No new mobs.** Builder, Warden, Courier, Collector, Sentinel. New behavior is a blueprint,
  a card, or a weight. One approved extra sits outside the five in `extra`: the Redstone Rat
  (creeper deterrent, no city logic). Anything else of that kind goes in `extra` too, never
  into the five or into `core`.
- **No new ops.** SET, ADD, SUB, AND, OR, NOT, JMP, JZ, WAIT, OUT, IN, NEED until Phase 4 ships.
- **One BuildTask type.** Growth, repair, and player blueprints all go through it.
- **Pinned versions.** `gradle.properties` versions do not float. A bump is a DECISIONS.md line.
- **Dependencies.** Fabric API only unless a phase note says otherwise. Prefer Fabric API
  events over mixins (the one mixin, creeper-flees-rat, exists because no event reaches a
  vanilla goal list).

## Working here

- Build: `./gradlew build`. Tests: `./gradlew test`. Dev client: `./gradlew runClient`.
- Mojang official mappings. In 26.2 the doc's type names are the real ones (`Identifier`,
  `BlockPos`, `Rotation`, `StructureTemplate`). When unsure of a name, list the remapped jar at
  `~/.gradle/caches/fabric-loom/26.2/minecraft-common.jar` rather than guessing.
- Pure logic (card parser, grammar solver, truth models) gets JUnit tests in `src/test`.
  In-world behavior gets Gametests. Phase acceptance criteria are in doc section 25.
- Cells are authored as circuits in `src/cellgen` (Tabor knows little redstone; Claude designs
  them, Tabor judges them in-game). `gradlew genCells` regenerates the NBT + sidecars; never
  hand-edit the generated files. `gradlew runGameTest` verifies every cell on a headless server
  and runs as part of `build`. See `docs/cells.md` for port conventions and the register timing.
- Verification uses the real redstone engine across server ticks (`verify.Verifier`), not a
  simulator. Truth models live in `verify.TruthModels`.
- City model: `core.CityState` plans the layout (deterministic per city seed) and owns the
  material ledger and the verification queue; `core.CityManager` persists cities per level and
  ticks them once a second; `entity.BuilderEntity` only claims tasks and places blocks;
  `grammar.Grammar` is pure. `docs/cells.md` has the port conventions and the cell table.
- `docs/city-as-computer.md` is a decided addendum that governs Phases 3+: honesty rule (no
  value without a register, no op without hardware; a card is a plan until its hardware is
  built), analog words everywhere with a saturating comparator ALU, random-per-city zoning
  that must be readable from the air.
- Mobs: `entity.FlyingCityMob` is the base for Builder, Warden and Courier (hovering, city-bound,
  immune to suffocation); `SentinelEntity` walks. Art is Mcdrizzy's (PR #1): meshes, models and
  renderers in `src/client`, sources in `art/`, generators in `tools/` (Python + Pillow). The
  Warden is shown and named in-game as the **Rectifier** (entity id stays `seedcity:warden`).
  Entities expose only synced presentation flags to the client (building, carrying, repairing,
  alertness); renderers never read server state. Hitboxes match the models: spawn mobs where
  the box is clear (above the Core roof), or they never find a path. Wardens repair by blueprint comparison (`verify.Integrity`), then
  re-queue port verification. Faults are planned slots built minus their `fault` block.
  Config is per city: always read it through `CityState.cfg()`, never the global in city code.
- Cards: `card.CardParser` (pure, tested), `core.Executor` (resolve + run: binds registers by
  ordinal, ALU ops to cells, ports by `type.N.port`; one instruction per clock beat; drives
  ports via Core terminals), `core.CardReaderBlock`, `core.ReaderWall`. Language reference in
  `docs/cards.md`. Twelve ops, no more.
- Districts are angular sectors from the city seed (`CityState.district`); cell weights per
  district live in `cellgen/Cells.java` and are the tuning knob for what grows where. Couriers
  (`entity.CourierEntity`) carry OUT/IN outside the core district via `CityState.post`; Core
  terminals still reach registers and ALU cells. Blueprints: `SeedCityItems`, `BuilderEntity.acceptBlueprint`.
- Terrain, supply and containment (Phase 5): `core.Terrain` reads the ground and computes site
  work; `CityState.fitSlot` decides a slot's level (`Slot.y`); `BuildTask.growth` digs, lays
  foundation, builds, then banks the earth apron; `entity.CollectorEntity` fetches what the
  ledger is short of (`CityState.deficit/claimGather/deposit`); `core.PlayerBlocks` is the
  placed-by-player flag; `#seedcity:warding` fences. L3: `card.FragmentLibrary` + `card.Dreamer`
  compose cards, `CityState.dream` runs them: a fresh city dreams at once, ambitiously when it
  has nothing to run on (a plan the builders grow toward while the previous program keeps
  running), so growth follows the city's own programs. A player card overrides all of it. See `docs/terrain.md`, `docs/fragments.md`.
- Phases 0 to 5 done (2026-09-13/14). Next: bus streets with taps and select lanes (deferred
  from Phase 4), the Foundry and compound cells from `docs/language-of-growth.md`, more cells
  and fragments, and play-testing on real terrain.
