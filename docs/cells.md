# Cell library

Each cell is two files with the same base name in `src/main/resources/data/seedcity/cells/`:

- `<name>.nbt` - the structure. Generated, do not hand-edit: run `gradlew genCells`.
- `<name>.json` - the sidecar. The only thing the grammar and the verifier read. Also generated.

Both come from the circuit authored in `src/cellgen/java/net/tabor/seedcity/cellgen/Cells.java`.
The design doc assumed cells would be hand-built in-game; they are authored in code instead
(see DECISIONS.md) because that makes them diffable, regenerable, and buildable without a client.
The in-world verifier is the check that a design actually works.

## Sidecar shape (design doc 22.1)

```json
{
  "id": "seedcity:register_block",
  "kind": "logic",
  "size": [7, 5, 7],
  "ports": [
    { "name": "in",  "dir": "in",  "face": "north", "pos": [3, 1, 0], "bits": 4 },
    { "name": "clk", "dir": "in",  "face": "west",  "pos": [0, 1, 3], "bits": 1 },
    { "name": "out", "dir": "out", "face": "south", "pos": [3, 1, 6], "bits": 4 }
  ],
  "truth": "register",
  "weights": { "core": 3, "residential": 4, "forge": 6, "plaza": 1 },
  "cost": { "redstone": 24, "stone": 80, "wood": 0 },
  "setpiece": true
}
```

Rules:

- `kind` is one of `logic | actuator | sensor | storage | decor | core`.
- `pos` is relative to the cell origin (min corner) and must lie on the named `face`.
- Two cells are compatible when an `out` port of one lands exactly on an `in` port of the
  other when placed adjacent. Width does not have to match: a 4-bit port reads a 1-bit driver
  as 15/0, a 1-bit port reads any strength as on. Same-width matings are preferred, not required.
- `bits: 4` is carried as signal strength 0-15; `bits: 1` is on/off.
- The grammar never wastes a live output: if a cell that listens to it fits, one is chosen;
  plazas and warehouses only fill slots nothing points at.
- `truth` names a verifier model: `passthrough, not, and, or, register, counter, decoder,
  actuator, sensor, clock, none`, and the analog ALU models `sub, complement, max, min`
  (ports `a`, `b`, `out`).
- `settle` (optional, default 40) is how many game ticks the verifier waits after driving inputs.
- `fault` (optional) names the one cell-local block the planner may leave out to plant this cell
  as a Fault Cell. Wires, junctions and bus segments declare their middle block.
- `loot` (optional) is a loot table every chest in the cell is filled from when a Builder places it.
- `setpiece` is `false` only for forge and decor cells.

## Footprint and port conventions

Every shipped cell is 7 wide (x) by 7 deep (z) with a solid floor at y=0. Ports sit at y=1 in the
centre of a face: north `[3,1,0]`, south `[3,1,6]`, west `[0,1,3]`, east `[6,1,3]`. One footprint
keeps the Phase 1 frontier a plain 7x7 grid, and centred ports mean any two cells that agree on
direction and width will mate after rotation.

Port blocks:

| Width | In-port block | Out-port block |
| --- | --- | --- |
| 1-bit | repeater outputting inward | repeater outputting outward |
| 4-bit | comparator outputting inward | comparator outputting outward |

Repeaters output 15 regardless of input, so they are only for 1-bit ports. Comparators pass
strength through unchanged and chain losslessly (comparator reading comparator, or comparator
reading a block another comparator powers). Dust loses one strength per block and must never sit
on a 4-bit path.

## How the verifier drives a cell

The verifier (`net.tabor.seedcity.verify`) uses the real redstone engine, not a simulator:

1. It places a `seedcity:probe` block just outside every in-port. The probe emits a chosen
   strength 0-15 on all faces.
2. It clears the block outside every out-port and reads the strength the port block emits.
3. For each stimulus the truth model asks for, it sets the probes, waits `settle` ticks recording
   the outputs every tick, then samples outputs and a snapshot of every block in the footprint.
4. The model judges the whole sequence and names a port on failure.

Ports whose outside block belongs to a neighbouring placement are left alone; the neighbour drives
them. Results are cached by (cell, neighbours, rotation).

## The cell library

