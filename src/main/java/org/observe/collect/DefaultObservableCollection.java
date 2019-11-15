package org.observe.collect;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.Subscription;
import org.observe.util.TypeTokens;
import org.qommons.Causable;
import org.qommons.Transaction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterList;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.CollectionLockingStrategy;
import org.qommons.collect.ElementId;
import org.qommons.collect.FastFailLockingStrategy;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.collect.StampedLockingStrategy;
import org.qommons.collect.ValueStoredCollection;
import org.qommons.tree.BetterTreeList;
import org.qommons.tree.RedBlackNodeList;
import org.qommons.tree.SortedTreeList;

import com.google.common.reflect.TypeToken;

/**
 * An {@link ObservableCollection} built on a {@link BetterList}, which should never be modified from outside
 *
 * @param <E> The type for the collection
 */
public class DefaultObservableCollection<E> implements ObservableCollection<E> {
	/**
	 * @param <E> The type of elements in the collection
	 * @param <B> The sub-type of the builder
	 */
	public static class Builder<E, B extends Builder<E, B>> {
		private final TypeToken<E> theType;
		private BetterList<E> theBacking;
		private CollectionLockingStrategy theLocker;
		private Comparator<? super E> theSorting;
		private Function<ElementId, ElementId> theElementSource;
		private BiFunction<ElementId, BetterCollection<?>, BetterList<ElementId>> theSourceElements;
		private Equivalence<? super E> theEquivalence;
		private String theDescription;

		/**
		 * @param type The type of elements in the collection
		 * @param initDescrip The initial (default) description for the collection
		 */
		protected Builder(TypeToken<E> type, String initDescrip) {
			theType = type;
			theDescription = initDescrip;
		}

		/**
		 * Copy constructor
		 *
		 * @param toCopy The builder to copy
		 */
		protected Builder(Builder<E, ?> toCopy) {
			this(toCopy.theType, toCopy.theDescription);
			theBacking = toCopy.theBacking;
			theLocker = toCopy.theLocker;
			theSorting = toCopy.theSorting;
			theElementSource = toCopy.theElementSource;
			theSourceElements = toCopy.theSourceElements;
			theEquivalence = toCopy.theEquivalence;
		}

		/**
		 * @param backing The pre-set backing for the collection
		 * @return This builder
		 */
		public B withBacking(BetterList<E> backing) {
			theBacking = backing;
			return (B) this;
		}

		/**
		 * @param equivalence The equivalence for the collection
		 * @return This builder
		 */
		public B withEquivalence(Equivalence<? super E> equivalence) {
			theEquivalence = equivalence;
			return (B) this;
		}

		/**
		 * @param locker The locker for the collection
		 * @return This builder
		 */
		public B withLocker(CollectionLockingStrategy locker) {
			theLocker = locker;
			return (B) this;
		}

		/**
		 * @param safe Whether the collection should be thread-safe
		 * @return This builder
		 */
		public B safe(boolean safe) {
			withLocker(safe ? new StampedLockingStrategy() : new FastFailLockingStrategy());
			return (B) this;
		}

		/**
		 * Specifies that the collection should maintain an order (but not necessarily distinctness) among its elements
		 *
		 * @param sorting The sorting for the collection
		 * @return This builder
		 */
		public Builder<E, ?> sortBy(Comparator<? super E> sorting) {
			theSorting = sorting;
			theEquivalence = Equivalence.of(TypeTokens.getRawType(theType), sorting, true);
			return this;
		}

		/**
		 * @param description The description for the collection
		 * @return This builder
		 */
		public B withDescription(String description) {
			theDescription = description;
			return (B) this;
		}

		/**
		 * @param elementSource A function to look up elements in the {@link #withBacking(BetterList) backing} collection by source element
		 *        ID
		 * @return This builder
		 */
		public B withElementSource(Function<ElementId, ElementId> elementSource) {
			theElementSource = elementSource;
			return (B) this;
		}

		/**
		 * @param sourceElements A function to look up elements in a source collection from an element in the
		 *        {@link #withBacking(BetterList) backing} collection
		 * @return This builder
		 */
		public B withSourceElements(BiFunction<ElementId, BetterCollection<?>, BetterList<ElementId>> sourceElements) {
			theSourceElements = sourceElements;
			return (B) this;
		}

		/** @return A builder to build an {@link ObservableSet} with these characteristics */
		public SetBuilder<E, ?> distinct() {
			if (theSorting != null)
				return new DefaultObservableSortedSet.Builder<>(this, theSorting);
			else
				return new SetBuilder<>(this);
		}

