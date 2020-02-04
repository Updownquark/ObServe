package org.observe.entity.impl;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.observe.entity.ConfigurableCreator;
import org.observe.entity.ConfigurableDeletion;
import org.observe.entity.ConfigurableQuery;
import org.observe.entity.ConfigurableUpdate;
import org.observe.entity.EntityChange;
import org.observe.entity.EntityChange.FieldChange;
import org.observe.entity.EntityCondition;
import org.observe.entity.EntityConstraint;
import org.observe.entity.EntityIdentity;
import org.observe.entity.EntityOperationException;
import org.observe.entity.EntityOperationVariable;
import org.observe.entity.EntityUpdate;
import org.observe.entity.ObservableEntity;
import org.observe.entity.ObservableEntityFieldType;
import org.observe.entity.ObservableEntityType;
import org.observe.entity.impl.ObservableEntityDataSetImpl.FieldTypeImpl;
import org.observe.util.EntityReflector;
import org.observe.util.EntityReflector.ReflectedField;
import org.observe.util.TypeTokens;
import org.qommons.Transaction;
import org.qommons.collect.BetterMap;
import org.qommons.collect.ElementId;
import org.qommons.collect.MapEntryHandle;
import org.qommons.collect.QuickSet.QuickMap;
import org.qommons.tree.BetterTreeMap;

class ObservableEntityTypeImpl<E> implements ObservableEntityType<E> {
	private final ObservableEntityDataSetImpl theEntitySet;
	private final String theName;
	private final List<ObservableEntityTypeImpl<? super E>> theSupers;
	private final List<ObservableEntityTypeImpl<? extends E>> theSubs;
	private final QuickMap<String, ObservableEntityFieldType<E, ?>> theFields;
	private final QuickMap<String, ObservableEntityFieldType<E, ?>> theIdFields;
	private final EntityReflector<E> theReflector;
	private final List<EntityConstraint<E>> theConstraints;

	private final BetterMap<EntityIdentity<E>, WeakReference<ObservableEntityImpl<? extends E>>> theEntitiesById;
	private final ReferenceQueue<Object> theCollectedEntities;
	private final Map<Reference<?>, ElementId> theEntitiesByRef;

	ObservableEntityTypeImpl(ObservableEntityDataSetImpl entitySet, String name, //
		List<ObservableEntityTypeImpl<? super E>> supers,
		QuickMap<String, ObservableEntityFieldType<E, ?>> fields, QuickMap<String, ObservableEntityFieldType<E, ?>> idFields,
		EntityReflector<E> reflector, List<EntityConstraint<E>> constraints) {
		theEntitySet = entitySet;
		theName = name;
		theSupers = supers;
		theSubs = new LinkedList<>();
		theIdFields = idFields;
		theFields = fields;
		theReflector = reflector;
		theConstraints = constraints;
		theEntitiesById = BetterTreeMap.<EntityIdentity<E>> build(EntityIdentity::compareTo).safe(false).buildMap();
		theCollectedEntities = new ReferenceQueue<>();
		theEntitiesByRef = new HashMap<>();
	}

	@Override
	public ObservableEntityDataSetImpl getEntitySet() {
		return theEntitySet;
	}

	@Override
	public List<? extends ObservableEntityType<? super E>> getSupers() {
		return theSupers;
	}

	void addSub(ObservableEntityTypeImpl<? extends E> sub) {
		theSubs.add(sub);
	}

	@Override
	public List<EntityConstraint<E>> getConstraints() {
		return theConstraints;
	}

	@Override
	public String getName() {
		return theName;
	}

	@Override
	public Class<E> getEntityType() {
		return theReflector == null ? null : TypeTokens.getRawType(theReflector.getType());
	}

	EntityReflector<E> getReflector() {
		return theReflector;
	}

	@Override
	public QuickMap<String, ObservableEntityFieldType<E, ?>> getFields() {
		return theFields;
	}

