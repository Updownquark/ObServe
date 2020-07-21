package org.observe.entity.impl;

import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.SettableValue;
import org.observe.Subscription;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollectionEvent;
import org.observe.collect.ObservableSortedSet;
import org.observe.entity.ConfigurableCreator;
import org.observe.entity.EntityChainAccess;
import org.observe.entity.EntityChange;
import org.observe.entity.EntityCollectionResult;
import org.observe.entity.EntityCondition;
import org.observe.entity.EntityCountResult;
import org.observe.entity.EntityIdentity;
import org.observe.entity.EntityOperation;
import org.observe.entity.EntityOperationException;
import org.observe.entity.EntityOperationVariable;
import org.observe.entity.EntityQuery;
import org.observe.entity.EntityQueryResult;
import org.observe.entity.EntityValueAccess;
import org.observe.entity.ObservableEntity;
import org.observe.entity.ObservableEntityFieldType;
import org.observe.entity.ObservableEntityResult;
import org.observe.entity.ObservableEntitySet;
import org.observe.entity.ObservableEntityType;
import org.observe.entity.QueryOrder;
import org.observe.entity.impl.QueryResultsTree.QueryResultNode;
import org.observe.util.ObservableCollectionWrapper;
import org.observe.util.TypeTokens;
import org.qommons.Ternian;
import org.qommons.Transaction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterList;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.CollectionLockingStrategy;
import org.qommons.collect.ElementId;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.collect.QuickSet.QuickMap;
import org.qommons.collect.RRWLockingStrategy;

import com.google.common.reflect.TypeToken;

public class QueryResults<E> extends AbstractOperationResult<E> {
	private final QueryResultNode theNode;
	private final EntityCondition<E> theSelection;
	private final ObservableSortedSet<ObservableEntity<? extends E>> theRawResults;
	private final ObservableSortedSet<ObservableEntity<? extends E>> theExposedResults;
	private final SettableValue<Long> theRawCountResult;

	private final AtomicInteger theListening;
	private volatile boolean isAdjusting;

	public QueryResults(QueryResultNode node, EntityCondition<E> selection, boolean count) {
		theNode = node;
		theSelection = selection;
		theListening = new AtomicInteger();
		if (count) {
			theExposedResults = theRawResults = null;
			theRawCountResult = SettableValue.build(long.class).withLock(selection.getEntityType().getEntitySet()).withValue(0L).build();
		} else {
			theRawCountResult = null;
			CollectionLockingStrategy locker = new RRWLockingStrategy(selection.getEntityType().getEntitySet());
			TypeToken<ObservableEntity<? extends E>> type;
			if (selection.getEntityType().getEntityType() != null)
				type = ObservableEntity.TYPE_KEY.getCompoundType(selection.getEntityType().getEntityType());
			else
				type = (TypeToken<ObservableEntity<? extends E>>) (TypeToken<?>) ObservableEntity.TYPE;
			theRawResults = ObservableSortedSet.build(type, ObservableEntity::compareTo).withLocker(locker).build();
			theExposedResults = theRawResults.flow().filterMod(fm -> {
				fm.noAdd("Results cannot be added to query results directly");
				fm.filterRemove(ObservableEntity::canDelete);
			}).collectPassive();
		}
	}

	boolean isCount() {
		return theRawCountResult != null;
	}

	@Override
	public EntityOperation<E> getOperation() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Observable<? extends QueryResults<E>> watchStatus() {
		return (Observable<? extends QueryResults<E>>) super.watchStatus();
	}

	@Override
	public void cancel(boolean mayInterruptIfRunning) {
		synchronized (theSelection.getEntityType().getEntitySet()) {
			if (theListening.decrementAndGet() == 0) {
				super.cancel(mayInterruptIfRunning);
				theNode.remove();
			}
		}
	}

	public EntityCondition<E> getSelection() {
		return theSelection;
	}

