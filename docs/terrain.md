# Terrain, supply and containment

Phase 5 (design doc 8, 20.1, 4.3). Everything here lives in `core.Terrain` (pure functions over
a level), `CityState.fitSlot` (the decision per slot), `BuildTask.growth` (the site work) and
`entity.CollectorEntity` (supply).

## A slot on the land

Before a slot is planned the city surveys the 7x7 ground under it: the top ground block of every
column (trees, plants, snow and water are not ground; a barrier is not ground). The slot is
unbuildable, and marked `BLOCKED` with the reason, when:

- any column has no ground within reach (`no ground`), or water on it (`water`);
- the ground heights spread more than `slopeLimit` (`slope`, default 3);
- a warding block stands in or one block around the footprint (`warded`);
- the floor would be more than `foundationDepth` above the lowest column (`drop`);
- anything a player built, or anything that is not natural ground, stands in the volume
  (`occupied`).

Otherwise the floor goes at the **median** ground level, replacing the turf, unless a neighbour
that already has a cell is within `terrainStep` (default 2) of it, in which case the slot joins
the neighbour's level so their ports mate and the street continues. Two slots at different
levels never mate: that face is a wall, and the signal stops at the step. `/seedcity fit` tells
you how the slot you stand in sits; `/seedcity slots` shows each slot's level.

The Core sits where the Seed was placed. The city is cut into the hillside where the ground is
higher and stands on foundations where it is lower.

## Site work

A growth task does four things in order, all by the Builder, block by block:

1. **Dig**: every natural block inside the footprint volume the blueprint leaves empty goes
   (logs, leaves, earth, stone). Wood, stone and redstone ore dug out are salvaged into the
   ledger.
2. **Foundation**: under every column, from the ground up to just below the floor, that
   column's floor block is laid, so a cell on a slope stands on masonry.
3. **The cell** itself.
4. **The apron** (the biome surface rule): on every side with nothing of the city beyond it,
   earth is banked against the wall, one block lower per block out for `apronWidth` blocks
   (default 3), using the surface the land already has there: grass stays grass, sand stays
   sand. Higher ground is left alone. A neighbour built later digs through the apron.

`apronWidth = 0` turns blending off.

## Collectors

The only mob that routinely leaves the city. When the ledger is short of a currency (below the
configured reserve plus what the planned frontier costs), the Core sends a Collector out with a
gather order. It walks to the nearest permitted source, mines `collectorLoad` blocks of it
(working a tree or a rock face, not one block), hauls the load to the nearest warehouse (else the
Core) and credits the ledger: a log is 4 wood, a stone block 2 stone, a redstone ore 8 redstone.

Permitted sources are natural blocks with an open face, on or near the surface, within the city
radius plus `collectorRange`, outside the city's own footprint, never a player's block, and never
beyond a warding line. With no source in reach the city logs it and growth stalls on that
currency, which is the design: a seed on a plain crawls, a seed by a forest and a cliff races.
`unlimitedMaterials` disables all of it.

One Collector to start, one more per warehouse, up to `maxCollectors`. They walk; fence them.

## Containment

- **Chunk cap**: a slot that would spread the city's footprint (planned included) past
  `maxChunks` is never planned. Growth halts exactly at the cap.
- **Warding**: blocks in the `#seedcity:warding` tag (lodestone and crying obsidian by default)
  fence the city. Builders will not plan a slot touching one; Collectors will not cross a line of
  them. Add blocks to the tag with a data pack.
- **Radius** (`maxRadiusSlots`), **builders**, **collectors** and **couriers** are capped.
- **Player builds**: positions where a player placed a block are remembered per level
  (`PlayerBlocks`). Builders will not plan over them and Collectors will not mine them, so the
  city cannot eat a base. Break the block and the mark goes with it.
- **Seed destroyed**: growth, repair and supply freeze; what runs keeps running.