		/**
		 * @param sorting The sorting for the set
		 * @return A builder to build an {@link ObservableSortedSet} with these characteristics
		 */
		public DefaultObservableSortedSet.Builder<E, ?> distinctSorted(Comparator<? super E> sorting) {
			return new DefaultObservableSortedSet.Builder<>(this, sorting);
		}

		/** @return The type for the collection */
		protected TypeToken<E> getType() {
			return theType;
		}

		/** @return The pre-set backing for the collection */
		protected BetterList<E> getBacking() {
			BetterList<E> backing = theBacking;
			theBacking = null; // Can only be used once
			return backing;
		}

		/** @return The equivalence for the collection */
		protected Equivalence<? super E> getEquivalence() {
			return theEquivalence;
		}

		/** @return The element source for the collection */
		protected Function<ElementId, ElementId> getElementSource() {
			return theElementSource;
		}

		/** @return The source element lookup function for the collection */
		protected BiFunction<ElementId, BetterCollection<?>, BetterList<ElementId>> getSourceElements() {
			return theSourceElements;
		}

		/** @return The description for the collection */
		protected String getDescription() {
			return theDescription;
		}

		/** @return The locker for the collection */
		protected CollectionLockingStrategy getLocker() {
			if (theLocker != null)
				return theLocker;
			else
				return new StampedLockingStrategy();
		}

		/** @return The sorting for the collection */
		protected Comparator<? super E> getSorting() {
			return theSorting;
		}

		/** @return A new, empty collection */
		public ObservableCollection<E> build() {
			BetterList<E> backing = theBacking;
			if (backing == null) {
				RedBlackNodeList.RBNLBuilder<E, ?> builder = theSorting != null ? SortedTreeList.buildTreeList(theSorting)
					: BetterTreeList.build();
				backing = builder.withDescription(theDescription).withLocker(getLocker()).build();
			}
			return new DefaultObservableCollection<>(theType, backing, theElementSource, theSourceElements, theEquivalence);
		}
	}

	/**
	 * A builder that builds an {@link ObservableSet}
	 *
	 * @param <E> The type of the set to build
	 * @param <B> The sub-type of the builder
	 */
	public static class SetBuilder<E, B extends SetBuilder<E, B>> extends Builder<E, B> {
		/**
		 * @param type The type of elements in the collection
		 * @param initDescrip The initial (default) description for the collection
		 */
		protected SetBuilder(TypeToken<E> type, String initDescrip) {
			super(type, initDescrip);
		}

		/**
		 * Copy constructor
		 *
		 * @param toCopy The builder to copy
		 */
		protected SetBuilder(Builder<E, ?> toCopy) {
			super(toCopy);
		}

		@Override
		public SetBuilder<E, ?> distinct() {
			return this;
		}

		@Override
		public ObservableSet<E> build() {
			return super.build().flow().distinct().collect();
		}
	}

	/**
	 * @param type The type for the new collection
	 * @return A builder to build a new ObservableCollection
	 */
	public static <E> Builder<E, ?> build(TypeToken<E> type) {
		return new Builder<>(type, "observable-collection");
	}

	private final TypeToken<E> theType;
	private final LinkedList<Causable> theTransactionCauses;
	private final BetterList<E> theValues;
	private final org.qommons.collect.ListenerList<Consumer<? super ObservableCollectionEvent<? extends E>>> theObservers;
	private final Function<ElementId, ElementId> theElementSource;
	private final BiFunction<ElementId, BetterCollection<?>, BetterList<ElementId>> theSourceElements;
	private final Equivalence<? super E> theEquivalence;

	/**
	 * @param type The type for this collection
	 * @param list The list to hold this collection's elements
	 */
	public DefaultObservableCollection(TypeToken<E> type, BetterList<E> list) {
		this(type, list, null, null, null);
	}

	/**
	 * @param type The type for this collection
	 * @param list The list to hold this collection's elements
	 * @param elementSource The function to provide element sources for this collection
	 * @param sourceElements The function to provide source elements for elements in this collection
	 * @param equivalence The equivalence for the collection
	 * @see #getElementsBySource(ElementId)
	 * @see #getSourceElements(ElementId, BetterCollection)
	 */
	public DefaultObservableCollection(TypeToken<E> type, BetterList<E> list, //
		Function<ElementId, ElementId> elementSource, BiFunction<ElementId, BetterCollection<?>, BetterList<ElementId>> sourceElements, //
		Equivalence<? super E> equivalence) {
		theType = type;
		if (list instanceof ObservableCollection)
			throw new UnsupportedOperationException("ObservableCollection is not supported here");
		theTransactionCauses = new LinkedList<>();
		theValues = list;
		theObservers = new org.qommons.collect.ListenerList<>("A collection may not be modified as a result of a change event");
		theElementSource = elementSource;
		theSourceElements = sourceElements;
		theEquivalence = equivalence == null ? Equivalence.DEFAULT : equivalence;
	}

