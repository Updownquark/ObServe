package org.observe.collect;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.Subscription;
import org.observe.collect.ObservableCollectionImpl.FlattenedObservableCollection;
import org.observe.collect.ObservableOrderedCollectionImpl.CachedOrderedCollection;
import org.observe.collect.ObservableOrderedCollectionImpl.CombinedOrderedCollection;
import org.observe.collect.ObservableOrderedCollectionImpl.EquivalenceSwitchedOrderedCollection;
import org.observe.collect.ObservableOrderedCollectionImpl.FilterMappedOrderedCollection;
import org.observe.collect.ObservableOrderedCollectionImpl.FlattenedOrderedCollection;
import org.observe.collect.ObservableOrderedCollectionImpl.FlattenedOrderedValueCollection;
import org.observe.collect.ObservableOrderedCollectionImpl.FlattenedOrderedValuesCollection;
import org.observe.collect.ObservableOrderedCollectionImpl.ModFilteredOrderedCollection;
import org.observe.collect.ObservableOrderedCollectionImpl.RefreshingOrderedCollection;
import org.observe.collect.ObservableOrderedCollectionImpl.TakenUntilOrderedCollection;
import org.observe.collect.ObservableReversibleSpliterator.WrappingReversibleObservableSpliterator;
import org.qommons.Ternian;
import org.qommons.Transaction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.ReversibleCollection;
import org.qommons.collect.TreeList;
import org.qommons.tree.CountedRedBlackNode.DefaultNode;
import org.qommons.tree.CountedRedBlackNode.DefaultTreeSet;

import com.google.common.reflect.TypeToken;

/** Holds default implementation methods and classes for {@link ObservableReversibleCollection} */
public class ObservableReversibleCollectionImpl {
	private ObservableReversibleCollectionImpl() {}

	/**
	 * Simple implementation of {@link ObservableReversibleCollection#removeLast(Object)}
	 *
	 * @param <E> The type of elements in the collection
	 * @param coll The collection to remove the element from
	 * @param o The value to remove from the collection
	 * @return Whether the value was found and removed
	 */
	public static <E> boolean removeLast(ObservableReversibleCollection<E> coll, Object o) {
		try (Transaction t = coll.lock(true, null)) {
			ObservableReversibleSpliterator<E> spliter = coll.spliterator(false);
			boolean[] found = new boolean[1];
			while (!found[0] && spliter.tryReverseElement(el -> {
				if (coll.equivalence().elementEquals(el.get(), o)) {
					el.remove();
					found[0] = true;
				}
			})) {
			}
			return found[0];
		}
	}

	/**
	 * Implements {@link ObservableReversibleCollection#find(Predicate, Supplier, boolean)}
	 *
	 * @param <E> The type of the value
	 */
	public static class ReversibleCollectionFinder<E> implements ObservableValue<E> {
		private final ObservableReversibleCollection<E> theCollection;
		private final Predicate<? super E> theTest;
		private final Supplier<? extends E> theDefaultValue;
		private final boolean isFirst;

		/**
		 * @param collection The collection to find elements in
		 * @param test The test to find elements that pass
		 * @param defaultValue Provides default values when no elements of the collection pass the test
		 * @param first Whether to get the first value in the collection that passes or the last value
		 */
		protected ReversibleCollectionFinder(ObservableReversibleCollection<E> collection, Predicate<? super E> test,
			Supplier<? extends E> defaultValue, boolean first) {
			theCollection = collection;
			theTest = test;
			theDefaultValue = defaultValue;
			isFirst = first;
		}

		@Override
		public TypeToken<E> getType() {
			return theCollection.getType();
		}

		@Override
		public boolean isSafe() {
			return true;
		}

		@Override
		public E get() {
			Object[] value = new Object[1];
			if (theCollection.find(theTest, el -> value[0] = el.get(), isFirst))
				return (E) value[0];
			else
				return theDefaultValue.get();
		}

