package org.observe.assoc;

import java.time.Duration;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.junit.Assert;
import org.junit.Test;
import org.observe.SimpleObservable;
import org.observe.Subscription;
import org.observe.assoc.ObservableGraph.Edge;
import org.observe.assoc.ObservableGraph.Node;
import org.observe.assoc.impl.DefaultObservableGraph;
import org.observe.collect.CollectionChangeEvent;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollectionTester;
import org.qommons.BiTuple;
import org.qommons.collect.MultiEntryHandle;
import org.qommons.testing.QommonsTestUtils;
import org.qommons.testing.TestHelper;
import org.qommons.testing.TestHelper.Testable;

/** Runs tests on the data structures built on top of observable collections. */
public class ObservableAssocTest {
	/** Tests the default multi-map produced by {@link ObservableMultiMap#build()} */
	@Test
	public void testDefaultMultiMap() {
		ObservableMultiMap<Integer, Integer> map = ObservableMultiMap.<Integer, Integer> build().build(null);
		ObservableCollectionTester<Integer> keyTester = new ObservableCollectionTester<>("keys", map.keySet());
		Map<Integer, ObservableCollectionTester<Integer>> valueTesters = new java.util.LinkedHashMap<>();

		int i = -1;
		try {
			for (i = 0; i < 10; i++)
				valueTesters.put(i, new ObservableCollectionTester<>("value@" + i, map.get(i)));
			for (i = 0; i < 99; i++) {
				int key = i % 9;
				map.add(key, i);
				if (i < 9)
					keyTester.add(i);
				keyTester.check(i < 9 ? 1 : 0);
				valueTesters.get(key).add(i).check(1);
				for (int j = 0; j < 10; j++) {
					if (j != key)
						valueTesters.get(j).check(0);
				}
			}
			for (i = 0; i < 99; i += 2) {
				int key = i % 9;
				map.remove(key, i);
				keyTester.check(0);
				valueTesters.get(key).remove(Integer.valueOf(i)).check(1);
				for (int j = 0; j < 10; j++) {
					if (j != key)
						valueTesters.get(j).check(0);
				}
			}
			i = -1;
			map.get(5).clear();
			valueTesters.get(5).clear().check(1);
			for (i = 0; i < 10; i++) {
				if (i != 5)
					valueTesters.get(i).check(0);
			}
		} catch (RuntimeException | Error e) {
			System.err.println("i=" + i);
			throw e;
		}
	}

	/** Tests {@link org.observe.collect.ObservableCollection.CollectionDataFlow#groupBy(Function, BiFunction)} */
	@Test
	public void testGroupedMultiMap() {
		ObservableCollection<Integer> list = ObservableCollection.create();
		ObservableMultiMap<Integer, Integer> map = list.flow().groupBy(v -> v % 9, null).gather();

		ObservableCollectionTester<Integer> keyTester = new ObservableCollectionTester<>("keys", map.keySet());
		Map<Integer, ObservableCollectionTester<Integer>> valueTesters = new java.util.LinkedHashMap<>();
		for (int i = 0; i < 10; i++)
			valueTesters.put(i, new ObservableCollectionTester<>("value@" + i, map.get(i)));
		for (int i = 0; i < 99; i++) {
			list.add(i);
			int key = i % 9;
			if (i < 9)
				keyTester.add(i);
			keyTester.check(i < 9 ? 1 : 0);
			valueTesters.get(key).add(i).check(1);
			for (int j = 0; j < 10; j++) {
				if (j != key)
					valueTesters.get(j).check(0);
			}
		}
		for (int i = 0; i < 99; i += 2) {
			list.remove(Integer.valueOf(i));
			int key = i % 9;
			keyTester.check(0);
			valueTesters.get(key).remove(Integer.valueOf(i)).check(1);
			for (int j = 0; j < 10; j++) {
				if (j != key)
					valueTesters.get(j).check(0);
			}
		}
		list.removeIf(v -> v % 9 == 5);
		valueTesters.get(5).clear().check(1);
		for (int j = 0; j < 10; j++) {
			if (j != 5)
				valueTesters.get(j).check(0);
		}
	}

