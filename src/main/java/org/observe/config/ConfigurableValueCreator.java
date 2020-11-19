package org.observe.config;

import java.util.Set;
import java.util.function.Function;

import org.observe.util.TypeTokens;
import org.qommons.collect.ElementId;

/**
 * Creates a value in a set of values
 *
 * @param <E> The type of the value set
 * @param <E2> The sub-type of the value to create
 */
public interface ConfigurableValueCreator<E, E2 extends E> extends ValueCreator<E, E2> {
	/**
	 * @return The {@link ConfiguredValueField#getIndex() indexes} of fields that must be set in this creator before the value can be
	 *         created
	 */
	Set<Integer> getRequiredFields();

	/**
	 * @param after The element currently in the collection that the value should be inserted after
	 * @return This creator
	 */
	ConfigurableValueCreator<E, E2> after(ElementId after);
	/**
	 * @param before The element currently in the collection that the value should be inserted before
	 * @return This creator
	 */
	ConfigurableValueCreator<E, E2> before(ElementId before);
	/**
	 * @param towardBeginning Whether the new value should be inserted into the collection toward the beginning or end
	 * @return This creator
	 */
	ConfigurableValueCreator<E, E2> towardBeginning(boolean towardBeginning);
	/**
	 * @param after The element currently in the collection that the value should be inserted after
	 * @param before The element currently in the collection that the value should be inserted before
	 * @param towardBeginning Whether the new value should be inserted into the collection toward the beginning or end
	 * @return This creator
	 */
	default ConfigurableValueCreator<E, E2> between(ElementId after, ElementId before, boolean towardBeginning) {
		return after(after).before(before).towardBeginning(towardBeginning);
	}

	/**
	 * @param field The field to check
	 * @return null if the given field can be set in this creator, or a reason why it can't be
	 */
	String isEnabled(ConfiguredValueField<? super E2, ?> field);
	/**
	 * @param <F> The type of the field
	 * @param field The field to check
	 * @param value The value to check
	 * @return null if the given field can be set to the given value in this creator, or a reason why it can't be
	 */
	<F> String isAcceptable(ConfiguredValueField<? super E2, F> field, F value);

	/**
	 * Sets a value for a field in this creator. The value created by this creator will have the given value for the field.
	 *
	 * @param fieldName The name of the field to set
	 * @param value The value for the field
	 * @return This creator
	 * @throws IllegalArgumentException If the given value is unacceptable for the given field
	 */
	default ConfigurableValueCreator<E, E2> with(String fieldName, Object value) throws IllegalArgumentException {
		return with((ConfiguredValueField<E2, Object>) getType().getFields().get(fieldName), value);
	}
	/**
	 * Sets a value for a field in this creator. The value created by this creator will have the given value for the field.
	 *
	 * @param fieldGetter A function that calls the getter for the field to set the value for
	 * @param value The value for the field
	 * @return This creator
	 * @throws IllegalArgumentException If the given value is unacceptable for the given field
	 */
	default <F> ConfigurableValueCreator<E, E2> with(Function<? super E2, F> fieldGetter, F value) throws IllegalArgumentException {
		return with(getType().getField(fieldGetter), value);
	}
	/**
	 * Sets a value for a field in this creator. The value created by this creator will have the given value for the field.
	 *
	 * @param field The field to set
	 * @param value The value for the field
	 * @return This creator
	 * @throws IllegalArgumentException If the given value is unacceptable for the given field
	 */
	<F> ConfigurableValueCreator<E, E2> with(ConfiguredValueField<E2, F> field, F value) throws IllegalArgumentException;

	/**
	 * <p>
	 * Copies the values of all of this creator's {@link ConfigurableValueCreator#getType() type}'s {@link ConfiguredValueType#getFields() fields} from
	 * the given value into this creator, such that a new value created with this creator will be as identical as possible to the given
	 * value.
	 * </p>
	 * <p>
	 * This method will not attempt copy fields that are not {@link #isEnabled(ConfiguredValueField) enabled} or for which the value is not
	 * {@link #isAcceptable(ConfiguredValueField, Object) acceptable} in this creator. Thus this method will never throw an exception as
	 * long as those two methods accurately predict the failure of the {@link #with(ConfiguredValueField, Object) with} methods.
	 * </p>
	 *
	 * @param template The entity whose field values to copy into this creator
	 * @return This creator
	 */
	default ConfigurableValueCreator<E, E2> copy(E template) {
		CopyImpl.copy(this, template);
		return this;
	}

	/** Helper class for default implementation of {@link ConfigurableValueCreator#copy(Object)} */
	class CopyImpl {
		static <E, E2 extends E> void copy(ConfigurableValueCreator<E, E2> creator, E template) {
			for (ConfiguredValueField<E2, ?> field : creator.getType().getFields().allValues()) {
				if (field.getOwnerType() instanceof EntityConfiguredValueType
					&& ((EntityConfiguredValueType<E2>) field.getOwnerType()).getIdFields().contains(field.getIndex()))
					continue; // Don't copy ID fields
				copyField(creator, field, template);
			}
		}

		private static <E, E2 extends E, F> boolean copyField(ConfigurableValueCreator<E, E2> creator, ConfiguredValueField<E2, F> field, E template) {
			if (creator.isEnabled(field) != null)
				return false;
			ConfiguredValueField<? super E, ? super F> templateField = findField(field, template);
			if (templateField == null)
				return false;
			Object value = templateField.get(template);
			if ((value != null && !TypeTokens.get().isInstance(field.getFieldType(), value))
				|| creator.isAcceptable(field, (F) value) != null)
				return false;
			creator.with(field, (F) value);
			return true;
		}

		private static <E, E2 extends E, F> ConfiguredValueField<? super E, ? super F> findField(ConfiguredValueField<E2, F> field,
			E template) {
			if (TypeTokens.get().isInstance(field.getOwnerType().getType(), template))
				return (ConfiguredValueField<? super E, F>) field;
			for (ConfiguredValueField<? super E2, ? super F> override : field.getOverrides()) {
				override = findField(override, template);
				if (override != null)
					return (ConfiguredValueField<? super E, ? super F>) override;
			}
			return null;
		}
	}
}
