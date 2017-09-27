package org.observe.assoc;

import static java.util.Arrays.asList;

import java.util.List;

import org.junit.Test;
import org.observe.ObservableValue;
import org.observe.SimpleSettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollectionTester;

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;

/** Runs tests on the data structures built on top of observable collections. */
public class ObservableAssocTest {
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
			ObservableValue.constant(root.children.getType(), root), type,
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