		@Override
		public Subscription subscribe(Observer<? super ObservableValueEvent<E>> observer) {
			boolean[] initialized = new boolean[1];
			Consumer<OrderedCollectionEvent<? extends E>> collObs = new Consumer<OrderedCollectionEvent<? extends E>>() {
				private ElementId theCurrentId;
				private E theCurrentValue;

				@Override
				public void accept(OrderedCollectionEvent<? extends E> evt) {
					switch (evt.getType()) {
					case add:
						if (!theTest.test(evt.getNewValue()))
							return;
						doAdd(evt);
						break;
					case remove:
						if (!theTest.test(evt.getOldValue()) || !Objects.equals(theCurrentId, evt.getElementId()))
							return;
						doRemove(evt);
						break;
					case set:
						boolean oldPass = theTest.test(evt.getOldValue());
						boolean newPass = evt.getOldValue() == evt.getNewValue() ? oldPass : theTest.test(evt.getNewValue());
						if (oldPass == newPass) {
							if (!oldPass || !Objects.equals(theCurrentId, evt.getElementId()))
								return;
							E oldValue = theCurrentValue;
							theCurrentValue = evt.getNewValue();
							fireChangeEvent(oldValue, theCurrentValue, evt, observer::onNext);
						} else if (!oldPass) {
							doAdd(evt);
						} else if (Objects.equals(theCurrentId, evt.getElementId())) {
							doRemove(evt);
						}
						break;
					}
				}

				private void doAdd(ObservableCollectionEvent<? extends E> evt) {
					if (theCurrentId == null || (evt.getElementId().compareTo(theCurrentId) < 0) == isFirst) {
						theCurrentId = evt.getElementId();
						theCurrentValue = evt.getNewValue();
						if (initialized[0])
							fireChangeEvent(theCurrentValue, evt.getNewValue(), evt, observer::onNext);
					}
				}

				private void doRemove(ObservableCollectionEvent<? extends E> evt) {
					E oldValue = theCurrentValue;
					theCurrentId = null;
					theCurrentValue = null;
					theCollection.findObservableElement(theTest, el -> {
						theCurrentId = el.getElementId();
						theCurrentValue = el.get();
					}, isFirst);
					fireChangeEvent(oldValue, theCurrentValue, evt, observer::onNext);
				}
			};
			if (isFirst)
				return theCollection.subscribeOrdered(collObs);
			else
				return theCollection.subscribeReverse(collObs);
		}
	}

	/**
	 * Implements {@link ObservableReversibleCollection#withEquivalence(Equivalence)}
	 *
	 * @param <E> The type of values in the collection
	 */
	public static class EquivalenceSwitchedReversibleCollection<E> extends EquivalenceSwitchedOrderedCollection<E>
	implements ObservableReversibleCollection<E> {
		/**
		 * @param wrap The source collection
		 * @param equivalence The equivalence set to use for equivalence operations
		 */
		protected EquivalenceSwitchedReversibleCollection(ObservableReversibleCollection<E> wrap, Equivalence<? super E> equivalence) {
			super(wrap, equivalence);
		}

		@Override
		protected ObservableReversibleCollection<E> getWrapped() {
			return (ObservableReversibleCollection<E>) super.getWrapped();
		}

		@Override
		public boolean removeLast(Object o) {
			return ObservableReversibleCollectionImpl.removeLast(this, o);
		}

		@Override
		public ObservableReversibleSpliterator<E> spliterator() {
			return spliterator(true);
		}

		@Override
		public ObservableReversibleSpliterator<E> spliterator(boolean fromStart) {
			return getWrapped().spliterator(fromStart);
		}

		@Override
		public CollectionSubscription subscribeReverse(Consumer<? super OrderedCollectionEvent<? extends E>> observer) {
			return getWrapped().subscribeReverse(observer);
		}
	}

	/**
	 * Implements {@link ObservableReversibleCollection#filterMap(FilterMapDef)}
	 *
	 * @param <E> The type of the collection to filter/map
	 * @param <T> The type of the filter/mapped collection
	 */
	public static class FilterMappedReversibleCollection<E, T> extends FilterMappedOrderedCollection<E, T>
	implements ObservableReversibleCollection<T> {
		/**
		 * @param wrap The source collection
		 * @param filterMapDef The filter-mapping definition defining which elements are filtered from the collection and how they are
		 *        mapped
		 */
		protected FilterMappedReversibleCollection(ObservableReversibleCollection<E> wrap, FilterMapDef<E, ?, T> filterMapDef) {
			super(wrap, filterMapDef);
		}

		@Override
		protected ObservableReversibleCollection<E> getWrapped() {
			return (ObservableReversibleCollection<E>) super.getWrapped();
		}

		@Override
		public boolean removeLast(Object o) {
			if (o != null && !getDef().checkDestType(o))
				return false;
			if (getDef().isReversible()) {
				FilterMapResult<T, E> reversed = getDef().reverse(new FilterMapResult<>((T) o));
				if (reversed.error != null)
					return false;
				return getWrapped().removeLast(reversed.result);
			} else
				return ObservableReversibleCollectionImpl.removeLast(this, o);
		}

		@Override
		public ObservableReversibleSpliterator<T> spliterator() {
			return spliterator(true);
		}

		@Override
		public ObservableReversibleSpliterator<T> spliterator(boolean fromStart) {
			return new WrappingReversibleObservableSpliterator<>(getWrapped().spliterator(fromStart), getType(), map());
		}

		@Override
		public CollectionSubscription subscribeReverse(Consumer<? super OrderedCollectionEvent<? extends T>> observer) {
			return getWrapped()
				.subscribeReverse(new FilterMappedObserver(evt -> observer.accept((OrderedCollectionEvent<? extends T>) evt)));
		}
	}

