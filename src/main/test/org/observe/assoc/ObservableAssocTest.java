package org.observe.assoc;

import static org.observe.collect.ObservableCollectionsTest.intType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.junit.Test;
import org.observe.assoc.ObservableGraph.Edge;
import org.observe.assoc.ObservableGraph.Node;
import org.observe.assoc.impl.DefaultObservableGraph;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollectionTester;

import com.google.common.reflect.TypeToken;

/** Runs tests on the data structures built on top of observable collections. */
public class ObservableAssocTest {
	/** Tests the default multi-map produced by {@link ObservableMultiMap#build(TypeToken, TypeToken)} */
	@Test
	public void testDefaultMultiMap() {
		ObservableMultiMap<Integer, Integer> map = ObservableMultiMap.build(intType, intType).build(null);
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
		ObservableCollection<Integer> list = ObservableCollection.create(intType);
		ObservableMultiMap<Integer, Integer> map = list.flow().groupBy(intType, v -> v % 9, null).gather();

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
		DefaultObservableGraph<Integer, Integer> graph = new DefaultObservableGraph<>(intType, intType);
		ObservableCollectionTester<Integer> nodeChecker = new ObservableCollectionTester<>("nodes", graph.getNodeValues());
		ObservableCollectionTester<Integer> edgeChecker = new ObservableCollectionTester<>("edges",
			graph.getEdges().flow().flattenValues(intType, e -> e).collect());
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
}