	public boolean isActive() {
		switch (getStatus()) {
		case WAITING:
		case EXECUTING:
		case FULFILLED:
			return true;
		default:
			return false;
		}
	}

	public void fulfill(Iterable<? extends ObservableEntity<?>> entities) {
		theNode.fulfill(entities);
	}

	public void init(Iterable<? extends ObservableEntity<?>> entities, boolean needsFilter) {
		if (theRawResults != null) {
			theRawResults.clear();
			for (ObservableEntity<?> entity : entities) {
				if (needsFilter && theSelection.getEntityType().isAssignableFrom(entity.getType())
					&& theSelection.test((ObservableEntity<? extends E>) entity, null) != null)
					continue;
				theRawResults.add((ObservableEntity<? extends E>) entity);
			}
			fulfilled();
		} else if (!needsFilter && entities instanceof Collection) {
			init(((Collection<?>) entities).size());
		} else {
			long count = 0;
			for (ObservableEntity<?> entity : entities) {
				if (needsFilter && theSelection.getEntityType().isAssignableFrom(entity.getType())
					&& theSelection.test((ObservableEntity<? extends E>) entity, null) != null)
					continue;
				count++;
			}
			init(count);
		}
	}

	public void init(long count) {
		theRawCountResult.set(count, null);
		fulfilled();
	}

	/**
	 * <p>
	 * This method allows query results to specify what information they need to correctly account for a change to the entity set.
	 * </p>
	 * <p>
	 * If a query requires the value of a particular field, that field should be populated with a non-null value. If a query requires the
	 * value of a secondary field (e.g. a.b.c), the value of the primary field should be populated with a field map of the secondary type,
	 * itself populated with non-null values for each field required. Tertiary fields (e.g. a.b.c.d) may also be specified this way, and so
	 * on.
	 * </p>
	 * <p>
	 * This method should not make any change to the query results, i.e. adding entities from the <code>entities</code> map into the query.
	 * The entities in the map may be shells here.
	 * </p>
	 *
	 * @param change The change from the data source
	 * @param entities All entities in the change, mapped by ID. The entities here may
	 * @param fields The set to add required fields to
	 */
	public void specifyDataRequirements(EntityChange<?> change, Map<EntityIdentity<?>, ObservableEntity<?>> entities,
		Set<EntityValueAccess<?, ?>> fields) {
		boolean changeSuper, changeSub;
		if (theSelection.getEntityType().equals(change.getEntityType()))
			changeSub = changeSuper = false;
		else if (theSelection.getEntityType().isAssignableFrom(change.getEntityType())) {
			changeSub = true;
			changeSuper = false;
		} else if (change.getEntityType().isAssignableFrom(theSelection.getEntityType())) {
			boolean hasMember = false;
			for (EntityIdentity<?> id : change.getEntities()) {
				if (theSelection.getEntityType().isAssignableFrom(id.getEntityType())) {
					hasMember = true;
					break;
				}
			}
			if (!hasMember)
				return; // No entities of this type
			changeSuper = true;
			changeSub = false;
		} else
			return; // Irrelevant
		addRequirements(change, entities, fields, theSelection, changeSuper, changeSub);
	}

