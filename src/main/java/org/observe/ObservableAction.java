package org.observe;

/** An action with an observable enabled property */
public interface ObservableAction {
	/**
	 * @param cause An object that may have caused the action (e.g. a user event)
	 * @throws IllegalStateException If this action is not enabled
	 */
	void act(Object cause) throws IllegalStateException;

	/** @return An observable whose value reports null if this value can be set directly, or a string describing why it cannot */
	ObservableValue<String> isEnabled();

	/**
	 * @param wrapper An observable value that supplies actions
	 * @return An action based on the content of the wrapper
	 */
	static ObservableAction flatten(ObservableValue<? extends ObservableAction> wrapper) {
		return new FlattenedObservableAction(wrapper);
	}

	/**
	 * An observable action whose methods reflect those of the content of an observable value, or a disabled action when the content is null
	 */
	class FlattenedObservableAction implements ObservableAction {
		private final ObservableValue<? extends ObservableAction> theWrapper;

		protected FlattenedObservableAction(ObservableValue<? extends ObservableAction> wrapper) {
			theWrapper = wrapper;
		}

		@Override
		public void act(Object cause) throws IllegalStateException {
			ObservableAction wrapped = theWrapper.get();
			if (wrapped != null)
				wrapped.act(cause);
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
