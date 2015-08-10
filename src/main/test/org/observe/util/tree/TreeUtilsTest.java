package org.observe.util.tree;

import java.util.Iterator;

import org.junit.Test;
import org.observe.collect.ObservableCollectionsTest;
import org.observe.util.tree.CountedRedBlackNode.DefaultNode;
import org.observe.util.tree.CountedRedBlackNode.DefaultTreeSet;
import org.observe.util.tree.RedBlackNode.ComparableValuedRedBlackNode;
import org.observe.util.tree.RedBlackNode.ValuedRedBlackNode;

/** Runs tests on the red-black tree structures behind the ObServe tree collections */
public class TreeUtilsTest {
	/**
	 * A testing method. Adds sequential nodes into a tree and removes them, checking validity of the tree at each step.
	 *
	 * @param <T> The type of values to put in the tree
	 * @param tree The initial tree node
	 * @param nodes The sequence of nodes to add to the tree. Must repeat.
	 */
	public static <T> void test(ValuedRedBlackNode<T> tree, Iterable<T> nodes) {
		Iterator<T> iter = nodes.iterator();
		iter.next(); // Skip the first value, assuming that's what's in the tree
		System.out.println(RedBlackNode.print(tree));
		System.out.println(" ---- ");
		while(iter.hasNext()) {
			T value = iter.next();
			System.out.println("Adding " + value);
			tree = (ValuedRedBlackNode<T>) tree.add(value, false).getNewRoot();
			System.out.println(RedBlackNode.print(tree));
			tree.checkValid();
			System.out.println(" ---- ");
		}
		System.out.println(" ---- \n ---- \nDeleting:");

		iter = nodes.iterator();
		while(iter.hasNext()) {
			T value = iter.next();
			System.out.println("Deleting " + value);
			tree = (ValuedRedBlackNode<T>) tree.findValue(value).delete();
			System.out.println(RedBlackNode.print(tree));
			if(tree != null)
				tree.checkValid();
			System.out.println(" ---- ");
		}
	}

	/**
	 * Iterates through the alphabet from 'a' up to the given character
	 *
	 * @param last The last letter to be returned from the iterator
	 * @return An alphabet iterable
	 */
	protected static final Iterable<String> alphaBet(char last) {
		return () -> {
			return new Iterator<String>() {
				private char theNext = 'a';

				@Override
				public boolean hasNext() {
					return theNext <= last;
				}

				@Override
				public String next() {
					String ret = "" + theNext;
					theNext++;
					return ret;
				}
			};
		};
	}

	@Test
	public void testTreeBasic() {
		test(ComparableValuedRedBlackNode.valueOf("a"), alphaBet('q'));
	}

	@Test
	public void countedTreeBasic() {
		class CountedStringNode extends CountedRedBlackNode<String> {
			public CountedStringNode(String value) {
				super(value);
			}

			@Override
			protected CountedRedBlackNode<String> createNode(String value) {
				return new CountedStringNode(value);
			}

			@Override
			protected int compare(String o1, String o2) {
				return o1.compareTo(o2);
			}
		}
		test(new CountedStringNode("a"), alphaBet('q'));
	}

	@Test
	public void testTreeSet() {
		DefaultTreeSet<Integer> set = new DefaultTreeSet<>(Integer::compareTo);
		ObservableCollectionsTest.testSortedSet(set, s -> {
			DefaultNode<Integer> root = set.getRoot();
			if(root != null)
				root.checkValid();
		});
	}

	public void testTreeMap() {
		// TODO
	}
}