	private Ternian addRequirements(EntityChange<?> change, Map<EntityIdentity<?>, ObservableEntity<?>> entities,
		Set<EntityValueAccess<?, ?>> fields, EntityCondition<E> condition, boolean changeSuper, boolean changeSub) {
		if (condition instanceof EntityCondition.ValueCondition) {
			EntityValueAccess<E, ?> field = ((EntityCondition.ValueCondition<E, ?>) condition).getField();
			if (!isIdentity(field) && !isLoadedInAll(field, change.getEntities(), entities, changeSuper, changeSub)) {
				fields.add(field);
				return Ternian.NONE;
			} else {
				boolean hasTrue = false, hasFalse = false;
				for (EntityIdentity<?> id : change.getEntities()) {
					if (changeSuper && !condition.getEntityType().isAssignableFrom(id.getEntityType()))
						continue;
					boolean test = condition.test((ObservableEntity<? extends E>) entities.get(id), QuickMap.empty()) == null;
					if (test) {
						if (hasFalse)
							break;
						hasTrue = true;
					} else {
						if (hasTrue)
							break;
						hasFalse = true;
					}
				}
				if (hasTrue && !hasFalse)
					return Ternian.TRUE;
				else if (hasFalse && !hasTrue)
					return Ternian.FALSE;
				else
					return Ternian.NONE;
			}
		} else if (condition instanceof EntityCondition.OrCondition) {
			boolean unknown = false;
			for (EntityCondition<E> child : ((EntityCondition.OrCondition<E>) condition).getConditions()) {
				Ternian test = addRequirements(change, entities, fields, child, changeSuper, changeSub);
				switch (test) {
				case TRUE:
					return Ternian.TRUE; // If one child condition is true, then the condition is true without needing to test further
				case NONE:
					unknown = true;
					break;
				default:
				}
			}
			return unknown ? Ternian.NONE : Ternian.FALSE;
		} else if (condition instanceof EntityCondition.AndCondition) {
			boolean unknown = false;
			for (EntityCondition<E> child : ((EntityCondition.AndCondition<E>) condition).getConditions()) {
				Ternian test = addRequirements(change, entities, fields, child, changeSuper, changeSub);
				switch (test) {
				case FALSE:
					return Ternian.FALSE; // If one child condition is false, then the condition is false without needing to test further
				case NONE:
					unknown = true;
					break;
				default:
				}
			}
			return unknown ? Ternian.NONE : Ternian.TRUE;
		} else if (condition instanceof EntityCondition.All)
			return Ternian.TRUE;
		else
			throw new IllegalStateException("Unrecognized entity condition type: " + condition.getClass().getName());
	}

	private boolean isIdentity(EntityValueAccess<E, ?> field) {
		if(field instanceof ObservableEntityFieldType)
			return ((ObservableEntityFieldType<E, ?>) field).getIdIndex() >= 0;
			else {
				for (ObservableEntityFieldType<?, ?> f : ((EntityChainAccess<E, ?>) field).getFieldSequence()) {
					if (f.getIdIndex() < 0)
						return false;
				}
				return true;
			}
	}

	private boolean isLoadedInAll(EntityValueAccess<E, ?> field, BetterList<? extends EntityIdentity<?>> ids,
		Map<EntityIdentity<?>, ObservableEntity<?>> entities, boolean changeSuper, boolean changeSub) {
		for (EntityIdentity<?> id : ids) {
			if (changeSuper && !theSelection.getEntityType().isAssignableFrom(id.getEntityType()))
				continue;
			if (field instanceof ObservableEntityFieldType) {
				if (!((ObservableEntity<? extends E>) entities.get(id)).isLoaded((ObservableEntityFieldType<E, ?>) field))
					return false;
			} else {
				Object entity = entities.get(id);
				for (ObservableEntityFieldType<?, ?> f : ((EntityChainAccess<E, ?>) field).getFieldSequence()) {
					if (!((ObservableEntity<Object>) entity).isLoaded((ObservableEntityFieldType<Object, ?>) f))
						return false;
					entity = ((ObservableEntity<Object>) entity).get((ObservableEntityFieldType<Object, ?>) f);
				}
			}
		}
		return true;
	}

