package org.observe.collect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.qommons.QommonsTestUtils.collectionsEqual;

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

import org.junit.Assert;
import org.junit.Test;
import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.SimpleObservable;
import org.observe.SimpleSettableValue;
import org.observe.Subscription;
import org.observe.assoc.ObservableMultiMap;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.qommons.QommonsTestUtils;
import org.qommons.Transaction;
import org.qommons.collect.CircularArrayList;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.collect.TransactableList;
import org.qommons.tree.BetterTreeList;
import org.qommons.tree.BetterTreeSet;

import com.google.common.reflect.TypeToken;


/** Tests observable collections and their default implementations */
public class ObservableCollectionsTest {
	/** The primitive integer type, for re-use */
	public static final TypeToken<Integer> intType = TypeToken.of(int.class);

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
		ObservableCollection<Integer> mappedOL = coll.flow().map(intType, mapFn, options -> options.cache(false).withReverse(reverseMapFn))
			.collectPassive();
		ObservableCollectionTester<Integer> mappedTester = new ObservableCollectionTester<>(mappedOL);

		Function<Integer, String> filterFn1 = v -> v % 3 == 0 ? null : "no";
		ObservableCollection<Integer> filteredOL1 = coll.flow().filter(filterFn1).collect();
		ObservableCollectionTester<Integer> filterTester1 = new ObservableCollectionTester<>(filteredOL1);

		Function<Integer, Integer> groupFn = v -> v % 3;
		ObservableMultiMap<Integer, Integer> grouped = coll.flow()
			.groupBy(intType, groupFn, options -> options.useFirst(true).withStaticCategories(true)).collect();
		Map<Integer, List<Integer>> groupedSynced = new LinkedHashMap<>();
		ObservableCollectionsTest.sync(grouped, groupedSynced, () -> new ArrayList<>());

		BinaryOperator<Integer> combineFn = (v1, v2) -> v1 + v2;
		BinaryOperator<Integer> reverseCombineFn = (v1, v2) -> v1 - v2;
		SimpleSettableValue<Integer> combineVar = new SimpleSettableValue<>(Integer.class, false);
		combineVar.set(10000, null);
		ObservableCollection<Integer> combinedOL = coll.flow().combine(intType, combine -> {
			return combine.with(combineVar).withReverse(reverseCombineFn).build(combineFn);
		}).collect();
		ObservableCollectionTester<Integer> combinedTester = new ObservableCollectionTester<>(combinedOL);

		// TODO Test reversed observable collections

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
				tester.checkSynced();
				if(check != null)
					check.accept(coll);

				mappedTester.set(coll.stream().map(mapFn).collect(Collectors.toList()));
				mappedTester.check();

				filterTester1.set(coll.stream().filter(v -> filterFn1.apply(v) == null).collect(Collectors.toList()));
				filterTester1.check();

				Set<Integer> groupKeySet = tester.getSyncedCopy().stream().map(groupFn).collect(Collectors.toSet());
				assertThat(grouped.keySet(), collectionsEqual(groupKeySet, false));
				assertThat(groupedSynced.keySet(), collectionsEqual(groupKeySet, false));
				for(Integer groupKey : groupKeySet) {
					List<Integer> values = tester.getSyncedCopy().stream().filter(v -> Objects.equals(groupFn.apply(v), groupKey))
						.collect(Collectors.toList());
					assertThat(grouped.get(groupKey), collectionsEqual(values, true));
					assertThat(groupedSynced.get(groupKey), collectionsEqual(values, true));
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

				ListIterator<Integer> listIter = ((List<Integer>) filteredOL1).listIterator();
				listIter.add(0);
				assertEquals(1, filteredOL1.size());
				accept(coll);
				try {
					listIter.add(1);
					assertTrue("Should have thrown an IllegalArgumentException", false);
				} catch (IllegalArgumentException e) {
				}
				listIter.previous();
				listIter.remove();
				assertEquals(0, filteredOL1.size());
				accept(coll);

				tester.setSynced(false);
				mappedTester.setSynced(false);
				filterTester1.setSynced(false);
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
		return coll.subscribe(evt -> {
			switch (evt.getType()) {
			case add:
				synced.add(evt.getIndex(), evt.getNewValue());
				break;
			case remove:
				assertEquals(evt.getOldValue(), synced.remove(evt.getIndex()));
				break;
			case set:
				assertEquals(evt.getOldValue(), synced.set(evt.getIndex(), evt.getNewValue()));
				break;
			}
		}, true);
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
		return map.subscribe(evt -> {
			switch(evt.getType()){
			case add:
				synced.computeIfAbsent(evt.getKey(), k -> collectCreator.get()).add(evt.getNewValue());
				break;
			case remove:
				C values = synced.get(evt.getKey());
				Assert.assertNotNull(values);
				if (values instanceof List)
					assertEquals(evt.getOldValue(), ((List<V>) values).remove(evt.getIndex()));
				else
					assertTrue(values.remove(evt.getNewValue()));
				if (values.isEmpty())
					synced.remove(evt.getKey());
				break;
			case set:
				values = synced.get(evt.getKey());
				Assert.assertNotNull(values);
				if (values instanceof List)
					assertEquals(evt.getOldValue(), ((List<V>) values).set(evt.getIndex(), evt.getNewValue()));
				else if (evt.getOldValue() != evt.getNewValue()) {
					assertTrue(values.remove(evt.getOldValue()));
					values.add(evt.getNewValue());
				}
				break;
			}
		}, true, true);
	}

	/** Runs a barrage of tests on a {@link DefaultObservableCollection} backed by a {@link CircularArrayList} */
	@Test
	public void testObservableArrayList() {
		CircularArrayList<Integer> backing = CircularArrayList.build().unsafe().build();
		testCollection(new DefaultObservableCollection<>(TypeToken.of(Integer.class), backing), set -> backing.checkValid());
	}

