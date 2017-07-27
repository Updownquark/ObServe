package org.observe.collect;

import java.util.Collection;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.Subscription;
import org.observe.collect.ObservableCollection.UniqueDataFlow;
import org.observe.collect.ObservableCollection.UniqueMappedCollectionBuilder;
import org.observe.collect.ObservableCollection.UniqueModFilterBuilder;
import org.observe.collect.ObservableCollectionDataFlowImpl.BaseCollectionDataFlow;
import org.observe.collect.ObservableCollectionDataFlowImpl.CollectionManager;
import org.observe.collect.ObservableCollectionDataFlowImpl.UniqueDataFlowWrapper;
import org.observe.collect.ObservableCollectionImpl.DerivedCollection;
import org.observe.collect.ObservableCollectionImpl.DerivedLWCollection;
import org.observe.collect.ObservableCollectionImpl.FlattenedValueCollection;
import org.observe.collect.ObservableCollectionImpl.ReversedObservableCollection;
import org.qommons.Transaction;
import org.qommons.collect.MutableElementHandle;

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
	public static class CombinedSet<E, X> implements ObservableSet<E> {
		// Note: Several (E) v casts below are technically incorrect, as the values may not be of type E
		// But they are runtime-safe because of the isElement tests
		private final ObservableSet<E> theLeft;
		private final ObservableCollection<X> theRight;
		private final boolean isUnion;
		private final boolean sameEquivalence;
		private final Subscription theSubscription;

		/**
		 * @param left The left set whose elements to reflect
		 * @param right The right set to filter this set's elements by
		 */
		protected CombinedSet(ObservableSet<E> left, ObservableCollection<X> right, boolean union, Observable<?> until) {
			theLeft = left;
			theRight = right;
			isUnion = union;
			sameEquivalence = left.equivalence().equals(right.equivalence());
			Subscription[] takeSub = new Subscription[1];
		}

		/** @return The left set whose elements this set reflects */
		protected ObservableSet<E> getLeft() {
			return theLeft;
		}

		/** @return The right collection that this set's elements are filtered by */
		protected ObservableCollection<X> getRight() {
			return theRight;
		}

		protected boolean isUnion() {
			return isUnion;
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
			return theLeft.isLockSupported() || theRight.isLockSupported();
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
				return MutableElementHandle.StdMsg.BAD_TYPE;
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
		public CollectionSubscription subscribe(Consumer<? super ObservableCollectionEvent<? extends E>> observer, boolean forward) {
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
			return new UniqueDataFlowWrapper<>(getSource(), super.filter(filter));
		}

		@Override
		public UniqueDataFlow<E, E, E> filterStatic(Function<? super E, String> filter) {
			return new UniqueDataFlowWrapper<>(getSource(), super.filterStatic(filter));
		}

		@Override
		public <X> UniqueMappedCollectionBuilder<E, E, X> mapEquivalent(TypeToken<X> target) {
			return new UniqueMappedCollectionBuilder<>(getSource(), this, target);
		}

		@Override
		public UniqueDataFlow<E, E, E> refresh(Observable<?> refresh) {
			return new UniqueDataFlowWrapper<>(getSource(), super.refresh(refresh));
		}

		@Override
		public UniqueDataFlow<E, E, E> refreshEach(Function<? super E, ? extends Observable<?>> refresh) {
			return new UniqueDataFlowWrapper<>(getSource(), super.refreshEach(refresh));
		}

		@Override
		public UniqueModFilterBuilder<E, E> filterModification() {
			return new UniqueModFilterBuilder<>(getSource(), this);
		}

		@Override
		public ObservableSet<E> collectLW() {
			return getSource();
		}

		@Override
		public ObservableSet<E> collect() {
			return (ObservableSet<E>) super.collect();
		}

		@Override
		public ObservableSet<E> collect(Observable<?> until) {
			if (until == Observable.empty)
				return getSource();
			else
				return new DerivedSet<>(getSource(), manageCollection(), until);
		}
	}

	public static class DerivedLWSet<E, T> extends DerivedLWCollection<E, T> implements ObservableSet<T> {
		/**
		 * @param source The source set. The unique operation is not light-weight, so the input must be a set
		 * @param flow The data flow used to create the modified collection
		 */
		public DerivedLWSet(ObservableSet<E> source, CollectionManager<E, ?, T> flow) {
			super(source, flow);
		}

		@Override
		protected ObservableSet<E> getSource() {
			return (ObservableSet<E>) super.getSource();
		}
	}

	public static class DerivedSet<E, T> extends DerivedCollection<E, T> implements ObservableSet<T> {
		public DerivedSet(ObservableCollection<E> source, CollectionManager<E, ?, T> flow, Observable<?> until) {
			super(source, flow, until);
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
