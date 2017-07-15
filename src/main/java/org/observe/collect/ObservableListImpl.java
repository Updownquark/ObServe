package org.observe.collect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.collect.ObservableCollection.CombinedValues;
import org.observe.collect.ObservableCollection.ElementSetter;
import org.observe.collect.ObservableCollectionDataFlowImpl.AbstractDataFlow;
import org.observe.collect.ObservableCollectionDataFlowImpl.BaseCollectionDataFlow;
import org.observe.collect.ObservableCollectionDataFlowImpl.CollectionManager;
import org.observe.collect.ObservableCollectionDataFlowImpl.CombinedCollectionDef;
import org.observe.collect.ObservableCollectionDataFlowImpl.ElementRefreshOp;
import org.observe.collect.ObservableCollectionDataFlowImpl.EquivalenceSwitchOp;
import org.observe.collect.ObservableCollectionDataFlowImpl.InitialElementsDataFlow;
import org.observe.collect.ObservableCollectionDataFlowImpl.MapOp;
import org.observe.collect.ObservableCollectionDataFlowImpl.RefreshOp;
import org.observe.collect.ObservableCollectionImpl.DerivedCollection;
import org.observe.collect.ObservableCollectionImpl.FlattenedObservableCollection;
import org.observe.collect.ObservableCollectionImpl.FlattenedValueCollection;
import org.observe.collect.ObservableCollectionImpl.ReversedObservableCollection;
import org.observe.collect.ObservableList.CombinedListBuilder2;
import org.observe.collect.ObservableList.ListDataFlow;
import org.observe.collect.ObservableList.ListModFilterBuilder;
import org.observe.collect.ObservableList.MappedListBuilder;
import org.qommons.Ternian;
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.collect.ElementSpliterator.ElementSpliteratorMap;
import org.qommons.collect.ImmutableIterator;
import org.qommons.collect.ReversibleElementSpliterator;
import org.qommons.collect.ReversibleList;
import org.qommons.collect.ReversibleSpliterator;
import org.qommons.collect.TransactableList;

import com.google.common.reflect.TypeToken;

/** Holds default implementation methods and classes for {@link ObservableList} */
public class ObservableListImpl {
	private ObservableListImpl() {}

	/**
	 * A simple {@link ObservableList#subList(int, int)} implementation for derived lists that are one-to-one mappings of their source lists
	 *
	 * @param <E> The type of values in the source list
	 * @param <T> The type of values in this list
	 */
	public static class SimpleMappedSubList<E, T> implements ReversibleList<T>, TransactableList<T> {
		private final ReversibleList<? extends E> theWrapped;
		private final TypeToken<T> theType;
		private final Equivalence<? super T> theEquivalence;
		private final Transactable theTransactable;
		private final ElementSpliteratorMap<E, T> theMap;

		/**
		 * @param wrapped The source sub-list
		 * @param type The type of the derived list
		 * @param equivalence The equivalence set of the derived list
		 * @param transactable The transaction to use for the derived list
		 * @param map
		 */
		public SimpleMappedSubList(ReversibleList<? extends E> wrapped, TypeToken<T> type, Equivalence<? super T> equivalence,
			Transactable transactable, ElementSpliteratorMap<E, T> map) {
			theWrapped = wrapped;
			theType = type;
			theEquivalence = equivalence;
			theTransactable = transactable;
			theMap = map;
		}

		/** @return The type of the derived collection */
		public TypeToken<T> getType() {
			return theType;
		}

		/** @return The equivalence of the derived collection */
		public Equivalence<? super T> equivalence() {
			return theEquivalence;
		}

		@Override
		public boolean isLockSupported() {
			return theTransactable.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theTransactable.lock(write, cause);
		}

		@Override
		public ImmutableIterator<T> iterator() {
			return ReversibleList.super.iterator();
		}

		@Override
		public int size() {
			return theWrapped.size();
		}

		@Override
		public boolean isEmpty() {
			return theWrapped.isEmpty();
		}

		@Override
		public boolean contains(Object value) {
			try (Transaction t = theTransactable.lock(false, null)) {
				Spliterator<T> iter = spliterator();
				boolean[] found = new boolean[1];
				while (!found[0] && iter.tryAdvance(v -> {
					if (equivalence().elementEquals(v, value))
						found[0] = true;
				})) {
				}
				return found[0];
			}
		}

		@Override
		public boolean containsAny(Collection<?> c) {
			if (c.isEmpty())
				return false;
			Set<T> cSet = ObservableCollectionImpl.toSet(equivalence(), c);
			if (cSet.isEmpty())
				return false;
			try (Transaction t = theTransactable.lock(false, null)) {
				Spliterator<T> iter = spliterator();
				boolean[] found = new boolean[1];
				while (iter.tryAdvance(next -> {
					found[0] = cSet.contains(next);
				}) && !found[0]) {
				}
				return found[0];
			}
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			if (c.isEmpty())
				return true;
			Set<T> cSet = ObservableCollectionImpl.toSet(equivalence(), c);
			if (cSet.isEmpty())
				return false;
			try (Transaction t = theTransactable.lock(false, null)) {
				Spliterator<T> iter = spliterator();
				while (iter.tryAdvance(next -> {
					cSet.remove(next);
				}) && !cSet.isEmpty()) {
				}
				return cSet.isEmpty();
			}
		}

