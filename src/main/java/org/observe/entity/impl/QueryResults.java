package org.observe.entity.impl;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
import org.observe.entity.EntityChange;
import org.observe.entity.EntityCollectionResult;
import org.observe.entity.EntityCondition;
import org.observe.entity.EntityCountResult;
import org.observe.entity.EntityIdentity;
import org.observe.entity.EntityLoadRequest;
import org.observe.entity.EntityOperation;
import org.observe.entity.EntityOperationException;
import org.observe.entity.EntityQuery;
import org.observe.entity.ObservableEntity;
import org.observe.entity.ObservableEntityResult;
import org.observe.entity.QueryOrder;
import org.observe.entity.impl.QueryResultsTree.QueryResultNode;
import org.observe.util.ObservableCollectionWrapper;
import org.qommons.Transaction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.CollectionLockingStrategy;
import org.qommons.collect.ElementId;
import org.qommons.collect.MutableCollectionElement;
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
		super(true);
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
	public Subscription onStatusChange(Consumer<ObservableEntityResult<E>> onChange) {
		return onStatusChange(() -> onChange.accept(this));
	}

	public EntityCondition<E> getSelection() {
		return theSelection;
	}

	public boolean isActive() {
		switch (getStatus()) {
		case WAITING:
		case EXECUTING:
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
					&& !theSelection.test((ObservableEntity<? extends E>) entity, null))
					continue;
				theRawResults.add((ObservableEntity<? extends E>) entity);
			}
		} else if (!needsFilter && entities instanceof Collection) {
			init(((Collection<?>) entities).size());
		} else {
			long count = 0;
			for (ObservableEntity<?> entity : entities) {
				if (needsFilter && theSelection.getEntityType().isAssignableFrom(entity.getType())
					&& !theSelection.test((ObservableEntity<? extends E>) entity, null))
					continue;
				count++;
			}
			init(count);
		}
	}

	public void init(long count) {
		theRawCountResult.set(count, null);
	}

	public void addDataRequests(List<EntityChange<?>> changes, List<EntityLoadRequest<?>> loadRequests,
		Map<EntityIdentity<?>, ObservableEntityImpl<?>> entities) {
		int todo = todo; // TODO Auto-generated method stub
	}

	public void handleChange(EntityChange<?> change, Map<EntityIdentity<?>, ObservableEntityImpl<?>> entities) {
		int todo = todo; // TODO
	}

	public EntityCollectionResult<E> getResults(EntityQuery<E> query, boolean withUpdates) {
		return new ResultWrapper(query, theExposedResults, withUpdates);
	}

	public EntityCountResult<E> getCountResults(EntityQuery<E> query) {
		ObservableValue<Long> count;
		if (theRawCountResult != null)
			count = theRawCountResult;
		else
			count = theRawResults.observeSize().map(i -> (long) i);
		return new CountResultWrapper(query, count);
	}

	// Don't delete--may need this later
	private static <E> Comparator<ObservableEntity<? extends E>> createComparator(EntityQuery<E> query) {
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

	class ResultWrapper extends ObservableCollectionWrapper<ObservableEntity<? extends E>> implements EntityCollectionResult<E> {
		private final EntityQuery<E> theQuery;

		ResultWrapper(EntityQuery<E> query, ObservableSortedSet<ObservableEntity<? extends E>> results, boolean withUpdates) {
			theQuery = query;
			ObservableCollection.DistinctSortedDataFlow<//
			ObservableEntity<? extends E>, ObservableEntity<? extends E>, ObservableEntity<? extends E>> flow = results.flow();
			if (!theQuery.getOrder().isEmpty())
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
		public EntityQuery<E> getOperation() {
			return theQuery;
		}

		@Override
		public boolean cancel(boolean mayInterruptIfRunning) {
			return QueryResults.this.cancel(mayInterruptIfRunning);
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
		public Subscription onStatusChange(Consumer<ObservableEntityResult<E>> onChange) {
			return QueryResults.this.onStatusChange(() -> onChange.accept(this));
		}

		@Override
		public EntityCollectionResult<E> dispose() {
			QueryResults.this.dispose();
			return this;
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
			ElementId before, boolean first,
			Runnable added) {
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

	class CountResultWrapper implements EntityCountResult<E> {
		private final EntityQuery<E> theQuery;
		private final ObservableValue<Long> theWrapped;

		CountResultWrapper(EntityQuery<E> query, ObservableValue<Long> wrapped) {
			theQuery = query;
			theWrapped = wrapped;
		}

		@Override
		public EntityQuery<E> getOperation() {
			return theQuery;
		}

		@Override
		public boolean cancel(boolean mayInterruptIfRunning) {
			return QueryResults.this.cancel(mayInterruptIfRunning);
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
		public Subscription onStatusChange(Consumer<ObservableEntityResult<E>> onChange) {
			return QueryResults.this.onStatusChange(() -> onChange.accept(this));
		}

		@Override
		public EntityCountResult<E> dispose() {
			QueryResults.this.dispose();
			return this;
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