	/**
	 * Implements {@link ObservableReversibleCollection#combine(ObservableCollection.CombinedCollectionDef)}
	 *
	 * @param <E> The type of values in the source collection
	 * @param <V> The type of values in this collection
	 */
	public static class CombinedReversibleCollection<E, V> extends CombinedOrderedCollection<E, V>
	implements ObservableReversibleCollection<V> {
		/**
		 * @param wrap The source collection
		 * @param def The combination definition containing the observable values to combine the source collection's elements with and how
		 *        to combine them
		 */
		protected CombinedReversibleCollection(ObservableReversibleCollection<E> wrap,
			org.observe.collect.ObservableCollection.CombinedCollectionDef<E, V> def) {
			super(wrap, def);
		}

		@Override
		protected ObservableReversibleCollection<E> getWrapped() {
			return (ObservableReversibleCollection<E>) super.getWrapped();
		}

		@Override
		public boolean removeLast(Object value) {
			if (getDef().getReverse() != null) {
				if (value == null && !getDef().areNullsReversed())
					return false;
				else if (value != null && getDef().targetType.getRawType().isInstance(value))
					return false;

				Map<ObservableValue<?>, Object> argValues = new HashMap<>(getDef().getArgs().size() * 4 / 3);
				for (ObservableValue<?> arg : getDef().getArgs())
					argValues.put(arg, arg.get());
				StaticCombinedValues<V> combined = new StaticCombinedValues<>();
				combined.argValues = argValues;
				combined.element = (V) value;
				return getWrapped().removeLast(getDef().getReverse().apply(combined));
			} else
				return ObservableReversibleCollectionImpl.removeLast(this, value);
		}

		@Override
		public ObservableReversibleSpliterator<V> spliterator() {
			return spliterator(true);
		}

		@Override
		public ObservableReversibleSpliterator<V> spliterator(boolean fromStart) {
			return new WrappingReversibleObservableSpliterator<>(getWrapped().spliterator(fromStart), getType(), map());
		}

		@Override
		public CollectionSubscription subscribeReverse(Consumer<? super OrderedCollectionEvent<? extends V>> observer) {
			CombinedObserver combinedObs = new CombinedObserver(evt -> observer.accept((OrderedCollectionEvent<? extends V>) evt));
			try (Transaction t = getWrapped().lock(false, null)) {
				combinedObs.init(getWrapped().subscribeReverse(combinedObs));
			}
			return combinedObs;
		}
	}

	/**
	 * Implements {@link ObservableReversibleCollection#reverse()}
	 *
	 * @param <E> The type of elements in the collection
	 */
	public static class ObservableReversedCollection<E> extends ReversibleCollection.ReversedCollection<E>
	implements ObservableReversibleCollection<E> {
		/** @param wrap The source collection */
		protected ObservableReversedCollection(ObservableReversibleCollection<E> wrap) {
			super(wrap);
		}

		@Override
		protected ObservableReversibleCollection<E> getWrapped() {
			return (ObservableReversibleCollection<E>) super.getWrapped();
		}

		@Override
		public TypeToken<E> getType() {
			return getWrapped().getType();
		}

		@Override
		public Equivalence<? super E> equivalence() {
			return getWrapped().equivalence();
		}

		@Override
		public boolean isLockSupported() {
			return getWrapped().isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return getWrapped().lock(write, cause);
		}

		@Override
		public ObservableReversibleSpliterator<E> spliterator() {
			return spliterator(true);
		}

		@Override
		public ObservableReversibleSpliterator<E> spliterator(boolean fromStart) {
			return (ObservableReversibleSpliterator<E>) super.spliterator(fromStart);
		}

		@Override
		public ObservableReversibleCollection<E> reverse() {
			return getWrapped();
		}

		@Override
		public E get(int index) {
			try (Transaction t = getWrapped().lock(false, null)) {
				return getWrapped().get(getWrapped().size() - index - 1);
			}
		}

		@Override
		public int indexOf(Object value) {
			try (Transaction t = getWrapped().lock(false, null)) {
				return getWrapped().size() - getWrapped().lastIndexOf(value) - 1;
			}
		}

		@Override
		public int lastIndexOf(Object value) {
			try (Transaction t = getWrapped().lock(false, null)) {
				return getWrapped().size() - getWrapped().indexOf(value) - 1;
			}
		}

		@Override
		public E[] toArray() {
			return ObservableReversibleCollection.super.toArray();
		}

		@Override
		public <T> T[] toArray(T[] a) {
			return ObservableReversibleCollection.super.toArray(a);
		}

		@Override
		public String canRemove(Object value) {
			return getWrapped().canRemove(value);
		}

		@Override
		public String canAdd(E value) {
			return getWrapped().canAdd(value);
		}

		@Override
		public CollectionSubscription subscribeOrdered(Consumer<? super OrderedCollectionEvent<? extends E>> observer) {
			return getWrapped().subscribeReverse(new ReversedSubscriber<>(observer));
		}

		@Override
		public CollectionSubscription subscribeReverse(Consumer<? super OrderedCollectionEvent<? extends E>> observer) {
			return getWrapped().subscribeOrdered(new ReversedSubscriber<>(observer));
		}

		private static class ReversedSubscriber<E> implements Consumer<OrderedCollectionEvent<? extends E>> {
			private final Consumer<? super OrderedCollectionEvent<? extends E>> theObserver;
			private int theSize;

			ReversedSubscriber(Consumer<? super OrderedCollectionEvent<? extends E>> observer) {
				theObserver = observer;
			}

			@Override
			public void accept(OrderedCollectionEvent<? extends E> evt) {
				if (evt.getType() == CollectionChangeType.add)
					theSize++;
				int index = theSize - evt.getIndex() - 1;
				if (evt.getType() == CollectionChangeType.remove)
					theSize++;
				OrderedCollectionEvent.doWith(new OrderedCollectionEvent<>(new ReversedElementId(evt.getElementId()), index, evt.getType(),
					evt.getOldValue(), evt.getNewValue(), evt), theObserver);
			}
		}

		private static class ReversedElementId implements ElementId {
			private final ElementId theSource;

			ReversedElementId(ElementId source) {
				super();
				theSource = source;
			}

			@Override
			public int compareTo(ElementId o) {
				return -theSource.compareTo(((ReversedElementId) o).theSource);
			}
		}
	}