		@Override
		public T get(int index) {
			return theMap.map(theWrapped.get(index));
		}

		@Override
		public int indexOf(Object value) {
			if (!equivalence().isElement(value))
				return -1;
			try (Transaction t = theTransactable.lock(false, null)) {
				int index = 0;
				for (T v : this) {
					if (equivalence().elementEquals(v, value))
						return index;
					index++;
				}
				return -1;
			}
		}

		@Override
		public int lastIndexOf(Object value) {
			if (!equivalence().isElement(value))
				return -1;
			try (Transaction t = theTransactable.lock(false, null)) {
				int result = -1;
				int index = 0;
				for (T v : this) {
					if (equivalence().elementEquals(v, value))
						result = index;
					index++;
				}
				return result;
			}
		}

		@Override
		public T[] toArray() {
			T[] array;
			try (Transaction t = theTransactable.lock(false, null)) {
				array = (T[]) java.lang.reflect.Array.newInstance(getType().wrap().getRawType(), size());
				int[] i = new int[1];
				spliterator().forEachRemaining(v -> array[i[0]++] = v);
			}
			return array;
		}

		@Override
		public <T2> T2[] toArray(T2[] a) {
			ArrayList<T> ret;
			try (Transaction t = theTransactable.lock(false, null)) {
				ret = new ArrayList<>();
				spliterator().forEachRemaining(v -> ret.add(v));
			}
			return ret.toArray(a);
		}

		@Override
		public ReversibleSpliterator<T> spliterator(boolean fromStart) {
			return new ReversibleElementSpliterator.MappedReversibleSpliterator<E, T>(
				(ReversibleSpliterator<E>) theWrapped.spliterator(fromStart), theMap);
		}

		@Override
		public ReversibleSpliterator<T> spliterator(int index) {
			return new ReversibleElementSpliterator.MappedReversibleSpliterator<E, T>(
				(ReversibleSpliterator<E>) theWrapped.spliterator(index), theMap);
		}

		@Override
		public ReversibleElementSpliterator<T> mutableSpliterator(boolean fromStart) {
			return ((ReversibleElementSpliterator<E>) theWrapped.mutableSpliterator(fromStart)).map(theMap);
		}

		@Override
		public ReversibleElementSpliterator<T> mutableSpliterator(int index) {
			return ((ReversibleElementSpliterator<E>) theWrapped.mutableSpliterator(index)).map(theMap);
		}

		@Override
		public boolean add(T e) {
			return ((ReversibleList<E>) theWrapped).add(attemptedAdd(e));
		}

		@Override
		public boolean addAll(Collection<? extends T> c) {
			return ((ReversibleList<E>) theWrapped).addAll(c.stream().map(this::attemptedAdd).collect(Collectors.toList()));
		}

		@Override
		public void add(int index, T element) {
			((ReversibleList<E>) theWrapped).add(index, attemptedAdd(element));
		}

		@Override
		public boolean addAll(int index, Collection<? extends T> c) {
			return ((ReversibleList<E>) theWrapped).addAll(index, c.stream().map(this::attemptedAdd).collect(Collectors.toList()));
		}

		@Override
		public boolean removeLast(Object value) {
			return theWrapped.find(v -> equivalence().elementEquals(wrap(v), value), el -> {
				attemptedRemove(el.get());
				el.remove();
			}, false);
		}

		@Override
		public void removeRange(int fromIndex, int toIndex) {
			if (!isRemoveRestricted())
				theWrapped.removeRange(fromIndex, toIndex);
			else {
				try (Transaction t = lock(true, null)) {
					int size = size();
					for (int i = fromIndex; i < toIndex && i < size; i++)
						attemptedRemove(theWrapped.get(i));
					theWrapped.removeRange(fromIndex, toIndex);
				}
			}
		}

		@Override
		public boolean remove(Object o) {
			return theWrapped.find(v -> equivalence().elementEquals(wrap(v), o), el -> {
				attemptedRemove(el.get());
				el.remove();
			});
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			if (c.isEmpty())
				return true;
			Set<T> cSet = ObservableCollectionImpl.toSet(equivalence(), c);
			if (cSet.isEmpty())
				return false;
			return theWrapped.removeIf(v -> {
				boolean remove = cSet.contains(wrap(v));
				if (remove)
					attemptedRemove(v);
				return remove;
			});
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			if (c.isEmpty())
				return false;
			Set<T> cSet = ObservableCollectionImpl.toSet(equivalence(), c);
			if (cSet.isEmpty())
				return false;
			return theWrapped.removeIf(v -> {
				boolean remove = !cSet.contains(wrap(v));
				if (remove)
					attemptedRemove(v);
				return remove;
			});
		}