	/** Tests {@link DefaultObservableGraph} */
	@Test
	public void testGraph() {
		DefaultObservableGraph<Integer, Integer> graph = new DefaultObservableGraph<>();
		ObservableCollectionTester<Integer> nodeChecker = new ObservableCollectionTester<>("nodes", graph.getNodeValues());
		ObservableCollectionTester<Integer> edgeChecker = new ObservableCollectionTester<>("edges",
			graph.getEdges().flow().flattenValues(e -> e).collect());
		Node<Integer, Integer> node0 = graph.addNode(0);
		nodeChecker.add(0);
		nodeChecker.check();
		Node<Integer, Integer> node1 = graph.addNode(1);
		nodeChecker.add(1);
		nodeChecker.check();
		List<ObservableCollectionTester<Edge<Integer, Integer>>> edgeCheckers = new ArrayList<>();
		ObservableCollectionTester<Edge<Integer, Integer>> node0OutChecker = new ObservableCollectionTester<>("node0out",
			node0.getOutward());
		ObservableCollectionTester<Edge<Integer, Integer>> node0InChecker = new ObservableCollectionTester<>("node0in", node0.getInward());
		ObservableCollectionTester<Edge<Integer, Integer>> node0AllChecker = new ObservableCollectionTester<>("node0edges",
			node0.getEdges());
		ObservableCollectionTester<Edge<Integer, Integer>> node1OutChecker = new ObservableCollectionTester<>("node1out",
			node1.getOutward());
		ObservableCollectionTester<Edge<Integer, Integer>> node1InChecker = new ObservableCollectionTester<>("node1in", node1.getInward());
		ObservableCollectionTester<Edge<Integer, Integer>> node1AllChecker = new ObservableCollectionTester<>("node1edges",
			node1.getEdges());
		edgeCheckers.addAll(Arrays.asList(node0OutChecker, node0InChecker, node0AllChecker, //
			node1OutChecker, node1InChecker, node1AllChecker));
		Edge<Integer, Integer> edge = graph.addEdge(node0, node1, true, 0);
		edgeChecker.add(0);
		node0OutChecker.add(edge);
		node1InChecker.add(edge);
		node0AllChecker.add(edge);
		node1AllChecker.add(edge);
		edgeChecker.check();
		edgeCheckers.forEach(ObservableCollectionTester::check);

		edge = graph.addEdge(node1, node0, true, 5);
		edgeChecker.add(5);
		node0InChecker.add(edge);
		node1OutChecker.add(edge);
		node0AllChecker.add(edge);
		node1AllChecker.add(0, edge);
		edgeChecker.check();
		edgeCheckers.forEach(ObservableCollectionTester::check);

		Node<Integer, Integer> node3 = graph.addNode(3);
		nodeChecker.add(3);
		nodeChecker.check();
		edgeChecker.check(0);
		edgeCheckers.forEach(ec -> ec.check(0));

		edge = graph.addEdge(node0, node3, true, 10);
		edgeChecker.add(1, Integer.valueOf(10));
		node0OutChecker.add(edge);
		node0AllChecker.add(1, edge);
		edgeChecker.check(0);
		edgeCheckers.forEach(ec -> ec.check());

		graph.getNodeValues().remove(1);
		edgeChecker.getExpected().remove(0);
		edgeChecker.getExpected().remove(1);
		node0OutChecker.getExpected().remove(0);
		node0InChecker.getExpected().remove(0);
		node0AllChecker.getExpected().remove(0);
		node0AllChecker.getExpected().remove(1);
		node1OutChecker.clear();
		node1InChecker.clear();
		node1AllChecker.clear();
		nodeChecker.remove(1);
		nodeChecker.check();
		edgeChecker.check();
		edgeCheckers.forEach(ec -> ec.check());
	}

	/**
	 * A more complicated test on {@link ObservableMultiMap}. I came up with this test to mimic a situation I encountered at work that I
	 * tried to solve this way, but there was a but in the multi-map code.
	 */
	@Test
	public void testMultiMapImproved() {
		TestHelper.createTester(ImprovedMultiMapTester.class).withMaxTotalDuration(Duration.ofSeconds(15))//
		/**/.revisitKnownFailures(true).withDebug(true)//
		.withPlacemarks("action")//
		.execute().throwErrorIfFailed();
	}

