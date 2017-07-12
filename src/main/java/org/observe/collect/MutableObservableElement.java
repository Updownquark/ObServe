package org.observe.collect;

import org.qommons.collect.CollectionElement;
import org.qommons.collect.ReversibleElementSpliterator;

import com.google.common.reflect.TypeToken;

/**
 * A {@link CollectionElement} that additionally provides the {@link ElementId element ID} for the element
 *
 * @param <E> The type of value in the element
 */
public interface MutableObservableElement<E> extends ObservableCollectionElement<E>, CollectionElement<E> {
	/** @return An immutable observable element backed by this mutable element's data */
	default ObservableCollectionElement<E> immutable() {
		return new ObservableCollectionElement<E>() {
			@Override
			public TypeToken<E> getType() {
				return MutableObservableElement.this.getType();
			}

			@Override
			public E get() {
				return MutableObservableElement.this.get();
			}

			@Override
			public ElementId getElementId() {
				return MutableObservableElement.this.getElementId();
			}

			@Override
			public String toString() {
				return MutableObservableElement.this.toString();
			}
		};
	}

	@Override
	default MutableObservableElement<E> reverse() {
		return new ReversedMutableElement<>(this);
	}

	class ReversedMutableElement<E> extends ReversibleElementSpliterator.ReversedCollectionElement<E>
		implements MutableObservableElement<E> {
		public ReversedMutableElement(MutableObservableElement<E> wrapped) {
			super(wrapped);
		}

		@Override
		protected MutableObservableElement<E> getWrapped() {
			return (MutableObservableElement<E>) super.getWrapped();
		}

		@Override
		public ElementId getElementId() {
			return getWrapped().getElementId().reverse();
		}
	}
}
