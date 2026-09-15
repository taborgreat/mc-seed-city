# Two languages, one graph

Design note, 2026-09-15. Status: **proposed, for Tabor to accept**. Source: Tabor's idea that the
player programs the world in something like an operating system, and the colony answers in a
graph of needs and capabilities that it turns into buildings. This note takes that idea as far as
the architecture allows without breaking the rules in CLAUDE.md, says exactly where it lands on
what already exists, and ends with the smallest steps that prove it in-game.

`docs/language-of-growth.md` (September 2026) rejected "a new primitive vocabulary". That was a
rejection of a second *card* vocabulary competing with the twelve ops. What is proposed here is
different: a language **above** the cards for the player, and a graph **below** the cards for the
colony. The twelve ops stay the machine language in the middle, because they are what the
verifier can prove and what the honesty rule is written against.

## The picture

```
 player language        house.3.door.open()   print(7)   when plaza.footfall: gate.*.shut()
        |  compiles to
 cards (machine)        OUT / IN / SET / ADD ... on ports and registers, one op per beat
        |  binds against, and when it cannot, asks
 colony graph           capabilities and cells, needs and provisions, edges are port matings
        |  is built by
 the city               slots, streets, bus, vaults, actuators, sensors; verified redstone
```

Compiling source into architecture happens at the second arrow. A card that binds runs. A card
that cannot bind is a plan, and a plan is a set of **needs**. Today a need is a flat list of cell
types (`ram_vault_1`, `alu_sub`). The proposal is to make needs a graph the colony walks.

## The colony language: a graph, not text

The colony never reads text. Its language is three kinds of node and two kinds of edge.

**Nodes**

- A **cell** is what exists now: ports, a truth model, a footprint, a cost, district weights. It
  knows only its neighbours, through its ports. Nothing changes here.
- A **capability** is a named thing the city can do: `clock`, `memory`, `logic.sub`, `logic.not`,
  `route.bus`, `actuate.gate`, `sense.footfall`, `display.bar`. A capability is provided by one or
  more **recipes**.
- A **recipe** is a way to provide a capability: a list of cells (or smaller capabilities) and how
  they must connect. `memory` has one recipe today: a RAM vault on a bus branch. `display.bar`
  might have a recipe "a lamp column driven by a decoder" and later a cheaper one the Foundry
  found.

**Edges**

- **requires**: capability to capability. `memory` requires `route.bus`; `route.bus` requires
  `clock`? No, the bus needs nothing; but `logic.*` requires `memory` to be useful, and
  `display.*` requires `memory` and `logic.sub`.
- **connects**: cell port to cell port, in the world. This edge is what `/seedcity graph` prints
  today: `(1,1) junction.out_e -> (1,2) register_block.in`.

Tabor's verbs are the operations the planner performs on this graph, and every one already has an
implementation:

| Verb | What it means on the graph | Where it lives today |
| --- | --- | --- |
| BUILD | plan a cell into a slot | `CityState.planAhead` |
| CONNECT | mate an out port to an in port across a slot edge | `Grammar.fit`, `CityState.meeting` |
| ROUTE | extend a street or bus until a need can be placed | bus growth rules in `Grammar.choose` |
| SPLIT / MERGE | a junction fans a wire out; a branch taps the bus and merges its return | `junction`, `bus_branch` |
| CLOCK | the root capability, one per city | forced clock tower |
| STORE / MEMORY | a vault on the bus | `ram_vault_k` |
| DISPLAY | a value made visible | not yet: the first new capability |

So the colony language is not new code. It is a **data file per capability** and a resolver
that expands a need into leaf needs, which is exactly the map the grammar already consumes.

### A need becomes a construction project

```
need: display.bar                     (the card says print(R0))
  recipe "lamp column":
    requires memory                   (a value to show)
      recipe "vault on the bus": ram_vault_k, reached by route.bus
        requires route.bus: bus_branch from the Core gate
    requires logic.decode             (which lamp lights)
      recipe: decoder_plaza in the Forge
    cells: lamp_tower                 (the visible part)
```

Expansion walks the tree, skips what is already built or committed (`committedOfType`), and
hands the leaves to the grammar as wants, weighted so the deepest missing prerequisite is placed
first. The Reader wall shows the tree, not the list:

```
PLAN  print(R0)
  display.bar ..... building lamp_tower (0/1)
    memory ........ ok (ram_vault_1)
    logic.decode .. planned decoder_plaza in the Forge
```

That is the "I don't know how to do that yet" moment, made legible. The player sees what the
colony decided it must grow, and can walk to each project.

### Rewriting itself

The colony "discovers better designs" in two bounded ways, both already in
`language-of-growth.md` as the Foundry:

1. **Choice among recipes.** When a capability has two recipes, the resolver picks the cheaper one
   that fits the district and the space. New recipes arrive as data files (Claude authors them,
   Tabor judges them in-game) or as blueprint items a player carries between cities.