	/**
	 * Implements {@link ObservableReversibleCollection#refresh(Observable)}
	 *
	 * @param <E> The type of the collection
	 */
	public static class RefreshingReversibleCollection<E> extends RefreshingOrderedCollection<E>
	implements ObservableReversibleCollection<E> {
		/**
		 * @param wrap The source collection
		 * @param refresh The observable to use to refresh the element values in the collection
		 */
		protected RefreshingReversibleCollection(ObservableReversibleCollection<E> wrap, Observable<?> refresh) {
			super(wrap, refresh);
		}

		@Override
		protected ObservableReversibleCollection<E> getWrapped() {
			return (ObservableReversibleCollection<E>) super.getWrapped();
		}

		@Override
		public boolean removeLast(Object o) {
			return getWrapped().removeLast(o);
		}

		@Override
		public ObservableReversibleSpliterator<E> spliterator() {
			return (ObservableReversibleSpliterator<E>) super.spliterator();
		}

		@Override
		public ObservableReversibleSpliterator<E> spliterator(boolean fromStart) {
			return getWrapped().spliterator(fromStart);
		}

		@Override
		public CollectionSubscription subscribeReverse(Consumer<? super OrderedCollectionEvent<? extends E>> observer) {
			CollectionSubscription collSub = getWrapped().subscribeReverse(observer);
			Subscription refreshSub = getRefresh().act(v -> {
				// There's a possibility that the refresh observable could fire on one thread while the collection fires on
				// another, so need to make sure the collection isn't firing while this refresh event happens.
				try (Transaction t = getWrapped().lock(false, v)) {
					doReverseRefresh(observer, v);
				}
			});
			return removeAll -> {
				refreshSub.unsubscribe();
				collSub.unsubscribe(removeAll);
			};
		}

		/**
		 * Does the refresh when this collection's {@link #getRefresh() refresh} observable fires
		 *
		 * @param observer The observer to fire the events to
		 * @param cause The object fired from the refresh observable
		 */
		protected void doReverseRefresh(Consumer<? super OrderedCollectionEvent<? extends E>> observer, Object cause) {
			int[] index = new int[] { getWrapped().size() - 1 };
			getWrapped().spliterator(false).forEachObservableElement(el -> {
				OrderedCollectionEvent.doWith(
					new OrderedCollectionEvent<>(el.getElementId(), index[0]--, CollectionChangeType.set, el.get(), el.get(), cause),
					observer::accept);
			});
		}
	}

