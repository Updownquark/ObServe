package org.observe.collect;

import static java.util.Arrays.asList;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.Test;
import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.SimpleSettableValue;
import org.observe.Subscription;
import org.observe.collect.impl.ObservableArrayList;
import org.observe.collect.impl.ObservableHashSet;
import org.observe.collect.impl.ObservableLinkedList;
import org.observe.collect.impl.ObservableTreeList;
import org.observe.collect.impl.ObservableTreeSet;
import org.observe.datastruct.ObservableMultiMap;
import org.observe.util.ObservableUtils;
import org.qommons.Transaction;

import com.google.common.reflect.TypeToken;

import prisms.lang.Type;

/** Tests observable collections and their default implementations */
public class ObservableCollectionsTest {
	private static final int COLLECTION_TEST_DEPTH = 5;

	/**
	 * A predicate interface that helps with testing observable structures
	 *
	 * @param <T> The type of the item to check
	 */
	public static interface Checker<T> extends Consumer<T>, Transaction {
	}

	/**
	 * Runs a barrage of tests against a collection, observable or not
	 *
	 * @param <T> The type of the collection
	 * @param coll The collection to test
	 * @param check An optional check to run against the collection after every modification
	 */
	public static <T extends Collection<Integer>> void testCollection(T coll, Consumer<? super T> check) {
		testCollection(coll, check, 0);
	}

	private static <T extends Collection<Integer>> void testCollection(T coll, Consumer<? super T> check, int depth) {
		if(coll instanceof ObservableCollection)
			check = (Consumer<? super T>) testingObservableCollection((ObservableCollection<Integer>) coll,
				(Consumer<? super ObservableCollection<Integer>>) check, depth);

		if(coll instanceof List)
			testList((List<Integer>) coll, (Consumer<? super List<Integer>>) check, depth);
		else if(coll instanceof NavigableSet)
			testSortedSet((NavigableSet<Integer>) coll, (Consumer<? super NavigableSet<Integer>>) check);
		else if(coll instanceof Set)
			testSet((Set<Integer>) coll, (Consumer<? super Set<Integer>>) check);
		else
			testBasicCollection(coll, check);
	}

	private static <T extends ObservableCollection<Integer>> Checker<ObservableCollection<Integer>> testingObservableCollection(T coll,
		Consumer<? super T> check, int depth) {

		boolean ordered = coll instanceof ObservableOrderedCollection;
		ArrayList<Integer> synced = new ArrayList<>();
		Subscription syncSub = sync(coll, synced);

		// Quick test first
		coll.addAll(sequence(50, null, true));
		assertThat(coll, collectionsEqual(synced, ordered));
		if(check != null)
			check.accept(coll);
		coll.clear();
		assertThat(coll, collectionsEqual(synced, ordered));
		if(check != null)
			check.accept(coll);

		Function<Integer, Integer> mapFn = v -> v + 1000;
		Function<Integer, Integer> reverseMapFn = v -> v - 1000;
		ObservableCollection<Integer> mappedOL = coll.map(null, mapFn, reverseMapFn);
		ArrayList<Integer> mappedSynced = new ArrayList<>();
		Subscription mappedSub = sync(mappedOL, mappedSynced);

		Predicate<Integer> filterFn1 = v -> v % 3 == 0;
		ObservableCollection<Integer> filteredOL1 = coll.filter(filterFn1);
		ArrayList<Integer> filteredSynced1 = new ArrayList<>();
		Subscription filteredSub1 = sync(filteredOL1, filteredSynced1);

		Function<Integer, Integer> filterMap = v -> v;
		ObservableCollection<Integer> filterMapOL = coll.filterMap(null, filterMap, filterMap, false);
		ArrayList<Integer> filterMapSynced = new ArrayList<>();
		Subscription filterMapSub = sync(filterMapOL, filterMapSynced);

		Function<Integer, Integer> groupFn = v -> v % 3;
		ObservableMultiMap<Integer, Integer> grouped = coll.groupBy(groupFn);
		Map<Integer, List<Integer>> groupedSynced = new LinkedHashMap<>();
		ObservableCollectionsTest.sync(grouped, groupedSynced, () -> new ArrayList<>());

		BinaryOperator<Integer> combineFn = (v1, v2) -> v1 + v2;
		BinaryOperator<Integer> reverseCombineFn = (v1, v2) -> v1 - v2;
		SimpleSettableValue<Integer> combineVar = new SimpleSettableValue<>(Integer.class, false);
		combineVar.set(10000, null);
		ObservableCollection<Integer> combinedOL = coll.combine(combineVar, coll.getType(), combineFn, reverseCombineFn);
		ArrayList<Integer> combinedSynced = new ArrayList<>();
		Subscription combineSub = sync(combinedOL, combinedSynced);

		BinaryOperator<Integer> maxFn = (v1, v2) -> v1 >= v2 ? v1 : v2;
		ObservableValue<Integer> sum = coll.reduce(0, combineFn, reverseCombineFn);
		ObservableValue<Integer> max = coll.reduce(Integer.MIN_VALUE, maxFn);
		Integer [] observedSum = new Integer[1];
		Integer [] observedMax = new Integer[1];
		sum.value().act(v -> observedSum[0] = v);
		max.value().act(v -> {
			observedMax[0] = v;
		});

		// If sorted set, test on some synced sub-sets
		class SubSetRange {
			final Integer min;

			final Integer max;

			final boolean includeMin;

			final boolean includeMax;

			@SuppressWarnings("hiding")
			SubSetRange(Integer min, Integer max, boolean includeMin, boolean includeMax) {
				this.min = min;
				this.max = max;
				this.includeMin = includeMin;
				this.includeMax = includeMax;
			}
		}
		List<SubSetRange> subSetRanges;
		List<ObservableSortedSet<Integer>> subSets;
		List<List<Integer>> syncedSubSets;
		List<Subscription> syncedSubSetSubs;
		if(coll instanceof ObservableSortedSet) {
			ObservableSortedSet<Integer> sortedSet = (ObservableSortedSet<Integer>) coll;
			subSetRanges = new ArrayList<>();
			subSets = new ArrayList<>();
			syncedSubSets = new ArrayList<>();
			syncedSubSetSubs = new ArrayList<>();

			int [] divisions = new int[] {0, 15, 30, 45, 60, 100};
			int lastDiv = divisions[divisions.length - 1];
			subSetRanges.add(new SubSetRange(null, divisions[0], true, false));
			subSets.add(sortedSet.headSet(divisions[0]));
			subSetRanges.add(new SubSetRange(null, divisions[0], true, true));
			subSets.add(sortedSet.headSet(divisions[0], true));
			subSetRanges.add(new SubSetRange(lastDiv, null, true, true));
			subSets.add(sortedSet.tailSet(lastDiv));
			subSetRanges.add(new SubSetRange(lastDiv, null, false, true));
			subSets.add(sortedSet.tailSet(lastDiv, false));
			for(int i = 0; i < divisions.length - 1; i++) {
				subSetRanges.add(new SubSetRange(divisions[i], divisions[i + 1], true, false));
				subSets.add(sortedSet.subSet(divisions[i], divisions[i + 1]));

				subSetRanges.add(new SubSetRange(divisions[i], divisions[i + 1], true, true));
				subSets.add(sortedSet.subSet(divisions[i], true, divisions[i + 1], true));

				subSetRanges.add(new SubSetRange(divisions[i], divisions[i + 1], false, false));
				subSets.add(sortedSet.subSet(divisions[i], false, divisions[i + 1], false));

				subSetRanges.add(new SubSetRange(divisions[i], divisions[i + 1], false, true));
				subSets.add(sortedSet.subSet(divisions[i], false, divisions[i + 1], true));
			}

			for(int i = 0; i < subSets.size(); i++) {
				ArrayList<Integer> sync = new ArrayList<>();
				syncedSubSets.add(sync);
				syncedSubSetSubs.add(sync(subSets.get(i), sync));
			}
		} else {
			subSetRanges = null;
			subSets = null;
			syncedSubSets = null;
			syncedSubSetSubs = null;
		}

		return new Checker<ObservableCollection<Integer>>() {
			@Override
			public void accept(ObservableCollection<Integer> value) {
				assertThat(coll, collectionsEqual(synced, ordered));
				if(check != null)
					check.accept(coll);

				List<Integer> mappedCorrect = coll.stream().map(mapFn).collect(Collectors.toList());
				assertThat(mappedOL, collectionsEqual(mappedCorrect, ordered));
				assertThat(mappedSynced, collectionsEqual(mappedCorrect, ordered));

				List<Integer> filteredCorrect1 = coll.stream().filter(filterFn1).collect(Collectors.toList());
				assertThat(filteredOL1, collectionsEqual(filteredCorrect1, ordered));
				assertThat(filteredSynced1, collectionsEqual(filteredCorrect1, ordered));

				assertThat(filterMapOL, collectionsEqual(synced, ordered));
				assertThat(filterMapSynced, collectionsEqual(synced, ordered));

				Set<Integer> groupKeySet = synced.stream().map(groupFn).collect(Collectors.toSet());
				assertThat(grouped.keySet(), collectionsEqual(groupKeySet, false));
				assertThat(groupedSynced.keySet(), collectionsEqual(groupKeySet, false));
				for(Integer groupKey : groupKeySet) {
					List<Integer> values = synced.stream().filter(v -> Objects.equals(groupFn.apply(v), groupKey))
						.collect(Collectors.toList());
					assertThat(grouped.get(groupKey), collectionsEqual(values, ordered));
					assertThat(groupedSynced.get(groupKey), collectionsEqual(values, ordered));
				}

				List<Integer> combinedCorrect = coll.stream().map(v -> v + combineVar.get()).collect(Collectors.toList());
				assertThat(combinedOL, collectionsEqual(combinedCorrect, ordered));
				assertThat(combinedSynced, collectionsEqual(combinedCorrect, ordered));

				if(subSets != null) {
					for(int i = 0; i < subSets.size(); i++) {
						ObservableSortedSet<Integer> subSet = subSets.get(i);
						checkSubSet((ObservableSortedSet<Integer>) coll, subSet, subSetRanges.get(i));
						assertThat(syncedSubSets.get(i), collectionsEqual(subSet, true));
					}
				}

				Optional<Integer> actualSum = coll.stream().reduce(combineFn);
				if(actualSum.isPresent()) {
					assertEquals(actualSum.get(), sum.get());
					assertEquals(actualSum.get(), observedSum[0]);
				} else {
					assertEquals(Integer.valueOf(0), sum.get());
					assertEquals(Integer.valueOf(0), observedSum[0]);
				}
				Optional<Integer> actualMax = coll.stream().reduce(maxFn);
				if(actualMax.isPresent()) {
					assertEquals(actualMax.get(), max.get());
					assertEquals(actualMax.get(), observedMax[0]);
				} else {
					assertEquals(Integer.valueOf(Integer.MIN_VALUE), max.get());
					assertEquals(Integer.valueOf(Integer.MIN_VALUE), observedMax[0]);
				}
			}

			private void checkSubSet(ObservableSortedSet<Integer> sortedSet, ObservableSortedSet<Integer> subSet, SubSetRange range) {
				Iterator<Integer> outerIter = sortedSet.iterator();
				Iterator<Integer> innerIter = subSet.iterator();
				boolean isInRange = range.min == null;
				int innerCount = 0;
				while(outerIter.hasNext()) {
					Integer outerNext = outerIter.next();
					if(!isInRange) {
						int comp = outerNext.compareTo(range.min);
						if(comp > 0 || (range.includeMin && comp == 0))
							isInRange = true;
					}
					if(isInRange && range.max != null) {
						int comp = outerNext.compareTo(range.max);
						if(comp > 0 || (!range.includeMax && comp == 0))
							break;
					}
					if(isInRange) {
						innerCount++;
						Integer innerNext = innerIter.next();
						assertEquals(innerNext, outerNext);
					}
				}
				assertFalse(innerIter.hasNext());
				assertEquals(innerCount, subSet.size());
			}

			@Override
			public void close() {
				if(depth < COLLECTION_TEST_DEPTH) {
					testCollection(mappedOL, this, depth + 1);
					testCollection(filterMapOL, this, depth + 1);
					testCollection(combinedOL, this, depth + 1);
				}

				// Test filter adding
				filteredOL1.add(0);
				assertEquals(1, filteredOL1.size());
				accept(coll);

				try {
					filteredOL1.add(1);
					assertTrue("Should have thrown an IllegalArgumentException", false);
				} catch(IllegalArgumentException e) {
				}
				assertEquals(1, filteredOL1.size());
				accept(coll);

				filteredOL1.remove(0);
				assertEquals(0, filteredOL1.size());
				accept(coll);

				if(coll instanceof ObservableList) {
					ListIterator<Integer> listIter = ((List<Integer>) filteredOL1).listIterator();
					listIter.add(0);
					assertEquals(1, filteredOL1.size());
					accept(coll);
					try {
						listIter.add(1);
						assertTrue("Should have thrown an IllegalArgumentException", false);
					} catch(IllegalArgumentException e) {
					}
					listIter.previous();
					listIter.remove();
					assertEquals(0, filteredOL1.size());
					accept(coll);
				}

				syncSub.unsubscribe();
				mappedSub.unsubscribe();
				filteredSub1.unsubscribe();
				filterMapSub.unsubscribe();
				combineSub.unsubscribe();
				if(syncedSubSetSubs != null) {
					for(Subscription sub : syncedSubSetSubs)
						sub.unsubscribe();
				}
			}
		};
	}