		@Override
		public void clear() {
			if (!isRemoveRestricted())
				theWrapped.clear();
			else {
				try (Transaction t = lock(true, null)) {
					for (E v : theWrapped)
						attemptedRemove(v);
					theWrapped.clear();
				}
			}
		}

		@Override
		public T remove(int index) {
			if (!isRemoveRestricted())
				return wrap(theWrapped.remove(index));
			else {
				try (Transaction t = lock(true, null)) {
					E value = theWrapped.get(index);
					attemptedRemove(value);
					return wrap(theWrapped.remove(index));
				}
			}
		}

		@Override
		public T set(int index, T element) {
			if (canModifyElementValue())
				return attemptSet(theWrapped.get(index), element, null);
			else if (isRemoveRestricted())
				return wrap(((ReversibleList<E>) theWrapped).set(index, attemptedAdd(element)));
			else {
				try (Transaction t = lock(true, null)) {
					E value = theWrapped.get(index);
					attemptedRemove(value);
					return wrap(((ReversibleList<E>) theWrapped).set(index, attemptedAdd(element)));
				}
			}
		}

		@Override
		public ListIterator<T> listIterator(int index) {
			return new SimpleMappedListIterator<E, T>(theWrapped.listIterator(index)) {
				@Override
				protected T wrap(E e) {
					return SimpleMappedSubList.this.wrap(e);
				}

				@Override
				protected void attemptRemove(E value) {
					SimpleMappedSubList.this.attemptedRemove(value);
				}

				@Override
				protected E attemptedAdd(T value) {
					return SimpleMappedSubList.this.attemptedAdd(value);
				}

				@Override
				protected boolean isElementSettable() {
					return SimpleMappedSubList.this.canModifyElementValue();
				}

				@Override
				protected T attemptSet(E container, T value, Object cause) {
					return SimpleMappedSubList.this.attemptSet(container, value, cause);
				}
			};
		}

		@Override
		public ReversibleList<T> subList(int fromIndex, int toIndex) {
			SimpleMappedSubList<E, T> outer = this;
			return new SimpleMappedSubList<E, T>(theWrapped.subList(fromIndex, toIndex), theType, theEquivalence, theTransactable) {
				@Override
				protected T wrap(E wrap) {
					return outer.wrap(wrap);
				}

				@Override
				protected boolean isRemoveRestricted() {
					return outer.isRemoveRestricted();
				}

				@Override
				protected String checkRemove(E value) {
					return outer.checkRemove(value);
				}

				@Override
				protected E attemptedRemove(E value) {
					return outer.attemptedRemove(value);
				}

				@Override
				protected String checkAdd(T value) {
					return outer.checkAdd(value);
				}

				@Override
				protected E attemptedAdd(T value) {
					return outer.attemptedAdd(value);
				}

				@Override
				protected boolean isElementSettable() {
					return outer.canModifyElementValue();
				}

				@Override
				protected String checkSet(E container, T value) {
					return outer.checkSet(container, value);
				}

				@Override
				protected T attemptSet(E container, T value, Object cause) {
					return outer.attemptSet(container, value, cause);
				}
			};
		}

		@Override
		public int hashCode() {
			try (Transaction t = theTransactable.lock(false, null)) {
				int hashCode = 1;
				for (Object e : this)
					hashCode += e.hashCode();
				return hashCode;
			}
		}

		@Override
		public boolean equals(Object o) {
			if (!(o instanceof Collection))
				return false;
			Collection<?> c = (Collection<?>) o;

			try (Transaction t1 = theTransactable.lock(false, null); Transaction t2 = Transactable.lock(c, false, null)) {
				Iterator<T> e1 = iterator();
				Iterator<?> e2 = c.iterator();
				while (e1.hasNext() && e2.hasNext()) {
					T o1 = e1.next();
					Object o2 = e2.next();
					if (!equivalence().elementEquals(o1, o2))
						return false;
				}
				return !(e1.hasNext() || e2.hasNext());
			}
		}

		@Override
		public String toString() {
			StringBuilder ret = new StringBuilder("(");
			boolean first = true;
			try (Transaction t = theTransactable.lock(false, null)) {
				for (Object value : this) {
					if (!first) {
						ret.append(", ");
					} else
						first = false;
					ret.append(value);
				}
			}
			ret.append(')');
			return ret.toString();
		}
	}

	/**
	 * Implements {@link ObservableList#reverse()}
	 *
	 * @param <E> The type of elements in the collection
	 */
	public static class ReversedList<E> extends ReversedObservableCollection<E> implements ObservableList<E> {
		/** @param list The source list */
		protected ReversedList(ObservableList<E> list) {
			super(list);
		}

		@Override
		protected ObservableList<E> getWrapped() {
			return (ObservableList<E>) super.getWrapped();
		}

		@Override
		public ObservableList<E> reverse() {
			return getWrapped();
		}

