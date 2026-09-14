# L3 fragments: the city's dreams

The city writes its own cards and runs them (design doc 14, L3): as soon as its Core stands,
whenever its reader is empty, and again after each dream has run its length. It composes a card
from **fragments**: short card snippets in `src/main/resources/data/seedcity/fragments/*.frag`,
hand-authored, with placeholders the city fills from the hardware it has actually built. The
result appears in the reader as a written book titled `Dream #n`, authored by "the city"; eject
it and you hold the dream. When the city dreams again, the old book goes to a warehouse chest.

A new city dreams as soon as its Core stands (`dreamAtStart`): over the hardware it has if any
fragment fits, otherwise a **plan**, a card written over one more actuator, sensor and vault
than exist and the ALU. The builders grow what the plan needs because the program asks for it,
and the plan goes live once it stands. After a dream has run `dreamLengthSeconds` with an idle
frontier, the city dreams bigger the same way, and the previous dream keeps running while the
new one waits for its hardware. The skyline follows the software.

A player's card is never overwritten by a dream. Insert a card and the dreaming stops; eject it
and the dreaming resumes. `/seedcity dream` forces one now.

## Placeholders

| Placeholder | Filled with |
| --- | --- |
| `$A1`, `$A2`, ... | an actuator input port, as cards name them (`drawbridge.2.in`, `vault.1.in`) |
| `$S1`, `$S2`, ... | a sensor output port (`daylight_plaza.1.out`) |
| `$R1`, `$R2`, ... | a register, from the vaults the city has built (`R0`, `R1`) |
| `$N1`, `$N2`, ... | a number from 1 to 15 |
| `$L1`, `$L2`, ... | a unique label name; write `$L1:` to define it and `JMP $L1` to use it |

A fragment is only used when the city has enough hardware for every placeholder it names. The
composer picks one to three usable fragments, fills them, wraps them in one loop, and keeps the
card only if it parses and every op binds (an `ADD` still needs the Forge's subtractor and
complement cells). Fragments that use ALU ops therefore appear in dreams only once the Forge has
grown: the city's dreams get richer as it does.

## Shipped fragments

| Fragment | What it does |
| --- | --- |
| `blink` | an actuator on and off on a slow pulse |
| `alternate` | two actuators take turns |
| `hold` | an actuator held at a level for a while |
| `follow` | an actuator follows a sensor for a counted while |
| `mirror` | an actuator does the opposite of a sensor |
| `countdown` | a register counts down to zero on an actuator |
| `ramp` | an actuator ramps to full in steps of three |
| `louder` | an actuator shows the greater of two sensors |

## Writing one

One op per line, comments after `;`, same language as cards (docs/cards.md). Keep them short:
a dream is a mood, not a program. Anything that needs a `WAIT` to be visible should have one.
Determinism holds: the same city, hardware and dream number always compose the same card.
