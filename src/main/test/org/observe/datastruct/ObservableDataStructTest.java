package org.observe.datastruct;

import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.observe.collect.ObservableCollectionsTest.collectionsEqual;
import static org.observe.collect.ObservableCollectionsTest.greaterThanOrEqual;
import static org.observe.collect.ObservableCollectionsTest.sequence;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Consumer;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.observe.collect.ObservableCollectionsTest.Checker;

/** Runs tests on the data structures built on top of observable collections */
public class ObservableDataStructTest {
	/**
	 * Runs a barrage of tests against a map, observable or not
	 *
	 * @param <T> The type of the map
	 * @param map The map to test
	 * @param check An optional check to run against the map after every modification
	 */
	public static <T extends Map<Integer, Integer>> void testMap(T map, Consumer<? super T> check) {
		testMap(map, check, 0);
	}

	private static <T extends Map<Integer, Integer>> void testMap(T map, Consumer<? super T> check, int depth) {
		if(map instanceof ObservableMap)
			check = (Consumer<? super T>) testingObservableMap((ObservableMap<Integer, Integer>) map,
				(Consumer<? super ObservableMap<Integer, Integer>>) check, depth);

		if(map instanceof NavigableMap)
			testSortedMap((NavigableMap<Integer, Integer>) map, (Consumer<? super NavigableMap<Integer, Integer>>) check);
		else
			testBasicMap(map, check);
	}

	private static <T extends ObservableMap<Integer, Integer>> Checker<ObservableMap<Integer, Integer>> testingObservableMap(T map,
		Consumer<? super T> check, int depth) {
	}

	/**
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 * @param values The map to test against
	 * @param ordered Whether to test the equality of the map as if order matters
	 * @return A matcher that matches a map m if m is equivalent to the given map, in an ordered way if specified
	 */
	public static <K, V> Matcher<Map<K, V>> mapsEqual(Map<K, V> values, boolean ordered) {
		Matcher<Collection<K>> keyMatcher = collectionsEqual(values.keySet(), ordered);
		return new org.hamcrest.BaseMatcher<Map<K, V>>() {
			@Override
			public boolean matches(Object arg0) {
				if(!keyMatcher.matches(arg0))
					return false;
				Map<K, V> arg = (Map<K, V>) arg0;
				for(Map.Entry<K, V> entry : values.entrySet())
					if(!Objects.equals(entry.getValue(), arg.get(entry.getKey())))
						return false;
				return true;
			}

			@Override
			public void describeTo(Description arg0) {
				arg0.appendText("map equivalent to ").appendValue(values);
			}
		};
	}

	/**
	 * Runs a map through a set of tests designed to ensure all {@link Map} methods are functioning correctly
	 *
	 * @param <T> The type of the map
	 * @param map The map to test
	 * @param check An optional function to apply after each map modification to ensure the structure of the map is correct and potentially
	 *            assert other side effects of map modification
	 */
	private static <T extends Map<Integer, Integer>> void testBasicMap(T map, Consumer<? super T> check) {
		// TODO
	}

