package org.observe.entity.impl;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.Observable;
import org.observe.SimpleObservable;
import org.observe.entity.ConditionalFieldConstraint;
import org.observe.entity.EntityChange;
import org.observe.entity.EntityCollectionResult;
import org.observe.entity.EntityCondition;
import org.observe.entity.EntityCondition.LiteralCondition;
import org.observe.entity.EntityConstraint;
import org.observe.entity.EntityCountResult;
import org.observe.entity.EntityCreationResult;
import org.observe.entity.EntityCreator;
import org.observe.entity.EntityDeletion;
import org.observe.entity.EntityIdentity;
import org.observe.entity.EntityLoadRequest;
import org.observe.entity.EntityLoadRequest.Fulfillment;
import org.observe.entity.EntityModification;
import org.observe.entity.EntityModificationResult;
import org.observe.entity.EntityOperationException;
import org.observe.entity.EntityQuery;
import org.observe.entity.EntityUpdate;
import org.observe.entity.EntityValueAccess;
import org.observe.entity.FieldConstraint;
import org.observe.entity.ObservableEntity;
import org.observe.entity.ObservableEntityDataSet;
import org.observe.entity.ObservableEntityField;
import org.observe.entity.ObservableEntityFieldEvent;
import org.observe.entity.ObservableEntityFieldType;
import org.observe.entity.ObservableEntityProvider;
import org.observe.entity.ObservableEntityProvider.SimpleEntity;
import org.observe.entity.ObservableEntityResult;
import org.observe.entity.ObservableEntityType;
import org.observe.entity.PreparedCreator;
import org.observe.entity.PreparedDeletion;
import org.observe.entity.PreparedQuery;
import org.observe.entity.PreparedUpdate;
import org.observe.entity.SimpleFieldConstraint;
import org.observe.util.EntityReflector;
import org.observe.util.EntityReflector.ReflectedField;
import org.observe.util.MethodRetrievingHandler;
import org.observe.util.ObservableEntityUtils;
import org.observe.util.TypeTokens;
import org.qommons.QommonsUtils;
import org.qommons.Stamped;
import org.qommons.StringUtils;
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterSortedList;
import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.collect.QuickSet;
import org.qommons.collect.QuickSet.QuickMap;
import org.qommons.collect.StampedLockingStrategy;
import org.qommons.tree.BetterTreeSet;

import com.google.common.reflect.TypeToken;

/** Implementation of an {@link ObservableEntityDataSet entity set} reliant on an {@link ObservableEntityProvider} for its data */
public class ObservableEntityDataSetImpl implements ObservableEntityDataSet {
	interface EntityAction {
		ObservableEntityResult<?> apply(boolean synchronous) throws EntityOperationException;
	}

	private Transactable theLock;
	private final BetterSortedSet<ObservableEntityTypeImpl<?>> theEntityTypes;
	private final Map<Class<?>, String> theClassMapping;
	private final ObservableEntityProvider theImplementation;
	private final List<EntityAction> theQueuedActions;
	private int theWriteLockDepth;
	private volatile boolean isActive;

	private final QueryResultsTree theResults;
	private final SimpleObservable<List<EntityChange<?>>> theChanges;

	private ObservableEntityDataSetImpl(ObservableEntityProvider implementation) {
		theEntityTypes = new BetterTreeSet<>(true, (et1, et2) -> compareEntityTypes(et1.getName(), et2.getName()));
		theClassMapping = new HashMap<>();
		theImplementation = implementation;
		theQueuedActions = new ArrayList<>();

		theResults = new QueryResultsTree(this);
		theChanges = new SimpleObservable<>();
	}

	void startup(Transactable lock, Observable<?> refresh, Observable<?> until) throws EntityOperationException {
		theLock = lock;
		isActive = true;
		theImplementation.install(this);
		refresh.takeUntil(until).act(__ -> {
			try (Transaction t = theLock.lock(true, null)) {
				processChanges();
			}
		});
		if (until != null)
			until.take(1).act(__ -> {
				isActive = false;
				theChanges.onCompleted(Collections.emptyList());
				theResults.dispose();
			});
	}

	ObservableEntityProvider getImplementation() {
		return theImplementation;
	}

	@Override
	public Transaction lock(boolean write, Object cause) {
		return wrapTransaction(theLock.lock(write, cause), write);
	}

	@Override
	public Transaction tryLock(boolean write, Object cause) {
		Transaction t = theLock.tryLock(write, cause);
		return t == null ? null : wrapTransaction(t, write);
	}

	boolean queueAction(EntityAction action) throws EntityOperationException {
		if (!isActive)
			throw new IllegalStateException("This entity set is no longer active");
		if (theWriteLockDepth == 1) {
			action.apply(true);
			processChanges();
			return false;
		} else {
			theQueuedActions.add(action);
			return true;
		}
	}

	private Transaction wrapTransaction(Transaction t, boolean write) {
		if (!isActive)
			throw new IllegalStateException("This entity set is no longer active");
		if (!write)
			return t;
		int preWLD = theWriteLockDepth;
		theWriteLockDepth++;
		return () -> {
			theWriteLockDepth = preWLD;
			if (preWLD == 0)
				flushQueuedActions();
			t.close();
		};
	}

	private void flushQueuedActions() {
		AtomicInteger completed = new AtomicInteger();
		int actionCount = theQueuedActions.size();
		for (EntityAction action : theQueuedActions) {
			ObservableEntityResult<?> result;
			try{
				result=action.apply(false);
			} catch(EntityOperationException e){
				System.err.println("Should not happen for async call");
				continue;
			}
			result.statusChanges().act(res -> {
				if (res.getStatus().isDone() && completed.incrementAndGet() == actionCount) {
					synchronized (completed) {
						completed.notify();
					}
				}
			});
		}
		theQueuedActions.clear();
		synchronized (completed) {
			processChanges();
			while (completed.get() < actionCount) {
				try {
					completed.wait(1);
				} catch (InterruptedException e) {}
			}
		}
	}

