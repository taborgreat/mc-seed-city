package net.tabor.seedcity.card;

/**
 * One parsed line. Which fields are set depends on the op:
 * <pre>
 *   SET/ADD/SUB/AND/OR  rd, src        NOT rd          JMP label       JZ rd, label
 *   WAIT count          OUT port, src  IN rd, port     NEED cell
 * </pre>
 */
public record Instr(Opcode op, int line, int rd, Operand src, String label, String port, String cell, int count) {
	public boolean hasRd() {
		return op == Opcode.SET || op == Opcode.ADD || op == Opcode.SUB || op == Opcode.AND || op == Opcode.OR
				|| op == Opcode.NOT || op == Opcode.JZ || op == Opcode.IN;
	}

	@Override
	public String toString() {
		return switch (op) {
			case SET, ADD, SUB, AND, OR -> op + " R" + rd + " " + src;
			case NOT -> op + " R" + rd;
			case JMP -> op + " " + label;
			case JZ -> op + " R" + rd + " " + label;
			case WAIT -> op + " " + count;
			case OUT -> op + " " + port + " " + src;
			case IN -> op + " R" + rd + " " + port;
			case NEED -> op + " " + cell;
		};
	}
}
