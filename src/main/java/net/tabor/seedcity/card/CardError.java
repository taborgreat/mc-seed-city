package net.tabor.seedcity.card;

/** A card that cannot be parsed. Carries the line so the Reader wall can show it (doc 22.2). */
public final class CardError extends Exception {
	private final int line;

	public CardError(int line, String message) {
		super(message);
		this.line = line;
	}

	public int line() {
		return line;
	}

	@Override
	public String toString() {
		return "line " + line + ": " + getMessage();
	}
}
