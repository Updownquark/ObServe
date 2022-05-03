package org.observe.util.swing;

/** Represents a sub-sequence in a text sequence that matches a filter */
public class TextMatch {
	/** The start index (inclusive) of the match in the sequence */
	public final int start;
	/** The end index (exclusive) of the match in the sequence */
	public final int end;

	/**
	 * @param start The start index (inclusive) of the match in the sequence
	 * @param end The end index (exclusive) of the match in the sequence
	 */
	public TextMatch(int start, int end) {
		this.start = start;
		this.end = end;
	}

	@Override
	public int hashCode() {
		return start ^ Integer.reverse(end);
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof TextMatch && start == ((TextMatch) obj).start && end == ((TextMatch) obj).end;
	}

	@Override
	public String toString() {
		return new StringBuilder("[").append(start).append(',').append(end).append(']').toString();
	}
}