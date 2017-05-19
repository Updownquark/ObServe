package org.observe.collect;

import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.qommons.collect.CollectionElement;
import org.qommons.collect.ReversibleSpliterator;

import com.google.common.reflect.TypeToken;

/**
 * An {@link ObservableElementSpliterator} that is also {@link ReversibleSpliterator reversible}
 * 
 * @param <E> The type of elements that this spliterator provides
 */
public interface ObservableReversibleSpliterator<E> extends ObservableElementSpliterator<E>, ReversibleSpliterator<E> {
	/**
	 * Gets the previous element in the spliterator, if available
	 *
	 * @param action The action to perform on the element
	 * @return Whether there was a previous element in the spliterator
	 */
	boolean tryReverseObservableElement(Consumer<? super ObservableCollectionElement<E>> action);

	/** @param action The action to perform on all the previous elements in the spliterator */
	default void forEachReverseObservableElement(Consumer<? super ObservableCollectionElement<E>> action) {
		while (tryReverseObservableElement(action)) {
		}
	}

	@Override
	default boolean tryReverseElement(Consumer<? super CollectionElement<E>> action) {
		return tryReverseObservableElement(action);
	}

	@Override
	default void forEachReverseElement(Consumer<? super CollectionElement<E>> action) {
		forEachReverseObservableElement(action);
	}
	@Override
	ObservableReversibleSpliterator<E> trySplit();

	/**
	 * @param <E> The compile-time type for the spliterator
	 * @param type The type for the ElementSpliterator
	 * @return An empty ElementSpliterator of the given type
	 */
	static <E> ObservableReversibleSpliterator<E> empty(TypeToken<E> type) {
		return new ObservableReversibleSpliterator<E>() {
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
			public boolean tryReverseObservableElement(Consumer<? super ObservableCollectionElement<E>> action) {
				return false;
			}

			@Override
			public ObservableReversibleSpliterator<E> trySplit() {
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
	class SimpleReversibleObservableSpliterator<T, V> extends SimpleObservableSpliterator<T, V>
	implements ObservableReversibleSpliterator<V> {
		public SimpleReversibleObservableSpliterator(ReversibleSpliterator<T> wrap, TypeToken<V> type,
			Supplier<? extends Function<? super T, ? extends ObservableCollectionElement<V>>> map) {
			super(wrap, type, map);
		}

		@Override
		protected ReversibleSpliterator<T> getWrapped() {
			return (ReversibleSpliterator<T>) super.getWrapped();
		}

		@Override
		public boolean tryReverseObservableElement(Consumer<? super ObservableCollectionElement<V>> action) {
			return tryReverseElement(el -> action.accept((ObservableCollectionElement<V>) el));
		}

		@Override
		public void forEachReverseObservableElement(Consumer<? super ObservableCollectionElement<V>> action) {
			forEachReverseElement(el -> action.accept((ObservableCollectionElement<V>) el));
		}

		@Override
		public ObservableReversibleSpliterator<V> trySplit() {
			ReversibleSpliterator<T> split = getWrapped().trySplit();
			if (split == null)
				return null;
			return new SimpleReversibleObservableSpliterator<>(split, getType(), getMap());
		}
	}

	/**
	 * A ElementSpliterator whose elements are the result of some filter-map operation on another ElementSpliterator's elements
	 *
	 * @param <T> The type of elements in the wrapped ElementSpliterator
	 * @param <V> The type of this ElementSpliterator's elements
	 */
	class WrappingReversibleObservableSpliterator<T, V> extends WrappingObservableSpliterator<T, V>
	implements ObservableReversibleSpliterator<V> {
		public WrappingReversibleObservableSpliterator(ObservableReversibleSpliterator<? extends T> wrap, TypeToken<V> type,
			Supplier<? extends Function<? super ObservableCollectionElement<? extends T>, ? extends ObservableCollectionElement<V>>> map) {
			super(wrap, type, map);
		}

		@Override
		protected ObservableReversibleSpliterator<? extends T> getWrapped() {
			return (ObservableReversibleSpliterator<? extends T>) super.getWrapped();
		}

		@Override
		public boolean tryReverseObservableElement(Consumer<? super ObservableCollectionElement<V>> action) {
			return tryAdvanceElement(el -> action.accept((ObservableCollectionElement<V>) el));
		}

		@Override
		public void forEachReverseObservableElement(Consumer<? super ObservableCollectionElement<V>> action) {
			forEachElement(el -> action.accept((ObservableCollectionElement<V>) el));
		}

		@Override
		public ObservableReversibleSpliterator<V> trySplit() {
			ObservableReversibleSpliterator<? extends T> wrapSplit = getWrapped().trySplit();
			if (wrapSplit == null)
				return null;
			return new WrappingReversibleObservableSpliterator<>(wrapSplit, getType(), getMap());
		}
	}
}