2. **Search for recipes.** The Foundry composes two to four verified cells against a wanted truth
   spec and keeps what verifies. Its winners are new recipes. That is the only unbounded part,
   and it stays bounded by "compositions of verified cells only".

The graph is rewritten by adding recipes and by re-resolving needs every two seconds against
what now stands, which the planner already does.

## The player language: addresses and verbs

The player writes for the world, not for registers. Every cell, mob and district is an address;
every address has verbs (things it can do) and readings (things it can tell you).

```
gate.*.shut()                     every gatehouse
house.3.door.open()               the third house
bell.ring()
wait 20
when plaza.footfall > 0:
    gate.*.shut()
    sentinel.*.wake()
every 4 beats:
    lamp.1.show(daylight.plaza)
n = daylight.plaza
n = 15 - n
print(n)
```

Grammar, small on purpose:

```
program := statement*
statement := call | assign | wait | when | every | loop | print
call      := address '.' verb [ '(' args ')' ]
address   := type [ '.' ordinal | '.*' ]          drawbridge.2, gate.*, plaza
reading   := address '.' name                     daylight.plaza, plaza.footfall
assign    := name '=' expr
expr      := number | name | reading | expr ('+' | '-' | 'and' | 'or') expr | 'not' expr
wait      := 'wait' number
when      := 'when' expr ':' block                  runs the block each time the reading is non-zero
every     := 'every' number 'beats' ':' block
loop      := 'loop' ':' block
print     := 'print' '(' expr ')'
```

Values are still 0 to 15. That is the machine, and the language does not pretend otherwise.

**Compilation to cards** is mechanical, one JUnit-tested pass, no runtime of its own:

| Player language | Card |
| --- | --- |
| `gate.2.shut()` | `OUT gatehouse.2.gate 15` (verbs are declared per cell: `shut: 15`, `open: 0`) |
| `n = plaza.footfall` | `IN R0 footfall_plaza.out` |
| `n = 15 - n` | `NOT R0` |
| `when expr: block` | `IN`, `JZ skip`, block, `skip:` inside the loop |
| `every 4 beats: block` | block, `WAIT 4`, `JMP` |
| `print(n)` | `OUT display.1.value R0`, which needs the `display` capability |
| `house.3.door.open()` | `OUT house.3.door 0`, once a house cell with a door port exists |

Names become registers by first use; more than two live names is a compile error until the bus
addresses more vaults, and the error says so. Verbs come from the cell sidecar (`"verbs": {"open":
0, "shut": 15}`), so a new cell brings its own words and the compiler never learns cell names.

**Mob addresses** (`villager.12.goto(storage)`) are the one thing that does not compile to a port.
The five mobs are the city's organs, not cells, and their behaviour is fixed by the rules. A mob
verb would be an order posted through `CityState` (a Courier already takes "carry this value
there"). Allowed later as a small fixed set of orders, never as per-building scripts.

**`tetris()`** is honest scope: it needs a display of hundreds of lamps and dozens of registers.
The colony would answer with a need tree it cannot satisfy in this version, and say so on the
Reader wall. `print(n)` on a fifteen-lamp bar is the first display, and it is reachable now.

## What is already there

| Proposed | Existing |
| --- | --- |
| needs as the compiler's output | `Executor.need`, `wanted()`, plans that re-resolve every two seconds |
| graph of connections | slots, `meeting`, `liveSlots`, `/seedcity graph` |
| BUILD, CONNECT, ROUTE, SPLIT, MERGE | planner, grammar, bus rules, junction, branch |
| MEMORY | RAM vaults on the bus |
| the colony writing programs | `Dreamer` and fragments |
| a cell knowing only its neighbours | ports and truth models |
| the player reading the graph | labels, Reader wall, `/seedcity fit` |

Missing: capability and recipe data, the resolver that expands a need into a tree, the player
language and its compiler, verbs in sidecars, and the first `display` cell.

## Steps, each one build and one look

1. **Verbs and readings in sidecars.** `gatehouse` gets `shut`/`open`; `drawbridge` gets
   `raise`/`lower`; sensors name their readings. Labels show verbs. No behaviour change.
2. **The player language compiler.** A pure parser and a pass to cards, in `card` (it is the
   Card interface), JUnit-tested. The Card Reader accepts either dialect; the Reader wall shows
   the compiled card under the source. `print` is a compile error until step 4.
3. **Capabilities and recipes as data**, and the resolver. Needs become a tree; the Reader wall
   shows it. The grammar still receives leaf wants, so nothing downstream changes.
4. **The first display**: a `lamp_tower` cell (fifteen lamps up a mast, lit to the value on its
   input) and the `display.bar` recipe. `print(n)` compiles to it. The skyline shows values.
5. **Recipe choice** when a second recipe exists, then the Foundry as recipe search.

Each step leaves the twelve ops, the five mobs, the five interfaces and the verifier exactly as
they are. What changes is that the player writes for the world, and the colony answers with
buildings.
