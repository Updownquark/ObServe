package org.observe.entity;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.SettableValue;
import org.observe.util.EntityReflector;
import org.observe.util.TypeTokens;
import org.qommons.Identifiable;
import org.qommons.Transaction;
import org.qommons.collect.MutableCollectionElement.StdMsg;

import com.google.common.reflect.TypeToken;

/**
 * A {@link SettableValue} representing a field of an {@link ObservableEntity}
 *
 * @param <E> The type of the entity
 * @param <F> The type of the field
 */
public class ObservableEntityField<E, F> implements EntityReflector.ObservableField<E, F> {
	/** The message returned from {@link #isAcceptable(Object)} if this value represents an ID field */
	public static final String ID_FIELD_UNSETTABLE = "ID fields cannot be changed";
	/** Constant observable with value {@link #ID_FIELD_UNSETTABLE} */
	public static final ObservableValue<String> ID_FIELD_UNSETTABLE_VALUE = ObservableValue.of(ObservableEntityField.ID_FIELD_UNSETTABLE);

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

	@Override
	public E getEntity() {
		return theEntity.getEntity();
	}

	@Override
	public int getFieldIndex() {
		return theField.getIndex();
	}

	/** @return The entity that this field belongs to */
	public ObservableEntity<E> getObservableEntity() {
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
		return (F) getObservableEntity().get(getFieldType().getIndex());
	}

	@Override
	public boolean isLockSupported() {
		return theField.getOwnerType().getEntitySet().isLockSupported();
	}

	@Override
	public Transaction lock(boolean write, Object cause) {
		return theField.getOwnerType().getEntitySet().lock(write, cause);
	}

	@Override
	public Transaction tryLock(boolean write, Object cause) {
		return theField.getOwnerType().getEntitySet().tryLock(write, cause);
	}

	@Override
	public long getStamp() {
		return getObservableEntity().getStamp();
	}

	@Override
	public Object getIdentity() {
		return Identifiable.wrap(getObservableEntity().getId(), getFieldType().getName());
	}

	@Override
	public <V extends F> F set(V value, Object cause) throws IllegalArgumentException, UnsupportedOperationException {
		String msg = isAcceptable(value);
		if (msg == StdMsg.UNSUPPORTED_OPERATION || msg == ID_FIELD_UNSETTABLE || msg == ObservableEntity.ENTITY_REMOVED)
			throw new UnsupportedOperationException(msg);
		else if (msg != null)
			throw new IllegalArgumentException(msg);
		return getObservableEntity().set(getFieldType().getIndex(), value, cause);
	}

	@Override
	public <V extends F> String isAcceptable(V value) {
		String msg = isEnabled().get();
		if (msg != null)
			return msg;
		return getObservableEntity().isAcceptable(getFieldType().getIndex(), value);
	}

	@Override
	public ObservableValue<String> isEnabled() {
		if (getFieldType().getIdIndex() >= 0)
			return ID_FIELD_UNSETTABLE_VALUE;
		else
			return ObservableValue.of(TypeTokens.get().STRING, () -> {
				if (!getObservableEntity().isPresent())
					return ObservableEntity.ENTITY_REMOVED;
				else
					return null;
			}, this::getStamp, getObservableEntity().onDelete());
	}

	/** @return An observable that fires {@link ObservableEntityFieldEvent field events} when the value of this field changes */
	public Observable<ObservableEntityFieldEvent<E, F>> fieldChanges() {
		return (Observable<ObservableEntityFieldEvent<E, F>>) getObservableEntity().fieldChanges(theField.getIndex());
	}

	@Override
	public Observable<ObservableValueEvent<F>> noInitChanges() {
		return (Observable<ObservableValueEvent<F>>) (Observable<?>) fieldChanges();
	}
}