	/**
	 * Adds a listener to keep a list updated with the contents of an observable collection
	 *
	 * @param <T> The type of elements in the collection
	 * @param coll The observable collection
	 * @param synced The unobservable collection to synchronize with the observable collection
	 * @return The subscription to use to terminate the synchronization
	 */
	public static <T> Subscription sync(ObservableCollection<T> coll, List<T> synced) {
		if(coll instanceof ObservableOrderedCollection)
			return ((ObservableOrderedCollection<T>) coll).onOrderedElement(el -> {
				el.subscribe(new Observer<ObservableValueEvent<T>>() {
					@Override
					public <V extends ObservableValueEvent<T>> void onNext(V evt) {
						if(evt.isInitial())
							synced.add(el.getIndex(), evt.getValue());
						else {
							assertEquals(evt.getOldValue(), synced.get(el.getIndex()));
							synced.set(el.getIndex(), evt.getValue());
						}
					}

					@Override
					public <V extends ObservableValueEvent<T>> void onCompleted(V evt) {
						assertEquals(evt.getValue(), synced.remove(el.getIndex()));
					}
				});
			});
		else
			return coll.onElement(el -> {
				el.subscribe(new Observer<ObservableValueEvent<T>>() {
					@Override
					public <V extends ObservableValueEvent<T>> void onNext(V evt) {
						if(evt.isInitial())
							synced.add(evt.getValue());
						else {
							assertEquals(evt.getOldValue(), synced.remove(evt.getOldValue()));
							synced.add(evt.getValue());
						}
					}

					@Override
					public <V extends ObservableValueEvent<T>> void onCompleted(V evt) {
						assertThat(synced, contains(evt.getValue()));
						synced.remove(evt.getValue());
					}
				});
			});
	}

	/**
	 * Adds a listener to keep a map of collections updated with the contents of an observable multi-map
	 *
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 * @param <C> The type of collection to create for the synced map
	 * @param map The observable map
	 * @param synced The unobservable map to synchronize with the observable collection
	 * @param collectCreator Creates collections for the unobservable map when a new key is observed in the observable map
	 * @return The subscription to use to terminate the synchronization
	 */
	public static <K, V, C extends Collection<V>> Subscription sync(ObservableMultiMap<K, V> map, Map<K, C> synced,
		Supplier<? extends C> collectCreator) {
		return map.keySet().onElement(el -> el.subscribe(new Observer<ObservableValueEvent<K>>() {
			@Override
			public <E extends ObservableValueEvent<K>> void onNext(E event) {
				if(!event.isInitial())
					return;
				assertEquals(null, synced.get(event.getValue()));
				C collect = collectCreator.get();
				synced.put(event.getValue(), collect);
				Subscription elSub = map.get(event.getValue()).onElement(el2 -> el2.subscribe(new Observer<ObservableValueEvent<V>>() {
					@Override
					public <E2 extends ObservableValueEvent<V>> void onNext(E2 event2) {
						if(el2 instanceof ObservableOrderedElement && collect instanceof List) {
							ObservableOrderedElement<V> orderedEl = (ObservableOrderedElement<V>) el2;
							if(event2.isInitial())
								((List<V>) collect).add(orderedEl.getIndex(), event2.getValue());
							else {
								assertEquals(event2.getOldValue(), ((List<V>) collect).get(orderedEl.getIndex()));
								((List<V>) collect).set(orderedEl.getIndex(), event2.getValue());
							}
						} else {
							if(event2.isInitial())
								collect.add(event2.getValue());
							else {
								assertEquals(event2.getOldValue(), collect.remove(event2.getOldValue()));
								collect.add(event2.getValue());
							}
						}
					}

					@Override
					public <E2 extends ObservableValueEvent<V>> void onCompleted(E2 event2) {
						if(el2 instanceof ObservableOrderedElement && collect instanceof List) {
							ObservableOrderedElement<V> orderedEl = (ObservableOrderedElement<V>) el2;
							assertEquals(event2.getValue(), ((List<V>) collect).remove(orderedEl.getIndex()));
						} else {
							assertThat(collect, contains(event2.getValue()));
							collect.remove(event2.getValue());
						}
					}
				}));
				el.completed().act(evt -> elSub.unsubscribe());
			}

			@Override
			public <E extends ObservableValueEvent<K>> void onCompleted(E event) {
				assertThat(synced.remove(event.getValue()), notNullValue());
			}
		}));
	}

	/**
	 * Runs a collection through a set of tests designed to ensure all {@link Collection} methods are functioning correctly
	 *
	 * @param <T> The type of the collection
	 * @param coll The collection to test
	 * @param check An optional function to apply after each collection modification to ensure the structure of the collection is correct
	 *            and potentially assert other side effects of collection modification
	 */
	private static <T extends Collection<Integer>> void testBasicCollection(T coll, Consumer<? super T> check) {
		// Most basic functionality, with iterator
		assertEquals(0, coll.size()); // Start with empty list
		assertTrue(coll.add(0)); //Test add
		assertEquals(1, coll.size()); // Test size
		if(check != null)
			check.accept(coll);
		Iterator<Integer> iter = coll.iterator(); //Test iterator
		assertEquals(true, iter.hasNext());
		assertEquals(0, (int) iter.next());
		assertEquals(false, iter.hasNext());
		iter = coll.iterator();
		assertEquals(true, iter.hasNext());
		assertEquals(0, (int) iter.next());
		iter.remove(); //Test iterator remove
		assertEquals(0, coll.size());
		if(check != null)
			check.accept(coll);
		assertEquals(false, iter.hasNext());
		iter = coll.iterator();
		assertEquals(false, iter.hasNext());
		assertTrue(coll.add(0));
		assertEquals(1, coll.size());
		assertFalse(coll.isEmpty());
		if(check != null)
			check.accept(coll);
		assertThat(coll, contains(0)); // Test contains
		assertTrue(coll.remove(0)); //Test remove
		assertFalse(coll.remove(0));
		assertTrue(coll.isEmpty()); // Test isEmpty
		if(check != null)
			check.accept(coll);
		assertThat(coll, not(contains(0)));

		ArrayList<Integer> toAdd=new ArrayList<>();
		for(int i = 0; i < 50; i++)
			toAdd.add(i);
		for(int i = 99; i >= 50; i--)
			toAdd.add(i);
		assertTrue(coll.addAll(toAdd)); // Test addAll
		assertEquals(100, coll.size());
		if(check != null)
			check.accept(coll);
		assertThat(coll, containsAll(0, 75, 50, 11, 99, 50)); // 50 twice. Test containsAll
		// 100 not in coll. 50 in list twice. Test removeAll.
		assertTrue(coll.removeAll(
			// Easier to debug this way
			asList(0, 50, 100, 10, 90, 20, 80, 30, 70, 40, 60, 50)));
		assertEquals(90, coll.size());
		if(check != null)
			check.accept(coll);

		ArrayList<Integer> copy = new ArrayList<>(coll); // More iterator testing
		assertThat(copy, collectionsEqual(coll, coll instanceof List));

		assertThat(coll, containsAll(1, 2, 11, 99));
		coll.retainAll(
			// Easier to debug this way
			asList(1, 51, 101, 11, 91, 21, 81, 31, 71, 41, 61, 51)); // 101 not in coll. 51 in list twice. Test retainAll.
		assertEquals(10, coll.size());
		if(check != null)
			check.accept(coll);
		assertThat(coll, not(containsAll(1, 2, 11, 99)));
		coll.clear(); // Test clear
		assertEquals(0, coll.size());
		if(check != null)
			check.accept(coll);
		assertThat(coll, not(contains(2)));
		// Leave the collection empty

		// Not testing toArray() methods. These are pretty simple, but should probably put those in some time.
	}

	private static <T extends Set<Integer>> void testSet(T set, Consumer<? super T> check) {
		testBasicCollection(set, check);

		assertTrue(set.add(0));
		assertEquals(1, set.size());
		if(check != null)
			check.accept(set);
		assertTrue(set.add(1));
		assertEquals(2, set.size());
		if(check != null)
			check.accept(set);
		assertFalse(set.add(0)); // Test uniqueness
		assertEquals(2, set.size());
		if(check != null)
			check.accept(set);
		assertTrue(set.remove(0));
		assertEquals(1, set.size());
		if(check != null)
			check.accept(set);
		set.clear();
		assertEquals(0, set.size());
		if(check != null)
			check.accept(set);
	}