	/** Runs a barrage of tests on a {@link DefaultObservableCollection} backed by a {@link BetterTreeList} */
	@Test
	public void testObservableTreeList() {
		BetterTreeList<Integer> backing = new BetterTreeList<>(false);
		testCollection(new DefaultObservableCollection<>(TypeToken.of(Integer.class), backing), set -> backing.checkValid());
	}

	/** Runs a barrage of tests on a {@link DefaultObservableCollection} backed by a {@link BetterTreeSet} */
	@Test
	public void testObservableTreeSet() {
		BetterTreeSet<Integer> backing = new BetterTreeSet<>(false, Integer::compareTo);
		testCollection(new DefaultObservableCollection<>(TypeToken.of(Integer.class), backing), set -> backing.checkValid());
	}

	// Older, more specific tests

	/** Tests basic {@link ObservableCollection} functionality */
	@Test
	public void observableCollection() {
		ObservableCollection<Integer> list = ObservableCollection.create(intType);
		ObservableCollectionTester<Integer> tester = new ObservableCollectionTester<>(list);

		for (int i = 0; i < 30; i++) {
			list.add(i);
			tester.add(i);
			tester.check(1);
		}
		for (int i = 0; i < 30; i++) {
			list.remove((Integer) i);
			tester.remove(i);
			tester.check(1);
		}

		for (int i = 0; i < 30; i++) {
			list.add(i);
			tester.add(i);
			tester.check(1);
		}
		tester.setSynced(false);
		List<Integer> unchanged = new ArrayList<>(list);
		for (int i = 30; i < 50; i++) {
			list.add(i);
			assertEquals(unchanged, tester.getExpected());
		}
		for (int i = 0; i < 30; i++) {
			list.remove((Integer) i);
			assertEquals(unchanged, tester.getExpected());
		}
	}

	/** Tests basic {@link ObservableSet} functionality */
	@Test
	public void observableSet() {
		ObservableSet<Integer> set = ObservableCollection.create(intType).flow().unique().collect();
		Set<Integer> compare1 = new TreeSet<>();
		Set<Integer> correct = new TreeSet<>();
		CollectionSubscription sub = set.subscribe(evt -> {
			switch (evt.getType()) {
			case add:
				assertTrue(compare1.add(evt.getNewValue()));
				break;
			case remove:
				assertTrue(compare1.remove(evt.getOldValue()));
				break;
			case set:
				throw new IllegalStateException("No sets on sets");
			}
		}, true);

		for(int i = 0; i < 30; i++) {
			assertTrue(set.add(i));
			correct.add(i);
			assertEquals(new TreeSet<>(set), compare1);
			assertEquals(correct, compare1);
		}
		for (int i = 0; i < 30; i++) {
			assertFalse(set.add(i));
			correct.add(i);
			assertEquals(new TreeSet<>(set), compare1);
			assertEquals(correct, compare1);
		}
		for(int i = 0; i < 30; i++) {
			assertTrue(set.remove(Integer.valueOf(i)));
			correct.remove(i);
			assertEquals(new TreeSet<>(set), compare1);
			assertEquals(correct, compare1);
		}

		for(int i = 0; i < 30; i++) {
			assertTrue(set.add(i));
			correct.add(i);
			assertEquals(new TreeSet<>(set), compare1);
			assertEquals(correct, compare1);
		}
		sub.unsubscribe();
		for(int i = 30; i < 50; i++) {
			assertTrue(set.add(i));
			assertEquals(correct, compare1);
		}
		for(int i = 0; i < 30; i++) {
			assertTrue(set.remove(Integer.valueOf(i)));
			assertEquals(correct, compare1);
		}
	}