	@Override
	public QuickMap<String, ObservableEntityFieldType<E, ?>> getIdentityFields() {
		return theIdFields;
	}

	@Override
	public ObservableEntity<? extends E> observableEntity(EntityIdentity<E> id) throws EntityOperationException {
		// First, just lock for read and see if we already have it
		try (Transaction t = theEntitySet.lock(false, null)) { // Updates to existing entities are fine
			WeakReference<ObservableEntityImpl<? extends E>> entityRef = theEntitiesById.get(id);
			if (entityRef != null) {
				ObservableEntity<? extends E> entity = entityRef.get();
				if (entity != null)
					return entity;
			}
		}
		try (Transaction t = theEntitySet.lock(true, null)) {
			clearCollectedEntities();
			MapEntryHandle<EntityIdentity<E>, WeakReference<ObservableEntityImpl<? extends E>>> entityEntry = theEntitiesById.getEntry(id);
			WeakReference<ObservableEntityImpl<? extends E>> entityRef = entityEntry == null ? null : entityEntry.get();
			if (entityRef != null) {
				ObservableEntity<? extends E> entity = entityRef.get();
				if (entity != null)
					return entity;
			}
			return select().entity(id).query().collect(false).dispose().peekFirst();
		}
	}

	@Override
	public ObservableEntityImpl<? extends E> observableEntity(E entity) {
		if (theReflector == null)
			throw new IllegalStateException("This entity is not represented by a java type");
		return (ObservableEntityImpl<? extends E>) theReflector.getAssociated(entity, this);
	}

	@Override
	public <F> ObservableEntityFieldType<E, F> getField(Function<? super E, F> fieldGetter) {
		if (theReflector == null)
			throw new IllegalStateException("This entity type is not mapped to a java entity, so this method cannot be used");
		ReflectedField<E, F> reflectorField = theReflector.getField(fieldGetter);
		return (ObservableEntityFieldType<E, F>) theFields.get(reflectorField.getFieldIndex());
	}

	void check() {
		for (ObservableEntityFieldType<E, ?> field : theFields.allValues())
			((FieldTypeImpl<E, ?>) field).check();
	}

	@Override
	public EntityCondition.All<E> select() {
		return new EntityCondition.All<>(this, new EntityCondition.SelectionMechanism<E>() {
			@Override
			public ConfigurableQuery<E> query(EntityCondition<E> selection) {
				return new ConfigurableQueryImpl<>(selection);
			}

			@Override
			public ConfigurableUpdate<E> update(EntityCondition<E> selection) {
				return new ConfigurableUpdateImpl<>(selection);
			}

			@Override
			public ConfigurableDeletion<E> delete(EntityCondition<E> selection) {
				return new ConfigurableDeletionImpl<>(selection);
			}
		}, Collections.emptyMap());
	}

	@Override
	public ConfigurableCreator<E> create() {
		QuickMap<String, Object> values = theFields.keySet().<Object> createMap().fill(EntityUpdate.NOT_SET);
		for (int f = 0; f < values.keySize(); f++) {
			Object defaultValue = getDefault(theFields.get(f));
			if (defaultValue != EntityUpdate.NOT_SET)
				values.put(f, defaultValue);
		}
		return new ConfigurableCreatorImpl<>(this, QuickMap.empty(), values.unmodifiable(), //
			theFields.keySet().<EntityOperationVariable<E>> createMap().unmodifiable());
	}

	private Object getDefault(ObservableEntityFieldType<E, ?> field) {
		if (!field.getFieldType().isPrimitive())
			return EntityUpdate.NOT_SET;
		Class<?> prim = TypeTokens.getRawType(TypeTokens.get().unwrap(field.getFieldType()));
		if (prim == boolean.class)
			return Boolean.FALSE;
		else if (prim == char.class)
			return Character.valueOf((char) 0);
		else if (prim == byte.class)
			return Byte.valueOf((byte) 0);
		else if (prim == short.class)
			return Short.valueOf((short) 0);
		else if (prim == int.class)
			return Integer.valueOf(0);
		else if (prim == long.class)
			return Long.valueOf(0);
		else if (prim == float.class)
			return Float.valueOf(0);
		else if (prim == double.class)
			return Double.valueOf(0);
		else
			throw new IllegalStateException("Unrecognized primitive type: " + field.getFieldType());
	}

