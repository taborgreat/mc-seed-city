/**
 * Interface: Card. Plain text in; an op list, then hardware needs, out. Twelve ops: SET, ADD,
 * SUB, AND, OR, NOT, JMP, JZ, WAIT, OUT, IN, NEED. No new ops until Phase 4 is accepted. Parse
 * errors carry a line number and never throw past the parser. Resolution against a city (which
 * register vault is R0, which ALU cell does SUB, which actuator is bridge.2.in) and execution
 * live in {@code core}, because they need the city.
 */
package net.tabor.seedcity.card;
