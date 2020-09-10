package org.observe.collect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.qommons.QommonsTestUtils.collectionsEqual;
import static org.qommons.debug.Debug.d;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Test;
import org.observe.Observable;
import org.observe.ObservableTester;
import org.observe.ObservableValue;
import org.observe.ObservableValueTester;
import org.observe.SimpleObservable;
import org.observe.SimpleSettableValue;
import org.observe.Subscription;
import org.observe.assoc.ObservableMultiMap;
import org.observe.assoc.ObservableMultiMapEvent;
import org.observe.assoc.ObservableSortedMultiMap;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.qommons.Causable;
import org.qommons.LambdaUtils;
import org.qommons.QommonsTestUtils;
import org.qommons.TestHelper;
import org.qommons.TestHelper.Testable;
import org.qommons.Transaction;
import org.qommons.collect.BetterList;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.collect.TransactableList;
import org.qommons.debug.Debug;
import org.qommons.tree.BetterTreeList;
import org.qommons.tree.BetterTreeSet;

import com.google.common.reflect.TypeToken;

/** Tests observable collections and their default implementations */
public class ObservableCollectionsTest {
	// Tweaks for debugging the barrage tests
	private static boolean BARRAGE_USE_MAP = true;
	private static boolean BARRAGE_USE_FILTER = true;
	private static boolean BARRAGE_USE_COMBINE = true;
	private static boolean BARRAGE_USE_MULTI_MAP = true;

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
	 * @param helper The test helper to assist with debugging
	 */
	public static <T extends Collection<Integer>> void testCollection(T coll, Consumer<? super T> check, TestHelper helper) {
		testCollection(coll, check, 0, helper);
	}

	private static <T extends Collection<Integer>> void testCollection(T coll, Consumer<? super T> check, int depth, TestHelper helper) {
		QommonsTestUtils.testCollection(coll, check, v -> {
			if (v instanceof ObservableCollection)
				return (Consumer<? super T>) testingObservableCollection((ObservableCollection<Integer>) v,
					(Consumer<? super ObservableCollection<Integer>>) check, depth, helper);
			else
				return null;
		}, depth, helper);
	}