	private static <T extends NavigableSet<Integer>> void testSortedSet(T set, Consumer<? super T> check) {
		testSet(set, coll -> {
			Comparator<? super Integer> comp = set.comparator();
			Integer last = null;
			for(Integer el : coll) {
				if(last != null)
					assertThat(el, greaterThanOrEqual(last, comp));
				last = el;
			}

			if(check != null)
				check.accept(coll);
		});

		// Test the special find methods of NavigableSet
		set.addAll(sequence(30, v -> v * 2, true));
		assertEquals(30, set.size());
		if(check != null)
			check.accept(set);
		assertEquals((Integer) 0, set.first());
		assertEquals((Integer) 58, set.last());
		assertEquals((Integer) 14, set.lower(16));
		assertEquals((Integer) 18, set.higher(16));
		assertEquals((Integer) 14, set.floor(15));
		assertEquals((Integer) 16, set.ceiling(15));
		assertEquals((Integer) 16, set.floor(16));
		assertEquals((Integer) 16, set.ceiling(16));
		assertEquals((Integer) 0, set.pollFirst());
		assertEquals(29, set.size());
		if(check != null)
			check.accept(set);
		assertEquals((Integer) 58, set.pollLast());
		assertEquals(28, set.size());
		if(check != null)
			check.accept(set);
		assertEquals((Integer) 2, set.pollFirst());
		assertEquals(27, set.size());
		if(check != null)
			check.accept(set);
		assertEquals((Integer) 56, set.pollLast());
		assertEquals(26, set.size());
		if(check != null)
			check.accept(set);

		Iterator<Integer> desc = set.descendingIterator(); // Test descendingIterator
		Integer last = null;
		while(desc.hasNext()) {
			Integer el = desc.next();
			if(last != null)
				assertThat(el, not(greaterThanOrEqual(last, set.comparator()))); // Strictly less than
			last = el;
		}

		// Test subsets
		Consumer<NavigableSet<Integer>> ssListener = ss -> {
			if(check != null)
				check.accept(set);
		};
		TreeSet<Integer> copy = new TreeSet<>(set);
		NavigableSet<Integer> subSet = (NavigableSet<Integer>) set.headSet(30);
		NavigableSet<Integer> copySubSet = (NavigableSet<Integer>) copy.headSet(30);
		assertThat(subSet, collectionsEqual(copySubSet, true));
		testSubSet(subSet, null, true, 30, false, ssListener);

		subSet = set.headSet(30, true);
		copySubSet = copy.headSet(30, true);
		assertThat(subSet, collectionsEqual(copySubSet, true));
		testSubSet(subSet, null, true, 30, true, ssListener);

		subSet = (NavigableSet<Integer>) set.tailSet(30);
		copySubSet = (NavigableSet<Integer>) copy.tailSet(30);
		assertThat(subSet, collectionsEqual(copySubSet, true));
		testSubSet(subSet, 30, true, null, true, ssListener);

		subSet = set.tailSet(30, false);
		copySubSet = copy.tailSet(30, false);
		assertThat(subSet, collectionsEqual(copySubSet, true));
		testSubSet(set.tailSet(30, false), 30, false, null, true, ssListener);

		ssListener.accept(set);

		subSet = (NavigableSet<Integer>) set.subSet(15, 45);
		copySubSet = (NavigableSet<Integer>) copy.subSet(15, 45);
		assertThat(subSet, collectionsEqual(copySubSet, true));
		testSubSet(subSet, 15, true, 45, false, ssListener);

		int todo; // TODO Test reversed sets
	}

	private static void testSubSet(NavigableSet<Integer> subSet, Integer min, boolean minInclude, Integer max, boolean maxInclude,
		Consumer<? super NavigableSet<Integer>> check) {
		int startSize = subSet.size();
		int size = startSize;
		ArrayList<Integer> remove = new ArrayList<>();
		if(min != null) {
			if(minInclude) {
				if(!subSet.contains(min)) {
					remove.add(min);
					size++;
				}
				subSet.add(min);
				assertEquals(size, subSet.size());
				check.accept(subSet);
			}
			try {
				if(minInclude) {
					subSet.add(min - 1);
				} else
					subSet.add(min);
				assertTrue("SubSet should have thrown argument exception", false);
			} catch(IllegalArgumentException e) {
			}
		} else {
			subSet.add(Integer.MIN_VALUE);
			size++;
			assertEquals(size, subSet.size());
			check.accept(subSet);
		}
		if(max != null) {
			if(maxInclude) {
				if(!subSet.contains(max)) {
					remove.add(max);
					size++;
				}
				subSet.add(max);
				assertEquals(size, subSet.size());
				check.accept(subSet);
			}
			try {
				if(maxInclude)
					subSet.add(max + 1);
				else
					subSet.add(max);
				assertTrue("SubSet should have thrown argument exception", false);
			} catch(IllegalArgumentException e) {
			}
		} else {
			subSet.add(Integer.MAX_VALUE);
			size++;
			assertEquals(size, subSet.size());
			check.accept(subSet);
		}
		remove.add(Integer.MIN_VALUE);
		remove.add(Integer.MAX_VALUE);
		subSet.removeAll(remove);
		assertEquals(startSize, subSet.size());
		check.accept(subSet);
	}

	private static <T extends List<Integer>> void testList(T list, Consumer<? super T> check, int depth) {
		testBasicCollection(list, check);

		assertTrue(list.addAll(
			// Easier to debug this way
			sequence(10, null, false)));
		assertEquals(10, list.size());
		if(check != null)
			check.accept(list);
		assertTrue(list.add(0)); // Test non-uniqueness
		assertEquals(11, list.size());
		if(check != null)
			check.accept(list);
		assertEquals((Integer) 0, list.remove(10));
		assertEquals(10, list.size());
		if(check != null)
			check.accept(list);
		assertTrue(list.addAll(
			// Easier to debug this way
			sequence(10, v -> v + 20, false)));
		assertEquals(20, list.size());
		if(check != null)
			check.accept(list);
		assertTrue(list.addAll(10,
			// Easier to debug this way
			sequence(10, v -> v + 10, false))); // Test addAll at index
		assertEquals(30, list.size());
		if(check != null)
			check.accept(list);
		for(int i = 0; i < 30; i++)
			assertEquals((Integer) i, list.get(i)); // Test get

		// Test range checks
		try {
			list.remove(-1);
			assertTrue("List should have thrown out of bounds exception", false);
		} catch(IndexOutOfBoundsException e) {
		}
		try {
			list.remove(list.size());
			assertTrue("List should have thrown out of bounds exception", false);
		} catch(IndexOutOfBoundsException e) {
		}
		try {
			list.add(-1, 0);
			assertTrue("List should have thrown out of bounds exception", false);
		} catch(IndexOutOfBoundsException e) {
		}
		try {
			list.add(list.size() + 1, 0);
			assertTrue("List should have thrown out of bounds exception", false);
		} catch(IndexOutOfBoundsException e) {
		}
		try {
			list.set(-1, 0);
			assertTrue("List should have thrown out of bounds exception", false);
		} catch(IndexOutOfBoundsException e) {
		}
		try {
			list.set(list.size(), 0);
			assertTrue("List should have thrown out of bounds exception", false);
		} catch(IndexOutOfBoundsException e) {
		}

		for(int i = 0; i < 30; i++)
			assertEquals(i, list.indexOf(i)); // Test indexOf
		list.add(0);
		list.add(1);
		if(check != null)
			check.accept(list);
		assertEquals(0, list.indexOf(0)); // Test indexOf with duplicate values
		assertEquals(30, list.lastIndexOf(0)); // Test lastIndexOf
		list.remove(31);
		list.remove(30);
		for(int i = 0; i < 30; i++) {
			assertEquals("i=" + i, (Integer) i, list.set(i, 30 - i - 1)); // Test set
			if(check != null)
				check.accept(list);
		}
		assertEquals(30, list.size());
		for(int i = 0; i < 30; i++)
			assertEquals(30 - i - 1, list.indexOf(i));
		if(check != null)
			check.accept(list);
		for(int i = 0; i < 30; i++) {
			assertEquals((Integer) (30 - i - 1), list.set(i, 30 - i - 1));
			if(check != null)
				check.accept(list);
		}
		assertTrue(list.remove((Integer) 10));
		assertEquals(29, list.size());
		if(check != null)
			check.accept(list);
		list.add(10, 10); // Test add at index
		assertEquals(30, list.size());
		if(check != null)
			check.accept(list);

		{// This is here so I'm sure this part of the test is valid
			ArrayList<Integer> test00 = new ArrayList<>(list.size());
			for(int i = 0; i < list.size(); i++)
				test00.add(null);
			ArrayList<Integer> test0 = new ArrayList<>(sequence(30, null, false));
			ListIterator<Integer> listIter01 = test0.listIterator(test0.size() / 2);
			ListIterator<Integer> listIter02 = test0.listIterator(test0.size() / 2);
			while(true) {
				boolean stop = true;
				if(listIter01.hasPrevious()) {
					test00.set(listIter01.previousIndex(), listIter01.previous());
					stop = false;
				}
				if(listIter02.hasNext()) {
					test00.set(listIter02.nextIndex(), listIter02.next());
					stop = false;
				}
				if(stop)
					break;
			}
			assertThat(test00, equalTo(test0));
		}

		// Test listIterator
		ArrayList<Integer> test = new ArrayList<>(list.size());
		for(int i = 0; i < list.size(); i++)
			test.add(null);
		ListIterator<Integer> listIter1 = list.listIterator(list.size() / 2); // Basic bi-directional read-only functionality
		ListIterator<Integer> listIter2 = list.listIterator(list.size() / 2);
		while(true) {
			boolean stop = true;
			if(listIter1.hasPrevious()) {
				test.set(listIter1.previousIndex(), listIter1.previous());
				stop = false;
			}
			if(listIter2.hasNext()) {
				test.set(listIter2.nextIndex(), listIter2.next());
				stop = false;
			}
			if(stop)
				break;
		}
		assertThat(test, equalTo(list));

		// Test listIterator modification
		listIter1 = list.listIterator(list.size() / 2);
		listIter2 = test.listIterator(list.size() / 2);
		int i;
		for(i = 0; listIter2.hasPrevious(); i++) {
			assertTrue("On Iteration " + i, listIter1.hasPrevious());
			int prev = listIter1.previous();
			assertThat("On Iteration " + i, listIter2.previous(), equalTo(prev));
			switch (i % 5) {
			case 0:
				int toAdd=i * 17 + 100;
				listIter1.add(toAdd);
				listIter2.add(toAdd);
				assertTrue("On Iteration " + i, listIter1.hasPrevious());
				assertThat("On Iteration " + i, listIter1.previous(), equalTo(toAdd)); // Back up over the added value
				listIter2.previous();
				break;
			case 1:
				listIter1.remove();
				listIter2.remove();
				break;
			case 2:
				listIter1.set(prev + 50);
				listIter2.set(prev + 50);
				break;
			}
			assertThat("On Iteration " + i, list, collectionsEqual(test, true));
			if(check != null)
				check.accept(list);
		}
		for(i = 0; listIter2.hasNext(); i++) {
			assertTrue("On Iteration " + i, listIter1.hasNext());
			int next = listIter1.next();
			assertThat("On Iteration " + i, listIter2.next(), equalTo(next));
			switch (i % 5) {
			case 0:
				int toAdd=i*53+1000;
				listIter1.add(toAdd);
				listIter2.add(toAdd);
				assertTrue("On Iteration " + i, listIter1.hasPrevious());
				assertThat("On Iteration " + i, toAdd, equalTo(listIter1.previous()));
				listIter1.next();
				break;
			case 1:
				listIter1.remove();
				listIter2.remove();
				break;
			case 2:
				listIter1.set(next + 1000);
				listIter2.set(next + 1000);
				break;
			}
			assertThat("On Iteration " + i, test, equalTo(list));
			if(check != null)
				check.accept(list);
		}

		list.clear();
		if(check != null)
			check.accept(list);
		if(depth + 1 < COLLECTION_TEST_DEPTH) {
			// Test subList
			list.addAll(sequence(30, null, false));
			if(check != null)
				check.accept(list);
			int subIndex = list.size() / 2;
			List<Integer> subList = list.subList(subIndex, subIndex + 5);
			assertEquals(5, subList.size());
			for(i = 0; i < subList.size(); i++)
				assertEquals((Integer) (subIndex + i), subList.get(i));
			i = 0;
			for(Integer el : subList)
				assertEquals((Integer) (subIndex + i++), el);
			subList.remove(0);
			assertEquals(4, subList.size());
			assertThat(list, not(contains(subIndex)));
			if(check != null)
				check.accept(list);
			subList.add(0, subIndex);
			assertThat(list, contains(subIndex));
			if(check != null)
				check.accept(list);
			try {
				subList.remove(-1);
				assertTrue("SubList should have thrown out of bounds exception", false);
			} catch(IndexOutOfBoundsException e) {
			}
			try {
				subList.remove(subList.size());
				assertTrue("SubList should have thrown out of bounds exception", false);
			} catch(IndexOutOfBoundsException e) {
			}
			assertEquals(30, list.size());
			assertEquals(5, subList.size());
			subList.clear();
			assertEquals(25, list.size());
			assertEquals(0, subList.size());
			if(check != null)
				check.accept(list);

			testCollection(subList, sl -> {
				assertEquals(list.size(), 25 + sl.size());
				for(int j = 0; j < list.size(); j++) {
					if(j < subIndex)
						assertEquals((Integer) j, list.get(j));
					else if(j < subIndex + sl.size())
						assertEquals(sl.get(j - subIndex), list.get(j));
					else
						assertEquals((Integer) (j - sl.size() + 5), list.get(j));
				}
				if(check != null)
					check.accept(list);
			}, depth + 1);
		}
		list.clear();
		assertEquals(0, list.size());
		if(check != null)
			check.accept(list);
	}

