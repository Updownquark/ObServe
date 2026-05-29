package org.observe.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.Observable;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableSortedSet;
import org.observe.config.ConfiguredValueField;
import org.observe.config.ConfiguredValueType;
import org.observe.config.SyncValueCreator;
import org.observe.config.SyncValueSet;
import org.observe.util.EntityReflector;
import org.observe.util.TypeTokens;
import org.qommons.Causable;
import org.qommons.QommonsUtils;
import org.qommons.Subscription;
import org.qommons.ThreadConstraint;
import org.qommons.Transaction;
import org.qommons.ValueHolder;
import org.qommons.collect.BetterSortedList.SortedSearchFilter;
import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.CollectionLockingStrategy;
import org.qommons.collect.ElementId;
import org.qommons.collect.ListenerList;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.data.impl.InMemoryEntitySet;
import org.qommons.data.mapping.EntityTypeMapping;
import org.qommons.data.mapping.EntityTypeSetMapping;
import org.qommons.data.migration.MigrationUtil;
import org.qommons.data.types.EntityField;
import org.qommons.data.types.EntityType;
import org.qommons.data.types.EntityTypeSet;
import org.qommons.data.types.FieldMapping;
import org.qommons.data.types.FieldType;
import org.qommons.data.values.GenericEntity;

import com.google.common.reflect.TypeToken;

/** {@link ObservableEntityDataSource} using a generic QommonData {@link EntityTypeSet} and {@link EntityReflector}s for each entity type */
public class ReflectedEntitySet extends InMemoryEntitySet implements ObservableEntityDataSource {
	private final Map<Class<?>, ReflectedEntityValueType<?>> theValueTypes;
	private final Map<String, ReflectedEntityValueType<?>> theValueTypesByName;
	private final Map<Class<?>, ReflectedRootValueSet<?>> theValueSets;
	private long theStamp;
	private final ListenerList<Consumer<? super Causable>> theChangeListeners;
	private final Observable<?> theUntil;
	private final Causable.CausableKey theChangeKey;

	/**
	 * @param dataTypes The generic mapped entity type set
	 * @param reflectors The cache containing reflectors for each entity type
	 * @param locking The locking strategy for this entity set
	 * @param until An observable to destroy all actively-maintained structures associated with this entity set
	 */
	public ReflectedEntitySet(EntityTypeSetMapping dataTypes, Map<TypeToken<?>, EntityReflector<?>> reflectors,
		Function<? super ReflectedEntitySet, ? extends CollectionLockingStrategy> locking, Observable<?> until) {
		super(dataTypes.getGenericTypes(),
			locking == null ? null : (Function<? super InMemoryEntitySet, ? extends CollectionLockingStrategy>) locking);
		theValueTypes = new HashMap<>();
		theValueTypesByName = new HashMap<>();
		theValueSets = new HashMap<>();
		theChangeListeners = ListenerList.build().build();
		theUntil = until == null ? Observable.empty : until;
		// Persistence (the most common listener) may cause changes in the entities such as replacing blobs with persistent instances.
		// These events can be ignored.
		boolean[] persisting = new boolean[1];
		theChangeKey = Causable.key((cause, data) -> {
			if (!persisting[0]) {
				persisting[0] = true;
				try {
					theChangeListeners.forEach(//
						l -> l.accept(cause));
				} finally {
					persisting[0] = false;
				}
			}
		});

		for (EntityTypeMapping<?> type : dataTypes.getEntityTypes().values())
			getOrCreateValueType(type, reflectors);
	}

	private <E> ReflectedEntityValueType<E> getOrCreateValueType(EntityTypeMapping<E> type,
		Map<TypeToken<?>, EntityReflector<?>> reflectors) {
		ReflectedEntityValueType<E> valueType = (ReflectedEntityValueType<E>) theValueTypes.get(type.getRealType());
		if (valueType != null)
			return valueType;
		List<ReflectedEntityValueType<? super E>> supers = Collections.emptyList();
		for (EntityType superType : type.getGenericType().getSuperTypes()) {
			if (supers.isEmpty())
				supers = new ArrayList<>();
			supers.add(getOrCreateValueType((EntityTypeMapping<? super E>) type.getTypeSet().getEntityTypes().get(superType.getName()),
				reflectors));
		}
		valueType = new ReflectedEntityValueType<>(QommonsUtils.unmodifiableCopy(supers), type.getGenericType(),
			(EntityReflector<E>) reflectors.get(TypeTokens.get().of(type.getRealType())));
		theValueTypes.put(type.getRealType(), valueType);
		theValueTypesByName.put(type.getGenericType().getName(), valueType);
		return valueType;
	}