		@Override
		public <T> T ofElementAt(int index, Function<? super ObservableCollectionElement<? extends E>, T> onElement) {
			try (Transaction t = lock(false, null)) {
				return getWrapped().ofElementAt(reflect(index, false), onElement);
			}
		}

		@Override
		public <T> T ofMutableElementAt(int index, Function<? super MutableObservableElement<? extends E>, T> onElement) {
			try (Transaction t = lock(true, null)) {
				return getWrapped().ofMutableElementAt(reflect(index, false), onElement);
			}
		}

		@Override
		public ObservableElementSpliterator<E> spliterator(int index) {
			try (Transaction t = lock(false, null)) {
				return getWrapped().spliterator(reflect(index, true)).reverse();
			}
		}

		@Override
		public MutableObservableSpliterator<E> mutableSpliterator(int index) {
			try (Transaction t = lock(false, null)) {
				return getWrapped().mutableSpliterator(reflect(index, true)).reverse();
			}
		}

		@Override
		public boolean remove(Object o) {
			return getWrapped().removeLast(o);
		}

		@Override
		public boolean removeLast(Object o) {
			return getWrapped().remove(o);
		}

		@Override
		public boolean addAll(Collection<? extends E> c) {
			return getWrapped().addAll(0, reverse(c));
		}

		private static <T> Collection<T> reverse(Collection<T> coll) {
			if (coll instanceof ReversedCollection)
				return ((ReversedCollection<T>) coll).reverse();
			List<T> copy = new ArrayList<>(coll);
			java.util.Collections.reverse(copy);
			return copy;
		}

		private int reflect(int index, boolean terminalInclusive) {
			int size = getWrapped().size();
			if (index < 0)
				throw new IndexOutOfBoundsException("" + index);
			if (index > size || (!terminalInclusive && index == size))
				throw new IndexOutOfBoundsException(index + " of " + size);
			int reflected = size - index;
			if (!terminalInclusive)
				reflected--;
			return reflected;
		}

		@Override
		public boolean addAll(int index, Collection<? extends E> c) {
			try (Transaction t = lock(true, null)) {
				return getWrapped().addAll(reflect(index, true), reverse(c));
			}
		}

		@Override
		public E get(int index) {
			try (Transaction t = lock(false, null)) {
				return getWrapped().get(reflect(index, false));
			}
		}

		@Override
		public E set(int index, E element) {
			try (Transaction t = lock(true, null)) {
				return getWrapped().set(reflect(index, false), element);
			}
		}

		@Override
		public void add(int index, E element) {
			try (Transaction t = lock(true, null)) {
				getWrapped().add(reflect(index, true), element);
			}
		}

		@Override
		public E remove(int index) {
			try (Transaction t = lock(true, null)) {
				return getWrapped().remove(reflect(index, false));
			}
		}

		@Override
		public void removeRange(int fromIndex, int toIndex) {
			try (Transaction t = lock(false, null)) {
				getWrapped().removeRange(reflect(toIndex, true), reflect(fromIndex, true));
			}
		}

		@Override
		public int indexOf(Object o) {
			try (Transaction t = lock(false, null)) {
				return reflect(getWrapped().lastIndexOf(o), false);
			}
		}

		@Override
		public int lastIndexOf(Object o) {
			try (Transaction t = lock(false, null)) {
				return reflect(getWrapped().indexOf(o), false);
			}
		}

		@Override
		public ListIterator<E> listIterator() {
			return listIterator(0);
		}

		@Override
		public ListIterator<E> listIterator(int index) {
			if (index < 0)
				throw new IndexOutOfBoundsException("" + index);
			try (Transaction t = getWrapped().lock(false, null)) {
				return new ReversedListIterator<>(getWrapped().listIterator(reflect(index, true)), () -> getWrapped().size());
			}
		}

		@Override
		public ReversibleList<E> subList(int fromIndex, int toIndex) {
			if (fromIndex < 0)
				throw new IndexOutOfBoundsException("" + fromIndex);
			if (fromIndex > toIndex)
				throw new IndexOutOfBoundsException(fromIndex + ">" + toIndex);
			try (Transaction t = getWrapped().lock(false, null)) {
				return getWrapped().subList(reflect(toIndex, true), reflect(fromIndex, true)).reverse();
			}
		}
	}

	public static class BaseListDataFlow<E> extends BaseCollectionDataFlow<E> implements ListDataFlow<E, E, E> {
		protected BaseListDataFlow(ObservableList<E> source) {
			super(source);
		}

		@Override
		protected ObservableList<E> getSource() {
			return (ObservableList<E>) super.getSource();
		}

		@Override
		public ListDataFlow<E, E, E> withEquivalence(Equivalence<? super E> equivalence) {
			return new EquivalenceSwitchListOp<>(getSource(), this, equivalence);
		}

		@Override
		public ListDataFlow<E, E, E> refresh(Observable<?> refresh) {
			return new RefreshListOp<>(getSource(), this, refresh);
		}