	/** @return This collection's backing values */
	protected BetterList<E> getValues() {
		return theValues;
	}

	@Override
	public Object getIdentity() {
		return theValues.getIdentity();
	}

	@Override
	public boolean isLockSupported() {
		return theValues.isLockSupported();
	}

	@Override
	public Transaction lock(boolean write, Object cause) {
		Transaction t = theValues.lock(write, cause);
		return addCause(t, write, cause);
	}

	private Transaction addCause(Transaction valueLock, boolean write, Object cause) {
		Causable tCause;
		Transaction causeFinish;
		if (cause == null && !theTransactionCauses.isEmpty()) {
			causeFinish = null;
			tCause = null;
		} else if (cause instanceof Causable) {
			causeFinish = null;
			tCause = (Causable) cause;
		} else {
			tCause = Causable.simpleCause(cause);
			causeFinish = Causable.use(tCause);
		}
		if (write && tCause != null)
			theTransactionCauses.add(tCause);
		return new Transaction() {
			private boolean isClosed;

			@Override
			public void close() {
				if (isClosed)
					return;
				isClosed = true;
				if (causeFinish != null)
					causeFinish.close();
				if (write && tCause != null)
					theTransactionCauses.removeLastOccurrence(tCause);
				valueLock.close();
			}
		};
	}

	@Override
	public Transaction tryLock(boolean write, Object cause) {
		Transaction t = theValues.tryLock(write, cause);
		return t == null ? null : addCause(t, write, cause);
	}

	Causable getCurrentCause() {
		return theTransactionCauses.peekFirst();
	}

	@Override
	public long getStamp() {
		return theValues.getStamp();
	}

	@Override
	public boolean isContentControlled() {
		return theValues.isContentControlled();
	}

	@Override
	public TypeToken<E> getType() {
		return theType;
	}

	@Override
	public Equivalence<? super E> equivalence() {
		return theEquivalence;
	}

	@Override
	public int size() {
		return theValues.size();
	}

	@Override
	public boolean isEmpty() {
		return theValues.isEmpty();
	}

	@Override
	public int getElementsBefore(ElementId id) {
		return theValues.getElementsBefore(id);
	}

	@Override
	public int getElementsAfter(ElementId id) {
		return theValues.getElementsAfter(id);
	}

	@Override
	public CollectionElement<E> getElement(int index) {
		return theValues.getElement(index);
	}

	@Override
	public CollectionElement<E> getElement(E value, boolean first) {
		return theValues.getElement(value, first);
	}

	@Override
	public CollectionElement<E> getElement(ElementId id) {
		return theValues.getElement(id);
	}

	@Override
	public CollectionElement<E> getTerminalElement(boolean first) {
		return theValues.getTerminalElement(first);
	}

	@Override
	public CollectionElement<E> getAdjacentElement(ElementId elementId, boolean next) {
		return theValues.getAdjacentElement(elementId, next);
	}

	@Override
	public MutableCollectionElement<E> mutableElement(ElementId id) {
		return mutableElementFor(theValues.mutableElement(id));
	}

	@Override
	public BetterList<CollectionElement<E>> getElementsBySource(ElementId sourceEl) {
		BetterList<CollectionElement<E>> els = theValues.getElementsBySource(sourceEl);
		if (!els.isEmpty())
			return els;
		if (theElementSource != null) {
			ElementId el = theElementSource.apply(sourceEl);
			if (el == null)
				return BetterList.empty();
			return BetterList.of(getElement(el));
		}
		return BetterList.empty();
	}

	@Override
	public BetterList<ElementId> getSourceElements(ElementId localElement, BetterCollection<?> sourceCollection) {
		if (sourceCollection == this)
			return theValues.getSourceElements(localElement, theValues); // Validate element
		else if (theSourceElements != null)
			return theSourceElements.apply(localElement, sourceCollection);
		return theValues.getSourceElements(localElement, sourceCollection);
	}