| Cell | Kind | Truth | Ports | Circuit |
| --- | --- | --- | --- | --- |
| core | core | none | none | open chamber around the Seed (a structure void keeps the Seed); placed first by force |
| clock_tower | logic | clock | out S 1b | torch-repeater loop, 68 game tick period, in a chamber under an 11-block spire; a torch ladder runs the beat up the spire as alternating bands of lamps; placed south of the core by force |
| bus_segment | logic | passthrough | in N 4b, out S 4b | a paved street with seven comparators in a glass-covered groove down the middle, lantern posts on the corners |
| wire_segment | logic | passthrough | in N 1b, out S 1b | a paved street: repeater, dust, repeater under glass; carries the clock |
| junction | logic | passthrough | in N 1b; out S, E, W 1b | a crossroads: dust cross under glass feeding three repeaters; fans the clock out |
| inverter | logic | not | in N 1b, out S 1b | repeater into a block, torch on the far side, dust out; lamp shows the inverted state |
| register_block | logic | register | in N 4b, clk W 1b, out S 4b | a vault: four-comparator ring holds a strength; clk gates the ring, NOT clk gates the input; windows light when non-zero and a four-lamp gauge on the ground-floor wall shows the value (1, >4, >7, >10) |
| daylight_plaza | sensor | sensor | out S 4b | daylight detector read by a comparator: a slow 4-bit source |
| drawbridge | actuator | actuator | in N 1b | a plank deck with a slime spine spans a water channel between two banks; the input powers a pier that drives one sticky piston, lifting the whole deck |
| storage_cell | storage | none | none | brick warehouse with barrels and a roof crane; Collectors unload and Builders fetch here |
| decor_plaza | decor | none | none | paved square with a fountain and lantern posts; the grammar's always-legal fallback |
| alu_sub | logic | sub | a N 4b, b W 4b, out S 4b | one subtract-mode comparator: out = max(a − b, 0) |
| alu_not | logic | complement | a N 4b, out S 4b | subtract from a constant 15: out = 15 − a |
| alu_or | logic | max | a N 4b, b W 4b, out S 4b | both inputs drive one block, which takes the stronger |
| vault | actuator | actuator | in N 4b | strongroom with a loot chest; the iron door opens only when in reads exactly 15 |

## Districts

Beyond the ring around the Seed, a city is cut into angular sectors named `forge`, `ram`,
`storage`, `residential` and `plaza`, shuffled and rotated by the city seed. A cell's `weights`
say how much it wants each district; zero keeps it out. Registers grow in RAM, ALU cells in the
Forge, warehouses in Storage, bridges and plazas in the quiet sectors. `/seedcity slots` prints
each slot's district.

The three ALU cells are the whole arithmetic of the analog computer (docs/city-as-computer.md):
the Core composes ADD as NOT, SUB, NOT and AND as SUB, SUB. They wear the Compute district's dark
look and are exempt from the set-piece rule.

Core and clock tower carry zero district weights so the grammar never picks them; the planner
places them by force at slots (0,0) and (0,1), and forces a junction at (0,2) on the clock's
output so the signal fans out from the start. Everything else is the grammar's choice.

### Register timing

The register is a gated latch: transparent while clk is high, holding while clk is low. Counted in
redstone ticks after a clk edge, the ring gate reopens at 3 and the input gate closes at 4 (make
before break, so the ring is never left undriven). An input change reaches the ring at 3, so `in`
must not change in the same redstone tick as a falling clk edge. The truth model and card execution
both respect that: write, drop clk with `in` stable, then change `in` freely while holding.

## Adding a cell

1. Add a method to `Cells.java` using the builder (`comparator`, `repeater`, `dust`, `wallTorch`,
   `lamp`, `stickyPiston`, `fill`, `walls`) and declare its ports, truth model, weights and cost.
   Repeaters and comparators are declared by the direction they output to.
2. Add it to `Cells.all()` and run `gradlew genCells`.
3. Add a test method to `CellVerifyTests` and run `gradlew runGameTest`. Iterate on the circuit
   until the verifier passes; the failure message names the port and the step.
4. Place it in a dev client with `/seedcity place <name>` and stand next to it. If it is not
   interesting to stand next to, keep working on it (forge and decor cells are exempt).
