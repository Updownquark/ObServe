package org.observe.assoc;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import org.observe.ObservableValue;
import org.observe.collect.ObservableSet;
import org.qommons.Equalizer;

import com.google.common.reflect.TypeToken;

public interface ObservableSetTree<N, V> extends ObservableTree<N, V> {
	@Override
	ObservableSet<? extends N> getChildren(N node);

	public static <N, V> ObservableSetTree<N, V> of(ObservableValue<N> root, TypeToken<V> valueType,
		Function<? super N, ? extends ObservableValue<? extends V>> getValue,
			Function<? super N, ? extends ObservableSet<? extends N>> getChildren) {
		return new ComposedSetTree<>(root, valueType, getValue, getChildren);
	}

	public static class ComposedSetTree<N, V> extends ComposedTree<N, V> implements ObservableSetTree<N, V> {
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

	public static <N, V> ObservableSet<List<V>> valuePathsOf(ObservableSetTree<N, V> tree, boolean onlyTerminal) {
		return valuePathsOf(tree, null, onlyTerminal);
	}

	public static <N, V> ObservableSet<List<V>> valuePathsOf(ObservableSetTree<N, V> tree, Function<? super V, ? extends N> nodeCreator,
		boolean onlyTerminal) {
		return new ValuePathSet<>(tree, nodeCreator, onlyTerminal);
	}

	public static class ValuePathSet<N, V> extends ValuePathCollection<N, V> implements ObservableSet<List<V>> {
		public ValuePathSet(ObservableSetTree<N, V> tree, Function<? super V, ? extends N> nodeCreator, boolean onlyTerminal) {
			super(tree, nodeCreator, onlyTerminal);
		}

		@Override
		public Equalizer getEqualizer() {
			return Objects::equals;
		}
	}
}
