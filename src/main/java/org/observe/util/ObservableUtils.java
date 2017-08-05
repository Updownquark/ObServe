package org.observe.util;

import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.Subscription;

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
			return wrapper.createInitialEvent(event.getNewValue(), event.getCause());
		else
			return wrapper.createChangeEvent(event.getOldValue(), event.getNewValue(), event.getCause());
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
				ObservableValueEvent.doWith(wrap(event, wrapper), observer::onNext);
			}

			@Override
			public <V extends ObservableValueEvent<? extends T>> void onCompleted(V event) {
				ObservableValueEvent.doWith(wrap(event, wrapper), observer::onCompleted);
			}
		});
	}
}