	/**
	 * @param <T> The type of the value to check containment for
	 * @param value The value to check containment for
	 * @return A matcher that matches a collection if it contains the given value
	 */
	public static <T> Matcher<Collection<T>> contains(T value) {
		return new org.hamcrest.BaseMatcher<Collection<T>>() {
			@Override
			public boolean matches(Object arg0) {
				return ((Collection<T>) arg0).contains(value);
			}

			@Override
			public void describeTo(Description arg0) {
				arg0.appendText("collection contains ").appendValue(value);
			}
		};
	}

	/**
	 * @param <T> The type of the values to check containment for
	 * @param values The values to check containment for
	 * @return A matcher that matches a collection if it contains all of the given values
	 */
	public static <T> Matcher<Collection<T>> containsAll(T... values) {
		return containsAll(asList(values));
	}

	/**
	 * @param <T> The type of the values to check containment for
	 * @param values The values to check containment for
	 * @return A matcher that matches a collection if it contains all of the given values
	 */
	public static <T> Matcher<Collection<T>> containsAll(Collection<T> values) {
		return new org.hamcrest.BaseMatcher<Collection<T>>() {
			@Override
			public boolean matches(Object arg0) {
				return ((Collection<T>) arg0).containsAll(values);
			}

			@Override
			public void describeTo(Description arg0) {
				arg0.appendText("collection contains all of ").appendValue(values);
			}
		};
	}

	/**
	 * @param <T> The element type of the collection
	 * @param values The collection to test against
	 * @param ordered Whether to test the equality of the collections as if order matters
	 * @return A matcher that matches a collection c if c is equivalent to the given collection, in an ordered way if specified
	 */
	public static <T> Matcher<Collection<T>> collectionsEqual(Collection<T> values, boolean ordered) {
		return new org.hamcrest.BaseMatcher<Collection<T>>() {
			@Override
			public boolean matches(Object arg0) {
				Collection<T> arg = (Collection<T>) arg0;
				if(arg.size() != values.size())
					return false;
				if(ordered) {
					// Must be equivalent
					Iterator<T> vIter = values.iterator();
					Iterator<T> aIter = arg.iterator();
					while(vIter.hasNext() && aIter.hasNext())
						if(!Objects.equals(vIter.next(), aIter.next()))
							return false;
					if(vIter.hasNext() || aIter.hasNext())
						return false;
					return true;
				} else {
					return values.containsAll(arg);
				}
			}

			@Override
			public void describeTo(Description arg0) {
				arg0.appendText("collection equivalent to ").appendValue(values);
			}
		};
	}

	/**
	 * Creates a sequence of values to test against
	 *
	 * @param <T> The type of the sequence
	 * @param num The number of items for the sequence
	 * @param map The map to transform the sequence indexes into sequence values
	 * @param scramble Whether to scramble the sequence in a reproducible way
	 * @return The sequence
	 */
	public static <T> Collection<T> sequence(int num, Function<Integer, T> map, boolean scramble) {
		ArrayList<T> ret = new ArrayList<>();
		for(int i = 0; i < num; i++)
			ret.add(null);
		for(int i = 0; i < num; i++) {
			T value;
			if(map != null)
				value = map.apply(i);
			else
				value = (T) (Integer) i;
			int index = i;
			if(scramble) {
				switch (i % 3) {
				case 0:
					index = i;
					break;
				case 1:
					index = i + 3;
					if(index >= num)
						index = 1;
					break;
				default:
					index = ((num / 3) - (i / 3) - 1) * 3 + 2;
					break;
				}
			}
			ret.set(index, value);
		}

		return Collections.unmodifiableCollection(ret);
	}

	/**
	 * @param <T> The value type
	 * @param value The value to compare against
	 * @param comp The comparator to do the comparison
	 * @return A matcher that matches a value v if v is greater than or equal to the <code>value</code>.
	 */
	public static <T> Matcher<T> greaterThanOrEqual(T value, Comparator<? super T> comp) {
		return new org.hamcrest.BaseMatcher<T>() {
			@Override
			public boolean matches(Object arg0) {
				return comp.compare((T) arg0, value) >= 1;
			}

			@Override
			public void describeTo(Description arg0) {
				arg0.appendText("value is not greater than " + value);
			}
		};
	}

	/** Runs a barrage of tests on {@link ObservableArrayList} */
	@Test
	public void testObservableArrayList() {
		testCollection(new ObservableArrayList<>(TypeToken.of(Integer.class)), null, 0);
	}

	/** Runs a barrage of tests on {@link ObservableLinkedList} */
	@Test
	public void testObservableLinkedList() {
		testCollection(new ObservableLinkedList<>(TypeToken.of(Integer.class)),
			// Easier to debug this way
			list -> list.validate(), 0);
	}

	/** Runs a barrage of tests on {@link ObservableTreeList} */
	@Test
	public void testObservableTreeList() {
		testCollection(new ObservableTreeList<>(TypeToken.of(Integer.class)), null, 0);
	}

	/** Runs a barrage of tests on {@link ObservableHashSet} */
	@Test
	public void testObservableHashSet() {
		testCollection(new ObservableHashSet<>(TypeToken.of(Integer.class)), null);
	}

	/** Runs a barrage of tests on {@link ObservableTreeSet} */
	@Test
	public void testObservableTreeSet() {
		testCollection(new ObservableTreeSet<>(TypeToken.of(Integer.class), Integer::compareTo), set -> set.checkValid());
	}

	// Older, more specific tests

	/** Tests basic {@link ObservableSet} functionality */
	@Test
	public void observableSet() {
		ObservableHashSet<Integer> set = new ObservableHashSet<>(TypeToken.of(Integer.TYPE));
		Set<Integer> compare1 = new TreeSet<>();
		Set<Integer> correct = new TreeSet<>();
		Subscription sub = set.onElement(element -> {
			element.subscribe(new Observer<ObservableValueEvent<Integer>>() {
				@Override
				public <V extends ObservableValueEvent<Integer>> void onNext(V value) {
					compare1.add(value.getValue());
				}

				@Override
				public <V extends ObservableValueEvent<Integer>> void onCompleted(V value) {
					compare1.remove(value.getValue());
				}
			});
		});

		for(int i = 0; i < 30; i++) {
			set.add(i);
			correct.add(i);
			assertEquals(new TreeSet<>(set), compare1);
			assertEquals(correct, compare1);
		}
		for(int i = 0; i < 30; i++) {
			set.remove(i);
			correct.remove(i);
			assertEquals(new TreeSet<>(set), compare1);
			assertEquals(correct, compare1);
		}

		for(int i = 0; i < 30; i++) {
			set.add(i);
			correct.add(i);
			assertEquals(new TreeSet<>(set), compare1);
			assertEquals(correct, compare1);
		}
		sub.unsubscribe();
		for(int i = 30; i < 50; i++) {
			set.add(i);
			assertEquals(correct, compare1);
		}
		for(int i = 0; i < 30; i++) {
			set.remove(i);
			assertEquals(correct, compare1);
		}
	}

	/** Tests basic {@link ObservableList} functionality */
	@Test
	public void observableList() {
		ObservableArrayList<Integer> list = new ObservableArrayList<>(TypeToken.of(Integer.TYPE));
		List<Integer> compare1 = new ArrayList<>();
		List<Integer> correct = new ArrayList<>();
		Subscription sub = list.onElement(element -> {
			ObservableOrderedElement<Integer> listEl = (ObservableOrderedElement<Integer>) element;
			element.subscribe(new Observer<ObservableValueEvent<Integer>>() {
				@Override
				public <V extends ObservableValueEvent<Integer>> void onNext(V value) {
					compare1.add(listEl.getIndex(), value.getValue());
				}

				@Override
				public <V extends ObservableValueEvent<Integer>> void onCompleted(V value) {
					compare1.remove(listEl.getIndex());
				}
			});
		});

		for(int i = 0; i < 30; i++) {
			list.add(i);
			correct.add(i);
			assertEquals(new ArrayList<>(list), compare1);
			assertEquals(correct, compare1);
		}
		for(int i = 0; i < 30; i++) {
			list.remove((Integer) i);
			correct.remove((Integer) i);
			assertEquals(new ArrayList<>(list), compare1);
			assertEquals(correct, compare1);
		}

		for(int i = 0; i < 30; i++) {
			list.add(i);
			correct.add(i);
			assertEquals(new ArrayList<>(list), compare1);
			assertEquals(correct, compare1);
		}
		sub.unsubscribe();
		for(int i = 30; i < 50; i++) {
			list.add(i);
			assertEquals(correct, compare1);
		}
		for(int i = 0; i < 30; i++) {
			list.remove((Integer) i);
			assertEquals(correct, compare1);
		}
	}

	/** Tests {@link ObservableSet#map(java.util.function.Function)} */
	@Test
	public void observableSetMap() {
		ObservableHashSet<Integer> set = new ObservableHashSet<>(TypeToken.of(Integer.TYPE));
		Set<Integer> compare1 = new TreeSet<>();
		Set<Integer> correct = new TreeSet<>();
		set.map(value -> value * 10).onElement(element -> {
			element.subscribe(new Observer<ObservableValueEvent<Integer>>() {
				@Override
				public <V extends ObservableValueEvent<Integer>> void onNext(V value) {
					compare1.add(value.getValue());
				}

				@Override
				public <V extends ObservableValueEvent<Integer>> void onCompleted(V value) {
					compare1.remove(value.getValue());
				}
			});
		});

		for(int i = 0; i < 30; i++) {
			set.add(i);
			correct.add(i * 10);
			assertEquals(correct, compare1);
		}
		for(int i = 0; i < 30; i++) {
			set.remove(i);
			correct.remove(i * 10);
			assertEquals(correct, compare1);
		}
	}