	@Override
	protected CollectionLockingStrategy getLock() { // Overridden to expose this to this package
		return super.getLock();
	}

	/**
	 * @param onChange A listener to be called when a set of changes to entities in this set ends
	 * @return A subscription to unsubscribe the listener
	 */
	public Subscription onChange(Consumer<? super Causable> onChange) {
		return theChangeListeners.add(onChange, true);
	}

	@Override
	protected void entityAffected(EntityType entityType) {
		Causable cause = getLock().getRootCausable();
		if (cause == null) {
			try (Causable.CausableInUse c = Causable.cause()) {
				c.onFinish(theChangeKey);
				super.entityAffected(entityType);
			}
		} else {
			super.entityAffected(entityType);
			cause.onFinish(theChangeKey);
		}
	}

	/** @return The observable that will destroy this entity set and all actively-maintained structures in it */
	public Observable<?> getUntil() {
		return theUntil;
	}

	@Override
	public Transaction lock(boolean tryOnly) {
		return getLock().lock(tryOnly);
	}

	@Override
	public Transaction lockWrite(boolean tryOnly, Object cause) {
		return getLock().lockWrite(tryOnly, cause);
	}

	@Override
	public CoreId getCoreId() {
		return getLock().getCoreId();
	}

	@Override
	public ThreadConstraint getThreadConstraint() {
		return getLock().getThreadConstraint();
	}

	@Override
	public long getStamp() {
		return theStamp;
	}

	@Override
	protected ObservableSortedSet<GenericEntity> createEntitySet(EntityType type, CollectionLockingStrategy locking) {
		return ObservableSortedSet.build(type).withLocking(locking).build();
	}

	@Override
	protected MappedEntity<?> createEntity(EntityType type, Object[] id) {
		return new MappedEntity<>(theValueTypesByName.get(type.getName()), this, id);
	}

	@Override
	protected void deleteEntity(GenericEntity entity) {
		theStamp++;
		super.deleteEntity(entity);
	}

	@Override
	protected void entityAffected(GenericEntity entity) {
		BetterSortedSet<GenericEntity> entities = getInternalEntities(entity.getType().getRootType());
		CollectionElement<GenericEntity> element = entities.getElement(entity, true);
		if (element != null)
			entities.mutableElement(element.getElementId()).set(element.get());
		theStamp++;
		super.entityAffected(entity);
	}

	@Override
	public <E> ReflectedEntityValueType<E> getType(Class<E> type) {
		return (ReflectedEntityValueType<E>) theValueTypes.get(type);
	}

	@Override
	public <E> SyncValueSet<E> observeEntities(Class<E> type) throws IllegalArgumentException {
		ReflectedEntityValueType<E> valueType = getType(type);
		if (valueType == null)
			throw new IllegalArgumentException("No such entity type: " + type.getName());
		try (Transaction t = lockWrite(false, null)) {
			return (SyncValueSet<E>) theValueSets.computeIfAbsent(type, __ -> new ReflectedRootValueSet<>(this, valueType));
		}
	}

	/**
	 * @param <E> The compile-time type of the entity to get
	 * @param type The run-time type of the entity to get
	 * @param id The ID values for the entity to get
	 * @return The entity in this set with the given type and ID
	 * @throws IllegalArgumentException If the given entity type is not supported by this entity set
	 */
	public <E> E getEntity(Class<E> type, Object... id) throws IllegalArgumentException {
		ReflectedEntityValueType<E> valueType = getType(type);
		if (valueType == null)
			throw new IllegalArgumentException("No such entity type: " + type.getName());
		try (Transaction t = lock(false)) {
			int f = 0;
			for (EntityField<?> field : valueType.getGenericType().getIdFields()) {
				if (field.getType() instanceof EntityType && id[f] != null)
					id[f] = EntityReflector.getAssociated(id[f], MappedEntity.ENTITY_ASSOC);
			}
			GenericEntity found = getEntity(valueType.getGenericType().getName(), id);
			return found == null ? null : ((MappedEntity<E>) found).getRealEntity();
		}
	}

