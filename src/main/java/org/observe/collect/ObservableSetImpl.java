package org.observe.collect;

import java.util.Collection;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.collect.ObservableCollection.UniqueDataFlow;
import org.observe.collect.ObservableCollection.UniqueMappedCollectionBuilder;
import org.observe.collect.ObservableCollectionDataFlowImpl.AbstractDataFlow;
import org.observe.collect.ObservableCollectionDataFlowImpl.BaseCollectionDataFlow;
import org.observe.collect.ObservableCollectionDataFlowImpl.CollectionManager;
import org.observe.collect.ObservableCollectionDataFlowImpl.UniqueDataFlowWrapper;
import org.observe.collect.ObservableCollectionDataFlowImpl.UniqueElementFinder;
import org.observe.collect.ObservableCollectionImpl.ConstantObservableCollection;
import org.observe.collect.ObservableCollectionImpl.DerivedCollection;
import org.observe.collect.ObservableCollectionImpl.FlattenedValueCollection;
import org.observe.collect.ObservableCollectionImpl.ReversedObservableCollection;
import org.qommons.Transaction;

import com.google.common.reflect.TypeToken;

/** Holds default implementation methods and classes for {@link ObservableSet} methods */
public class ObservableSetImpl {
	private ObservableSetImpl() {}

	public static class ReversedSet<E> extends ReversedObservableCollection<E> implements ObservableSet<E> {
		public ReversedSet(ObservableSet<E> wrapped) {
			super(wrapped);
		}

		@Override
		protected ObservableSet<E> getWrapped() {
			return (ObservableSet<E>) super.getWrapped();
		}

		@Override
		public ObservableSet<E> reverse() {
			return getWrapped();
		}
	}

	/**
	 * Implements {@link ObservableSet#intersect(ObservableCollection)}
	 *
	 * @param <E> The type of the elements in the set
	 * @param <X> The type of elements in the filtering collection
	 */
	public static class IntersectedSet<E, X> implements ObservableSet<E> {
		// Note: Several (E) v casts below are technically incorrect, as the values may not be of type E
		// But they are runtime-safe because of the isElement tests
		private final ObservableSet<E> theLeft;
		private final ObservableCollection<X> theRight;
		private final boolean sameEquivalence;

		/**
		 * @param left The left set whose elements to reflect
		 * @param right The right set to filter this set's elements by
		 */
		protected IntersectedSet(ObservableSet<E> left, ObservableCollection<X> right) {
			theLeft = left;
			theRight = right;
			sameEquivalence = left.equivalence().equals(right.equivalence());
		}

		/** @return The left set whose elements this set reflects */
		protected ObservableSet<E> getLeft() {
			return theLeft;
		}

		/** @return The right collection that this set's elements are filtered by */
		protected ObservableCollection<X> getRight() {
			return theRight;
		}

		@Override
		public TypeToken<E> getType() {
			return theLeft.getType();
		}

		@Override
		public Equivalence<? super E> equivalence() {
			return theLeft.equivalence();
		}

		@Override
		public boolean isLockSupported() {
			return theLeft.isLockSupported() || theRight.isLockSupported(); // Report whether locking will have any effect
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			Transaction lt = theLeft.lock(write, cause);
			Transaction rt = theRight.lock(write, cause);
			return () -> {
				lt.close();
				rt.close();
			};
		}

		/** @return The right collection's elements, in a set using the left set's equivalence */
		protected Set<? super E> getRightSet() {
			return ObservableCollectionImpl.toSet(theLeft.equivalence(), theRight);
		}

		@Override
		public int size() {
			if (theLeft.isEmpty() || theRight.isEmpty())
				return 0;
			if (sameEquivalence && theRight instanceof ObservableSet) {
				try (Transaction lt = theLeft.lock(false, null); Transaction rt = theRight.lock(false, null)) {
					return (int) theLeft.stream().filter(theRight::contains).count();
				}
			} else {
				Set<? super E> rightSet = getRightSet();
				try (Transaction t = theLeft.lock(false, null)) {
					return (int) theLeft.stream().filter(rightSet::contains).count();
				}
			}
		}

		@Override
		public boolean isEmpty() {
			if (theLeft.isEmpty() || theRight.isEmpty())
				return true;
			try (Transaction t = theRight.lock(false, null)) {
				return !theLeft.containsAny(theRight);
			}
		}