	private void processChanges() {
		List<EntityChange<?>> changes = theImplementation.changes();
		if (changes.isEmpty())
			return;
		List<EntityLoadRequest<?>> loadRequests = new LinkedList<>();
		List<EntityChange<?>> noLoadNeeded = new LinkedList<>();
		Map<EntityIdentity<?>, ObservableEntity<?>> entities = new HashMap<>();
		loadEntities(changes, entities);
		for (EntityChange<?> change : changes) {
			if (change.changeType == EntityChange.EntityChangeType.remove)
				noLoadNeeded.add(change);
			else {
				Set<? extends EntityValueAccess<?, ?>> requiredFields = theResults.specifyDataRequirements(change, entities);
				if (requiredFields != null && !requiredFields.isEmpty())
					loadRequests.add(new EntityLoadRequest<>((EntityChange<Object>) change, (Set<EntityValueAccess<?, ?>>) requiredFields));
				else
					noLoadNeeded.add(change);
			}
		}
		QueryResultsTree.EntityUsage usage = new QueryResultsTree.EntityUsage() {
			@Override
			public <E> ObservableEntity<E> use(ObservableEntity<E> entity) {
				return ObservableEntityDataSetImpl.this.use(entity, entities);
			}
		};
		if (!noLoadNeeded.isEmpty()) {
			fulfillNoLoadEntities(noLoadNeeded, entities);
			for (EntityChange<?> change : noLoadNeeded) {
				((ObservableEntityTypeImpl<?>) change.getEntityType()).handleChange(change, entities, false, false);
				theResults.handleChange(change, entities, usage);
			}
		}
		if (!loadRequests.isEmpty()) {
			try {
				theImplementation.loadEntityData(loadRequests, fulfilled -> {
					fulfillEntities(fulfilled, entities);
					for (EntityLoadRequest.Fulfillment<?> f : fulfilled) {
						EntityChange<?> change = f.getRequest().getChange();
						((ObservableEntityTypeImpl<?>) change.getEntityType()).handleChange(change, entities, false, false);
						theResults.handleChange(change, entities, usage);
					}
				}, err -> {
					// TODO Logging
					System.err.println("Unable to load field data for new entities");
					err.printStackTrace();
				});
			} catch (EntityOperationException e) {
				// TODO Logging
				System.err.println("Unable to load field data for new entities");
				e.printStackTrace();
			}
		}
		theChanges.onNext(changes);
	}

	@Override
	public List<ObservableEntityType<?>> getEntityTypes() {
		return (List<ObservableEntityType<?>>) (List<?>) theEntityTypes;
	}

	@Override
	public ObservableEntityType<?> getEntityType(String entityName) {
		return theEntityTypes.searchValue(et -> compareEntityTypes(entityName, et.getName()),
			BetterSortedList.SortedSearchFilter.OnlyMatch);
	}

	@Override
	public <E> ObservableEntityType<E> getEntityType(Class<E> type) {
		String entityName = theClassMapping.get(type);
		if (entityName == null)
			return null;
		ObservableEntityType<?> entityType = getEntityType(entityName);
		if (entityType != null && entityType.getEntityType() == type)
			return (ObservableEntityType<E>) entityType;
		return null;
	}

	@Override
	public Observable<List<EntityChange<?>>> changes() {
		return theChanges.readOnly();
	}

	<E, F> String isAcceptable(ObservableEntityImpl<E> entity, int fieldIndex, F value) {
		ObservableEntityFieldType<E, F> field = (ObservableEntityFieldType<E, F>) entity.getField(fieldIndex);
		if (field.getIdIndex() >= 0)
			return ObservableEntityField.ID_FIELD_UNSETTABLE;
		else if (value == null && field.getFieldType().isPrimitive())
			return "Null is not allowed in a primitive-typed field";
		else if (value != null && !TypeTokens.get().isInstance(field.getFieldType(), value))
			return StdMsg.BAD_TYPE;
		if (value != null && !TypeTokens.get().isInstance(field.getFieldType(), value))
			return StdMsg.BAD_TYPE;
		String message = null;
		for (FieldConstraint<E, F> constraint : field.getConstraints()) {
			String msg = constraint.canAccept(value);
			if (msg != null) {
				if (message == null)
					message = msg;
				else
					message += "\n" + msg;
			}
		}
		return message;
	}

	<E> String canDelete(ObservableEntityImpl<E> entity) {
		if (!isActive)
			return "This entity set is no longer connected to a data source";
		return null; // TODO Check referential constraints
	}

	<E> EntityCreationResult<E> create(EntityCreator<? super E, E> creator, boolean sync, Object cause,
		Consumer<? super ObservableEntity<E>> preAdd)
			throws EntityOperationException {
		if (!isActive)
			throw new IllegalStateException("This entity set is no longer connected to a data source");
		Object prepared = creator instanceof PreparedCreator ? ((PreparedCreatorImpl<?, E>) creator).getPreparedObject() : null;
		if (sync) {
			SimpleEntity<E> simple = theImplementation.create(creator, prepared, null, null);
			ObservableEntity<E> entity = getOrCreateEntity(simple.getIdentity(), simple.getFields());
			if (preAdd != null)
				preAdd.accept(entity);
			return new SyncCreateResult<>(creator, entity);
		} else {
			AsyncCreateResult<E> result = new AsyncCreateResult<>(creator);
			theImplementation.create(creator, prepared,
				simple -> {
					ObservableEntity<E> entity = getOrCreateEntity(simple.getIdentity(), simple.getFields());
					if (preAdd != null)
						preAdd.accept(entity);
					result.fulfill(entity);
				}, result::failed);
			return result;
		}
	}

	<E> EntityCountResult<E> count(EntityQuery<E> query) throws EntityOperationException {
		if (!isActive)
			throw new IllegalStateException("This entity set is no longer connected to a data source");
		return theResults.addQuery(query, true).getCountResults(query);
	}

	<E> EntityCollectionResult<E> collect(EntityQuery<E> query, boolean withUpdates) throws EntityOperationException {
		if (!isActive)
			throw new IllegalStateException("This entity set is no longer connected to a data source");
		return theResults.addQuery(query, false).getResults(query, withUpdates);
	}

	<E> void executeQuery(EntityQuery<E> query, QueryResults<E> results) throws EntityOperationException {
		Object prepared = query instanceof PreparedQuery ? ((PreparedQueryImpl<E>) query).getPreparedObject() : null;
		if (results.isCount())
			theImplementation.count(query, prepared, results::init, results::failed);
		else
			theImplementation.query(query, prepared, fields -> results.fulfill(entitiesFor(fields)), results::failed);
	}

	<E> EntityModificationResult<E> update(EntityUpdate<E> update, boolean sync, Object cause) throws EntityOperationException {
		if (!isActive)
			throw new IllegalStateException("This entity set is no longer connected to a data source");
		Object prepared = update instanceof PreparedUpdate ? ((PreparedUpdateImpl<E>) update).getPreparedObject() : null;
		if (sync)
			return new SyncModResult<>(update, theImplementation.update(update, prepared, null, null));
		else {
			AsyncModResult<E> result = new AsyncModResult<>(update);
			theImplementation.update(update, prepared, result::fulfill, result::failed);
			return result;
		}
	}