		@Override
		public ListDataFlow<E, E, E> refreshEach(Function<? super E, ? extends Observable<?>> refresh) {
			return new ElementRefreshListOp<>(getSource(), this, refresh);
		}

		@Override
		public <X> MappedListBuilder<E, E, X> map(TypeToken<X> target) {
			return new MappedListBuilder<>(getSource(), this, target);
		}

		@Override
		public <X> ListDataFlow<E, ?, X> flatMap(TypeToken<X> target, Function<? super E, ? extends ObservableValue<? extends X>> map) {
			return (ListDataFlow<E, ?, X>) super.flatMap(target, map);
		}

		@Override
		public <V, X> CombinedListBuilder2<E, E, V, X> combineWith(ObservableValue<V> value, TypeToken<X> target) {
			return new CombinedListBuilder2<>(getSource(), this, target, value, Ternian.NONE);
		}

		@Override
		public <V, X> CombinedListBuilder2<E, E, V, X> combineWith(ObservableValue<V> value, boolean combineNulls, TypeToken<X> target) {
			return new CombinedListBuilder2<>(getSource(), this, target, value, Ternian.of(combineNulls));
		}

		@Override
		public ListModFilterBuilder<E, E> filterModification() {
			return new ListModFilterBuilder<>(getSource(), this);
		}

		@Override
		public ObservableList<E> collect(Observable<?> until) {
			if (until == Observable.empty)
				return getSource();
			else
				return new DerivedList<>(getSource(), manageCollection(), until);
		}
	}

	public static class InitialElementsListFlow<E> extends InitialElementsDataFlow<E> implements ListDataFlow<E, E, E> {
		protected InitialElementsListFlow(ObservableList<E> source, ListDataFlow<E, ?, E> parent, TypeToken<E> targetType,
			Collection<? extends E> initialValues) {
			super(source, parent, targetType, initialValues);
		}

		@Override
		protected ObservableList<E> getSource() {
			return (ObservableList<E>) super.getSource();
		}

		@Override
		public ListDataFlow<E, E, E> withEquivalence(Equivalence<? super E> equivalence) {
			return new EquivalenceSwitchListOp<>(getSource(), this, equivalence);
		}

		@Override
		public ListDataFlow<E, E, E> refresh(Observable<?> refresh) {
			return new RefreshListOp<>(getSource(), this, refresh);
		}

		@Override
		public ListDataFlow<E, E, E> refreshEach(Function<? super E, ? extends Observable<?>> refresh) {
			return new ElementRefreshListOp<>(getSource(), this, refresh);
		}

		@Override
		public <X> MappedListBuilder<E, E, X> map(TypeToken<X> target) {
			return new MappedListBuilder<>(getSource(), this, target);
		}

		@Override
		public <V, X> CombinedListBuilder2<E, E, V, X> combineWith(ObservableValue<V> value, TypeToken<X> target) {
			return new CombinedListBuilder2<>(getSource(), this, target, value, Ternian.NONE);
		}

		@Override
		public <V, X> CombinedListBuilder2<E, E, V, X> combineWith(ObservableValue<V> value, boolean combineNulls, TypeToken<X> target) {
			return new CombinedListBuilder2<>(getSource(), this, target, value, Ternian.of(combineNulls));
		}

		@Override
		public ListModFilterBuilder<E, E> filterModification() {
			return new ListModFilterBuilder<>(getSource(), this);
		}

		@Override
		public ObservableList<E> collect(Observable<?> until) {
			DerivedList<E, E> collected = new DerivedList<>(getSource(), manageCollection(), until);
			getSource().addAll(getInitialValues());
			return collected;
		}
	}

	public static class EquivalenceSwitchListOp<E, T> extends EquivalenceSwitchOp<E, T> implements ListDataFlow<E, T, T> {
		protected EquivalenceSwitchListOp(ObservableList<E> source, AbstractDataFlow<E, ?, T> parent, Equivalence<? super T> equivalence) {
			super(source, parent, equivalence);
		}

		@Override
		protected ObservableList<E> getSource() {
			return (ObservableList<E>) super.getSource();
		}

		@Override
		public ListDataFlow<E, T, T> withEquivalence(Equivalence<? super T> equivalence) {
			return new EquivalenceSwitchListOp<>(getSource(), this, equivalence);
		}

		@Override
		public ListDataFlow<E, T, T> refresh(Observable<?> refresh) {
			return new RefreshListOp<>(getSource(), this, refresh);
		}

		@Override
		public ListDataFlow<E, T, T> refreshEach(Function<? super T, ? extends Observable<?>> refresh) {
			return new ElementRefreshListOp<>(getSource(), this, refresh);
		}

		@Override
		public <X> MappedListBuilder<E, T, X> map(TypeToken<X> target) {
			return new MappedListBuilder<>(getSource(), this, target);
		}

		@Override
		public <X> ListDataFlow<E, ?, X> flatMap(TypeToken<X> target, Function<? super T, ? extends ObservableValue<? extends X>> map) {
			return (ListDataFlow<E, ?, X>) super.flatMap(target, map);
		}