	/**
	 * Handles a change from the data source, using entities populated with all data specified by
	 * {@link #specifyDataRequirements(EntityChange, Map, Set)}.
	 *
	 * This method may add, remove, or update query results to account for the change. Any entities inserted into query results must be
	 * obtained from the <code>usage</code>, however. Entities may not be modified by this method and this method should never ask for field
	 * values other than those requested by {@link #specifyDataRequirements(EntityChange, Map, Set)}.
	 *
	 * @param change The change to handle
	 * @param entities All entities referenced by the change, by ID
	 * @param usage The usage function to obtain insertable entities
	 */
	public void handleChange(EntityChange<?> change, Map<EntityIdentity<?>, ObservableEntity<?>> entities,
		QueryResultsTree.EntityUsage usage) {
		boolean changeSuper;
		if (theSelection.getEntityType().equals(change.getEntityType()))
			changeSuper = false;
		else if (theSelection.getEntityType().isAssignableFrom(change.getEntityType())) {
			changeSuper = false;
		} else if (change.getEntityType().isAssignableFrom(theSelection.getEntityType())) {
			changeSuper = true;
		} else
			return; // Irrelevant

		if (theRawResults != null) {
			for (EntityIdentity<?> id : change.getEntities()) {
				if (changeSuper && !theSelection.getEntityType().isAssignableFrom(id.getEntityType()))
					continue;
				ObservableEntity<? extends E> entity = (ObservableEntity<? extends E>) entities.get(id);
				boolean included = theSelection.test(entity, QuickMap.empty()) == null;
				switch (change.changeType) {
				case add:
					if (included)
						theRawResults.add(usage.use(entity));
					break;
				case remove:
					if (included)
						theRawResults.remove(entity);
					break;
				default:
					if (included) {
						CollectionElement<ObservableEntity<? extends E>> element = theRawResults.getElement(entity, true);
						if (element == null)
							theRawResults.add(usage.use(entity));
						else
							theRawResults.mutableElement(element.getElementId()).set(element.get());
					} else
						theRawResults.remove(entity); // No effect if already removed
					break;
				}
			}
		} else {
			switch (change.changeType) {
			case add:
				theRawCountResult.set(theRawCountResult.get() + entities.size(), change);
				break;
			case remove:
				theRawCountResult.set(theRawCountResult.get() - entities.size(), change);
				break;
			default:
				break;
			}
		}
	}

	public EntityCollectionResult<E> getResults(EntityQuery<E> query, boolean withUpdates) {
		theListening.getAndIncrement();
		return new ResultWrapper(query, theExposedResults, withUpdates);
	}

	public EntityCountResult<E> getCountResults(EntityQuery<E> query) {
		theListening.getAndIncrement();
		ObservableValue<Long> count;
		if (theRawCountResult != null)
			count = theRawCountResult;
		else
			count = theRawResults.observeSize().map(i -> (long) i);
		return new CountResultWrapper(query, count);
	}

	static <E> Comparator<ObservableEntity<? extends E>> createComparator(EntityQuery<E> query) {
		return new Comparator<ObservableEntity<? extends E>>() {
			@Override
			public int compare(ObservableEntity<? extends E> o1, ObservableEntity<? extends E> o2) {
				int idCompare = o1.getId().compareTo(o2.getId());
				if (idCompare == 0)
					return 0;
				for (QueryOrder<E, ?> order : query.getOrder()) {
					int comp = compare(o1, o2, order);
					if (comp != 0)
						return comp;
				}
				return idCompare;
			}

			private <F> int compare(ObservableEntity<? extends E> o1, ObservableEntity<? extends E> o2, QueryOrder<E, F> order) {
				return order.getValue().compare(order.getValue().getValue(o1), order.getValue().getValue(o2));
			}
		};
	}

	public ObservableSortedSet<ObservableEntity<? extends E>> getResults() {
		return theRawResults;
	}

	Subscription wrap(Supplier<Subscription> listen) {
		theListening.getAndIncrement();
		Subscription superSub = listen.get();
		return new Subscription() {
			private boolean isSubscribed = true;

			@Override
			public void unsubscribe() {
				if (isSubscribed) {
					isSubscribed = false;
					superSub.unsubscribe();
					theListening.getAndDecrement();
				}
			}
		};
	}

