# The city as a computer

Design addendum, September 2026. Status: **decided** (Tabor, 2026-09-13); Phases 3 and 4 are built to it, except the bus and select lanes, which are still to come. Nothing here changes
Phase 2. It governs Phases 3 to 5 and everything after.

## The idea

The Seed grows a city: roads, storage, lights, bridges, districts. As the city matures, the Core
becomes its processor, and the computer physically grows instead of appearing all at once.

- The Core is the CPU, where programs execute.
- A RAM district grows to hold registers and working state.
- A Storage district holds long-term memory: punch cards, saved programs.
- A Compute district (the doc's Forge) grows for arithmetic and, later, parallel work.
- Bus streets are the data buses connecting the districts.
- The Clock Tower stays the heartbeat.

Hardware is never pre-generated. The city builds only what its programs need. A tiny program
produces a tiny computer; a larger one demands more registers, more bus, more arithmetic, and
eventually new districts. Writing software changes the skyline. From above, the mature city reads
like a CPU die: dense compute somewhere in it, a beautiful, explorable town around it.

Progression falls out of it: discover and repair a city, then own a Seed and grow one, then grow a
computer that never reaches a finished state, because every new capability is new hardware. Two
cities running different programs grow into different shapes. Expansion packs are new districts.

## How it sits against the design doc

Most of this is already in the doc in embryo:

- Part II: "what the city builds is whatever the program requires", the `NEED` op, the Forge, and
  "seen from above, a CPU already looks like a city".
- Section 15: registers are physical Register Block cells; "the program is physically present in
  the city".
- Section 19: the five interfaces. Nothing here needs a sixth. It is Card resolving into more
  kinds of Goal, and Grammar honouring district zoning. Builders and Verify are unchanged.

What it changes:

1. **Districts become functional, not radial.** A district is a typed zone with a purpose, a
   capacity, its own cell subset and routing style, and a look you can recognise from the air.
2. **Goals become demand.** Resolving a card yields a hardware requirement, and the planner grows
   zones to meet it.
3. **Buses become real data paths** with addressing, not just a wire the clock rides on.

## The three decisions

### 1. Honesty: no value without a register, no operation without hardware

Pillar one says everything works. The Core may sequence and control in software, at clock speed.
It may not hold state or compute results the city has not built the hardware for.

- Every register a program uses is a Register Block cell in a RAM district. The Core reads and
  writes it through its ports. Break the cell and the value is gone.
- Every arithmetic or logic op a program uses requires a built, verified cell of that kind in a
  Compute district. The Core routes operands to it over the bus, waits the hardware's latency,
  and reads the result back. No adder, no `ADD`.
- Every `OUT`/`IN` is a port on a real actuator or sensor cell, reached over bus or by Courier.
- Control (fetch, decode, branch, `WAIT`) lives in the Core as software, clocked by the tower.
  Pushing it into redstone is a later Forge project, one instruction at a time.

**A program is a plan until its hardware is finished.** Inserting a card whose needs are not yet
built does not run anything: the card is held as a plan, its needs become goals, the builders
build, the verifier verifies, and only when every need is satisfied does the program go live
(doc 15.2). Until then the Reader wall shows what is still missing. The previous program keeps
running meanwhile.

### 2. Words are analog, everywhere

Four bits carried as signal strength 0-15 on one wire, in the town and in Compute alike. One
lane per word keeps the 7x7 grid clean, a lamp per register shows what is held, and comparator
ladders can show a value travelling down a street. This makes Seed City a **16-level analog
computer**, and the instruction set maps onto native comparator operations:

| Op | Analog meaning | Hardware |
| --- | --- | --- |
| `SUB a b` | max(a − b, 0), saturating | one comparator in subtract mode (native) |
| `NOT a` | 15 − a | subtract mode with a constant 15 on the rear |
| `ADD a b` | min(a + b, 15), saturating | 15 − ((15 − a) − b): three comparators and a constant |
| `OR a b` | max(a, b) | two outputs merging onto one wire (native) |
| `AND a b` | min(a, b) | a − (a − b): two comparators |
| `JZ a` | branch when a == 0 | compare mode against a constant 1, or read directly |
| `SET`, `JMP`, `WAIT` | no data path | Core sequencing |

Consequences to carry into Phase 3: arithmetic saturates rather than wrapping, so the sample card
in doc 22.2 ("wraps at 16") counts down with `SUB` and `JZ` instead, and comparisons are
`SUB` then `JZ`. Fuzzy `AND`/`OR` as min/max is a feature: Sentinel alertness and daylight are
graded values and the language treats them as such.

### 3. Zoning is random per city, readable from the architecture

No fixed compass layout. Where RAM, Compute and Storage grow is decided by the city seed and the
demand, so every city is a different shape and players learn to read cities by climbing high and
looking. The corollary is a hard requirement on the cell library: **every district must be
recognisable from the air**. Register blocks read as vaults in rows with lit windows; Compute is
dense, dark and tight; Storage is warehouses and cranes; bus streets are glass channels. District
identity comes from the cells, never from the map position.

## Architecture sketch (v1, 4-bit analog, one bus)

- **Data bus:** one analog lane in a loop of bus streets from the Core through RAM and Compute
  and back. New cells: `bus_corner`, `bus_cross` (two lanes crossing, no contact), `bus_tap`
  (a gated branch: value flows when its select line is high; the subtract-mode comparator gate
  the register already uses).
- **Select bus:** a second lane carrying a register address 0-15. A `decoder_plaza` per RAM
  block turns it into one-hot select lines; each register's `bus_tap` opens only when selected.
  Reading register 3 means: Core puts 3 on the select lane, waits, reads the data lane.
- **Write:** Core puts the destination address on select and the value on data, pulses `clk`
  on the RAM block. Registers are transparent latches with a documented hold time; the Core's
  sequencing honours it.
- **Compute:** the Forge district from the doc, renamed. Holds the analog ALU cells from the table
  above, packed tight, with its own clock divider. Compact library, no set-piece rule, but a
  recognisable look.
- **Storage:** storage cells and card lecterns. Cards the city has run (and, in Phase 5, cards it
  wrote) are physical items you can find here.
- **IO:** actuators and sensors stay spread through the town on wire branches and Couriers. They
  get bus taps only where a program reads or writes them.

Timing at the doc's clock (68 game ticks per beat): an instruction touching two registers and an
adder is on the order of ten beats. Programs run at the pace of a slow heartbeat, which is what
the doc wants. Per-district clock dividers keep Compute from waiting on drawbridges.

## Demand: what a card asks the city to build

| Card uses | City needs |
| --- | --- |
| registers R0..Rn | n+1 register cells in RAM, one decoder per 8, bus and select lanes reaching them |
| `ADD` / `SUB` | an adder / subtractor cell in Compute |
| `AND`, `OR`, `NOT` | the matching analog logic cell in Compute |
| `JZ` | a zero-detect cell in Compute |
| `WAIT n` | nothing new: the Core counts beats |
| `OUT port`, `IN port` | a bus tap or Courier route to that actuator or sensor |
| `NEED cell` | exactly that cell (already in the doc) |
| a second card in the slot | a Storage district cell to keep the first one |

Each row becomes Goals. The planner grows the matching zone adjacent to the bus, in priority
order from the Core (doc 20.3). Unused hardware is never torn down; the city only grows. A later
program that needs less leaves idle districts behind: ruins with a story.

## What would be expansion, not core

- GPU/parallel district: many identical cells fed by one broadcast bus, for choreography over
  many lamps or doors at once. Needs no new op if `OUT` can target a bus of actuators.
- Networking between cities (Couriers over land, later towers).
- Robotics, manufacturing, power grids, other architectures. Each is a district type, a cell
  subset, and a demand row. The framework does not change for them.

## Impact on the phase plan

- Phase 2: unchanged (Wardens, Fault Cells, Sentinels, placed-by-player flag).
- Phase 3: Core, cards, L1, Reader wall, designed to the honesty rule from day one: the
  interpreter reads and writes physical registers over the bus; saturating analog arithmetic;
  cards held as plans until their hardware exists.
- Phase 4: functional districts: RAM, Compute, Storage zoning chosen per city seed, the demand
  table, bus taps and select lanes, the analog ALU cells, the first adder. This is the phase that
  turns the city into a computer.
- Phase 5: unchanged, plus the GPU district as the first expansion if there is appetite.

## Still open

- How visible should data movement be? Comparator ladders into lamps along bus streets would
  let you watch a value travel. Costly in blocks, wonderful to look at. Decide when the first bus
  is built.
- Reclaiming unused hardware: current answer is never.