		@Override
		public <V, X> CombinedListBuilder2<E, T, V, X> combineWith(ObservableValue<V> value, TypeToken<X> target) {
			return new CombinedListBuilder2<>(getSource(), this, target, value, Ternian.NONE);
		}

		@Override
		public <V, X> CombinedListBuilder2<E, T, V, X> combineWith(ObservableValue<V> value, boolean combineNulls, TypeToken<X> target) {
			return new CombinedListBuilder2<>(getSource(), this, target, value, Ternian.of(combineNulls));
		}

		@Override
		public ListModFilterBuilder<E, T> filterModification() {
			return new ListModFilterBuilder<>(getSource(), this);
		}

		@Override
		public ObservableList<T> collect(Observable<?> until) {
			return new DerivedList<E, T>(getSource(), manageCollection(), until);
		}
	}

	public static class MapListOp<E, I, T> extends MapOp<E, I, T> implements ListDataFlow<E, I, T> {
		protected MapListOp(ObservableList<E> source, AbstractDataFlow<E, ?, I> parent, TypeToken<T> target,
			Function<? super I, ? extends T> map, boolean mapNulls, Function<? super T, ? extends I> reverse,
			ElementSetter<? super I, ? super T> elementReverse, boolean reverseNulls, boolean reEvalOnUpdate, boolean fireIfUnchanged,
			boolean cached) {
			super(source, parent, target, map, mapNulls, reverse, elementReverse, reverseNulls, reEvalOnUpdate, fireIfUnchanged, cached);
		}

		@Override
		protected ObservableList<E> getSource() {
			return (ObservableList<E>) super.getSource();
		}

		@Override
		public ListDataFlow<E, T, T> withEquivalence(Equivalence<? super T> equivalence) {
			return new EquivalenceSwitchListOp<>(getSource(), this, equivalence);
		}

		@Override
		public ListDataFlow<E, T, T> refresh(Observable<?> refresh) {
			return new RefreshListOp<>(getSource(), this, refresh);
		}

		@Override
		public ListDataFlow<E, T, T> refreshEach(Function<? super T, ? extends Observable<?>> refresh) {
			return new ElementRefreshListOp<>(getSource(), this, refresh);
		}

		@Override
		public <X> MappedListBuilder<E, T, X> map(TypeToken<X> target) {
			return new MappedListBuilder<>(getSource(), this, target);
		}

		@Override
		public <V, X> CombinedListBuilder2<E, T, V, X> combineWith(ObservableValue<V> value, TypeToken<X> target) {
			return new CombinedListBuilder2<>(getSource(), this, target, value, Ternian.NONE);
		}

		@Override
		public <V, X> CombinedListBuilder2<E, T, V, X> combineWith(ObservableValue<V> value, boolean combineNulls, TypeToken<X> target) {
			return new CombinedListBuilder2<>(getSource(), this, target, value, Ternian.of(combineNulls));
		}

		@Override
		public ListModFilterBuilder<E, T> filterModification() {
			return new ListModFilterBuilder<>(getSource(), this);
		}

		@Override
		public ObservableList<T> collect(Observable<?> until) {
			return new DerivedList<E, T>(getSource(), manageCollection(), until);
		}
	}

	public static class CombinedListOp<E, I, T> extends CombinedCollectionDef<E, I, T> implements ListDataFlow<E, I, T> {
		protected CombinedListOp(ObservableList<E> source, AbstractDataFlow<E, ?, I> parent, TypeToken<T> target,
			Map<ObservableValue<?>, Boolean> args, Function<? super CombinedValues<? extends I>, ? extends T> combination,
			boolean combineNulls, Function<? super CombinedValues<? extends T>, ? extends I> reverse, boolean reverseNulls) {
			super(source, parent, target, args, combination, combineNulls, reverse, reverseNulls);
		}

		@Override
		protected ObservableList<E> getSource() {
			return (ObservableList<E>) super.getSource();
		}

		@Override
		public ListDataFlow<E, T, T> withEquivalence(Equivalence<? super T> equivalence) {
			return new EquivalenceSwitchListOp<>(getSource(), this, equivalence);
		}

		@Override
		public ListDataFlow<E, T, T> refresh(Observable<?> refresh) {
			return new RefreshListOp<>(getSource(), this, refresh);
		}

		@Override
		public ListDataFlow<E, T, T> refreshEach(Function<? super T, ? extends Observable<?>> refresh) {
			return new ElementRefreshListOp<>(getSource(), this, refresh);
		}

		@Override
		public <X> MappedListBuilder<E, T, X> map(TypeToken<X> target) {
			return new MappedListBuilder<>(getSource(), this, target);
		}

		@Override
		public <V, X> CombinedListBuilder2<E, T, V, X> combineWith(ObservableValue<V> value, TypeToken<X> target) {
			return new CombinedListBuilder2<>(getSource(), this, target, value, Ternian.NONE);
		}