	static class ImprovedMultiMapTester implements Testable {
		static class ValueHolder implements Comparable<ValueHolder> {
			final int id;
			final ObservableCollection<Integer> values;

			ValueHolder(int id, TestHelper helper) {
				this.id = id;
				values = ObservableCollection.<Integer> build().build();
				int initValues = helper.getInt(0, 10);
				for (int i = 0; i < initValues; i++)
					values.add(helper.getInt(0, 20)); // Narrow range so there's lots of grouping
			}

			@Override
			public int compareTo(ValueHolder o) {
				return Integer.compare(id, o.id);
			}

			@Override
			public String toString() {
				return Integer.toString(id);
			}
		}

		static String mapString(ObservableCollection<ValueHolder> holders) {
			StringBuilder str = new StringBuilder();
			str.append('{');
			boolean first = true;
			for (ValueHolder holder : holders) {
				if (first)
					first = false;
				else
					str.append(", ");
				str.append(holder).append('=').append(holder.values);
			}
			return str.append('}').toString();
		}

		static String mapString(SortedMap<Integer, SortedMap<ValueHolder, Integer>> expected) {
			StringBuilder str = new StringBuilder();
			str.append('{');
			boolean first = true;
			for (Map.Entry<Integer, SortedMap<ValueHolder, Integer>> entry : expected.entrySet()) {
				if (first)
					first = false;
				else
					str.append(", ");
				str.append(entry.getKey()).append('=').append(entry.getValue().keySet());
			}
			return str.append('}').toString();
		}

		final SimpleObservable<Void> testUntil = SimpleObservable.build().build();
		final ObservableCollection<ValueHolder> holders = ObservableCollection.<ValueHolder> build().build();
		final ObservableMultiMap<Integer, ValueHolder> test = holders.flow()
			.<BiTuple<ValueHolder, Integer>> flatMap(holder -> holder.values.flow().map(v -> new BiTuple<>(holder, v)))//
			.groupBy(tuple -> tuple.getValue2(), Integer::compare, null)//
			.withValues(values -> values.map(tuple -> tuple.getValue1()).distinctSorted(ValueHolder::compareTo, false))//
			.gather();
		int expectedValueSize;
		final SortedMap<Integer, SortedMap<ValueHolder, Integer>> expected = new TreeMap<>();
		SortedMap<Integer, List<ValueHolder>> listeningMap;
		SortedSet<Integer> listeningKeySet;
		final SortedMap<Integer, ObservableCollection<ValueHolder>> watchedCollections = new TreeMap<>();
		final SortedMap<Integer, List<ValueHolder>> listeningCollections = new TreeMap<>();