	/** Tests {@link ObservableCollection#observeContainsAll(ObservableCollection)} */
	@Test
	public void observableContainsAll() {
		ObservableCollection<Integer> list1 = ObservableCollection.create(intType);
		ObservableCollection<Integer> list2 = ObservableCollection.create(intType);
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

	/** Tests {@link CollectionDataFlow#map(TypeToken, Function)} */
	@Test
	public void observableSetMap() {
		ObservableSet<Integer> set = ObservableCollection.create(intType).flow().unique().collect();
		Set<Integer> compare1 = new TreeSet<>();
		Set<Integer> correct = new TreeSet<>();
		set.flow().map(intType, value -> value * 10, options -> options.cache(false)).collectPassive().subscribe(evt -> {
			switch (evt.getType()) {
			case add:
				assertTrue(compare1.add(evt.getNewValue()));
				break;
			case remove:
				assertTrue(compare1.remove(evt.getOldValue()));
				break;
			case set:
				throw new IllegalStateException("No sets on sets");
			}
		}, true);

		for(int i = 0; i < 30; i++) {
			set.add(i);
			correct.add(i * 10);
			assertEquals(correct, compare1);
		}
		for(int i = 0; i < 30; i++) {
			set.remove(Integer.valueOf(i));
			correct.remove(i * 10);
			assertEquals(correct, compare1);
		}
	}

	/** Tests {@link CollectionDataFlow#filter(Function)} */
	@Test
	public void observableSetFilter() {
		ObservableSet<Integer> set = ObservableCollection.create(intType).flow().unique().collect();
		Set<Integer> compare1 = new TreeSet<>();
		Set<Integer> correct = new TreeSet<>();
		set.flow().filter(value -> (value != null && value % 2 == 0) ? null : StdMsg.ILLEGAL_ELEMENT).collect().subscribe(evt -> {
			switch (evt.getType()) {
			case add:
				assertTrue(compare1.add(evt.getNewValue()));
				break;
			case remove:
				assertTrue(compare1.remove(evt.getOldValue()));
				break;
			case set:
				throw new IllegalStateException("No sets on sets");
			}
		}, true);

		for(int i = 0; i < 30; i++) {
			set.add(i);
			if(i % 2 == 0)
				correct.add(i);
			assertEquals(correct, compare1);
		}
		for(int i = 0; i < 30; i++) {
			set.remove(Integer.valueOf(i));
			if(i % 2 == 0)
				correct.remove(i);
			assertEquals(correct, compare1);
		}
	}

	/** Tests {@link CollectionDataFlow#filterStatic(Function)} */
	@Test
	public void observableSetFilterStatic() {
		ObservableSet<Integer> set = ObservableCollection.create(intType).flow().unique().collect();
		Set<Integer> compare1 = new TreeSet<>();
		Set<Integer> correct = new TreeSet<>();
		set.flow().filterStatic(value -> (value != null && value % 2 == 0) ? null : StdMsg.ILLEGAL_ELEMENT).collect().subscribe(evt -> {
			switch (evt.getType()) {
			case add:
				assertTrue(compare1.add(evt.getNewValue()));
				break;
			case remove:
				assertTrue(compare1.remove(evt.getOldValue()));
				break;
			case set:
				throw new IllegalStateException("No sets on sets");
			}
		}, true);

		for (int i = 0; i < 30; i++) {
			set.add(i);
			if (i % 2 == 0)
				correct.add(i);
			assertEquals(correct, compare1);
		}
		for (int i = 0; i < 30; i++) {
			set.remove(Integer.valueOf(i));
			if (i % 2 == 0)
				correct.remove(i);
			assertEquals(correct, compare1);
		}
	}

	/** Tests {@link CollectionDataFlow#filter(Function)} and {@link CollectionDataFlow#map(TypeToken, Function)} together */
	@Test
	public void observableSetFilterMap() {
		ObservableSet<Integer> set = ObservableCollection.create(intType).flow().unique().collect();
		Set<Integer> compare1 = new TreeSet<>();
		Set<Integer> correct = new TreeSet<>();
		set.flow()//
		.filter(value -> (value == null || value % 2 != 0) ? StdMsg.ILLEGAL_ELEMENT : null)//
		.map(intType, value -> value / 2)//
		.collect().subscribe(evt -> {
			switch (evt.getType()) {
			case add:
				assertTrue(compare1.add(evt.getNewValue()));
				break;
			case remove:
				assertTrue(compare1.remove(evt.getOldValue()));
				break;
			case set:
				throw new IllegalStateException("No sets on sets");
			}
		}, true);

		for(int i = 0; i < 30; i++) {
			set.add(i);
			if(i % 2 == 0)
				correct.add(i / 2);
			assertEquals(correct, compare1);
		}
		for(int i = 0; i < 30; i++) {
			set.remove(Integer.valueOf(i));
			if(i % 2 == 0)
				correct.remove(i / 2);
			assertEquals(correct, compare1);
		}
	}

	/** Tests {@link CollectionDataFlow#combine(TypeToken, Function)} */
	@Test
	public void observableSetCombine() {
		ObservableSet<Integer> set = ObservableCollection.create(intType).flow().unique().collect();
		SimpleSettableValue<Integer> value1 = new SimpleSettableValue<>(Integer.TYPE, false);
		value1.set(1, null);
		List<Integer> compare1 = new ArrayList<>();
		Set<Integer> correct = new TreeSet<>();
		set.flow()//
		.combine(intType, combine -> {
			return combine.with(value1).build((v1, v2) -> v1 * v2);
		}).filter(value -> value != null && value % 3 == 0 ? null : StdMsg.ILLEGAL_ELEMENT)//
		.collect().subscribe(evt -> {
			switch (evt.getType()) {
			case add:
				assertTrue(compare1.add(evt.getNewValue()));
				break;
			case remove:
				assertTrue(compare1.remove(evt.getOldValue()));
				break;
			case set:
				assertTrue(compare1.remove(evt.getOldValue()));
				assertTrue(compare1.add(evt.getNewValue()));
				break;
			}
		}, true);

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
			set.remove(Integer.valueOf(i));
			int value = i * value1.get();
			if(value % 3 == 0)
				correct.remove(value);
			assertEquals(correct, new TreeSet<>(compare1));
			assertEquals(correct.size(), compare1.size());
		}
	}

	/** Tests {@link CollectionDataFlow#unique()} */
	@Test
	public void observableSetUnique() {
		ObservableCollection<Integer> list = ObservableCollection.create(intType);
		ObservableSet<Integer> unique = list.flow().unique().collect();
		testUnique(list, unique);
	}

	/** Tests {@link CollectionDataFlow#uniqueSorted(Comparator, boolean)} */
	@Test
	public void observableSortedSetUnique() {
		ObservableCollection<Integer> list = ObservableCollection.create(intType);
		ObservableSortedSet<Integer> unique = list.flow().uniqueSorted(Integer::compareTo, false).collect();
		testUnique(list, unique);
	}