	/** Tests {@link ObservableSet#filter(java.util.function.Predicate)} */
	@Test
	public void observableSetFilter() {
		ObservableHashSet<Integer> set = new ObservableHashSet<>(TypeToken.of(Integer.TYPE));
		Set<Integer> compare1 = new TreeSet<>();
		Set<Integer> correct = new TreeSet<>();
		set.filter(value -> (value != null && value % 2 == 0)).onElement(element -> {
			element.subscribe(new Observer<ObservableValueEvent<Integer>>() {
				@Override
				public <V extends ObservableValueEvent<Integer>> void onNext(V value) {
					compare1.add(value.getValue());
				}

				@Override
				public <V extends ObservableValueEvent<Integer>> void onCompleted(V value) {
					compare1.remove(value.getValue());
				}
			});
		});

		for(int i = 0; i < 30; i++) {
			set.add(i);
			if(i % 2 == 0)
				correct.add(i);
			assertEquals(correct, compare1);
		}
		for(int i = 0; i < 30; i++) {
			set.remove(i);
			if(i % 2 == 0)
				correct.remove(i);
			assertEquals(correct, compare1);
		}
	}

	/** Tests {@link ObservableSet#filterMap(java.util.function.Function)} */
	@Test
	public void observableSetFilterMap() {
		ObservableHashSet<Integer> set = new ObservableHashSet<>(TypeToken.of(Integer.TYPE));
		Set<Integer> compare1 = new TreeSet<>();
		Set<Integer> correct = new TreeSet<>();
		set.filterMap(value -> {
			if(value == null || value % 2 != 0)
				return null;
			return value / 2;
		}).onElement(element -> {
			element.subscribe(new Observer<ObservableValueEvent<Integer>>() {
				@Override
				public <V extends ObservableValueEvent<Integer>> void onNext(V value) {
					compare1.add(value.getValue());
				}

				@Override
				public <V extends ObservableValueEvent<Integer>> void onCompleted(V value) {
					compare1.remove(value.getValue());
				}
			});
		});

		for(int i = 0; i < 30; i++) {
			set.add(i);
			if(i % 2 == 0)
				correct.add(i / 2);
			assertEquals(correct, compare1);
		}
		for(int i = 0; i < 30; i++) {
			set.remove(i);
			if(i % 2 == 0)
				correct.remove(i / 2);
			assertEquals(correct, compare1);
		}
	}

	/** Tests {@link ObservableSet#combine(ObservableValue, java.util.function.BiFunction)} */
	@Test
	public void observableSetCombine() {
		ObservableHashSet<Integer> set = new ObservableHashSet<>(TypeToken.of(Integer.TYPE));
		SimpleSettableValue<Integer> value1 = new SimpleSettableValue<>(Integer.TYPE, false);
		value1.set(1, null);
		List<Integer> compare1 = new ArrayList<>();
		Set<Integer> correct = new TreeSet<>();
		set.combine(value1, (v1, v2) -> v1 * v2).filter(value -> value != null && value % 3 == 0).onElement(element -> {
			element.subscribe(new Observer<ObservableValueEvent<Integer>>() {
				@Override
				public <V extends ObservableValueEvent<Integer>> void onNext(V event) {
					if(!event.isInitial())
						compare1.remove(event.getOldValue());
					compare1.add(event.getValue());
				}

				@Override
				public <V extends ObservableValueEvent<Integer>> void onCompleted(V event) {
					compare1.remove(event.getValue());
				}
			});
		});

		for(int i = 0; i < 30; i++) {
			set.add(i);
			int value = i * value1.get();
			if(value % 3 == 0)
				correct.add(value);
			assertEquals(correct, new TreeSet<>(compare1));
			assertEquals(correct.size(), compare1.size());
		}

		value1.set(3, null);
		correct.clear();
		for(int i = 0; i < 30; i++) {
			int value = i * value1.get();
			if(value % 3 == 0)
				correct.add(value);
		}
		assertEquals(correct, new TreeSet<>(compare1));
		assertEquals(correct.size(), compare1.size());

		value1.set(10, null);
		correct.clear();
		for(int i = 0; i < 30; i++) {
			int value = i * value1.get();
			if(value % 3 == 0)
				correct.add(value);
		}
		assertEquals(correct, new TreeSet<>(compare1));
		assertEquals(correct.size(), compare1.size());

		for(int i = 0; i < 30; i++) {
			set.remove(i);
			int value = i * value1.get();
			if(value % 3 == 0)
				correct.remove(value);
			assertEquals(correct, new TreeSet<>(compare1));
			assertEquals(correct.size(), compare1.size());
		}
	}

	/** Tests {@link ObservableSet#unique(ObservableCollection)} */
	@Test
	public void observableSetUnique() {
		ObservableArrayList<Integer> list = new ObservableArrayList<>(TypeToken.of(Integer.TYPE));
		ObservableSet<Integer> unique = ObservableSet.unique(list);
		List<Integer> compare1 = new ArrayList<>();
		Set<Integer> correct = new TreeSet<>();

		unique.onElement(element -> {
			element.subscribe(new Observer<ObservableValueEvent<Integer>>() {
				@Override
				public <V extends ObservableValueEvent<Integer>> void onNext(V event) {
					if(!event.isInitial())
						compare1.remove(event.getOldValue());
					compare1.add(event.getValue());
				}

				@Override
				public <V extends ObservableValueEvent<Integer>> void onCompleted(V event) {
					compare1.remove(event.getValue());
				}
			});
		});

		for(int i = 0; i < 30; i++) {
			list.add(i);
			correct.add(i);
		}
		assertEquals(correct, new TreeSet<>(unique));
		assertEquals(correct, new TreeSet<>(compare1));
		assertEquals(correct.size(), compare1.size());

		for(int i = 0; i < 30; i++) {
			list.add(i);
		}
		assertEquals(correct, new TreeSet<>(unique));
		assertEquals(correct, new TreeSet<>(compare1));
		assertEquals(correct.size(), compare1.size());

		for(int i = 29; i >= 0; i--) {
			list.remove(30 + i);
		}
		assertEquals(correct, new TreeSet<>(unique));
		assertEquals(correct, new TreeSet<>(compare1));
		assertEquals(correct.size(), compare1.size());

		for(int i = 0; i < 30; i++) {
			list.add(i);
		}
		assertEquals(correct, new TreeSet<>(unique));
		assertEquals(correct, new TreeSet<>(compare1));
		assertEquals(correct.size(), compare1.size());

		for(int i = 29; i >= 0; i--) {
			list.remove(i);
		}
		assertEquals(correct, new TreeSet<>(unique));
		assertEquals(correct, new TreeSet<>(compare1));
		assertEquals(correct.size(), compare1.size());

		for(int i = 29; i >= 0; i--) {
			list.remove(i);
			correct.remove(i);
		}
		assertEquals(correct, new TreeSet<>(unique));
		assertEquals(correct, new TreeSet<>(compare1));
		assertEquals(correct.size(), compare1.size());

		for(int i = 0; i < 30; i++) {
			list.add(i);
			list.add(i);
			correct.add(i);
		}
		assertEquals(correct, new TreeSet<>(unique));
		assertEquals(correct, new TreeSet<>(compare1));
		assertEquals(correct.size(), compare1.size());
		list.clear();
		correct.clear();
		assertEquals(correct, new TreeSet<>(unique));
		assertEquals(correct, new TreeSet<>(compare1));
		assertEquals(correct.size(), compare1.size());
	}

	/** Tests {@link ObservableCollection#flatten(ObservableCollection)} */
	@Test
	public void observableCollectionFlatten() {
		ObservableHashSet<Integer> set1 = new ObservableHashSet<>(TypeToken.of(Integer.TYPE));
		ObservableHashSet<Integer> set2 = new ObservableHashSet<>(TypeToken.of(Integer.TYPE));
		ObservableHashSet<Integer> set3 = new ObservableHashSet<>(TypeToken.of(Integer.TYPE));
		ObservableArrayList<ObservableSet<Integer>> outer = new ObservableArrayList<>(new TypeToken<ObservableSet<Integer>>() {});
		outer.add(set1);
		outer.add(set2);
		ObservableCollection<Integer> flat = ObservableCollection.flatten(outer);
		List<Integer> compare1 = new ArrayList<>();
		List<Integer> filtered = new ArrayList<>();
		flat.onElement(element -> {
			element.subscribe(new Observer<ObservableValueEvent<Integer>>() {
				@Override
				public <V extends ObservableValueEvent<Integer>> void onNext(V event) {
					if(!event.isInitial())
						compare1.remove(event.getOldValue());
					compare1.add(event.getValue());
				}

				@Override
				public <V extends ObservableValueEvent<Integer>> void onCompleted(V event) {
					compare1.remove(event.getValue());
				}
			});
		});
		flat.filter(value -> value != null && value % 3 == 0).onElement(element -> {
			element.subscribe(new Observer<ObservableValueEvent<Integer>>() {
				@Override
				public <V extends ObservableValueEvent<Integer>> void onNext(V event) {
					if(!event.isInitial())
						filtered.remove(event.getOldValue());
					filtered.add(event.getValue());
				}

				@Override
				public <V extends ObservableValueEvent<Integer>> void onCompleted(V event) {
					filtered.remove(event.getValue());
				}
			});
		});

		List<Integer> correct1 = new ArrayList<>();
		List<Integer> correct2 = new ArrayList<>();
		List<Integer> correct3 = new ArrayList<>();
		List<Integer> correct = new ArrayList<>();
		List<Integer> filteredCorrect = new ArrayList<>();

		for(int i = 0; i < 30; i++) {
			set1.add(i);
			set2.add(i * 10);
			set3.add(i * 100);
			correct1.add(i);
			correct2.add(i * 10);
			correct3.add(i * 100);
			correct.clear();
			correct.addAll(correct1);
			correct.addAll(correct2);
			filteredCorrect.clear();
			for(int j : correct1)
				if(j % 3 == 0)
					filteredCorrect.add(j);
			for(int j : correct2)
				if(j % 3 == 0)
					filteredCorrect.add(j);
			assertEquals(new TreeSet<>(flat), new TreeSet<>(compare1));
			assertEquals(flat.size(), compare1.size());
			assertEquals(new TreeSet<>(correct), new TreeSet<>(compare1));
			assertEquals(correct.size(), compare1.size());
			assertEquals(new TreeSet<>(filteredCorrect), new TreeSet<>(filtered));
			assertEquals(filteredCorrect.size(), filtered.size());
		}

		outer.add(set3);
		correct.clear();
		correct.addAll(correct1);
		correct.addAll(correct2);
		correct.addAll(correct3);
		filteredCorrect.clear();
		for(int j : correct1)
			if(j % 3 == 0)
				filteredCorrect.add(j);
		for(int j : correct2)
			if(j % 3 == 0)
				filteredCorrect.add(j);
		for(int j : correct3)
			if(j % 3 == 0)
				filteredCorrect.add(j);

		assertEquals(new TreeSet<>(flat), new TreeSet<>(compare1));
		assertEquals(flat.size(), compare1.size());
		assertEquals(new TreeSet<>(correct), new TreeSet<>(compare1));
		assertEquals(correct.size(), compare1.size());
		assertEquals(new TreeSet<>(filteredCorrect), new TreeSet<>(filtered));
		assertEquals(filteredCorrect.size(), filtered.size());

		outer.remove(set2);
		correct.clear();
		correct.addAll(correct1);
		correct.addAll(correct3);
		filteredCorrect.clear();
		for(int j : correct1)
			if(j % 3 == 0)
				filteredCorrect.add(j);
		for(int j : correct3)
			if(j % 3 == 0)
				filteredCorrect.add(j);

		assertEquals(new TreeSet<>(flat), new TreeSet<>(compare1));
		assertEquals(flat.size(), compare1.size());
		assertEquals(new TreeSet<>(correct), new TreeSet<>(compare1));
		assertEquals(correct.size(), compare1.size());
		assertEquals(new TreeSet<>(filteredCorrect), new TreeSet<>(filtered));
		assertEquals(filteredCorrect.size(), filtered.size());

		for(int i = 0; i < 30; i++) {
			set1.remove(i);
			set2.remove(i * 10);
			set3.remove(i * 100);
			correct1.remove((Integer) i);
			correct2.remove((Integer) (i * 10));
			correct3.remove((Integer) (i * 100));
			correct.clear();
			correct.addAll(correct1);
			correct.addAll(correct3);
			filteredCorrect.clear();
			for(int j : correct1)
				if(j % 3 == 0)
					filteredCorrect.add(j);
			for(int j : correct3)
				if(j % 3 == 0)
					filteredCorrect.add(j);

			assertEquals(new TreeSet<>(flat), new TreeSet<>(compare1));
			assertEquals(flat.size(), compare1.size());
			assertEquals(new TreeSet<>(correct), new TreeSet<>(compare1));
			assertEquals(correct.size(), compare1.size());
			assertEquals(new TreeSet<>(filteredCorrect), new TreeSet<>(filtered));
			assertEquals(filteredCorrect.size(), filtered.size());
		}
	}