		@Override
		public <V, X> CombinedListBuilder2<E, T, V, X> combineWith(ObservableValue<V> value, boolean combineNulls, TypeToken<X> target) {
			return new CombinedListBuilder2<>(getSource(), this, target, value, Ternian.of(combineNulls));
		}

		@Override
		public ListModFilterBuilder<E, T> filterModification() {
			return new ListModFilterBuilder<>(getSource(), this);
		}

		@Override
		public ObservableList<T> collect(Observable<?> until) {
			return new DerivedList<E, T>(getSource(), manageCollection(), until);
		}
	}

	public static class RefreshListOp<E, T> extends RefreshOp<E, T> implements ListDataFlow<E, T, T> {
		protected RefreshListOp(ObservableList<E> source, AbstractDataFlow<E, ?, T> parent, Observable<?> refresh) {
			super(source, parent, refresh);
		}

		@Override
		protected ObservableList<E> getSource() {
			return (ObservableList<E>) super.getSource();
		}

		@Override
		public ListDataFlow<E, T, T> withEquivalence(Equivalence<? super T> equivalence) {
			return new EquivalenceSwitchListOp<>(getSource(), this, equivalence);
		}

		@Override
		public ListDataFlow<E, T, T> refresh(Observable<?> refresh) {
			return new RefreshListOp<>(getSource(), this, refresh);
		}

		@Override
		public ListDataFlow<E, T, T> refreshEach(Function<? super T, ? extends Observable<?>> refresh) {
			return new ElementRefreshListOp<>(getSource(), this, refresh);
		}

		@Override
		public <X> MappedListBuilder<E, T, X> map(TypeToken<X> target) {
			return new MappedListBuilder<>(getSource(), this, target);
		}

		@Override
		public <V, X> CombinedListBuilder2<E, T, V, X> combineWith(ObservableValue<V> value, TypeToken<X> target) {
			return new CombinedListBuilder2<>(getSource(), this, target, value, Ternian.NONE);
		}

		@Override
		public <V, X> CombinedListBuilder2<E, T, V, X> combineWith(ObservableValue<V> value, boolean combineNulls, TypeToken<X> target) {
			return new CombinedListBuilder2<>(getSource(), this, target, value, Ternian.of(combineNulls));
		}

		@Override
		public ListModFilterBuilder<E, T> filterModification() {
			return new ListModFilterBuilder<>(getSource(), this);
		}

		@Override
		public ObservableList<T> collect(Observable<?> until) {
			return new DerivedList<E, T>(getSource(), manageCollection(), until);
		}
	}

	public static class ElementRefreshListOp<E, T> extends ElementRefreshOp<E, T> implements ListDataFlow<E, T, T> {
		protected ElementRefreshListOp(ObservableList<E> source, AbstractDataFlow<E, ?, T> parent,
			Function<? super T, ? extends Observable<?>> elementRefresh) {
			super(source, parent, elementRefresh);
		}

		@Override
		protected ObservableList<E> getSource() {
			return (ObservableList<E>) super.getSource();
		}

		@Override
		public ListDataFlow<E, T, T> withEquivalence(Equivalence<? super T> equivalence) {
			return new EquivalenceSwitchListOp<>(getSource(), this, equivalence);
		}

		@Override
		public ListDataFlow<E, T, T> refresh(Observable<?> refresh) {
			return new RefreshListOp<>(getSource(), this, refresh);
		}

		@Override
		public ListDataFlow<E, T, T> refreshEach(Function<? super T, ? extends Observable<?>> refresh) {
			return new ElementRefreshListOp<>(getSource(), this, refresh);
		}

		@Override
		public <X> MappedListBuilder<E, T, X> map(TypeToken<X> target) {
			return new MappedListBuilder<>(getSource(), this, target);
		}

		@Override
		public <V, X> CombinedListBuilder2<E, T, V, X> combineWith(ObservableValue<V> value, TypeToken<X> target) {
			return new CombinedListBuilder2<>(getSource(), this, target, value, Ternian.NONE);
		}

		@Override
		public <V, X> CombinedListBuilder2<E, T, V, X> combineWith(ObservableValue<V> value, boolean combineNulls, TypeToken<X> target) {
			return new CombinedListBuilder2<>(getSource(), this, target, value, Ternian.of(combineNulls));
		}

		@Override
		public ListModFilterBuilder<E, T> filterModification() {
			return new ListModFilterBuilder<>(getSource(), this);
		}

		@Override
		public ObservableList<T> collect(Observable<?> until) {
			return new DerivedList<E, T>(getSource(), manageCollection(), until);
		}
	}

