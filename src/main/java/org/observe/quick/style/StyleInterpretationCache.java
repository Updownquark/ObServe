package org.observe.quick.style;

import java.util.LinkedHashMap;
import java.util.Map;

import org.observe.SettableValue;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.qommons.io.LocatedFilePosition;

/** A cache of interpreted style {@link InterpretedStyleApplication applications} and value expressions */
public interface StyleInterpretationCache {
	/**
	 * A cache entry for the style application and value for a particular style value
	 *
	 * @param <T> The type of the style value
	 */
	public static class InterpretedStyleData<T> {
		/** The interpreted style application for the value */
		public final InterpretedStyleApplication application;
		/** The interprted value expression */
		public final InterpretedValueSynth<SettableValue<?>, SettableValue<T>> value;

		/**
		 * @param application The interpreted style application for the value
		 * @param value The interprted value expression
		 */
		public InterpretedStyleData(InterpretedStyleApplication application,
			InterpretedValueSynth<SettableValue<?>, SettableValue<T>> value) {
			this.application = application;
			this.value = value;
		}
	}

	/**
	 * @param valuePosition The file position of the value expression
	 * @return Whether this cache contains interpreted data for the value expression
	 */
	boolean containsKey(LocatedFilePosition valuePosition);

	/**
	 * @param <T> The type of the value
	 * @param valuePosition The file position of the value expression
	 * @return The interpreted data in this cache for the given value
	 */
	<T> InterpretedStyleData<T> get(LocatedFilePosition valuePosition);

	/** @return A modifiable cache to populate */
	public static Modifiable create() {
		return new Modifiable();
	}

	/** A modifiable {@link StyleInterpretationCache} */
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

		/**
		 * Removes all data from this cache
		 *
		 * @return This cache
		 */
		public Modifiable clear() {
			theInterpretedValues.clear();
			return this;
		}

		/**
		 * @param <T> The type of the style value
		 * @param position The file position of the style value
		 * @param application The interpreted application for the style value
		 * @param value The interpreted style value
		 * @return This cache
		 */
		public <T> Modifiable with(LocatedFilePosition position, InterpretedStyleApplication application,
			InterpretedValueSynth<SettableValue<?>, SettableValue<T>> value) {
			theInterpretedValues.put(position, new InterpretedStyleData<>(application, value));
			return this;
		}

		/** @return An unmodifiable view of this cache */
		public StyleInterpretationCache unmodifiable() {
			return theUnmodifiable;
		}
	}

	/** An unmodifiable view of a {@link StyleInterpretationCache} */
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