	private void testUnique(ObservableCollection<Integer> list, ObservableSet<Integer> unique) {
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
		assertThat(compare1, collectionsEqual(correct, true));

		for(int i = 0; i < 30; i++) {
			list.add(i);
		}
		assertEquals(correct, new TreeSet<>(unique));
		assertEquals(correct, new TreeSet<>(compare1));
		assertEquals(correct.size(), compare1.size());
		assertThat(compare1, collectionsEqual(correct, true));

		for(int i = 29; i >= 0; i--) {
			list.remove(30 + i);
		}
		assertEquals(correct, new TreeSet<>(unique));
		assertEquals(correct, new TreeSet<>(compare1));
		assertEquals(correct.size(), compare1.size());
		assertThat(compare1, collectionsEqual(correct, true));

		for(int i = 0; i < 30; i++) {
			list.add(i);
		}
		assertEquals(correct, new TreeSet<>(unique));
		assertEquals(correct, new TreeSet<>(compare1));
		assertEquals(correct.size(), compare1.size());
		assertThat(compare1, collectionsEqual(correct, true));

		for(int i = 29; i >= 0; i--) {
			list.remove(i);
		}
		assertEquals(correct, new TreeSet<>(unique));
		assertEquals(correct, new TreeSet<>(compare1));
		assertEquals(correct.size(), compare1.size());
		assertThat(compare1, collectionsEqual(correct, true));

		for(int i = 29; i >= 0; i--) {
			list.remove(i);
			correct.remove(i);
		}
		assertEquals(correct, new TreeSet<>(unique));
		assertEquals(correct, new TreeSet<>(compare1));
		assertEquals(correct.size(), compare1.size());
		assertThat(compare1, collectionsEqual(correct, true));

		for(int i = 0; i < 30; i++) {
			list.add(i);
			list.add(i);
			correct.add(i);
		}
		assertEquals(correct, new TreeSet<>(unique));
		assertEquals(correct, new TreeSet<>(compare1));
		assertEquals(correct.size(), compare1.size());
		assertThat(compare1, collectionsEqual(correct, true));
		list.clear();
		correct.clear();
		assertEquals(correct, new TreeSet<>(unique));
		assertEquals(correct, new TreeSet<>(compare1));
		assertEquals(correct.size(), compare1.size());
		assertThat(compare1, collectionsEqual(correct, true));
	}

	/** Tests {@link CollectionDataFlow#flatMapC(TypeToken, Function)} */
	@Test
	public void observableCollectionFlatten() {
		ObservableSet<Integer> set1 = ObservableCollection.create(intType).flow().unique().collect();
		ObservableSet<Integer> set2 = ObservableCollection.create(intType).flow().unique().collect();
		ObservableSet<Integer> set3 = ObservableCollection.create(intType).flow().unique().collect();
		ObservableCollection<ObservableSet<Integer>> outer = ObservableCollection.create(new TypeToken<ObservableSet<Integer>>() {});
		outer.add(set1);
		outer.add(set2);
		CollectionDataFlow<?, ?, Integer> flat = outer.flow().flatMapC(intType, s -> s);
		ObservableCollectionTester<Integer> tester = new ObservableCollectionTester<>(flat.collect());
		ObservableCollectionTester<Integer> filterTester = new ObservableCollectionTester<>(//
			flat.filter(value -> value != null && value % 3 == 0 ? null : StdMsg.ILLEGAL_ELEMENT).collect());

		List<Integer> correct1 = new ArrayList<>();
		List<Integer> correct2 = new ArrayList<>();
		List<Integer> correct3 = new ArrayList<>();

		for(int i = 0; i < 30; i++) {
			set1.add(i);
			set2.add(i * 10);
			set3.add(i * 100);
			correct1.add(i);
			correct2.add(i * 10);
			correct3.add(i * 100);
			tester.clear();
			tester.addAll(correct1);
			tester.addAll(correct2);
			filterTester.clear();
			for(int j : correct1)
				if(j % 3 == 0)
					filterTester.add(j);
			for(int j : correct2)
				if(j % 3 == 0)
					filterTester.add(j);

			tester.check();
			filterTester.check();
		}

		outer.add(set3);
		tester.clear();
		tester.addAll(correct1);
		tester.addAll(correct2);
		tester.addAll(correct3);
		filterTester.clear();
		for(int j : correct1)
			if(j % 3 == 0)
				filterTester.add(j);
		for(int j : correct2)
			if(j % 3 == 0)
				filterTester.add(j);
		for(int j : correct3)
			if(j % 3 == 0)
				filterTester.add(j);

		tester.check();
		filterTester.check();

		outer.remove(set2);
		tester.clear();
		tester.addAll(correct1);
		tester.addAll(correct3);
		filterTester.clear();
		for(int j : correct1)
			if(j % 3 == 0)
				filterTester.add(j);
		for(int j : correct3)
			if(j % 3 == 0)
				filterTester.add(j);

		tester.check();
		filterTester.check();

		for(int i = 0; i < 30; i++) {
			set1.remove(Integer.valueOf(i));
			set2.remove(Integer.valueOf(i * 10));
			set3.remove(Integer.valueOf(i * 100));
			correct1.remove((Integer) i);
			correct2.remove((Integer) (i * 10));
			correct3.remove((Integer) (i * 100));
			tester.clear();
			tester.addAll(correct1);
			tester.addAll(correct3);
			filterTester.clear();
			for(int j : correct1)
				if(j % 3 == 0)
					filterTester.add(j);
			for(int j : correct3)
				if(j % 3 == 0)
					filterTester.add(j);

			tester.check();
			filterTester.check();
		}
	}