	<O, E> SyncValueSet<E> createMemberValueSet(EntityType type, ObservableSortedSet<E> values, EntityField<GenericEntity> mappingField,
		GenericEntity owner) {
		ReflectedEntityValueType<E> entityType = (ReflectedEntityValueType<E>) theValueTypesByName.get(type.getName());
		ReflectedRootValueSet<E> rootValues = (ReflectedRootValueSet<E>) observeEntities(
			TypeTokens.getRawType(entityType.getReflector().getType()));
		if (mappingField == null)
			return new MemberValueSet<>(rootValues, values);
		O realOwner = ((MappedEntity<O>) owner).getRealEntity();
		return new MappedMemberValueSet<>(rootValues, values, realOwner, mappingField);
	}

	static class ReflectedRootValueSet<E> implements SyncValueSet<E> {
		private final ReflectedEntitySet theEntitySet;
		private final ReflectedEntityValueType<E> theType;
		private final ObservableSortedSet<E> theBaseValues;
		private final ObservableSortedSet<E> theExposedValues;

		ReflectedRootValueSet(ReflectedEntitySet entitySet, ReflectedEntityValueType<E> type) {
			theEntitySet = entitySet;
			theType = type;
			EntityType genericType = type.getGenericType();
			ObservableSortedSet<GenericEntity> rootGenericEntities = (ObservableSortedSet<GenericEntity>) entitySet
				.getInternalEntities(genericType.getRootType());
			ObservableCollection.DistinctSortedDataFlow<GenericEntity, ?, ?> realEntityFlow = rootGenericEntities.flow()//
				.mapEquivalent(e -> ((MappedEntity<?>) e).getRealEntity(),
					e -> (MappedEntity<?>) EntityReflector.getAssociated(e, MappedEntity.ENTITY_ASSOC));
			if (genericType.getSuperTypes().isEmpty())
				theBaseValues = (ObservableSortedSet<E>) realEntityFlow.collectPassive();
			else {
				theBaseValues = realEntityFlow//
					.filter(TypeTokens.getRawType(type.getType()))//
					.collectActive(entitySet.getUntil());
			}
			theExposedValues = theBaseValues.flow()//
				.filterMod(mod -> mod//
					.noAdd("Entities cannot be added this way.  Use " + SyncValueSet.class.getSimpleName() + ".create()")//
					.filterRemove(entity -> ((GenericEntity) EntityReflector.getAssociated(entity, MappedEntity.ENTITY_ASSOC)).canDelete())//
					)//
				.collectPassive();
			theBaseValues.onChange(evt -> {
				switch (evt.getType()) {
				case add: // Handled by the creator
					break;
				case remove:
					if (evt.getMovement() == null)
						((GenericEntity) EntityReflector.getAssociated(evt.getOldValue(), MappedEntity.ENTITY_ASSOC)).delete();
					else {
						evt.getMovement().onDiscard(__ -> {
							((GenericEntity) EntityReflector.getAssociated(evt.getOldValue(), MappedEntity.ENTITY_ASSOC)).delete();
						});
					}
					break;
				case set:
					break;
				}
			});
		}

		@Override
		public ReflectedEntityValueType<E> getType() {
			return theType;
		}

		public ReflectedEntitySet getEntitySet() {
			return theEntitySet;
		}

		@Override
		public ObservableSortedSet<E> getValues() {
			return theExposedValues;
		}

		@Override
		public <E2 extends E> ReflectedValueCreator<E2> create(TypeToken<E2> subType) {
			ReflectedEntityValueType<E2> subValueType = theEntitySet.getType(TypeTokens.getRawType(subType));
			if (subValueType == null)
				throw new IllegalArgumentException("No such type " + subType);
			else if (!theType.isAssignableFrom(subValueType))
				throw new IllegalArgumentException("Type " + subType + " does not extend this value type (" + theType.getType() + ")");
			return new ReflectedValueCreator<>(subValueType);
		}

		@Override
		public String toString() {
			return theExposedValues.toString();
		}

