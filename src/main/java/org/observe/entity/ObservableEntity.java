package org.observe.entity;

import java.util.function.Function;

import org.observe.Observable;
import org.qommons.Identifiable;
import org.qommons.Stamped;

/**
 * A managed entity in a data set
 *
 * @param <E> The java-type of the entity
 */
public interface ObservableEntity<E> extends Stamped, Identifiable {
	/** The message returned from {@link #isAcceptable(int, Object)} or {@link #canDelete()} if the entity has already been removed */
	public static final String ENTITY_REMOVED = "This entity has been removed";

	ObservableEntityType<E> getType();

	E getEntity();

	EntityIdentity<E> getId();
	@Override
	default EntityIdentity<E> getIdentity() {
		return getId();
	}

	Object get(int fieldIndex);
	String isAcceptable(int fieldIndex, Object value);
	<F> F set(int fieldIndex, F value, Object cause);
	default Observable<? extends ObservableEntityFieldEvent<E, ?>> fieldChanges(int fieldIndex) {
		return allFieldChanges().filter(evt -> evt.getField().getFieldIndex() == fieldIndex);
	}
	Observable<ObservableEntityFieldEvent<E, ?>> allFieldChanges();

	default ObservableEntityField<E, ?> getField(String fieldName) {
		ObservableEntityFieldType<E, ?> fieldType = getType().getFields().getIfPresent(fieldName);
		if (fieldType == null)
			throw new IllegalArgumentException("No such field " + getType().getEntityName() + "." + fieldName);
		return getField(fieldType);
	}
	default ObservableEntityField<E, ?> getField(int fieldIndex) {
		return getField(getType().getFields().get(fieldIndex));
	}
	default <F> ObservableEntityField<E, F> getField(Function<? super E, F> fieldGetter) {
		return getField(getType().getField(fieldGetter));
	}
	default <F> ObservableEntityField<E, F> getField(ObservableEntityFieldType<? super E, F> fieldType) {
		ObservableEntityFieldType<E, F> myFieldType;
		if (fieldType.getEntityType() == getType()) {
			if (getType().getFields().get(fieldType.getFieldIndex()) != fieldType)
				throw new IllegalArgumentException("Unrecognized field type: " + fieldType);
			myFieldType = (ObservableEntityFieldType<E, F>) fieldType;
		} else {
			myFieldType = (ObservableEntityFieldType<E, F>) getType().getFields().getIfPresent(fieldType.getName());
			if (myFieldType == null)
				throw new IllegalArgumentException("No such field " + getType().getEntityName() + "." + fieldType.getName());
		}
		return new ObservableEntityField<>(this, myFieldType);
	}

	boolean isPresent();
	String canDelete();
	void delete() throws UnsupportedOperationException;
	default Observable<ObservableEntity<?>> onDelete() {
		// Use ID field if we can, since those only fire completed events
		if (getType().getIdentityFields().keySize() > 0)
			return fieldChanges(getType().getIdentityFields().get(0).getFieldIndex()).completed().map(__ -> this);
		else if (getType().getFields().keySize() > 0)
			return fieldChanges(0).completed().map(__ -> this);
		else
			throw new UnsupportedOperationException("This default method must be overridden if the type has no fields");
	}
}
