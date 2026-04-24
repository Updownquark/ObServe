package org.observe.data;

import java.util.Comparator;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.Equivalence;
import org.observe.ObservableValue;
import org.observe.SimpleObservable;
import org.observe.assoc.ObservableMap;
import org.observe.assoc.ObservableMultiMap;
import org.observe.assoc.ObservableSortedMap;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollectionBuilder;
import org.observe.collect.ObservableSet;
import org.observe.collect.ObservableSortedCollection;
import org.observe.collect.ObservableSortedSet;
import org.observe.data.ReflectedEntityValueType.RealFieldValueProducer;
import org.observe.util.EntityReflector;
import org.observe.util.EntityReflector.FieldChange;
import org.qommons.CausalLock;
import org.qommons.SimpleUniqueKey;
import org.qommons.Subscription;
import org.qommons.Transaction;
import org.qommons.collect.ListenerList;
import org.qommons.data.impl.AbstractGenericEntity;
import org.qommons.data.types.EntityField;
import org.qommons.data.types.FieldType;
import org.qommons.data.values.GenericEntity;

public class MappedEntity<E> extends AbstractGenericEntity implements EntityReflector.ObservableEntityInstanceBacking<E> {
	static final Object ENTITY_ASSOC = new SimpleUniqueKey("mappedEntity");

	private final ReflectedEntityValueType<E> theType;
	private final Object[] theRealFieldValues;
	private final E theRealEntity;
	private ListenerList<Consumer<FieldChange<?>>>[] theListeners;
	private SimpleObservable<Void> theUntil;

	public MappedEntity(ReflectedEntityValueType<E> type, ReflectedEntitySet entitySet, Object[] id) {
		super(type.getGenericType(), entitySet, id);
		theType = type;
		theRealFieldValues = new Object[type.getFields().keySize()];
		theRealEntity = theType.getReflector().newInstance(this);
		EntityReflector.associate(theRealEntity, ENTITY_ASSOC, this);
		int f = 0;
		for (ReflectedFieldType<E, ?, ?> field : theType.getFields().values()) {
			theRealFieldValues[f] = ((RealFieldValueProducer<Object, ?>) field.getRealMapping()).genericToReal(//
				get(field.getGenericField()), entitySet, this);
		}
	}

	public E getRealEntity() {
		return theRealEntity;
	}

	public SimpleObservable<Void> getUntil() {
		if (theUntil == null)
			theUntil = SimpleObservable.create(b -> b.withLocking(getEntitySet()));
		return theUntil;
	}

	@Override
	public ReflectedEntitySet getEntitySet() {
		return (ReflectedEntitySet) super.getEntitySet();
	}

	@Override
	public MappedEntity<E> set(EntityField<?> field, Object value) {
		try (Transaction t = getEntitySet().lock(true, null)) {
			super.set(field, value);
			int index = theType.getGenericType().indexOf(field);
			Object oldValue = theRealFieldValues[index];
			Object newValue = ((Function<Object, Object>) theType.getFields().get(index).getGenericMapping()).apply(value);
			theRealFieldValues[index] = newValue;
			if (theListeners != null) {
				ListenerList<Consumer<FieldChange<?>>> listeners = theListeners[index];
				if (listeners != null) {
					FieldChange<Object> change = new FieldChange<>(oldValue, newValue, getEntitySet().getLock().getRootCausable());
					listeners.forEach(//
						l -> l.accept(change));
				}
			}
			getEntitySet().entityAffected(this);
		}
		return this;
	}

	@Override
	protected void fieldStructureChanged(EntityField<?> field) {
		super.fieldStructureChanged(field);
		// Only tell the entity set if the field is not mapped.
		// The content of a mapped field is defined by properties of the member entities,
		// so changes to mapped fields are really changes to the members, and not to this entity.
		// In particular, changes to mapped fields don't affect how this entity is persisted.
		if (field.getMapping() == null)
			getEntitySet().entityAffected(this);
	}

