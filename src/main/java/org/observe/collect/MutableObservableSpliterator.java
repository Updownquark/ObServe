package org.observe.collect;

import java.util.Comparator;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.qommons.collect.CollectionElement;
import org.qommons.collect.ReversibleElementSpliterator;

import com.google.common.reflect.TypeToken;

public interface MutableObservableSpliterator<E> extends ObservableElementSpliterator<E>, ReversibleElementSpliterator<E> {
	@Override
	default MutableObservableSpliterator<E> trySplit() {
		return null;
	}

	boolean tryAdvanceMutableElement(Consumer<? super MutableObservableElement<E>> action);

	boolean tryReverseMutableElement(Consumer<? super MutableObservableElement<E>> action);

	default void forEachMutableElement(Consumer<? super MutableObservableElement<E>> action) {
		while (tryAdvanceMutableElement(action)) {
		}
	}

	default void forEachReverseMutableElement(Consumer<? super MutableObservableElement<E>> action) {
		while (tryReverseMutableElement(action)) {
		}
	}

	@Override
	default boolean tryAdvanceElement(Consumer<? super CollectionElement<E>> action) {
		return tryAdvanceMutableElement(action);
	}

	@Override
	default boolean tryReverseElement(Consumer<? super CollectionElement<E>> action) {
		return tryReverseMutableElement(el -> action.accept(el));
	}

	@Override
	default boolean tryReverseObservableElement(Consumer<? super ObservableCollectionElement<E>> action) {
		return tryReverseMutableElement(el -> action.accept(el.immutable()));
	}

	@Override
	default void forEachElement(Consumer<? super CollectionElement<E>> action) {
		forEachMutableElement(action);
	}

	@Override
	default boolean tryAdvanceObservableElement(Consumer<? super ObservableCollectionElement<E>> action) {
		return tryAdvanceMutableElement(el -> action.accept(el.immutable()));
	}

	@Override
	default void forEachObservableElement(Consumer<? super ObservableCollectionElement<E>> action) {
		forEachMutableElement(el -> action.accept(el.immutable()));
	}

	@Override
	default boolean tryAdvance(Consumer<? super E> action) {
		return tryAdvanceObservableElement(el -> action.accept(el.get()));
	}

	@Override
	default boolean tryReverse(Consumer<? super E> action) {
		return ObservableElementSpliterator.super.tryReverse(action);
	}

	@Override
	default void forEachRemaining(Consumer<? super E> action) {
		forEachObservableElement(el -> action.accept(el.get()));
	}

	@Override
	default void forEachReverse(Consumer<? super E> action) {
		forEachReverseObservableElement(el -> action.accept(el.get()));
	}

	@Override
	default MutableObservableSpliterator<E> reverse() {
		return new ReversedMutableObservableSpliterator<>(this);
	}

	@Override
	default ObservableElementSpliterator<E> immutable() {
		return new ObservableElementSpliterator<E>() {
			@Override
			public TypeToken<E> getType() {
				return MutableObservableSpliterator.this.getType();
			}

			@Override
			public long estimateSize() {
				return MutableObservableSpliterator.this.estimateSize();
			}

			@Override
			public long getExactSizeIfKnown() {
				return MutableObservableSpliterator.this.getExactSizeIfKnown();
			}

			@Override
			public int characteristics() {
				return MutableObservableSpliterator.this.characteristics();
			}

			@Override
			public Comparator<? super E> getComparator() {
				return MutableObservableSpliterator.this.getComparator();
			}

			@Override
			public boolean tryAdvanceObservableElement(Consumer<? super ObservableCollectionElement<E>> action) {
				return MutableObservableSpliterator.this.tryAdvanceObservableElement(action);
			}

			@Override
			public boolean tryReverseObservableElement(Consumer<? super ObservableCollectionElement<E>> action) {
				return MutableObservableSpliterator.this.tryAdvanceObservableElement(action);
			}

			@Override
			public void forEachObservableElement(Consumer<? super ObservableCollectionElement<E>> action) {
				MutableObservableSpliterator.this.forEachObservableElement(action);
			}

			@Override
			public void forEachReverseObservableElement(Consumer<? super ObservableCollectionElement<E>> action) {
				MutableObservableSpliterator.this.forEachReverseObservableElement(action);
			}

			@Override
			public boolean tryAdvance(Consumer<? super E> action) {
				return MutableObservableSpliterator.this.tryAdvance(action);
			}

			@Override
			public boolean tryReverse(Consumer<? super E> action) {
				return MutableObservableSpliterator.this.tryReverse(action);
			}

			@Override
			public void forEachRemaining(Consumer<? super E> action) {
				MutableObservableSpliterator.this.forEachRemaining(action);
			}

			@Override
			public void forEachReverse(Consumer<? super E> action) {
				MutableObservableSpliterator.this.forEachReverse(action);
			}

			@Override
			public ObservableElementSpliterator<E> trySplit() {
				MutableObservableSpliterator<E> split = MutableObservableSpliterator.this.trySplit();
				return split == null ? null : split.immutable();
			}

			@Override
			public String toString() {
				return MutableObservableSpliterator.this.toString();
			}
		};
	}

