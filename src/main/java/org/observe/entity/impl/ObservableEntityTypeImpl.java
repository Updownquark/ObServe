package org.observe.entity.impl;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Function;

import org.observe.entity.EntityCreator;
import org.observe.entity.EntityIdentity;
import org.observe.entity.EntitySelection;
import org.observe.entity.EntityValueAccess;
import org.observe.entity.IdentityFieldType;
import org.observe.entity.ObservableEntity;
import org.observe.entity.ObservableEntityFieldType;
import org.observe.entity.ObservableEntityType;
import org.qommons.Transaction;
import org.qommons.ValueHolder;
import org.qommons.collect.BetterHashMap;
import org.qommons.collect.BetterMap;
import org.qommons.collect.ParameterSet;
import org.qommons.collect.ParameterSet.ParameterMap;
import org.qommons.collect.StampedLockingStrategy;

class ObservableEntityTypeImpl<E> implements ObservableEntityType<E> {
	private final ObservableEntitySetImpl theEntitySet;
	private final String theName;
	private final Class<E> theEntityType;
	private final ObservableEntityTypeImpl<? super E> theParent;
	private final ObservableEntityTypeImpl<? super E> theRoot;
	private final ParameterMap<IdentityFieldType<? super E, ?>> theIdFields;
	private final ParameterMap<ObservableEntityFieldType<? super E, ?>> theFields;
	private final BetterMap<EntityIdentity<? super E>, WeakReference<ObservableEntityImpl<? extends E>>> theEntitiesById;
	private final IdentityHashMap<E, WeakReference<ObservableEntityImpl<? extends E>>> theEntitiesByProxy;

	private final StampedLockingStrategy theLock;
	private final BetterMap<Method, MethodHandle> theDefaultMethods;
	private final E theProxy;
	private final MethodRetrievingHandler theProxyHandler;
	private final ParameterMap<ObservableEntityFieldType<? super E, ?>> theFieldsByGetter;

	ObservableEntityTypeImpl(ObservableEntitySetImpl entitySet, String name, Class<E> entityType,
		ObservableEntityTypeImpl<? super E> parent, ParameterMap<IdentityFieldType<? super E, ?>> idFields,
		ParameterMap<ObservableEntityFieldType<? super E, ?>> fields, E proxy, MethodRetrievingHandler handler) {
		theEntitySet = entitySet;
		theName = name;
		theEntityType = entityType;
		theParent = parent;
		if (parent != null) {
			theRoot = parent.getRoot();
			theLock = parent.theLock;
		} else {
			theRoot = this;
			theLock = new StampedLockingStrategy();
		}
		theIdFields = idFields;
		theFields = fields;
		theEntitiesById = BetterHashMap.build().unsafe().buildMap();
		theEntitiesByProxy = new IdentityHashMap<>();
		theDefaultMethods = BetterHashMap.build().buildMap();
		theProxy = proxy;
		theProxyHandler = handler;
		if (theProxy != null) {
			Map<String, ObservableEntityFieldType<? super E, ?>> getters = new HashMap<>();
			for (ObservableEntityFieldType<? super E, ?> field : fields.allValues()) {
				if (((ObservableEntitySetImpl.FieldTypeImpl<?, ?>) field).getFieldGetter() != null)
					getters.put(((ObservableEntitySetImpl.FieldTypeImpl<?, ?>) field).getFieldGetter().getName(), field);
			}
			ParameterSet getterNames = ParameterSet.of(getters.keySet());
			ParameterMap<ObservableEntityFieldType<? super E, ?>> fieldsByGetter = getterNames.createMap();
			for (int i = 0; i < getterNames.size(); i++)
				fieldsByGetter.put(i, getters.get(getterNames.get(i)));
			theFieldsByGetter = fieldsByGetter.unmodifiable();
		} else
			theFieldsByGetter = null;
	}

	@Override
	public boolean isLockSupported() {
		return true;
	}

	@Override
	public Transaction lock(boolean write, boolean structural, Object cause) {
		// It's split like this in case I ever decide that more work needs to be done on locking the root than just the lock itself
		if (theRoot != this)
			return theRoot.lock(write, structural, cause);
		else
			return theLock.lock(write, structural, cause);
	}

	@Override
	public Transaction tryLock(boolean write, boolean structural, Object cause) {
		// It's split like this in case I ever decide that more work needs to be done on locking the root than just the lock itself
		if (theRoot != this)
			return theRoot.tryLock(write, structural, cause);
		else
			return theLock.tryLock(write, structural, cause);
	}

	@Override
	public ObservableEntityTypeImpl<? super E> getParent() {
		return theParent;
	}

	@Override
	public ObservableEntityTypeImpl<? super E> getRoot() {
		return theRoot;
	}

	@Override
	public String getEntityName() {
		return theName;
	}

	@Override
	public Class<E> getEntityType() {
		return theEntityType;
	}

	@Override
	public ParameterMap<ObservableEntityFieldType<? super E, ?>> getFields() {
		return theFields;
	}

	@Override
	public ParameterMap<IdentityFieldType<? super E, ?>> getIdentityFields() {
		return theIdFields;
	}

	@Override
	public ObservableEntity<? extends E> observableEntity(EntityIdentity<? super E> id) {
		// First, just lock for read and see if we already have it
		try (Transaction t = lock(false, true, null)) { // Updates to existing entities are fine
			WeakReference<ObservableEntityImpl<? extends E>> entityRef = theEntitiesById.get(id);
			if (entityRef != null) {
				ObservableEntity<? extends E> entity = entityRef.get();
				if (entity != null)
					return entity;
			}
		}
		try (Transaction t = lock(true, null)) {
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
		try (Transaction t = lock(false, true, null)) {
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
	public <F> EntityValueAccess<E, F> fieldValue(ObservableEntityFieldType<? super E, F> field) {
		// TODO Auto-generated method stub
	}

	@Override
	public <F> ObservableEntityFieldType<? super E, F> getField(Function<? super E, F> fieldGetter) {
		// TODO Auto-generated method stub
	}

	@Override
	public EntitySelection<E> select() {
		return new EntitySelectionImpl<>(this, ParameterSet.EMPTY.createMap());
	}

	@Override
	public EntityCreator<E> create() {}

	MethodHandle getDefaultMethod(Method method) {
		return theDefaultMethods.computeIfAbsent(method, m -> {
			try {
				return MethodHandles.lookup().in(theEntityType).unreflectSpecial(method, theEntityType);
			} catch (IllegalAccessException e) {
				throw new IllegalStateException("Could not access method " + method, e);
			}
		});
	}

	private ObservableEntityImpl<? extends E> pullEntity(EntityIdentity<? super E> id) {}
}
