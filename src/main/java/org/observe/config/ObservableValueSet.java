package org.observe.config;

import org.observe.collect.ObservableCollection;

import com.google.common.reflect.TypeToken;

public interface ObservableValueSet<E> {
	ConfiguredValueType<E> getType();

	ObservableCollection<? extends E> getValues();

	default ValueCreator<E, E> create() {
		return create(getType().getType());
	}

	<E2 extends E> ValueCreator<E, E2> create(TypeToken<E2> subType);

	static <E> ObservableValueSet<E> empty(TypeToken<E> type) {
		return new EmptyValueSet<>(type);
	}

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
		public ObservableCollection<? extends E> getValues() {
			return theValues;
		}

		@Override
		public <E2 extends E> ValueCreator<E, E2> create(TypeToken<E2> subType) {
			throw new UnsupportedOperationException();
		}
	}
}
