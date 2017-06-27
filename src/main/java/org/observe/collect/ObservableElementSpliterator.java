package org.observe.collect;

import java.util.Comparator;
import java.util.Spliterator;
import java.util.function.Consumer;

import org.qommons.collect.ElementSpliterator;

import com.google.common.reflect.TypeToken;

/**
 * An {@link ElementSpliterator} that supplies {@link ObservableCollectionElement}s
 *
 * @param <E> The type of values the spliterator provides
 */
public interface ObservableElementSpliterator<E> extends Spliterator<E> {
	/** @return The type of values in this spliterator */
	TypeToken<E> getType();

	/**
	 * Iterates through each element covered by this ElementSpliterator
	 *
	 * @param action Accepts each element in sequence. Unless a sub-type of ElementSpliterator or a specific supplier of a
	 *        ElementSpliterator advertises otherwise, the element object may only be treated as valid until the next element is returned
	 *        and also should not be kept longer than the reference to the ElementSpliterator.
	 * @return false if no remaining elements existed upon entry to this method, else true.
	 */
	boolean tryAdvanceObservableElement(Consumer<? super ObservableCollectionElement<E>> action);

	/**
	 * Operates on each element remaining in this ElementSpliterator
	 *
	 * @param action The action to perform on each element
	 */
	default void forEachObservableElement(Consumer<? super ObservableCollectionElement<E>> action) {
		while (tryAdvanceObservableElement(action)) {
		}
	}

	@Override
	default boolean tryAdvance(Consumer<? super E> action) {
		return tryAdvanceObservableElement(el -> action.accept(el.get()));
	}

	@Override
	default void forEachRemaining(Consumer<? super E> action) {
		forEachObservableElement(el -> action.accept(el.get()));
	}

	@Override
	ObservableElementSpliterator<E> trySplit();

	interface ObservableElementSpliteratorMap<E, T> {
		TypeToken<T> getType();

		T map(E value);

		default boolean canFilterValues() {
			return true;
		}

		boolean test(E srcValue);

		default boolean test(ObservableCollectionElement<E> el) {
			return test(el.get());
		}

		default long filterEstimatedSize(long srcSize) {
			return srcSize;
		}

		default long filterExactSize(long srcSize) {
			return -1;
		}
		default int modifyCharacteristics(int srcChars) {
			return srcChars;
		}
		default Comparator<? super T> mapComparator(Comparator<? super E> srcCompare) {
			return null;
		}
	}

	default <T> ObservableElementSpliterator<T> map(ObservableElementSpliteratorMap<E, T> map) {
		return new MappedObservableElementSpliterator<>(this, map);
	}

	/**
	 * @param <E> The compile-time type for the spliterator
	 * @param type The type for the ElementSpliterator
	 * @return An empty ElementSpliterator of the given type
	 */
	static <E> ObservableElementSpliterator<E> empty(TypeToken<E> type) {
		return new ObservableElementSpliterator<E>() {
			@Override
			public TypeToken<E> getType() {
				return type;
			}

			@Override
			public long estimateSize() {
				return 0;
			}

			@Override
			public long getExactSizeIfKnown() {
				return 0;
			}

			@Override
			public int characteristics() {
				return Spliterator.IMMUTABLE | Spliterator.SIZED;
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

	class MappedObservableElementSpliterator<E, T> implements ObservableElementSpliterator<T> {
		private final ObservableElementSpliterator<E> theSource;
		private final ObservableElementSpliteratorMap<E, T> theMap;

		private TypeToken<T> theType;
		private final MappedElement theElement;

		public MappedObservableElementSpliterator(ObservableElementSpliterator<E> source, ObservableElementSpliteratorMap<E, T> map) {
			theSource = source;
			theMap = map;

			theElement = new MappedElement();
		}

		@Override
		public TypeToken<T> getType() {
			if (theType == null)
				theType = theMap.getType();
			return theType;
		}

		@Override
		public long estimateSize() {
			return theMap.filterEstimatedSize(theSource.estimateSize());
		}

		@Override
		public long getExactSizeIfKnown() {
			return theMap.filterExactSize(theSource.getExactSizeIfKnown());
		}

		@Override
		public int characteristics() {
			return theMap.modifyCharacteristics(theSource.characteristics());
		}

		@Override
		public Comparator<? super T> getComparator() {
			return theMap.mapComparator(theSource.getComparator());
		}

		@Override
		public boolean tryAdvanceObservableElement(Consumer<? super ObservableCollectionElement<T>> action) {
			while (theSource.tryAdvanceObservableElement(el -> {
				theElement.setSource(el);
				if (theElement.isAccepted())
					action.accept(theElement);
			})) {
				if (theElement.isAccepted())
					return true;
			}
			return false;
		}

		@Override
		public void forEachObservableElement(Consumer<? super ObservableCollectionElement<T>> action) {
			theSource.forEachObservableElement(el -> {
				theElement.setSource(el);
				if (theElement.isAccepted())
					action.accept(theElement);
			});
		}

		@Override
		public boolean tryAdvance(Consumer<? super T> action) {
			if (theMap.canFilterValues()) {
				boolean[] accepted = new boolean[1];
				while (!accepted[0] && theSource.tryAdvance(v -> {
					accepted[0] = theMap.test(v);
					if (accepted[0])
						action.accept(theMap.map(v));
				})) {
				}
				return accepted[0];
			} else
				return ObservableElementSpliterator.super.tryAdvance(action);
		}

		@Override
		public void forEachRemaining(Consumer<? super T> action) {
			if (theMap.canFilterValues()) {
				theSource.forEachRemaining(v -> {
					if (theMap.test(v))
						action.accept(theMap.map(v));
				});
			} else
				ObservableElementSpliterator.super.forEachRemaining(action);
		}

		@Override
		public ObservableElementSpliterator<T> trySplit() {
			ObservableElementSpliterator<E> split = theSource.trySplit();
			return split == null ? null : split.map(theMap);
		}

		class MappedElement implements ObservableCollectionElement<T> {
			private ObservableCollectionElement<E> theSourceEl;
			private boolean isAccepted;

			void setSource(ObservableCollectionElement<E> sourceEl) {
				isAccepted = theMap.test(sourceEl);
				theSourceEl = isAccepted ? sourceEl : null;
			}

			boolean isAccepted() {
				return isAccepted;
			}

			@Override
			public TypeToken<T> getType() {
				return MappedObservableElementSpliterator.this.getType();
			}

			@Override
			public ElementId getElementId() {
				return theSourceEl.getElementId();
			}

			@Override
			public T get() {
				return theMap.map(theSourceEl.get());
			}

			@Override
			public String toString() {
				return String.valueOf(get());
			}
		}
	}
}