	/**
	 * Implements {@link ObservableReversibleCollection#refreshEach(Function)}
	 *
	 * @param <E> The type of the collection
	 */
	public static class ElementRefreshingReversibleCollection<E>
	extends ObservableOrderedCollectionImpl.ElementRefreshingOrderedCollection<E> implements ObservableReversibleCollection<E> {
		/**
		 * @param wrap The source collection
		 * @param refresh The function of observables to use to refresh each element
		 */
		protected ElementRefreshingReversibleCollection(ObservableReversibleCollection<E> wrap,
			Function<? super E, Observable<?>> refresh) {
			super(wrap, refresh);
		}

		@Override
		protected ObservableReversibleCollection<E> getWrapped() {
			return (ObservableReversibleCollection<E>) super.getWrapped();
		}

		@Override
		public boolean removeLast(Object o) {
			return getWrapped().removeLast(o);
		}

		@Override
		public ObservableReversibleSpliterator<E> spliterator() {
			return (ObservableReversibleSpliterator<E>) super.spliterator();
		}

		@Override
		public ObservableReversibleSpliterator<E> spliterator(boolean fromStart) {
			return getWrapped().spliterator(fromStart);
		}

		@Override
		public CollectionSubscription subscribeReverse(Consumer<? super OrderedCollectionEvent<? extends E>> observer) {
			ElementRefreshingObserver refreshing = new ElementRefreshingObserver(
				evt -> observer.accept((OrderedCollectionEvent<? extends E>) evt));
			CollectionSubscription collSub = getWrapped().subscribeReverse(refreshing);
			return removeAll -> {
				collSub.unsubscribe(removeAll);
				// If removeAll is true, elements should be empty
				if (!removeAll) {
					refreshing.done();
				}
			};
		}
	}

	/**
	 * Implements {@link ObservableReversibleCollection#filterModification(ModFilterDef)}
	 *
	 * @param <E> The type of values in the collection
	 */
	public static class ModFilteredReversibleCollection<E> extends ModFilteredOrderedCollection<E>
	implements ObservableReversibleCollection<E> {
		/**
		 * @param wrapped The source collection
		 * @param def The definition to define which modifications are permitted on the collection
		 */
		protected ModFilteredReversibleCollection(ObservableReversibleCollection<E> wrapped,
			org.observe.collect.ObservableCollection.ModFilterDef<E> def) {
			super(wrapped, def);
		}

		@Override
		protected ObservableReversibleCollection<E> getWrapped() {
			return (ObservableReversibleCollection<E>) super.getWrapped();
		}

		@Override
		public boolean removeLast(Object value) {
			if (getDef().checkRemove(value) == null)
				return getWrapped().removeLast(value);
			else
				return false;
		}

		@Override
		public ObservableReversibleSpliterator<E> spliterator() {
			return spliterator(true);
		}

		@Override
		public ObservableReversibleSpliterator<E> spliterator(boolean fromStart) {
			return new WrappingReversibleObservableSpliterator<>(getWrapped().spliterator(fromStart), getType(), map());
		}

		@Override
		public CollectionSubscription subscribeReverse(Consumer<? super OrderedCollectionEvent<? extends E>> observer) {
			return getWrapped().subscribeReverse(observer);
		}
	}

	/**
	 * Implements {@link ObservableReversibleCollection#cached(Observable)}
	 *
	 * @param <E> The type of elements in the collection
	 */
	public static class CachedReversibleCollection<E> extends CachedOrderedCollection<E> implements ObservableReversibleCollection<E> {
		/**
		 * @param wrapped The collection whose values to reflect
		 * @param until The observable to listen to to cease caching
		 */
		protected CachedReversibleCollection(ObservableReversibleCollection<E> wrapped, Observable<?> until) {
			super(wrapped, until);
		}

		@Override
		protected ObservableReversibleCollection<E> getWrapped() {
			return (ObservableReversibleCollection<E>) super.getWrapped();
		}

		@Override
		protected BetterCollection<E> createCache() {
			return new TreeList<E>() {
				@Override
				public boolean contains(Object o) {
					return find(v -> equivalence().elementEquals(v, o), el -> {
					});
				}

				@Override
				public int indexOf(Object o) {
					DefaultNode<E> node = findNode(n -> equivalence().elementEquals(n.getValue(), o), Ternian.TRUE);
					return node == null ? -1 : node.getIndex();
				}

				@Override
				public int lastIndexOf(Object o) {
					DefaultNode<E> node = findNode(n -> equivalence().elementEquals(n.getValue(), o), Ternian.FALSE);
					return node == null ? -1 : node.getIndex();
				}
			};
		}

		@Override
		public boolean removeLast(Object o) {
			if (isDone())
				throw new IllegalStateException("This cached collection's finisher has fired");
			return getWrapped().removeLast(o);
		}

		@Override
		public ObservableReversibleSpliterator<E> spliterator() {
			return spliterator(true);
		}

		@Override
		public ObservableReversibleSpliterator<E> spliterator(boolean fromStart) {
			if (isDone())
				throw new IllegalStateException("This cached collection's finisher has fired");
			return new WrappingReversibleObservableSpliterator<>(getWrapped().spliterator(fromStart), getType(), map());
		}

