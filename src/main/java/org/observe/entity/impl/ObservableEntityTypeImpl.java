package org.observe.entity.impl;

import java.lang.ref.WeakReference;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.function.Function;

import org.observe.entity.EntityConstraint;
import org.observe.entity.EntityCreator;
import org.observe.entity.EntityIdentity;
import org.observe.entity.EntitySelection;
import org.observe.entity.ObservableEntity;
import org.observe.entity.ObservableEntityDataSet;
import org.observe.entity.ObservableEntityFieldType;
import org.observe.entity.ObservableEntityType;
import org.observe.entity.impl.ObservableEntityDataSetImpl.FieldTypeImpl;
import org.observe.util.EntityReflector;
import org.observe.util.EntityReflector.ReflectedField;
import org.observe.util.TypeTokens;
import org.qommons.Transaction;
import org.qommons.ValueHolder;
import org.qommons.collect.BetterHashMap;
import org.qommons.collect.BetterMap;
import org.qommons.collect.QuickSet;
import org.qommons.collect.QuickSet.QuickMap;

class ObservableEntityTypeImpl<E> implements ObservableEntityType<E> {
	private final ObservableEntityDataSetImpl theEntitySet;
	private final String theName;
	private final List<ObservableEntityTypeImpl<? super E>> theSupers;
	private final QuickMap<String, ObservableEntityFieldType<E, ?>> theFields;
	private final QuickMap<String, ObservableEntityFieldType<E, ?>> theIdFields;
	private final BetterMap<EntityIdentity<? super E>, WeakReference<ObservableEntityImpl<? extends E>>> theEntitiesById;
	private final IdentityHashMap<E, WeakReference<ObservableEntityImpl<? extends E>>> theEntitiesByProxy;
	private final EntityReflector<E> theReflector;
	private final List<EntityConstraint<E>> theConstraints;

	ObservableEntityTypeImpl(ObservableEntityDataSetImpl entitySet, String name, List<ObservableEntityTypeImpl<? super E>> supers,
		QuickMap<String, ObservableEntityFieldType<E, ?>> fields, QuickMap<String, ObservableEntityFieldType<E, ?>> idFields,
		EntityReflector<E> reflector, List<EntityConstraint<E>> constraints) {
		theEntitySet = entitySet;
		theName = name;
		theSupers = supers;
		theIdFields = idFields;
		theFields = fields;
		theReflector = reflector;
		theConstraints = constraints;
		theEntitiesById = BetterHashMap.build().unsafe().buildMap();
		theEntitiesByProxy = new IdentityHashMap<>();
	}

	@Override
	public ObservableEntityDataSet getEntitySet() {
		return theEntitySet;
	}

	@Override
	public List<? extends ObservableEntityType<? super E>> getSupers() {
		return theSupers;
	}

	@Override
	public List<EntityConstraint<E>> getConstraints() {
		return theConstraints;
	}

	@Override
	public String getEntityName() {
		return theName;
	}

	@Override
	public Class<E> getEntityType() {
		return theReflector == null ? null : TypeTokens.getRawType(theReflector.getType());
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
	public ObservableEntity<? extends E> observableEntity(EntityIdentity<? super E> id) {
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
			ValueHolder<ObservableEntity<? extends E>> result = new ValueHolder<>();
			theEntitiesById.compute(id, (_id, entityRef) -> {
				ObservableEntityImpl<? extends E> entity;
				if (entityRef != null) {
					entity = entityRef.get();
					if (entity != null) {
						result.accept(entity);
						return entityRef;
					}
				}
				entity = pullEntity(_id);
				return entity == null ? null : new WeakReference<>(entity);
			});
			return result.get();
		}
	}

	@Override
	public ObservableEntityImpl<? extends E> observableEntity(E entity) {
		try (Transaction t = theEntitySet.lock(false, null)) {
			// This should be pretty straightforward. If the caller has a reference to the entity,
			// then the ObservableEntity can't have been GC'd.
			// The only way this could fail is if the caller has synthesized the entity implementation by external means, which is illegal.
			WeakReference<ObservableEntityImpl<? extends E>> entityRef = theEntitiesByProxy.get(entity);
			if (entityRef == null)
				throw new IllegalArgumentException("Entity " + entity + " has been synthesized by exernal means, which is illegal!");
			ObservableEntityImpl<? extends E> obsEntity = entityRef.get();
			if (obsEntity == null)
				throw new IllegalArgumentException("Entity " + entity + " has been synthesized by exernal means, which is illegal!");
			return obsEntity;
		}
	}

	@Override
	public <F> ObservableEntityFieldType<E, F> getField(Function<? super E, F> fieldGetter) {
		if (theReflector == null)
			throw new IllegalStateException("This entity type is not mapped to a java entity, so this method cannot be used");
		ReflectedField<E, F> reflectorField = theReflector.getField(fieldGetter);
		return (ObservableEntityFieldType<E, F>) theFields.get(reflectorField.getFieldIndex());
	}

	@Override
	public EntitySelection<E> select() {
		return new EntitySelectionImpl<>(this, QuickSet.<String> empty().createMap());
	}

	@Override
	public EntityCreator<E> create() {
		return new EntityCreatorImpl<>(this, QuickSet.<String> empty().createMap(), theIdFields.keySet().createMap(),
			theFields.keySet().createMap());
	}

	private ObservableEntityImpl<? extends E> pullEntity(EntityIdentity<? super E> id) {}

	void check() {
		for (ObservableEntityFieldType<E, ?> field : theFields.allValues())
			((FieldTypeImpl<E, ?>) field).check();
	}
}
