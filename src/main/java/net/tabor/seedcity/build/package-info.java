/**
 * Interface: BuildTask. A cell plus a position on one side, a Builder that executes it on the other. All construction, including repair and player blueprints, goes through this one task type. Signature sketch: record BuildTask(cell, origin, rotation, cost); step(builder).
 */
package net.tabor.seedcity.build;