	private static <T extends NavigableMap<Integer, Integer>> void testSortedMap(T map, Consumer<? super T> check) {
		testBasicMap(map, coll -> {
			Comparator<? super Integer> comp = map.comparator();
			Integer last = null;
			for(Integer el : map.keySet()) {
				if(last != null)
					assertThat(el, greaterThanOrEqual(last, comp));
				last = el;
			}

			if(check != null)
				check.accept(coll);
		});

		int todo; // TODO Test with values

		// Test the special find methods of NavigableSet
		for(Integer v : sequence(30, v -> v * 2, true))
			map.put(v, v + 1);
		assertEquals(30, map.size());
		if(check != null)
			check.accept(map);
		assertEquals((Integer) 0, map.firstKey());
		assertEquals((Integer) 58, map.lastKey());
		assertEquals((Integer) 14, map.lowerKey(16));
		assertEquals((Integer) 18, map.higherKey(16));
		assertEquals((Integer) 14, map.floorKey(15));
		assertEquals((Integer) 16, map.ceilingKey(15));
		assertEquals((Integer) 16, map.floorKey(16));
		assertEquals((Integer) 16, map.ceilingKey(16));
		assertEquals((Integer) 0, map.pollFirstEntry().getKey());
		assertEquals(29, map.size());
		if(check != null)
			check.accept(map);
		assertEquals((Integer) 58, map.pollLastEntry().getKey());
		assertEquals(28, map.size());
		if(check != null)
			check.accept(map);
		assertEquals((Integer) 2, map.pollFirstEntry().getKey());
		assertEquals(27, map.size());
		if(check != null)
			check.accept(map);
		assertEquals((Integer) 56, map.pollLastEntry().getKey());
		assertEquals(26, map.size());
		if(check != null)
			check.accept(map);

		Iterator<Integer> desc = map.descendingKeySet().iterator(); // Test descendingIterator
		Integer last = null;
		while(desc.hasNext()) {
			Integer el = desc.next();
			if(last != null)
				assertThat(el, not(greaterThanOrEqual(last, map.comparator()))); // Strictly less than
			last = el;
		}

		// Test subsets
		Consumer<NavigableMap<Integer, Integer>> ssListener = ss -> {
			if(check != null)
				check.accept(map);
		};
		TreeMap<Integer, Integer> copy = new TreeMap<>(map);
		NavigableMap<Integer, Integer> subSet = (NavigableMap<Integer, Integer>) map.headMap(30);
		NavigableMap<Integer, Integer> copySubSet = (NavigableMap<Integer, Integer>) copy.headMap(30);
		assertThat(subSet, mapsEqual(copySubSet, true));
		testSubMap(subSet, null, true, 30, false, ssListener);

		subSet = map.headMap(30, true);
		copySubSet = copy.headMap(30, true);
		assertThat(subSet, mapsEqual(copySubSet, true));
		testSubMap(subSet, null, true, 30, true, ssListener);

		subSet = (NavigableMap<Integer, Integer>) map.tailMap(30);
		copySubSet = (NavigableMap<Integer, Integer>) copy.tailMap(30);
		assertThat(subSet, mapsEqual(copySubSet, true));
		testSubMap(subSet, 30, true, null, true, ssListener);

		subSet = map.tailMap(30, false);
		copySubSet = copy.tailMap(30, false);
		assertThat(subSet, mapsEqual(copySubSet, true));
		testSubMap(map.tailMap(30, false), 30, false, null, true, ssListener);

		subSet = (NavigableMap<Integer, Integer>) map.subMap(15, 45);
		copySubSet = (NavigableMap<Integer, Integer>) copy.subMap(15, 45);
		assertThat(subSet, mapsEqual(copySubSet, true));
		testSubMap(subSet, 15, true, 45, false, ssListener);

		int todo2; // TODO Test reversed maps
	}

	private static void testSubMap(NavigableMap<Integer, Integer> subMap, Integer min, boolean minInclude, Integer max, boolean maxInclude,
		Consumer<? super NavigableMap<Integer, Integer>> check) {
		int startSize = subMap.size();
		int size = startSize;
		ArrayList<Integer> remove = new ArrayList<>();
		if(min != null) {
			if(minInclude) {
				if(!subMap.containsKey(min)) {
					remove.add(min);
					size++;
				}
				subMap.put(min, -min);
				assertEquals(size, subMap.size());
				check.accept(subMap);
			}
			try {
				if(minInclude) {
					subMap.put(min - 1, 0);
				} else
					subMap.put(min, 0);
				assertTrue("SubSet should have thrown argument exception", false);
			} catch(IllegalArgumentException e) {
			}
		} else {
			subMap.put(Integer.MIN_VALUE, 0);
			size++;
			assertEquals(size, subMap.size());
			check.accept(subMap);
		}
		if(max != null) {
			if(maxInclude) {
				if(!subMap.containsKey(max)) {
					remove.add(max);
					size++;
				}
				subMap.put(max, -max);
				assertEquals(size, subMap.size());
				check.accept(subMap);
			}
			try {
				if(maxInclude)
					subMap.put(max + 1, 0);
				else
					subMap.put(max, 0);
				assertTrue("SubSet should have thrown argument exception", false);
			} catch(IllegalArgumentException e) {
			}
		} else {
			subMap.put(Integer.MAX_VALUE, -1);
			size++;
			assertEquals(size, subMap.size());
			check.accept(subMap);
		}
		remove.add(Integer.MIN_VALUE);
		remove.add(Integer.MAX_VALUE);
		subMap.removeAll(remove);
		assertEquals(startSize, subMap.size());
		check.accept(subMap);
	}
}
