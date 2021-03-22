package org.observe.config;

import org.observe.collect.ObservableCollection;

import com.google.common.reflect.TypeToken;

/**
 * Represents a collection of values with {@link #create() addition} capability
 * 
 * @param <E> The type of values in the set
 */
public interface ObservableValueSet<E> {
	/** @return The type of values in the set, detailing fields and other type information */
	ConfiguredValueType<E> getType();

	/** @return The values in the set */
	ObservableCollection<E> getValues();

	/** @return A creator structure that may be used to add values to the set */
	default ConfigurableValueCreator<E, E> create() {
		return create(getType().getType());
	}

	/**
	 * @param <E2> The sub-type of value to add
	 * @param subType The sub-type of value to add
	 * @return A creator structure that may be used to add values to the set
	 */
	<E2 extends E> ConfigurableValueCreator<E, E2> create(TypeToken<E2> subType);

	/**
	 * @param <E> The type of the set
	 * @param type The type of the set
	 * @return An empty {@link ObservableValueSet} with the given type
	 */
	static <E> ObservableValueSet<E> empty(TypeToken<E> type) {
		return new EmptyValueSet<>(type);
	}

	/**
	 * Implements {@link ObservableValueSet#empty(TypeToken)}
	 * 
	 * @param <E> The type of the set
	 */
	class EmptyValueSet<E> implements ObservableValueSet<E> {
		private final ConfiguredValueType<E> theType;
		private final ObservableCollection<E> theValues;

		public EmptyValueSet(TypeToken<E> type) {
			theType = ConfiguredValueType.empty(type);
			theValues = ObservableCollection.of(type);
		}

		@Override
		public ConfiguredValueType<E> getType() {
			return theType;
		}

		@Override
		public ObservableCollection<E> getValues() {
			return theValues;
		}

		@Override
		public <E2 extends E> ConfigurableValueCreator<E, E2> create(TypeToken<E2> subType) {
			throw new UnsupportedOperationException();
		}
	}
}