		class ReflectedValueCreator<E2 extends E> implements SyncValueCreator<E, E2> {
			private final ReflectedEntityValueType<E2> theSubType;
			private ElementId theAfter;
			private ElementId theBefore;
			private Boolean isTowardBeginning;
			private final ValueHolder<Object>[] theFieldValues;

			ReflectedValueCreator(ReflectedEntityValueType<E2> subType) {
				theSubType = subType;
				theFieldValues = new ValueHolder[subType.getFields().keySize()];
			}

			@Override
			public ReflectedEntityValueType<E2> getType() {
				return theSubType;
			}

			@Override
			public SyncValueCreator<E, E2> after(ElementId after) {
				theAfter = after;
				return this;
			}

			@Override
			public SyncValueCreator<E, E2> before(ElementId before) {
				theBefore = before;
				return this;
			}

			@Override
			public SyncValueCreator<E, E2> towardBeginning(boolean towardBeginning) {
				isTowardBeginning = towardBeginning;
				return this;
			}

			@Override
			public Set<Integer> getRequiredFields() {
				Set<Integer> required = Collections.emptySet();
				for (EntityField<?> field : theSubType.getGenericType().getIdFields()) {
					if (!MigrationUtil.isIncrementable(field.getType())) {
						if (required.isEmpty())
							required = new TreeSet<>();
						required.add(theSubType.getGenericType().indexOf(field));
					}
				}
				return required;
			}

			@Override
			public String isEnabled(ConfiguredValueField<? super E2, ?> field) {
				EntityField<?> genericField = theSubType.getGenericType().getFields().get(((ReflectedFieldType<?, ?, ?>) field).getIndex());
				if (genericField.getType() instanceof FieldType.ParameterizedType || genericField.getType() == FieldType.BLOB)
					return "The structure value of field " + field + " cannot be set, but its content may be changed";
				return null;
			}

			@Override
			public <F> String isAcceptable(ConfiguredValueField<? super E2, F> field, F value) {
				String enabled = isEnabled(field);
				if (enabled != null)
					return enabled;
				ReflectedFieldType<E2, ?, F> myField = (ReflectedFieldType<E2, ?, F>) field;
				if (myField.getGenericField().getIndexReference() != null)
					return "Field " + myField.getName() + " is controlled by " + myField.getGenericField().getIndexReference().parentField;
				else if (value == null) {
					if (myField.getGenericField().isId() && myField.getGenericField().getType() instanceof FieldType.SimpleType)
						return "null is not acceptable for simple ID fields";
					else
						return null;
				} else if (!TypeTokens.get().isInstance(myField.getFieldType(), value))
					return "Expected an instance of " + myField.getFieldType() + ", not " + value.getClass().getName();
				else if (myField.getGenericField().getType() instanceof EntityType) {
					// Make sure it's an entity from this entity set
					MappedEntity<?> entity = (MappedEntity<?>) EntityReflector.getAssociated(value, MappedEntity.ENTITY_ASSOC);
					if (entity == null || entity.getEntitySet() != getEntitySet()//
						|| !((EntityType) myField.getGenericField().getType()).isInstance(entity)//
						|| !getEntitySet().isMember(entity))
						return "This " + myField.getFieldType() + " is not a member of this entity set";
					else if (myField.getGenericField().getMappingReference() != null) {
						EntityField<?> refField = myField.getGenericField().getMappingReference().parentField;
						if (refField.getType() instanceof EntityType) {
							if (entity.get(refField) != null)
								return "Entity " + entity + "'s " + refField.getName() + " is already set";
						} else if (refField.getType() instanceof FieldType.MapType) {
							ReflectedFieldType<E2, Object, Object> keyField = (ReflectedFieldType<E2, Object, Object>) theSubType
								.getFields()
								.get(theSubType.getGenericType().indexOf(myField.getGenericField().getMappingReference().keyField));
							Object key = keyField.getGenericMapping().apply(theFieldValues[keyField.getIndex()]);
							if (((Map<Object, ?>) entity.get(refField)).containsKey(key))
								return "Entity " + value + "already has a " + refField.getName() + " entry with " + keyField.getName() + " "
								+ key;
						}
						EntityField<Object> sortBy = (EntityField<Object>) myField.getGenericField().getMappingReference().sortByField;
						if (sortBy != null && refField instanceof FieldType.CollectionType
							&& ((FieldType.CollectionType<?, ?>) refField).isDistinct) {
							ReflectedFieldType<E2, Object, Object> mySortBy = (ReflectedFieldType<E2, Object, Object>) theSubType
								.getFields().get(theSubType.getGenericType().indexOf(sortBy));
							Object sortValue = mySortBy.getGenericMapping().apply(theFieldValues[mySortBy.getIndex()]);
							BetterSortedSet<GenericEntity> collection = (BetterSortedSet<GenericEntity>) entity.get(refField);
							if (collection.search(e -> sortBy.getType().compare(sortValue, e.get(sortBy)),
								SortedSearchFilter.OnlyMatch) != null) {
								return "Entity " + value + "'s " + refField.getName() + " already has a value with " + sortBy.getName()
								+ " " + sortValue;
							}
						}
					}
				}
				for (FieldMapping<?, ?, ?> ref : myField.getGenericField().getAncillaryMappingReferences()) {
					if (ref.keyField == myField.getGenericField() && ref.parentField.getType() instanceof FieldType.MapType) {
						Object realEntity = theFieldValues[theSubType.getGenericType().indexOf(ref.mappedReferenceField)];
						if (realEntity != null) {
							MappedEntity<?> entity = (MappedEntity<?>) EntityReflector.getAssociated(realEntity, MappedEntity.ENTITY_ASSOC);
							Object key = myField.getGenericMapping().apply(value);
							if (entity != null && ((Map<Object, ?>) entity.get(ref.parentField)).containsKey(key))
								return "Entity " + realEntity + "already has a " + ref.parentField.getName() + " entry with "
								+ myField.getName() + " " + key;
						}
					}
				}
				return null;
			}

