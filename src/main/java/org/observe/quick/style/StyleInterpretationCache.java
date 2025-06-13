package org.observe.quick.style;

import java.util.LinkedHashMap;
import java.util.Map;

import org.observe.SettableValue;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.qommons.io.LocatedFilePosition;

public interface StyleInterpretationCache {
	public static class InterpretedStyleData<T> {
		public final InterpretedStyleApplication application;
		public final InterpretedValueSynth<SettableValue<?>, SettableValue<T>> value;

		public InterpretedStyleData(InterpretedStyleApplication application,
			InterpretedValueSynth<SettableValue<?>, SettableValue<T>> value) {
			this.application = application;
			this.value = value;
		}
	}

	boolean containsKey(LocatedFilePosition valuePosition);

	<T> InterpretedStyleData<T> get(LocatedFilePosition valuePosition);

	public static Modifiable create() {
		return new Modifiable();
	}

	public static class Modifiable implements StyleInterpretationCache {
		private final Map<LocatedFilePosition, InterpretedStyleData<?>> theInterpretedValues;
		private final Unmodifiable theUnmodifiable;

		Modifiable() {
			theInterpretedValues = new LinkedHashMap<>();
			theUnmodifiable = new Unmodifiable(theInterpretedValues);
		}

		@Override
		public boolean containsKey(LocatedFilePosition valuePosition) {
			return theInterpretedValues.containsKey(valuePosition);
		}

		@Override
		public <T> InterpretedStyleData<T> get(LocatedFilePosition position) {
			InterpretedStyleData<?> found = theInterpretedValues.get(position);
			if (found == null)
				throw new IllegalStateException("No interpretation found for value at " + position.toShortString());
			return (InterpretedStyleData<T>) found;
		}

		public Modifiable clear() {
			theInterpretedValues.clear();
			return this;
		}

		public <T> Modifiable with(LocatedFilePosition position, InterpretedStyleApplication application,
			InterpretedValueSynth<SettableValue<?>, SettableValue<T>> value) {
			theInterpretedValues.put(position, new InterpretedStyleData<>(application, value));
			return this;
		}

		public StyleInterpretationCache unmodifiable() {
			return theUnmodifiable;
		}
	}

	static class Unmodifiable implements StyleInterpretationCache {
		private final Map<LocatedFilePosition, InterpretedStyleData<?>> theInterpretedValues;

		Unmodifiable(Map<LocatedFilePosition, InterpretedStyleData<?>> interpretedValues) {
			theInterpretedValues = interpretedValues;
		}

		@Override
		public boolean containsKey(LocatedFilePosition valuePosition) {
			return theInterpretedValues.containsKey(valuePosition);
		}

		@Override
		public <T> InterpretedStyleData<T> get(LocatedFilePosition position) {
			InterpretedStyleData<?> found = theInterpretedValues.get(position);
			if (found == null)
				throw new IllegalStateException("No interpretation found for value at " + position.toShortString());
			return (InterpretedStyleData<T>) found;
		}
	}
}