		@Override
		public CollectionSubscription subscribeReverse(Consumer<? super OrderedCollectionEvent<? extends E>> observer) {
			if (isDone())
				throw new IllegalStateException("This cached collection's finisher has fired");
			Subscription changeSub;
			try (Transaction t = lock(false, null)) {
				for (Map.Entry<ElementId, E> entry : getCacheMap().descendingMap().entrySet())
					ObservableCollectionEvent.doWith(initialEvent(entry.getValue(), entry.getKey()), observer);
				changeSub = getChanges().act(observer::accept);
			}
			return removeAll -> {
				changeSub.unsubscribe();
				if (removeAll) {
					try (Transaction t = lock(false, null)) {
						// Remove from the front for reverse
						for (Map.Entry<ElementId, E> entry : getCacheMap().entrySet())
							ObservableCollectionEvent.doWith(removeEvent(entry.getValue(), entry.getKey()), observer);
					}
				}
			};
		}
	}

	/**
	 * Backs {@link ObservableReversibleCollection#takeUntil(Observable)}
	 *
	 * @param <E> The type of elements in the collection
	 */
	public static class TakenUntilReversibleCollection<E> extends TakenUntilOrderedCollection<E>
	implements ObservableReversibleCollection<E> {
		/**
		 * @param wrap The collection whose content to use
		 * @param until The observable to terminate observation into the collection
		 * @param terminate Whether the until observable's firing will remove all the collections's elements
		 */
		protected TakenUntilReversibleCollection(ObservableReversibleCollection<E> wrap, Observable<?> until, boolean terminate) {
			super(wrap, until, terminate);
		}

		@Override
		protected ObservableReversibleCollection<E> getWrapped() {
			return (ObservableReversibleCollection<E>) super.getWrapped();
		}

		@Override
		public boolean removeLast(Object o) {
			return getWrapped().removeLast(o);
		}

		@Override
		public ObservableReversibleSpliterator<E> spliterator() {
			return (ObservableReversibleSpliterator<E>) super.spliterator();
		}

		@Override
		public ObservableReversibleSpliterator<E> spliterator(boolean fromStart) {
			return getWrapped().spliterator(fromStart);
		}

		@Override
		public CollectionSubscription subscribeReverse(Consumer<? super OrderedCollectionEvent<? extends E>> observer) {
			CollectionSubscription collSub = getWrapped().subscribeReverse(observer);
			AtomicBoolean complete = new AtomicBoolean(false);
			Subscription obsSub = getUntil().take(1).act(u -> {
				if (!complete.getAndSet(true))
					collSub.unsubscribe(isTerminating());
			});
			return removeAll -> {
				if (!complete.getAndSet(true)) {
					obsSub.unsubscribe();
					collSub.unsubscribe(removeAll);
				}
				// If the until has already fired and this collection is non-terminating, there's no way to determine which elements the
				// listener knows about, hence no way to act on the removeAll given here. We just have to assume that for non-terminating
				// collections, the code no longer cares about the content.
			};
		}
	}

	/**
	 * Implements {@link ObservableReversibleCollection#flattenValues(ObservableReversibleCollection)}
	 *
	 * @param <E> The type of elements in the collection
	 */
	public static class FlattenedReversibleValuesCollection<E> extends FlattenedOrderedValuesCollection<E>
	implements ObservableReversibleCollection<E> {
		/** @param collection A collection of values to flatten */
		protected FlattenedReversibleValuesCollection(ObservableReversibleCollection<? extends ObservableValue<? extends E>> collection) {
			super(collection);
		}

		@Override
		protected ObservableReversibleCollection<? extends ObservableValue<? extends E>> getWrapped() {
			return (ObservableReversibleCollection<? extends ObservableValue<? extends E>>) super.getWrapped();
		}

		@Override
		public boolean removeLast(Object o) {
			return ObservableReversibleCollectionImpl.removeLast(this, o);
		}

		@Override
		public ObservableReversibleSpliterator<E> spliterator() {
			return spliterator(true);
		}

		@Override
		public ObservableReversibleSpliterator<E> spliterator(boolean fromStart) {
			return new WrappingReversibleObservableSpliterator<>(getWrapped().spliterator(fromStart), getType(), map());
		}

		@Override
		public CollectionSubscription subscribeReverse(Consumer<? super OrderedCollectionEvent<? extends E>> observer) {
			FlatteningObserver flattening = new FlatteningObserver(evt -> observer.accept((OrderedCollectionEvent<? extends E>) evt));
			CollectionSubscription collSub = getWrapped().subscribeReverse(flattening);
			return removeAll -> {
				try (Transaction t = getWrapped().lock(false, null)) {
					collSub.unsubscribe(removeAll);
					if (!removeAll) {
						flattening.done();
					}
				}
			};
		}
	}

