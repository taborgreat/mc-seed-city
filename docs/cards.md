# Punch cards

A card is a program the city runs. You write it in a book and quill (or a written book), walk
into the Core, and right-click the Card Reader with it. Right-click the reader with an empty hand
to eject the card; the city then goes back to whatever its hardware does on its own.

`/seedcity card <name>` hands you a written book with one of the built-in example cards
(`hold_bridges`, `blink`, `countdown`, `daylight`). Copy it, change it, insert it.

## The language

One op per line. Comments start with `;`. Labels are a name followed by `:` on their own line.
Registers are `R0` to `R7`. Immediates are `0` to `15`. Case does not matter.

| Op | Meaning |
| --- | --- |
| `SET Rd X` | Rd = X (X is a register or a number) |
| `ADD Rd X` | Rd = min(Rd + X, 15) |
| `SUB Rd X` | Rd = max(Rd − X, 0) |
| `AND Rd X` | Rd = min(Rd, X) |
| `OR Rd X` | Rd = max(Rd, X) |
| `NOT Rd` | Rd = 15 − Rd |
| `JMP label` | continue at label |
| `JZ Rd label` | continue at label if Rd is 0 |
| `WAIT n` | do nothing for n beats of the Clock Tower |
| `OUT port X` | drive an actuator's input with X |
| `IN Rd port` | read a sensor's output into Rd |
| `NEED cell` | ask the city to build a cell (Phase 4) |

Words are analog: a value is a redstone strength from 0 to 15, and arithmetic saturates instead
of wrapping. `AND` and `OR` are min and max, which makes them work on graded values like daylight
or a Sentinel's alertness as well as on/off signals.

## Naming ports

`type.port` is the first built cell of that type; `type.N.port` is the Nth built one;
`type.*.port` is every one. Types are cell names (`drawbridge`, `daylight_plaza`, `register_block`);
ports are what the cell declares (`/seedcity list` shows them). `OUT` needs an input port, `IN`
an output port. A port that does not exist is a card error with a line number; a cell that has
not been built yet is a hardware need.

## What actually happens

Every value lives in a register vault and every operation runs through an ALU cell; the Core only
sequences (docs/city-as-computer.md). `SET R0 15` drives the vault's input port and pulses its
clock; you can watch the vault's windows light. `SUB R0 6` puts R0 and 6 on the subtractor's
ports, waits for the comparators, reads the result and latches it back. `ADD` is three trips
(complement, subtract, complement); `AND` is two subtractions.

One instruction starts on each beat of the Clock Tower (68 game ticks), so a program runs at the
speed of a slow heartbeat, and `WAIT 2` is two beats. Break the tower and the program stops.

The Core reaches a register or ALU port through a **terminal**, a glowing signal block it places
just outside the port, replacing whatever was there (a wire's end, a wall, air). Ejecting the card
puts everything back. `OUT` and `IN` to cells outside the core district go by **Courier**: a mob
picks the value up at the Core, flies it to the port and drives it there, or reads a sensor and
flies the value back while the program waits. You can watch information travel; if the Courier
never gets there, the value never arrives. Bus streets with real addressing are a later phase.

## Plans

A card that needs hardware the city has not built (`ADD` with no subtractor and complement cells,
`R3` in a city with two vaults, `drawbridge.4.in` with three bridges) is accepted as a **plan**.
The Reader wall lists what is missing, the builders favour those cells at the frontier, and the
card goes live by itself once everything it needs is built and verified. Until then the previous
card, or the hardware default, keeps running.

## Reading the wall

The Reader wall floats above the Seed inside the Core. It shows the program state (default, plan
with its needs, or live with the current line), the bound registers' values read straight from
their vaults, whether the clock is high, and any card error with its line number.
