package org.observe.datastruct;

import static java.util.Arrays.asList;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.observe.collect.ObservableCollectionsTest.collectionsEqual;
import static org.observe.collect.ObservableCollectionsTest.containsAll;
import static org.observe.collect.ObservableCollectionsTest.greaterThanOrEqual;
import static org.observe.collect.ObservableCollectionsTest.sequence;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Function;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.Subscription;
import org.observe.collect.ObservableCollectionsTest.Checker;
import org.observe.collect.ObservableOrderedCollection;
import org.observe.collect.ObservableSortedSet;

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

		boolean sorted = map.keySet() instanceof ObservableSortedSet;
		Map<Integer, Integer> synced = sorted ? new TreeMap<>() : new HashMap<>();
		Subscription syncSub = sync(map, synced);

		Function<Integer, Integer> mapFn = v -> v * 2;
		ObservableMap<Integer, Integer> mapped = map.map(mapFn);
		Map<Integer, Integer> syncMapped = sorted ? new TreeMap<>() : new HashMap<>();
		Subscription mappedSub = sync(mapped, syncMapped);

		int todo; // TODO Probably want more tests here

		return new Checker<ObservableMap<Integer, Integer>>() {
			@Override
			public void accept(ObservableMap<Integer, Integer> value) {
				assertThat(map, mapsEqual(synced, sorted));
				if(check != null)
					check.accept(map);

				Map<Integer, Integer> realMapped = sorted ? new TreeMap<>() : new HashMap<>();
				for(Map.Entry<Integer, Integer> entry : map.entrySet())
					realMapped.put(entry.getKey(), mapFn.apply(entry.getValue()));
				assertThat(realMapped, mapsEqual(mapped, sorted));
				assertThat(realMapped, mapsEqual(syncMapped, sorted));
			}

			@Override
			public void close() {
				syncSub.unsubscribe();
				mappedSub.unsubscribe();
			}
		};
	}

	private static <K, V> Subscription sync(ObservableMap<K, V> map, Map<K, V> syncMap) {
		return ((ObservableOrderedCollection<Map.Entry<K, V>>) map.entrySet()).onElement(entryEl -> {
			entryEl.subscribe(new Observer<ObservableValueEvent<Map.Entry<K, V>>>() {
				@Override
				public <V2 extends ObservableValueEvent<Entry<K, V>>> void onNext(V2 evt) {
					((ObservableMap.ObservableEntry<K, V>) evt.getValue()).takeUntil(entryEl)
					.subscribe(new Observer<ObservableValueEvent<V>>() {
						@Override
						public <V3 extends ObservableValueEvent<V>> void onNext(V3 evt2) {
							V oldValue = syncMap.put(evt.getValue().getKey(), evt2.getValue());
							assertEquals(evt.isInitial() ? null : evt2.getOldValue(), oldValue);
						}
					});
				}

				@Override
				public <V2 extends ObservableValueEvent<Entry<K, V>>> void onCompleted(V2 evt) {
					assertEquals(evt.getOldValue(), syncMap.remove(evt.getValue().getKey()));
				}
			});
		});
	}

	/**
	 * @param <T> The type of the key to check containment for
	 * @param value The key to check containment for
	 * @return A matcher that matches a map if it contains the given key
	 */
	public static <T> Matcher<Map<T, ?>> containsKey(T value) {
		return new org.hamcrest.BaseMatcher<Map<T, ?>>() {
			@Override
			public boolean matches(Object arg0) {
				return ((Map<T, ?>) arg0).containsKey(value);
			}

			@Override
			public void describeTo(Description arg0) {
				arg0.appendText("collection contains ").appendValue(value);
			}
		};
	}

	/**
	 * @param <T> The type of the value to check containment for
	 * @param value The value to check containment for
	 * @return A matcher that matches a map if it contains the given value
	 */
	public static <T> Matcher<Map<?, T>> containsValue(T value) {
		return new org.hamcrest.BaseMatcher<Map<?, T>>() {
			@Override
			public boolean matches(Object arg0) {
				return ((Map<?, T>) arg0).containsValue(value);
			}

			@Override
			public void describeTo(Description arg0) {
				arg0.appendText("collection contains ").appendValue(value);
			}
		};
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
				Map<K, V> arg = (Map<K, V>) arg0;
				if(!keyMatcher.matches(arg.keySet()))
					return false;
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
		// Most basic functionality, with iterator
		assertEquals(0, map.size()); // Start with empty map
		assertEquals(null, map.put(0, 1)); // Test put
		assertEquals(1, map.size()); // Test size
		if(check != null)
			check.accept(map);
		Iterator<Integer> iter = map.keySet().iterator(); // Test key iterator
		assertEquals(true, iter.hasNext());
		assertEquals(0, (int) iter.next());
		assertEquals(false, iter.hasNext());
		iter = map.keySet().iterator();
		assertEquals(true, iter.hasNext());
		assertEquals(0, (int) iter.next());
		iter.remove(); // Test iterator remove
		assertEquals(0, map.size());
		if(check != null)
			check.accept(map);
		assertEquals(false, iter.hasNext());
		iter = map.keySet().iterator();
		assertEquals(false, iter.hasNext());
		assertEquals(null, map.put(0, 1));
		assertEquals(1, map.size());
		assertFalse(map.isEmpty());
		if(check != null)
			check.accept(map);
		assertThat(map, containsKey(0)); // Test containsKey
		assertThat(map, containsValue(1)); // Test containsValue
		assertEquals(1, (int) map.get(0));
		assertEquals(1, (int) map.remove(0)); // Test remove
		assertEquals(null, map.remove(0));
		assertTrue(map.isEmpty()); // Test isEmpty
		if(check != null)
			check.accept(map);
		assertThat(map, not(containsKey(0)));
		assertThat(map, not(containsValue(1)));
		assertEquals(null, map.get(0));

		Map<Integer, Integer> toAdd = new HashMap<>();
		for(int i = 0; i < 50; i++)
			toAdd.put(i, i + 1);
		for(int i = 99; i >= 50; i--)
			toAdd.put(i, i + 1);
		map.putAll(toAdd); // Test putAll
		assertEquals(100, map.size());
		if(check != null)
			check.accept(map);
		assertThat(map.keySet(), containsAll(0, 75, 50, 11, 99, 50)); // 50 twice. Test containsAll
		// 100 not in map. 50 in list twice. Test removeAll.
		assertTrue(map.keySet().removeAll(
			// Easier to debug this way
			asList(0, 50, 100, 10, 90, 20, 80, 30, 70, 40, 60, 50)));
		assertEquals(90, map.size());
		if(check != null)
			check.accept(map);

		Map<Integer, Integer> copy = new HashMap<>(map); // More iterator testing
		assertThat(copy, mapsEqual(map, false));

		assertThat(map.keySet(), containsAll(1, 2, 11, 99));
		map.keySet().retainAll(
			// Easier to debug this way
			asList(1, 51, 101, 11, 91, 21, 81, 31, 71, 41, 61, 51)); // 101 not in map. 51 in list twice. Test retainAll.
		assertEquals(10, map.size());
		if(check != null)
			check.accept(map);
		assertThat(map.keySet(), not(containsAll(1, 2, 11, 99)));
		map.clear(); // Test clear
		assertEquals(0, map.size());
		if(check != null)
			check.accept(map);
		assertThat(map, not(containsKey(2)));
		// Leave the map empty

		assertEquals(null, map.put(0, 1));
		assertEquals(1, map.size());
		if(check != null)
			check.accept(map);
		assertEquals(null, map.put(1, 2));
		assertEquals(2, map.size());
		if(check != null)
			check.accept(map);
		assertEquals((Integer) 1, map.put(0, 2)); // Test uniqueness
		assertEquals((Integer) 2, map.put(0, 1));
		assertEquals(2, map.size());
		if(check != null)
			check.accept(map);
		assertEquals((Integer) 1, map.remove(0));
		assertEquals(1, map.size());
		if(check != null)
			check.accept(map);
		map.clear();
		assertEquals(0, map.size());
		if(check != null)
			check.accept(map);
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
		for(Integer rem : remove)
			subMap.remove(rem);
		assertEquals(startSize, subMap.size());
		check.accept(subMap);
	}
}