			@Override
			public <F> SyncValueCreator<E, E2> with(ConfiguredValueField<E2, F> field, F value) throws IllegalArgumentException {
				String msg = isAcceptable(field, value);
				if (msg != null)
					throw new IllegalArgumentException(msg);
				theFieldValues[field.getIndex()] = new ValueHolder<>(value);
				return this;
			}

			@Override
			public String canCreate() {
				return canCreate(new Object[theSubType.getGenericType().getIdFields().size()]);
			}

			@Override
			public CollectionElement<E> create(Consumer<? super E2> preAddAction) {
				Object[] id = new Object[theSubType.getGenericType().getIdFields().size()];
				String canCreate = canCreate(id);
				if (canCreate != null)
					throw new IllegalStateException(canCreate);
				// Creating using the protected method, so it's not added yet
				MappedEntity<E2> entity = (MappedEntity<E2>) getEntitySet().createEntity(theSubType.getGenericType(), id);
				for (int f = 0; f < theFieldValues.length; f++) {
					if (theSubType.getFields().get(f).getGenericField().isId())
						continue;
					ValueHolder<?> holder = theFieldValues[f];
					if (holder != null)
						entity.set(theSubType.genericToReflected().toDest(f), holder.get());
				}
				if (preAddAction != null)
					preAddAction.accept(entity.getRealEntity());
				CollectionElement<GenericEntity> added = getEntitySet().addEntity(entity);
				return getValues()
					.getElementsBySource(added.getElementId(), getEntitySet().getInternalEntities(theSubType.getGenericType())).getFirst();
			}

			private String canCreate(Object[] id) {
				String msg = getId(id);
				if (msg != null)
					return msg;
				// We've already checked all the arguments.
				// It's possible that a referenced entity has changed in a way that will cause this creator to throw an exception,
				// but it's not worth re-checking for that case.
				return null;
			}