	<E> EntityModificationResult<E> delete(EntityDeletion<E> delete, boolean sync, Object cause) throws EntityOperationException {
		if (!isActive)
			throw new IllegalStateException("This entity set is no longer connected to a data source");
		Object prepared = delete instanceof PreparedDeletion ? ((PreparedDeletionImpl<E>) delete).getPreparedObject() : null;
		if (sync)
			return new SyncModResult<>(delete, theImplementation.delete(delete, prepared, null, null));
		else {
			AsyncModResult<E> result = new AsyncModResult<>(delete);
			theImplementation.delete(delete, prepared, result::fulfill, result::failed);
			return result;
		}
	}

	private <E> ObservableEntity<E> getOrCreateEntity(EntityIdentity<E> id, QuickMap<String, Object> fields) {
		ObservableEntityImpl<E> entity = ((ObservableEntityTypeImpl<E>) id.getEntityType()).getOrCreate(id);
		if (fields != null) {
			for (int f = 0; f < fields.keySize(); f++) {
				ObservableEntityFieldType<E, Object> field = (ObservableEntityFieldType<E, Object>) entity.getType().getFields().get(f);
				if (!entity.isLoaded(field) && fields.get(f) != EntityUpdate.NOT_SET)
					entity._set(field, EntityUpdate.NOT_SET, fields.get(f));
			}
		}
		return entity;
	}

	private void loadEntities(List<EntityChange<?>> changes, Map<EntityIdentity<?>, ObservableEntity<?>> entities) {
		for (EntityChange<?> change : changes)
			loadEntities(change, entities);
	}

	private <E> void loadEntities(EntityChange<E> change, Map<EntityIdentity<?>, ObservableEntity<?>> entities) {
		for (EntityIdentity<? extends E> id : change.getEntities()) {
			entities.computeIfAbsent(id, __ -> {
				ObservableEntity<? extends E> entity = ((ObservableEntityTypeImpl<E>) change.getEntityType()).getIfPresent(id);
				if (entity == null)
					entity = new PartialEntity<>(id);
				return entity;
			});
		}
	}

	private void fulfillEntities(List<Fulfillment<?>> fulfilled, Map<EntityIdentity<?>, ObservableEntity<?>> entities) {
		for (Fulfillment<?> f : fulfilled)
			fulfillEntities(f.getRequest().getChange(), f.getResults(), entities);
	}

	private void fulfillNoLoadEntities(List<EntityChange<?>> changes, Map<EntityIdentity<?>, ObservableEntity<?>> entities) {
		for (EntityChange<?> change : changes)
			fulfillEntities(change, null, entities);
	}

	private <E> void fulfillEntities(EntityChange<E> change, List<QuickMap<String, Object>> fields,
		Map<EntityIdentity<?>, ObservableEntity<?>> entities) {
		int index = 0;
		for (EntityIdentity<? extends E> id : change.getEntities()) {
			ObservableEntity<E> entity = (ObservableEntity<E>) entities.get(id);
			QuickMap<String, Object> entityFields = fields == null ? null : fields.get(index);
			if (entity instanceof PartialEntity) {
				((PartialEntity<E>) entity).theFields = entityFields;
				entity = getOrCreateEntity(entity.getId(), entityFields);
				entities.put(id, entity);
			} else if (entityFields != null) {
				for (int f = 0; f < entityFields.keySize(); f++) {
					ObservableEntityFieldType<E, Object> field = (ObservableEntityFieldType<E, Object>) entity.getType().getFields().get(f);
					if (entityFields.get(f) != EntityUpdate.NOT_SET && !entity.isLoaded(field))
						((ObservableEntityImpl<E>) entity)._set(field, EntityUpdate.NOT_SET, entityFields.get(f));
				}
			}
			index++;
		}
	}

	private <E> ObservableEntity<E> use(ObservableEntity<E> entity, Map<EntityIdentity<?>, ObservableEntity<?>> entities) {
		if (entity instanceof PartialEntity) {
			entity = getOrCreateEntity(entity.getId(), ((PartialEntity<E>) entity).theFields);
			entities.put(entity.getId(), entity);
		}
		return entity;
	}

	private <E> Iterable<ObservableEntity<? extends E>> entitiesFor(Iterable<SimpleEntity<? extends E>> fields) {
		return () -> new Iterator<ObservableEntity<? extends E>>() {
			private final Iterator<SimpleEntity<? extends E>> theFieldIterator = fields.iterator();

			@Override
			public boolean hasNext() {
				return theFieldIterator.hasNext();
			}

			@Override
			public ObservableEntity<? extends E> next() {
				SimpleEntity<? extends E> simple = theFieldIterator.next();
				return getOrCreateEntity(simple.getIdentity(), simple.getFields());
			}
		};
	}

	<E, F> void loadField(ObservableEntityImpl<E> entity, ObservableEntityFieldType<E, F> field, Consumer<? super F> onLoad,
		Consumer<EntityOperationException> onFail) throws EntityOperationException {
		EntityLoadRequest<E> request = new EntityLoadRequest<>(entity.getId(), Collections.singleton(field));
		if (onLoad == null) {
			List<Fulfillment<?>> fulfilled = theImplementation.loadEntityData(Collections.singletonList(request), null, null);
			entity._set(field, (F) EntityUpdate.NOT_SET, (F) fulfilled.get(0).getResults().get(0));
		} else {
			theImplementation.loadEntityData(Collections.singletonList(request), fulfilled -> {
				entity._set(field, (F) EntityUpdate.NOT_SET, (F) fulfilled.get(0).getResults().get(0));
			}, onFail);
		}
	}

	private static int compareEntityTypes(String type1, String type2) {
		return QommonsUtils.compareNumberTolerant(type1, type2, true, true);
	}

	/**
	 * @param implementation The data set implementation to power the entity set
	 * @return A builder for an entity set
	 */
	public static EntitySetBuilder build(ObservableEntityProvider implementation) {
		return new EntitySetBuilder(implementation);
	}

	/** Builds an entity set */
	public static class EntitySetBuilder {
		private final ObservableEntityDataSetImpl theEntitySet;
		private Transactable theLock;
		private Observable<?> theRefresh;
		private boolean isBuilding;