	private static <T extends ObservableCollection<Integer>> Checker<ObservableCollection<Integer>> testingObservableCollection(T coll,
		Consumer<? super T> check, int depth, TestHelper helper) {

		ObservableCollectionTester<Integer> tester = new ObservableCollectionTester<>("base", coll);

		// Quick test first
		coll.addAll(QommonsTestUtils.sequence(50, null, true));
		tester.checkSynced();
		if(check != null)
			check.accept(coll);
		coll.clear();
		tester.checkSynced();
		if(check != null)
			check.accept(coll);

		Function<Integer, Integer> mapFn = LambdaUtils.printableFn(v -> v + 1000, () -> "+1000");
		Function<Integer, Integer> reverseMapFn = LambdaUtils.printableFn(v -> v - 1000, () -> "-1000");
		ObservableCollection<Integer> mappedOL;
		ObservableCollectionTester<Integer> mappedTester;
		if (BARRAGE_USE_MAP) {
			mappedOL = coll.flow().transform(intType, options -> options.cache(false).map(mapFn).withReverse(reverseMapFn))
				.collectPassive();
			d().set("mapped@" + depth, mappedOL);
			mappedTester = new ObservableCollectionTester<>("mapped", mappedOL);
		} else {
			mappedOL = null;
			mappedTester = null;
		}

		Function<Integer, String> filterFn1 = LambdaUtils.printableFn(v -> v % 3 == 0 ? null : "no", () -> "multiple of 3 only");
		ObservableCollection<Integer> filteredOL1;
		ObservableCollectionTester<Integer> filterTester1;
		if (BARRAGE_USE_FILTER) {
			filteredOL1 = coll.flow().filter(filterFn1).collect();
			d().set("filtered@" + depth, filteredOL1);
			filterTester1 = new ObservableCollectionTester<>("filtered", filteredOL1);
		} else {
			filteredOL1 = null;
			filterTester1 = null;
		}

		Function<Integer, Integer> groupFn = LambdaUtils.printableFn(v -> v % 3, () -> "%3");
		ObservableMultiMap<Integer, Integer> grouped;
		Map<Integer, List<Integer>> groupedSynced;
		ObservableSortedMultiMap<Integer, Integer> groupedSorted;
		NavigableMap<Integer, List<Integer>> groupedSortedSynced;
		if (BARRAGE_USE_MULTI_MAP) {
			grouped = coll.flow().groupBy(intType, groupFn, null).gather();
			d().set("grouped@" + depth, grouped);
			groupedSynced = new LinkedHashMap<>();
			ObservableCollectionsTest.sync(grouped, groupedSynced, () -> new ArrayList<>());

			groupedSorted = coll.flow().groupBy(intType, groupFn, Integer::compareTo, null).gather();
			d().set("groupedSorted@" + depth, groupedSorted);
			groupedSortedSynced = new TreeMap<>();
			ObservableCollectionsTest.sync(groupedSorted, groupedSortedSynced, () -> new ArrayList<>());
		} else {
			grouped = null;
			groupedSynced = null;
			groupedSorted = null;
			groupedSortedSynced = null;
		}

		BiFunction<Integer, Integer, Integer> combineFn = LambdaUtils.printableBiFn((v1, v2) -> v1 + v2, "+", null);
		BiFunction<Integer, Integer, Integer> reverseCombineFn = LambdaUtils.printableBiFn((v1, v2) -> v1 - v2, "-", null);
		SimpleSettableValue<Integer> combineVar = new SimpleSettableValue<>(Integer.class, false);
		combineVar.set(10000, null);
		ObservableCollection<Integer> combinedOL;
		ObservableCollectionTester<Integer> combinedTester;
		if (BARRAGE_USE_COMBINE) {
			combinedOL = coll.flow().transform(intType, combine -> {
				return combine.combineWith(combineVar).build((s, cv) -> //
				combineFn.apply(s, cv.get(combineVar))).withReverse(reverseCombineFn);
			}).collect();
			d().set("combined@" + depth, combinedOL);
			combinedTester = new ObservableCollectionTester<>("combined", combinedOL);
		} else {
			combinedOL = null;
			combinedTester = null;
		}

		// TODO Test reversed observable collections

		BiFunction<Integer, Integer, Integer> maxFn = LambdaUtils.printableBiFn((v1, v2) -> v1 >= v2 ? v1 : v2, "max", null);
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

				if (mappedTester != null) {
					mappedTester.set(coll.stream().map(mapFn).collect(Collectors.toList()));
					mappedTester.check();
				}

				if (filterTester1 != null) {
					filterTester1.set(coll.stream().filter(v -> filterFn1.apply(v) == null).collect(Collectors.toList()));
					filterTester1.check();
				}

				if (grouped != null) {
					Set<Integer> groupKeySet = tester.getSyncedCopy().stream().map(groupFn).collect(Collectors.toSet());
					assertThat(grouped.keySet(), collectionsEqual(groupKeySet, false));
					assertThat(groupedSynced.keySet(), collectionsEqual(groupKeySet, false));
					for (Integer groupKey : groupKeySet) {
						List<Integer> values = tester.getSyncedCopy().stream().filter(v -> Objects.equals(groupFn.apply(v), groupKey))
							.collect(Collectors.toList());
						Debug.d().debugIf(!values.equals(grouped.get(groupKey)));
						assertThat(grouped.get(groupKey), collectionsEqual(values, true));
						assertThat(groupedSynced.get(groupKey), collectionsEqual(values, true));
					}

					Set<Integer> groupSortedKeySet = tester.getSyncedCopy().stream().map(groupFn).sorted().collect(Collectors.toSet());
					assertThat(groupedSorted.keySet(), collectionsEqual(groupSortedKeySet, false));
					assertThat(groupedSynced.keySet(), collectionsEqual(groupSortedKeySet, false));
					for (Integer groupKey : groupSortedKeySet) {
						List<Integer> values = tester.getSyncedCopy().stream().filter(v -> Objects.equals(groupFn.apply(v), groupKey))
							.collect(Collectors.toList());
						assertThat(groupedSorted.get(groupKey), collectionsEqual(values, true));
						assertThat(groupedSortedSynced.get(groupKey), collectionsEqual(values, true));
					}
				}

				if (combinedTester != null) {
					combinedTester.set(coll.stream().map(v -> v + combineVar.get()).collect(Collectors.toList()));
					combinedTester.check();
				}

				if(subSets != null) {
					for(int i = 0; i < subSets.size(); i++) {
						ObservableSortedSet<Integer> subSet = subSets.get(i);
						checkSubSet((ObservableSortedSet<Integer>) coll, subSet, subSetRanges.get(i));
						assertThat(syncedSubSets.get(i), collectionsEqual(subSet, true));
					}
				}

				Optional<Integer> actualSum = coll.stream().reduce((v1, v2) -> combineFn.apply(v1, v2));
				if(actualSum.isPresent()) {
					assertEquals(actualSum.get(), sum.get());
					assertEquals(actualSum.get(), observedSum[0]);
				} else {
					assertEquals(Integer.valueOf(0), sum.get());
					assertEquals(Integer.valueOf(0), observedSum[0]);
				}
				Optional<Integer> actualMax = coll.stream().reduce((v1, v2) -> maxFn.apply(v1, v2));
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
					if (mappedOL != null)
						testCollection(mappedOL, this, depth + 1, helper);
					if (combinedOL != null)
						testCollection(combinedOL, this, depth + 1, helper);
				}

