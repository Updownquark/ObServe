package org.observe.collect;

import org.qommons.collect.MutableElementHandle;
import org.qommons.collect.ElementId;
import org.qommons.value.Value;

import com.google.common.reflect.TypeToken;

/**
 * A {@link MutableElementHandle} that additionally provides the {@link ElementId element ID} for the element
 *
 * @param <E> The type of value in the element
 */
public interface ObservableCollectionElement<E> extends Value<E> {
	/** @return The ID of this element */
	ElementId getElementId();

	default ObservableCollectionElement<E> reverse() {
		return new ReversedCollectionElement<>(this);
	}

	class ReversedCollectionElement<E> implements ObservableCollectionElement<E> {
		private final ObservableCollectionElement<E> theWrapped;

		public ReversedCollectionElement(ObservableCollectionElement<E> wrapped) {
			theWrapped = wrapped;
		}

		protected ObservableCollectionElement<E> getWrapped() {
			return theWrapped;
		}

		@Override
		public TypeToken<E> getType() {
			return theWrapped.getType();
		}

		@Override
		public E get() {
			return theWrapped.get();
		}

		@Override
		public ElementId getElementId() {
			return theWrapped.getElementId().reverse();
		}

		@Override
		public ObservableCollectionElement<E> reverse() {
			return theWrapped;
		}
	}
}
