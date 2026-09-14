package net.tabor.seedcity.card;

/** A register R0-R7 or an immediate 0-15. */
public record Operand(boolean register, int value) {
	public static Operand reg(int n) {
		return new Operand(true, n);
	}

	public static Operand imm(int v) {
		return new Operand(false, v);
	}

	@Override
	public String toString() {
		return register ? "R" + value : Integer.toString(value);
	}
}