		@Override
		public ObservableElementSpliterator<E> spliterator() {
			if (theLeft.isEmpty() || theRight.isEmpty())
				return ObservableElementSpliterator.empty(theLeft.getType());
			// Create the right set for filtering when the method is called
			Set<? super E> rightSet = getRightSet();
			ObservableElementSpliterator<E> leftSplit = theLeft.spliterator();
			return new WrappingObservableSpliterator<>(leftSplit, leftSplit.getType(), () -> {
				ObservableCollectionElement<E>[] container = new ObservableCollectionElement[1];
				WrappingObservableElement<E, E> wrappingEl = new WrappingObservableElement<E, E>(getType(), container) {
					@Override
					public E get() {
						return getWrapped().get();
					}

					@Override
					public <V extends E> String isAcceptable(V value) {
						return ((ObservableCollectionElement<E>) getWrapped()).isAcceptable(value);
					}

					@Override
					public <V extends E> E set(V value, Object cause) throws IllegalArgumentException {
						return ((ObservableCollectionElement<E>) getWrapped()).set(value, cause);
					}
				};
				return el -> {
					if (!rightSet.contains(el.get()))
						return null;
					container[0] = (ObservableCollectionElement<E>) el;
					return wrappingEl;
				};
			});
		}

		@Override
		public boolean contains(Object o) {
			if (!theLeft.contains(o))
				return false;
			if (sameEquivalence)
				return theRight.contains(o);
			else {
				try (Transaction t = theRight.lock(false, null)) {
					Equivalence<? super E> equiv = theLeft.equivalence();
					return theRight.stream().anyMatch(v -> equiv.isElement(v) && equiv.elementEquals((E) v, o));
				}
			}
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			if (c.isEmpty())
				return true;
			if (theRight.isEmpty())
				return false;
			if (!theLeft.containsAll(c))
				return false;
			if (sameEquivalence)
				return theRight.containsAll(c);
			else
				return getRightSet().containsAll(c);
		}

		@Override
		public boolean containsAny(Collection<?> c) {
			if (c.isEmpty())
				return false;
			if (theLeft.isEmpty() || theRight.isEmpty())
				return false;
			if (sameEquivalence && theRight instanceof ObservableSet) {
				try (Transaction lt = theLeft.lock(false, null); Transaction rt = theRight.lock(false, null)) {
					for (Object o : c)
						if (theLeft.contains(o) && theRight.contains(o))
							return true;
				}
			} else {
				Set<? super E> rightSet = getRightSet();
				try (Transaction t = theLeft.lock(false, null)) {
					for (Object o : c)
						if (theLeft.contains(o) && rightSet.contains(o))
							return true;
				}
			}
			return false;
		}

		@Override
		public String canAdd(E value) {
			String msg = theLeft.canAdd(value);
			if (msg != null)
				return msg;
			if (value != null && !theRight.getType().getRawType().isInstance(value))
				return ObservableCollection.StdMsg.BAD_TYPE;
			msg = theRight.canAdd((X) value);
			if (msg != null)
				return msg;
			return null;
		}

		@Override
		public boolean add(E e) {
			try (Transaction lt = theLeft.lock(true, null); Transaction rt = theRight.lock(true, null)) {
				boolean addedLeft = false;
				if (!theLeft.contains(e)) {
					if (!theLeft.add(e))
						return false;
					addedLeft = true;
				}
				if (!theRight.contains(e)) {
					try {
						if (!theRight.add((X) e)) {
							if (addedLeft)
								theLeft.remove(e);
							return false;
						}
					} catch (RuntimeException ex) {
						if (addedLeft)
							theLeft.remove(e);
						throw ex;
					}
				}
				return true;
			}
		}

		@Override
		public boolean addAll(Collection<? extends E> c) {
			boolean addedAny = false;
			try (Transaction lt = theLeft.lock(true, null); Transaction rt = theRight.lock(true, null)) {
				for (E o : c) {
					boolean addedLeft = false;
					if (!theLeft.contains(o)) {
						if (!theLeft.add(o))
							continue;
						addedLeft = true;
					}
					boolean addedRight = false;
					if (!theRight.contains(o)) {
						addedRight = true;
						if (!theRight.add((X) o)) {
							if (addedLeft)
								theLeft.remove(o);
							continue;
						}
					}
					if (addedLeft || addedRight)
						addedAny = true;
				}
			}
			return addedAny;
		}

		@Override
		public String canRemove(Object value) {
			String msg = theLeft.canRemove(value);
			if (msg != null)
				return msg;
			msg = theRight.canRemove(value);
			if (msg != null)
				return msg;
			return null;
		}

		@Override
		public boolean remove(Object o) {
			return theLeft.remove(o);
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			return theLeft.removeAll(c);
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			return theLeft.retainAll(c);
		}

		@Override
		public void clear() {
			if (theLeft.isEmpty() || theRight.isEmpty())
				return;
			Set<? super E> rightSet = getRightSet();
			theLeft.removeAll(rightSet);
		}

		@Override
		public CollectionSubscription subscribe(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
			// TODO Auto-generated method stub
		}
	}

