package org.observe.expresso;

import org.observe.SettableValue;

import com.google.common.reflect.TypeToken;

/**
 * A synthetic field is a feature that allows a dev to define notional fields derived from objects that are not exposed as java fields. The
 * fields can be used in expresso identically to a java field.
 *
 * @param <E> The type of the value on which the field is defined
 * @param <F> The type of the field value
 */
public interface SyntheticField<E, F> {
	/**
	 * The definition for a {@link SyntheticField}
	 * 
	 * @param <E> The type of the value on which the field is defined
	 * @param <F> The type of the field value
	 */
	public interface Def<E, F> {
		/**
		 * @param <E2> The sub-type of entity to get the field instance for
		 * @param entityType The sub-type of entity to get the field instance for
		 * @return The instance of this field for the given entity type
		 */
		<E2 extends E> SyntheticField<E2, ? extends F> get(TypeToken<E2> entityType);
	}

	/** @return The type of this field */
	TypeToken<F> getType();

	/**
	 * @param entity A value containing an entity
	 * @return The derived field value for the given entity
	 */
	SettableValue<F> get(SettableValue<? extends E> entity);
}
