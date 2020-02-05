package org.observe.entity.impl;

import java.util.function.Consumer;

import org.observe.Observable;
import org.observe.Observer;
import org.observe.Subscription;
import org.observe.entity.EntityIdentity;
import org.observe.entity.EntityOperationException;
import org.observe.entity.EntityUpdate;
import org.observe.entity.ObservableEntity;
import org.observe.entity.ObservableEntityField;
import org.observe.entity.ObservableEntityFieldEvent;
import org.observe.entity.ObservableEntityFieldType;
import org.qommons.Causable;
import org.qommons.Identifiable;
import org.qommons.Transaction;
import org.qommons.collect.ListenerList;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.collect.QuickSet.QuickMap;

class ObservableEntityImpl<E> implements ObservableEntity<E> {
	private final ObservableEntityTypeImpl<E> theType;
	private final EntityIdentity<E> theId;
	private final E theEntity;
	private final QuickMap<String, Object> theFields;
	private long theStamp;
	private ListenerList<Observer<? super ObservableEntityFieldEvent<E, ?>>> theFieldObservers;
	private volatile boolean isPresent;

	ObservableEntityImpl(ObservableEntityTypeImpl<E> type, EntityIdentity<E> id) {
		theType = type;
		theId = id;
		theFields = type.getFields().keySet().createMap();
		for (int f = 0; f < theFields.keySize(); f++) {
			if (type.getFields().get(f).getIdIndex() >= 0)
				theFields.put(f, id.getFields().get(theType.getFields().get(f).getIdIndex()));
			else
				theFields.put(f, EntityUpdate.NOT_SET);
		}
		if (type.getReflector() == null)
			theEntity = null;
		else {
			theEntity = type.getReflector().newInstance(this::get, (fieldIndex, value) -> set(fieldIndex, value, null));
			type.getReflector().associate(theEntity, type, this);
		}
		theStamp = Double.doubleToLongBits(Math.random());
		isPresent = true;
	}

	@Override
	public ObservableEntityTypeImpl<E> getType() {
		return theType;
	}

	@Override
	public EntityIdentity<E> getId() {
		return theId;
	}

	@Override
	public E getEntity() {
		return theEntity;
	}

	@Override
	public long getStamp() {
		return theStamp;
	}

	@Override
	public Object get(int fieldIndex) {
		Object value = theFields.get(fieldIndex);
		if (value == EntityUpdate.NOT_SET) {
			try {
				load(theType.getFields().get(fieldIndex), null, null);
			} catch (EntityOperationException e) {
				throw new IllegalStateException("Could not load field " + theId + "." + theType.getFields().get(fieldIndex), e);
			}
			return theFields.get(fieldIndex);
		} else
			return value;
	}

	@Override
	public String isAcceptable(int fieldIndex, Object value) {
		if (!isPresent)
			return ENTITY_REMOVED;
		return theType.getEntitySet().isAcceptable(this, fieldIndex, value);
	}

	@Override
	public <F> F set(int fieldIndex, F value, Object cause) {
		if (!isPresent)
			throw new UnsupportedOperationException(ENTITY_REMOVED);
		try (Transaction t = theType.getEntitySet().lock(true, null)) {
			String msg = isAcceptable(fieldIndex, value);
			if (msg == StdMsg.UNSUPPORTED_OPERATION || msg == ObservableEntityField.ID_FIELD_UNSETTABLE || msg == ENTITY_REMOVED)
				throw new UnsupportedOperationException(msg);
			else if (msg != null)
				throw new IllegalArgumentException(msg);
			F oldValue = (F) theFields.get(fieldIndex);
			ObservableEntityFieldType<E, F> field = (ObservableEntityFieldType<E, F>) theType.getFields().get(fieldIndex);
			try {
				theType.select().entity(theId).update().setField(field, value).execute(true, cause);
			} catch (IllegalStateException | EntityOperationException e) {
				throw new IllegalArgumentException("Update failed", e);
			}
			return oldValue;
		}
	}

