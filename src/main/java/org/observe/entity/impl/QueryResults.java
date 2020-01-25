package org.observe.entity.impl;

import java.util.Comparator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.SettableValue;
import org.observe.Subscription;
import org.observe.collect.ObservableCollectionEvent;
import org.observe.collect.ObservableSortedSet;
import org.observe.entity.EntityQuery;
import org.observe.entity.ObservableEntity;
import org.observe.entity.QueryOrder;
import org.observe.util.ObservableCollectionWrapper;
import org.qommons.Transaction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.CollectionLockingStrategy;
import org.qommons.collect.ElementId;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.collect.RRWLockingStrategy;

import com.google.common.reflect.TypeToken;

public class QueryResults<E> {
	private final EntityQuery<E> theQuery;
	private final ObservableSortedSet<ObservableEntity<E>> theRawResults;
	private final ObservableSortedSet<ObservableEntity<E>> theExposedResults;
	private final SettableValue<Long> theRawCountResult;
	private final ObservableValue<Long> theExposedCountResult;

	private final AtomicInteger theListening;
	private boolean isAdjusting;

	public QueryResults(EntityQuery<E> query, boolean count) {
		theQuery = query;
		theListening = new AtomicInteger();
		if (count) {
			theRawCountResult = SettableValue.build(long.class).withLock(query.getEntityType().getEntitySet()).withValue(0L).build();
			theExposedCountResult = new CountResultWrapper(theRawCountResult);
			theExposedResults = theRawResults = null;
		} else {
			theExposedCountResult = theRawCountResult = null;
			CollectionLockingStrategy locker = new RRWLockingStrategy(query.getEntityType().getEntitySet());
			Comparator<ObservableEntity<E>> sorting = createComparator(theQuery);
			if (query.getEntityType().getEntityType() != null) {
				TypeToken<ObservableEntity<E>> type = ObservableEntity.TYPE_KEY.getCompoundType(query.getEntityType().getEntityType());
				theRawResults = ObservableSortedSet.build(type, sorting).withLocker(locker).build();
			} else {
				TypeToken<ObservableEntity<E>> type = (TypeToken<ObservableEntity<E>>) (TypeToken<?>) ObservableEntity.TYPE;
				theRawResults = ObservableSortedSet.build(type, sorting).withLocker(locker).build();
			}
			theExposedResults = new ResultWrapper(theRawResults.flow().filterMod(fm -> {
				fm.noAdd("Results cannot be added to query results directly");
				fm.filterRemove(ObservableEntity::canDelete);
			}).collectPassive());
		}
	}

	private static <E> Comparator<ObservableEntity<E>> createComparator(EntityQuery<E> query) {
		return new Comparator<ObservableEntity<E>>() {
			@Override
			public int compare(ObservableEntity<E> o1, ObservableEntity<E> o2) {
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

			private <F> int compare(ObservableEntity<E> o1, ObservableEntity<E> o2, QueryOrder<E, F> order) {
				return order.getValue().compare(order.getValue().getValue(o1), order.getValue().getValue(o2));
			}
		};
	}

	public ObservableSortedSet<ObservableEntity<E>> getResults() {
		return theExposedResults;
	}

	public ObservableValue<Long> getCount() {
		return theExposedCountResult;
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

	class ResultWrapper extends ObservableCollectionWrapper<ObservableEntity<E>> implements ObservableSortedSet<ObservableEntity<E>> {
		public ResultWrapper(ObservableSortedSet<ObservableEntity<E>> wrapped) {
			init(wrapped);
		}

		@Override
		protected ObservableSortedSet<ObservableEntity<E>> getWrapped() throws IllegalStateException {
			return (ObservableSortedSet<ObservableEntity<E>>) super.getWrapped();
		}

		@Override
		public MutableCollectionElement<ObservableEntity<E>> mutableElement(ElementId id) {
			MutableCollectionElement<ObservableEntity<E>> superMCE = super.mutableElement(id);
			return new MutableCollectionElement<ObservableEntity<E>>() {
				@Override
				public ElementId getElementId() {
					return superMCE.getElementId();
				}

				@Override
				public ObservableEntity<E> get() {
					return superMCE.get();
				}

				@Override
				public BetterCollection<ObservableEntity<E>> getCollection() {
					return superMCE.getCollection();
				}

				@Override
				public String isEnabled() {
					return superMCE.isEnabled();
				}

				@Override
				public String isAcceptable(ObservableEntity<E> value) {
					return superMCE.isAcceptable(value); // Let the filter report the error
				}

				@Override
				public void set(ObservableEntity<E> value) throws UnsupportedOperationException, IllegalArgumentException {
					superMCE.set(value); // Let the filter throw the exception
				}

				@Override
				public String canRemove() {
					return get().canDelete();
				}

				@Override
				public void remove() throws UnsupportedOperationException {
					get().delete();
				}
			};
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends ObservableEntity<E>>> observer) {
			return wrap(() -> super.onChange(observer));
		}

		@Override
		public int indexFor(Comparable<? super ObservableEntity<E>> search) {
			return getWrapped().indexFor(search);
		}

		@Override
		public Comparator<? super ObservableEntity<E>> comparator() {
			return getWrapped().comparator();
		}

		@Override
		public CollectionElement<ObservableEntity<E>> search(Comparable<? super ObservableEntity<E>> search, SortedSearchFilter filter) {
			return getWrapped().search(search, filter);
		}

		@Override
		public CollectionElement<ObservableEntity<E>> getOrAdd(ObservableEntity<E> value, ElementId after, ElementId before, boolean first,
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
		public <X> boolean repair(ElementId element, RepairListener<ObservableEntity<E>, X> listener) {
			return false; // Shouldn't be any way for the set to become inconsistent
		}

		@Override
		public <X> boolean repair(RepairListener<ObservableEntity<E>, X> listener) {
			return false; // Shouldn't be any way for the set to become inconsistent
		}
	}

	class CountResultWrapper implements ObservableValue<Long> {
		private final ObservableValue<Long> theWrapped;

		CountResultWrapper(ObservableValue<Long> wrapped) {
			theWrapped = wrapped;
		}

		@Override
		public Object getIdentity() {
			return theWrapped.getIdentity();
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
