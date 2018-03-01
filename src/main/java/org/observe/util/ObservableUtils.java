package org.observe.util;

import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;

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
}
