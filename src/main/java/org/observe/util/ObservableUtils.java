package org.observe.util;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.Subscription;
import org.observe.collect.ObservableList;

/** Utility methods for observables */
public class ObservableUtils {
	/**
	 * Wraps an event from an observable value to use a different observable value as the source
	 *
	 * @param <T> The type of the value to wrap an event for
	 * @param event The event to wrap
	 * @param wrapper The wrapper observable to wrap the event for
	 * @return An event with the same values as the given event, but created by the given observable
	 */
	public static <T> ObservableValueEvent<T> wrap(ObservableValueEvent<? extends T> event, ObservableValue<T> wrapper) {
		if (event.isInitial())
			return wrapper.createInitialEvent(event.getValue(), event.getCause());
		else
			return wrapper.createChangeEvent(event.getOldValue(), event.getValue(), event.getCause());
	}

	/**
	 * Wraps all events from an observable value to use a different observable value as the source
	 *
	 * @param <T> The type of the value to wrap events for
	 * @param value The observable value whose events to wrap
	 * @param wrapper The wrapper observable to wrap the events for
	 * @param observer The observer interested in the wrapped events
	 * @return The subscription to unsubscribe from the wrapped events
	 */
	public static <T> Subscription wrap(ObservableValue<? extends T> value, ObservableValue<T> wrapper,
		Observer<? super ObservableValueEvent<T>> observer) {
		return value.subscribe(new Observer<ObservableValueEvent<? extends T>>() {
			@Override
			public <V extends ObservableValueEvent<? extends T>> void onNext(V event) {
				Observer.onNextAndFinish(observer, wrap(event, wrapper));
			}

			@Override
			public <V extends ObservableValueEvent<? extends T>> void onCompleted(V event) {
				Observer.onCompletedAndFinish(observer, wrap(event, wrapper));
			}
		});
	}

	/**
	 * A seemingly narrow use case. Makes an observable to be used as the until in
	 * {@link org.observe.collect.ObservableCollection#takeUntil(Observable)} when this will be called as a result of an observable value
	 * containing an observable collection being called
	 *
	 * @param value The collection-containing value
	 * @param cause The event on the value that is the cause of this call
	 * @return The until observable to use
	 */
	public static Observable<?> makeUntil(ObservableValue<?> value, ObservableValueEvent<?> cause) {
		Observable<?> until = value.noInit().fireOnComplete();
		if (!cause.isInitial()) {
			/* If we don't do this, the listener for the until will get added to the end of the queue and will be
			 * called for the same change event we're in now.  So we skip one. */
			until = until.skip(1);
		}
		return until;
	}

	private static class ControllableObservableList<T> extends ObservableListWrapper<T> {
		private volatile boolean isControlled;

		public ControllableObservableList(ObservableList<T> wrap) {
			super(wrap, false);
		}

		protected ObservableList<T> getController() {
			if (isControlled)
				throw new IllegalStateException("This list is already controlled");
			isControlled = true;
			return super.getWrapped();
		}
	}

	/**
	 * A mechanism for passing controllable lists to super constructors
	 *
	 * @param <T> The type of the list
	 * @param list The list to control
	 * @return A list that cannot be modified directly and for which a single call to {@link #getController(ObservableList)} will return a
	 *         modifiable list, changes to which will be reflected in the return value
	 */
	public static <T> ObservableList<T> control(ObservableList<T> list) {
		return new ControllableObservableList<>(list);
	}

	/**
	 * Gets the controller for a list created by {@link #control(ObservableList)}
	 *
	 * @param <T> The type of the list
	 * @param controllableList The controllable list
	 * @return The controller for the list
	 * @throws IllegalArgumentException If the given list was not created by {@link #control(ObservableList)}
	 * @throws IllegalStateException If the given list is already controlled
	 */
	public static <T> ObservableList<T> getController(ObservableList<T> controllableList) {
		if (!(controllableList instanceof ControllableObservableList))
			throw new IllegalArgumentException("This list is not controllable.  Use control(ObservableList) to create a controllable list");
		return ((ControllableObservableList<T>) controllableList).getController();
	}
}