	/** Tests {@link ObservableCollection#fold(ObservableCollection)} */
	@Test
	public void observableCollectionFold() {
		SimpleSettableValue<Integer> obs1 = new SimpleSettableValue<>(Integer.class, true);
		SimpleSettableValue<Integer> obs2 = new SimpleSettableValue<>(Integer.class, true);
		SimpleSettableValue<Integer> obs3 = new SimpleSettableValue<>(Integer.class, true);
		ObservableSet<ObservableValue<Integer>> set = ObservableCollection.create(new TypeToken<ObservableValue<Integer>>() {}).flow()
			.unique().collect();
		set.add(obs1);
		set.add(obs2);
		Observable<Integer> folded = ObservableCollection
			.fold(set.flow().map(new TypeToken<Observable<Integer>>() {}, value -> value.value(), options -> options.cache(false))
				.collectPassive());
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

	/** Tests {@link CollectionDataFlow#map(TypeToken, Function)} */
	@Test
	public void observableListMap() {
		ObservableCollection<Integer> list = ObservableCollection.create(intType);
		ObservableCollectionTester<Integer> lwTester = new ObservableCollectionTester<>(
			list.flow().map(intType, value -> value * 10, options -> options.cache(false)).collectPassive());
		ObservableCollectionTester<Integer> hwTester = new ObservableCollectionTester<>(
			list.flow().map(intType, value -> value * 10).collect());

		for(int i = 0; i < 30; i++) {
			list.add(i);
			lwTester.add(i * 10);
			hwTester.add(i * 10);
			lwTester.check();
			hwTester.check();
		}
		for(int i = 0; i < 30; i++) {
			list.add(i);
			lwTester.add(i * 10);
			hwTester.add(i * 10);
			lwTester.check();
			hwTester.check();
		}
		for(int i = 0; i < 30; i++) {
			list.remove(Integer.valueOf(i));
			lwTester.remove(Integer.valueOf(i * 10));
			hwTester.remove(Integer.valueOf(i * 10));
			lwTester.check();
			hwTester.check();
		}
	}

	/** Tests {@link CollectionDataFlow#filter(Function)} */
	@Test
	public void observableListFilter() {
		ObservableCollection<Integer> list = ObservableCollection.create(intType);
		ObservableCollectionTester<Integer> tester = new ObservableCollectionTester<>(//
			list.flow().filter(value -> value != null && value % 2 == 0 ? null : StdMsg.ILLEGAL_ELEMENT).collect());

		for(int i = 0; i < 30; i++) {
			list.add(i);
			if(i % 2 == 0)
				tester.add(i);
			tester.check(i % 2 == 0 ? 1 : 0);
		}
		for(int i = 0; i < 30; i++) {
			list.add(i);
			if(i % 2 == 0)
				tester.add(i);
			tester.check(i % 2 == 0 ? 1 : 0);
		}
		for(int i = 0; i < 30; i++) {
			list.remove(Integer.valueOf(i));
			if(i % 2 == 0)
				tester.remove(i);
			tester.check(i % 2 == 0 ? 1 : 0);
		}
	}

	/** Tests {@link CollectionDataFlow#filterStatic(Function)} */
	@Test
	public void observableListFilterStatic() {
		ObservableCollection<Integer> list = ObservableCollection.create(intType);
		ObservableCollectionTester<Integer> tester = new ObservableCollectionTester<>(//
			list.flow().filterStatic(value -> value != null && value % 2 == 0 ? null : StdMsg.ILLEGAL_ELEMENT).collect());

		for (int i = 0; i < 30; i++) {
			list.add(i);
			if (i % 2 == 0)
				tester.add(i);
			tester.check(i % 2 == 0 ? 1 : 0);
		}
		for (int i = 0; i < 30; i++) {
			list.add(i);
			if (i % 2 == 0)
				tester.add(i);
			tester.check(i % 2 == 0 ? 1 : 0);
		}
		for (int i = 0; i < 30; i++) {
			list.remove(Integer.valueOf(i));
			if (i % 2 == 0)
				tester.remove(i);
			tester.check(i % 2 == 0 ? 1 : 0);
		}
	}

	/** Slight variation on {@link #observableListFilter()} */
	@Test
	public void observableListFilter2() {
		ObservableCollection<Integer> list = ObservableCollection.create(intType);

		ObservableCollectionTester<Integer> tester0 = new ObservableCollectionTester<>(//
			list.flow().filter(value -> value % 3 == 0 ? null : StdMsg.ILLEGAL_ELEMENT).collect());
		ObservableCollectionTester<Integer> tester1 = new ObservableCollectionTester<>(//
			list.flow().filter(value -> value % 3 == 1 ? null : StdMsg.ILLEGAL_ELEMENT).collect());
		ObservableCollectionTester<Integer> tester2 = new ObservableCollectionTester<>(//
			list.flow().filter(value -> value % 3 == 2 ? null : StdMsg.ILLEGAL_ELEMENT).collect());

		int count = 30;
		for(int i = 0; i < count; i++) {
			list.add(i);
			switch (i % 3) {
			case 0:
				tester0.add(i);
				break;
			case 1:
				tester1.add(i);
				break;
			case 2:
				tester2.add(i);
				break;
			}
			tester0.check(i % 3 == 0 ? 1 : 0);
			tester1.check(i % 3 == 1 ? 1 : 0);
			tester2.check(i % 3 == 2 ? 1 : 0);
		}
		for(int i = 0; i < count; i++) {
			int value = i + 1;
			list.set(i, value);
			switch (i % 3) {
			case 0:
				tester0.remove(Integer.valueOf(i));
				break;
			case 1:
				tester1.remove(Integer.valueOf(i));
				break;
			case 2:
				tester2.remove(Integer.valueOf(i));
				break;
			}
			switch (value % 3) {
			case 0:
				tester0.add(i / 3, value);
				break;
			case 1:
				tester1.add(i / 3, value);
				break;
			case 2:
				tester2.add(i / 3, value);
				break;
			}
			tester0.check();
			tester1.check();
			tester2.check();
		}
		for(int i = count - 1; i >= 0; i--) {
			int value = list.get(i);
			list.remove(Integer.valueOf(value));
			switch (value % 3) {
			case 0:
				tester0.remove(Integer.valueOf(value));
				break;
			case 1:
				tester1.remove(Integer.valueOf(value));
				break;
			case 2:
				tester2.remove(Integer.valueOf(value));
				break;
			}
			tester0.check(i % 3 == 0 ? 1 : 0);
			tester1.check(i % 3 == 1 ? 1 : 0);
			tester2.check(i % 3 == 2 ? 1 : 0);
		}
	}

	/** Tests {@link CollectionDataFlow#filter(Function)} and {@link CollectionDataFlow#map(TypeToken, Function)} together */
	@Test
	public void observableListFilterMap() {
		ObservableCollection<Integer> list = ObservableCollection.create(intType);
		ObservableCollectionTester<Integer> tester = new ObservableCollectionTester<>(list.flow()//
			.filter(value -> (value == null || value % 2 != 0) ? StdMsg.ILLEGAL_ELEMENT : null)//
			.map(intType, value -> value / 2)//
			.collect());

		for(int i = 0; i < 30; i++) {
			list.add(i);
			if(i % 2 == 0)
				tester.add(i / 2);
			tester.check(i % 2 == 0 ? 1 : 0);
		}
		for(int i = 0; i < 30; i++) {
			list.add(i);
			if(i % 2 == 0)
				tester.add(i / 2);
			tester.check(i % 2 == 0 ? 1 : 0);
		}
		for(int i = 0; i < 30; i++) {
			list.remove(Integer.valueOf(i));
			if(i % 2 == 0)
				tester.remove(Integer.valueOf(i / 2));
			tester.check(i % 2 == 0 ? 1 : 0);
		}
	}

	/** Tests {@link CollectionDataFlow#combine(TypeToken, Function)} */
	@Test
	public void observableListCombine() {
		ObservableCollection<Integer> list = ObservableCollection.create(intType);
		SimpleSettableValue<Integer> value1 = new SimpleSettableValue<>(Integer.TYPE, false);
		value1.set(1, null);
		ObservableCollectionTester<Integer> tester = new ObservableCollectionTester<>(list.flow()//
			.combine(intType, combine -> {
				return combine.with(value1).build((v1, v2) -> v1 * v2);
			}).filter(value -> value != null && value % 3 == 0 ? null : StdMsg.ILLEGAL_ELEMENT)//
			.collect());

		for(int i = 0; i < 30; i++) {
			list.add(i);
			int value = i * value1.get();
			if(value % 3 == 0)
				tester.add(value);
			tester.check(i % 3 == 0 ? 1 : 0);
		}

		value1.set(3, null);
		tester.clear();
		for(int i = 0; i < 30; i++) {
			int value = i * value1.get();
			if(value % 3 == 0)
				tester.add(value);
		}
		tester.check();

		value1.set(10, null);
		tester.clear();
		for(int i = 0; i < 30; i++) {
			int value = i * value1.get();
			if(value % 3 == 0)
				tester.add(value);
		}
		tester.check();

		for(int i = 0; i < 30; i++) {
			list.remove(Integer.valueOf(i));
			int value = i * value1.get();
			if(value % 3 == 0)
				tester.remove(Integer.valueOf(value));
			tester.check(value % 3 == 0 ? 0 : 0);
		}
	}

	/** Tests {@link CollectionDataFlow#flatMapC(TypeToken, Function)} */
	@Test
	public void observableListFlatten() {
		ObservableCollection<Integer> set1 = ObservableCollection.create(intType);
		ObservableCollection<Integer> set2 = ObservableCollection.create(intType);
		ObservableCollection<Integer> set3 = ObservableCollection.create(intType);
		ObservableCollection<ObservableCollection<Integer>> outer = ObservableCollection
			.create(new TypeToken<ObservableCollection<Integer>>() {});
		outer.add(set1);
		outer.add(set2);
		ObservableCollection<Integer> flat = outer.flow().flatMapC(intType, s -> s).collect();
		ObservableCollectionTester<Integer> tester = new ObservableCollectionTester<>(flat);
		ObservableCollectionTester<Integer> filteredTester = new ObservableCollectionTester<>(//
			flat.flow().filter(value -> value != null && value % 3 == 0 ? null : StdMsg.ILLEGAL_ELEMENT).collect());

		List<Integer> correct1 = new ArrayList<>();
		List<Integer> correct2 = new ArrayList<>();
		List<Integer> correct3 = new ArrayList<>();

		for(int i = 0; i < 30; i++) {
			set1.add(i);
			set2.add(i * 10);
			set3.add(i * 100);
			correct1.add(i);
			correct2.add(i * 10);
			correct3.add(i * 100);
			tester.clear();
			tester.addAll(correct1);
			tester.addAll(correct2);
			filteredTester.clear();
			for(int j : correct1)
				if(j % 3 == 0)
					filteredTester.add(j);
			for(int j : correct2)
				if(j % 3 == 0)
					filteredTester.add(j);
			tester.check();
			filteredTester.check();
		}

		outer.add(set3);
		tester.clear();
		tester.addAll(correct1);
		tester.addAll(correct2);
		tester.addAll(correct3);
		filteredTester.clear();
		for(int j : correct1)
			if(j % 3 == 0)
				filteredTester.add(j);
		for(int j : correct2)
			if(j % 3 == 0)
				filteredTester.add(j);
		for(int j : correct3)
			if(j % 3 == 0)
				filteredTester.add(j);

		tester.check();
		filteredTester.check();

		outer.remove(set2);
		tester.clear();
		tester.addAll(correct1);
		tester.addAll(correct3);
		filteredTester.clear();
		for(int j : correct1)
			if(j % 3 == 0)
				filteredTester.add(j);
		for(int j : correct3)
			if(j % 3 == 0)
				filteredTester.add(j);

		tester.check();
		filteredTester.check();

		for(int i = 0; i < 30; i++) {
			set1.remove((Integer) i);
			set2.remove((Integer) (i * 10));
			set3.remove((Integer) (i * 100));
			correct1.remove((Integer) i);
			correct2.remove((Integer) (i * 10));
			correct3.remove((Integer) (i * 100));
			tester.clear();
			tester.addAll(correct1);
			tester.addAll(correct3);
			filteredTester.clear();
			for(int j : correct1)
				if(j % 3 == 0)
					filteredTester.add(j);
			for(int j : correct3)
				if(j % 3 == 0)
					filteredTester.add(j);

			tester.check();
			filteredTester.check();
		}
	}

	/** Tests {@link ObservableCollection#observeFind(Predicate, Supplier, boolean)} */
	@Test
	public void observableListFind() {
		ObservableCollection<Integer> list = ObservableCollection.create(intType);
		ObservableValue<Integer> found = list.observeFind(value -> value % 3 == 0, () -> null, false);
		Integer [] received = new Integer[] {0};
		found.act(value -> received[0] = value.getNewValue());
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

	/** Tests {@link CollectionDataFlow#flatMapV(TypeToken, Function)} */
	@Test
	public void flattenListValues() {
		ObservableCollection<ObservableValue<Integer>> list = ObservableCollection.create(new TypeToken<ObservableValue<Integer>>() {});
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
		list.flow().flatMapV(intType, v -> v).collect().observeFind(value -> value % 3 == 0, () -> null, false).value()
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

	/** Tests {@link CollectionDataFlow#sorted(java.util.Comparator)} */
	@Test
	public void sortedObservableList() {
		ObservableCollection<Integer> list = ObservableCollection.create(intType);

		ObservableCollectionTester<Integer> tester = new ObservableCollectionTester<>(//
			list.flow().sorted(Integer::compareTo).collect());

		for(int i = 30; i >= 0; i--) {
			list.add(i);
			tester.add(i);
		}

		java.util.Collections.sort(tester.getExpected());
		tester.check();

		for (int i = 0; i < list.size(); i++) {
			list.set(i, list.get(i) - 50);
			tester.clear();
			tester.addAll(list);
			java.util.Collections.sort(tester.getExpected());
			tester.check();
		}

		for (int i = -20; i >= -50; i--) {
			list.remove((Integer) i);
			tester.remove(Integer.valueOf(i));

			tester.check();
		}
	}

	/** Tests {@link CollectionDataFlow#flatMapC(TypeToken, Function)} and {@link CollectionDataFlow#sorted(Comparator)} */
	@Test
	public void observableOrderedFlatten() {
		observableOrderedFlatten(null);
		observableOrderedFlatten(Comparable::compareTo);
	}

	private void observableOrderedFlatten(java.util.Comparator<Integer> comparator) {
		ObservableCollection<ObservableCollection<Integer>> outer = ObservableCollection
			.create(new TypeToken<ObservableCollection<Integer>>() {});
		ObservableCollection<Integer> list1 = ObservableCollection.create(intType);
		ObservableCollection<Integer> list2 = ObservableCollection.create(intType);
		ObservableCollection<Integer> list3 = ObservableCollection.create(intType);

		outer.add(list1);
		outer.add(list2);

		ArrayList<Integer> correct1 = new ArrayList<>();
		ArrayList<Integer> correct2 = new ArrayList<>();
		ArrayList<Integer> correct3 = new ArrayList<>();

		// Add data before the subscription because subscribing to non-empty, indexed, flattened collections is complicated
		// This comment is old. Not sure it's that complicated anymore
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

		CollectionDataFlow<?, ?, Integer> flow = outer.flow().flatMapC(intType, c -> c);
		if (comparator != null)
			flow = flow.sorted(comparator);
		SimpleObservable<Void> unsub = new SimpleObservable<>();
		ObservableCollection<Integer> flat = flow.collect(unsub);
		ObservableCollectionTester<Integer> tester = new ObservableCollectionTester<>(flat);
		tester.set(join(comparator, correct1, correct2)).check();

		outer.add(list3);
		tester.set(join(comparator, correct1, correct2, correct3)).check();

		outer.remove(list2);
		tester.set(join(comparator, correct1, correct3)).check();

		list1.remove((Integer) 16);
		correct1.remove((Integer) 16);
		tester.set(join(comparator, correct1, correct3)).check();
		list1.add(list1.indexOf(19), 16);
		correct1.add(correct1.indexOf(19), 16);
		tester.set(join(comparator, correct1, correct3)).check();

		unsub.onNext(null);
	}

	private static <T> List<T> join(Comparator<? super T> comparator, List<T>... correct) {
		ArrayList<T> ret = new ArrayList<>();
		for(List<T> c : correct)
			ret.addAll(c);
		if(comparator != null)
			java.util.Collections.sort(ret, comparator);
		return ret;
	}

	/** Tests {@link CollectionDataFlow#refreshEach(Function)} */
	@Test
	public void testRefreshEach() {
		ObservableCollection<int[]> list = ObservableCollection.create(new TypeToken<int[]>() {});
		Map<int[], SimpleObservable<Void>> elObservables = new LinkedHashMap<>();
		for(int i = 0; i < 30; i++) {
			int [] el = new int[] {i};
			SimpleObservable<Void> elObs = new SimpleObservable<>();
			elObservables.put(el, elObs);
			list.add(el);
		}
		ObservableCollection<Integer> values = list.flow().refreshEach(el -> elObservables.get(el)).map(intType, el -> el[0]).collect();
		ObservableCollectionTester<Integer> tester = new ObservableCollectionTester<>(values);
		tester.check();

		for(int i = 0; i < list.size(); i++) {
			list.get(i)[0]++;
			tester.getExpected().set(i, list.get(i)[0]);
			elObservables.get(list.get(i)).onNext(null);
			tester.check();
		}

		for(int i = 0; i < list.size(); i++) {
			list.get(i)[0] *= 50;
			tester.getExpected().set(i, list.get(i)[0]);
			elObservables.get(list.get(i)).onNext(null);
			tester.check();
		}

		for(int i = 0; i < list.size(); i++) {
			list.get(i)[0]--;
			tester.getExpected().set(i, list.get(i)[0]);
			elObservables.get(list.get(i)).onNext(null);
			tester.check();
		}

		for(int i = 0; i < list.size(); i++) {
			list.get(i)[0] = 0;
			tester.getExpected().set(i, list.get(i)[0]);
			elObservables.get(list.get(i)).onNext(null);
			tester.check();
		}

		list.clear();
		for (SimpleObservable<Void> controller : elObservables.values())
			controller.onNext(null);
		tester.checkOps(0);

		for (int[] value : elObservables.keySet())
			list.add(value);

		tester.setSynced(false);
		for (int i = 0; i < list.size(); i++)
			elObservables.get(list.get(i)).onNext(null);
		tester.checkOps(0);
	}

	/** Tests basic transaction functionality on observable collections */
	@Test
	public void testTransactionsBasic() {
		// Use find() and changes() to test
		ObservableCollection<Integer> list = ObservableCollection.create(intType);
		testTransactionsByFind(list, list);
		testTransactionsByChanges(list, list);
	}

	/** Tests transactions in {@link CollectionDataFlow#flatMapC(TypeToken, Function) flattened} collections */
	@Test
	public void testTransactionsFlattened() {
		ObservableCollection<Integer> list1 = ObservableCollection.create(intType);
		ObservableCollection<Integer> list2 = ObservableCollection.create(intType);
		TypeToken<ObservableCollection<Integer>> outerType = new TypeToken<ObservableCollection<Integer>>() {};
		ObservableCollection<Integer> flat = ObservableCollection.flatten(ObservableCollection.create(outerType, list1, list2)).collect();
		list1.add(50);

		testTransactionsByFind(flat, list2);
		testTransactionsByChanges(flat, list2);
	}

	private void testTransactionsByFind(ObservableCollection<Integer> observable, TransactableList<Integer> controller) {
		Integer [] found = new Integer[1];
		int [] findCount = new int[1];
		Subscription sub = observable.observeFind(value -> value % 5 == 4, () -> null, false).act(event -> {
			findCount[0]++;
			found[0] = event.getNewValue();
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

	private void testTransactionsByChanges(ObservableCollection<Integer> observable, TransactableList<Integer> controller) {
		ArrayList<Integer> compare = new ArrayList<>(observable);
		ArrayList<Integer> correct = new ArrayList<>(observable);
		int [] changeCount = new int[1];
		Subscription sub = observable.changes().act(event -> {
			changeCount[0]++;
			for(CollectionChangeEvent.ElementChange<Integer> change : event.elements){
				switch (event.type) {
				case add:
					compare.add(change.index, change.newValue);
					break;
				case remove:
					assertEquals(compare.remove(change.index), change.newValue);
					break;
				case set:
					assertEquals(compare.set(change.index, change.newValue), change.oldValue);
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

	/** Tests transactions caused by {@link CollectionDataFlow#refresh(Observable) refreshing} on an observable */
	@Test
	public void testTransactionsRefresh() {
		ObservableCollection<Integer> list = ObservableCollection.create(new TypeToken<Integer>() {});
		SimpleObservable<Void> refresh = new SimpleObservable<>();

		for(int i = 0; i < 30; i++)
			list.add(i);

		int [] changes = new int[1];
		Subscription sub = list.flow().refresh(refresh).collect().simpleChanges().act(v -> {
			changes[0]++;
		});
		int correctChanges = 0;

		assertEquals(correctChanges, changes[0]);

		refresh.onNext(null);
		correctChanges++;
		assertEquals(correctChanges, changes[0]);

		Transaction trans = list.lock(true, null);
		refresh.onNext(null);
		refresh.onNext(null);
		trans.close();
		correctChanges++;
		assertEquals(correctChanges, changes[0]);

		trans = list.lock(true, null);
		for(int i = 0; i < 30; i++)
			list.set(i, i + 1);
		refresh.onNext(null);
		trans.close();
		correctChanges++;
		assertEquals(correctChanges, changes[0]);

		list.clear();
		correctChanges++;
		assertEquals(correctChanges, changes[0]);
		refresh.onNext(null);
		assertEquals(correctChanges, changes[0]);

		list.addAll(java.util.Arrays.asList(0, 1, 2, 3, 4));
		correctChanges++;
		assertEquals(correctChanges, changes[0]);
		refresh.onNext(null);
		correctChanges++;
		assertEquals(correctChanges, changes[0]);

		int preChanges = changes[0];
		sub.unsubscribe();
		refresh.onNext(null);
		assertEquals(preChanges, changes[0]);
	}

	/** Tests transactions caused by {@link CollectionDataFlow#combine(TypeToken, Function) combining} a list with an observable value */
	@Test
	public void testTransactionsCombined() {
		ObservableCollection<Integer> list = ObservableCollection.create(new TypeToken<Integer>() {});
		SimpleSettableValue<Integer> mult = new SimpleSettableValue<>(new TypeToken<Integer>() {}, false);
		mult.set(1, null);
		ObservableCollection<Integer> product = list.flow().combine(intType, combine -> {
			return combine.with(mult).build((v1, v2) -> //
			v1 * v2);
		}).collect();

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
