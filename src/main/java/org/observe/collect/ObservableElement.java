package org.observe.collect;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.Subscription;
import org.qommons.Identifiable;
import org.qommons.Transaction;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;

import com.google.common.reflect.TypeToken;

/**
 * An {@link ObservableValue} whose state reflects a (possibly absent) element in a {@link ObservableCollection collection}
 *
 * @param <T> The type of value in the element
 */
public interface ObservableElement<T> extends ObservableValue<T> {
	/**
	 * A change event fired from an {@link ObservableElement}
	 *
	 * @param <T> The type of value in the element
	 */
	class ObservableElementEvent<T> extends ObservableValueEvent<T> {
		private final ElementId theOldElement;
		private final ElementId theNewElement;

		public ObservableElementEvent(TypeToken<T> type, boolean initial, ElementId oldElement, ElementId newElement, T oldValue,
			T newValue, Object cause) {
			super(type, initial, oldValue, newValue, cause);
			theOldElement = oldElement;
			theNewElement = newElement;
		}

		public ElementId getOldElement() {
			return theOldElement;
		}

		public ElementId getNewElement() {
			return theNewElement;
		}
	}

	/**
	 * @return The ID of the {@link CollectionElement#getElementId() element} in the collection that this value's state currently reflects.
	 *         May be null if no element is currently reflected by this value's state.
	 */
	ElementId getElementId();

	/** @return An observable of {@link ObservableValueEvent}s to alert a consumer to changes to this element's state */
	default Observable<ObservableElementEvent<T>> elementChangesNoInit() {
		return elementChanges().noInit();
	}

	/** @return An observable of {@link ObservableValueEvent}s to alert a consumer to changes to this element's state */
	Observable<ObservableElementEvent<T>> elementChanges();

	@Override
	default Observable<ObservableValueEvent<T>> changes() {
		return (Observable<ObservableValueEvent<T>>) (Observable<?>) elementChanges();
	}

	@Override
	default Observable<ObservableValueEvent<T>> noInitChanges() {
		return (Observable<ObservableValueEvent<T>>) (Observable<?>) elementChangesNoInit();
	}

	/**
	 * Creates an event to alert a consumer to this element's initial state upon listening
	 *
	 * @param element The ID of the current state's element
	 * @param value The value of the current state
	 * @param cause The initial cause (usually null)
	 * @return The initial event to fire
	 */
	default ObservableElementEvent<T> createInitialEvent(ElementId element, T value, Object cause) {
		return new ObservableElementEvent<>(getType(), true, null, element, null, value, cause);
	}

	/**
	 * Creates an event to alert a consumer to a change in this element's state
	 *
	 * @param oldElement The ID of the previous state's element
	 * @param oldVal The value of the previous state
	 * @param newElement The ID of the current state's element
	 * @param newVal The value of the current state
	 * @param cause The cause of the change
	 * @return The change event to fire
	 */
	default ObservableElementEvent<T> createChangeEvent(ElementId oldElement, T oldVal, ElementId newElement, T newVal, Object cause) {
		return new ObservableElementEvent<>(getType(), false, oldElement, newElement, oldVal, newVal, cause);
	}

	/**
	 * @param <T> The type of the element
	 * @param type The type of the element
	 * @return An element that is always empty ({@link #get() value}==null, {@link #getElementId() element}==null)
	 */
	static <T> ObservableElement<T> empty(TypeToken<T> type) {
		class EmptyElement extends AbstractIdentifiable implements ObservableElement<T> {
			@Override
			public TypeToken<T> getType() {
				return type;
			}

			@Override
			public long getStamp() {
				return 0;
			}

			@Override
			protected Object createIdentity() {
				return Identifiable.baseId("empty-element", "empty-element");
			}

			@Override
			public T get() {
				return null;
			}

			@Override
			public Transaction lock() {
				return Transaction.NONE;
			}

			@Override
			public ElementId getElementId() {
				return null;
			}

			@Override
			public Observable<ObservableElementEvent<T>> elementChanges() {
				class ElementChanges extends AbstractIdentifiable implements Observable<ObservableElementEvent<T>> {
					@Override
					protected Object createIdentity() {
						return Identifiable.wrap(EmptyElement.this.getIdentity(), "changes");
					}

					@Override
					public boolean isSafe() {
						return true;
					}

					@Override
					public Transaction lock() {
						return Transaction.NONE;
					}

					@Override
					public Transaction tryLock() {
						return Transaction.NONE;
					}

					@Override
					public CoreId getCoreId() {
						return CoreId.EMPTY;
					}

					@Override
					public Subscription subscribe(Observer<? super ObservableElementEvent<T>> observer) {
						ObservableElementEvent<T> event = createInitialEvent(null, null, null);
						try (Transaction t = event.use()) {
							observer.onNext(event);
						}
						return Subscription.NONE;
					}
				}
				return new ElementChanges();
			}
		}
		return new EmptyElement();
	}
}
