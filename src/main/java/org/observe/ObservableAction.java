package org.observe;

import java.util.function.Consumer;

import org.observe.collect.ObservableCollection;

/** An action with an observable enabled property */
public interface ObservableAction {
	/** An ObservableAction that is always enabled and does nothing */
	public static final ObservableAction DO_NOTHING = new ObservableAction() {
		@Override
		public void act(Object cause) throws IllegalStateException {
		}

		@Override
		public ObservableValue<String> isEnabled() {
			return SettableValue.ALWAYS_ENABLED;
		}

		@Override
		public String toString() {
			return "doNothing";
		}
	};

	/**
	 * @param cause An object that may have caused the action (e.g. a user event)
	 * @throws IllegalStateException If this action is not enabled
	 */
	void act(Object cause) throws IllegalStateException;

	/** @return An observable whose value reports null if this value can be set directly, or a string describing why it cannot */
	ObservableValue<String> isEnabled();

	/**
	 * @param disabled The disabled message to override the action with
	 * @return A new ObservableAction whose {@link #isEnabled()} message is the value of the given message, or this action's enablement if
	 *         that is null
	 */
	default ObservableAction disableWith(ObservableValue<String> disabled) {
		return new DisabledObservableAction(this, disabled);
	}

	/**
	 * @param type The type of the action
	 * @param action The action (parameter is the cause)
	 * @return An ObservableAction that invokes the given function on its {@link #act(Object)} method
	 */
	static ObservableAction of(Consumer<Object> action) {
		return new SimpleObservableAction(action);
	}

	/**
	 * @param wrapper An observable value that supplies actions
	 * @return An action based on the content of the wrapper
	 */
	static ObservableAction flatten(ObservableValue<? extends ObservableAction> wrapper) {
		return new FlattenedObservableAction(wrapper);
	}

	/**
	 * @param type The run-time type of the action's value
	 * @param value The value to be returned each time by the action
	 * @return An action that does nothing but return the given value
	 */
	static ObservableAction nullAction() {
		return DO_NOTHING;
	}

	/**
	 * @param type The type for the action
	 * @param message The disabled message for the action
	 * @return An action that is always disabled with the given message
	 */
	static ObservableAction disabled(String message) {
		return new ObservableAction() {
			@Override
			public void act(Object cause) throws IllegalStateException {
				throw new IllegalStateException(message);
			}

			@Override
			public ObservableValue<String> isEnabled() {
				return ObservableValue.of(message);
			}
		};
	}

	/**
	 * Combines several actions into one
	 *
	 * @param actions The actions to combine
	 * @return A single action that invokes the given actions and returns their values as an array
	 */
	static ObservableAction and(ObservableAction... actions) {
		return and(ObservableCollection.of(actions));
	}

	/**
	 * Combines several actions into one
	 *
	 * @param actions The actions to combine
	 * @return A single action that invokes the given actions and returns their values as an array
	 */
	static ObservableAction and(ObservableCollection<? extends ObservableAction> actions) {
		return new AndObservableAction(actions);
	}

	/** Implements {@link ObservableAction#of(Consumer)} */
	class SimpleObservableAction implements ObservableAction {
		private final Consumer<Object> theAction;

		public SimpleObservableAction(Consumer<Object> action) {
			theAction = action;
		}

		@Override
		public void act(Object cause) throws IllegalStateException {
			theAction.accept(cause);
		}

		@Override
		public ObservableValue<String> isEnabled() {
			return SettableValue.ALWAYS_ENABLED;
		}
	}

	/** Implements {@link ObservableAction#disableWith(ObservableValue)} */
	class DisabledObservableAction implements ObservableAction {
		private final ObservableAction theParentAction;
		private final ObservableValue<String> theDisablement;

		public DisabledObservableAction(ObservableAction parentAction, ObservableValue<String> disablement) {
			theParentAction = parentAction;
			theDisablement = disablement;
		}

		/** @return The action that this action wraps and performs */
		protected ObservableAction getParentAction() {
			return theParentAction;
		}

		/** @return The additional disablement provided by this action */
		protected ObservableValue<String> getDisablement() {
			return theDisablement;
		}

		@Override
		public void act(Object cause) throws IllegalStateException {
			String msg = theDisablement.get();
			if (msg != null)
				throw new IllegalStateException(msg);
			theParentAction.act(cause);
		}

		@Override
		public ObservableValue<String> isEnabled() {
			return ObservableValue.firstValue(null, null, theDisablement, theParentAction.isEnabled());
		}

		@Override
		public String toString() {
			return theParentAction.toString();
		}
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
			return ObservableValue.flatten(theWrapper//
				.map(action -> action == null ? ObservableValue.of("Empty Action") : action.isEnabled()), //
				() -> "This wrapper (" + theWrapper + ") is empty");
		}
	}

	/** Implements {@link ObservableAction#and(ObservableCollection)} */
	class AndObservableAction implements ObservableAction {
		private final ObservableCollection<? extends ObservableAction> theActions;

		protected AndObservableAction(ObservableCollection<? extends ObservableAction> actions) {
			theActions = actions;
		}

		@Override
		public void act(Object cause) throws IllegalStateException {
			ObservableAction[] actions = theActions.toArray(new ObservableAction[theActions.size()]);
			for (ObservableAction action : actions) {
				String msg = action.isEnabled().get();
				if (msg != null)
					throw new IllegalStateException(msg);
			}
			for (int i = 0; i < actions.length; i++)
				actions[i].act(cause);
		}

		@Override
		public ObservableValue<String> isEnabled() {
			return theActions.flow().flattenValues(action -> action.isEnabled()).collect().observeFind(enabled -> enabled != null).first()
				.find();
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder();
			boolean first = true;
			for (ObservableAction action : theActions) {
				if (!first)
					str.append(';');
				first = false;
				str.append(action);
			}
			return str.toString();
		}
	}
}
