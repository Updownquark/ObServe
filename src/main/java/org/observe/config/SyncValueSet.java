package org.observe.config;

import org.observe.collect.ObservableCollection;

import com.google.common.reflect.TypeToken;

/**
 * An {@link ObservableValueSet} without asynchronous creation capabilities
 * 
 * @param <E> The type of values in the set
 */
public interface SyncValueSet<E> extends ObservableValueSet<E> {
	@Override
	default SyncValueCreator<E, E> create() {
		return create(getType().getType());
	}

	@Override
	<E2 extends E> SyncValueCreator<E, E2> create(TypeToken<E2> subType);

	/**
	 * @param <E> The type of the set
	 * @param type The type of the set
	 * @return An empty {@link SyncValueSet} with the given type
	 */
	static <E> SyncValueSet<E> empty(TypeToken<E> type) {
		return new EmptySyncValueSet<>(type);
	}

	/**
	 * Implements {@link SyncValueSet#empty(TypeToken)}
	 * 
	 * @param <E> The type of the set
	 */
	class EmptySyncValueSet<E> implements SyncValueSet<E> {
		private final ConfiguredValueType<E> theType;
		private final ObservableCollection<E> theValues;

		public EmptySyncValueSet(TypeToken<E> type) {
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
		public <E2 extends E> SyncValueCreator<E, E2> create(TypeToken<E2> subType) {
			throw new UnsupportedOperationException();
		}
	}
}
