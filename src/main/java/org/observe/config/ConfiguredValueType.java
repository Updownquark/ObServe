package org.observe.config;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import org.qommons.collect.QuickSet.QuickMap;

import com.google.common.reflect.TypeToken;

/**
 * Represents a type of entity or value with an ordered set of named fields
 *
 * @param <E> The compile-time type of the entity or value
 */
public interface ConfiguredValueType<E> {
	/** @return The run-time type of the entity or value */
	TypeToken<E> getType();

	/** @return Any other types that this type inherits from */
	List<? extends ConfiguredValueType<? super E>> getSupers();

	/** @return This value type's fields */
	QuickMap<String, ? extends ConfiguredValueField<E, ?>> getFields();

	/**
	 * @param fieldGetter The getter for the field in the java type
	 * @return The field type in this value type represented by the given java field
	 * @throws IllegalArgumentException If the given field does not represent a field getter in this value type
	 */
	<F> ConfiguredValueField<E, F> getField(Function<? super E, F> fieldGetter) throws IllegalArgumentException;

	/**
	 * @return Whether this type allows setting fields whose names are not in the {@link #getFields() field set}. Entities, for example,
	 *         generally have a fixed set of fields with no place for others. Maps, however, could be treated as values with flexible
	 *         fields.
	 */
	boolean allowsCustomFields();

	/**
	 * @param type The value type
	 * @return A {@link ConfiguredValueType} with no fields
	 */
	static <E> ConfiguredValueType<E> empty(TypeToken<E> type) {
		return new EmptyValueType<>(type);
	}

	/**
	 * Implements {@link ConfiguredValueType#empty(TypeToken)}
	 *
	 * @param <E> The value type
	 */
	class EmptyValueType<E> implements ConfiguredValueType<E> {
		private final TypeToken<E> theType;

		public EmptyValueType(TypeToken<E> type) {
			theType = type;
		}

		@Override
		public TypeToken<E> getType() {
			return theType;
		}

		@Override
		public List<? extends ConfiguredValueType<? super E>> getSupers() {
			return Collections.emptyList();
		}

		@Override
		public QuickMap<String, ? extends ConfiguredValueField<E, ?>> getFields() {
			return QuickMap.empty();
		}

		@Override
		public <F> ConfiguredValueField<E, F> getField(Function<? super E, F> fieldGetter) {
			throw new IllegalArgumentException("No such field: " + fieldGetter);
		}

		@Override
		public boolean allowsCustomFields() {
			return false;
		}
	}
}