	abstract class QueryResultWrapper implements EntityQueryResult<E> {
		private final EntityQuery<E> theQuery;
		private AtomicBoolean isCanceled;

		QueryResultWrapper(EntityQuery<E> query) {
			theQuery = query;
			isCanceled = getStatus().isDone() ? null : new AtomicBoolean(false);
		}

		@Override
		public EntityQuery<E> getOperation() {
			return theQuery;
		}

		@Override
		public void cancel(boolean mayInterruptIfRunning) {
			AtomicBoolean canceled = isCanceled;
			if (canceled != null && !canceled.getAndSet(true))
				QueryResults.this.cancel(mayInterruptIfRunning);
		}

		@Override
		public ResultStatus getStatus() {
			return QueryResults.this.getStatus();
		}

		@Override
		public EntityOperationException getFailure() {
			return QueryResults.this.getFailure();
		}

		@Override
		public Observable<? extends ObservableEntityResult<E>> watchStatus() {
			return QueryResults.this.watchStatus();
		}
	}

	class ResultWrapper extends QueryResultWrapper implements EntityCollectionResult<E> {
		private final EntitySetImpl theEntitySet;
		private final EntitySet theEntities;

		ResultWrapper(EntityQuery<E> query, ObservableSortedSet<ObservableEntity<? extends E>> results, boolean withUpdates) {
			super(query);
			theEntitySet = new EntitySetImpl();
			theEntities = new EntitySet(query, results, withUpdates);
		}

		@Override
		public ObservableEntitySet<E> get() {
			return theEntitySet;
		}

		class EntitySetImpl implements ObservableEntitySet<E> {
			@Override
			public EntityCollectionResult<E> getResults() {
				return ResultWrapper.this;
			}

			@Override
			public ObservableSortedSet<? extends ObservableEntity<? extends E>> getEntities() {
				return theEntities;
			}

			@Override
			public <E2 extends E> ConfigurableCreator<E, E2> create(TypeToken<E2> subType) {
				ObservableEntityType<E2> subEntity = getType().getEntitySet().getEntityType(TypeTokens.getRawType(subType));
				if (!getType().isAssignableFrom(subEntity))
					throw new IllegalArgumentException("No such sub-entity " + subType);
				QuickMap<String, Object> fieldValues = subEntity.getFields().keySet().createMap();
				return new ConfigurableCreatorImpl<>(subEntity, QuickMap.empty(), fieldValues.unmodifiable(), //
					subEntity.getFields().keySet().<EntityOperationVariable<E2>> createMap().unmodifiable(), QueryResults.this);
			}
		}

		class EntitySet extends ObservableCollectionWrapper<ObservableEntity<? extends E>>
		implements ObservableSortedSet<ObservableEntity<? extends E>> {
			EntitySet(EntityQuery<E> query, ObservableSortedSet<ObservableEntity<? extends E>> results, boolean withUpdates) {
				ObservableCollection.DistinctSortedDataFlow<//
				ObservableEntity<? extends E>, ObservableEntity<? extends E>, ObservableEntity<? extends E>> flow = results.flow();
				if (!getOperation().getOrder().isEmpty())
					flow = flow.distinctSorted(createComparator(query), false);
				if (!withUpdates)
					flow = flow.mapEquivalent(flow.getTargetType(), e -> e, e -> e, opts -> opts.cache(false).fireIfUnchanged(false));
				init(flow.collect());
			}

			@Override
			protected ObservableSortedSet<ObservableEntity<? extends E>> getWrapped() throws IllegalStateException {
				return (ObservableSortedSet<ObservableEntity<? extends E>>) super.getWrapped();
			}

			@Override
			public MutableCollectionElement<ObservableEntity<? extends E>> mutableElement(ElementId id) {
				MutableCollectionElement<ObservableEntity<? extends E>> superMCE = super.mutableElement(id);
				return new MutableCollectionElement<ObservableEntity<? extends E>>() {
					@Override
					public ElementId getElementId() {
						return superMCE.getElementId();
					}

					@Override
					public ObservableEntity<? extends E> get() {
						return superMCE.get();
					}

					@Override
					public BetterCollection<ObservableEntity<? extends E>> getCollection() {
						return superMCE.getCollection();
					}

					@Override
					public String isEnabled() {
						return superMCE.isEnabled();
					}

					@Override
					public String isAcceptable(ObservableEntity<? extends E> value) {
						return superMCE.isAcceptable(value); // Let the filter report the error
					}

					@Override
					public void set(ObservableEntity<? extends E> value) throws UnsupportedOperationException, IllegalArgumentException {
						superMCE.set(value); // Let the filter throw the exception
					}

					@Override
					public String canRemove() {
						return get().canDelete();
					}

					@Override
					public void remove() throws UnsupportedOperationException {
						get().delete(null);
					}
				};
			}

			@Override
			public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends ObservableEntity<? extends E>>> observer) {
				return wrap(() -> super.onChange(observer));
			}