		@Override
		public void accept(TestHelper helper) {
			// Let's initialize the map with some values to start with so we don't have to over-weight adding holders below,
			// and also to test the initial value gathering
			int initHolders = helper.getInt(0, 4);
			Set<Integer> holderIds = new HashSet<>();
			for (int i = 0; i < initHolders; i++) {
				int holderId = helper.getInt(0, 10_000);
				while (!holderIds.add(holderId))
					holderId = helper.getInt(0, 10_000);
				holders.add(new ValueHolder(holderId, helper));
			}
			for (ValueHolder holder : holders) {
				for (Integer value : holder.values) {
					expected.computeIfAbsent(value, v -> new TreeMap<>()).compute(holder,
						(h, oldCount) -> {
							if (oldCount == null) {
								expectedValueSize++;
								return 1;
							} else
								return oldCount + 1;
						});
				}
			}

			initTestListeners(helper);

			helper.placemark();
			if (helper.isReproducing()) {
				System.out.println("Expected=" + mapString(expected));
				System.out.println("Actual=  " + test.toString());
			}
			check(0);
			for (int i = 0; i < 250; i++) {
				int changeIndex = i + 1;
				helper.createAction()//
				.or(0.05, () -> { // Add value holder
					int holderId = helper.getInt(0, 10_000);
					while (!holderIds.add(holderId))
						holderId = helper.getInt(0, 10_000);
					ValueHolder newHolder = new ValueHolder(holderId, helper);
					if (helper.isReproducing())
						System.out.println("Adding holder\n\t" + newHolder.id + "=" + newHolder.values);
					for (Integer value : newHolder.values) {
						expected.computeIfAbsent(value, v -> new TreeMap<>()).compute(newHolder,
							(h, oldCount) -> {
								if (oldCount == null) {
									expectedValueSize++;
									return 1;
								} else
									return oldCount + 1;
							});
					}
					holders.add(newHolder);
				})//
				.or(0.025, () -> { // Remove value holder
					if (holders.isEmpty())
						return;
					int index = helper.getInt(0, holders.size());
					if (helper.isReproducing())
						System.out.println("Removing holder\n\t" + holders.get(index).id + "=" + holders.get(index).values);
					ValueHolder removed = holders.remove(index);
					for (Integer value : removed.values) {
						expected.compute(value, (v, set) -> {
							set.compute(removed, (h, oldCount) -> {
								if (oldCount.intValue() == 1) {
									expectedValueSize--;
									return null;
								} else
									return oldCount - 1;
							});
							return set.isEmpty() ? null : set;
						});
					}
				})//
				.or(.01, () -> { // Replace an entire holder
					if (holders.isEmpty())
						return;
					int index = helper.getInt(0, holders.size());
					int holderId = helper.getInt(0, 10_000);
					while (!holderIds.add(holderId))
						holderId = helper.getInt(0, 10_000);
					ValueHolder removed = holders.get(index);
					ValueHolder newHolder = new ValueHolder(holderId, helper);
					if (helper.isReproducing())
						System.out.println("Replacing holder\n\t" + removed.id + "=" + removed.values + "\n\twith\n\t" + newHolder.id
							+ "=" + newHolder.values);
					for (Integer value : newHolder.values) {
						expected.computeIfAbsent(value, v -> new TreeMap<>()).compute(newHolder,
							(h, oldCount) -> {
								if (oldCount == null) {
									expectedValueSize++;
									return 1;
								} else
									return oldCount + 1;
							});
					}
					for (Integer value : removed.values) {
						expected.compute(value, (v, set) -> {
							set.compute(removed, (h, oldCount) -> {
								if (oldCount.intValue() == 1) {
									expectedValueSize--;
									return null;
								} else
									return oldCount - 1;
							});
							return set.isEmpty() ? null : set;
						});
					}
					holders.set(index, newHolder);
				})//
				.or(.25, () -> { // Add values to a holder
					if (holders.isEmpty())
						return;
					int index = helper.getInt(0, holders.size());
					ValueHolder toModify = holders.get(index);
					int count = helper.getInt(1, 10);
					if (helper.isReproducing())
						System.out.println("Adding " + count + " value" + (count == 1 ? "" : "s") + " to holder\n\t" + toModify.id + "="
							+ toModify.values + "\n\t");
					for (int j = 0; j < count; j++) {
						int valueIndex = helper.getInt(0, toModify.values.size() + 1);
						int newValue = helper.getInt(0, 20);
						if (helper.isReproducing()) {
							if (j > 0)
								System.out.print(", ");
							System.out.print(newValue + "@" + valueIndex);
						}
						expected.computeIfAbsent(newValue, v -> new TreeMap<>()).compute(toModify, (h, oldCount) -> {
							if (oldCount == null) {
								expectedValueSize++;
								return 1;
							} else
								return oldCount + 1;
						});
						toModify.values.add(valueIndex, newValue);
						check(changeIndex);
					}
					if (helper.isReproducing())
						System.out.println();
				})//
				.or(.15, () -> { // Remove values from a holder
					if (holders.isEmpty())
						return;
					int holderIndex = helper.getInt(0, holders.size());
					ValueHolder toModify = holders.get(holderIndex);
					if (toModify.values.isEmpty())
						return;
					int count = Math.min(toModify.values.size(), helper.getInt(1, 10));
					if (helper.isReproducing())
						System.out.println("Removing " + count + " value" + (count == 1 ? "" : "s") + " from holder\n\t" + toModify.id
							+ "=" + toModify.values + "\n\t");
					for (int j = 0; j < count; j++) {
						int valueIndex = helper.getInt(0, toModify.values.size());
						if (helper.isReproducing()) {
							if (j > 0)
								System.out.print(", ");
							System.out.print(toModify.values.get(valueIndex) + "@" + valueIndex);
						}
						int oldValue = toModify.values.remove(valueIndex);
						expected.compute(oldValue, (v, set) -> {
							set.compute(toModify, (h, oldCount) -> {
								if (oldCount.intValue() == 1) {
									expectedValueSize--;
									return null;
								} else
									return oldCount - 1;
							});
							return set.isEmpty() ? null : set;
						});
						check(changeIndex);
					}
					if (helper.isReproducing())
						System.out.println();
				})//
				.or(.15, () -> { // Update values in a holder
					if (holders.isEmpty())
						return;
					int holderIndex = helper.getInt(0, holders.size());
					ValueHolder toModify = holders.get(holderIndex);
					if (toModify.values.isEmpty())
						return;
					int count = Math.min(toModify.values.size(), helper.getInt(1, 10));
					if (helper.isReproducing())
						System.out.println("Updating " + count + " value" + (count == 1 ? "" : "s") + " in holder\n\t" + toModify.id
							+ "=" + toModify.values + "\n\t");
					for (int j = 0; j < count; j++) {
						int valueIndex = helper.getInt(0, toModify.values.size());
						int newValue = helper.getInt(0, 20);
						if (helper.isReproducing()) {
							if (j > 0)
								System.out.print(", ");
							System.out.print(toModify.values.get(valueIndex) + "->" + newValue + "@" + valueIndex);
						}
						int oldValue = toModify.values.set(valueIndex, newValue);
						expected.compute(oldValue, (v, set) -> {
							set.compute(toModify, (h, oldCount) -> {
								if (oldCount.intValue() == 1) {
									expectedValueSize--;
									return null;
								} else
									return oldCount - 1;
							});
							return set.isEmpty() ? null : set;
						});
						expected.computeIfAbsent(newValue, v -> new TreeMap<>()).compute(toModify,
							(h, oldCount) -> {
								if (oldCount == null) {
									expectedValueSize++;
									return 1;
								} else
									return oldCount + 1;
							});
						check(changeIndex);
					}
					if (helper.isReproducing())
						System.out.println();
				})//
				.or(.01, () -> { // Clear all values from a holder
					if (holders.isEmpty())
						return;
					int index = helper.getInt(0, holders.size());
					ValueHolder toModify = holders.get(index);
					if (helper.isReproducing())
						System.out.println("Clearing holder\n\t" + toModify.id + "=" + toModify.values);
					for (Integer value : toModify.values) {
						expected.compute(value, (v, set) -> {
							set.compute(toModify, (h, oldCount) -> {
								if (oldCount.intValue() == 1) {
									expectedValueSize--;
									return null;
								} else
									return oldCount - 1;
							});
							return set.isEmpty() ? null : set;
						});
					}
					toModify.values.clear();
				})//
				.execute("action");
				if (helper.isReproducing()) {
					System.out.println("Expected=" + mapString(expected));
					System.out.println("Actual=  " + test.toString());
					if (listeningMap != null)
						System.out.println("Listen=  " + listeningMap);
				}
				check(changeIndex);
			}
			testUntil.onNext(null);
		}