	@Override
	protected <T> T createEmptyStructure(EntityField<?> field, FieldType.ParameterizedType<T> type) {
		if (type instanceof FieldType.CollectionType) {
			ObservableCollectionBuilder<?, ?> builder;
			FieldType.CollectionType<?, ?> collType = (FieldType.CollectionType<?, ?>) type;
			if (collType.isSorted) {
				Comparator<?> sort;
				if (field.getMapping() != null && field.getMapping().sortByField != null)
					sort = field.getMapping().entitySort;
				else
					sort = type;
				if (collType.isDistinct)
					builder = ObservableSortedSet.build(sort);
				else
					builder = ObservableSortedCollection.build(sort);
			} else {
				if (collType.isDistinct)
					builder = ObservableSet.build();
				else
					builder = ObservableCollection.build();
			}
			return (T) builder.withLocking(getEntitySet().getLock()).withDescription(this + "." + field.getName()).build();
		} else if (type instanceof FieldType.MapType) {
			ObservableMap.Builder<?, ?, ?> builder;
			FieldType.MapType<?, ?, ?> mapType = (FieldType.MapType<?, ?, ?>) type;
			if (mapType.isSorted)
				builder = ObservableSortedMap.build(mapType.keyType);
			else
				builder = ObservableMap.build();
			return (T) builder.withLocking(getEntitySet().getLock()).withDescription(this + "." + field.getName()).build();
		} else if (type instanceof FieldType.MultiMapType) {
			ObservableMultiMap.Builder<?, ?, ?> builder;
			FieldType.MultiMapType<?, ?, ?> mapType = (FieldType.MultiMapType<?, ?, ?>) type;
			if (mapType.isSorted)
				builder = ObservableMultiMap.build().sortedBy((Comparator<Object>) mapType.keyType);
			else
				builder = ObservableMultiMap.build();
			if (field.getMapping() != null && field.getMapping().sortByField != null)
				builder = ((ObservableMultiMap.Builder<?, GenericEntity, ?>) builder)
				.withValueEquivalence(Equivalence.DEFAULT.sorted(field.getMapping().entitySort, true));
			return (T) builder.withLocking(getEntitySet().getLock()).withDescription(this + "." + field.getName()).build(getUntil());
		} else
			throw new IllegalStateException("Unrecognized parameterized field type: " + type);
	}

	@Override
	protected void deleted() {
		getEntitySet().deleteEntity(this);
	}

	@Override
	public Object get(int fieldIndex) {
		return theRealFieldValues[fieldIndex];
	}

	@Override
	public void set(int fieldIndex, Object newValue) {
		ReflectedFieldType<E, ?, ?> field = theType.getFields().get(fieldIndex);
		Function<?, ?> reverse = field.getGenericMapping();
		if (reverse == null)
			throw new IllegalArgumentException(isEnabled(fieldIndex).get());
		set(field.getGenericField(), ((Function<Object, ?>) reverse).apply(newValue));
	}

	private ListenerList<Consumer<FieldChange<?>>> getListeners(int fieldIndex) {
		if (theListeners == null)
			theListeners = new ListenerList[getType().getFields().size()];
		if (theListeners[fieldIndex] == null)
			theListeners[fieldIndex] = ListenerList.build().build();
		return theListeners[fieldIndex];
	}

	@Override
	public Subscription addListener(E entity, int fieldIndex, Consumer<FieldChange<?>> listener) {
		return getListeners(fieldIndex).add(listener, true);
	}

	@Override
	public CausalLock getLock(int fieldIndex) {
		return getEntitySet().getLock();
	}

	@Override
	public boolean isEventing(int fieldIndex) {
		return theListeners != null && theListeners[fieldIndex] != null && theListeners[fieldIndex].isFiring();
	}

	@Override
	public long getStamp(int fieldIndex) {
		return getListeners(fieldIndex).getStamp();
	}

	@Override
	public ObservableValue<String> isEnabled(int fieldIndex) {
		return ObservableValue.of(isEnabled(theType.getGenericType().getFields().get(fieldIndex)));
	}

	@Override
	public String isAcceptable(int fieldIndex, Object value) {
		return isAcceptable(theType.getGenericType().getFields().get(fieldIndex), value);
	}
}
