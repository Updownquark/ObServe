package org.observe.collect;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.qommons.QommonsTestUtils.collectionsEqual;
import static org.qommons.QommonsTestUtils.contains;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
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

import org.junit.Test;
import org.observe.DefaultObservable;
import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.SimpleSettableValue;
import org.observe.Subscription;
import org.observe.assoc.ObservableMultiMap;
import org.observe.collect.impl.ObservableArrayList;
import org.observe.collect.impl.ObservableHashSet;
import org.observe.collect.impl.ObservableLinkedList;
import org.observe.collect.impl.ObservableTreeList;
import org.observe.collect.impl.ObservableTreeSet;
import org.qommons.Equalizer;
import org.qommons.QommonsTestUtils;
import org.qommons.Transaction;

import com.google.common.reflect.TypeToken;

/** Tests observable collections and their default implementations */
public class ObservableCollectionsTest {
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
		QommonsTestUtils.testCollection(coll, check, v -> {
			if (v instanceof ObservableCollection)
				return (Consumer<? super T>) testingObservableCollection((ObservableCollection<Integer>) v,
					(Consumer<? super ObservableCollection<Integer>>) check, depth);
			else
				return null;
		} , depth);
	}

	private static <T extends ObservableCollection<Integer>> Checker<ObservableCollection<Integer>> testingObservableCollection(T coll,
		Consumer<? super T> check, int depth) {

		ObservableCollectionTester<Integer> tester = new ObservableCollectionTester<>(coll);

		// Quick test first
		coll.addAll(QommonsTestUtils.sequence(50, null, true));
		tester.checkSynced();
		if(check != null)
			check.accept(coll);
		coll.clear();
		tester.checkSynced();
		if(check != null)
			check.accept(coll);

		Function<Integer, Integer> mapFn = v -> v + 1000;
		Function<Integer, Integer> reverseMapFn = v -> v - 1000;
		ObservableCollection<Integer> mappedOL = coll.map(null, mapFn, reverseMapFn);
		ObservableCollectionTester<Integer> mappedTester = new ObservableCollectionTester<>(mappedOL);

		Predicate<Integer> filterFn1 = v -> v % 3 == 0;
		ObservableCollection<Integer> filteredOL1 = coll.filter(filterFn1);
		ObservableCollectionTester<Integer> filterTester1 = new ObservableCollectionTester<>(filteredOL1);

		Function<Integer, Integer> filterMap = v -> v;
		ObservableCollection<Integer> filterMapOL = coll.filterMap(null, filterMap, filterMap, false);
		ObservableCollectionTester<Integer> filterMapTester = new ObservableCollectionTester<>(filterMapOL);

		Function<Integer, Integer> groupFn = v -> v % 3;
		ObservableMultiMap<Integer, Integer> grouped = coll.groupBy(groupFn);
		Map<Integer, List<Integer>> groupedSynced = new LinkedHashMap<>();
		ObservableCollectionsTest.sync(grouped, groupedSynced, () -> new ArrayList<>());

		BinaryOperator<Integer> combineFn = (v1, v2) -> v1 + v2;
		BinaryOperator<Integer> reverseCombineFn = (v1, v2) -> v1 - v2;
		SimpleSettableValue<Integer> combineVar = new SimpleSettableValue<>(Integer.class, false);
		combineVar.set(10000, null);
		ObservableCollection<Integer> combinedOL = coll.combine(combineVar, coll.getType(), combineFn, reverseCombineFn);
		ObservableCollectionTester<Integer> combinedTester = new ObservableCollectionTester<>(combinedOL);

		// TODO Test reversed sets

		BinaryOperator<Integer> maxFn = (v1, v2) -> v1 >= v2 ? v1 : v2;
		ObservableValue<Integer> sum = coll.reduce(0, combineFn, reverseCombineFn);
		ObservableValue<Integer> maxValue = coll.reduce(Integer.MIN_VALUE, maxFn);
		Integer [] observedSum = new Integer[1];
		Integer [] observedMax = new Integer[1];
		Subscription sumSub = sum.value().act(v -> observedSum[0] = v);
		Subscription maxSub = maxValue.value().act(v -> {
			observedMax[0] = v;
		});

		// If sorted set, test on some synced sub-sets
		class SubSetRange {
			final Integer min;

			final Integer max;

			final boolean includeMin;

			final boolean includeMax;

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
				boolean ordered = coll instanceof ObservableOrderedCollection;
				tester.checkSynced();
				if(check != null)
					check.accept(coll);

				mappedTester.set(coll.stream().map(mapFn).collect(Collectors.toList()));
				mappedTester.check();

				filterTester1.set(coll.stream().filter(filterFn1).collect(Collectors.toList()));
				filterTester1.check();

				filterMapTester.set(tester.getSyncedCopy());
				filterMapTester.check();

				Set<Integer> groupKeySet = tester.getSyncedCopy().stream().map(groupFn).collect(Collectors.toSet());
				assertThat(grouped.keySet(), collectionsEqual(groupKeySet, false));
				assertThat(groupedSynced.keySet(), collectionsEqual(groupKeySet, false));
				for(Integer groupKey : groupKeySet) {
					List<Integer> values = tester.getSyncedCopy().stream().filter(v -> Objects.equals(groupFn.apply(v), groupKey))
						.collect(Collectors.toList());
					assertThat(grouped.get(groupKey), collectionsEqual(values, ordered));
					assertThat(groupedSynced.get(groupKey), collectionsEqual(values, ordered));
				}

				combinedTester.set(coll.stream().map(v -> v + combineVar.get()).collect(Collectors.toList()));
				combinedTester.check();

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
					assertEquals(actualMax.get(), maxValue.get());
					assertEquals(actualMax.get(), observedMax[0]);
				} else {
					assertEquals(Integer.valueOf(Integer.MIN_VALUE), maxValue.get());
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
				if (depth < QommonsTestUtils.COLLECTION_TEST_DEPTH) {
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

				tester.setSynced(false);
				mappedTester.setSynced(false);
				filterTester1.setSynced(false);
				filterMapTester.setSynced(false);
				combinedTester.setSynced(false);
				if(syncedSubSetSubs != null) {
					for(Subscription sub : syncedSubSetSubs)
						sub.unsubscribe();
				}

				sumSub.unsubscribe();
				maxSub.unsubscribe();
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
		return sync(coll, synced, new int[1]);
	}

	private static <T> Subscription sync(ObservableCollection<T> coll, List<T> synced, int [] opCount) {
		if(coll instanceof ObservableOrderedCollection)
			return ((ObservableOrderedCollection<T>) coll).onOrderedElement(el -> {
				el.subscribe(new Observer<ObservableValueEvent<T>>() {
					@Override
					public <V extends ObservableValueEvent<T>> void onNext(V evt) {
						opCount[0]++;
						if(evt.isInitial())
							synced.add(el.getIndex(), evt.getValue());
						else {
							assertEquals(evt.getOldValue(), synced.get(el.getIndex()));
							synced.set(el.getIndex(), evt.getValue());
						}
					}

					@Override
					public <V extends ObservableValueEvent<T>> void onCompleted(V evt) {
						opCount[0]++;
						assertEquals(evt.getValue(), synced.remove(el.getIndex()));
					}
				});
			});
		else
			return coll.onElement(el -> {
				el.subscribe(new Observer<ObservableValueEvent<T>>() {
					@Override
					public <V extends ObservableValueEvent<T>> void onNext(V evt) {
						opCount[0]++;
						if(evt.isInitial())
							synced.add(evt.getValue());
						else {
							assertTrue(synced.remove(evt.getOldValue()));
							synced.add(evt.getValue());
						}
					}

					@Override
					public <V extends ObservableValueEvent<T>> void onCompleted(V evt) {
						opCount[0]++;
						assertTrue(synced.remove(evt.getValue()));
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

	/** Tests {@link ObservableCollection#observeContainsAll(ObservableCollection)} */
	@Test
	public void observableContainsAll() {
		ObservableArrayList<Integer> list1 = new ObservableArrayList<>(TypeToken.of(Integer.TYPE));
		ObservableArrayList<Integer> list2 = new ObservableArrayList<>(TypeToken.of(Integer.TYPE));
		list1.addValues(0, 1, 2, 4, 5, 6, 7, 8, 8);
		list2.addValues(0, 2, 4, 6, 8, 10, 10);
		ObservableValue<Boolean> containsAll = list1.observeContainsAll(list2);
		assertEquals(false, containsAll.get());
		boolean[] reported = new boolean[1];
		int[] events = new int[1];
		Subscription sub = containsAll.value().act(//
			ca -> {
				reported[0] = ca;
				events[0]++;
			});
		int correctEvents = 1;
		assertEquals(false, reported[0]);
		assertEquals(correctEvents, events[0]);
		list1.add(10);
		correctEvents++;
		assertEquals(true, reported[0]);
		assertEquals(correctEvents, events[0]);
		list1.remove(Integer.valueOf(10));
		correctEvents++;
		assertEquals(false, reported[0]);
		assertEquals(correctEvents, events[0]);
		list2.remove(Integer.valueOf(10));
		assertEquals(false, reported[0]);
		assertEquals(correctEvents, events[0]);
		list2.remove(Integer.valueOf(10));
		correctEvents++;
		assertEquals(true, reported[0]);
		assertEquals(correctEvents, events[0]);
		list2.add(10);
		correctEvents++;
		assertEquals(false, reported[0]);
		assertEquals(correctEvents, events[0]);
		list2.remove(Integer.valueOf(10));
		correctEvents++;
		assertEquals(true, reported[0]);
		assertEquals(correctEvents, events[0]);
		list1.remove(Integer.valueOf(8));
		assertEquals(true, reported[0]);
		assertEquals(correctEvents, events[0]);
		list1.remove(Integer.valueOf(8));
		correctEvents++;
		assertEquals(false, reported[0]);
		assertEquals(correctEvents, events[0]);
		try (Transaction t = list1.lock(true, null)) {
			list1.add(8);
			list1.remove(Integer.valueOf(8));
			list1.add(8);
			list1.remove(Integer.valueOf(8));
		}
		assertEquals(false, reported[0]);
		assertEquals(correctEvents, events[0]);
		try (Transaction t = list1.lock(true, null)) {
			list1.add(8);
			list1.remove(Integer.valueOf(8));
			list1.add(8);
		}
		correctEvents++;
		assertEquals(true, reported[0]);
		assertEquals(correctEvents, events[0]);

		sub.unsubscribe();
		list1.remove(Integer.valueOf(8));
		assertEquals(true, reported[0]);
		assertEquals(correctEvents, events[0]);
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

	/** Tests {@link ObservableSet#filterStatic(Predicate)} */
	@Test
	public void observableSetFilterStatic() {
		ObservableHashSet<Integer> set = new ObservableHashSet<>(TypeToken.of(Integer.TYPE));
		Set<Integer> compare1 = new TreeSet<>();
		Set<Integer> correct = new TreeSet<>();
		set.filterStatic(value -> (value != null && value % 2 == 0)).onElement(element -> {
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

		for (int i = 0; i < 30; i++) {
			set.add(i);
			if (i % 2 == 0)
				correct.add(i);
			assertEquals(correct, compare1);
		}
		for (int i = 0; i < 30; i++) {
			set.remove(i);
			if (i % 2 == 0)
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

	/** Tests {@link ObservableSet#unique(ObservableCollection, Equalizer)} */
	@Test
	public void observableSetUnique() {
		ObservableArrayList<Integer> list = new ObservableArrayList<>(TypeToken.of(Integer.TYPE));
		ObservableSet<Integer> unique = ObservableSet.unique(list, Objects::equals);
		testUnique(list, unique);
	}

	/** Tests {@link ObservableOrderedSet#unique(ObservableOrderedCollection, Equalizer, boolean)} */
	@Test
	public void observableOrderedSetUnique() {
		ObservableArrayList<Integer> list = new ObservableArrayList<>(TypeToken.of(Integer.TYPE));
		ObservableOrderedSet<Integer> unique = ObservableOrderedSet.unique(list, Objects::equals, true);
		testUnique(list, unique);
	}

	/** Tests {@link ObservableSortedSet#unique(ObservableCollection, Comparator)} */
	@Test
	public void observableSortedSetUnique() {
		ObservableArrayList<Integer> list = new ObservableArrayList<>(TypeToken.of(Integer.TYPE));
		ObservableSortedSet<Integer> unique = ObservableSortedSet.unique(list, Integer::compareTo);
		testUnique(list, unique);
	}

	private void testUnique(ObservableList<Integer> list, ObservableSet<Integer> unique) {
		List<Integer> compare1 = new ArrayList<>();
		Set<Integer> correct = new TreeSet<>();

		sync(unique, compare1);

		for(int i = 0; i < 30; i++) {
			list.add(i);
			correct.add(i);
		}
		assertEquals(correct, new TreeSet<>(unique));
		assertEquals(correct, new TreeSet<>(compare1));
		assertEquals(correct.size(), compare1.size());
		if (unique instanceof ObservableOrderedCollection)
			assertThat(compare1, collectionsEqual(correct, true));

		for(int i = 0; i < 30; i++) {
			list.add(i);
		}
		assertEquals(correct, new TreeSet<>(unique));
		assertEquals(correct, new TreeSet<>(compare1));
		assertEquals(correct.size(), compare1.size());
		if (unique instanceof ObservableOrderedCollection)
			assertThat(compare1, collectionsEqual(correct, true));

		for(int i = 29; i >= 0; i--) {
			list.remove(30 + i);
		}
		assertEquals(correct, new TreeSet<>(unique));
		assertEquals(correct, new TreeSet<>(compare1));
		assertEquals(correct.size(), compare1.size());
		if (unique instanceof ObservableOrderedCollection)
			assertThat(compare1, collectionsEqual(correct, true));

		for(int i = 0; i < 30; i++) {
			list.add(i);
		}
		assertEquals(correct, new TreeSet<>(unique));
		assertEquals(correct, new TreeSet<>(compare1));
		assertEquals(correct.size(), compare1.size());
		if (unique instanceof ObservableOrderedCollection)
			assertThat(compare1, collectionsEqual(correct, true));

		for(int i = 29; i >= 0; i--) {
			list.remove(i);
		}
		assertEquals(correct, new TreeSet<>(unique));
		assertEquals(correct, new TreeSet<>(compare1));
		assertEquals(correct.size(), compare1.size());
		if (unique instanceof ObservableOrderedCollection)
			assertThat(compare1, collectionsEqual(correct, true));

		for(int i = 29; i >= 0; i--) {
			list.remove(i);
			correct.remove(i);
		}
		assertEquals(correct, new TreeSet<>(unique));
		assertEquals(correct, new TreeSet<>(compare1));
		assertEquals(correct.size(), compare1.size());
		if (unique instanceof ObservableOrderedCollection)
			assertThat(compare1, collectionsEqual(correct, true));

		for(int i = 0; i < 30; i++) {
			list.add(i);
			list.add(i);
			correct.add(i);
		}
		assertEquals(correct, new TreeSet<>(unique));
		assertEquals(correct, new TreeSet<>(compare1));
		assertEquals(correct.size(), compare1.size());
		if (unique instanceof ObservableOrderedCollection)
			assertThat(compare1, collectionsEqual(correct, true));
		list.clear();
		correct.clear();
		assertEquals(correct, new TreeSet<>(unique));
		assertEquals(correct, new TreeSet<>(compare1));
		assertEquals(correct.size(), compare1.size());
		if (unique instanceof ObservableOrderedCollection)
			assertThat(compare1, collectionsEqual(correct, true));
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

	/** Tests {@link ObservableList#filter(Predicate)} */
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

	/** Tests {@link ObservableList#filterStatic(Predicate)} */
	@Test
	public void observableListFilterStatic() {
		ObservableArrayList<Integer> list = new ObservableArrayList<>(TypeToken.of(Integer.TYPE));
		List<Integer> compare1 = new ArrayList<>();
		List<Integer> correct = new ArrayList<>();
		list.filterStatic(value -> value != null && value % 2 == 0).onElement(element -> {
			ObservableOrderedElement<Integer> listEl = (ObservableOrderedElement<Integer>) element;
			element.subscribe(new Observer<ObservableValueEvent<Integer>>() {
				@Override
				public <V extends ObservableValueEvent<Integer>> void onNext(V value) {
					if (!value.isInitial())
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

		for (int i = 0; i < 30; i++) {
			list.add(i);
			if (i % 2 == 0)
				correct.add(i);
			assertEquals(correct, compare1);
		}
		for (int i = 0; i < 30; i++) {
			list.add(i);
			if (i % 2 == 0)
				correct.add(i);
			assertEquals(correct, compare1);
		}
		for (int i = 0; i < 30; i++) {
			list.remove(Integer.valueOf(i));
			if (i % 2 == 0)
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
		ObservableValue<Integer> found = list.findFirst(value -> value % 3 == 0);
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

	/** Tests {@link ObservableList#flattenValues(ObservableList)} */
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
		ObservableList.flattenValues(list).findFirst(value -> value % 3 == 0).value()
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

	/** Tests {@link ObservableOrderedCollection#sorted(java.util.Comparator)} */
	@Test
	public void sortedObservableList() {
		ObservableArrayList<Integer> list = new ObservableArrayList<>(TypeToken.of(Integer.TYPE));

		List<Integer> compare = new ArrayList<>();
		list.sorted(Integer::compareTo).onElement(element -> {
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

		List<Integer> correct = new ArrayList<>(30);

		for(int i = 30; i >= 0; i--) {
			list.add(i);
			correct.add(i);
		}

		java.util.Collections.sort(correct);
		assertEquals(correct, compare);

		for (int i = 0; i < list.size(); i++) {
			list.set(i, list.get(i) - 50);
			correct.clear();
			correct.addAll(list);
			java.util.Collections.sort(correct);
			assertEquals(correct, compare);
		}

		for (int i = -20; i >= -50; i--) {
			list.remove((Integer) i);
			correct.remove((Integer) i);

			assertEquals(correct, compare);
		}
	}

	/**
	 * Tests {@link ObservableOrderedCollection#flatten(ObservableOrderedCollection)} and
	 * {@link ObservableOrderedCollection#sorted(Comparator)}
	 */
	@Test
	public void observableOrderedFlatten() {
		observableOrderedFlatten(null);
		observableOrderedFlatten(Comparable::compareTo);
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

		ObservableOrderedCollection<Integer> flat = ObservableOrderedCollection.flatten(outer);
		if (comparator != null)
			flat = flat.sorted(comparator);
		Subscription sub = flat.onOrderedElement(element -> {
			element.subscribe(new Observer<ObservableValueEvent<Integer>>() {
				@Override
				public <V extends ObservableValueEvent<Integer>> void onNext(V event) {
					if(event.isInitial())
						compare.add(element.getIndex(), event.getValue());
					else
						compare.set(element.getIndex(), event.getValue());
				}

				@Override
				public <V extends ObservableValueEvent<Integer>> void onCompleted(V event) {
					compare.remove(element.getIndex());
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
		ObservableCollectionTester<Integer> tester = new ObservableCollectionTester<>(list);

		int count = 30;
		for(int i = 0; i < count; i++) {
			set.add(i);
			tester.add(i);

			tester.check();
		}
		ArrayList<Integer> compare2 = new ArrayList<>();
		sync(list, compare2);
		assertEquals(tester.getExpected(), compare2);
		for(int i = count - 1; i >= 0; i--) {
			if(i % 2 == 0) {
				set.remove(i);
				tester.remove(i); // By index
			}

			tester.check();
			assertEquals(tester.getExpected(), compare2);
		}
	}

	/**
	 * Tests {@link ObservableSet#unique(ObservableCollection, Equalizer)} wrapped with {@link ObservableList#asList(ObservableCollection)}.
	 * I wrote this test to capture a specific test case, but I couldn't reproduce the error here. Not sure this test is super valuable.
	 */
	@Test
	public void observableListFromUnique() {
		ObservableArrayList<Integer> list = new ObservableArrayList<>(TypeToken.of(Integer.TYPE));
		ObservableList<Integer> uniqued = ObservableList.asList(ObservableSet.unique(list, Objects::equals));
		ObservableCollectionTester<Integer> tester = new ObservableCollectionTester<>(uniqued);

		int count = 30;
		for(int i = 0; i < count; i++) {
			list.add(i);
			list.add(i);
			tester.add(i);

			tester.check();
		}
		list.clear();
		tester.clear();
		tester.check();
	}

	/** Tests {@link ObservableCollection#refreshEach(Function)} */
	@Test
	public void testRefreshEach() {
		ObservableArrayList<int []> list = new ObservableArrayList<>(new TypeToken<int []>() {});
		Map<int [], Observable<Void>> elObservables = new LinkedHashMap<>();
		Map<int [], Observer<Void>> controllers = new LinkedHashMap<>();
		for(int i = 0; i < 30; i++) {
			int [] el = new int[] {i};
			DefaultObservable<Void> elObs = new DefaultObservable<>();
			elObservables.put(el, elObs);
			controllers.put(el, elObs.control(null));
			list.add(el);
		}
		ObservableList<Integer> values = list.refreshEach(el -> elObservables.get(el)).map(el -> el[0]);
		ObservableCollectionTester<Integer> tester = new ObservableCollectionTester<>(values);
		tester.check();

		for(int i = 0; i < list.size(); i++) {
			list.get(i)[0]++;
			tester.getExpected().set(i, list.get(i)[0]);
			controllers.get(list.get(i)).onNext(null);
			tester.check();
		}

		for(int i = 0; i < list.size(); i++) {
			list.get(i)[0] *= 50;
			tester.getExpected().set(i, list.get(i)[0]);
			controllers.get(list.get(i)).onNext(null);
			tester.check();
		}

		for(int i = 0; i < list.size(); i++) {
			list.get(i)[0]--;
			tester.getExpected().set(i, list.get(i)[0]);
			controllers.get(list.get(i)).onNext(null);
			tester.check();
		}

		for(int i = 0; i < list.size(); i++) {
			list.get(i)[0] = 0;
			tester.getExpected().set(i, list.get(i)[0]);
			controllers.get(list.get(i)).onNext(null);
			tester.check();
		}

		list.clear();
		for(Observer<Void> controller : controllers.values())
			controller.onNext(null);
		tester.checkOps(0);

		for(int [] value : controllers.keySet())
			list.add(value);

		tester.setSynced(false);
		for(int i = 0; i < list.size(); i++) {
			controllers.get(list.get(i)).onNext(null);
		}
		tester.checkOps(0);
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

	private void testTransactionsByFind(ObservableList<Integer> observable, TransactableList<Integer> controller) {
		Integer [] found = new Integer[1];
		int [] findCount = new int[1];
		Subscription sub = observable.findFirst(value -> value % 5 == 4).act(event -> {
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

	/** Tests transactions caused by {@link ObservableCollection#refresh(Observable) refreshing} on an observable */
	@Test
	public void testTransactionsRefresh() {
		ObservableArrayList<Integer> list = new ObservableArrayList<>(new TypeToken<Integer>() {});
		DefaultObservable<Void> refresh = new DefaultObservable<>();
		Observer<Void> control = refresh.control(null);

		for(int i = 0; i < 30; i++)
			list.add(i);

		int [] changes = new int[1];
		Subscription sub = list.refresh(refresh).simpleChanges().act(v -> {
			changes[0]++;
		});
		int correctChanges = 0;

		assertEquals(correctChanges, changes[0]);

		control.onNext(null);
		correctChanges++;
		assertEquals(correctChanges, changes[0]);

		Transaction trans = list.lock(true, null);
		control.onNext(null);
		control.onNext(null);
		trans.close();
		correctChanges++;
		assertEquals(correctChanges, changes[0]);

		trans = list.lock(true, null);
		for(int i = 0; i < 30; i++)
			list.set(i, i + 1);
		control.onNext(null);
		trans.close();
		correctChanges++;
		assertEquals(correctChanges, changes[0]);

		list.clear();
		correctChanges++;
		assertEquals(correctChanges, changes[0]);
		control.onNext(null);
		assertEquals(correctChanges, changes[0]);

		list.addAll(java.util.Arrays.asList(0, 1, 2, 3, 4));
		correctChanges++;
		assertEquals(correctChanges, changes[0]);
		control.onNext(null);
		correctChanges++;
		assertEquals(correctChanges, changes[0]);

		int preChanges = changes[0];
		sub.unsubscribe();
		control.onNext(null);
		assertEquals(preChanges, changes[0]);
	}

	/**
	 * Tests transactions caused by {@link ObservableCollection#combine(ObservableValue, java.util.function.BiFunction) combining} a list
	 * with an observable value
	 */
	@Test
	public void testTransactionsCombined() {
		ObservableArrayList<Integer> list = new ObservableArrayList<>(new TypeToken<Integer>() {});
		SimpleSettableValue<Integer> mult = new SimpleSettableValue<>(new TypeToken<Integer>() {}, false);
		mult.set(1, null);
		ObservableList<Integer> product = list.combine(mult, (v1, v2) -> v1 * v2);

		for(int i = 0; i < 30; i++)
			list.add(i);

		int [] changes = new int[1];
		Subscription sub = product.simpleChanges().act(v -> {
			changes[0]++;
		});
		int correctChanges = 0;

		assertEquals(correctChanges, changes[0]);

		mult.set(2, null);
		correctChanges++;
		assertEquals(correctChanges, changes[0]);

		Transaction trans = list.lock(true, null);
		mult.set(3, null);
		mult.set(4, null);
		trans.close();
		correctChanges++;
		assertEquals(correctChanges, changes[0]);

		trans = list.lock(true, null);
		for(int i = 0; i < 30; i++)
			list.set(i, i + 1);
		mult.set(5, null);
		trans.close();
		correctChanges++;
		assertEquals(correctChanges, changes[0]);

		list.clear();
		correctChanges++;
		assertEquals(correctChanges, changes[0]);
		mult.set(6, null);
		assertEquals(correctChanges, changes[0]);

		list.addAll(java.util.Arrays.asList(0, 1, 2, 3, 4));
		correctChanges++;
		assertEquals(correctChanges, changes[0]);
		mult.set(7, null);
		correctChanges++;
		assertEquals(correctChanges, changes[0]);

		int preChanges = changes[0];
		sub.unsubscribe();
		mult.set(8, null);
		assertEquals(preChanges, changes[0]);
	}
}
