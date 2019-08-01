package org.observe;

import java.lang.reflect.Array;
import java.util.function.Function;

import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollectionImpl;
import org.observe.util.TypeTokens;

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;

/**
 * An action with an observable enabled property
 *
 * @param <T> The type of value the action produces
 */
public interface ObservableAction<T> {
	/** This class's type key */
	@SuppressWarnings("rawtypes")
	static TypeTokens.TypeKey<ObservableAction> TYPE_KEY = TypeTokens.get().keyFor(ObservableAction.class)
	.enableCompoundTypes(new TypeTokens.UnaryCompoundTypeCreator<ObservableAction>() {
		@Override
		public <P> TypeToken<? extends ObservableAction> createCompoundType(TypeToken<P> param) {
			return new TypeToken<ObservableAction<P>>() {}.where(new TypeParameter<P>() {}, param);
		}
	});
	/** This class's wildcard {@link TypeToken} */
	static TypeToken<ObservableAction<?>> TYPE = TYPE_KEY.parameterized();

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
	 * @param disabled The disabled message to override the action with
	 * @return A new ObservableAction whose {@link #isEnabled()} message is the value of the given message, or this action's enablement if
	 *         that is null
	 */
	default ObservableAction<T> disableWith(ObservableValue<String> disabled) {
		return new DisabledObservableAction<>(this, disabled);
	}

	/**
	 * @param <T> The type of the action
	 * @param type The type of the action
	 * @param action The action (parameter is the cause)
	 * @return An ObservableAction that invokes the given function on its {@link #act(Object)} method
	 */
	static <T> ObservableAction<T> of(TypeToken<T> type, Function<Object, T> action) {
		return new SimpleObservableAction<>(type, action);
	}

	/**
	 * @param <T> The type of the action
	 * @param wrapper An observable value that supplies actions
	 * @return An action based on the content of the wrapper
	 */
	static <T> ObservableAction<T> flatten(ObservableValue<? extends ObservableAction<? extends T>> wrapper) {
		return new FlattenedObservableAction<>(wrapper);
	}

	/**
	 * @param <T> The type of the acton's value
	 * @param type The run-time type of the action's value
	 * @param value The value to be returned each time by the action
	 * @return An action that does nothing but return the given value
	 */
	static <T> ObservableAction<T> nullAction(TypeToken<T> type, T value) {
		return new ObservableAction<T>() {
			@Override
			public TypeToken<T> getType() {
				return type;
			}

			@Override
			public T act(Object cause) throws IllegalStateException {
				return value;
			}

			@Override
			public ObservableValue<String> isEnabled() {
				return ObservableValue.of(TypeToken.of(String.class), null);
			}
		};
	}

	/**
	 * @param <T> The type of the action
	 * @param type The type for the action
	 * @param message The disabled message for the action
	 * @return An action that is always disabled with the given message
	 */
	static <T> ObservableAction<T> disabled(TypeToken<T> type, String message) {
		return new ObservableAction<T>() {
			@Override
			public TypeToken<T> getType() {
				return type;
			}

			@Override
			public T act(Object cause) throws IllegalStateException {
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
	 * @param <T> The type of the actions
	 * @param type The run-time type of the actions
	 * @param actions The actions to combine
	 * @return A single action that invokes the given actions and returns their values as an array
	 */
	static <T> ObservableAction<T[]> and(TypeToken<T> type, ObservableAction<? extends T>... actions) {
		TypeToken<ObservableAction<? extends T>> actionType = new TypeToken<ObservableAction<? extends T>>() {}
		.where(new TypeParameter<T>() {}, type);
		return and(ObservableCollection.of(actionType, actions));
	}

	/**
	 * Combines several actions into one
	 *
	 * @param <T> The type of the actions
	 * @param actions The actions to combine
	 * @return A single action that invokes the given actions and returns their values as an array
	 */
	static <T> ObservableAction<T[]> and(ObservableCollection<? extends ObservableAction<? extends T>> actions) {
		return new AndObservableAction<>(actions);
	}

	/**
	 * Implements {@link ObservableAction#of(TypeToken, Function)}
	 *
	 * @param <T> The type of the action
	 */
	class SimpleObservableAction<T> implements ObservableAction<T> {
		private final TypeToken<T> theType;
		private final Function<Object, T> theAction;

		public SimpleObservableAction(TypeToken<T> type, Function<Object, T> action) {
			theType = type;
			theAction = action;
		}

		@Override
		public TypeToken<T> getType() {
			return theType;
		}

		@Override
		public T act(Object cause) throws IllegalStateException {
			return theAction.apply(cause);
		}

		@Override
		public ObservableValue<String> isEnabled() {
			return SettableValue.ALWAYS_ENABLED;
		}
	}

	/**
	 * Implements {@link ObservableAction#disableWith(ObservableValue)}
	 *
	 * @param <T> The type of the action
	 */
	class DisabledObservableAction<T> implements ObservableAction<T> {
		private final ObservableAction<T> theParentAction;
		private final ObservableValue<String> theDisablement;

		public DisabledObservableAction(ObservableAction<T> parentAction, ObservableValue<String> disablement) {
			theParentAction = parentAction;
			theDisablement = disablement;
		}

		@Override
		public TypeToken<T> getType() {
			return theParentAction.getType();
		}

		@Override
		public T act(Object cause) throws IllegalStateException {
			String msg = theDisablement.get();
			if (msg != null)
				throw new IllegalStateException(msg);
			return theParentAction.act(cause);
		}

		@Override
		public ObservableValue<String> isEnabled() {
			return ObservableValue.firstValue(TypeTokens.get().STRING, msg -> msg != null, () -> null, theDisablement,
				theParentAction.isEnabled());
		}
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
			return ObservableValue.flatten(
				theWrapper//
				.map(action -> action == null ? ObservableValue.of(TypeTokens.get().STRING, "Empty Action") : action.isEnabled()), //
				() -> "This wrapper (" + theWrapper + ") is empty");
		}
	}

	/**
	 * Implements {@link ObservableAction#and(ObservableCollection)}
	 *
	 * @param <T> The type of the actions
	 */
	class AndObservableAction<T> implements ObservableAction<T[]> {
		private final ObservableCollection<? extends ObservableAction<? extends T>> theActions;
		private final TypeToken<T[]> theArrayType;

		protected AndObservableAction(ObservableCollection<? extends ObservableAction<? extends T>> actions) {
			theActions = actions;
			theArrayType = new TypeToken<T[]>() {}.where(new TypeParameter<T>() {}, (TypeToken<T>) actions.getType()
				.resolveType(ObservableCollection.class.getTypeParameters()[0]).resolveType(ObservableAction.class.getTypeParameters()[0]));
		}

		@Override
		public TypeToken<T []> getType() {
			return theArrayType;
		}

		@Override
		public T[] act(Object cause) throws IllegalStateException {
			ObservableAction<? extends T>[] actions = theActions.toArray();
			for (ObservableAction<? extends T> action : actions) {
				String msg = action.isEnabled().get();
				if (msg != null)
					throw new IllegalStateException(msg);
			}
			T[] values = (T[]) Array.newInstance(TypeTokens.getRawType(theArrayType.getComponentType()), actions.length);
			for (int i = 0; i < values.length; i++)
				values[i] = actions[i].act(cause);
			return values;
		}

		@Override
		public ObservableValue<String> isEnabled() {
			return theActions.flow().flattenValues(ObservableCollectionImpl.STRING_TYPE, action -> action.isEnabled()).collect()
				.observeFind(enabled -> enabled != null).first().find();
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder();
			boolean first = true;
			for (ObservableAction<?> action : theActions) {
				if (!first)
					str.append(';');
				first = false;
				str.append(action);
			}
			return str.toString();
		}
	}
}
