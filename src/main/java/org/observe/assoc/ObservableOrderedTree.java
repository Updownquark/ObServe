package org.observe.assoc;

import java.util.function.Function;

import org.observe.ObservableValue;
import org.observe.collect.ObservableIndexedCollection;

import com.google.common.reflect.TypeToken;

/**
 * An ObservableTree whose child collections are all ordered
 * 
 * @param <N> The node type of the tree
 * @param <V> The value type of the tree
 */
public interface ObservableOrderedTree<N, V> extends ObservableTree<N, V> {
	@Override
	ObservableIndexedCollection<? extends N> getChildren(N node);

	/**
	 * Builds a tree from components
	 *
	 * @param root The root for the tree
	 * @param valueType The value type of the tree
	 * @param getValue The node-value getter for the tree
	 * @param getChildren The children getter for the tree
	 * @return The built tree
	 */
	public static <N, V> ObservableTree<N, V> of(ObservableValue<N> root, TypeToken<V> valueType,
		Function<? super N, ? extends ObservableValue<? extends V>> getValue,
			Function<? super N, ? extends ObservableIndexedCollection<? extends N>> getChildren) {
		return new ComposedOrderedTree<>(root, valueType, getValue, getChildren);
	}

	/**
	 * Implements {@link ObservableOrderedTree#of(ObservableValue, TypeToken, Function, Function)}
	 * 
	 * @param <N> The node type of the tree
	 * @param <V> The value type of the tree
	 */
	public static class ComposedOrderedTree<N, V> extends ComposedTree<N, V> implements ObservableOrderedTree<N, V> {
		/**
		 * @param root The root for the tree
		 * @param valueType The value type of the tree
		 * @param valueGetter The node-value getter for the tree
		 * @param childrenGetter The children getter for the tree
		 */
		public ComposedOrderedTree(ObservableValue<N> root, TypeToken<V> valueType,
			Function<? super N, ? extends ObservableValue<? extends V>> valueGetter,
				Function<? super N, ? extends ObservableIndexedCollection<? extends N>> childrenGetter) {
			super(root, valueType, valueGetter, childrenGetter);
		}

		@Override
		public ObservableIndexedCollection<? extends N> getChildren(N node) {
			return (ObservableIndexedCollection<? extends N>) super.getChildren(node);
		}
	}
}
