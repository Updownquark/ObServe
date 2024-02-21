package org.observe.collect;

import java.util.Collection;
import java.util.Collections;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.qommons.Identifiable;
import org.qommons.Transaction;
import org.qommons.collect.ElementId;
import org.qommons.collect.MutableCollectionElement.StdMsg;

/**
 * A {@link SettableValue settable} {@link ObservableElement}
 *
 * @param <E> The type of the element
 */
public interface SettableElement<E> extends ObservableElement<E>, SettableValue<E> {
	@Override
	default <V extends E> SettableElement<E> withValue(V value, Object cause)
		throws IllegalArgumentException, UnsupportedOperationException {
		SettableValue.super.withValue(value, cause);
		return this;
	}

	@Override
	default ObservableElement<E> unsettable() {
		return new UnsettableElement<>(this);
	}

	/**
	 * @param type The type of the element
	 * @return An element that is always empty
	 */
	static <E> SettableElement<E> empty() {
		return new EmptyElement<>();
	}

	/**
	 * Implements {@link SettableElement#unsettable()}
	 *
	 * @param <E> The type of this element
	 */
	class UnsettableElement<E> extends UnsettableValue<E> implements ObservableElement<E> {
		public UnsettableElement(SettableElement<E> value) {
			super(value);
		}

		@Override
		protected SettableElement<E> getSource() {
			return (SettableElement<E>) super.getSource();
		}

		@Override
		public ElementId getElementId() {
			return getSource().getElementId();
		}

		@Override
		public Observable<ObservableElementEvent<E>> elementChanges() {
			return getSource().elementChanges();
		}
	}

	/**
	 * Implements {@link SettableElement#empty()}
	 *
	 * @param <E> The type of the element
	 */
	class EmptyElement<E> extends AbstractIdentifiable implements SettableElement<E> {
		@Override
		public boolean isLockSupported() {
			return true;
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return Transaction.NONE;
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return Transaction.NONE;
		}

		@Override
		public Collection<Cause> getCurrentCauses() {
			return Collections.emptyList();
		}

		@Override
		public ElementId getElementId() {
			return null;
		}

		@Override
		public Observable<ObservableElementEvent<E>> elementChanges() {
			return Observable.constant(new ObservableElementEvent<>(true, null, null, null, null, null));
		}

		@Override
		public <V extends E> E set(V value, Object cause) throws IllegalArgumentException, UnsupportedOperationException {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public <V extends E> String isAcceptable(V value) {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public ObservableValue<String> isEnabled() {
			return SettableValue.ALWAYS_DISABLED;
		}

		@Override
		public E get() {
			return null;
		}

		@Override
		public long getStamp() {
			return 0;
		}

		@Override
		protected Object createIdentity() {
			return Identifiable.baseId("EmptyElement", this);
		}
	}
}