			@Override
			public int indexFor(Comparable<? super ObservableEntity<? extends E>> search) {
				return getWrapped().indexFor(search);
			}

			@Override
			public Comparator<? super ObservableEntity<? extends E>> comparator() {
				return getWrapped().comparator();
			}

			@Override
			public CollectionElement<ObservableEntity<? extends E>> search(Comparable<? super ObservableEntity<? extends E>> search,
				SortedSearchFilter filter) {
				return getWrapped().search(search, filter);
			}

			@Override
			public CollectionElement<ObservableEntity<? extends E>> getOrAdd(ObservableEntity<? extends E> value, ElementId after,
				ElementId before, boolean first, Runnable added) {
				// The wrapped set will throw the exception if add is invoked
				return getWrapped().getOrAdd(value, after, before, first, added);
			}

			@Override
			public boolean isConsistent(ElementId element) {
				return true; // Shouldn't be any way for the set to become inconsistent
			}

			@Override
			public boolean checkConsistency() {
				return false; // Shouldn't be any way for the set to become inconsistent
			}

			@Override
			public <X> boolean repair(ElementId element, RepairListener<ObservableEntity<? extends E>, X> listener) {
				return false; // Shouldn't be any way for the set to become inconsistent
			}

			@Override
			public <X> boolean repair(RepairListener<ObservableEntity<? extends E>, X> listener) {
				return false; // Shouldn't be any way for the set to become inconsistent
			}
		}
	}

	class CountResultWrapper extends QueryResultWrapper implements EntityCountResult<E> {
		private final ObservableValue<Long> theWrapped;

		CountResultWrapper(EntityQuery<E> query, ObservableValue<Long> wrapped) {
			super(query);
			theWrapped = wrapped;
		}

		@Override
		public Object getIdentity() {
			return theWrapped.getIdentity(); // TODO Can do better than this
		}

		@Override
		public long getStamp() {
			return theWrapped.getStamp();
		}

		@Override
		public TypeToken<Long> getType() {
			return theWrapped.getType();
		}

		@Override
		public Long get() {
			return theWrapped.get();
		}

		@Override
		public Observable<ObservableValueEvent<Long>> noInitChanges() {
			Observable<ObservableValueEvent<Long>> superChanges = theWrapped.noInitChanges();
			return new Observable<ObservableValueEvent<Long>>() {
				@Override
				public Object getIdentity() {
					return superChanges.getIdentity();
				}

				@Override
				public Subscription subscribe(Observer<? super ObservableValueEvent<Long>> observer) {
					return wrap(() -> superChanges.subscribe(observer));
				}

				@Override
				public boolean isSafe() {
					return superChanges.isSafe();
				}

				@Override
				public Transaction lock() {
					return superChanges.lock();
				}

				@Override
				public Transaction tryLock() {
					return superChanges.tryLock();
				}
			};
		}
	}
}