	/** Tests {@link ObservableCollection#fold(ObservableCollection)} */
	@Test
	public void observableCollectionFold() {
		SimpleSettableValue<Integer> obs1 = new SimpleSettableValue<>(Integer.class, true);
		SimpleSettableValue<Integer> obs2 = new SimpleSettableValue<>(Integer.class, true);
		SimpleSettableValue<Integer> obs3 = new SimpleSettableValue<>(Integer.class, true);
		ObservableHashSet<ObservableValue<Integer>> set = new ObservableHashSet<>(new TypeToken<ObservableValue<Integer>>() {});
		set.add(obs1);
		set.add(obs2);
		Observable<Integer> folded = ObservableCollection.fold(set.map(value -> value.value()));
		int [] received = new int[1];
		folded.noInit().act(value -> received[0] = value);

		obs1.set(1, null);
		assertEquals(1, received[0]);
		obs2.set(2, null);
		assertEquals(2, received[0]);
		obs3.set(3, null);
		assertEquals(2, received[0]);
		set.add(obs3);
		assertEquals(3, received[0]); // Initial value fired
		obs3.set(4, null);
		assertEquals(4, received[0]);
		set.remove(obs2);
		obs2.set(5, null);
		assertEquals(4, received[0]);
	}

	/** Tests {@link ObservableList#map(java.util.function.Function)} */
	@Test
	public void observableListMap() {
		ObservableArrayList<Integer> list = new ObservableArrayList<>(TypeToken.of(Integer.TYPE));
		List<Integer> compare1 = new ArrayList<>();
		List<Integer> correct = new ArrayList<>();
		list.map(value -> value * 10).onElement(element -> {
			ObservableOrderedElement<Integer> listEl = (ObservableOrderedElement<Integer>) element;
			element.subscribe(new Observer<ObservableValueEvent<Integer>>() {
				@Override
				public <V extends ObservableValueEvent<Integer>> void onNext(V value) {
					if(!value.isInitial())
						compare1.set(listEl.getIndex(), value.getValue());
					else
						compare1.add(listEl.getIndex(), value.getValue());
				}

				@Override
				public <V extends ObservableValueEvent<Integer>> void onCompleted(V value) {
					compare1.remove(listEl.getIndex());
				}
			});
		});

		for(int i = 0; i < 30; i++) {
			list.add(i);
			correct.add(i * 10);
			assertEquals(correct, compare1);
		}
		for(int i = 0; i < 30; i++) {
			list.add(i);
			correct.add(i * 10);
			assertEquals(correct, compare1);
		}
		for(int i = 0; i < 30; i++) {
			list.remove(Integer.valueOf(i));
			correct.remove(Integer.valueOf(i * 10));
			assertEquals(correct, compare1);
		}
	}

	/** Tests {@link ObservableList#filter(java.util.function.Predicate)} */
	@Test
	public void observableListFilter() {
		ObservableArrayList<Integer> list = new ObservableArrayList<>(TypeToken.of(Integer.TYPE));
		List<Integer> compare1 = new ArrayList<>();
		List<Integer> correct = new ArrayList<>();
		list.filter(value -> value != null && value % 2 == 0).onElement(element -> {
			ObservableOrderedElement<Integer> listEl = (ObservableOrderedElement<Integer>) element;
			element.subscribe(new Observer<ObservableValueEvent<Integer>>() {
				@Override
				public <V extends ObservableValueEvent<Integer>> void onNext(V value) {
					if(!value.isInitial())
						compare1.set(listEl.getIndex(), value.getValue());
					else
						compare1.add(listEl.getIndex(), value.getValue());
				}

				@Override
				public <V extends ObservableValueEvent<Integer>> void onCompleted(V value) {
					compare1.remove(listEl.getIndex());
				}
			});
		});

		for(int i = 0; i < 30; i++) {
			list.add(i);
			if(i % 2 == 0)
				correct.add(i);
			assertEquals(correct, compare1);
		}
		for(int i = 0; i < 30; i++) {
			list.add(i);
			if(i % 2 == 0)
				correct.add(i);
			assertEquals(correct, compare1);
		}
		for(int i = 0; i < 30; i++) {
			list.remove(Integer.valueOf(i));
			if(i % 2 == 0)
				correct.remove(Integer.valueOf(i));
			assertEquals(correct, compare1);
		}
	}

	/** Slight variation on {@link #observableListFilter()} */
	@Test
	public void observableListFilter2() {
		ObservableArrayList<Integer> list = new ObservableArrayList<>(TypeToken.of(Integer.TYPE));
		List<Integer> compare0 = new ArrayList<>();
		List<Integer> correct0 = new ArrayList<>();
		List<Integer> compare1 = new ArrayList<>();
		List<Integer> correct1 = new ArrayList<>();
		List<Integer> compare2 = new ArrayList<>();
		List<Integer> correct2 = new ArrayList<>();

		list.filter(value -> value % 3 == 0).onElement(element -> {
			ObservableOrderedElement<Integer> oel = (ObservableOrderedElement<Integer>) element;
			element.subscribe(new Observer<ObservableValueEvent<Integer>>() {
				@Override
				public <V extends ObservableValueEvent<Integer>> void onNext(V value) {
					if(!value.isInitial())
						compare0.set(oel.getIndex(), value.getValue());
					else
						compare0.add(oel.getIndex(), value.getValue());
				}

				@Override
				public <V extends ObservableValueEvent<Integer>> void onCompleted(V value) {
					compare0.remove(oel.getIndex());
				}
			});
		});
		list.filter(value -> value % 3 == 1).onElement(element -> {
			ObservableOrderedElement<Integer> oel = (ObservableOrderedElement<Integer>) element;
			element.subscribe(new Observer<ObservableValueEvent<Integer>>() {
				@Override
				public <V extends ObservableValueEvent<Integer>> void onNext(V value) {
					if(!value.isInitial())
						compare1.set(oel.getIndex(), value.getValue());
					else
						compare1.add(oel.getIndex(), value.getValue());
				}

				@Override
				public <V extends ObservableValueEvent<Integer>> void onCompleted(V value) {
					compare1.remove(oel.getIndex());
				}
			});
		});
		list.filter(value -> value % 3 == 2).onElement(element -> {
			ObservableOrderedElement<Integer> oel = (ObservableOrderedElement<Integer>) element;
			element.subscribe(new Observer<ObservableValueEvent<Integer>>() {
				@Override
				public <V extends ObservableValueEvent<Integer>> void onNext(V value) {
					if(!value.isInitial())
						compare2.set(oel.getIndex(), value.getValue());
					else
						compare2.add(oel.getIndex(), value.getValue());
				}

				@Override
				public <V extends ObservableValueEvent<Integer>> void onCompleted(V value) {
					compare2.remove(oel.getIndex());
				}
			});
		});

		int count = 30;
		for(int i = 0; i < count; i++) {
			list.add(i);
			switch (i % 3) {
			case 0:
				correct0.add(i);
				break;
			case 1:
				correct1.add(i);
				break;
			case 2:
				correct2.add(i);
				break;
			}
			assertEquals(correct0, compare0);
			assertEquals(correct1, compare1);
			assertEquals(correct2, compare2);
		}
		for(int i = 0; i < count; i++) {
			int value = i + 1;
			list.set(i, value);
			switch (i % 3) {
			case 0:
				correct0.remove(Integer.valueOf(i));
				break;
			case 1:
				correct1.remove(Integer.valueOf(i));
				break;
			case 2:
				correct2.remove(Integer.valueOf(i));
				break;
			}
			switch (value % 3) {
			case 0:
				correct0.add(i / 3, value);
				break;
			case 1:
				correct1.add(i / 3, value);
				break;
			case 2:
				correct2.add(i / 3, value);
				break;
			}
			assertEquals(correct0, compare0);
			assertEquals(correct1, compare1);
			assertEquals(correct2, compare2);
		}
		for(int i = count - 1; i >= 0; i--) {
			int value = list.get(i);
			list.remove(Integer.valueOf(value));
			switch (value % 3) {
			case 0:
				correct0.remove(Integer.valueOf(value));
				break;
			case 1:
				correct1.remove(Integer.valueOf(value));
				break;
			case 2:
				correct2.remove(Integer.valueOf(value));
				break;
			}
			assertEquals(correct0, compare0);
			assertEquals(correct1, compare1);
			assertEquals(correct2, compare2);
		}
	}

	/** Tests {@link ObservableList#filterMap(java.util.function.Function)} */
	@Test
	public void observableListFilterMap() {
		ObservableArrayList<Integer> list = new ObservableArrayList<>(TypeToken.of(Integer.TYPE));
		List<Integer> compare1 = new ArrayList<>();
		List<Integer> correct = new ArrayList<>();
		list.filterMap(value -> {
			if(value == null || value % 2 != 0)
				return null;
			return value / 2;
		}).onElement(element -> {
			ObservableOrderedElement<Integer> listEl = (ObservableOrderedElement<Integer>) element;
			element.subscribe(new Observer<ObservableValueEvent<Integer>>() {
				@Override
				public <V extends ObservableValueEvent<Integer>> void onNext(V value) {
					if(!value.isInitial())
						compare1.set(listEl.getIndex(), value.getValue());
					else
						compare1.add(listEl.getIndex(), value.getValue());
				}

				@Override
				public <V extends ObservableValueEvent<Integer>> void onCompleted(V value) {
					compare1.remove(listEl.getIndex());
				}
			});
		});

		for(int i = 0; i < 30; i++) {
			list.add(i);
			if(i % 2 == 0)
				correct.add(i / 2);
			assertEquals(correct, compare1);
		}
		for(int i = 0; i < 30; i++) {
			list.add(i);
			if(i % 2 == 0)
				correct.add(i / 2);
			assertEquals(correct, compare1);
		}
		for(int i = 0; i < 30; i++) {
			list.remove(Integer.valueOf(i));
			if(i % 2 == 0)
				correct.remove(Integer.valueOf(i / 2));
			assertEquals(correct, compare1);
		}
	}