	public static class ListModFilteredOp<E, T> extends ObservableCollectionDataFlowImpl.ModFilteredOp<E, T>
	implements ListDataFlow<E, T, T> {
		protected ListModFilteredOp(ObservableList<E> source, AbstractDataFlow<E, ?, T> parent, String immutableMsg, boolean allowUpdates,
			String addMsg, String removeMsg, Function<? super T, String> addMsgFn, Function<? super T, String> removeMsgFn) {
			super(source, parent, immutableMsg, allowUpdates, addMsg, removeMsg, addMsgFn, removeMsgFn);
		}

		@Override
		protected ObservableList<E> getSource() {
			return (ObservableList<E>) super.getSource();
		}

		@Override
		public ListDataFlow<E, T, T> withEquivalence(Equivalence<? super T> equivalence) {
			return new EquivalenceSwitchListOp<>(getSource(), this, equivalence);
		}

		@Override
		public ListDataFlow<E, T, T> refresh(Observable<?> refresh) {
			return new RefreshListOp<>(getSource(), this, refresh);
		}

		@Override
		public ListDataFlow<E, T, T> refreshEach(Function<? super T, ? extends Observable<?>> refresh) {
			return new ElementRefreshListOp<>(getSource(), this, refresh);
		}

		@Override
		public <X> MappedListBuilder<E, T, X> map(TypeToken<X> target) {
			return new MappedListBuilder<>(getSource(), this, target);
		}

		@Override
		public <V, X> CombinedListBuilder2<E, T, V, X> combineWith(ObservableValue<V> value, TypeToken<X> target) {
			return new CombinedListBuilder2<>(getSource(), this, target, value, Ternian.NONE);
		}

		@Override
		public <V, X> CombinedListBuilder2<E, T, V, X> combineWith(ObservableValue<V> value, boolean combineNulls, TypeToken<X> target) {
			return new CombinedListBuilder2<>(getSource(), this, target, value, Ternian.of(combineNulls));
		}

		@Override
		public ListModFilterBuilder<E, T> filterModification() {
			return new ListModFilterBuilder<>(getSource(), this);
		}

		@Override
		public ObservableList<T> collect(Observable<?> until) {
			return new DerivedList<E, T>(getSource(), manageCollection(), until);
		}
	}

	public static class DerivedList<E, T> extends DerivedCollection<E, T> implements ObservableList<T> {
		protected DerivedList(ObservableList<E> source, CollectionManager<E, ?, T> flow, Observable<?> until) {
			super(source, flow, until);
		}

		@Override
		protected ObservableList<E> getSource() {
			return (ObservableList<E>) super.getSource();
		}
	}

	/**
	 * Implements {@link ObservableList#flattenValue(ObservableValue)}
	 *
	 * @param <E> The type of elements in the collection
	 */
	public static class FlattenedObservableValueList<E> extends FlattenedValueCollection<E> implements ObservableList<E> {
		/** @param collectionObservable The value of lists to flatten */
		protected FlattenedObservableValueList(ObservableValue<? extends ObservableList<E>> collectionObservable) {
			super(collectionObservable);
		}

		@Override
		protected ObservableValue<? extends ObservableList<E>> getWrapped() {
			return (ObservableValue<? extends ObservableList<E>>) super.getWrapped();
		}

		@Override
		public <T> T ofElementAt(int index, Function<? super ObservableCollectionElement<? extends E>, T> onElement) {
			ObservableList<? extends E> list = getWrapped().get();
			if (list == null)
				throw new IndexOutOfBoundsException(index + " of 0");
			return list.ofElementAt(index, onElement);
		}

		@Override
		public <T> T ofMutableElementAt(int index, Function<? super MutableObservableElement<? extends E>, T> onElement) {
			ObservableList<? extends E> list = getWrapped().get();
			if (list == null)
				throw new IndexOutOfBoundsException(index + " of 0");
			return list.ofMutableElementAt(index, onElement);
		}

		@Override
		public MutableObservableSpliterator<E> mutableSpliterator(int index) {
			ObservableList<? extends E> list = getWrapped().get();
			if (list == null) {
				if (index == 0)
					return MutableObservableSpliterator.empty(getType());
				else
					throw new IndexOutOfBoundsException(index + " of 0");
			}
			return ((ObservableList<E>) list).mutableSpliterator(index);
		}

		@Override
		public ObservableElementSpliterator<E> spliterator(int index) {
			ObservableList<? extends E> list = getWrapped().get();
			if (list == null) {
				if (index == 0)
					return ObservableElementSpliterator.empty(getType());
				else
					throw new IndexOutOfBoundsException(index + " of 0");
			}
			return ((ObservableList<E>) list).spliterator(index);
		}
	}

	/**
	 * Implements {@link ObservableList#flatten(ObservableList)}
	 *
	 * @param <E> The type of elements in the collection
	 */
	public static class FlattenedObservableList<E> extends FlattenedObservableCollection<E> implements ObservableList<E> {
		/** @param outer The list of lists to flatten */
		protected FlattenedObservableList(ObservableList<? extends ObservableList<? extends E>> outer) {
			super(outer);
		}

		@Override
		protected ObservableList<? extends ObservableList<? extends E>> getOuter() {
			return (ObservableList<? extends ObservableList<? extends E>>) super.getOuter();
		}
	}
}
