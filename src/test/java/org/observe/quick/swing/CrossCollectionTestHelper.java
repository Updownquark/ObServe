package org.observe.quick.swing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Helper class for the crossed-collections Quick test */
public class CrossCollectionTestHelper {
	private static List<List<Integer>> POS_Fs;
	private static List<List<Integer>> NEG_Fs;

	static {
		POS_Fs = new ArrayList<>();
		NEG_Fs = new ArrayList<>();
		List<Integer> posF = Collections.emptyList();
		List<Integer> negF = posF;
		POS_Fs.add(posF);
		NEG_Fs.add(negF);
		for (int i = 2; i < 10000; i *= 2) {
			List<Integer> newPosF = new ArrayList<>(posF.size() + 1);
			POS_Fs.add(Collections.unmodifiableList(newPosF));
			newPosF.addAll(posF);
			newPosF.add(i);
			posF = newPosF;
			List<Integer> newNegF = new ArrayList<>(negF.size() + 1);
			NEG_Fs.add(Collections.unmodifiableList(newNegF));
			newNegF.addAll(negF);
			newNegF.add(0, -i);
			negF = newNegF;
		}
	}

	/**
	 * @param b The B value
	 * @return The F values for the given B
	 */
	public static List<Integer> calculateFs(int b) {
		int i, e;
		boolean neg = b < 0;
		if (neg) {
			b = -b;
		}
		for (i = 0, e = 2; e < b; i++, e *= 2) {
		}
		if (neg) {
			return NEG_Fs.get(i);
		} else {
			return POS_Fs.get(i);
		}
	}
}
