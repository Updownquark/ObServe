package org.observe.util.swing;

import java.util.Arrays;

import org.qommons.ArrayUtils;

/**
 * A simple sorted set of {@link TextMatch}es. This highly-specialized and highly-optimized class makes combining matches fast and easy. It
 * assumes single-threaded usage, as it should only be ever used on the Java AWT Event Queue thread. For performance reasons however, this
 * is never checked.
 */
public class SortedMatchSet {
	/** Immutable empty match set */
	public static final SortedMatchSet EMPTY = new SortedMatchSet(0);

	private int[] theMatches;
	private int theSize; // This is double the number of matches in the set, as each match takes two spots

	/** Creates a mutable empty match set */
	public SortedMatchSet() {
		this(10);
	}

	/**
	 * Creates a mutable empty match set
	 *
	 * @param capacity The initial capacity for the match set
	 */
	public SortedMatchSet(int capacity) {
		theMatches = new int[capacity << 1];
	}

	/**
	 * Adds a text match to this match set
	 *
	 * @param match The match to add
	 * @return This match set
	 */
	public SortedMatchSet add(TextMatch match) {
		if (theMatches.length == 0)
			throw new UnsupportedOperationException("Can't add to EMPTY");
		return add(match.start, match.end);
	}

	/**
	 * Adds a text match to this match set
	 *
	 * @param start The offset index of the start of the text match
	 * @param end The offset index of the end of the text match
	 * @return This match set
	 */
	public SortedMatchSet add(int start, int end) {
		if (theMatches.length == 0)
			throw new UnsupportedOperationException("Can't add to EMPTY");
		int index = ArrayUtils.binarySearch(0, (theSize >> 1) - 1, idx -> {
			int dblIdx = idx << 1;
			int comp = Integer.compare(start, theMatches[dblIdx]);
			if (comp == 0)
				comp = Integer.compare(end, theMatches[dblIdx + 1]);
			return comp;
		});
		if (index >= 0)
			return this; // Duplicate
		boolean newCap = theSize == theMatches.length;
		int[] newMatches;
		if (newCap)
			newMatches = new int[theMatches.length << 1];
		else
			newMatches = theMatches;
		index = -index - 1;
		index = index << 1; // Double it
		int move = theSize - index;
		if (move >= 6) {
			System.arraycopy(theMatches, index, newMatches, index + 2, move);
		} else if (move > 0) {
			for (int i = theSize - 1; i >= index; i--)
				newMatches[i + 2] = theMatches[i];
		}
		if (newCap) {
			if (index >= 6) {
				System.arraycopy(theMatches, 0, newMatches, 0, index);
			} else if (index > 0) {
				for (int i = 0; i < index; i++)
					newMatches[i] = theMatches[i];
			}
			theMatches = newMatches;
		}
		newMatches[index] = start;
		newMatches[index + 1] = end;
		theSize += 2;
		return this;
	}

	/**
	 * Adds all matches from another match set into this one
	 *
	 * @param matches The matches to add
	 * @return This match set
	 */
	public SortedMatchSet addAll(SortedMatchSet matches) {
		if (matches == null)
			return this;
		if (theMatches.length == 0)
			throw new UnsupportedOperationException("Can't add to EMPTY");
		if (theSize + matches.theSize > theMatches.length) {
			int[] newMatches;
			if (theSize > matches.theSize)
				newMatches = new int[theSize << 1];
			else
				newMatches = new int[matches.theSize << 1];
			System.arraycopy(theMatches, 0, newMatches, 0, theSize);
			theMatches = newMatches;
		}
		for (int i = 0; i < matches.theSize; i += 2)
			add(matches.theMatches[i], matches.theMatches[i + 1]);
		return this;
	}

	/** @return All text matches in this match set */
	public TextMatch[] getMatches() {
		int matchCount = theSize >> 1;
		TextMatch[] copy = new TextMatch[matchCount];
		for (int i = 0, j = 0; i < matchCount; i++)
			copy[i] = new TextMatch(theMatches[j++], theMatches[j++]);
		return copy;
	}

	/** @return The number of matches in this match set */
	public int size() {
		return theSize >>> 1;
	}

	/**
	 * @param i The index of the match in this match set
	 * @return The offset index of the start of the text match at the given index
	 */
	public int getStart(int i) {
		int idx = i << 1;
		if (idx >= theSize)
			throw new IndexOutOfBoundsException(i + " of " + (theSize / 2));
		return theMatches[idx];
	}

	/**
	 * @param i The index of the match in this match set
	 * @return The offset index of the end of the text match at the given index
	 */
	public int getEnd(int i) {
		int idx = i << 1;
		if (idx >= theSize)
			throw new IndexOutOfBoundsException(i + " of " + (theSize / 2));
		return theMatches[idx + 1];
	}

	/** @return All the text matches in this match set, with any intersecting or abutting matches combined into one */
	public TextMatch[] getDisjointMatches() {
		TextMatch[] matches=new TextMatch[theSize];
		if (theSize == 0)
			return matches;
		int m=0;
		int start=theMatches[0], end=theMatches[1];
		for (int i = 2; i < theSize; i += 2) {
			int mStart=theMatches[i];
			if(mStart>end) {
				matches[m++]=new TextMatch(start, end);
				start=mStart;
			}
			end=theMatches[i+1];
		}
		matches[m++] = new TextMatch(start, end);
		if (m < matches.length)
			matches = Arrays.copyOf(matches, m);
		return matches;
	}

	@Override
	public String toString() {
		StringBuilder str = new StringBuilder();
		for (int i = 0; i < theSize; i++) {
			if (i > 0)
				str.append(',');
			str.append('[');
			str.append(theMatches[i]);
			str.append(',');
			i++;
			str.append(theMatches[i]);
			str.append(']');
		}
		return str.toString();
	}
}