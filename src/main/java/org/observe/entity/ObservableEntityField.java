package org.observe.entity;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.SettableValue;
import org.observe.util.TypeTokens;
import org.qommons.Identifiable;
import org.qommons.collect.MutableCollectionElement.StdMsg;

import com.google.common.reflect.TypeToken;

/**
 * A {@link SettableValue} representing a field of an {@link ObservableEntity}
 *
 * @param <E> The type of the entity
 * @param <F> The type of the field
 */
public class ObservableEntityField<E, F> implements SettableValue<F> {
	/** The message returned from {@link #isAcceptable(Object)} if this value represents an ID field */
	public static final String ID_FIELD_UNSETTABLE = "ID fields cannot be changed";

	private final ObservableEntity<E> theEntity;
	private final ObservableEntityFieldType<E, F> theField;

	/**
	 * @param entity The entity that this field belongs to
	 * @param field The field type of this field
	 */
	public ObservableEntityField(ObservableEntity<E> entity, ObservableEntityFieldType<E, F> field) {
		theEntity = entity;
		theField = field;
	}

	/** @return The entity that this field belongs to */
	public ObservableEntity<E> getEntity() {
		return theEntity;
	}

	/** @return The field type of this field */
	public ObservableEntityFieldType<E, F> getFieldType() {
		return theField;
	}

	@Override
	public TypeToken<F> getType() {
		return getFieldType().getFieldType();
	}

	@Override
	public F get() {
		return (F) getEntity().get(getFieldType().getFieldIndex());
	}

	@Override
	public long getStamp() {
		return getEntity().getStamp();
	}

	@Override
	public Object getIdentity() {
		return Identifiable.wrap(getEntity().getId(), getFieldType().getName());
	}

	@Override
	public <V extends F> F set(V value, Object cause) throws IllegalArgumentException, UnsupportedOperationException {
		String msg = isAcceptable(value);
		if (msg == StdMsg.UNSUPPORTED_OPERATION || msg == ID_FIELD_UNSETTABLE || msg == ObservableEntity.ENTITY_REMOVED)
			throw new UnsupportedOperationException(msg);
		else if (msg != null)
			throw new IllegalArgumentException(msg);
		return getEntity().set(getFieldType().getFieldIndex(), value, cause);
	}

	@Override
	public <V extends F> String isAcceptable(V value) {
		String msg = isEnabled().get();
		if (msg != null)
			return msg;
		return getEntity().isAcceptable(getFieldType().getFieldIndex(), value);
	}

	@Override
	public ObservableValue<String> isEnabled() {
		if (getFieldType().getIdIndex() >= 0)
			return ObservableValue.of(ID_FIELD_UNSETTABLE);
		else
			return ObservableValue.of(TypeTokens.get().STRING, () -> {
				if (!getEntity().isPresent())
					return ObservableEntity.ENTITY_REMOVED;
				else
					return null;
			}, this::getStamp, getEntity().onDelete());
	}

	/** @return An observable that fires {@link ObservableEntityFieldEvent field events} when the value of this field changes */
	public Observable<ObservableEntityFieldEvent<E, F>> fieldChanges() {
		return (Observable<ObservableEntityFieldEvent<E, F>>) getEntity().fieldChanges(theField.getFieldIndex());
	}

	@Override
	public Observable<ObservableValueEvent<F>> noInitChanges() {
		return (Observable<ObservableValueEvent<F>>) (Observable<?>) fieldChanges();
	}
}
