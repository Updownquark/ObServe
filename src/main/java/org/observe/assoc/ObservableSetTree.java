package org.observe.assoc;

import java.util.List;
import java.util.function.Function;

import org.observe.ObservableValue;
import org.observe.collect.ObservableSet;

import com.google.common.reflect.TypeToken;

/**
 * An ObservableTree whose child collections are sets
 *
 * @param <N> The type of node in the tree
 * @param <V> The type of value in the tree
 */
public interface ObservableSetTree<N, V> extends ObservableTree<N, V> {
	@Override
	ObservableSet<? extends N> getChildren(N node);

	/**
	 * Builds a tree from components
	 *
	 * @param root The root for the tree
	 * @param valueType The value type of the tree
	 * @param getValue The node-value getter for the tree
	 * @param getChildren The children getter for the tree
	 * @return The built tree
	 */
	public static <N, V> ObservableSetTree<N, V> of(ObservableValue<N> root, TypeToken<V> valueType,
		Function<? super N, ? extends ObservableValue<? extends V>> getValue,
			Function<? super N, ? extends ObservableSet<? extends N>> getChildren) {
		return new ComposedSetTree<>(root, valueType, getValue, getChildren);
	}

	/**
	 * @param tree The tree to get the value paths of
	 * @param onlyTerminal Whether to include only terminal paths, or also to include intermediate paths (paths ending in a node that also
	 *        has children not in the path)
	 * @return A collection of immutable, constant lists of values. Each list is the values from the root to a node. The number of lists is
	 *         equal to the total number of nodes (if <code>onlyTerminal</code> is false) or leaf nodes in the tree
	 */
	public static <N, V> ObservableSet<List<V>> valuePathsOf(ObservableSetTree<N, V> tree, boolean onlyTerminal) {
		return valuePathsOf(tree, null, onlyTerminal);
	}

	/**
	 * @param tree The tree to get the value paths of
	 * @param nodeCreator Used to add values into a tree
	 * @param onlyTerminal Whether to include only terminal paths, or also to include intermediate paths (paths ending in a node that also
	 *        has children not in the path)
	 * @return A collection of immutable, constant lists of values. Each list is the values from the root to a node. The number of lists is
	 *         equal to the total number of nodes (if <code>onlyTerminal</code> is false) or leaf nodes in the tree
	 */
	public static <N, V> ObservableSet<List<V>> valuePathsOf(ObservableSetTree<N, V> tree, Function<? super V, ? extends N> nodeCreator,
		boolean onlyTerminal) {
		return new ValuePathSet<>(tree, nodeCreator, onlyTerminal);
	}

	/**
	 * Implements {@link ObservableSetTree#of(ObservableValue, TypeToken, Function, Function)}
	 *
	 * @param <N> The node type of the tree
	 * @param <V> The value type of the tree
	 */
	public static class ComposedSetTree<N, V> extends ComposedTree<N, V> implements ObservableSetTree<N, V> {
		/**
		 * @param root The root for the tree
		 * @param valueType The value type of the tree
		 * @param valueGetter The node-value getter for the tree
		 * @param childrenGetter The children getter for the tree
		 */
		public ComposedSetTree(ObservableValue<N> root, TypeToken<V> valueType,
			Function<? super N, ? extends ObservableValue<? extends V>> valueGetter,
				Function<? super N, ? extends ObservableSet<? extends N>> childrenGetter) {
			super(root, valueType, valueGetter, childrenGetter);
		}

		@Override
		public ObservableSet<? extends N> getChildren(N node) {
			return (ObservableSet<? extends N>) super.getChildren(node);
		}
	}

	/**
	 * Implements {@link ObservableTree#valuePathsOf(ObservableTree, Function, boolean)}
	 *
	 * @param <N> The node type of the tree
	 * @param <V> The value type of the tree
	 */
	public static class ValuePathSet<N, V> extends ValuePathCollection<N, V> implements ObservableSet<List<V>> {
		/**
		 * @param tree The tree to get the value paths of
		 * @param nodeCreator Used to add values into a tree
		 * @param onlyTerminal Whether to include only terminal paths, or also to include intermediate paths (paths ending in a node that
		 *        also has children not in the path)
		 */
		public ValuePathSet(ObservableSetTree<N, V> tree, Function<? super V, ? extends N> nodeCreator, boolean onlyTerminal) {
			super(tree, nodeCreator, onlyTerminal);
		}
	}
}