		EntitySetBuilder(ObservableEntityProvider implementation) {
			theEntitySet = new ObservableEntityDataSetImpl(implementation);
			theLock = new StampedLockingStrategy(theEntitySet);
			theRefresh = Observable.empty();
			isBuilding = true;
		}

		/**
		 * @param lock The lock to govern thread safety of the entity data
		 * @return This builder
		 */
		public EntitySetBuilder withLock(Transactable lock) {
			theLock = lock;
			return this;
		}

		/**
		 * @param refresh An observable to query the data source for changes
		 * @return This builder
		 */
		public EntitySetBuilder withRefresh(Observable<?> refresh) {
			theRefresh = refresh;
			return this;
		}

		/**
		 * @param entityName The name for the entity type
		 * @return A builder for the new entity type
		 */
		public <E> ObservableEntityTypeBuilder<E> withEntityType(String entityName) {
			return withEntityType(entityName, null);
		}

		/**
		 * @param entity The java type to build an entity for
		 * @return A builder to build the entity type
		 */
		public <E> ObservableEntityTypeBuilder<E> withEntityType(Class<E> entity) {
			ObservableEntityType<E>[] builtType = new ObservableEntityType[1];
			EntityReflector.Builder<E> refBuilder = EntityReflector.build(TypeTokens.get().of(entity), false);
			if (Stamped.class.isAssignableFrom(entity)) {
				refBuilder.withCustomMethod(e -> ((Stamped) e).getStamp(), (e, args) -> {
					if (builtType[0] == null)
						builtType[0] = theEntitySet.getEntityType(entity);
					return builtType[0].observableEntity(e).getStamp();
				});
			}
			return withEntityType(null, EntityReflector.build(TypeTokens.get().of(entity), false).build());
		}

		/**
		 * @param entity The reflector for a java type to build an entity type for
		 * @return A builder to build the entity type
		 */
		public <E> ObservableEntityTypeBuilder<E> withEntityType(EntityReflector<E> entity) {
			Class<E> javaType = TypeTokens.getRawType(entity.getType());
			if (javaType.getTypeParameters().length > 0)
				throw new IllegalArgumentException("Cannot create an entity from a parameterized type: " + javaType.getName());
			return withEntityType(null, entity);
		}

		private <E> ObservableEntityTypeBuilder<E> withEntityType(String entityName, EntityReflector<E> reflector) {
			if (!isBuilding)
				throw new IllegalStateException("This builder has already built its entity set");
			Class<E> javaType;
			if (reflector != null) {
				javaType = TypeTokens.getRawType(reflector.getType());
				if (javaType.getTypeParameters().length > 0)
					throw new IllegalArgumentException("Cannot create an entity from a parameterized type: " + javaType.getName());
				if (entityName == null)
					entityName = javaType.getSimpleName();
				if (theEntitySet.theClassMapping.containsKey(javaType))
					throw new IllegalArgumentException("An ObservableEntityType mapped to " + javaType.getName() + " is already defined");
			} else
				javaType = null;
			if (theEntitySet.getEntityType(entityName) != null)
				throw new IllegalArgumentException("Entity type named " + entityName + " already defined");
			return new ObservableEntityTypeBuilder<>(this, theEntitySet, entityName, javaType, reflector);
		}

		/**
		 * @param entity The java type of the entity to get
		 * @return The entity type representing the given java type in this builder
		 * @throws IllegalArgumentException If no such entity has been built yet
		 */
		public <E> ObservableEntityType<E> getEntityType(Class<E> entity) {
			ObservableEntityType<E> type = theEntitySet.getEntityType(entity);
			if (type == null)
				throw new IllegalArgumentException("No entity type built for " + entity.getName());
			return type;
		}

		/**
		 * @param name The name of the entity type to get
		 * @return The entity type with the given name
		 * @throws IllegalArgumentException If no such entity has been built yet
		 */
		public ObservableEntityType<?> getEntityType(String name) {
			ObservableEntityType<?> type = theEntitySet.getEntityType(name);
			if (type == null)
				throw new IllegalArgumentException("No such entity type: " + name);
			return type;
		}

		/**
		 * Builds the entity set
		 *
		 * @param until An observable that, when fired, will stop the entity set from being updated with changes from the data source
		 * @return The new entity set
		 * @throws EntityOperationException If an error occurs setting up the entity persistence
		 */
		public ObservableEntityDataSet build(Observable<?> until) throws EntityOperationException {
			if (!isBuilding)
				throw new IllegalStateException("This builder has already built its entity set");
			isBuilding = false;
			for (ObservableEntityType<?> entity : theEntitySet.getEntityTypes())
				((ObservableEntityTypeImpl<?>) entity).check();
			theEntitySet.startup(theLock, theRefresh, until);
			return theEntitySet;
		}
	}

	/**
	 * Builds an entity type
	 *
	 * @param <E> The java type of the entity
	 */
	public static class ObservableEntityTypeBuilder<E> {
		private final EntitySetBuilder theSetBuilder;
		private final ObservableEntityDataSetImpl theEntitySet;
		private final Class<E> theJavaType;
		private final EntityReflector<E> theReflector;
		private final E theProxy;
		private final MethodRetrievingHandler theProxyHandler;
		private String theEntityName;

		private final List<ObservableEntityTypeImpl<? super E>> theParents;
		private final Map<String, ObservableEntityFieldBuilder<E, ?>> theFields;

		private boolean isBuilding;

		/* TODO
		 * Defaulted overridden methods (e.g. one of multiple ID fields from a super type is constant for a particular sub-type)
		 */

		ObservableEntityTypeBuilder(EntitySetBuilder setBuilder, ObservableEntityDataSetImpl entitySet, String entityName,
			Class<E> entityType, EntityReflector<E> reflector) {
			theSetBuilder = setBuilder;
			theEntitySet = entitySet;
			theJavaType = entityType;
			theReflector = reflector;
			theEntityName = entityName;
			if (theJavaType != null) {
				theProxyHandler = new MethodRetrievingHandler();
				theProxy = (E) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[] { theJavaType }, theProxyHandler);

			} else {
				theProxyHandler = null;
				theProxy = null;
			}

			theParents = new LinkedList<>();
			theFields = new LinkedHashMap<>();
			isBuilding = true;
		}

		/**
		 * @param name The name for the entity
		 * @return This builder
		 */
		public ObservableEntityTypeBuilder<E> withName(String name) {
			if (!isBuilding)
				throw new IllegalStateException("This builder has already built its entity type");
			theEntityName = name;
			return this;
		}