	ObservableEntityImpl<? extends E> getIfPresent(EntityIdentity<? extends E> id) {
		WeakReference<ObservableEntityImpl<? extends E>> entityRef = theEntitiesById.get(id);
		return entityRef == null ? null : entityRef.get();
	}

	ObservableEntityImpl<E> getOrCreate(EntityIdentity<E> id) {
		WeakReference<ObservableEntityImpl<? extends E>> entityRef = theEntitiesById.get(id);
		ObservableEntityImpl<E> entity = (ObservableEntityImpl<E>) (entityRef == null ? null : entityRef.get());
		if (entity == null) {
			entity = new ObservableEntityImpl<>(this, id);
			entityRef = new WeakReference<>(entity, theCollectedEntities);
			theEntitiesByRef.put(entityRef, theEntitiesById.putEntry(id, entityRef, false).getElementId());
		}
		return entity;
	}

	void handleChange(EntityChange<?> change, Map<EntityIdentity<?>, ObservableEntity<?>> entities, boolean fromSuper, boolean fromSub) {
		clearCollectedEntities();
		boolean propagateUp = false, propagateDown = false;
		switch (change.changeType) {
		case add:
			break; // Don't care
		case remove:
			propagateUp = propagateDown = true;
			for (EntityIdentity<?> id : change.getEntities()) {
				if (isAssignableFrom(id.getEntityType())) {
					WeakReference<ObservableEntityImpl<? extends E>> entityRef = theEntitiesById.remove(id);
					if (entityRef != null)
						theEntitiesByRef.remove(entityRef);
				}
			}
			break;
		case setField:
			EntityChange.EntityFieldValueChange<E> fieldValueChange = (EntityChange.EntityFieldValueChange<E>) change;
			int entityIdx = 0;
			for (EntityIdentity<?> id : change.getEntities()) {
				if (isAssignableFrom(id.getEntityType())) {
					WeakReference<ObservableEntityImpl<? extends E>> entityRef = theEntitiesById.remove(id);
					ObservableEntityImpl<? extends E> entity = entityRef == null ? null : entityRef.get();
					if (entity != null) {
						for (FieldChange<E, ?> fieldChange : fieldValueChange.getFieldChanges()) {
							FieldChange<E, Object> fc = (FieldChange<E, Object>) fieldChange;
							entity._set(fc.getField(), fc.getOldValues().get(entityIdx), fc.getNewValue());
						}
					}
				}
				entityIdx++;
			}
			break;
		case updateCollectionField:
			// TODO
		case updateMapField:
			// TODO
		}
		if (propagateDown && !fromSub) {
			for (ObservableEntityTypeImpl<? extends E> sub : theSubs)
				sub.handleChange(change, entities, true, false);
		}
		if (propagateUp && !fromSuper) {
			for (ObservableEntityTypeImpl<? super E> sup : theSupers)
				sup.handleChange(change, entities, false, true);
		}
	}

	void trackEntity(ObservableEntityImpl<? extends E> entity) {
		WeakReference<ObservableEntityImpl<? extends E>> entityRef = new WeakReference<>(entity, theCollectedEntities);
		theEntitiesById.put(fromSubId(entity.getId()), entityRef);
		for (ObservableEntityTypeImpl<? super E> superType : theSupers)
			superType.trackEntity(entity);
	}

	private void clearCollectedEntities() {
		Reference<?> ref = theCollectedEntities.poll();
		while (ref != null) {
			ElementId entity = theEntitiesByRef.remove(ref);
			if (entity != null)
				theEntitiesById.mutableEntry(entity).remove();
			ref = theCollectedEntities.poll();
		}
	}
}