	public static class UniqueBaseFlow<E> extends BaseCollectionDataFlow<E> implements UniqueDataFlow<E, E, E> {
		protected UniqueBaseFlow(ObservableSet<E> source) {
			super(source);
		}

		@Override
		protected ObservableSet<E> getSource() {
			return (ObservableSet<E>) super.getSource();
		}

		@Override
		public UniqueDataFlow<E, E, E> filter(Function<? super E, String> filter) {
			return (UniqueDataFlow<E, E, E>) super.filter(filter);
		}

		@Override
		public UniqueDataFlow<E, E, E> filter(Function<? super E, String> filter, boolean filterNulls) {
			return new UniqueDataFlowWrapper<>((AbstractDataFlow<E, ?, E>) super.filter(filter, filterNulls));
		}

		@Override
		public UniqueDataFlow<E, E, E> filterStatic(Function<? super E, String> filter) {
			return (UniqueDataFlow<E, E, E>) super.filterStatic(filter);
		}

		@Override
		public UniqueDataFlow<E, E, E> filterStatic(Function<? super E, String> filter, boolean filterNulls) {
			return new UniqueDataFlowWrapper<>((AbstractDataFlow<E, ?, E>) super.filterStatic(filter, filterNulls));
		}

		@Override
		public <X> UniqueMappedCollectionBuilder<E, E, X> mapEquivalent(TypeToken<X> target) {
			return new UniqueMappedCollectionBuilder<>(this, target);
		}

		@Override
		public UniqueDataFlow<E, E, E> refresh(Observable<?> refresh) {
			return new UniqueDataFlowWrapper<>((AbstractDataFlow<E, ?, E>) super.refresh(refresh));
		}

		@Override
		public UniqueDataFlow<E, E, E> refreshEach(Function<? super E, ? extends Observable<?>> refresh) {
			return new UniqueDataFlowWrapper<>((AbstractDataFlow<E, ?, E>) super.refreshEach(refresh));
		}

		@Override
		public CollectionManager<E, ?, E> manageCollection() {
			// TODO Auto-generated method stub
			return super.manageCollection();
		}

		@Override
		public ObservableSet<E> collect(Observable<?> until) {
			if (until == Observable.empty)
				return getSource();
			else
				return new DerivedSet<>(getSource(), manageCollection(), until);
		}
	}

	public static class DerivedSet<E, T> extends DerivedCollection<E, T> implements ObservableSet<T> {
		private final UniqueElementFinder<T> theElementFinder;

		public DerivedSet(ObservableCollection<E> source, CollectionManager<E, ?, T> flow, UniqueElementFinder<T> elementFinder,
			Observable<?> until) {
			super(source, flow, until);
			theElementFinder = elementFinder;
		}

		protected UniqueElementFinder<T> getElementFinder() {
			return theElementFinder;
		}

		@Override
		public boolean forObservableElement(T value, Consumer<? super ObservableCollectionElement<? extends T>> onElement, boolean first) {
			ElementId id = getElementFinder().getId(value);
			if (id == null)
				return false;
			forWrappedElementAt(id, onElement);
			return true;
		}

		@Override
		public boolean forMutableElement(T value, Consumer<? super MutableObservableElement<? extends T>> onElement, boolean first) {
			ElementId id = getElementFinder().getId(value);
			if (id == null)
				return false;
			forWrappedMutableElementAt(id, onElement);
			return true;
		}
	}

	/**
	 * Implements {@link ObservableSet#constant(TypeToken, Equivalence, Collection)}
	 *
	 * @param <E> The type of elements in the set
	 */
	public static class ConstantObservableSet<E> extends ConstantObservableCollection<E> implements ObservableSet<E> {
		private final Equivalence<? super E> theEquivalence;

		/**
		 * @param type The type of the set
		 * @param equivalence The equivalence set for the set's uniqueness
		 * @param collection The values for the set
		 */
		public ConstantObservableSet(TypeToken<E> type, Equivalence<? super E> equivalence, Collection<E> collection) {
			super(type, ObservableCollectionImpl.toSet(equivalence, collection));
			theEquivalence = equivalence;
		}

		@Override
		public Equivalence<? super E> equivalence() {
			return theEquivalence;
		}
	}

	/**
	 * Implements {@link ObservableSet#flattenValue(ObservableValue)}
	 *
	 * @param <E> The type of elements in the set
	 */
	public static class FlattenedValueSet<E> extends FlattenedValueCollection<E> implements ObservableSet<E> {
		/** @param collectionObservable The value containing a set to flatten */
		protected FlattenedValueSet(ObservableValue<? extends ObservableSet<E>> collectionObservable) {
			super(collectionObservable);
		}

		@Override
		protected ObservableValue<? extends ObservableSet<E>> getWrapped() {
			return (ObservableValue<? extends ObservableSet<E>>) super.getWrapped();
		}
	}
}
