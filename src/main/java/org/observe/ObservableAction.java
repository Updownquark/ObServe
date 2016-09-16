package org.observe;

import com.google.common.reflect.TypeToken;

/**
 * An action with an observable enabled property
 *
 * @param <T> The type of value the action produces
 */
public interface ObservableAction<T> {
	/** @return The run-time type of values that this action produces */
	TypeToken<T> getType();

	/**
	 * @param cause An object that may have caused the action (e.g. a user event)
	 * @return The result of the action
	 * @throws IllegalStateException If this action is not enabled
	 */
	T act(Object cause) throws IllegalStateException;

	/** @return An observable whose value reports null if this value can be set directly, or a string describing why it cannot */
	ObservableValue<String> isEnabled();

	/**
	 * @param <T> The type of the action
	 * @param wrapper An observable value that supplies actions
	 * @return An action based on the content of the wrapper
	 */
	static <T> ObservableAction<T> flatten(ObservableValue<? extends ObservableAction<? extends T>> wrapper) {
		return new FlattenedObservableAction<>(wrapper);
	}

	/**
	 * An observable action whose methods reflect those of the content of an observable value, or a disabled action when the content is null
	 *
	 * @param <T> The type of value the action produces
	 */
	class FlattenedObservableAction<T> implements ObservableAction<T> {
		private final ObservableValue<? extends ObservableAction<? extends T>> theWrapper;
		private final TypeToken<T> theType;

		protected FlattenedObservableAction(ObservableValue<? extends ObservableAction<? extends T>> wrapper) {
			theWrapper = wrapper;
			theType = (TypeToken<T>) wrapper.getType().resolveType(ObservableValue.class.getTypeParameters()[0]);
		}

		@Override
		public TypeToken<T> getType() {
			if (theType != null)
				return theType;
			ObservableAction<? extends T> outerVal = theWrapper.get();
			if (outerVal == null)
				throw new IllegalStateException("Flattened action is null and no type given: " + theWrapper);
			return (TypeToken<T>) outerVal.getType();
		}

		@Override
		public T act(Object cause) throws IllegalStateException {
			ObservableAction<? extends T> wrapped = theWrapper.get();
			if (wrapped != null)
				return wrapped.act(cause);
			else
				throw new IllegalStateException("This wrapper (" + theWrapper + ") is empty");
		}

		@Override
		public ObservableValue<String> isEnabled() {
			return ObservableValue.flatten(theWrapper.mapV(action -> action.isEnabled()),
				() -> "This wrapper (" + theWrapper + ") is empty");
		}
	}
}