	@Override
	public boolean isLoaded(ObservableEntityFieldType<? super E, ?> field) {
		if (!field.getEntityType().equals(theType))
			field = theType.getFields().get(field.getName());
		return theFields.get(field.getFieldIndex()) != EntityUpdate.NOT_SET;
	}

	@Override
	public <F> ObservableEntity<E> load(ObservableEntityFieldType<E, F> field, Consumer<? super F> onLoad,
		Consumer<EntityOperationException> onFail) throws EntityOperationException {
		Object value = theFields.get(field.getFieldIndex());
		if (value != EntityUpdate.NOT_SET) {
			if (onLoad != null)
				onLoad.accept((F) value);
		} else
			theType.getEntitySet().loadField(this, field, onLoad, onFail);
		return this;
	}

	@Override
	public boolean isPresent() {
		return isPresent;
	}

	@Override
	public String canDelete() {
		if (!isPresent)
			return ENTITY_REMOVED;
		return theType.getEntitySet().canDelete(this);
	}

	@Override
	public void delete(Object cause) throws UnsupportedOperationException {
		if (!isPresent)
			throw new UnsupportedOperationException(ENTITY_REMOVED);
		try {
			theType.select().entity(theId).delete().execute(true, cause);
		} catch (IllegalStateException | EntityOperationException e) {
			throw new UnsupportedOperationException("Delete failed", e);
		}
	}

	<F> void _set(ObservableEntityFieldType<? super E, F> field, F oldValue, F newValue) {
		if (field.getEntityType() != theType)
			field = (ObservableEntityFieldType<E, F>) theType.getFields().get(field.getName());
		theStamp++;
		Object myOldValue = theFields.put(field.getFieldIndex(), newValue);
		if (myOldValue != EntityUpdate.NOT_SET) {
			if (oldValue == EntityUpdate.NOT_SET)
				oldValue = (F) myOldValue;
			ObservableEntityFieldEvent<E, F> event = new ObservableEntityFieldEvent<>(this, (ObservableEntityFieldType<E, F>) field,
				oldValue, newValue, null);
			try (Transaction evtT = Causable.use(event)) {
				theFieldObservers.forEach(//
					listener -> listener.onNext(event));
			}
		}
	}

	void removed(Object cause) {
		isPresent = false;
		for (int f = 0; f < theFields.keySize(); f++) {
			Object oldValue = theFields.get(f);
			ObservableEntityFieldType<E, Object> field = (ObservableEntityFieldType<E, Object>) theType.getFields().get(f);
			ObservableEntityFieldEvent<E, Object> event = new ObservableEntityFieldEvent<>(this, field, oldValue, oldValue, cause);
			try (Transaction evtT = Causable.use(event)) {
				theFieldObservers.forEach(//
					listener -> listener.onCompleted(event));
			}
		}
	}

	@Override
	public Observable<ObservableEntityFieldEvent<E, ?>> allFieldChanges() {
		class FieldChangesObservable extends AbstractIdentifiable implements Observable<ObservableEntityFieldEvent<E, ?>> {
			@Override
			protected Object createIdentity() {
				return Identifiable.wrap(ObservableEntityImpl.this.getIdentity(), "fieldChanges");
			}

			@Override
			public boolean isSafe() {
				return getType().getEntitySet().isLockSupported();
			}

			@Override
			public Transaction lock() {
				return getType().getEntitySet().lock(false, null);
			}

			@Override
			public Transaction tryLock() {
				return getType().getEntitySet().tryLock(false, null);
			}

			@Override
			public Subscription subscribe(Observer<? super ObservableEntityFieldEvent<E, ?>> observer) {
				if (theFieldObservers == null) {
					synchronized (ObservableEntityImpl.this) {
						if (theFieldObservers == null)
							theFieldObservers = ListenerList.build().build();
					}
				}
				return theFieldObservers.add(observer, true)::run;
			}
		}
		return new FieldChangesObservable();
	}

	@Override
	public int hashCode() {
		return theId.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof ObservableEntity && theId.equals(((ObservableEntity<?>) obj).getId());
	}

	@Override
	public String toString() {
		return theId.toString();
	}
}