				if (filteredOL1 != null) {
					// Test filter adding
					filteredOL1.add(0);
					assertEquals(1, filteredOL1.size());
					accept(coll);

					try {
						filteredOL1.add(1);
						assertTrue("Should have thrown an IllegalArgumentException", false);
					} catch (IllegalArgumentException e) {}
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
					} catch (IllegalArgumentException e) {}
					listIter.previous();
					listIter.remove();
					assertEquals(0, filteredOL1.size());
					accept(coll);
				}

				tester.setSynced(false);
				if (mappedTester != null)
					mappedTester.setSynced(false);
				if (filterTester1 != null)
					filterTester1.setSynced(false);
				if (combinedTester != null)
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
	public static <K, V, C extends List<V>> Subscription sync(ObservableMultiMap<K, V> map, Map<K, C> synced,
		Supplier<? extends C> collectCreator) {
		return map.subscribe(new Consumer<ObservableMultiMapEvent<? extends K, ? extends V>>() {
			@Override
			public void accept(ObservableMultiMapEvent<? extends K, ? extends V> evt) {
				switch(evt.getType()){
				case add:
					synced.computeIfAbsent(evt.getKey(), k -> collectCreator.get()).add(evt.getIndex(), evt.getNewValue());
					break;
				case remove:
					C values = synced.get(evt.getKey());
					Assert.assertNotNull(values);
					assertEquals(evt.getOldValue(), values.remove(evt.getIndex()));
					if (values.isEmpty())
						synced.remove(evt.getKey());
					break;
				case set:
					values = synced.get(evt.getKey());
					Assert.assertNotNull(values);
					V oldValue = values.set(evt.getIndex(), evt.getNewValue());
					if (!Objects.equals(evt.getOldValue(), oldValue))
						assertEquals(evt.getOldValue(), oldValue);
					break;
				}
			}
		}, true, true);
	}

	static class TreeListTester implements TestHelper.Testable {
		@Override
		public void accept(TestHelper helper) {
			BetterTreeList<Integer> backing = new BetterTreeList<>(false);
			testCollection(ObservableCollection.create(TypeToken.of(Integer.class), backing), set -> backing.checkValid(), helper);
		}
	}

	/** Runs a barrage of tests on a {@link DefaultObservableCollection} backed by a {@link BetterTreeList} */
	@Test
	public void testObservableTreeList() {
		TestHelper.createTester(TreeListTester.class).withDebug(false).withFailurePersistence(false).withRandomCases(1).execute()
		.throwErrorIfFailed();
	}

	static class TreeSetTester implements TestHelper.Testable {
		@Override
		public void accept(TestHelper helper) {
			BetterTreeSet<Integer> backing = new BetterTreeSet<>(false, Integer::compareTo);
			testCollection(ObservableCollection.create(TypeToken.of(Integer.class), backing), set -> backing.checkValid(), helper);
		}
	}

	/** Runs a barrage of tests on a {@link DefaultObservableCollection} backed by a {@link BetterTreeSet} */
	@Test
	public void testObservableTreeSet() {
		TestHelper.createTester(TreeSetTester.class).withDebug(false).withFailurePersistence(false).withRandomCases(1).execute()
		.throwErrorIfFailed();
	}

	// Random test generation

	interface CollectionAdjuster {
		boolean add(List<Integer> values, int index, Integer value);

		boolean remove(List<Integer> values, int index, Integer value);

		boolean set(List<Integer> values, int index, Integer oldValue, Integer newValue);

		void refresh(List<Integer> src, List<Integer> values);
	}

	static class SimpleRandomTester implements Testable {
		SimpleRandomTester() {
		}

		@Override
		public void accept(TestHelper helper) {
			ObservableCollection<Integer> source = ObservableCollection.create(intType);
			CollectionAdjuster refresh;
			CollectionDataFlow<Integer, ?, Integer> derivedFlow;
			int methods = 3;
			int modifier1, modifier2;
			int range = 1000;
			switch (helper.getInt(0, methods)) {
			case 0:
				Function<Integer, Integer> map;
				boolean adjustable;
				switch (helper.getInt(0, 5)) {
				case 0:
					modifier1 = helper.getInt(0, 100);
					map = v -> v + modifier1;
					adjustable = true;
					break;
				case 1:
					modifier1 = helper.getInt(0, 100);
					map = v -> v - modifier1;
					adjustable = true;
					break;
				case 2:
					modifier1 = helper.getInt(0, 100);
					map = v -> v * modifier1;
					adjustable = modifier1 > 0;
					break;
				case 3:
					modifier1 = helper.getInt(1, 10);
					map = v -> v / modifier1;
					adjustable = false;
					break;
				default:
					modifier1 = helper.getInt(1, 10);
					map = v -> v % modifier1;
					adjustable = false;
					break;
				}
				derivedFlow = source.flow().transform(intType,
					tx -> tx//
					.cache(helper.getBoolean())//
					.fireIfUnchanged(helper.getBoolean())//
					.reEvalOnUpdate(helper.getBoolean()).map(map)//
					);
				refresh = new CollectionAdjuster() {
					@Override
					public boolean add(List<Integer> values, int index, Integer value) {
						if (index >= 0)
							values.add(index, map.apply(value));
						else
							values.add(map.apply(value));
						return true;
					}

					@Override
					public boolean remove(List<Integer> values, int index, Integer value) {
						if (index >= 0)
							assertEquals(map.apply(value), values.remove(index));
						else if (adjustable)
							values.remove(map.apply(value));
						else
							return false;
						return true;
					}

					@Override
					public boolean set(List<Integer> values, int index, Integer oldValue, Integer newValue) {
						assertEquals(map.apply(oldValue), values.set(index, map.apply(newValue)));
						return true;
					}

					@Override
					public void refresh(List<Integer> src, List<Integer> values) {
						src.stream().map(map).forEach(v -> values.add(v));
					}
				};
				break;
			case 1:
				Predicate<Integer> filter;
				switch (helper.getInt(0, 3)) {
				case 0:
					modifier1 = helper.getInt(0, range);
					filter = v -> v < modifier1;
					break;
				case 1:
					modifier1 = helper.getInt(0, range);
					filter = v -> v > modifier1;
					break;
				default:
					modifier1 = helper.getInt(2, 10);
					modifier2 = helper.getInt(0, modifier1);
					filter = v -> (v % modifier1) == modifier2;
					break;
				}
				derivedFlow = source.flow().filter(v -> filter.test(v) ? null : StdMsg.ILLEGAL_ELEMENT);
				refresh = new CollectionAdjuster() {
					@Override
					public boolean add(List<Integer> values, int index, Integer value) {
						if (index >= 0)
							return false;
						if (!filter.test(value))
							return true;
						values.add(value);
						return true;
					}

					@Override
					public boolean remove(List<Integer> values, int index, Integer value) {
						if (!filter.test(value))
							return true;
						if (index >= 0)
							return false;
						values.remove(value);
						return true;
					}

					@Override
					public boolean set(List<Integer> values, int index, Integer oldValue, Integer newValue) {
						return !filter.test(oldValue) && !filter.test(newValue);
					}

					@Override
					public void refresh(List<Integer> src, List<Integer> values) {
						src.stream().filter(filter).forEach(values::add);
					}
				};
				break;
			default:
				boolean forwardSort = helper.getBoolean();
				Comparator<Integer> compare = forwardSort ? Integer::compareTo : (i1, i2) -> -Integer.compare(i1, i2);
				derivedFlow = source.flow().sorted(compare);
				refresh = new CollectionAdjuster() {
					@Override
					public boolean add(List<Integer> values, int index, Integer value) {
						index = Collections.binarySearch(values, value, compare);
						if (index < 0)
							index = -index - 1;
						values.add(index, value);
						return true;
					}

					@Override
					public boolean remove(List<Integer> values, int index, Integer value) {
						index = Collections.binarySearch(values, value, compare);
						if (index < 0)
							return true;
						values.remove(index);
						return true;
					}

					@Override
					public boolean set(List<Integer> values, int index, Integer oldValue, Integer newValue) {
						values.remove(oldValue);
						index = Collections.binarySearch(values, newValue, compare);
						if (index < 0)
							index = -index - 1;
						values.add(index, newValue);
						return true;
					}

					@Override
					public void refresh(List<Integer> src, List<Integer> values) {}
				};
				break;
			}
			ObservableCollection<Integer> derived;
			if (derivedFlow.supportsPassive() && helper.getBoolean())
				derived = derivedFlow.collectPassive();
			else
				derived = derivedFlow.collectActive(Observable.empty);
			ObservableCollectionTester<Integer> tester;
			BetterList<Integer> expected = new BetterTreeList<>(false);
			if (helper.getBoolean()) {
				derived = derived.reverse();
				tester = new ObservableCollectionTester<>("reversed", derived, expected.reverse());
			} else
				tester = new ObservableCollectionTester<>("base", derived, expected);

			for (int i = 0; i < 500; i++) {
				int index;
				Integer value;
				boolean adjusted;
				helper.placemark();
				switch (helper.getInt(0, 10)) {
				case 0:
				case 1:
				case 2:
				case 3:
				case 4: // More adds than other ops
					value = helper.getInt(0, range);
					source.add(value);
					adjusted = refresh.add(expected, -1, value);
					break;
				case 5:
					index = helper.getInt(0, source.size() + 1);
					value = helper.getInt(0, range);
					source.add(index, value);
					adjusted = refresh.add(expected, index, value);
					break;
				case 6:
				case 7: // Set
					value = helper.getInt(0, range);
					if (source.isEmpty()) {
						source.add(value);
						adjusted = refresh.add(expected, -1, value);
					} else {
						index = helper.getInt(0, source.size());
						Integer oldValue = source.set(index, value);
						adjusted = refresh.set(expected, index, oldValue, value);
					}
					break;
				case 8: // Remove by value
					value = helper.getInt(0, range);
					if (source.isEmpty()) {
						source.add(value);
						adjusted = refresh.add(expected, -1, value);
					} else {
						source.remove(value);
						adjusted = refresh.remove(expected, -1, value);
					}
					break;
				case 9: // Remove by index
					if (source.isEmpty()) {
						value = helper.getInt(0, range);
						source.add(value);
						adjusted = refresh.add(expected, -1, value);
					} else {
						index = helper.getInt(0, source.size());
						value = source.remove(index);
						adjusted = refresh.remove(expected, index, value);
					}
					break;
				default:
					throw new IllegalStateException("Bad int");
				}
				if (!adjusted) {
					expected.clear();
					refresh.refresh(source, expected);
				}
				tester.check();
			}
		}
	}

	/** Tests some simple {@link ObservableCollection} functionality in a random way */
	@Test
	public void randomSimple() {
		TestHelper.createTester(SimpleRandomTester.class).withMaxTotalDuration(Duration.ofSeconds(5))//
		/**/.withPersistenceDir(new File("src/main/test/org/observe/collect"), false)//
		.execute().throwErrorIfFailed();
	}

	// Older, more specific tests

	/** Tests basic {@link ObservableCollection} functionality */
	@Test
	public void observableCollection() {
		ObservableCollection<Integer> list = ObservableCollection.create(intType);
		ObservableCollectionTester<Integer> tester = new ObservableCollectionTester<>("", list);

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
		ObservableSet<Integer> set = ObservableCollection.create(intType).flow().distinct().collect();
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
		list1.with(0, 1, 2, 4, 5, 6, 7, 8, 8);
		list2.with(0, 2, 4, 6, 8, 10, 10);
		ObservableValue<Boolean> containsAll = list1.observeContainsAll(list2);
		ObservableValueTester<Boolean> tester = new ObservableValueTester<>(containsAll);
		tester.check(false, 1);
		list1.add(10);
		tester.check(true, 1);
		list1.remove(Integer.valueOf(10));
		tester.check(false, 1);
		list2.remove(Integer.valueOf(10));
		tester.check(false, 0);
		list2.remove(Integer.valueOf(10));
		tester.check(true, 1);
		list2.add(10);
		tester.check(false, 1);
		list2.remove(Integer.valueOf(10));
		tester.check(true, 1);
		list1.remove(Integer.valueOf(8));
		tester.check(true, 0);
		list1.remove(Integer.valueOf(8));
		tester.check(false, 1);
		try (Transaction t = list1.lock(true, null)) {
			list1.add(8);
			list1.remove(Integer.valueOf(8));
			list1.add(8);
			list1.remove(Integer.valueOf(8));
		}
		tester.check(false, 0);
		try (Transaction t = list1.lock(true, null)) {
			list1.add(8);
			list1.remove(Integer.valueOf(8));
			list1.add(8);
		}
		tester.check(true, 1);

		tester.setSynced(false);
		list1.remove(Integer.valueOf(8));
		tester.checkValue(true);
		tester.checkOps(0);
	}

	/** Tests {@link CollectionDataFlow#map(TypeToken, Function)} */
	@Test
	public void observableSetMap() {
		ObservableSet<Integer> set = ObservableCollection.create(intType).flow().distinct().collect();
		Set<Integer> compare1 = new TreeSet<>();
		Set<Integer> correct = new TreeSet<>();
		set.flow().transform(intType, tx -> tx.cache(false).map(value -> value * 10)).collectPassive().subscribe(evt -> {
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
		ObservableSet<Integer> set = ObservableCollection.create(intType).flow().distinct().collect();
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

	/** Tests {@link CollectionDataFlow#filter(Function)} and {@link CollectionDataFlow#map(TypeToken, Function)} together */
	@Test
	public void observableSetFilterMap() {
		ObservableSet<Integer> set = ObservableCollection.create(intType).flow().distinct().collect();
		Set<Integer> compare1 = new TreeSet<>();
		Set<Integer> correct = new TreeSet<>();
		set.flow()//
		.filter(value -> (value == null || value % 2 != 0) ? StdMsg.ILLEGAL_ELEMENT : null)//
		.transform(intType, tx -> tx.map(value -> value / 2))//
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
		ObservableSet<Integer> set = ObservableCollection.create(intType).flow().distinct().collect();
		SimpleSettableValue<Integer> value1 = new SimpleSettableValue<>(Integer.TYPE, false);
		value1.set(1, null);
		List<Integer> compare1 = new ArrayList<>();
		Set<Integer> correct = new TreeSet<>();
		set.flow()//
		.transform(intType, combine -> {
			return combine.combineWith(value1).build((s, cv) -> s * cv.get(value1));
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

	/** Tests {@link CollectionDataFlow#distinct()} */
	@Test
	public void observableSetUnique() {
		ObservableCollection<Integer> list = ObservableCollection.create(intType);
		ObservableSet<Integer> unique = list.flow().distinct().collect();
		testUnique(list, unique);
	}

	/** Tests {@link CollectionDataFlow#distinctSorted(Comparator, boolean)} */
	@Test
	public void observableSortedSetUnique() {
		ObservableCollection<Integer> list = ObservableCollection.create(intType);
		ObservableSortedSet<Integer> unique = list.flow().distinctSorted(Integer::compareTo, false).collect();
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

	/** Tests {@link CollectionDataFlow#flatMap(TypeToken, Function)} */
	@Test
	public void observableCollectionFlatten() {
		ObservableSet<Integer> set1 = ObservableCollection.create(intType).flow().distinct().collect();
		ObservableSet<Integer> set2 = ObservableCollection.create(intType).flow().distinct().collect();
		ObservableSet<Integer> set3 = ObservableCollection.create(intType).flow().distinct().collect();
		ObservableCollection<ObservableSet<Integer>> outer = ObservableCollection.create(new TypeToken<ObservableSet<Integer>>() {});
		outer.add(set1);
		outer.add(set2);
		CollectionDataFlow<?, ?, Integer> flat = outer.flow().flatMap(intType, s -> s.flow());
		ObservableCollectionTester<Integer> tester = new ObservableCollectionTester<>("flat", flat.collect());
		ObservableCollectionTester<Integer> filterTester = new ObservableCollectionTester<>("filter", //
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
			.distinct().collect();
		set.add(obs1);
		set.add(obs2);
		Observable<Integer> folded = ObservableCollection
			.fold(set.flow().transform(new TypeToken<Observable<Integer>>() {}, tx -> tx.cache(false).map(value -> value.value()))
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
		ObservableCollectionTester<Integer> lwTester = new ObservableCollectionTester<>("light",
			list.flow().transform(intType, tx -> tx.cache(false).map(value -> value * 10)).collectPassive());
		ObservableCollectionTester<Integer> hwTester = new ObservableCollectionTester<>("heavy",
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
		ObservableCollectionTester<Integer> tester = new ObservableCollectionTester<>("filtered", //
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

	/** Slight variation on {@link #observableListFilter()} */
	@Test
	public void observableListFilter2() {
		ObservableCollection<Integer> list = ObservableCollection.create(intType);

		ObservableCollectionTester<Integer> tester0 = new ObservableCollectionTester<>("%3==0", //
			list.flow().filter(value -> value % 3 == 0 ? null : StdMsg.ILLEGAL_ELEMENT).collect());
		ObservableCollectionTester<Integer> tester1 = new ObservableCollectionTester<>("%3==1", //
			list.flow().filter(value -> value % 3 == 1 ? null : StdMsg.ILLEGAL_ELEMENT).collect());
		ObservableCollectionTester<Integer> tester2 = new ObservableCollectionTester<>("%3==2", //
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
			tester0.check(value % 3 == 0 ? 1 : 0);
			tester1.check(value % 3 == 1 ? 1 : 0);
			tester2.check(value % 3 == 2 ? 1 : 0);
		}
	}

	/** Tests {@link CollectionDataFlow#filter(Function)} and {@link CollectionDataFlow#map(TypeToken, Function)} together */
	@Test
	public void observableListFilterMap() {
		ObservableCollection<Integer> list = ObservableCollection.create(intType);
		ObservableCollectionTester<Integer> tester = new ObservableCollectionTester<>("filterMap",
			list.flow()//
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
		ObservableCollectionTester<Integer> tester = new ObservableCollectionTester<>("combined", list.flow()//
			.transform(intType, combine -> {
				return combine.combineWith(value1).build((s, cv) -> s * cv.get(value1));
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

	/** Tests {@link CollectionDataFlow#flatMap(TypeToken, Function)} */
	@Test
	public void observableListFlatten() {
		ObservableCollection<Integer> set1 = ObservableCollection.create(intType);
		ObservableCollection<Integer> set2 = ObservableCollection.create(intType);
		ObservableCollection<Integer> set3 = ObservableCollection.create(intType);
		ObservableCollection<ObservableCollection<Integer>> outer = ObservableCollection
			.create(new TypeToken<ObservableCollection<Integer>>() {});
		outer.add(set1);
		outer.add(set2);
		ObservableCollection<Integer> flat = outer.flow().flatMap(intType, s -> s.flow()).collect();
		ObservableCollectionTester<Integer> tester = new ObservableCollectionTester<>("flat", flat);
		ObservableCollectionTester<Integer> filteredTester = new ObservableCollectionTester<>("flatFiltered", //
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

	/** Tests {@link ObservableCollection#observeFind(Predicate)} */
	@Test
	public void observableListFind() {
		ObservableCollection<Integer> list = ObservableCollection.create(intType);
		ObservableValue<Integer> found = list.observeFind(value -> value % 3 == 0).first().find();
		ObservableValueTester<Integer> tester = new ObservableValueTester<>(found);
		Integer [] correct = new Integer[] {null};
		tester.check(correct[0], 1);

		for(int i = 1; i < 30; i++) {
			list.add(i);
			if (i % 3 == 0 && correct[0] == null) {
				correct[0] = i;
				tester.check(i, 1);
			} else
				tester.check(correct[0], 0);
		}
		for(int i = 1; i < 30; i++) {
			list.remove(Integer.valueOf(i));
			if(i % 3 == 0) {
				correct[0] += 3;
				if(correct[0] >= 30)
					correct[0] = null;
				tester.check(correct[0], 1);
			} else
				tester.check(correct[0], 0);
		}
	}

	/** Tests {@link CollectionDataFlow#flattenValues(TypeToken, Function)} */
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
		ObservableCollection<Integer> flattened = list.flow().flattenValues(intType, v -> v).collect();
		ObservableCollectionTester<Integer> tester = new ObservableCollectionTester<>("flattened", flattened);
		flattened.observeFind(value -> value % 3 == 0).anywhere().find().value().act(value -> received[0] = value);
		assertEquals(Integer.valueOf(3), received[0]);
		value3.set(4, null);
		tester.getExpected().set(2, 4);
		tester.check();
		assertEquals(null, received[0]);
		value4.set(6, null);
		tester.getExpected().set(3, 6);
		tester.check();
		assertEquals(Integer.valueOf(6), received[0]);
		list.remove(value4);
		tester.getExpected().remove(3);
		tester.check();
		assertEquals(null, received[0]);
		list.add(value5);
		tester.add(value5.get());
		tester.check();
		assertEquals(Integer.valueOf(9), received[0]);
		list.add(3, value4);
		tester.getExpected().add(3, value4.get());
		tester.check();

		// Test list modification
		flattened.set(4, 10);
		tester.getExpected().set(4, 10);
		assertEquals(Integer.valueOf(10), value5.get());
		tester.check();
		assertTrue(list.contains(value3));
		flattened.remove(2);
		tester.getExpected().remove(2);
		assertFalse(list.contains(value3));
		tester.check();
	}

	/** Tests {@link ObservableCollection#flattenValue(ObservableValue)} */
	@Test
	public void flattenListValue() {
		SimpleSettableValue<ObservableCollection<Integer>> listVal = new SimpleSettableValue<>(
			new TypeToken<ObservableCollection<Integer>>() {}, true);
		ObservableCollection<Integer> firstList = ObservableCollection.create(TypeToken.of(Integer.TYPE));
		ObservableCollection<Integer> secondList = ObservableCollection.create(TypeToken.of(Integer.TYPE));
		listVal.set(firstList, null);

		firstList.with(10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0);
		secondList.with(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

		ObservableCollectionTester<Integer> tester = new ObservableCollectionTester<>("flattened",
			ObservableCollection.flattenValue(listVal));
		tester.check(firstList);
		firstList.with(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
		tester.check(firstList);
		listVal.set(null, null);
		tester.check(ObservableCollection.of(TypeToken.of(Integer.TYPE)));
		listVal.set(firstList, null);
		tester.check(firstList);
		listVal.set(secondList, null);
		tester.check(secondList);
		try (Transaction t = firstList.lock(true, null)) {
			for (int i = firstList.size() - 1; i > 10; i--)
				firstList.remove(i);
		}
		tester.check(secondList, 0);
		listVal.set(firstList, null);
		tester.check(firstList);
		secondList.clear();
		tester.check(firstList, 0);
	}

	/** Tests {@link CollectionDataFlow#sorted(java.util.Comparator)} */
	@Test
	public void sortedObservableList() {
		ObservableCollection<Integer> list = ObservableCollection.create(intType);

		ObservableCollectionTester<Integer> tester = new ObservableCollectionTester<>("sorted", //
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

	/** Tests {@link CollectionDataFlow#flatMap(TypeToken, Function)} and {@link CollectionDataFlow#sorted(Comparator)} */
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

		CollectionDataFlow<?, ?, Integer> flow = outer.flow().flatMap(intType, c -> c.flow());
		if (comparator != null)
			flow = flow.sorted(comparator);
		SimpleObservable<Void> unsub = new SimpleObservable<>();
		ObservableCollection<Integer> flat = flow.collectActive(unsub);
		ObservableCollectionTester<Integer> tester = new ObservableCollectionTester<>("flat", flat);
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
		ObservableCollectionTester<Integer> tester = new ObservableCollectionTester<>("refreshed", values);
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

	/** Tests transactions in {@link CollectionDataFlow#flatMap(TypeToken, Function) flattened} collections */
	@Test
	public void testTransactionsFlattened() {
		ObservableCollection<Integer> list1 = ObservableCollection.create(intType);
		ObservableCollection<Integer> list2 = ObservableCollection.create(intType);
		TypeToken<ObservableCollection<Integer>> outerType = new TypeToken<ObservableCollection<Integer>>() {};
		ObservableCollection<Integer> flat = ObservableCollection.create(outerType).with(list1, list2).flow()
			.flatMap(intType, c -> c.flow()).collect();
		list1.add(50);

		testTransactionsByFind(flat, list2);
		testTransactionsByChanges(flat, list2);
	}

	private void testTransactionsByFind(ObservableCollection<Integer> observable, TransactableList<Integer> controller) {
		ObservableValueTester<Integer> tester = new ObservableValueTester<>(
			observable.observeFind(value -> value % 5 == 4).first().find());

		tester.checkOps(1);
		controller.add(0);
		tester.checkOps(0);
		controller.add(3);
		tester.checkOps(0);
		controller.add(9);
		tester.check(9, 1);
		Causable cause = Causable.simpleCause(null);
		try (Transaction t = Causable.use(cause)) {
			Transaction trans = controller.lock(true, cause);
			tester.checkOps(0);
			controller.add(0, 4);
			tester.checkOps(0);
			trans.close();
		}
		tester.check(4, 1);

		tester.setSynced(false);
		controller.clear();
	}

	private void testTransactionsByChanges(ObservableCollection<Integer> observable, TransactableList<Integer> controller) {
		ArrayList<Integer> compare = new ArrayList<>(observable);
		ArrayList<Integer> correct = new ArrayList<>(observable);
		boolean[] error = new boolean[1];
		int [] changeCount = new int[1];
		int[] correctChanges = new int[1];
		Subscription sub = observable.changes().act(event -> {
			if (error[0])
				return;
			changeCount[0]++;
			switch (event.type) {
			case add:
				for (CollectionChangeEvent.ElementChange<Integer> change : event.elements) {
					compare.add(change.index, change.newValue);
				}
				break;
			case remove:
				for (CollectionChangeEvent.ElementChange<Integer> change : event.getElementsReversed()) {
					assertEquals(compare.remove(change.index), change.newValue);
				}
				break;
			case set:
				for (CollectionChangeEvent.ElementChange<Integer> change : event.elements) {
					assertEquals(compare.set(change.index, change.newValue), change.oldValue);
				}
				break;
			}
		});
		assertEquals(correctChanges[0], changeCount[0]);

		for(int i = 0; i < 30; i++) {
			assertEquals(correctChanges[0], changeCount[0]);
			int toAdd = (int) (Math.random() * 2000000) - 1000000;
			controller.add(toAdd);
			correctChanges[0]++;
			correct.add(toAdd);
			assertEquals(correct, new ArrayList<>(observable));
			assertEquals(correct, compare);
		}
		assertEquals(correctChanges[0], changeCount[0]);

		Causable cause = Causable.simpleCause(null);
		try (Transaction t = Causable.use(cause); Transaction trans = controller.lock(true, cause)) {
			controller.clear();
			correct.clear();
			correct.addAll(observable);
			correctChanges[0]++;
			// Depending on the nature of the observable collection, the event for the clear may have been fired immediately, or it may
			// be fired at the next add, so don't check it until after that add
			for (int i = 0; i < 30; i++) {
				int toAdd = (int) (Math.random() * 2000000) - 1000000;
				controller.add(toAdd);
				if (i == 0)
					assertEquals(correctChanges[0], changeCount[0]);
				correct.add(toAdd);
				assertEquals(correct, new ArrayList<>(observable));
			}
			assertEquals(correctChanges[0], changeCount[0]);
		} catch (RuntimeException | Error e) {
			error[0] = true;
			throw e;
		}
		correctChanges[0]++;
		assertEquals(correctChanges[0], changeCount[0]);
		assertEquals(correct, compare);

		sub.unsubscribe();
		controller.clear();
	}

	/** Tests transactions caused by {@link CollectionDataFlow#refresh(Observable) refreshing} on an observable */
	@Test
	public void testTransactionsRefresh() {
		ObservableCollection<Integer> list = ObservableCollection.create(new TypeToken<Integer>() {});
		SimpleObservable<Causable> refresh = new SimpleObservable<>();

		for(int i = 0; i < 30; i++)
			list.add(i);

		ObservableTester tester = new ObservableTester(list.flow().refresh(refresh).collect().simpleChanges());

		tester.checkOps(0);

		refresh.onNext(null);
		tester.checkOps(1);

		Causable cause = Causable.simpleCause(null);
		try (Transaction t = Causable.use(cause)) {
			Transaction trans = list.lock(true, cause);
			refresh.onNext(cause);
			refresh.onNext(cause);
			trans.close();
		}
		tester.checkOps(1);

		cause = Causable.simpleCause(null);
		try (Transaction t = Causable.use(cause)) {
			Transaction trans = list.lock(true, cause);
			for (int i = 0; i < 30; i++)
				list.set(i, i + 1);
			refresh.onNext(cause);
			trans.close();
		}
		tester.checkOps(1);

		list.clear();
		tester.checkOps(1);
		refresh.onNext(null);
		tester.checkOps(0);

		list.addAll(java.util.Arrays.asList(0, 1, 2, 3, 4));
		tester.checkOps(1);
		refresh.onNext(null);
		tester.checkOps(1);

		tester.setSynced(false);
		refresh.onNext(null);
		tester.checkOps(0);
	}

	/** Tests transactions caused by {@link CollectionDataFlow#combine(TypeToken, Function) combining} a list with an observable value */
	@Test
	public void testTransactionsCombined() {
		ObservableCollection<Integer> list = ObservableCollection.create(new TypeToken<Integer>() {});
		SimpleSettableValue<Integer> mult = new SimpleSettableValue<>(new TypeToken<Integer>() {}, false);
		mult.set(1, null);
		ObservableCollection<Integer> product = list.flow().transform(intType, combine -> {
			return combine.combineWith(mult).build((s, cv) -> //
			s * cv.get(mult));
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

		Causable cause = Causable.simpleCause(null);
		try (Transaction t = Causable.use(cause)) {
			Transaction trans = list.lock(true, cause);
			mult.set(3, cause);
			mult.set(4, cause);
			trans.close();
		}
		correctChanges++;
		assertEquals(correctChanges, changes[0]);

		cause = Causable.simpleCause(null);
		try (Transaction t = Causable.use(cause)) {
			Transaction trans = list.lock(true, cause);
			for (int i = 0; i < 30; i++)
				list.set(i, i + 1);
			mult.set(5, cause);
			trans.close();
		}
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