		/**
		 * If this builder was initialized with a java type, fills out the super types by reflection
		 *
		 * @return This builder
		 */
		public ObservableEntityTypeBuilder<E> fillSupersFromClass() {
			if (!isBuilding)
				throw new IllegalStateException("This builder has already built its entity type");
			else if (theReflector == null)
				throw new IllegalStateException("This entity type is not mapped to a java type");
			for (EntityReflector<? super E> superRef : theReflector.getSuper()) {
				Class<? super E> superClass = TypeTokens.getRawType(superRef.getType());
				if (superClass.getTypeParameters().length > 0)
					continue; // Entities cannot have type parameters
				ObservableEntityType<? super E> superEntity = theSetBuilder.getEntityType(superClass);
				if (superEntity == null) {
					// If it hasn't been declared, create it
					theSetBuilder.withEntityType(superRef).fillSupersFromClass().fillFieldsFromClass().build();
					superEntity = theSetBuilder.getEntityType(superClass);
				}
				withSuper(superEntity);
			}
			return this;
		}

		/**
		 * If this builder was initialized with a java type, fills out the fields by reflection
		 *
		 * @return This builder
		 */
		public ObservableEntityTypeBuilder<E> fillFieldsFromClass() {
			if (!isBuilding)
				throw new IllegalStateException("This builder has already built its entity type");
			else if (theReflector == null)
				throw new IllegalStateException("This entity type is not mapped to a java type");
			for (int f = 0; f < theReflector.getFields().keySize(); f++) {
				ReflectedField<E, ?> field = theReflector.getFields().get(f);
				boolean useSuper = false;
				for (ObservableEntityTypeImpl<? super E> parent : theParents) {
					if (parent.getFields().keySet().contains(field.getName())) {
						useSuper = true;
						break;
					}
				}
				if (!useSuper)
					buildField(field);
			}
			return this;
		}

		private <F> void buildField(ReflectedField<E, F> field) {
			ObservableEntityFieldBuilder<E, F> fieldBuilder = withField(field.getName(), field.getType());
			if (field.isId())
				fieldBuilder.id();
			fieldBuilder.mapTo(field.getGetter().getMethod());
			fieldBuilder.build();
		}

		/**
		 * @param parent The java super type for this type
		 * @return This builder
		 */
		public ObservableEntityTypeBuilder<E> withSuper(Class<? super E> parent) {
			return withSuper(EntityReflector.build(TypeTokens.get().of(parent), false).build());
		}

		/**
		 * @param parent The reflector of the super type for this type
		 * @return This builder
		 */
		public ObservableEntityTypeBuilder<E> withSuper(EntityReflector<? super E> parent) {
			Class<? super E> superType = TypeTokens.getRawType(parent.getType());
			ObservableEntityType<? super E> superEntity = theSetBuilder.getEntityType(superType);
			if (superEntity == null) {
				theSetBuilder.withEntityType(parent).fillFieldsFromClass().build();
				superEntity = theSetBuilder.getEntityType(superType);
			}
			return withSuper(superEntity);
		}

		/**
		 * @param parent The super type for this type
		 * @return This builder
		 */
		public ObservableEntityTypeBuilder<E> withSuper(ObservableEntityType<?> parent) {
			if (!isBuilding)
				throw new IllegalStateException("This builder has already built its entity type");
			else if (!theFields.isEmpty())
				throw new IllegalStateException("Parent(s) must be defined before any fields");
			if (!(parent instanceof ObservableEntityTypeImpl) || theEntitySet.getEntityType(parent.getName()) != parent)
				throw new IllegalArgumentException("An entity type's parent must be present in the same entity set");
			if (theJavaType != null)
				checkSuperType(parent);
			theParents.add((ObservableEntityTypeImpl<? super E>) parent);
			return this;
		}

		private void checkSuperType(ObservableEntityType<?> parent) {
			if (parent.getEntityType() != null) {
				if (!parent.getEntityType().isAssignableFrom(theJavaType))
					throw new IllegalArgumentException("Entity type " + parent.getName() + " (" + parent.getEntityType().getName()
						+ ") cannot be a super type of " + theEntityName + " (" + theJavaType.getName() + ")");
				return;
			}
			for (ObservableEntityType<?> parentParent : parent.getSupers())
				checkSuperType(parentParent);
		}

		/**
		 * @param fieldGetter The java getter for the new field
		 * @return A builder for the new field
		 */
		public <F> ObservableEntityFieldBuilder<E, F> withField(Function<? super E, F> fieldGetter) {
			if (theJavaType == null)
				throw new IllegalStateException("This method can only be used with a java-typed entity");
			fieldGetter.apply(theProxy);
			Method method = theProxyHandler.getInvoked();
			if (method == null)
				throw new IllegalArgumentException(fieldGetter + " is not a getter method for a " + theJavaType.getName() + " field");
			String name = method.getName();
			if (!name.startsWith("get") || name.length() == 3)
				throw new IllegalArgumentException(name + " is not a getter method for a " + theJavaType.getName() + " field");
			name = name.substring(3);
			if (Character.isUpperCase(name.charAt(0)))
				name = Character.toLowerCase(name.charAt(0)) + name.substring(1);
			return (ObservableEntityFieldBuilder<E, F>) withField(name, TypeToken.of(method.getGenericReturnType())).mapTo(method);
		}

		/**
		 * @param fieldName The name for the new field
		 * @param type The type for the new field
		 * @return A builder for the new field
		 */
		public <F> ObservableEntityFieldBuilder<E, F> withField(String fieldName, TypeToken<F> type) {
			if (!isBuilding)
				throw new IllegalStateException("This builder has already built its entity type");
			else if (theFields.containsKey(fieldName))
				throw new IllegalArgumentException("A field named " + theEntityName + "." + fieldName + " has already been defined");

			ObservableEntityFieldBuilder<E, F> field = new ObservableEntityFieldBuilder<>(this, fieldName, type);
			for (ObservableEntityTypeImpl<? super E> parent : theParents) {
				ObservableEntityFieldType<? super E, ?> superField = parent.getFields().getIfPresent(fieldName);
				if (superField != null) {
					if (TypeTokens.get().unwrap(TypeTokens.getRawType(superField.getFieldType())) != void.class
						&& !superField.getFieldType().isAssignableFrom(type))
						throw new IllegalArgumentException("Field " + superField + " cannot be overridden with type " + type);
					field.withOverride((ObservableEntityFieldType<? super E, F>) superField);
				}
			}
			if (theReflector != null && theReflector.getFields().keyIndexTolerant(fieldName) >= 0)
				field.mapTo(theReflector.getFields().get(fieldName).getGetter().getMethod());

			theFields.put(fieldName, field);
			return field;
		}

