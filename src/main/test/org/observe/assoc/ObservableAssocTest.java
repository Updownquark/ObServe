package org.observe.assoc;

import static java.util.Arrays.asList;
import static org.observe.collect.ObservableCollectionsTest.intType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.observe.ObservableValue;
import org.observe.SimpleSettableValue;
import org.observe.assoc.ObservableGraph.Edge;
import org.observe.assoc.ObservableGraph.Node;
import org.observe.assoc.impl.DefaultObservableGraph;
import org.observe.collect.Equivalence;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollectionTester;

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;

/** Runs tests on the data structures built on top of observable collections. */
public class ObservableAssocTest {
	@Test
	public void testDefaultMultiMap() {
		ObservableMultiMap<Integer, Integer> map = ObservableMultiMap.create(intType, intType, Equivalence.DEFAULT).collect();
		ObservableCollectionTester<Integer> keyTester = new ObservableCollectionTester<>(map.keySet());
		Map<Integer, ObservableCollectionTester<Integer>> valueTesters = new java.util.LinkedHashMap<>();
		for (int i = 0; i < 10; i++)
			valueTesters.put(i, new ObservableCollectionTester<>(map.get(i)));
		for (int i = 0; i < 99; i++) {
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
		for (int i = 0; i < 99; i += 2) {
			int key = i % 9;
			map.remove(key, i);
			keyTester.check(0);
			valueTesters.get(key).remove(Integer.valueOf(i)).check(1);
			for (int j = 0; j < 10; j++) {
				if (j != key)
					valueTesters.get(j).check(0);
			}
		}
		map.get(5).clear();
		valueTesters.get(5).clear().check(1);
		for (int j = 0; j < 10; j++) {
			if (j != 5)
				valueTesters.get(j).check(0);
		}
	}

	@Test
	public void testGroupedMultiMap() {
		ObservableCollection<Integer> list = ObservableCollection.create(intType);
		ObservableMultiMap<Integer, Integer> map = list.flow().groupBy(intType, v -> v % 9).collect();

		ObservableCollectionTester<Integer> keyTester = new ObservableCollectionTester<>(map.keySet());
		Map<Integer, ObservableCollectionTester<Integer>> valueTesters = new java.util.LinkedHashMap<>();
		for (int i = 0; i < 10; i++)
			valueTesters.put(i, new ObservableCollectionTester<>(map.get(i)));
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

	// TODO Add tests for maps, multi-maps, and graphs and more tests for trees

	@Test
	public void testGraph() {
		DefaultObservableGraph<Integer, Integer> graph = new DefaultObservableGraph<>(intType, intType);
		ObservableCollectionTester<Integer> nodeChecker = new ObservableCollectionTester<>(graph.getNodeValues());
		ObservableCollectionTester<Integer> edgeChecker = new ObservableCollectionTester<>(
			graph.getEdges().flow().flattenValues(intType, e -> e).collect());
		Node<Integer, Integer> node0 = graph.addNode(0);
		nodeChecker.add(0);
		nodeChecker.check();
		Node<Integer, Integer> node1 = graph.addNode(1);
		nodeChecker.add(1);
		nodeChecker.check();
		List<ObservableCollectionTester<Edge<Integer, Integer>>> edgeCheckers = new ArrayList<>();
		ObservableCollectionTester<Edge<Integer, Integer>> node0OutChecker = new ObservableCollectionTester<>(node0.getOutward());
		ObservableCollectionTester<Edge<Integer, Integer>> node0InChecker = new ObservableCollectionTester<>(node0.getInward());
		ObservableCollectionTester<Edge<Integer, Integer>> node0AllChecker = new ObservableCollectionTester<>(node0.getEdges());
		ObservableCollectionTester<Edge<Integer, Integer>> node1OutChecker = new ObservableCollectionTester<>(node1.getOutward());
		ObservableCollectionTester<Edge<Integer, Integer>> node1InChecker = new ObservableCollectionTester<>(node1.getInward());
		ObservableCollectionTester<Edge<Integer, Integer>> node1AllChecker = new ObservableCollectionTester<>(node1.getEdges());
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

	class TreeNode<T> {
		final SimpleSettableValue<T> value;
		final ObservableCollection<TreeNode<T>> children;

		TreeNode(TypeToken<T> type, T initValue) {
			value = new SimpleSettableValue<>(type, false);
			children = ObservableCollection.create(new TypeToken<TreeNode<T>>() {}.where(new TypeParameter<T>() {}, type));

			value.set(initValue, null);
		}

		TreeNode<T> addChild(T... childValues) {
			for (T childValue : childValues)
				children.add(new TreeNode<>(value.getType(), childValue));
			return this;
		}
	}

	/** Tests {@link ObservableTree#valuePathsOf(ObservableTree, boolean)} */
	@Test
	public void testTreeValuePaths() {
		TypeToken<Integer> type=TypeToken.of(Integer.class);
		TreeNode<Integer> root=new TreeNode<>(type, 0);
		root.addChild(1, 2, 3);
		root.children.get(0).addChild(4, 7, 10);
		root.children.get(1).addChild(5, 8, 11);
		root.children.get(2).addChild(6, 9, 12);

		ObservableTree<TreeNode<Integer>, Integer> tree = ObservableTree.of(
			ObservableValue.of(root.children.getType(), root), type,
			n -> n.value, n -> n.children);

		ObservableCollection<List<Integer>> allPaths = ObservableTree.valuePathsOf(tree, false);
		ObservableCollection<List<Integer>> terminalPaths = ObservableTree.valuePathsOf(tree, true);
		ObservableCollectionTester<List<Integer>> allPathsTester = new ObservableCollectionTester<>(allPaths);
		ObservableCollectionTester<List<Integer>> terminalPathsTester = new ObservableCollectionTester<>(terminalPaths);

		allPathsTester.set(//
			asList(0), //
			asList(0, 1), asList(0, 2), asList(0, 3), //
			asList(0, 1, 4), asList(0, 1, 7), asList(0, 1, 10), //
			asList(0, 2, 5), asList(0, 2, 8), asList(0, 2, 11), //
			asList(0, 3, 6), asList(0, 3, 9), asList(0, 3, 12));
		terminalPathsTester.set(//
			asList(0, 1, 4), asList(0, 1, 7), asList(0, 1, 10), //
			asList(0, 2, 5), asList(0, 2, 8), asList(0, 2, 11), //
			asList(0, 3, 6), asList(0, 3, 9), asList(0, 3, 12));

		allPathsTester.check();
		terminalPathsTester.check();

		root.children.get(1).children.get(1).addChild(12, 15, 18);
		allPathsTester.add(//
			asList(0, 2, 8, 12), asList(0, 2, 8, 15), asList(0, 2, 8, 18));
		terminalPathsTester.remove(asList(0, 2, 8)).add(//
			asList(0, 2, 8, 12), asList(0, 2, 8, 15), asList(0, 2, 8, 18));

		allPathsTester.check();
		terminalPathsTester.check();

		root.children.remove(1);
		allPathsTester.removeAll(//
			asList(0, 2), //
			asList(0, 2, 5), asList(0, 2, 8), asList(0, 2, 11), //
			asList(0, 2, 8, 12), asList(0, 2, 8, 15), asList(0, 2, 8, 18));
		terminalPathsTester.removeAll(//
			asList(0, 2, 5), asList(0, 2, 11), //
			asList(0, 2, 8, 12), asList(0, 2, 8, 15), asList(0, 2, 8, 18));

		allPathsTester.check();
		terminalPathsTester.check();
	}
}