	/**
	 * Implements {@link ObservableReversibleCollection#flattenValue(ObservableValue)}
	 *
	 * @param <E> The type of elements in the collection
	 */
	public static class FlattenedReversibleValueCollection<E> extends FlattenedOrderedValueCollection<E>
	implements ObservableReversibleCollection<E> {
		/** @param collectionObservable The value to present as a static collection */
		protected FlattenedReversibleValueCollection(
			ObservableValue<? extends ObservableReversibleCollection<? extends E>> collectionObservable) {
			super(collectionObservable);
		}

		@Override
		protected ObservableValue<? extends ObservableReversibleCollection<? extends E>> getWrapped() {
			return (ObservableValue<? extends ObservableReversibleCollection<? extends E>>) super.getWrapped();
		}

		@Override
		public boolean removeLast(Object o) {
			ObservableReversibleCollection<? extends E> coll = getWrapped().get();
			return coll == null ? false : coll.removeLast(o);
		}

		@Override
		public ObservableReversibleSpliterator<E> spliterator() {
			return spliterator(true);
		}

		@Override
		public ObservableReversibleSpliterator<E> spliterator(boolean fromStart) {
			ObservableReversibleCollection<? extends E> coll = getWrapped().get();
			return coll == null ? ObservableReversibleSpliterator.empty(getType())
				: new WrappingReversibleObservableSpliterator<>(coll.spliterator(fromStart), getType(), map());
		}

		@Override
		public CollectionSubscription subscribeReverse(Consumer<? super OrderedCollectionEvent<? extends E>> observer) {
			ReversibleFlattenedObserver flatObs = new ReversibleFlattenedObserver(
				evt -> observer.accept((OrderedCollectionEvent<? extends E>) evt), true);
			Subscription valueSub = getWrapped().safe().subscribe(flatObs);
			return removeAll -> {
				valueSub.unsubscribe();
				flatObs.unsubscribe(removeAll);
			};
		}

		/** An observable on this collection's value that can subscribe to its contents in reverse */
		protected class ReversibleFlattenedObserver extends FlattenedObserver {
			private final boolean isReversed;

			/**
			 * @param observer The oberver for this collection
			 * @param reverse Whether to subscribe to the elements in reverse
			 */
			protected ReversibleFlattenedObserver(Consumer<? super ObservableCollectionEvent<? extends E>> observer, boolean reverse) {
				super(observer);
				isReversed = reverse;
			}

			@Override
			protected CollectionSubscription subscribe(ObservableCollection<? extends E> coll,
				Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
				return isReversed ? ((ObservableReversibleCollection<? extends E>) coll).subscribeReverse(observer)
					: super.subscribe(coll, observer);
			}
		}
	}