			private String getId(Object[] id) {
				// If we get here, all the missing ID values are simple, since those are required
				int i = 0;
				int missingIdx = -1;
				FieldType<?> missingIdFieldType = null;
				BetterSortedSet<GenericEntity> entities = getEntitySet().getInternalEntities(theSubType.getGenericType().getRootType());
				GenericEntity after = theAfter == null ? null : (GenericEntity) EntityReflector.getAssociated(//
					getValues().getElement(theAfter).get(), MappedEntity.ENTITY_ASSOC);
				GenericEntity before = theBefore == null ? null : (GenericEntity) EntityReflector.getAssociated(//
					getValues().getElement(theBefore).get(), MappedEntity.ENTITY_ASSOC);
				Object afterBound = null, beforeBound = null;
				boolean afterRelevant = after != null, afterMatches = afterRelevant;
				boolean beforeRelevant = before != null, beforeMatches = beforeRelevant;
				for (EntityField<?> field : theSubType.getGenericType().getIdFields()) {
					int fieldIndex = theSubType.getGenericType().indexOf(field);
					if (theFieldValues[fieldIndex] != null) {
						Object value = theFieldValues[fieldIndex].get();
						if (value != null && field.getMappingReference() != null
							&& field.getMappingReference().parentField.getType() instanceof EntityType) {
							// Make sure the target entity's mapped field is empty
							// Parameterized types are not allowed for ID fields, so we know this is just an entity type field
							GenericEntity genericValue = (GenericEntity) EntityReflector.getAssociated(value, MappedEntity.ENTITY_ASSOC);
							Object entityValue = genericValue.get(field.getMappingReference().parentField);
							if (entityValue != null)
								return theSubType.getFields().get(fieldIndex).getFieldType() + " " + value + "'s "
								+ field.getMappingReference().parentField.getName() + " is already populated";
						}
						Comparator<Object> fieldSort = (Comparator<Object>) field.getType();
						if (afterRelevant) {
							int comp = fieldSort.compare(value, after.get(field));
							if (comp < 0)
								return StdMsg.ILLEGAL_ELEMENT_POSITION;
							else if (comp > 0) {
								afterMatches = false;
								if (missingIdx < 0)
									afterRelevant = false;
							}
						}
						if (beforeRelevant) {
							int comp = fieldSort.compare(value, before.get(field));
							if (comp > 0)
								return StdMsg.ILLEGAL_ELEMENT_POSITION;
							else if (comp < 0) {
								beforeMatches = false;
								if (missingIdx < 0)
									beforeRelevant = false;
							}
						}
						id[i] = ((Function<Object, ?>) theSubType.getFields().get(fieldIndex).getGenericMapping()).apply(value);
						i++;
					} else if (missingIdx >= 0) {
						return "Only a single ID field may remain unspecified";
					} else if (MigrationUtil.isIncrementable(field.getType())) {
						missingIdx = i++;
						missingIdFieldType = field.getType();
						if (afterRelevant)
							afterBound = after.get(field);
						if (beforeRelevant)
							beforeBound = before.get(field);
					} else
						return "Required field " + field + " is not specified";
				}
				if (missingIdx < 0) { // All ID fields are specified
					if (afterMatches || beforeMatches || !isAvailable(entities, id))
						return StdMsg.ELEMENT_EXISTS;
					else
						return null;
				} else if (entities.isEmpty()) { // No entities of this type exist. Create an initial ID.
					id[missingIdx] = MigrationUtil.getInitialValue(missingIdFieldType);
					return null;
				}
				if (isTowardBeginning == null || !isTowardBeginning) {
					if (beforeBound == null) {
						id[missingIdx] = entities.getLast().get(theSubType.getGenericType().getIdFields().get(missingIdx));
						if (isAvailable(entities, id))
							return null;
						Object[] adj = getAdjacentAvailableId(entities, id, missingIdx, missingIdFieldType, true, null, false);
						if (adj != null) {
							id[missingIdx] = adj[missingIdx];
							return null;
						}
						adj = getAdjacentAvailableId(entities, id, missingIdx, missingIdFieldType, false, afterBound, afterMatches);
						if (adj != null) {
							id[missingIdx] = adj[missingIdx];
							return null;
						} else if (afterBound != null)
							return "No IDs available in selected range";
						else
							return "Unspecified ID space is full--no more values possible for this type";
					} else {
						id[missingIdx] = beforeBound;
						if (!beforeMatches && isAvailable(entities, id))
							return null;
						Object[] adj = getAdjacentAvailableId(entities, id, missingIdx, missingIdFieldType, false, afterBound,
							afterMatches);
						if (adj != null) {
							id[missingIdx] = adj[missingIdx];
							return null;
						} else
							return "Unspecified ID space is full--no more values possible for this type";
					}
				} else {
					if (afterBound == null) {
						id[missingIdx] = entities.getFirst().get(theSubType.getGenericType().getIdFields().get(missingIdx));
						if (isAvailable(entities, id))
							return null;
						Object[] adj = getAdjacentAvailableId(entities, id, missingIdx, missingIdFieldType, false, null, false);
						if (adj != null) {
							id[missingIdx] = adj[missingIdx];
							return null;
						}
						adj = getAdjacentAvailableId(entities, id, missingIdx, missingIdFieldType, true, beforeBound, beforeMatches);
						if (adj != null) {
							id[missingIdx] = adj[missingIdx];
							return null;
						} else if (beforeBound != null)
							return "No IDs available in selected range";
						else
							return "Unspecified ID space is full--no more values possible for this type";
					} else {
						id[missingIdx] = afterBound;
						if (!afterMatches && isAvailable(entities, id))
							return null;
						Object[] adj = getAdjacentAvailableId(entities, id, missingIdx, missingIdFieldType, true, beforeBound,
							beforeMatches);
						if (adj != null) {
							id[missingIdx] = adj[missingIdx];
							return null;
						} else
							return "Unspecified ID space is full--no more values possible for this type";
					}
				}
			}