		void initTestListeners(TestHelper helper) {
			// Test map listening also, but not all the time--some code in the multi-maps takes shortcuts if no listeners are installed
			if (helper.getBoolean()) {
				listeningMap = new TreeMap<>();
				Subscription sub = test.subscribe(mme -> {
					switch (mme.getType()) {
					case add:
						listeningMap.computeIfAbsent(mme.getKey(), k -> new ArrayList<>()).add(mme.getIndex(), mme.getNewValue());
						break;
					case remove:
						listeningMap.compute(mme.getKey(), (k, old) -> {
							Assert.assertEquals(mme.getOldValue(), old.remove(mme.getIndex()));
							return old.isEmpty() ? null : old;
						});
						break;
					case set:
						if (mme.getElementId() == null)
							listeningMap.put(mme.getKey(), listeningMap.remove(mme.getOldKey()));
						else {
							List<ValueHolder> values = listeningMap.get(mme.getKey());
							Assert.assertEquals(mme.getOldValue(), values.set(mme.getIndex(), mme.getNewValue()));
						}
						break;
					}
				}, true, true);
				testUntil.act(__ -> sub.unsubscribe());
			} else
				listeningMap = null;
			if (helper.getBoolean()) {
				listeningKeySet = new TreeSet<>();
				Subscription sub = test.keySet().subscribe(evt -> {
					switch (evt.getType()) {
					case add:
						Assert.assertTrue(listeningKeySet.add(evt.getNewValue()));
						break;
					case remove:
						Assert.assertTrue(listeningKeySet.remove(evt.getOldValue()));
						break;
					case set:
						Assert.assertTrue(listeningKeySet.remove(evt.getOldValue()));
						Assert.assertTrue(listeningKeySet.add(evt.getNewValue()));
						break;
					}
				}, true);
				testUntil.act(__ -> sub.unsubscribe());
			} else
				listeningKeySet = null;
			for (int i = 0; i < helper.getInt(0, 20); i++) {
				int value = helper.getInt(0, 20);
				while (watchedCollections.keySet().contains(value))
					value = helper.getInt(0, 20);
				watchedCollections.put(value, test.get(value));
				if (helper.getBoolean()) {
					List<ValueHolder> listening = new ArrayList<>();
					listeningCollections.put(value, listening);
					listening.addAll(watchedCollections.get(value));
					watchedCollections.get(value).changes().takeUntil(testUntil).act(evt -> {
						for (CollectionChangeEvent.ElementChange<ValueHolder> change : evt.getElements()) {
							switch (evt.type) {
							case add:
								listening.add(change.index, change.newValue);
								break;
							case remove:
								Assert.assertEquals(change.oldValue, listening.remove(change.index));
								break;
							case set:
								Assert.assertEquals(change.oldValue, listening.set(change.index, change.newValue));
								break;
							}
						}
					});
				}
			}
		}