	/**
	 * Implements {@link ObservableReversibleCollection#flatten(ObservableReversibleCollection)}
	 *
	 * @param <E> The type of the collection
	 */
	public static class FlattenedReversibleCollection<E> extends FlattenedOrderedCollection<E>
	implements ObservableReversibleCollection<E> {
		/** @param outer The collection of collections to flatten */
		protected FlattenedReversibleCollection(
			ObservableReversibleCollection<? extends ObservableReversibleCollection<? extends E>> outer) {
			super(outer);
		}

		@Override
		protected ObservableReversibleCollection<? extends ObservableReversibleCollection<? extends E>> getOuter() {
			return (ObservableReversibleCollection<? extends ObservableReversibleCollection<? extends E>>) super.getOuter();
		}

		@Override
		public boolean removeLast(Object o) {
			boolean [] removed=new boolean[1];
			ObservableReversibleSpliterator<? extends ObservableReversibleCollection<? extends E>> outerSplit=getOuter().spliterator(false);
			while(!removed[0] && outerSplit.tryReverseElement(el->{
				ObservableReversibleCollection<? extends E> inner=el.get();
				if(inner.equivalence().equals(equivalence()))
					removed[0]=inner.removeLast(o);
				else{
					ObservableReversibleSpliterator<? extends E> innerSplit=inner.spliterator(false);
					boolean [] found=new boolean[1];
					while(!found[0] && innerSplit.tryReverseElement(innerEl->{
						found[0]=equivalence().elementEquals(innerEl.get(), o);
						if(found[0] && innerEl.canRemove()==null){
							innerEl.remove();
							removed[0]=true;
						}
					})){
					}
				}
			})){
			}
			return removed[0];
		}

		@Override
		public ObservableReversibleSpliterator<E> spliterator() {
			return spliterator(true);
		}

		@Override
		public ObservableReversibleSpliterator<E> spliterator(boolean fromStart) {
			return flattenReversible(getOuter().spliterator(fromStart), coll -> coll.spliterator(fromStart));
		}

		/**
		 * @param <C> The sub-type of reversible collection held by the outer collection
		 * @param outer The outer spliterator
		 * @param innerSplit The function to produce spliterators for the inner collections
		 * @return The flattened spliterator
		 */
		protected <C extends ObservableReversibleCollection<? extends E>> ObservableReversibleSpliterator<E> flattenReversible(
			ObservableReversibleSpliterator<? extends C> outer,
			Function<? super C, ? extends ObservableReversibleSpliterator<? extends E>> innerSplit) {
			return new ReversibleFlattenedSpliterator<>(outer, innerSplit);
		}

		/**
		 * A spliterator for the flattened reversible collection
		 *
		 * @param <C> The sub-type of reversible collection held by the outer collection
		 */
		protected class ReversibleFlattenedSpliterator<C extends ObservableReversibleCollection<? extends E>>
		extends FlattenedSpliterator<C> implements ObservableReversibleSpliterator<E> {
			/**
			 * @param outerSpliterator A spliterator from the outer collection
			 * @param innerSplit The function to produce spliterators for the inner collections
			 */
			protected ReversibleFlattenedSpliterator(ObservableReversibleSpliterator<? extends C> outerSpliterator,
				Function<? super C, ? extends ObservableReversibleSpliterator<? extends E>> innerSplit) {
				super(outerSpliterator, innerSplit);
			}

			@Override
			protected ObservableReversibleSpliterator<? extends C> getOuterSpliterator() {
				return (ObservableReversibleSpliterator<? extends C>) super.getOuterSpliterator();
			}

			@Override
			protected ObservableReversibleSpliterator<? extends E> innerSplit(C coll) {
				return (ObservableReversibleSpliterator<? extends E>) super.innerSplit(coll);
			}

			@Override
			protected ObservableReversibleSpliterator<E> wrapInnerSplit(ObservableElementSpliterator<? extends E> innerSplit) {
				return new WrappingReversibleObservableSpliterator<>((ObservableReversibleSpliterator<? extends E>) innerSplit, getType(),
					getElementMap());
			}

			@Override
			protected ObservableReversibleSpliterator<E> getInnerator() {
				return (ObservableReversibleSpliterator<E>) super.getInnerator();
			}

			@Override
			public boolean tryReverseObservableElement(Consumer<? super ObservableCollectionElement<E>> action) {
				boolean[] found = new boolean[1];
				while (!found[0]) {
					if (getInnerator() == null && !getOuterSpliterator().tryReverseObservableElement(el -> {
						setOuterElement(el);
					}))
						return false;
					found[0] = getInnerator().tryReverseObservableElement(action);
				}
				return found[0];
			}

			@Override
			public ObservableReversibleSpliterator<E> trySplit() {
				return (ObservableReversibleSpliterator<E>) super.trySplit();
			}
		}

		@Override
		public CollectionSubscription subscribeReverse(Consumer<? super OrderedCollectionEvent<? extends E>> observer) {
			ReversibleOuterObserver outerObs = new ReversibleOuterObserver(observer, true);
			CollectionSubscription collSub;
			try (Transaction t = getOuter().lock(false, null)) {
				collSub = getOuter().subscribeReverse(outerObs);
				outerObs.setInitialized();
			}
			return removeAll -> {
				try (Transaction t = getOuter().lock(false, null)) {
					if (!removeAll)
						outerObs.done();
					collSub.unsubscribe(removeAll);
				}
			};
		}

		/** An observer to the outer collection that creates {@link ReversibleAddObserver}s for the outer collection's elements */
		protected class ReversibleOuterObserver extends OrderedOuterObserver {
			private final boolean isReversed;

			/**
			 * @param observer The observer for this collection
			 * @param reversed Whether to subscribe to the inner collections in reverse
			 */
			protected ReversibleOuterObserver(Consumer<? super OrderedCollectionEvent<? extends E>> observer, boolean reversed) {
				super(observer);
				isReversed = reversed;
			}

			@Override
			protected FlattenedObservableCollection<E>.AddObserver createElementObserver(ElementId elementId,
				Consumer<? super ObservableCollectionEvent<? extends E>> observer, Object metadata) {
				return new ReversibleAddObserver(elementId, observer, (DefaultTreeSet<ElementId>) metadata, isReversed);
			}

			@Override
			protected void done() {
				Collection<AddObserver> toKill = isReversed ? getElements().descendingMap().values() : getElements().values();
				for (AddObserver addObs : toKill)
					addObs.remove(false);
				getElements().clear();
			}
		}

		/** An add observer that may subscribe to the inner collection in reverse */
		protected class ReversibleAddObserver extends OrderedAddObserver {
			private final boolean isReversed;

			/**
			 * @param elementId The ID of the element to observe
			 * @param observer The subscriber
			 * @param presentIds The set of element IDs that are currently present in the collection
			 * @param reversed Whether this observer will subscribe to the inner collection in reverse
			 */
			protected ReversibleAddObserver(ElementId elementId, Consumer<? super ObservableCollectionEvent<? extends E>> observer,
				DefaultTreeSet<ElementId> presentIds, boolean reversed) {
				super(elementId, observer, presentIds);
				isReversed = reversed;
			}

			@Override
			protected CollectionSubscription subscribe(ObservableCollection<? extends E> collection) {
				return isReversed ? ((ObservableReversibleCollection<? extends E>) collection).subscribeReverse(this)
					: super.subscribe(collection);
			}
		}
	}
}
