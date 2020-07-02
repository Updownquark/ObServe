package org.observe.config;

import java.util.List;

import com.google.common.reflect.TypeToken;

/**
 * Represents a field in an entity or value type
 *
 * @param <E> The entity or value type
 * @param <F> The type of values that may be present in this field
 */
public interface ConfiguredValueField<E, F> {
	/** @return The entity or value type that this field is a member of */
	ConfiguredValueType<E> getOwnerType();

	/** @return The fields in the entity's super types that this field overrides */
	List<? extends ConfiguredValueField<? super E, ? super F>> getOverrides();

	/** @return The name */
	String getName();

	/** @return The run-time type of values that may be present in this field */
	TypeToken<F> getFieldType();

	/** @return The index of this field in the entity type's {@link ConfiguredValueType#getFields() field map} */
	int getIndex();

	/**
	 * @param entity The entity/value to get the value of this field from
	 * @return The value of this field in the given entity
	 */
	F get(E entity);

	/**
	 * @param entity The entity/value to set the value of this field in
	 * @param fieldValue The value of this field to set in the given entity
	 * @throws UnsupportedOperationException If the field cannot be set for any value-independent reason
	 * @throws IllegalArgumentException If the given field value is not allowed in this field for the given entity
	 */
	void set(E entity, F fieldValue) throws UnsupportedOperationException, IllegalArgumentException;
}