	/** Tests {@link ObservableList#combine(ObservableValue, java.util.function.BiFunction)} */
	@Test
	public void observableListCombine() {
		ObservableArrayList<Integer> list = new ObservableArrayList<>(TypeToken.of(Integer.TYPE));
		SimpleSettableValue<Integer> value1 = new SimpleSettableValue<>(Integer.TYPE, false);
		value1.set(1, null);
		List<Integer> compare1 = new ArrayList<>();
		List<Integer> correct = new ArrayList<>();
		list.combine(value1, (v1, v2) -> v1 * v2).filter(value -> value != null && value % 3 == 0).onElement(element -> {
			ObservableOrderedElement<Integer> listEl = (ObservableOrderedElement<Integer>) element;
			element.subscribe(new Observer<ObservableValueEvent<Integer>>() {
				@Override
				public <V extends ObservableValueEvent<Integer>> void onNext(V event) {
					if(!event.isInitial())
						compare1.set(listEl.getIndex(), event.getValue());
					else
						compare1.add(listEl.getIndex(), event.getValue());
				}

				@Override
				public <V extends ObservableValueEvent<Integer>> void onCompleted(V event) {
					compare1.remove(listEl.getIndex());
				}
			});
		});

		for(int i = 0; i < 30; i++) {
			list.add(i);
			int value = i * value1.get();
			if(value % 3 == 0)
				correct.add(value);
			assertEquals(correct, compare1);
			assertEquals(correct.size(), compare1.size());
		}

		value1.set(3, null);
		correct.clear();
		for(int i = 0; i < 30; i++) {
			int value = i * value1.get();
			if(value % 3 == 0)
				correct.add(value);
		}
		assertEquals(correct, compare1);
		assertEquals(correct.size(), compare1.size());

		value1.set(10, null);
		correct.clear();
		for(int i = 0; i < 30; i++) {
			int value = i * value1.get();
			if(value % 3 == 0)
				correct.add(value);
		}
		assertEquals(correct, compare1);
		assertEquals(correct.size(), compare1.size());

		for(int i = 0; i < 30; i++) {
			list.remove(Integer.valueOf(i));
			int value = i * value1.get();
			if(value % 3 == 0)
				correct.remove(Integer.valueOf(value));
			assertEquals(correct, compare1);
			assertEquals(correct.size(), compare1.size());
		}
	}

	/** Tests {@link ObservableList#flatten(ObservableList)} */
	@Test
	public void observableListFlatten() {
		ObservableArrayList<Integer> set1 = new ObservableArrayList<>(TypeToken.of(Integer.TYPE));
		ObservableArrayList<Integer> set2 = new ObservableArrayList<>(TypeToken.of(Integer.TYPE));
		ObservableArrayList<Integer> set3 = new ObservableArrayList<>(TypeToken.of(Integer.TYPE));
		ObservableArrayList<ObservableList<Integer>> outer = new ObservableArrayList<>(new TypeToken<ObservableList<Integer>>() {});
		outer.add(set1);
		outer.add(set2);
		ObservableList<Integer> flat = ObservableList.flatten(outer);
		List<Integer> compare1 = new ArrayList<>();
		List<Integer> filtered = new ArrayList<>();
		flat.onElement(element -> {
			ObservableOrderedElement<Integer> listEl = (ObservableOrderedElement<Integer>) element;
			element.subscribe(new Observer<ObservableValueEvent<Integer>>() {
				@Override
				public <V extends ObservableValueEvent<Integer>> void onNext(V event) {
					if(!event.isInitial())
						compare1.set(listEl.getIndex(), event.getOldValue());
					else
						compare1.add(listEl.getIndex(), event.getValue());
				}

				@Override
				public <V extends ObservableValueEvent<Integer>> void onCompleted(V event) {
					compare1.remove(listEl.getIndex());
				}
			});
		});
		flat.filter(value -> value != null && value % 3 == 0).onElement(element -> {
			ObservableOrderedElement<Integer> listEl = (ObservableOrderedElement<Integer>) element;
			element.subscribe(new Observer<ObservableValueEvent<Integer>>() {
				@Override
				public <V extends ObservableValueEvent<Integer>> void onNext(V event) {
					if(!event.isInitial()) {
						filtered.set(listEl.getIndex(), event.getValue());
					} else {
						filtered.add(listEl.getIndex(), event.getValue());
					}
				}

				@Override
				public <V extends ObservableValueEvent<Integer>> void onCompleted(V event) {
					filtered.remove(listEl.getIndex());
				}
			});
		});

		List<Integer> correct1 = new ArrayList<>();
		List<Integer> correct2 = new ArrayList<>();
		List<Integer> correct3 = new ArrayList<>();
		List<Integer> correct = new ArrayList<>();
		List<Integer> filteredCorrect = new ArrayList<>();

		for(int i = 0; i < 30; i++) {
			set1.add(i);
			set2.add(i * 10);
			set3.add(i * 100);
			correct1.add(i);
			correct2.add(i * 10);
			correct3.add(i * 100);
			correct.clear();
			correct.addAll(correct1);
			correct.addAll(correct2);
			filteredCorrect.clear();
			for(int j : correct1)
				if(j % 3 == 0)
					filteredCorrect.add(j);
			for(int j : correct2)
				if(j % 3 == 0)
					filteredCorrect.add(j);
			assertEquals(new ArrayList<>(flat), compare1);
			assertEquals(flat.size(), compare1.size());
			assertEquals(correct, compare1);
			assertEquals(correct.size(), compare1.size());
			assertEquals(filteredCorrect, filtered);
			assertEquals(filteredCorrect.size(), filtered.size());
		}

		outer.add(set3);
		correct.clear();
		correct.addAll(correct1);
		correct.addAll(correct2);
		correct.addAll(correct3);
		filteredCorrect.clear();
		for(int j : correct1)
			if(j % 3 == 0)
				filteredCorrect.add(j);
		for(int j : correct2)
			if(j % 3 == 0)
				filteredCorrect.add(j);
		for(int j : correct3)
			if(j % 3 == 0)
				filteredCorrect.add(j);

		assertEquals(new ArrayList<>(flat), compare1);
		assertEquals(flat.size(), compare1.size());
		assertEquals(correct, compare1);
		assertEquals(correct.size(), compare1.size());
		assertEquals(filteredCorrect, filtered);
		assertEquals(filteredCorrect.size(), filtered.size());

		outer.remove(set2);
		correct.clear();
		correct.addAll(correct1);
		correct.addAll(correct3);
		filteredCorrect.clear();
		for(int j : correct1)
			if(j % 3 == 0)
				filteredCorrect.add(j);
		for(int j : correct3)
			if(j % 3 == 0)
				filteredCorrect.add(j);

		assertEquals(new ArrayList<>(flat), compare1);
		assertEquals(flat.size(), compare1.size());
		assertEquals(correct, compare1);
		assertEquals(correct.size(), compare1.size());
		assertEquals(filteredCorrect, filtered);
		assertEquals(filteredCorrect.size(), filtered.size());

		for(int i = 0; i < 30; i++) {
			set1.remove((Integer) i);
			set2.remove((Integer) (i * 10));
			set3.remove((Integer) (i * 100));
			correct1.remove((Integer) i);
			correct2.remove((Integer) (i * 10));
			correct3.remove((Integer) (i * 100));
			correct.clear();
			correct.addAll(correct1);
			correct.addAll(correct3);
			filteredCorrect.clear();
			for(int j : correct1)
				if(j % 3 == 0)
					filteredCorrect.add(j);
			for(int j : correct3)
				if(j % 3 == 0)
					filteredCorrect.add(j);

			assertEquals(new ArrayList<>(flat), compare1);
			assertEquals(flat.size(), compare1.size());
			assertEquals(correct, compare1);
			assertEquals(correct.size(), compare1.size());
			assertEquals(filteredCorrect, filtered);
			assertEquals(filteredCorrect.size(), filtered.size());
		}
	}

	/** Tests {@link ObservableList#find(java.util.function.Predicate)} */
	@Test
	public void observableListFind() {
		ObservableArrayList<Integer> list = new ObservableArrayList<>(TypeToken.of(Integer.TYPE));
		ObservableValue<Integer> found = list.find(value -> value % 3 == 0);
		Integer [] received = new Integer[] {0};
		found.act(value -> received[0] = value.getValue());
		Integer [] correct = new Integer[] {null};

		assertEquals(correct[0], received[0]);
		assertEquals(correct[0], found.get());
		for(int i = 1; i < 30; i++) {
			list.add(i);
			if(i % 3 == 0 && correct[0] == null)
				correct[0] = i;
			assertEquals(correct[0], received[0]);
			assertEquals(correct[0], found.get());
		}
		for(int i = 1; i < 30; i++) {
			list.remove(Integer.valueOf(i));
			if(i % 3 == 0) {
				correct[0] += 3;
				if(correct[0] >= 30)
					correct[0] = null;
			}
			assertEquals(correct[0], received[0]);
			assertEquals(correct[0], found.get());
		}
	}

	/** Tests {@link ObservableUtils#flattenListValues(Type, ObservableList)} */
	@Test
	public void flattenListValues() {
		ObservableArrayList<ObservableValue<Integer>> list = new ObservableArrayList<>(new TypeToken<ObservableValue<Integer>>() {});
		SimpleSettableValue<Integer> value1 = new SimpleSettableValue<>(Integer.TYPE, false);
		value1.set(1, null);
		SimpleSettableValue<Integer> value2 = new SimpleSettableValue<>(Integer.TYPE, false);
		value2.set(2, null);
		SimpleSettableValue<Integer> value3 = new SimpleSettableValue<>(Integer.TYPE, false);
		value3.set(3, null);
		SimpleSettableValue<Integer> value4 = new SimpleSettableValue<>(Integer.TYPE, false);
		value4.set(4, null);
		SimpleSettableValue<Integer> value5 = new SimpleSettableValue<>(Integer.TYPE, false);
		value5.set(9, null);
		list.addAll(java.util.Arrays.asList(value1, value2, value3, value4));

		Integer [] received = new Integer[1];
		ObservableUtils.flattenListValues(TypeToken.of(Integer.TYPE), list).find(value -> value % 3 == 0).value()
		.act(value -> received[0] = value);
		assertEquals(Integer.valueOf(3), received[0]);
		value3.set(4, null);
		assertEquals(null, received[0]);
		value4.set(6, null);
		assertEquals(Integer.valueOf(6), received[0]);
		list.remove(value4);
		assertEquals(null, received[0]);
		list.add(value5);
		assertEquals(Integer.valueOf(9), received[0]);
	}

