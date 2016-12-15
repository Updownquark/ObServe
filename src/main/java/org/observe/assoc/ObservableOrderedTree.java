package org.observe.assoc;

import java.util.function.Function;

import org.observe.ObservableValue;
import org.observe.collect.ObservableOrderedCollection;

import com.google.common.reflect.TypeToken;

public interface ObservableOrderedTree<N, V> extends ObservableTree<N, V> {
	@Override
	ObservableOrderedCollection<? extends N> getChildren(N node);

	public static <N, V> ObservableTree<N, V> of(ObservableValue<N> root, TypeToken<V> valueType,
		Function<? super N, ? extends ObservableValue<? extends V>> getValue,
		Function<? super N, ? extends ObservableOrderedCollection<? extends N>> getChildren) {
		return new ComposedOrderedTree<>(root, valueType, getValue, getChildren);
	}

	public static class ComposedOrderedTree<N, V> extends ComposedTree<N, V> implements ObservableOrderedTree<N, V> {
		public ComposedOrderedTree(ObservableValue<N> root, TypeToken<V> valueType,
			Function<? super N, ? extends ObservableValue<? extends V>> valueGetter,
				Function<? super N, ? extends ObservableOrderedCollection<? extends N>> childrenGetter) {
			super(root, valueType, valueGetter, childrenGetter);
		}

		@Override
		public ObservableOrderedCollection<? extends N> getChildren(N node) {
			return (ObservableOrderedCollection<? extends N>) super.getChildren(node);
		}
	}
}
