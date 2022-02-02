package org.observe.util.swing;

public class TextMatch {
	public final int start;
	public final int end;

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