			private boolean isAvailable(BetterSortedSet<GenericEntity> entities, Object[] id) {
				return entities.search(e -> -e.compareToId(id), SortedSearchFilter.OnlyMatch) == null;
			}

			private <F> Object[] getAdjacentAvailableId(BetterSortedSet<GenericEntity> entities, Object[] id, int index,
				FieldType<F> fieldType, boolean next, Object bound, boolean exclusive) {
				Object[] adj = id.clone();
				while (true) {
					Object adjI = MigrationUtil.adjust(fieldType, adj[index], next);
					if (adjI == null)
						return null;
					adj[index] = adjI;
					if (bound != null && bound.equals(adjI)) {
						if (!exclusive && isAvailable(entities, adj))
							return adj;
						return null;
					}
					if (isAvailable(entities, adj))
						return adj;
				}
			}

			@Override
			public String toString() {
				return theSubType + " creator";
			}
		}
	}

	static class MemberValueSet<E> implements SyncValueSet<E> {
		private final ReflectedRootValueSet<E> theRootValues;
		private final ObservableSortedSet<E> theValues;

		MemberValueSet(ReflectedRootValueSet<E> rootValues, ObservableSortedSet<E> values) {
			theRootValues = rootValues;
			theValues = values;
		}

		@Override
		public ReflectedEntityValueType<E> getType() {
			return theRootValues.getType();
		}

		protected ReflectedRootValueSet<E> getRootValues() {
			return theRootValues;
		}

		@Override
		public ObservableSortedSet<E> getValues() {
			return theValues;
		}

		@Override
		public <E2 extends E> SyncValueCreator<E, E2> create(TypeToken<E2> subType) {
			return new MemberValueCreator<>(theRootValues.create(subType), theRootValues.getValues(), theValues);
		}

		@Override
		public String toString() {
			return theValues.toString();
		}
	}

	static class MemberValueCreator<E, E2 extends E> implements SyncValueCreator<E, E2> {
		private final SyncValueCreator<E, E2> theRootCreator;
		private final ObservableSortedSet<E> theRootMembers;
		private final ObservableSortedSet<E> theMembers;

		MemberValueCreator(SyncValueCreator<E, E2> rootCreator, ObservableSortedSet<E> rootMembers, ObservableSortedSet<E> members) {
			theRootCreator = rootCreator;
			theRootMembers = rootMembers;
			theMembers = members;
		}

		@Override
		public ConfiguredValueType<E2> getType() {
			return theRootCreator.getType();
		}

		protected ObservableSortedSet<E> getRootMembers() {
			return theRootMembers;
		}

		protected ObservableSortedSet<E> getMembers() {
			return theMembers;
		}

		@Override
		public SyncValueCreator<E, E2> after(ElementId after) {
			theRootCreator.after(theRootMembers.getElement(theMembers.getElement(after).get(), true).getElementId());
			return this;
		}

		@Override
		public SyncValueCreator<E, E2> before(ElementId before) {
			theRootCreator.before(theRootMembers.getElement(theMembers.getElement(before).get(), true).getElementId());
			return this;
		}

