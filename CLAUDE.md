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
  a card, or a weight.
- **No new ops.** SET, ADD, SUB, AND, OR, NOT, JMP, JZ, WAIT, OUT, IN, NEED until Phase 4 ships.
- **One BuildTask type.** Growth, repair, and player blueprints all go through it.
- **Pinned versions.** `gradle.properties` versions do not float. A bump is a DECISIONS.md line.
- **Dependencies.** Fabric API only unless a phase note says otherwise. Prefer Fabric API
  events over mixins.

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
- Mobs: `entity.FlyingCityMob` is the base for Builder and Warden (hovering, city-bound);
  `SentinelEntity` walks. Wardens repair by blueprint comparison (`verify.Integrity`), then
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
- Phases 0 to 4 done (2026-09-13/14). Current phase: 5 (terrain blending and the city as a
  biome, containment polish, L3 self-authored cards; then bus streets with select lanes, and the
  Foundry from `docs/language-of-growth.md`). Acceptance is design doc section 25, phase 5.
