package org.observe.assoc;

import static java.util.Arrays.asList;
import static org.observe.collect.ObservableCollectionsTest.intType;

import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.observe.ObservableValue;
import org.observe.SimpleSettableValue;
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
