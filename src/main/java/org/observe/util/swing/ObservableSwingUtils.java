package org.observe.util.swing;

import java.util.ArrayList;
import java.util.List;

import org.qommons.IntList;

/** Utilities for the org.observe.util.swing package */
public class ObservableSwingUtils {
	/**
	 * Sorts a list of ints and returns the start and end of each continuous interval in the list
	 *
	 * @param ints The integers to parse into intervals
	 * @param forward Whether to iterate from least to greatest or greatest to least
	 * @return The intervals, in the form of [start, end] arrays
	 */
	public static int [] [] getContinuousIntervals(int [] ints, boolean forward) {
		IntList indexes = new IntList(true, true);
		indexes.addAll(ints);
		int start, end;
		List<int []> ret = new ArrayList<>();
		if(forward) {
			start = 0;
			end = 0;
			while(end < indexes.size()) {
				while(end < indexes.size() - 1 && indexes.get(end + 1) == indexes.get(end) + 1) {
					end++;
				}
				ret.add(new int[] {indexes.get(start), indexes.get(end)});
				start = end = end + 1;
			}
		} else {
			end = indexes.size() - 1;
			start = end;
			while(start >= 0) {
				while(start > 0 && indexes.get(start - 1) == indexes.get(start) - 1) {
					start--;
				}
				ret.add(new int[] {indexes.get(start), indexes.get(end)});
				start = end = start - 1;
			}
		}
		int [] [] retArray = new int[ret.size()][];
		for(int i = 0; i < retArray.length; i++) {
			retArray[i] = ret.get(i);
		}
		return retArray;
	}
}
