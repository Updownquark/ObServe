package org.observe.entity;

import org.observe.ObservableValueEvent;
import org.observe.util.EntityReflector;

/**
 * An {@link ObservableValueEvent} for entity fields
 *
 * @param <E> The type of the entity
 * @param <F> The type of the field
 */
public class ObservableEntityFieldEvent<E, F> extends EntityReflector.EntityFieldChangeEvent<E, F> {
	private final ObservableEntity<E> theEntity;
	private final ObservableEntityFieldType<E, F> theField;

	/**
	 * @param entity The entity whose field value changed
	 * @param field The field that changed
	 * @param oldValue The previous value of the field
	 * @param newValue The new value of the field
	 * @param cause The cause of the change
	 */
	public ObservableEntityFieldEvent(ObservableEntity<E> entity, ObservableEntityFieldType<E, F> field, F oldValue, F newValue,
		Object cause) {
		super(field.getIndex(), oldValue, newValue, cause);
		theField = field;
		theEntity = entity;
	}

	@Override
	public E getEntity() {
		return theEntity.getEntity();
	}

	/** @return The entity whose field value changed */
	public ObservableEntity<E> getObservableEntity() {
		return theEntity;
	}

	/** @return The field that changed */
	public ObservableEntityFieldType<E, F> getField() {
		return theField;
	}

	@Override
	public String toString() {
		return theEntity.getId() + "." + theField.getName() + ": " + super.toString();
	}
}