		@Override
		public SyncValueCreator<E, E2> towardBeginning(boolean towardBeginning) {
			theRootCreator.towardBeginning(towardBeginning);
			return this;
		}

		@Override
		public Set<Integer> getRequiredFields() {
			return theRootCreator.getRequiredFields();
		}

		@Override
		public String isEnabled(ConfiguredValueField<? super E2, ?> field) {
			return theRootCreator.isEnabled(field);
		}

		@Override
		public <F> String isAcceptable(ConfiguredValueField<? super E2, F> field, F value) {
			return theRootCreator.isAcceptable(field, value);
		}

		@Override
		public <F> SyncValueCreator<E, E2> with(ConfiguredValueField<E2, F> field, F value) throws IllegalArgumentException {
			theRootCreator.with(field, value);
			return this;
		}

		@Override
		public String canCreate() {
			return theRootCreator.canCreate();
		}

		@Override
		public CollectionElement<E> create(Consumer<? super E2> preAddAction) {
			CollectionElement<E> created = theRootCreator.create(preAddAction);
			return theMembers.getOrAdd(created.get(), null, null, false, null, null);
		}
	}

	static class MappedMemberValueSet<O, E> extends MemberValueSet<E> {
		private final O theOwner;
		private final EntityField<GenericEntity> theMappedReferenceField;

		MappedMemberValueSet(ReflectedRootValueSet<E> rootValues, ObservableSortedSet<E> values, O owner,
			EntityField<GenericEntity> mappedReferenceField) {
			super(rootValues, values);
			theOwner = owner;
			theMappedReferenceField = mappedReferenceField;
		}

		@Override
		public <E2 extends E> SyncValueCreator<E, E2> create(TypeToken<E2> subType) {
			ReflectedRootValueSet<E>.ReflectedValueCreator<E2> rootCreator = getRootValues().create(subType);
			int mappedFieldIndex = rootCreator.getType().getGenericType().indexOf(theMappedReferenceField);
			ReflectedFieldType<E2, GenericEntity, O> mappedReferenceField = (ReflectedFieldType<E2, GenericEntity, O>) rootCreator.getType()
				.getFields().get(mappedFieldIndex);
			return new MappedMemberValueCreator<>(rootCreator, getRootValues().getValues(), getValues(), theOwner, mappedReferenceField,
				mappedFieldIndex);
		}
	}

	static class MappedMemberValueCreator<O, E, E2 extends E> extends MemberValueCreator<E, E2> {
		private final ReflectedFieldType<E2, GenericEntity, O> theMappedReferenceField;
		private final Set<Integer> theRequiredFields;

		MappedMemberValueCreator(SyncValueCreator<E, E2> rootCreator, ObservableSortedSet<E> rootMembers, ObservableSortedSet<E> members,
			O owner, ReflectedFieldType<E2, GenericEntity, O> mappedReferenceField, int mappedReferenceFieldIndex) {
			super(rootCreator, rootMembers, members);
			theMappedReferenceField = mappedReferenceField;
			super.with(mappedReferenceField, owner);
			// Don't require the field we're overriding
			NavigableSet<Integer> required = new TreeSet<>(super.getRequiredFields());
			required.remove(mappedReferenceFieldIndex);
			theRequiredFields = Collections.unmodifiableNavigableSet(required);
		}

		@Override
		public Set<Integer> getRequiredFields() {
			return theRequiredFields;
		}

		@Override
		public String isEnabled(ConfiguredValueField<? super E2, ?> field) {
			if (field.isAssignableFrom(theMappedReferenceField))
				return "This field must be set to the owner entity";
			return super.isEnabled(field);
		}

		@Override
		public <F> String isAcceptable(ConfiguredValueField<? super E2, F> field, F value) {
			if (field.isAssignableFrom(theMappedReferenceField))
				return "This field must be set to the owner entity";
			return super.isAcceptable(field, value);
		}

		@Override
		public <F> SyncValueCreator<E, E2> with(ConfiguredValueField<E2, F> field, F value) throws IllegalArgumentException {
			if (field.isAssignableFrom(theMappedReferenceField))
				throw new IllegalArgumentException("This field must be set to the owner entity");
			return super.with(field, value);
		}
	}
}
