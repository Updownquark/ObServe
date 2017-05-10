package org.observe.collect;

import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementSpliterator;

import com.google.common.reflect.TypeToken;

public interface ObservableElementSpliterator<E> extends ElementSpliterator<E> {
	boolean tryAdvanceObservableElement(Consumer<? super ObservableCollectionElement<E>> action);

	default void forEachObservableElement(Consumer<? super ObservableCollectionElement<E>> action) {
		while (tryAdvanceObservableElement(action)) {
		}
	}

	@Override
	default boolean tryAdvanceElement(Consumer<? super CollectionElement<E>> action) {
		return tryAdvanceObservableElement(action);
	}

	@Override
	default void forEachElement(Consumer<? super CollectionElement<E>> action) {
		forEachObservableElement(action);
	}

	@Override
	ObservableElementSpliterator<E> trySplit();

	/**
	 * @param <E> The compile-time type for the spliterator
	 * @param type The type for the ElementSpliterator
	 * @return An empty ElementSpliterator of the given type
	 */
	static <E> ObservableElementSpliterator<E> empty(TypeToken<E> type) {
		return new ObservableElementSpliterator<E>() {
			@Override
			public long estimateSize() {
				return 0;
			}

			@Override
			public int characteristics() {
				return Spliterator.IMMUTABLE | Spliterator.SIZED;
			}

			@Override
			public TypeToken<E> getType() {
				return type;
			}

			@Override
			public boolean tryAdvanceObservableElement(Consumer<? super ObservableCollectionElement<E>> action) {
				return false;
			}

			@Override
			public ObservableElementSpliterator<E> trySplit() {
				return null;
			}
		};
	}

	/**
	 * A ElementSpliterator whose elements are the result of some filter-map operation on a vanilla {@link Spliterator}'s elements
	 *
	 * @param <T> The type of elements in the wrapped Spliterator
	 * @param <V> The type of this ElementSpliterator's elements
	 */
	class SimpleObservableSpliterator<T, V> extends SimpleSpliterator<T, V> implements ObservableElementSpliterator<V> {
		public SimpleObservableSpliterator(Spliterator<T> wrap, TypeToken<V> type,
			Supplier<? extends Function<? super T, ? extends ObservableCollectionElement<V>>> map) {
			super(wrap, type, map);
		}

		@Override
		protected Supplier<? extends Function<? super T, ? extends ObservableCollectionElement<V>>> getMap() {
			return (Supplier<? extends Function<? super T, ? extends ObservableCollectionElement<V>>>) super.getMap();
		}

		@Override
		public boolean tryAdvanceObservableElement(Consumer<? super ObservableCollectionElement<V>> action) {
			return tryAdvanceElement(el -> action.accept((ObservableCollectionElement<V>) el));
		}

		@Override
		public void forEachObservableElement(Consumer<? super ObservableCollectionElement<V>> action) {
			forEachElement(el -> action.accept((ObservableCollectionElement<V>) el));
		}

		@Override
		public ObservableElementSpliterator<V> trySplit() {
			Spliterator<T> split = getWrapped().trySplit();
			if (split == null)
				return null;
			return new SimpleObservableSpliterator<>(split, getType(), getMap());
		}
	}

	/**
	 * A ElementSpliterator whose elements are the result of some filter-map operation on another ElementSpliterator's elements
	 *
	 * @param <T> The type of elements in the wrapped ElementSpliterator
	 * @param <V> The type of this ElementSpliterator's elements
	 */
	class WrappingObservableSpliterator<T, V> extends WrappingSpliterator<T, V> implements ObservableElementSpliterator<V> {
		public WrappingObservableSpliterator(ObservableElementSpliterator<? extends T> wrap, TypeToken<V> type,
			Supplier<? extends Function<? super ObservableCollectionElement<? extends T>, ? extends ObservableCollectionElement<V>>> map) {
			super(wrap, type, (Supplier<? extends Function<? super CollectionElement<? extends T>, ? extends CollectionElement<V>>>) map);
		}

		@Override
		protected ObservableElementSpliterator<? extends T> getWrapped() {
			return (ObservableElementSpliterator<? extends T>) super.getWrapped();
		}

		@Override
		protected Supplier<? extends Function<? super CollectionElement<? extends T>, ? extends ObservableCollectionElement<V>>> getMap() {
			return (Supplier<? extends Function<? super CollectionElement<? extends T>, ? extends ObservableCollectionElement<V>>>) super.getMap();
		}

		@Override
		protected Function<? super CollectionElement<? extends T>, ? extends ObservableCollectionElement<V>> getInstanceMap() {
			return (Function<? super CollectionElement<? extends T>, ? extends ObservableCollectionElement<V>>) super.getInstanceMap();
		}

		@Override
		public boolean tryAdvanceObservableElement(Consumer<? super ObservableCollectionElement<V>> action) {
			return tryAdvanceElement(el -> action.accept((ObservableCollectionElement<V>) el));
		}

		@Override
		public void forEachObservableElement(Consumer<? super ObservableCollectionElement<V>> action) {
			forEachElement(el -> action.accept((ObservableCollectionElement<V>) el));
		}

		@Override
		public ObservableElementSpliterator<V> trySplit() {
			ObservableElementSpliterator<? extends T> wrapSplit = getWrapped().trySplit();
			if (wrapSplit == null)
				return null;
			return new WrappingObservableSpliterator<>(wrapSplit, getType(), getMap());
		}
	}

	/**
	 * An element returned from {@link ObservableElementSpliterator.WrappingObservableSpliterator}
	 *
	 * @param <T> The type of value in the element wrapped by this element
	 * @param <V> The type of this element
	 */
	abstract class WrappingObservableElement<T, V> extends WrappingElement<T, V> implements ObservableCollectionElement<V> {
		public WrappingObservableElement(TypeToken<V> type, ObservableCollectionElement<? extends T>[] wrapped) {
			super(type, wrapped);
		}

		@Override
		protected ObservableCollectionElement<? extends T> getWrapped() {
			return (ObservableCollectionElement<? extends T>) super.getWrapped();
		}

		@Override
		public Object getElementId() {
			return getWrapped().getElementId();
		}
	}
}