	static <E> MutableObservableSpliterator<E> empty(TypeToken<E> type) {
		return new MutableObservableSpliterator<E>() {
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
				return IMMUTABLE | ORDERED | SIZED;
			}

			@Override
			public boolean tryAdvanceMutableElement(Consumer<? super MutableObservableElement<E>> action) {
				return false;
			}

			@Override
			public boolean tryReverseMutableElement(Consumer<? super MutableObservableElement<E>> action) {
				return false;
			}
		};
	}

	interface MutableObservableSpliteratorMap<E, T> extends ObservableElementSpliteratorMap<E, T>, ElementSpliteratorMap<E, T> {
		@Override
		default boolean canFilterValues() {
			return true;
		}

		@Override
		default boolean test(CollectionElement<E> el) {
			return test((ObservableCollectionElement<E>) el);
		}

		@Override
		default boolean test(ObservableCollectionElement<E> el) {
			return test(el.get());
		}

		@Override
		default long filterEstimatedSize(long srcSize) {
			return ElementSpliteratorMap.super.filterEstimatedSize(srcSize);
		}

		@Override
		default long filterExactSize(long srcSize) {
			return ElementSpliteratorMap.super.filterExactSize(srcSize);
		}

		@Override
		default int modifyCharacteristics(int srcChars) {
			return ElementSpliteratorMap.super.modifyCharacteristics(srcChars);
		}

		@Override
		default Comparator<? super T> mapComparator(Comparator<? super E> srcCompare) {
			return null;
		}

		default ElementId mapId(ElementId id) {
			return id;
		}
	}

	default <T> MutableObservableSpliterator<T> map(MutableObservableSpliteratorMap<E, T> map) {
		return new MappedMutableObservableSpliterator<>(this, map);
	}

	class ReversedMutableObservableSpliterator<E> extends ReversedElementSpliterator<E> implements MutableObservableSpliterator<E> {
		public ReversedMutableObservableSpliterator(MutableObservableSpliterator<E> wrap) {
			super(wrap);
		}

		@Override
		protected MutableObservableSpliterator<E> getWrapped() {
			return (MutableObservableSpliterator<E>) super.getWrapped();
		}

		@Override
		public boolean tryAdvanceMutableElement(Consumer<? super MutableObservableElement<E>> action) {
			return getWrapped().tryReverseMutableElement(action);
		}

		@Override
		public boolean tryReverseMutableElement(Consumer<? super MutableObservableElement<E>> action) {
			return getWrapped().tryAdvanceMutableElement(action);
		}

		@Override
		public boolean tryAdvanceObservableElement(Consumer<? super ObservableCollectionElement<E>> action) {
			return getWrapped().tryReverseObservableElement(action);
		}

		@Override
		public boolean tryReverseObservableElement(Consumer<? super ObservableCollectionElement<E>> action) {
			return getWrapped().tryAdvanceObservableElement(action);
		}

		@Override
		public MutableObservableSpliterator<E> reverse() {
			return getWrapped();
		}

		@Override
		public MutableObservableSpliterator<E> trySplit() {
			MutableObservableSpliterator<E> split = getWrapped().trySplit();
			return split == null ? null : split.reverse();
		}
	}

	class MappedMutableObservableSpliterator<E, T> extends MappedElementSpliterator<E, T> implements MutableObservableSpliterator<T> {
		private final MappedImmutableElement<E, T> theImmutableElement;

		public MappedMutableObservableSpliterator(MutableObservableSpliterator<E> source, MutableObservableSpliteratorMap<E, T> map) {
			super(source, map);
			theImmutableElement = createImmutableElement();
		}

		@Override
		protected MutableObservableSpliterator<E> getSource() {
			return (MutableObservableSpliterator<E>) super.getSource();
		}

		@Override
		protected MutableObservableSpliteratorMap<E, T> getMap() {
			return (MutableObservableSpliteratorMap<E, T>) super.getMap();
		}

		@Override
		protected MappedObservableElement<E, T> getElement() {
			return (MappedObservableElement<E, T>) super.getElement();
		}

		@Override
		protected MappedObservableElement<E, T> createElement() {
			return new MappedObservableElement<>(getMap(), this::getType);
		}

		protected MappedImmutableElement<E, T> getImmutableElement() {
			return theImmutableElement;
		}

		protected MappedImmutableElement<E, T> createImmutableElement() {
			return new MappedImmutableElement<>(getMap(), this::getType);
		}

		@Override
		public boolean tryAdvanceMutableElement(Consumer<? super MutableObservableElement<T>> action) {
			while (getSource().tryAdvanceMutableElement(el -> {
				getElement().setSource(el);
				if (getElement().isAccepted())
					action.accept(getElement());
			})) {
				if (getElement().isAccepted())
					return true;
			}
			return false;
		}

		@Override
		public boolean tryReverseMutableElement(Consumer<? super MutableObservableElement<T>> action) {
			while (getSource().tryReverseMutableElement(el -> {
				getElement().setSource(el);
				if (getElement().isAccepted())
					action.accept(getElement());
			})) {
				if (getElement().isAccepted())
					return true;
			}
			return false;
		}

		@Override
		public void forEachMutableElement(Consumer<? super MutableObservableElement<T>> action) {
			getSource().forEachMutableElement(el -> {
				getElement().setSource(el);
				if (getElement().isAccepted())
					action.accept(getElement());
			});
		}

		@Override
		public void forEachReverseMutableElement(Consumer<? super MutableObservableElement<T>> action) {
			getSource().forEachReverseMutableElement(el -> {
				getElement().setSource(el);
				if (getElement().isAccepted())
					action.accept(getElement());
			});
		}

		@Override
		public boolean tryAdvanceObservableElement(Consumer<? super ObservableCollectionElement<T>> action) {
			while (getSource().tryAdvanceObservableElement(el -> {
				theImmutableElement.setSource(el);
				if (theImmutableElement.isAccepted())
					action.accept(theImmutableElement);
			})) {
				if (theImmutableElement.isAccepted())
					return true;
			}
			return false;
		}

		@Override
		public boolean tryReverseObservableElement(Consumer<? super ObservableCollectionElement<T>> action) {
			while (getSource().tryReverseObservableElement(el -> {
				theImmutableElement.setSource(el);
				if (theImmutableElement.isAccepted())
					action.accept(theImmutableElement);
			})) {
				if (theImmutableElement.isAccepted())
					return true;
			}
			return false;
		}

		@Override
		public void forEachObservableElement(Consumer<? super ObservableCollectionElement<T>> action) {
			getSource().forEachObservableElement(el -> {
				theImmutableElement.setSource(el);
				if (theImmutableElement.isAccepted())
					action.accept(theImmutableElement);
			});
		}

		@Override
		public void forEachReverseObservableElement(Consumer<? super ObservableCollectionElement<T>> action) {
			getSource().forEachReverseObservableElement(el -> {
				theImmutableElement.setSource(el);
				if (theImmutableElement.isAccepted())
					action.accept(theImmutableElement);
			});
		}

		@Override
		public MutableObservableSpliterator<T> trySplit() {
			return (MutableObservableSpliterator<T>) super.trySplit();
		}

		@Override
		public ObservableElementSpliterator<T> immutable() {
			return MutableObservableSpliterator.super.immutable();
		}

		protected static class MappedObservableElement<E, T> extends MappedElementSpliterator.MappedElement<E, T>
		implements MutableObservableElement<T> {
			protected MappedObservableElement(MutableObservableSpliteratorMap<E, T> map, Supplier<TypeToken<T>> type) {
				super(map, type);
			}

			@Override
			protected void setSource(CollectionElement<E> sourceEl) {
				if (!(sourceEl instanceof MutableObservableElement))
					throw new IllegalArgumentException("Element is not mutable");
				super.setSource(sourceEl);
			}

			@Override
			protected MutableObservableElement<E> getSourceEl() {
				return (MutableObservableElement<E>) super.getSourceEl();
			}

			@Override
			public ElementId getElementId() {
				return getSourceEl().getElementId();
			}

			@Override
			protected boolean isAccepted() {
				return super.isAccepted();
			}
		}

		protected static class MappedImmutableElement<E, T> implements ObservableCollectionElement<T> {
			private ObservableCollectionElement<E> theSourceEl;
			private final MutableObservableSpliteratorMap<E, T> theMap;
			private final Supplier<TypeToken<T>> theType;
			private boolean isAccepted;

			protected MappedImmutableElement(MutableObservableSpliteratorMap<E, T> map, Supplier<TypeToken<T>> type) {
				theMap = map;
				theType = type;
			}

			protected void setSource(ObservableCollectionElement<E> sourceEl) {
				isAccepted = theMap.test(sourceEl);
				theSourceEl = isAccepted ? sourceEl : null;
			}

			protected ObservableCollectionElement<E> getSourceEl() {
				return theSourceEl;
			}

			protected boolean isAccepted() {
				return isAccepted;
			}

			@Override
			public TypeToken<T> getType() {
				return theType.get();
			}

			@Override
			public T get() {
				return theMap.map(theSourceEl.get());
			}

			@Override
			public ElementId getElementId() {
				return theSourceEl.getElementId();
			}
		}
	}
}
