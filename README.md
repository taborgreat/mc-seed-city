# Seed City

A Minecraft mod where a working redstone city grows itself from a seed, is built and maintained
by mobs, and can be steered by the player.

You place a single block. Builder mobs emerge from it and construct a city outward, block by block,
using a grammar of verified redstone cells. The city is a machine: at its core is a place to feed it
a program, its lights and doors and elevators are that program's output, and its mobs are its organs.

The full specification is [Seed_City_Design_Doc.pdf](Seed_City_Design_Doc.pdf). Part III of it is
the implementation reference and wins over Parts I and II. Judgement calls made along the way are
logged in [DECISIONS.md](DECISIONS.md).

## Stack

| Item | Choice |
| --- | --- |
| Minecraft | 26.2 (pinned in `gradle.properties`) |
| Loader | Fabric Loader 0.19.5, Fabric API 0.160.0+26.2 |
| Build | Gradle 9.5.1 (wrapper), Fabric Loom 1.17.20 |
| Language | Java 25, Mojang official mappings |
| Mod id | `seedcity` |

## Building

You need a JDK 25 with `JAVA_HOME` pointing at it. Nothing else; the Gradle wrapper fetches Gradle.

```
./gradlew build          # compile, unit tests, gametests, produce build/libs/seedcity-<version>.jar
./gradlew genCells       # regenerate the cell library (NBT + sidecars) from src/cellgen
./gradlew runGameTest    # headless server: place every cell and run the verifier on it
./gradlew test           # unit tests only
./gradlew runClient      # launch a dev client with the mod loaded
./gradlew runServer      # launch a dev dedicated server
./gradlew genSources     # decompile Minecraft for IDE navigation
```

On Windows use `gradlew.bat`. The first build downloads Minecraft and remaps it; expect several minutes.

In a dev world (creative, flat world is easiest):

```
/seedcity platform 48        # a stone boat under your feet
/seedcity plant              # a powered Seed where you stand: the city starts growing
/seedcity city               # status: slots built, stock, what each builder is doing
/seedcity slots              # every slot of the nearest city and why it is blocked, if it is
/seedcity graph              # the city as a graph: connections, dead ends, what carries the clock
/seedcity card <name>        # a written book with a built-in card (hold_bridges, blink, countdown, daylight)
/seedcity insert <name>      # insert a built-in card straight into the nearest city
/seedcity eject              # eject the card
/seedcity reader             # what the Reader wall shows
/seedcity blueprint <cell>   # a blueprint item: right-click a Builder with it where you want the cell
/seedcity list               # the cell library
/seedcity place <cell> [rotation 0-3]
/seedcity verify <cell> [rotation 0-3] [keep]
/seedcity verifyall
```

Cells placed by hand go two blocks east of you. Breaking the Seed freezes its city. The Seed is
also craftable: redstone blocks and comparators around an eye of ender.

Things to try once a city has grown: break a block inside a wire and watch the district's
Warden come and put it back; find the dark district (`/seedcity city` names it), find the one
missing block in its wire, and place it; stand near a register vault while its value is high and
see what the Sentinel on the roof does.

## Repo layout

```
src/main/java/net/tabor/seedcity/
  cell/      Cell interface: NBT + sidecar loader, ports, footprints
  grammar/   Grammar interface: port-compatibility solver with district weights
  build/     BuildTask interface: the one construction task type
  verify/    Verify interface: headless tick simulator and truth models
  card/      Card interface: punch-card parser and resolver (twelve ops)
  entity/    Builder, Warden, Courier, Collector, Sentinel
  core/      The Core and CityState (district priority list, frontier, program)
  config/    Server config and containment caps
  command/   /seedcity dev and admin commands
  mixin/     Empty; prefer Fabric API events
src/client/java/net/tabor/seedcity/client/   renderers and screens only
src/cellgen/java/                            cell circuits and the NBT generator (no Minecraft dependency)
src/gametest/java/                           gametests: every cell verified on a headless server
src/main/resources/data/seedcity/
  cells/     generated structure NBT + JSON sidecar pairs (the cell library)
  cards/     built-in cards, including the L0 default
  fragments/ L3 fragments
src/test/java/                               JUnit tests for pure-logic packages
docs/                                        cell format and conventions
```

Every piece of code belongs to exactly one of the five interfaces (Cell, Grammar, BuildTask, Verify,
Card). If a task cannot be described as work on one of them, it is either a cell to hand-build
in-game or it is out of scope.

## Adding a cell

Cells are authored as circuits in [Cells.java](src/cellgen/java/net/tabor/seedcity/cellgen/Cells.java)
and generated into structure NBT plus a JSON sidecar by `gradlew genCells`. The sidecar declares
size, ports, the truth model, district weights, material cost, and whether the cell is a set piece.
The verifier then drives the placed cell with real redstone and checks every port against its truth
model; a cell ships only when that passes. The full format, the port conventions, and the
step-by-step are in [docs/cells.md](docs/cells.md).

## Phases

| Phase | Deliverable | Done when |
| --- | --- | --- |
| 0 | Cell format, loader, port schema, six hand-built cells | `/seedcity place` and `/seedcity verify` work for all six |
| 1 | City boat: Seed block, Builder, frontier grammar, L0 default | A seed on a 48x48 platform grows a working machine to the edge |
| 2 | Wardens, Fault Cells, Sentinels | Break something; watch it get repaired |
| 3 | The Core, punch cards, L1 behavior scripts | Feed the city a card; the bridge obeys your rule |
| 4 | Player blueprints, Couriers, vault logic, L2 hardware, Forge district | `NEED adder_4bit` gets built and verified |
| 5 | Terrain blending, containment polish, L3 self-authored programs | A seed on open ground grows a city that fits the land |

Phase 0 is complete (2026-09-13): generated cells load, place, and pass verification in the
gametest suite, and a broken cell fails naming its port. Phase 1 is complete (2026-09-13): a
powered Seed on a platform spawns Builders that enclose it in a Core and fill the platform with
verified cells, layout determined by the seed alone, clock pulsing into a drawbridge. Phase 2 is
complete (2026-09-13): Wardens patrol each district and rebuild anything that differs from the
blueprint, every city plants a Fault Cell whose district stays dark until a player fixes the
break, and Sentinels guard register vaults, asleep at 0 and hostile at 15. Phase 3 is complete
(2026-09-14): write a card in a book, insert it at the Core's Card Reader, and the city runs it
through its own register vaults and ALU cells one instruction per clock beat; bad cards show
their line on the Reader wall, ejecting returns to the default, and a card that needs hardware
waits as a plan while the builders grow it. See [docs/cards.md](docs/cards.md).
Phase 4 is complete (2026-09-14): cities are zoned per seed into Forge, RAM, Storage and
residential districts you can read from the air; a card that needs hardware makes the builders
grow it in the right district and goes live only after verification; Couriers carry `OUT` and
`IN` between districts; a blueprint handed to a Builder builds a cell where you stand; and vaults
open only on a computed 15. Current phase: **5**.
