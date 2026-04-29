package org.observe.collect;

import java.util.Collection;
import java.util.function.Consumer;

import org.observe.Equivalence;
import org.qommons.Subscription;
import org.qommons.Transaction;
import org.qommons.collect.ElementId;
import org.qommons.collect.ListElement;
import org.qommons.collect.ModControlledCollection;

public class ModControlledObservableCollection<E, C extends ObservableCollection<E>> extends ModControlledCollection.ModControlledList<E, C>
implements ObservableCollection<E> {
	public static <E, C extends ObservableCollection<E>> C controlCollection(C collection,
		CollectionModificationControl<E> control,
		CollectionModificationListener<E> listener){
		if(collection instanceof ObservableSortedSet)
			return (C) new MCOSortedSet<>((ObservableSortedSet<E>) collection, control, listener);
		else if(collection instanceof ObservableSortedCollection)
			return (C) new MCOSortedCollection<>((ObservableSortedCollection<E>) collection, control, listener);
		else if(collection instanceof ObservableSet)
			return (C) new MCOSet<>((ObservableSet<E>) collection, control, listener);
		else
			return (C) new ModControlledObservableCollection<>(collection, control, listener);
	}

	public static <E, C extends ObservableSet<E>> C controlSet(C collection, CollectionModificationControl<E> control,
		CollectionModificationListener<E> listener) {
		if (collection instanceof ObservableSortedSet)
			return (C) new MCOSortedSet<>((ObservableSortedSet<E>) collection, control, listener);
		else
			return (C) new MCOSet<>(collection, control, listener);
	}

	public ModControlledObservableCollection(C backing, CollectionModificationControl<E> control,
		CollectionModificationListener<E> listener) {
		super(backing, control, listener);
	}

	@Override
	public ModControlledObservableCollection<E, C> alias(String alias) {
		super.alias(alias);
		return this;
	}

	@Override
	public boolean isLockSupported() {
		return getBacking().isLockSupported();
	}

	@Override
	public boolean isEventing() {
		return getBacking().isEventing();
	}

	@Override
	public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
		return getBacking().onChange(observer);
	}

	@Override
	public Equivalence<? super E> equivalence() {
		return getBacking().equivalence();
	}

	@Override
	public void setValue(Collection<ElementId> elements, E value) {
		setAllValues(getBacking(), getControl(), getListener(), elements, value);
	}

	static <E> void setAllValues(ObservableCollection<E> backing, CollectionModificationControl<E> control,
		CollectionModificationListener<E> listener, Collection<ElementId> elements, E value) {
		String msg = null;
		for (ElementId id : elements) {
			msg = control.isAcceptable(backing.getElement(id), value);
			if (msg != null)
				break;
		}
		if (msg != null)
			throw new UnsupportedOperationException(msg);
		backing.setValue(elements, value);
		for (ElementId id : elements)
			listener.elementReplaced(backing.getElement(id), value);
	}

	public static class MCOSet<E, C extends ObservableSet<E>> extends ModControlledObservableCollection<E, ObservableSet<E>>
	implements ObservableSet<E> {
		public MCOSet(ObservableSet<E> backing, CollectionModificationControl<E> control, CollectionModificationListener<E> listener) {
			super(backing, control, listener);
		}

		@Override
		public MCOSet<E, C> alias(String alias) {
			super.alias(alias);
			return this;
		}

		@Override
		public ListElement<E> getOrAdd(E value, ElementId after, ElementId before, boolean first, Runnable preAdd, Runnable postAdd) {
			CollectionModificationControl<E> control = getControl();
			CollectionModificationListener<E> listener = getListener();
			if (control == null && listener == null)
				return getBacking().getOrAdd(value, after, before, first, preAdd, postAdd);
			try (Transaction t = lock(true, null)) {
				ListElement<E> found = getElement(value, first);
				if (found != null)
					return found;
				String msg = control == null ? null : control.canAdd(value, after, before);
				if (msg != null)
					throw new UnsupportedOperationException(msg);
				if (preAdd != null)
					preAdd.run();
				ListElement<E> added = getBacking().addElement(value, after, before, first);
				if (listener != null)
					listener.elementAdded(added);
				if (postAdd != null)
					postAdd.run();
				return added;
			}
		}

		@Override
		public boolean isConsistent(ElementId element) {
			return getBacking().isConsistent(element);
		}

		@Override
		public boolean checkConsistency() {
			return getBacking().checkConsistency();
		}

		@Override
		public <X> boolean repair(ElementId element, RepairListener<E, X> listener) {
			if (!isConsistent(element))
				throw new UnsupportedOperationException("Set repair is not supported here");
			return false;
		}

		@Override
		public <X> boolean repair(RepairListener<E, X> listener) {
			if (checkConsistency())
				throw new UnsupportedOperationException("Set repair is not supported here");
			return false;
		}

		@Override
		public <T> T[] toArray(T[] a) {
			return super.toArray(a);
		}
	}

	public static class MCOSortedCollection<E, C extends ObservableSortedCollection<E>>
	extends ModControlledCollection.ModControlledSortedList<E, C> implements ObservableSortedCollection<E> {
		public MCOSortedCollection(C backing, CollectionModificationControl<E> control, CollectionModificationListener<E> listener) {
			super(backing, control, listener);
		}

		@Override
		public MCOSortedCollection<E, C> alias(String alias) {
			super.alias(alias);
			return this;
		}

		@Override
		public boolean isLockSupported() {
			return getBacking().isLockSupported();
		}

		@Override
		public boolean isEventing() {
			return getBacking().isEventing();
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
			return getBacking().onChange(observer);
		}

		@Override
		public Equivalence<? super E> equivalence() {
			return getBacking().equivalence();
		}

		@Override
		public void setValue(Collection<ElementId> elements, E value) {
			setAllValues(getBacking(), getControl(), getListener(), elements, value);
		}
	}

	public static class MCOSortedSet<E, C extends ObservableSortedSet<E>> extends ModControlledCollection.ModControlledSortedSet<E, C>
	implements ObservableSortedSet<E> {
		public MCOSortedSet(C backing, CollectionModificationControl<E> control, CollectionModificationListener<E> listener) {
			super(backing, control, listener);
		}

		@Override
		public MCOSortedSet<E, C> alias(String alias) {
			super.alias(alias);
			return this;
		}

		@Override
		public boolean isLockSupported() {
			return getBacking().isLockSupported();
		}

		@Override
		public boolean isEventing() {
			return getBacking().isEventing();
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
			return getBacking().onChange(observer);
		}

		@Override
		public Equivalence.SortedEquivalence<? super E> equivalence() {
			return getBacking().equivalence();
		}

		@Override
		public void setValue(Collection<ElementId> elements, E value) {
			setAllValues(getBacking(), getControl(), getListener(), elements, value);
		}
	}
}