	@Override
	public String canAdd(E value, ElementId after, ElementId before) {
		return theValues.canAdd(value, after, before);
	}

	@Override
	public CollectionElement<E> addElement(E value, ElementId after, ElementId before, boolean first)
		throws UnsupportedOperationException, IllegalArgumentException {
		try (Transaction t = lock(true, null)) {
			CollectionElement<E> el = theValues.addElement(value, after, before, first);
			if (el == null)
				return null;
			ObservableCollectionEvent<E> event = new ObservableCollectionEvent<>(el.getElementId(), getType(),
				theValues.getElementsBefore(el.getElementId()), CollectionChangeType.add, null, value, getCurrentCause());
			fire(event);
			return el;
		}
	}

	@Override
	public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
		return theObservers.add(observer, true)::run;
	}

	@Override
	public void clear() {
		try (Transaction t = lock(true, null)) {
			CollectionElement<E> el = getTerminalElement(true);
			while (el != null) {
				MutableCollectionElement<E> mutable = mutableElement(el.getElementId());
				if (mutable.canRemove() == null)
					mutable.remove();
				el = getAdjacentElement(el.getElementId(), true);
			}
		}
	}

	@Override
	public void setValue(Collection<ElementId> elements, E value) {
		for (ElementId el : elements)
			mutableElement(el).set(value);
	}

	void fire(ObservableCollectionEvent<E> evt) {
		try (Transaction t = ObservableCollectionEvent.use(evt)) {
			theObservers.forEach(//
				listener -> listener.accept(evt));
		}
	}

	private MutableCollectionElement<E> mutableElementFor(MutableCollectionElement<E> valueEl) {
		return new MutableCollectionElement<E>() {
			@Override
			public BetterCollection<E> getCollection() {
				return DefaultObservableCollection.this;
			}

			@Override
			public ElementId getElementId() {
				return valueEl.getElementId();
			}

			@Override
			public E get() {
				return valueEl.get();
			}

			@Override
			public String isEnabled() {
				return valueEl.isEnabled();
			}

			@Override
			public String isAcceptable(E value) {
				return valueEl.isAcceptable(value);
			}

			@Override
			public void set(E value) throws UnsupportedOperationException, IllegalArgumentException {
				E old = get();
				if (value == old && theValues instanceof ValueStoredCollection) {
					// A pure update on a value-stored collection may mean that the value has changed such that it needs to be moved
					// Correct the storage structure
					boolean[] thisMoved = new boolean[1];
					RepairOperation op = new RepairOperation(getCurrentCause());
					try (Transaction opT = Causable.use(op); Transaction vt = lock(true, op)) {
						((ValueStoredCollection<E>) theValues).repair(valueEl.getElementId(),
							new ValueStoredCollection.RepairListener<E, Void>() {
							@Override
							public Void removed(CollectionElement<E> element) {
								if (element.getElementId().equals(valueEl.getElementId()))
									thisMoved[0] = true;
								fire(new ObservableCollectionEvent<>(element.getElementId(), getType(),
									theValues.getElementsBefore(element.getElementId()), CollectionChangeType.remove, element.get(),
									element.get(), op));
								return null;
							}

							@Override
							public void disposed(E oldValue, Void data) {}

							@Override
							public void transferred(CollectionElement<E> element, Void data) {
								fire(new ObservableCollectionEvent<>(element.getElementId(), getType(),
									theValues.getElementsBefore(element.getElementId()), CollectionChangeType.add, null, element.get(),
									op));
							}
						});
					}
					if (thisMoved[0])
						return;
				}
				valueEl.set(value);
				fire(new ObservableCollectionEvent<>(getElementId(), getType(), getElementsBefore(getElementId()), CollectionChangeType.set,
					old, value, getCurrentCause()));
			}

			@Override
			public String canRemove() {
				return valueEl.canRemove();
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				try (Transaction t = lock(true, null)) {
					E old = get();
					valueEl.remove();
					fire(new ObservableCollectionEvent<>(getElementId(), getType(), getElementsBefore(getElementId()),
						CollectionChangeType.remove, old, old, getCurrentCause()));
				}
			}

			@Override
			public String toString() {
				return valueEl.toString();
			}
		};
	}

	/** A Causable representing a {@link ValueStoredCollection} repair operation */
	static class RepairOperation extends Causable {
		RepairOperation(Object cause) {
			super(cause);
		}
	}

	@Override
	public int hashCode() {
		return theValues.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		return theValues.equals(obj);
	}

	@Override
	public String toString() {
		return theValues.toString();
	}
}