		private void check(int action) {
			try {
				StringBuilder msg = new StringBuilder().append(action).append(": ");
				Assert.assertEquals(message(msg, "Unequal entry sizes"), expected.size(), test.keySet().size());
				Assert.assertEquals(message(msg, "Unequal value sizes"), expectedValueSize, test.valueSize());
				Assert.assertEquals(message(msg, "Incorrect values collection"), expectedValueSize, test.values().size());
				Iterator<? extends MultiEntryHandle<Integer, ValueHolder>> testEntries = test.entrySet().iterator();
				Iterator<Map.Entry<Integer, SortedMap<ValueHolder, Integer>>> expectedEntries = expected.entrySet().iterator();
				Iterator<ValueHolder> valuesIter = test.values().iterator();
				Iterator<Map.Entry<Integer, List<ValueHolder>>> listeningIter = listeningMap == null ? null
					: listeningMap.entrySet().iterator();
				Iterator<Integer> listeningKSIter = listeningKeySet == null ? null : listeningKeySet.iterator();
				while (expectedEntries.hasNext()) {
					Map.Entry<Integer, SortedMap<ValueHolder, Integer>> expectedEntry = expectedEntries.next();
					if (!testEntries.hasNext())
						throw new AssertionError(message(msg, "Missing value: ", expectedEntry));
					MultiEntryHandle<Integer, ValueHolder> testEntry = testEntries.next();
					Assert.assertEquals(message(msg, "Key mismatch"), expectedEntry.getKey(), testEntry.getKey());
					Assert.assertEquals(message(msg, "Entry value size mismatch: ", expectedEntry.getKey()),
						expectedEntry.getValue().size(), testEntry.getValues().size());

					Iterator<ValueHolder> expectedValueIter = expectedEntry.getValue().keySet().iterator();
					Iterator<ValueHolder> testValueIter = testEntry.getValues().iterator();
					while (expectedValueIter.hasNext()) {
						ValueHolder expectedValue = expectedValueIter.next();
						if (!testValueIter.hasNext())
							throw new AssertionError(message(msg, "Missing entry: ", expectedEntry.getKey() + ": " + expectedValue));
						ValueHolder testValue1 = testValueIter.next();
						Assert.assertEquals(message(msg, "Entry value mismatch: ", expectedEntry.getKey()), expectedValue, testValue1);
						if (!valuesIter.hasNext())
							throw new AssertionError(
								message(msg, "Missing values() entry: ", expectedEntry.getKey() + ": " + expectedValue));
						ValueHolder testValue2 = valuesIter.next();
						Assert.assertEquals(message(msg, "Entry value mismatch: ", expectedEntry.getKey()), expectedValue, testValue2);
					}
					if (testValueIter.hasNext())
						throw new AssertionError(action + ": Extra value: " + expectedEntry.getKey() + ": " + testEntries.next());

					if (listeningIter != null) {
						if (!listeningIter.hasNext())
							throw new AssertionError(message(msg, "Missing listened value: ", expectedEntry));
						Map.Entry<Integer, List<ValueHolder>> listeningEntry = listeningIter.next();
						Assert.assertEquals(message(msg, "Listening key mismatch"), expectedEntry.getKey(), listeningEntry.getKey());
						Assert.assertEquals(message(msg, "Listening entry value size mismatch: ", expectedEntry.getKey()),
							expectedEntry.getValue().size(), listeningEntry.getValue().size());

						expectedValueIter = expectedEntry.getValue().keySet().iterator();
						testValueIter = listeningEntry.getValue().iterator();
						while (expectedValueIter.hasNext()) {
							ValueHolder expectedValue = expectedValueIter.next();
							if (!testValueIter.hasNext())
								throw new AssertionError(
									message(msg, "Missing listening entry: ", expectedEntry.getKey() + ": " + expectedValue));
							ValueHolder testValue1 = testValueIter.next();
							Assert.assertEquals(message(msg, "Listening entry value mismatch: ", expectedEntry.getKey()), expectedValue,
								testValue1);
						}
						if (testValueIter.hasNext())
							throw new AssertionError(
								action + ": Extra listening value: " + expectedEntry.getKey() + ": " + testEntries.next());
					}
					if (listeningKSIter != null) {
						if (!listeningKSIter.hasNext())
							throw new AssertionError(message(msg, "Missing listened key set value: ", expectedEntry));
						Assert.assertEquals(message(msg, "Listening key set mismatch"), expectedEntry.getKey(), listeningKSIter.next());
					}
					if (listeningCollections.containsKey(expectedEntry.getKey())) {
						QommonsTestUtils.assertThat(message(msg, "listening to ", expectedEntry.getKey()),
							listeningCollections.get(expectedEntry.getKey()),
							QommonsTestUtils.collectionsEqual(expectedEntry.getValue().keySet(), true));
					}
					if (watchedCollections.containsKey(expectedEntry.getKey())) {
						QommonsTestUtils.assertThat(message(msg, "watching ", expectedEntry.getKey()),
							watchedCollections.get(expectedEntry.getKey()),
							QommonsTestUtils.collectionsEqual(expectedEntry.getValue().keySet(), true));
					}
				}
				if (testEntries.hasNext())
					throw new AssertionError(action + ": Extra value: " + testEntries.next());
				if (listeningIter != null && listeningIter.hasNext())
					throw new AssertionError(action + ": Extra listening value: " + listeningIter.next());
				for (Map.Entry<Integer, List<ValueHolder>> listening : listeningCollections.entrySet()) {
					if (!expected.containsKey(listening.getKey())) {
						QommonsTestUtils.assertThat(message(msg, "listening ", listening.getKey()), listening.getValue(),
							QommonsTestUtils.collectionsEqual(Collections.emptyList(), true));
					}
				}
				for (Map.Entry<Integer, ObservableCollection<ValueHolder>> watching : watchedCollections.entrySet()) {
					if (!expected.containsKey(watching.getKey())) {
						QommonsTestUtils.assertThat(message(msg, "watching ", watching.getKey()), watching.getValue(),
							QommonsTestUtils.collectionsEqual(Collections.emptyList(), true));
					}
				}
			} catch (RuntimeException | Error e) {
				System.out.println("\nExpected=" + mapString(expected));
				System.out.println("Actual=  " + test.toString());
				if (listeningMap != null)
					System.out.println("Listen=  " + listeningMap);
				throw e;
			}
		}

		private static String message(StringBuilder str, Object... message) {
			int preLen = str.length();
			for (Object m : message)
				str.append(m);
			String msg = str.toString();
			str.setLength(preLen);
			return msg;
		}
	}
}