	/** Tests {@link ObservableOrderedCollection#sort(ObservableCollection, java.util.Comparator)} */
	@Test
	public void sortedObservableList() {
		ObservableArrayList<Integer> list = new ObservableArrayList<>(TypeToken.of(Integer.TYPE));

		List<Integer> compare = new ArrayList<>();
		ObservableOrderedCollection.sort(list, null).onElement(element -> {
			ObservableOrderedElement<Integer> orderedEl = (ObservableOrderedElement<Integer>) element;
			element.subscribe(new Observer<ObservableValueEvent<Integer>>() {
				@Override
				public <V extends ObservableValueEvent<Integer>> void onNext(V event) {
					if(event.isInitial())
						compare.add(orderedEl.getIndex(), event.getValue());
					else
						compare.set(orderedEl.getIndex(), event.getValue());
				}

				@Override
				public <V extends ObservableValueEvent<Integer>> void onCompleted(V event) {
					compare.remove(orderedEl.getIndex());
				}
			});
		});

		List<Integer> correct = new ArrayList<>();

		for(int i = 30; i >= 0; i--) {
			list.add(i);
			correct.add(i);
		}

		java.util.Collections.sort(correct);
		assertEquals(correct, compare);

		for(int i = 30; i >= 0; i--) {
			list.remove((Integer) i);
			correct.remove((Integer) i);

			assertEquals(correct, compare);
		}
	}

	/** Tests {@link ObservableOrderedCollection#flatten(ObservableOrderedCollection, java.util.Comparator)} */
	@Test
	public void observableOrderedFlatten() {
		observableOrderedFlatten(Comparable::compareTo);
		observableOrderedFlatten(null);
	}

	private void observableOrderedFlatten(java.util.Comparator<Integer> comparator) {
		ObservableArrayList<ObservableList<Integer>> outer = new ObservableArrayList<>(new TypeToken<ObservableList<Integer>>() {});
		ObservableArrayList<Integer> list1 = new ObservableArrayList<>(TypeToken.of(Integer.TYPE));
		ObservableArrayList<Integer> list2 = new ObservableArrayList<>(TypeToken.of(Integer.TYPE));
		ObservableArrayList<Integer> list3 = new ObservableArrayList<>(TypeToken.of(Integer.TYPE));

		outer.add(list1);
		outer.add(list2);

		ArrayList<Integer> compare = new ArrayList<>();
		ArrayList<Integer> correct1 = new ArrayList<>();
		ArrayList<Integer> correct2 = new ArrayList<>();
		ArrayList<Integer> correct3 = new ArrayList<>();

		// Add data before the subscription because subscribing to non-empty, indexed, flattened collections is complicated
		for(int i = 0; i <= 30; i++) {
			if(i % 3 == 1) {
				list1.add(i);
				correct1.add(i);
			} else if(i % 3 == 0) {
				list2.add(i);
				correct2.add(i);
			} else {
				list3.add(i);
				correct3.add(i);
			}
		}

		Subscription sub = ObservableOrderedCollection.flatten(outer, comparator).onElement(element -> {
			ObservableOrderedElement<Integer> orderedEl = (ObservableOrderedElement<Integer>) element;
			element.subscribe(new Observer<ObservableValueEvent<Integer>>() {
				@Override
				public <V extends ObservableValueEvent<Integer>> void onNext(V event) {
					if(event.isInitial())
						compare.add(orderedEl.getIndex(), event.getValue());
					else
						compare.set(orderedEl.getIndex(), event.getValue());
				}

				@Override
				public <V extends ObservableValueEvent<Integer>> void onCompleted(V event) {
					compare.remove(orderedEl.getIndex());
				}
			});
		});
		assertEquals(join(comparator, correct1, correct2), compare);

		outer.add(list3);
		assertEquals(join(comparator, correct1, correct2, correct3), compare);

		outer.remove(list2);
		assertEquals(join(comparator, correct1, correct3), compare);

		list1.remove((Integer) 16);
		correct1.remove((Integer) 16);
		assertEquals(join(comparator, correct1, correct3), compare);
		list1.add(list1.indexOf(19), 16);
		correct1.add(correct1.indexOf(19), 16);
		assertEquals(join(comparator, correct1, correct3), compare);

		sub.unsubscribe();
	}

	private static <T> List<T> join(Comparator<? super T> comparator, List<T>... correct) {
		ArrayList<T> ret = new ArrayList<>();
		for(List<T> c : correct)
			ret.addAll(c);
		if(comparator != null)
			java.util.Collections.sort(ret, comparator);
		return ret;
	}

	/** Tests {@link ObservableList#asList(ObservableCollection)} */
	@Test
	public void obervableListFromCollection() {
		ObservableHashSet<Integer> set = new ObservableHashSet<>(TypeToken.of(Integer.TYPE));
		ObservableList<Integer> list = ObservableList.asList(set);
		ArrayList<Integer> compare1 = new ArrayList<>();
		ArrayList<Integer> correct = new ArrayList<>();
		sync(list, compare1);

		int count = 30;
		for(int i = 0; i < count; i++) {
			set.add(i);
			correct.add(i);

			assertEquals(correct, compare1);
		}
		ArrayList<Integer> compare2 = new ArrayList<>();
		sync(list, compare2);
		assertEquals(correct, compare2);
		for(int i = count - 1; i >= 0; i--) {
			if(i % 2 == 0) {
				set.remove(i);
				correct.remove(i); // By index
			}

			assertEquals(correct, compare1);
			assertEquals(correct, compare2);
		}
	}

	/**
	 * Tests {@link ObservableSet#unique(ObservableCollection)} wrapped with {@link ObservableList#asList(ObservableCollection)}. I wrote
	 * this test to capture a specific test case, but I couldn't reproduce the error here. Not sure this test is super valuable.
	 */
	@Test
	public void observableListFromUnique() {
		ObservableArrayList<Integer> list = new ObservableArrayList<>(TypeToken.of(Integer.TYPE));
		ObservableList<Integer> uniqued = ObservableList.asList(ObservableSet.unique(list));
		ArrayList<Integer> compare = new ArrayList<>();
		ArrayList<Integer> correct = new ArrayList<>();
		sync(uniqued, compare);

		int count = 30;
		for(int i = 0; i < count; i++) {
			list.add(i);
			list.add(i);
			correct.add(i);

			assertEquals(correct, compare);
		}
		list.clear();
		correct.clear();
		assertEquals(correct, compare);
	}

	/** Reproduces a bug I found with list changes */
	@Test
	public void observableListChanges() {
		ObservableArrayList<Integer> list = new ObservableArrayList<>(TypeToken.of(Integer.TYPE));
		ArrayList<Integer> synced = new ArrayList<>();
		// No idea why I have to cast this
		((ObservableOrderedCollection<Integer>) list).changes().act(change -> {
			switch (change.type) {
			case add:
				for(int i = 0; i < change.indexes.size(); i++)
					synced.add(change.indexes.get(i), change.values.get(i));
				break;
			case remove:
				for(int i = 0; i < change.indexes.size(); i++)
					synced.remove(change.indexes.get(i));
				break;
			case set:
				for(int i = 0; i < change.indexes.size(); i++)
					synced.set(change.indexes.get(i), change.values.get(i));
				break;
			}
		});

		for(int i = 0; i < 15; i++)
			list.add(i);
		try (Transaction t = list.lock(true, null)) {

		}
	}

	/** Tests basic transaction functionality on observable collections */
	@Test
	public void testTransactionsBasic() {
		// Use find() and changes() to test
		ObservableArrayList<Integer> list = new ObservableArrayList<>(TypeToken.of(Integer.TYPE));
		testTransactionsByFind(list, list);
		testTransactionsByChanges(list, list);
	}

	/** Tests transactions in {@link ObservableList#flatten(ObservableList) flattened} collections */
	@Test
	public void testTransactionsFlattened() {
		ObservableArrayList<Integer> list1 = new ObservableArrayList<>(TypeToken.of(Integer.TYPE));
		ObservableArrayList<Integer> list2 = new ObservableArrayList<>(TypeToken.of(Integer.TYPE));
		ObservableList<Integer> flat = ObservableList.flattenLists(TypeToken.of(Integer.TYPE), list1, list2);
		list1.add(50);

		testTransactionsByFind(flat, list2);
		testTransactionsByChanges(flat, list2);
	}

	/**
	 * Tests transactions caused by {@link ObservableCollection#combine(ObservableValue, java.util.function.BiFunction) combining} a list
	 * with an observable value
	 */
	@Test
	public void testTransactionsCombined() {
		// TODO
		throw new sun.reflect.generics.reflectiveObjects.NotImplementedException();
	}

	/** Tests transactions caused by {@link ObservableCollection#refresh(Observable) refreshing} on an observable */
	@Test
	public void testTransactionsRefresh() {
		// TODO
		throw new sun.reflect.generics.reflectiveObjects.NotImplementedException();
	}

	/** Tests {@link ObservableCollection#refreshEach(Function)} */
	@Test
	public void testRefreshEach() {
		// TODO
		throw new sun.reflect.generics.reflectiveObjects.NotImplementedException();
	}

	private void testTransactionsByFind(ObservableList<Integer> observable, TransactableList<Integer> controller) {
		Integer [] found = new Integer[1];
		int [] findCount = new int[1];
		Subscription sub = observable.find(value -> value % 5 == 4).act(event -> {
			findCount[0]++;
			found[0] = event.getValue();
		});

		assertEquals(1, findCount[0]);
		controller.add(0);
		assertEquals(1, findCount[0]);
		controller.add(3);
		assertEquals(1, findCount[0]);
		controller.add(9);
		assertEquals(2, findCount[0]);
		assertEquals(9, (int) found[0]);
		Transaction trans = controller.lock(true, null);
		assertEquals(2, findCount[0]);
		controller.add(0, 4);
		assertEquals(2, findCount[0]);
		trans.close();
		assertEquals(3, findCount[0]);
		assertEquals(4, (int) found[0]);

		sub.unsubscribe();
		controller.clear();
	}

	private void testTransactionsByChanges(ObservableList<Integer> observable, TransactableList<Integer> controller) {
		ArrayList<Integer> compare = new ArrayList<>(observable);
		ArrayList<Integer> correct = new ArrayList<>(observable);
		int [] changeCount = new int[1];
		Subscription sub = observable.changes().act(event -> {
			changeCount[0]++;
			for(int i = 0; i < event.indexes.size(); i++) {
				switch (event.type) {
				case add:
					compare.add(event.indexes.get(i), event.values.get(i));
					break;
				case remove:
					assertEquals(compare.remove(event.indexes.get(i)), event.values.get(i));
					break;
				case set:
					assertEquals(compare.set(event.indexes.get(i), event.values.get(i)), event.oldValues.get(i));
					break;
				}
			}
		});
		assertEquals(0, changeCount[0]);

		for(int i = 0; i < 30; i++) {
			assertEquals(i, changeCount[0]);
			int toAdd = (int) (Math.random() * 2000000) - 1000000;
			controller.add(toAdd);
			correct.add(toAdd);
			assertEquals(correct, new ArrayList<>(observable));
			assertEquals(correct, compare);
		}
		assertEquals(30, changeCount[0]);

		Transaction trans = controller.lock(true, null);
		controller.clear();
		correct.clear();
		correct.addAll(observable);
		for(int i = 0; i < 30; i++) {
			int toAdd = (int) (Math.random() * 2000000) - 1000000;
			controller.add(toAdd);
			correct.add(toAdd);
			assertEquals(correct, new ArrayList<>(observable));
		}
		// The exact numbers here and down are pretty deep in the weeds of the changes observable. Still need this test to draw attention
		// to when this changes
		assertEquals(32, changeCount[0]);
		trans.close();
		assertEquals(33, changeCount[0]);
		assertEquals(correct, compare);

		sub.unsubscribe();
		controller.clear();
	}
}
