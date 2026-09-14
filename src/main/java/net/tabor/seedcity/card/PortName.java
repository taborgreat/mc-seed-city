package net.tabor.seedcity.card;

/**
 * A port reference from a card: {@code type.port}, {@code type.N.port} or {@code type.*.port}.
 *
 * @param ordinal 1-based build ordinal of the cell, 0 for "first", -1 for "every"
 */
public record PortName(String type, int ordinal, String port) {
	public static PortName parse(String text) {
		String[] parts = text.split("\\.");
		if (parts.length == 2) {
			return new PortName(parts[0], 0, parts[1]);
		}
		int ord = parts[1].equals("*") ? -1 : Integer.parseInt(parts[1]);
		return new PortName(parts[0], ord, parts[2]);
	}

	public boolean all() {
		return ordinal < 0;
	}

	@Override
	public String toString() {
		return type + "." + (ordinal < 0 ? "*" : ordinal == 0 ? "1" : Integer.toString(ordinal)) + "." + port;
	}
}