		/**
		 * Builds the entity type
		 *
		 * @return The builder for the entity set
		 */
		public EntitySetBuilder build() {
			Map<String, ObservableEntityFieldBuilder<E, ?>> fieldBuilders = new LinkedHashMap<>(theFields);
			for (ObservableEntityTypeImpl<? super E> parent : theParents) {
				for (ObservableEntityFieldType<? super E, ?> field : parent.getFields().allValues()) {
					ObservableEntityFieldBuilder<E, ?> builder = fieldBuilders.get(field.getName());
					if (builder == null) {
						builder = new ObservableEntityFieldBuilder<>(this, field.getName(), field.getFieldType());
						if (field.getIdIndex() >= 0)
							builder.id();
						fieldBuilders.put(field.getName(), builder);
					}
					((ObservableEntityFieldBuilder<E, Object>) builder).withOverride((ObservableEntityFieldType<E, Object>) field);
				}
			}

			Set<String> tempIdFieldNames = new LinkedHashSet<>();
			for (ObservableEntityFieldBuilder<E, ?> field : fieldBuilders.values()) {
				if (field.isId)
					tempIdFieldNames.add(field.getName());
			}
			if (tempIdFieldNames.isEmpty())
				throw new IllegalStateException("No identity fields defined for root-level entity type " + theEntityName);
			QuickSet<String> fieldNames = QuickSet.of(StringUtils.DISTINCT_NUMBER_TOLERANT, fieldBuilders.keySet());
			QuickSet<String> idFieldNames = QuickSet.of(StringUtils.DISTINCT_NUMBER_TOLERANT, tempIdFieldNames);
			QuickMap<String, ObservableEntityFieldType<E, ?>> fields = fieldNames.createMap();
			QuickMap<String, ObservableEntityFieldType<E, ?>> idFields = idFieldNames.createMap();
			List<EntityConstraint<E>> constraints = new ArrayList<>(5);
			ObservableEntityTypeImpl<E> entityType = new ObservableEntityTypeImpl<>(theEntitySet, theEntityName,
				Collections.unmodifiableList(theParents), fields.unmodifiable(), idFields.unmodifiable(), theReflector,
				Collections.unmodifiableList(constraints));
			for (ObservableEntityTypeImpl<? super E> parent : theParents)
				parent.addSub(entityType);
			for (ObservableEntityFieldBuilder<E, ?> fieldBuilder : theFields.values()) {
				int fieldIndex = fieldNames.indexOf(fieldBuilder.getName());
				int idIndex = idFieldNames.indexOfTolerant(fieldBuilder.getName());
				ObservableEntityFieldType<E, ?> field = fieldBuilder.buildField(entityType, fieldIndex, idIndex);
				fields.put(fieldIndex, field);
				if (idIndex >= 0)
					idFields.put(idIndex, field);
				constraints.addAll(field.getConstraints());
			}
			theEntitySet.theEntityTypes.add(entityType);
			if (theJavaType != null)
				theEntitySet.theClassMapping.put(theJavaType, theEntityName);
			isBuilding = false;
			return theSetBuilder;
		}
	}

	/**
	 * Builds a field of an entity type
	 *
	 * @param <E> The type of the entity
	 * @param <F> The type of the field
	 */
	public static class ObservableEntityFieldBuilder<E, F> {
		final ObservableEntityTypeBuilder<E> theTypeBuilder;
		final String theName;
		final TypeToken<F> theType;
		List<ObservableEntityFieldType<? super E, F>> theOverrides;
		List<FieldConstraintBuilder<E, F>> theConstraints;
		Method theFieldGetter;
		boolean isId;
		String targetEntity;
		Comparator<? super F> theCompare;

		ObservableEntityFieldBuilder(ObservableEntityTypeBuilder<E> typeBuilder, String name, TypeToken<F> type) {
			theTypeBuilder = typeBuilder;
			theName = name;
			theType = type;
		}

		String getName() {
			return theName;
		}

		void withOverride(ObservableEntityFieldType<? super E, F> override) {
			if (theOverrides == null)
				theOverrides = new LinkedList<>();
			theOverrides.add(override);
		}

		/**
		 * Specifies that this field is an ID field
		 *
		 * @return This builder
		 */
		public ObservableEntityFieldBuilder<E, F> id() {
			isId = true;
			return this;
		}

		/**
		 * @param compare The comparator for values of this field's type
		 * @return This builder
		 */
		public ObservableEntityFieldBuilder<E, F> compareWith(Comparator<? super F> compare) {
			if (!theOverrides.isEmpty())
				throw new IllegalStateException("Cannot override field sorting from " + theOverrides);
			theCompare = compare;
			return this;
		}

		/**
		 * Specifies that the value of this field is another entity in the entity set
		 *
		 * @param entityName The name of the entity type (that may not yet have been built) in the entity set
		 * @return This builder
		 */
		public ObservableEntityFieldBuilder<E, F> withTarget(String entityName) {
			if (TypeTokens.get().unwrap(theType).isPrimitive()) // TODO Other things can't be entities either, e.g. Strings, Dates, etc.
				throw new UnsupportedOperationException("Field of type " + theType + " cannot target an entity");
			// Can't check the name or retrieve the entity now, because it may not have been defined yet, which is legal
			targetEntity = entityName;
			return this;
		}

		/**
		 * Adds a simple constraint to the field
		 *
		 * @param type The type of the constraint
		 * @param name The name for the constraint
		 * @return This builder
		 * @see EntityConstraint#NOT_NULL
		 * @see EntityConstraint#UNIQUE
		 */
		public ObservableEntityFieldBuilder<E, F> withConstraint(String type, String name) {
			if (theConstraints == null)
				theConstraints = new LinkedList<>();
			theConstraints.add(new FieldConstraintBuilder<>(type, name, field -> new SimpleFieldConstraint<>(field, name, type)));
			return this;
		}

		/**
		 * Adds a {@link EntityConstraint#CHECK check}-type constraint to this field
		 *
		 * @param name The name for the constraint
		 * @param condition Produces a literal (constant-based) condition to check values of this field against
		 * @return This builder
		 */
		public ObservableEntityFieldBuilder<E, F> withCheckConstraint(String name,
			Function<EntityCondition.EntityConditionIntermediate1<E, F>, LiteralCondition<E, F>> condition) {
			if (theConstraints == null)
				theConstraints = new LinkedList<>();
			theConstraints.add(new FieldConstraintBuilder<>(EntityConstraint.CHECK, name, field -> {
				return new ConditionalFieldConstraint<>(field, name, condition.apply(field.getOwnerType().select().where(field)));
			}));
			return this;
		}

		/**
		 * @param fieldGetter The java getter to map to this field
		 * @return This builder
		 */
		public ObservableEntityFieldBuilder<E, F> mapTo(Function<? super E, ? extends F> fieldGetter) {
			Class<E> type = theTypeBuilder.theJavaType;
			if (type == null)
				throw new IllegalStateException("This method can only be used with a java-typed entity");
			Method method = ObservableEntityUtils.getField(type, fieldGetter);
			if (method == null)
				throw new IllegalArgumentException(fieldGetter + " is not a getter method for a " + type.getName() + " field");
			String name = method.getName();
			if (!name.startsWith("get") || name.length() == 3)
				throw new IllegalArgumentException(name + " is not a getter method for a " + type.getName() + " field");
			return mapTo(method);
		}

		ObservableEntityFieldBuilder<E, F> mapTo(Method fieldGetter) {
			theFieldGetter = fieldGetter;
			return this;
		}

		ObservableEntityFieldType<E, F> buildField(ObservableEntityTypeImpl<E> entityType, int fieldIndex, int idIndex) {
			if (isId && theOverrides != null)
				throw new IllegalArgumentException("Id fields cannot be added to a sub-entity");
			return new FieldTypeImpl<>(this, entityType, fieldIndex, idIndex);
		}

		/**
		 * Builds the field
		 *
		 * @return The builder for the entity type of the field
		 */
		public ObservableEntityTypeBuilder<E> build() {
			return theTypeBuilder;
		}
	}

	static class FieldConstraintBuilder<E, F> {
		final String constraintType;
		final String name;
		final Function<ObservableEntityFieldType<E, F>, FieldConstraint<E, F>> constraint;

		FieldConstraintBuilder(String constraintType, String name,
			Function<ObservableEntityFieldType<E, F>, FieldConstraint<E, F>> constraint) {
			this.constraintType = constraintType;
			this.name = name;
			this.constraint = constraint;
		}
	}

	static class FieldTypeImpl<E, F> implements ObservableEntityFieldType<E, F> {
		private final ObservableEntityTypeImpl<E> theEntityType;
		private final TypeToken<F> theFieldType;
		private final String theTargetEntityName;
		private ObservableEntityTypeImpl<F> theTargetEntity;
		private final String theName;
		private final Method theFieldGetter;
		private final int theFieldIndex;
		private final int theIdIndex;
		private final List<ObservableEntityFieldType<? super E, F>> theOverrides;
		private final List<FieldConstraint<E, F>> theConstraints;
		private final Comparator<? super F> theCompare;

		FieldTypeImpl(ObservableEntityFieldBuilder<E, F> builder, ObservableEntityTypeImpl<E> entity, int fieldIndex, int idIndex) {
			theEntityType = entity;
			theFieldType = builder.theType;
			theName = builder.theName;
			theFieldGetter = builder.theFieldGetter;
			theFieldIndex = fieldIndex;
			theIdIndex = idIndex;
			theTargetEntityName = builder.targetEntity;

			if (builder.theOverrides == null)
				theOverrides = Collections.emptyList();
			else {
				List<ObservableEntityFieldType<? super E, F>> newOverrides = new ArrayList<>(builder.theOverrides.size());
				newOverrides.addAll(builder.theOverrides);
				theOverrides = Collections.unmodifiableList(newOverrides);
			}

			if (builder.theConstraints == null)
				theConstraints = Collections.emptyList();
			else {
				List<FieldConstraint<E, F>> newConstraints = new ArrayList<>(builder.theConstraints.size());
				for (FieldConstraintBuilder<E, F> c : builder.theConstraints)
					newConstraints.add(c.constraint.apply(this));
				theConstraints = Collections.unmodifiableList(newConstraints);
			}
			if (builder.theCompare != null)
				theCompare = builder.theCompare;
			else if (!theOverrides.isEmpty())
				theCompare = ((FieldTypeImpl<? super E, ? super F>) theOverrides.get(0)).theCompare;
			else if (TypeTokens.get().isComparable(theFieldType))
				theCompare = (v1, v2) -> ((Comparable<F>) v1).compareTo(v2);
				else{
					if (theIdIndex >= 0)
						throw new IllegalStateException("ID fields must be comparable");
					for (FieldConstraint<E, F> c : theConstraints) {
						if (c instanceof ConditionalFieldConstraint
							&& ((ConditionalFieldConstraint<E, F>) c).getCondition().getComparison() != 0)
							throw new IllegalStateException("Comparison constraints can only apply to comparable fields");
					}
					theCompare = null;
				}
		}

		@Override
		public ObservableEntityType<E> getOwnerType() {
			return theEntityType;
		}

		@Override
		public TypeToken<F> getFieldType() {
			return theFieldType;
		}

		@Override
		public ObservableEntityType<F> getTargetEntity() {
			return theTargetEntity;
		}

		@Override
		public BetterList<ObservableEntityFieldType<?, ?>> getFieldSequence() {
			return BetterList.of(this);
		}

		@Override
		public String getName() {
			return theName;
		}

		@Override
		public int getIndex() {
			return theFieldIndex;
		}

		@Override
		public int getIdIndex() {
			return theIdIndex;
		}

		@Override
		public List<? extends ObservableEntityFieldType<? super E, F>> getOverrides() {
			return theOverrides;
		}

		Method getFieldGetter() {
			return theFieldGetter;
		}

		@Override
		public List<FieldConstraint<E, F>> getConstraints() {
			return theConstraints;
		}

		void check() {
			if (theTargetEntityName != null) {
				ObservableEntityType<?> target = theEntityType.getEntitySet().getEntityType(theTargetEntityName);
				if (target == null)
					throw new IllegalArgumentException(
						"Target " + theTargetEntityName + " of field " + this + " is not defined in the data set");
				else if (target.getEntityType() != null && !TypeTokens.getRawType(theFieldType).isAssignableFrom(target.getEntityType()))
					throw new IllegalArgumentException("Entity " + target.getName() + "(" + target.getEntityType().getName()
						+ ") cannot be a target of field " + this + "(" + theFieldType + ")");
				theTargetEntity = (ObservableEntityTypeImpl<F>) target;
			} else {
				theTargetEntity = (ObservableEntityTypeImpl<F>) theEntityType.getEntitySet()
					.getEntityType(TypeTokens.getRawType(theFieldType));
				if (theEntityType == null) {
					// TODO Check against supported types
				}
			}
		}

		@Override
		public int hashCode() {
			return Objects.hash(theEntityType, theFieldIndex);
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;
			else if (!(obj instanceof ObservableEntityFieldType))
				return false;
			ObservableEntityFieldType<?, ?> other = (ObservableEntityFieldType<?, ?>) obj;
			return theEntityType.equals(other.getOwnerType()) && theFieldIndex == other.getIndex();
		}

		@Override
		public String toString() {
			return theEntityType.getName() + "." + theName;
		}

		@Override
		public int compare(F o1, F o2) {
			return theCompare.compare(o1, o2);
		}
	}

	static class PartialEntity<E> implements ObservableEntity<E> {
		private final EntityIdentity<E> theId;
		QuickMap<String, Object> theFields;

		PartialEntity(EntityIdentity<E> id) {
			theId = id;
		}

		@Override
		public long getStamp() {
			throw new UnsupportedOperationException("Partial internal implementation");
		}

		@Override
		public ObservableEntityType<E> getType() {
			return theId.getEntityType();
		}

		@Override
		public E getEntity() {
			throw new UnsupportedOperationException("Partial internal implementation");
		}

		@Override
		public EntityIdentity<E> getId() {
			return theId;
		}

		@Override
		public Object get(int fieldIndex) {
			int idIndex = getType().getFields().get(fieldIndex).getIdIndex();
			if (idIndex >= 0)
				return theId.getFields().get(idIndex);
			else if (theFields != null)
				return theFields.get(fieldIndex);
			else
				return EntityUpdate.NOT_SET;
		}

		@Override
		public String isAcceptable(int fieldIndex, Object value) {
			throw new UnsupportedOperationException("Partial internal implementation");
		}

		@Override
		public <F> F set(int fieldIndex, F value, Object cause) throws UnsupportedOperationException, IllegalArgumentException {
			throw new UnsupportedOperationException("Partial internal implementation");
		}

		@Override
		public EntityModificationResult<E> update(int fieldIndex, Object value, boolean sync, Object cause)
			throws IllegalArgumentException, UnsupportedOperationException, EntityOperationException {
			throw new UnsupportedOperationException("Partial internal implementation");
		}

		@Override
		public Observable<ObservableEntityFieldEvent<E, ?>> allFieldChanges() {
			throw new UnsupportedOperationException("Partial internal implementation");
		}

		@Override
		public boolean isLoaded(ObservableEntityFieldType<? super E, ?> field) {
			if (!theId.getEntityType().equals(field.getOwnerType()))
				field = theId.getEntityType().getFields().get(field.getName());
			int idIndex = getType().getFields().get(field.getIndex()).getIdIndex();
			return idIndex >= 0 || (theFields != null && theFields.get(field.getIndex()) != EntityUpdate.NOT_SET);
		}

		@Override
		public <F> ObservableEntity<E> load(ObservableEntityFieldType<E, F> field, Consumer<? super F> onLoad,
			Consumer<EntityOperationException> onFail) throws EntityOperationException {
			throw new UnsupportedOperationException("Partial internal implementation");
		}

		@Override
		public boolean isPresent() {
			return false;
		}

		@Override
		public String canDelete() {
			throw new UnsupportedOperationException("Partial internal implementation");
		}

		@Override
		public void delete(Object cause) throws UnsupportedOperationException {
			throw new UnsupportedOperationException("Partial internal implementation");
		}
	}

	static class SyncCreateResult<E> implements EntityCreationResult<E> {
		private final EntityCreator<? super E, E> theOperation;
		private final ObservableEntity<? extends E> theResult;

		SyncCreateResult(EntityCreator<? super E, E> operation, ObservableEntity<? extends E> result) {
			theOperation = operation;
			theResult = result;
		}

		@Override
		public boolean cancel(boolean mayInterruptIfRunning) {
			return false;
		}

		@Override
		public ResultStatus getStatus() {
			return ResultStatus.FULFILLED;
		}

		@Override
		public EntityOperationException getFailure() {
			return null;
		}

		@Override
		public Observable<? extends ObservableEntityResult<E>> statusChanges() {
			return Observable.empty();
		}

		@Override
		public EntityCreator<? super E, E> getOperation() {
			return theOperation;
		}

		@Override
		public ObservableEntity<? extends E> getNewEntity() {
			return theResult;
		}
	}

	static class AsyncCreateResult<E> extends AbstractOperationResult<E> implements EntityCreationResult<E> {
		private final EntityCreator<? super E, E> theOperation;
		private volatile ObservableEntity<? extends E> theResult;

		AsyncCreateResult(EntityCreator<? super E, E> operation) {
			super(false);
			theOperation = operation;
		}

		@Override
		public EntityCreator<? super E, E> getOperation() {
			return theOperation;
		}

		@Override
		public ObservableEntity<? extends E> getNewEntity() {
			return theResult;
		}

		void fulfill(ObservableEntity<? extends E> result) {
			theResult = result;
			fulfilled();
		}
	}

	static class SyncModResult<E> implements EntityModificationResult<E> {
		private final EntityModification<E> theOperation;
		private final long theResult;

		SyncModResult(EntityModification<E> operation, long result) {
			theOperation = operation;
			theResult = result;
		}

		@Override
		public boolean cancel(boolean mayInterruptIfRunning) {
			return false;
		}

		@Override
		public ResultStatus getStatus() {
			return ResultStatus.FULFILLED;
		}

		@Override
		public EntityOperationException getFailure() {
			return null;
		}

		@Override
		public Observable<? extends ObservableEntityResult<E>> statusChanges() {
			return Observable.empty();
		}

		@Override
		public EntityModification<E> getOperation() {
			return theOperation;
		}

		@Override
		public long getModified() {
			return theResult;
		}
	}

	static class AsyncModResult<E> extends AbstractOperationResult<E> implements EntityModificationResult<E> {
		private final EntityModification<E> theOperation;
		private volatile long theResult;

		AsyncModResult(EntityModification<E> operation) {
			super(false);
			theOperation = operation;
			theResult = -1;
		}

		@Override
		public EntityModification<E> getOperation() {
			return theOperation;
		}

		@Override
		public long getModified() {
			return theResult;
		}

		void fulfill(long result) {
			theResult = result;
			fulfilled();
		}
	}
}
