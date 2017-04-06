package org.observe;

import org.observe.collect.impl.ObservableArrayList;
import org.qommons.QommonsUtils;
import org.qommons.Transaction;

import com.google.common.reflect.TypeToken;

/**
 * A benchmarking class that runs a bunch of operations on an ArrayList and prints out the time it took to run them.
 *
 * This is not a unit test. Just a tool for me to use to test out potential optimizations.
 */
public class ArrayListBenchmark {
	private static final Integer[] values = new Integer[1000];
	static {
		for (int i = 0; i < values.length; i++)
			values[i] = Integer.valueOf(i);
	}

	/**
	 * Runs the test
	 *
	 * @param args Command-line args, ignored
	 */
	public static void main(String[] args) {
		ObservableArrayList<Integer> list = new ObservableArrayList<>(TypeToken.of(Integer.class));
		long start = System.currentTimeMillis();
		for (int i = 0; i < 100; i++) {
			if (i > 0 && i % 10 == 0) {
				System.out.print(i);
				System.out.print('%');
			} else
				System.out.print('.');
			System.out.flush();
			try (Transaction t = list.lock(true, null)) {
				addStuff(list, 10000);
				addStuff(list, 10000);
				removeStuff(list, 10000);
				addStuff(list, 10000);
				addStuff(list, 10000);
				removeStuff(list, 10000);
				addStuff(list, 10000);
				removeStuff(list, 10000);
				removeStuff(list, 10000);
				removeStuff(list, 10000);
			}
		}
		long end = System.currentTimeMillis();
		System.out.println("\n10kk ops in " + QommonsUtils.printTimeLength(end - start) + ": "
			+ (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) + " mem used");
	}

	private static void addStuff(ObservableArrayList<Integer> list, int number) {
		for (int i = 0; i < number; i++)
			list.add(valueFor(i));
	}

	private static void removeStuff(ObservableArrayList<Integer> list, int number) {
		for (int i = 0; i < number; i++)
			list.remove(valueFor(i));
	}

	private static Integer valueFor(int i) {
		return values[i % values.length];
	}
}
