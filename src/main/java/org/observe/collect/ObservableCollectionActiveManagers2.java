package org.observe.collect;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.observe.Equivalence;
import org.observe.Observable;
import org.observe.Subscription;
import org.observe.XformOptions;
import org.observe.collect.FlatMapOptions.FlatMapDef;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.collect.ObservableCollectionActiveManagers.AbstractSameTypeActiveManager;
import org.observe.collect.ObservableCollectionActiveManagers.AbstractSameTypeElement;
import org.observe.collect.ObservableCollectionActiveManagers.ActiveCollectionManager;
import org.observe.collect.ObservableCollectionActiveManagers.CollectionElementListener;
import org.observe.collect.ObservableCollectionActiveManagers.DerivedCollectionElement;
import org.observe.collect.ObservableCollectionActiveManagers.ElementAccepter;
import org.observe.collect.ObservableCollectionDataFlowImpl.ModFilterer;
import org.observe.util.TypeTokens;
import org.observe.util.WeakListening;
import org.qommons.BiTuple;
import org.qommons.Causable;
import org.qommons.Identifiable;
import org.qommons.LambdaUtils;
import org.qommons.Lockable;
import org.qommons.Lockable.CoreId;
import org.qommons.Ternian;
import org.qommons.ThreadConstrained;
import org.qommons.ThreadConstraint;
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterHashMap;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterMap;
import org.qommons.collect.BetterMap.MapRepairListener;
import org.qommons.collect.BetterSortedList.SortedSearchFilter;
import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.MapEntryHandle;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.collect.OptimisticContext;
import org.qommons.tree.BetterTreeList;
import org.qommons.tree.BetterTreeSet;
import org.qommons.tree.BinaryTreeNode;

import com.google.common.reflect.TypeToken;

/**
 * Contains more implementations of {@link ActiveCollectionManager} and its dependencies
 *
 * @see ObservableCollectionActiveManagers
 */
public class ObservableCollectionActiveManagers2 {
	private ObservableCollectionActiveManagers2() {}

	static class IntersectionManager<E, T, X> implements ActiveCollectionManager<E, T, T> {
		private class IntersectionElement {
			private ElementId valueEl;
			final List<IntersectedCollectionElement> sourceElements;
			final List<ElementId> filterElements;

			IntersectionElement() {
				sourceElements = new ArrayList<>(2);
				filterElements = new ArrayList<>(2);
			}

			boolean isPresent() {
				return filterElements.isEmpty() == isExclude;
			}

			X get() {
				return theValues.getEntryById(valueEl).getKey();
			}

			void filterElementAdded(ElementId rightEl, Object cause) {
				boolean preEmpty = filterElements.isEmpty();
				filterElements.add(rightEl);
				if (preEmpty)
					presentChanged(cause);
			}

			void filterElementRemoved(ElementId rightEl, Object cause) {
				filterElements.remove(rightEl);
				if (filterElements.isEmpty()) {
					presentChanged(cause);
					if (sourceElements.isEmpty())
						theValues.mutableEntry(valueEl).remove();
				}
			}

			void sourceAdded(IntersectedCollectionElement element) {
				sourceElements.add(element);
			}

			void sourceRemoved(IntersectedCollectionElement element) {
				sourceElements.remove(element);
				if (sourceElements.isEmpty() && filterElements.isEmpty())
					theValues.mutableEntry(valueEl).remove();
			}

			private void presentChanged(Object cause) {
				if (isPresent()) {
					for (IntersectedCollectionElement el : sourceElements)
						theAccepter.accept(el, cause);
				} else {
					for (IntersectedCollectionElement el : sourceElements)
						el.fireRemove(cause);
				}
			}

			@Override
			public String toString() {
				return get() + " (" + sourceElements.size() + "/" + filterElements.size() + ")";
			}
		}

		class IntersectedCollectionElement implements DerivedCollectionElement<T> {
			private final DerivedCollectionElement<T> theParentEl;
			private IntersectionElement intersection;
			private CollectionElementListener<T> theListener;

			IntersectedCollectionElement(DerivedCollectionElement<T> parentEl, IntersectionElement intersect, boolean synthetic) {
				theParentEl = parentEl;
				intersection = intersect;
				if (!synthetic)
					theParentEl.setListener(new CollectionElementListener<T>() {
						@Override
						public void update(T oldValue, T newValue, Object cause, boolean internalOnly) {
							if (internalOnly) {
								ObservableCollectionActiveManagers.update(theListener, oldValue, newValue, cause, true);
							} else {
								boolean oldPresent;
								if (intersection == null)
									oldPresent = isExclude;
								else
									oldPresent = intersection.isPresent();
								if (intersection != null)
									intersection.sourceRemoved(IntersectedCollectionElement.this);
								intersection = theFilterEquivalence.isElement(newValue) ? intersection((X) newValue) : null;
								if (intersection != null)
									intersection.sourceAdded(IntersectedCollectionElement.this);
								boolean newPresent;
								if (intersection == null)
									newPresent = isExclude;
								else
									newPresent = intersection.isPresent();
								if (oldPresent && newPresent)
									ObservableCollectionActiveManagers.update(theListener, oldValue, newValue, cause, false);
								else if (oldPresent && !newPresent) {
									ObservableCollectionActiveManagers.removed(theListener, oldValue, cause);
									theListener = null;
								} else if (!oldPresent && newPresent)
									theAccepter.accept(IntersectedCollectionElement.this, cause);
								else { // Wasn't present before, still isn't present. Nothing to do.
								}
							}
						}

						@Override
						public void removed(T value, Object cause) {
							boolean wasPresent;
							if (intersection == null)
								wasPresent = isExclude;
							else
								wasPresent = intersection.isPresent();
							if (wasPresent)
								ObservableCollectionActiveManagers.removed(theListener, value, cause);
							if (intersection != null)
								intersection.sourceRemoved(IntersectedCollectionElement.this);
							intersection = null;
						}
					});
			}

			void fireRemove(Object cause) {
				ObservableCollectionActiveManagers.removed(theListener, theParentEl.get(), cause);
				theListener = null;
			}

			@Override
			public int compareTo(DerivedCollectionElement<T> o) {
				return theParentEl.compareTo(((IntersectedCollectionElement) o).theParentEl);
			}

			@Override
			public void setListener(CollectionElementListener<T> listener) {
				theListener = listener;
			}

			@Override
			public T get() {
				return theParentEl.get();
			}

			@Override
			public String isEnabled() {
				return theParentEl.isEnabled();
			}

			@Override
			public String isAcceptable(T value) {
				if (!theFilterEquivalence.isElement(value))
					return isExclude ? null : StdMsg.ILLEGAL_ELEMENT;
				String msg = theParentEl.isAcceptable(value);
				if (msg != null)
					return msg;
				if (theFilterEquivalence.isElement(theParentEl.get()) && theFilterEquivalence.elementEquals((X) theParentEl.get(), value))
					return null;
				IntersectionElement intersect = theValues.get(value);
				boolean filterHas = intersect == null ? false : !intersect.filterElements.isEmpty();
				if (filterHas == isExclude)
					return StdMsg.ILLEGAL_ELEMENT;
				return null;
			}

			@Override
			public void set(T value) throws UnsupportedOperationException, IllegalArgumentException {
				if (!theFilterEquivalence.isElement(value)) {
					if (!isExclude)
						throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
				} else if (theFilterEquivalence.isElement(theParentEl.get())
					&& !theFilterEquivalence.elementEquals((X) theParentEl.get(), value)) {
					IntersectionElement intersect = theValues.get(value);
					boolean filterHas = intersect == null ? false : !intersect.filterElements.isEmpty();
					if (filterHas == isExclude)
						throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
				}
				theParentEl.set(value);
			}

			@Override
			public String canRemove() {
				return theParentEl.canRemove();
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				theParentEl.remove();
			}

			@Override
			public boolean equals(Object o) {
				return o instanceof IntersectionManager.IntersectedCollectionElement
					&& theParentEl.compareTo(((IntersectedCollectionElement) o).theParentEl) == 0;
			}
		}

		private final ActiveCollectionManager<E, ?, T> theParent;
		private final ObservableCollection<X> theFilter;
		private final Equivalence<? super X> theFilterEquivalence; // Make this a field since we'll need it often
		/** Whether a value's presence in the right causes the value in the left to be present (false) or absent (true) in the result */
		private final boolean isExclude;
		private final BetterMap<X, IntersectionElement> theValues;
		// The following two fields are needed because the values may mutate
		private final Map<ElementId, IntersectionElement> theRightElementValues;

		private ElementAccepter<T> theAccepter;

		IntersectionManager(ActiveCollectionManager<E, ?, T> parent, CollectionDataFlow<?, ?, X> filter, boolean exclude) {
			theParent = parent;
			theFilter = filter.collect();
			theFilterEquivalence = filter.equivalence();
			isExclude = exclude;
			// Use the filter's equivalence, just like BetterCollection#removeAll(collection) and retainAll(Collection)
			theValues = theFilterEquivalence.createMap();
			theRightElementValues = new HashMap<>();
		}

		@Override
		public Object getIdentity() {
			return Identifiable.wrap(theParent.getIdentity(), isExclude ? "without" : "intersect", theFilter.getIdentity());
		}

		@Override
		public TypeToken<T> getTargetType() {
			return theParent.getTargetType();
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			return ThreadConstraint.union(theParent.getThreadConstraint(), theFilter.getThreadConstraint());
		}

		@Override
		public boolean isLockSupported() {
			return theParent.isLockSupported() || theFilter.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return Lockable.lockAll(
				Lockable.lockable(theParent, write, cause),
				Lockable.lockable(theFilter));
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return Lockable.tryLockAll(
				Lockable.lockable(theParent, write, cause), Lockable.lockable(theFilter));
		}

		@Override
		public CoreId getCoreId() {
			return Lockable.getCoreId(Lockable.lockable(theParent, false, null),
				Lockable.lockable(theFilter));
		}

		@Override
		public boolean clear() {
			return false;
		}

		@Override
		public boolean isContentControlled() {
			return true;
		}

		@Override
		public Comparable<DerivedCollectionElement<T>> getElementFinder(T value) {
			Comparable<DerivedCollectionElement<T>> parentFinder = theParent.getElementFinder(value);
			if (parentFinder == null)
				return null;
			return el -> parentFinder.compareTo(((IntersectedCollectionElement) el).theParentEl);
		}

		@Override
		public BetterList<DerivedCollectionElement<T>> getElementsBySource(ElementId sourceEl, BetterCollection<?> sourceCollection) {
			try (Transaction t = lock(false, null)) {
				return BetterList.of(Stream.concat(//
					theParent.getElementsBySource(sourceEl, sourceCollection).stream()
					.map(el -> new IntersectedCollectionElement(el, null, true)), //
					theFilter.getElementsBySource(sourceEl, sourceCollection).stream().flatMap(el -> {
						IntersectionElement intEl = theRightElementValues.get(el.getElementId());
						return intEl == null ? Stream.empty() : intEl.sourceElements.stream();
					}).distinct()));
			}
		}

		@Override
		public BetterList<ElementId> getSourceElements(DerivedCollectionElement<T> localElement, BetterCollection<?> sourceCollection) {
			return BetterList.of(Stream.concat(//
				theParent.getSourceElements(((IntersectedCollectionElement) localElement).theParentEl, sourceCollection).stream(), //
				((IntersectedCollectionElement) localElement).intersection.filterElements.stream().flatMap(//
					el -> theFilter.getSourceElements(el, sourceCollection).stream())//
				));
		}

		@Override
		public DerivedCollectionElement<T> getEquivalentElement(DerivedCollectionElement<?> flowEl) {
			if (!(flowEl instanceof IntersectionManager.IntersectedCollectionElement))
				return null;
			DerivedCollectionElement<T> found = theParent.getEquivalentElement(((IntersectedCollectionElement) flowEl).theParentEl);
			return found == null ? null : new IntersectedCollectionElement(found, null, true);
		}

		@Override
		public String canAdd(T toAdd, DerivedCollectionElement<T> after, DerivedCollectionElement<T> before) {
			IntersectionElement intersect = theFilterEquivalence.isElement(toAdd) ? theValues.get(toAdd) : null;
			boolean filterHas = intersect == null ? false : !intersect.filterElements.isEmpty();
			if (filterHas == isExclude)
				return StdMsg.ILLEGAL_ELEMENT;
			return theParent.canAdd(toAdd, strip(after), strip(before));
		}

		@Override
		public DerivedCollectionElement<T> addElement(T value, DerivedCollectionElement<T> after, DerivedCollectionElement<T> before,
			boolean first) {
			IntersectionElement intersect = theFilterEquivalence.isElement(value) ? theValues.get(value) : null;
			boolean filterHas = intersect == null ? false : !intersect.filterElements.isEmpty();
			if (filterHas == isExclude)
				throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
			DerivedCollectionElement<T> parentEl = theParent.addElement(value, strip(after), strip(before), first);
			return parentEl == null ? null : new IntersectedCollectionElement(parentEl, null, true);
		}

		@Override
		public String canMove(DerivedCollectionElement<T> valueEl, DerivedCollectionElement<T> after, DerivedCollectionElement<T> before) {
			return theParent.canMove(strip(valueEl), strip(after), strip(before));
		}

		@Override
		public DerivedCollectionElement<T> move(DerivedCollectionElement<T> valueEl, DerivedCollectionElement<T> after,
			DerivedCollectionElement<T> before, boolean first, Runnable afterRemove) {
			return new IntersectedCollectionElement(theParent.move(strip(valueEl), strip(after), strip(before), first, afterRemove), null,
				true);
		}

		private DerivedCollectionElement<T> strip(DerivedCollectionElement<T> el) {
			return el == null ? null : ((IntersectedCollectionElement) el).theParentEl;
		}

		@Override
		public Equivalence<? super T> equivalence() {
			return theParent.equivalence(); // We don't actually switch the equivalence from the parent, we just filter on it
		}

		@Override
		public void setValues(Collection<DerivedCollectionElement<T>> elements, T newValue)
			throws UnsupportedOperationException, IllegalArgumentException {
			IntersectionElement intersect = theFilterEquivalence.isElement(newValue) ? theValues.get(newValue) : null;
			boolean filterHas = intersect == null ? false : !intersect.filterElements.isEmpty();
			if (filterHas == isExclude)
				throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
			theParent.setValues(elements.stream().map(el -> ((IntersectedCollectionElement) el).theParentEl).collect(Collectors.toList()),
				newValue);
		}

		IntersectionElement intersection(X value) {
			MapEntryHandle<X, IntersectionElement> element = theValues.getOrPutEntry(value, v -> new IntersectionElement(), null, null,
				false, null);
			element.getValue().valueEl = element.getElementId();
			return element.getValue();
		}

		@Override
		public void begin(boolean fromStart, ElementAccepter<T> onElement, WeakListening listening) {
			theAccepter = onElement;
			listening.withConsumer((ObservableCollectionEvent<? extends X> evt) -> {
				// We're not modifying, but we want to obtain an exclusive lock
				// to ensure that nothing above or below us is firing events at the same time.
				try (Transaction t = theParent.lock(true, evt)) {
					IntersectionElement element;
					switch (evt.getType()) {
					case add:
						element = intersection(evt.getNewValue());
						element.filterElementAdded(evt.getElementId(), evt);
						theRightElementValues.put(evt.getElementId(), element);
						break;
					case remove:
						element = theRightElementValues.remove(evt.getElementId());
						element.filterElementRemoved(evt.getElementId(), evt);
						break;
					case set:
						element = theRightElementValues.get(evt.getElementId());
						if (theFilterEquivalence.elementEquals(element.get(), evt.getNewValue())) {
							// Check for change of the entire sort order
							// We don't care about the order of the values, as it's just used for a filter,
							// but we do need to maintain the set's integrity
							theValues.repair(element.valueEl, new MapRepairListener<X, IntersectionElement, Void>() {
								@Override
								public Void removed(MapEntryHandle<X, IntersectionElement> removed) {
									return null;
								}

								@Override
								public void disposed(X key, IntersectionElement value, Void data) {
									IntersectionElement newIntersect = theValues.get(key);
									if (newIntersect != null) {
										for (ElementId filterEl : value.filterElements) {
											value.filterElementRemoved(filterEl, evt);
											newIntersect.filterElementAdded(filterEl, evt);
										}
									} else {
										for (ElementId filterEl : value.filterElements)
											value.filterElementRemoved(filterEl, evt);
									}
								}

								@Override
								public void transferred(MapEntryHandle<X, IntersectionElement> readded, Void data) {
									// Don't care
								}
							});
							return;
						}
						element.filterElementRemoved(evt.getElementId(), evt);
						IntersectionElement newEl = intersection(evt.getNewValue());
						theRightElementValues.put(evt.getElementId(), newEl);
						newEl.filterElementAdded(evt.getElementId(), evt);
						break;
					}
				}
			}, action -> theFilter.subscribe(action, fromStart).removeAll());
			theParent.begin(fromStart, (parentEl, cause) -> {
				IntersectionElement element = theFilterEquivalence.isElement(parentEl.get()) ? intersection((X) parentEl.get()) : null;
				IntersectedCollectionElement el = new IntersectedCollectionElement(parentEl, element, false);
				element.sourceAdded(el);
				if (element.isPresent())
					onElement.accept(el, cause);
			}, listening);
		}
	}

	static class UpdateCatchingManager<E, T> extends AbstractSameTypeActiveManager<E, T> {
		private final ThreadConstraint theConstraint;
		private final ThreadConstraint theUnionConstraint;
		private final ReentrantLock theLock;

		UpdateCatchingManager(ActiveCollectionManager<E, ?, T> parent, ThreadConstraint constraint) {
			super(parent);
			if (constraint != null) {
				theConstraint = constraint;
				theUnionConstraint = ThreadConstraint.union(parent.getThreadConstraint(), constraint);
				theLock = new ReentrantLock();
			} else {
				theConstraint = theUnionConstraint = parent.getThreadConstraint();
				theLock = null;
			}
		}

		@Override
		public Object getIdentity() {
			return Identifiable.wrap(getParent().getIdentity(), "catchUpdates");
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			return theUnionConstraint;
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			if (theLock == null)
				return getParent().tryLock(write, cause);
			if (write) {
				if (getParent().getThreadConstraint().isEventThread()) {
					Transaction sourceWrite = getParent().tryLock(true, cause);
					if (sourceWrite != null) {
						theLock.lock();
						return () -> {
							sourceWrite.close();
							theLock.unlock();
						};
					} else if (theConstraint.isEventThread()) {
						Transaction sourceRead = getParent().tryLock(false, cause);
						if (sourceRead == null)
							return null;
						theLock.lock();
						return () -> {
							sourceRead.close();
							theLock.unlock();
						};
					} else
						throw new IllegalStateException(WRONG_THREAD_MESSAGE);
				} else if (theConstraint.isEventThread()) {
					Transaction t = getParent().tryLock(false, cause);
					if (t == null)
						return null;
					theLock.lock();
					return () -> {
						t.close();
						theLock.unlock();
					};
				} else
					throw new IllegalStateException(WRONG_THREAD_MESSAGE);
			} else {
				Transaction t = getParent().tryLock(false, cause);
				if (t == null)
					return null;
				theLock.lock();
				return () -> {
					t.close();
					theLock.unlock();
				};
			}
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			if (theLock == null)
				return getParent().lock(write, cause);
			if (write) {
				if (getParent().getThreadConstraint().isEventThread()) {
					Transaction sourceWrite = getParent().tryLock(true, cause);
					if (sourceWrite != null) {
						theLock.lock();
						return () -> {
							sourceWrite.close();
							theLock.unlock();
						};
					} else if (theConstraint.isEventThread()) {
						Transaction sourceRead = getParent().lock(false, cause);
						theLock.lock();
						return () -> {
							sourceRead.close();
							theLock.unlock();
						};
					} else
						throw new IllegalStateException(WRONG_THREAD_MESSAGE);
				} else if (theConstraint.isEventThread()) {
					Transaction t = getParent().lock(false, cause);
					theLock.lock();
					return () -> {
						t.close();
						theLock.unlock();
					};
				} else
					throw new IllegalStateException(WRONG_THREAD_MESSAGE);
			} else {
				Transaction t = getParent().lock(false, cause);
				theLock.lock();
				return () -> {
					t.close();
					theLock.unlock();
				};
			}
		}

		@Override
		protected boolean areElementsWrapped() {
			return true;
		}

		@Override
		protected DerivedCollectionElement<T> wrap(DerivedCollectionElement<T> parentEl, boolean synthetic) {
			return parentEl == null ? null : new UpdateCatchingElement(parentEl);
		}

		@Override
		protected DerivedCollectionElement<T> peel(DerivedCollectionElement<T> myEl) {
			return myEl == null ? null : ((UpdateCatchingElement) myEl).theParentEl;
		}

		@Override
		public void setValues(Collection<DerivedCollectionElement<T>> elements, T newValue)
			throws UnsupportedOperationException, IllegalArgumentException {
			boolean allUpdates;
			if (theConstraint.isEventThread()) {
				allUpdates = true;
				for (DerivedCollectionElement<T> el : elements) {
					if (el.get() != newValue) {
						allUpdates = false;
						break;
					}
				}
			} else
				allUpdates = false;
			if (!allUpdates)
				super.setValues(elements, newValue);
			else {
				Causable cause = Causable.simpleCause();
				try (Transaction causeT = cause.use(); Transaction t = getParent().lock(false, cause)) {
					theLock.lock();
					try {
						for (DerivedCollectionElement<T> el : elements)
							((UpdateCatchingElement) el).update(newValue, cause);
					} finally {
						theLock.unlock();
					}
				}
			}
		}

		class UpdateCatchingElement extends AbstractSameTypeElement<T> {
			private CollectionElementListener<T> theListener;

			UpdateCatchingElement(DerivedCollectionElement<T> parentEl) {
				super(parentEl);
			}

			@Override
			public void setListener(CollectionElementListener<T> listener) {
				theListener = listener;
				super.setListener(listener);
			}

			@Override
			public void set(T value) throws UnsupportedOperationException, IllegalArgumentException {
				if (value == get() && theConstraint.isEventThread()) {
					Causable cause = Causable.simpleCause();
					try (Transaction causeT = cause.use(); Transaction t = getParent().lock(false, cause)) {
						theLock.lock();
						try {
							update(value, cause);
						} finally {
							theLock.unlock();
						}
					}
				} else
					super.set(value);
			}

			private void update(T value, Object cause) {
				if (theListener != null)
					theListener.update(value, value, cause, false);
			}
		}
	}

	static class ActiveModFilteredManager<E, T> extends AbstractSameTypeActiveManager<E, T> {
		private final ModFilterer<T> theFilter;

		ActiveModFilteredManager(ActiveCollectionManager<E, ?, T> parent, ModFilterer<T> filter) {
			super(parent);
			theFilter = filter;
		}

		@Override
		public Object getIdentity() {
			return Identifiable.wrap(getParent().getIdentity(), "filterMod", theFilter);
		}

		@Override
		protected boolean areElementsWrapped() {
			return true;
		}

		@Override
		protected DerivedCollectionElement<T> wrap(DerivedCollectionElement<T> parentEl, boolean synthetic) {
			return parentEl == null ? null : new ModFilteredElement(parentEl);
		}

		@Override
		protected DerivedCollectionElement<T> peel(DerivedCollectionElement<T> myEl) {
			return myEl == null ? null : ((ModFilteredElement) myEl).theParentEl;
		}

		@Override
		public boolean clear() {
			if (!theFilter.isRemoveFiltered() && theFilter.getUnmodifiableMessage() == null)
				return getParent().clear();
			if (theFilter.getUnmodifiableMessage() != null || theFilter.getRemoveMessage() != null)
				return true;
			else
				return false;
		}

		@Override
		public boolean isContentControlled() {
			return !theFilter.isEmpty() || getParent().isContentControlled();
		}

		@Override
		public String canAdd(T toAdd, DerivedCollectionElement<T> after, DerivedCollectionElement<T> before) {
			String msg = theFilter.canAdd(toAdd);
			if (msg == null)
				msg = super.canAdd(toAdd, after, before);
			return msg;
		}

		@Override
		public DerivedCollectionElement<T> addElement(T value, DerivedCollectionElement<T> after, DerivedCollectionElement<T> before,
			boolean first) {
			theFilter.assertAdd(value);
			return super.addElement(value, after, before, first);
		}

		@Override
		public String canMove(DerivedCollectionElement<T> valueEl, DerivedCollectionElement<T> after, DerivedCollectionElement<T> before) {
			String msg = theFilter.canMove();
			if (msg == null)
				return super.canMove(valueEl, after, before);
			else if ((after == null || valueEl.compareTo(after) >= 0) && (before == null || valueEl.compareTo(before) <= 0))
				return null;
			return msg;
		}

		@Override
		public DerivedCollectionElement<T> move(DerivedCollectionElement<T> valueEl, DerivedCollectionElement<T> after,
			DerivedCollectionElement<T> before, boolean first, Runnable afterRemove) {
			if (theFilter.canMove() != null && (after == null || valueEl.compareTo(after) >= 0)
				&& (before == null || valueEl.compareTo(before) <= 0))
				return valueEl;
			theFilter.assertMovable();
			return super.move(valueEl, after, before, first, afterRemove);
		}

		@Override
		public void setValues(Collection<DerivedCollectionElement<T>> elements, T newValue)
			throws UnsupportedOperationException, IllegalArgumentException {
			for (DerivedCollectionElement<T> el : elements)
				theFilter.assertSet(newValue, el::get);
			getParent().setValues(//
				elements.stream().map(el -> ((ModFilteredElement) el).theParentEl).collect(Collectors.toList()), newValue);
		}

		private class ModFilteredElement extends AbstractSameTypeElement<T> {
			ModFilteredElement(DerivedCollectionElement<T> parentEl) {
				super(parentEl);
			}

			@Override
			public String isEnabled() {
				String msg = theFilter.isEnabled();
				if (msg == null)
					msg = theParentEl.isEnabled();
				return msg;
			}

			@Override
			public String isAcceptable(T value) {
				String msg = theFilter.isAcceptable(value, //
					this::get);
				if (msg == null)
					msg = theParentEl.isAcceptable(value);
				return msg;
			}

			@Override
			public void set(T value) throws UnsupportedOperationException, IllegalArgumentException {
				theFilter.assertSet(value, //
					this::get);
				theParentEl.set(value);
			}

			@Override
			public String canRemove() {
				String msg = theFilter.canRemove(//
					this::get);
				if (msg == null)
					msg = theParentEl.canRemove();
				return msg;
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				theFilter.assertRemove(//
					this::get);
				theParentEl.remove();
			}
		}
	}

	static class ActiveRefreshingCollectionManager<E, T> extends AbstractSameTypeActiveManager<E, T> {
		private final Observable<?> theRefresh;
		private final BetterList<RefreshingElement> theElements;

		ActiveRefreshingCollectionManager(ActiveCollectionManager<E, ?, T> parent, Observable<?> refresh) {
			super(parent);
			theRefresh = refresh;
			// if (ObservableCollectionActiveManagers.isStrictMode())
			// theElements = SortedTreeList.<RefreshingElement> buildTreeList(RefreshingElement::compareTo).build();
			// else
			theElements = BetterTreeList.<RefreshingElement> build().build();
		}

		@Override
		public Object getIdentity() {
			return Identifiable.wrap(getParent().getIdentity(), "refresh", theRefresh.getIdentity());
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			return ThreadConstrained.getThreadConstraint(getParent(), theRefresh);
		}

		@Override
		public boolean isLockSupported() {
			return getParent().isLockSupported() && theRefresh.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return Lockable.lockAll(Lockable.lockable(getParent(), write, cause), theRefresh);
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return Lockable.tryLockAll(Lockable.lockable(getParent(), write, cause), theRefresh);
		}

		@Override
		public CoreId getCoreId() {
			return Lockable.getCoreId(Lockable.lockable(getParent(), false, null), theRefresh);
		}

		@Override
		protected boolean areElementsWrapped() {
			return true;
		}

		@Override
		protected DerivedCollectionElement<T> wrap(DerivedCollectionElement<T> parentEl, boolean synthetic) {
			return parentEl == null ? null : new RefreshingElement(parentEl, synthetic);
		}

		@Override
		protected DerivedCollectionElement<T> peel(DerivedCollectionElement<T> myEl) {
			return myEl == null ? null : ((RefreshingElement) myEl).theParentEl;
		}

		@Override
		public void begin(boolean fromStart, ElementAccepter<T> onElement, WeakListening listening) {
			getParent().begin(fromStart, (parentEl, cause) -> {
				// Make sure the refresh doesn't fire while we're firing notifications from the parent change
				try (Transaction t = theRefresh.lock()) {
					onElement.accept(new RefreshingElement(parentEl, false), cause);
				}
			}, listening);
			listening.withConsumer((Object r) -> {
				// Make sure the parent doesn't fire while we're firing notifications from the refresh
				// Wrapping the event with a causable here allows listeners down the line to take actions after the entire refresh
				Transaction extraT = null;
				if (!(r instanceof Causable)) {
					r = Causable.simpleCause(r);
					extraT = ((Causable) r).use();
				}
				try (Transaction t = getParent().lock(false, r)) {
					// Refreshing should be done in element order
					Collections.sort(theElements);
					CollectionElement<RefreshingElement> el = theElements.getTerminalElement(true);
					while (el != null) {
						// But now need to re-set the correct element ID for each element
						el.get().theElementId = el.getElementId();
						el.get().refresh(r);
						el = theElements.getAdjacentElement(el.getElementId(), true);
					}
				} finally {
					if (extraT != null)
						extraT.close();
				}
			}, theRefresh::act);
		}

		private class RefreshingElement extends AbstractSameTypeElement<T> {
			private ElementId theElementId;
			private CollectionElementListener<T> theListener;

			RefreshingElement(DerivedCollectionElement<T> parent, boolean synthetic) {
				super(parent);
				if (!synthetic) {
					theElementId = theElements.addElement(this, false).getElementId();
					theParentEl.setListener(new CollectionElementListener<T>() {
						@Override
						public void update(T oldValue, T newValue, Object cause, boolean internalOnly) {
							try (Transaction t = theRefresh.lock()) {
								ObservableCollectionActiveManagers.update(theListener, oldValue, newValue, cause, internalOnly);
							}
						}

						@Override
						public void removed(T value, Object cause) {
							theElements.mutableElement(theElementId).remove();
							try (Transaction t = theRefresh.lock()) {
								ObservableCollectionActiveManagers.removed(theListener, value, cause);
							}
						}
					});
				} else
					theElementId = null;
			}

			void refresh(Object cause) {
				T value = get();
				ObservableCollectionActiveManagers.update(theListener, value, value, cause, false);
			}

			@Override
			public void setListener(CollectionElementListener<T> listener) {
				theListener = listener;
			}
		}
	}

	static class ElementRefreshingCollectionManager<E, T> extends AbstractSameTypeActiveManager<E, T> {
		private class RefreshHolder {
			private final ElementId theElementId;
			private final Subscription theSub;
			final BetterSortedSet<RefreshingElement> elements;

			RefreshHolder(Observable<?> refresh) {
				theElementId = theRefreshObservables.putEntry(refresh, this, false).getElementId();
				elements = BetterTreeSet.<RefreshingElement> buildTreeSet(RefreshingElement::compareTo).build();
				theSub = theListening.withConsumer(r -> {
					try (Transaction t = Lockable.lockAll(
						Lockable.lockable(theLock, ElementRefreshingCollectionManager.this, ThreadConstraint.ANY),
						Lockable.lockable(getParent(), false, null))) {
						RefreshingElement setting = (RefreshingElement) theSettingElement.get();
						if (setting != null && elements.contains(setting))
							setting.refresh(r);
						for (RefreshingElement el : elements) {
							if (el != setting)
								el.refresh(r);
						}
					}
				}, action -> refresh.noInit().act(action));
			}

			void remove(ElementId element) {
				elements.mutableElement(element).remove();
				if (elements.isEmpty()) {
					theSub.unsubscribe();
					theRefreshObservables.mutableEntry(theElementId).remove();
				}
			}
		}

		private final Function<? super T, ? extends Observable<?>> theRefresh;
		private final BetterMap<Observable<?>, RefreshHolder> theRefreshObservables;
		private WeakListening theListening;
		private final ReentrantLock theLock;
		Supplier<DerivedCollectionElement<? extends T>> theSettingElement;

		ElementRefreshingCollectionManager(ActiveCollectionManager<E, ?, T> parent, Function<? super T, ? extends Observable<?>> refresh) {
			super(parent);
			theRefresh = refresh;
			theRefreshObservables = BetterHashMap.build().build();
			theLock = new ReentrantLock();
			theSettingElement = () -> null;
		}

		void withSettingElement(Supplier<DerivedCollectionElement<? extends T>> settingElement) {
			theSettingElement = settingElement;
		}

		@Override
		public Object getIdentity() {
			return Identifiable.wrap(getParent().getIdentity(), "refresh", theRefresh);
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			return ThreadConstraint.ANY; // Can't know
		}

		@Override
		public boolean isLockSupported() {
			return true;
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			// The purpose of the refresh lock is solely to prevent simultaneous refresh events,
			// or any refresh events that would violate the contract of a held lock
			// If this lock method will obtain any exclusive locks, then locking the refresh lock is unnecessary,
			// because incoming refresh updates obtain a read lock on the parent
			if (write)
				return getParent().lock(write, cause);
			else
				return Lockable.lockAll(Lockable.lockable(getParent(), write, cause),
					Lockable.lockable(theLock, this, ThreadConstraint.ANY));
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			// The purpose of the refresh lock is solely to prevent simultaneous refresh events,
			// or any refresh events that would violate the contract of a held lock
			// If this lock method will obtain any exclusive locks, then locking the refresh lock is unnecessary,
			// because incoming refresh updates obtain a read lock on the parent
			if (write)
				return getParent().tryLock(write, cause);
			else
				return Lockable.tryLockAll(Lockable.lockable(getParent(), write, cause),
					Lockable.lockable(theLock, this, ThreadConstraint.ANY));
		}

		@Override
		public CoreId getCoreId() {
			return Lockable.getCoreId(Lockable.lockable(getParent(), false, null), Lockable.lockable(theLock, this, ThreadConstraint.ANY));
		}

		Transaction lockRefresh() {
			return Lockable.lock(theLock, this);
		}

		@Override
		protected boolean areElementsWrapped() {
			return true;
		}

		@Override
		protected DerivedCollectionElement<T> wrap(DerivedCollectionElement<T> parentEl, boolean synthetic) {
			return parentEl == null ? null : new RefreshingElement(parentEl);
		}

		@Override
		protected DerivedCollectionElement<T> peel(DerivedCollectionElement<T> myEl) {
			return myEl == null ? null : ((RefreshingElement) myEl).theParentEl;
		}

		@Override
		public void begin(boolean fromStart, ElementAccepter<T> onElement, WeakListening listening) {
			theListening = listening;
			getParent().begin(fromStart, (parentEl, cause) -> {
				try (Transaction t = lockRefresh()) {
					onElement.accept(new RefreshingElement(parentEl), cause);
				}
			}, listening);
		}

		private class RefreshingElement extends AbstractSameTypeElement<T> {
			private Observable<?> theCurrentRefresh;
			private RefreshHolder theCurrentHolder;
			private ElementId theElementId;
			private CollectionElementListener<T> theListener;
			private boolean isInstalled;

			RefreshingElement(DerivedCollectionElement<T> parentEl) {
				super(parentEl);
			}

			/**
			 * Called when the value of the element changes. Handles the case where the element's refresh observable changes as a result.
			 *
			 * @param value The new value for the element
			 */
			void updated(T value) {
				Observable<?> newRefresh = theRefresh.apply(value);
				if (newRefresh == theCurrentRefresh)
					return;

				// Refresh is different, need to remove from old refresh and add to new
				if (theCurrentHolder != null) { // Remove from old refresh if non-null
					theCurrentHolder.remove(theElementId);
					theElementId = null;
					theCurrentHolder = null;
				}
				theCurrentRefresh = newRefresh;
				if (newRefresh != null) {
					RefreshHolder newHolder = theRefreshObservables.get(newRefresh);
					if (newHolder == null)
						newHolder = new RefreshHolder(newRefresh); // Adds itself
					theCurrentHolder = newHolder;
					theElementId = newHolder.elements.addElement(this, false).getElementId();
				}
			}

			private void installOrUninstall() {
				if (!isInstalled && theListener != null) {
					isInstalled = true;
					updated(theParentEl.get()); // Subscribe to the initial refresh value
					theParentEl.setListener(new CollectionElementListener<T>() {
						@Override
						public void update(T oldValue, T newValue, Object cause, boolean internalOnly) {
							try (Transaction t = lockRefresh()) {
								ObservableCollectionActiveManagers.update(theListener, oldValue, newValue, cause, internalOnly);
								if (!internalOnly)
									updated(newValue);
							}
						}

						@Override
						public void removed(T value, Object cause) {
							try (Transaction t = lockRefresh()) {
								if (theCurrentHolder != null) { // Remove from old refresh if non-null
									theCurrentHolder.remove(theElementId);
									theElementId = null;
									theCurrentHolder = null;
								}
								theCurrentRefresh = null;
								ObservableCollectionActiveManagers.removed(theListener, value, cause);
							}
						}
					});
				} else if (isInstalled && theListener == null) {
					theParentEl.setListener(null);
					if (theCurrentHolder != null) { // Remove from old refresh if non-null
						theCurrentHolder.remove(theElementId);
						theElementId = null;
						theCurrentHolder = null;
					}
					theCurrentRefresh = null;
				}
			}

			void refresh(Object cause) {
				T value = get();
				ObservableCollectionActiveManagers.update(theListener, value, value, cause, false);
			}

			@Override
			public void setListener(CollectionElementListener<T> listener) {
				theListener = listener;
				installOrUninstall();
			}
		}
	}

	static class FlattenedManager<E, I, V, T> implements ActiveCollectionManager<E, I, T> {
		private final ActiveCollectionManager<E, ?, I> theParent;
		private final TypeToken<T> theTargetType;
		private final Function<? super I, ? extends CollectionDataFlow<?, ?, ? extends V>> theMap;
		private final FlatMapDef<I, V, T> theOptions;

		private ElementAccepter<T> theAccepter;
		private WeakListening theListening;
		private final BetterTreeSet<FlattenedHolder> theOuterElements;
		private final ReentrantReadWriteLock theLock;

		FlattenedManager(ActiveCollectionManager<E, ?, I> parent, TypeToken<T> targetType,
			Function<? super I, ? extends CollectionDataFlow<?, ?, ? extends V>> map, FlatMapDef<I, V, T> options) {
			theParent = parent;
			theTargetType = targetType;
			theMap = map;
			theOptions = options;

			theOuterElements = BetterTreeSet.<FlattenedHolder> buildTreeSet((f1, f2) -> f1.theParentEl.compareTo(f2.theParentEl)).build();
			theLock = new ReentrantReadWriteLock();
		}

		@Override
		public Object getIdentity() {
			return Identifiable.wrap(theParent.getIdentity(), "flatten", theMap, theOptions);
		}

		@Override
		public TypeToken<T> getTargetType() {
			return theTargetType;
		}

		@Override
		public Equivalence<? super T> equivalence() {
			return Equivalence.DEFAULT;
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			if (theParent.getThreadConstraint() == ThreadConstraint.NONE) {
				return ThreadConstrained.getThreadConstraint(theOuterElements);
			} else
				return ThreadConstraint.ANY; // Can't know
		}

		@Override
		public boolean isLockSupported() {
			return true; // No way to know if any of the outer collection's elements will ever support locking
		}

		Transaction lockLocal() {
			Lock localLock = theLock.writeLock();
			localLock.lock();
			return localLock::unlock;
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			/* No operations against this manager can affect the parent collection, but only its content collections */
			return Lockable.lockAll(Lockable.lockable(theParent), () -> theOuterElements, //
				oe -> Lockable.lockable(oe.manager, write, cause));
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			/* No operations against this manager can affect the parent collection, but only its content collections */
			return Lockable.tryLockAll(Lockable.lockable(theParent), () -> theOuterElements, //
				oe -> Lockable.lockable(oe.manager, write, cause));
		}

		@Override
		public CoreId getCoreId() {
			return Transactable.getCoreId(theParent, () -> theOuterElements, oe -> oe.manager);
		}

		@Override
		public boolean isContentControlled() {
			// The only way this method could ever reliably return false is if we could be sure
			// that the outer collection could never obtain any new values nor have any set operations that result in new flows
			// If we could determine that, the code below would be correct. As it is, we always have to return true.
			// try (Transaction t = theParent.lock(false, null)) {
			// boolean anyControlled = false;
			// for (FlattenedHolder outerEl : theOuterElements) {
			// if (outerEl.manager == null)
			// continue;
			// anyControlled |= outerEl.manager.isContentControlled();
			// }
			// return anyControlled;
			// }
			return true;
		}

		@Override
		public Comparable<DerivedCollectionElement<T>> getElementFinder(T value) {
			return null;
		}

		@Override
		public BetterList<DerivedCollectionElement<T>> getElementsBySource(ElementId sourceEl, BetterCollection<?> sourceCollection) {
			try (Transaction t = lock(false, null)) {
				List<DerivedCollectionElement<T>> elements = new LinkedList<>();

				BetterList<DerivedCollectionElement<I>> parentEBS = theParent.getElementsBySource(sourceEl, sourceCollection);
				for (DerivedCollectionElement<I> outerEl : parentEBS) {
					BinaryTreeNode<FlattenedHolder> fh = theOuterElements.getRoot()
						.findClosest(fhEl -> outerEl.compareTo(fhEl.get().theParentEl), true, true, OptimisticContext.TRUE);
					if (fh != null)
						elements.addAll(fh.get().theElements);
				}

				for (FlattenedHolder holder : theOuterElements) {
					if (parentEBS.contains(holder.theParentEl))
						continue;
					BetterList<? extends DerivedCollectionElement<? extends V>> holderEls = holder.manager.getElementsBySource(sourceEl,
						sourceCollection);
					for (DerivedCollectionElement<? extends V> innerEl : holderEls) {
						BinaryTreeNode<FlattenedElement> fe = holder.theElements.getRoot().findClosest(
							feEl -> ((DerivedCollectionElement<V>) innerEl).compareTo((DerivedCollectionElement<V>) feEl.get().theParentEl),
							true, true, OptimisticContext.TRUE);
						if (fe != null
							&& ((DerivedCollectionElement<V>) fe.get().theParentEl).compareTo((DerivedCollectionElement<V>) innerEl) == 0)
							elements.add(fe.get());
					}
				}
				return elements.isEmpty() ? BetterList.empty()
					: BetterList.of(elements.toArray(new DerivedCollectionElement[elements.size()]));
			}
		}

		@Override
		public BetterList<ElementId> getSourceElements(DerivedCollectionElement<T> localElement, BetterCollection<?> sourceCollection) {
			FlattenedElement flatEl = (FlattenedElement) localElement;
			ActiveCollectionManager<?, ?, T> mgr = (ActiveCollectionManager<?, ?, T>) ((FlattenedElement) localElement).theHolder.manager;
			if (mgr == null)
				return theParent.getSourceElements(flatEl.theHolder.theParentEl, sourceCollection);
			else
				return BetterList.of(Stream.concat(//
					theParent.getSourceElements(flatEl.theHolder.theParentEl, sourceCollection).stream(), //
					mgr.getSourceElements((DerivedCollectionElement<T>) flatEl.theParentEl, sourceCollection).stream()//
					));
		}

		@Override
		public DerivedCollectionElement<T> getEquivalentElement(DerivedCollectionElement<?> flowEl) {
			if (!(flowEl instanceof FlattenedManager.FlattenedElement))
				return null;
			FlattenedElement other = (FlattenedElement) flowEl;
			DerivedCollectionElement<I> parentFound = theParent.getEquivalentElement(other.theHolder.theParentEl);
			if (parentFound == null)
				return null;
			FlattenedHolder holder = theOuterElements.searchValue(h -> parentFound.compareTo(h.theParentEl), SortedSearchFilter.OnlyMatch);
			if (holder == null || holder.manager == null)
				return null;
			DerivedCollectionElement<V> flatFound = (DerivedCollectionElement<V>) holder.manager.getEquivalentElement(other.theParentEl);
			if (flatFound == null)
				return null;
			return holder.theElements.searchValue(e -> flatFound.compareTo((DerivedCollectionElement<V>) e.theParentEl),
				SortedSearchFilter.OnlyMatch);
		}

		@Override
		public boolean clear() {
			try (Transaction t = theParent.lock(false, null)) {
				boolean allCleared = true;
				for (FlattenedHolder outerEl : theOuterElements) {
					if (outerEl.manager == null)
						continue;
					allCleared &= outerEl.manager.clear();
				}
				return allCleared;
			}
		}

		class FlattenedHolderIter {
			FlattenedHolder holder;
			FlattenedElement lowBound;
			FlattenedElement highBound;

			DerivedCollectionElement<V> getBound(boolean low) {
				FlattenedElement bound = low ? lowBound : highBound;
				return bound == null ? null : (DerivedCollectionElement<V>) bound.theParentEl;
			}
		}

		class InterElementIterable implements Iterable<FlattenedHolderIter> {
			private final FlattenedElement start;
			private final FlattenedElement end;
			private final boolean ascending;

			InterElementIterable(DerivedCollectionElement<T> start, DerivedCollectionElement<T> end, boolean ascending) {
				this.start = (FlattenedManager<E, I, V, T>.FlattenedElement) start;
				this.end = (FlattenedManager<E, I, V, T>.FlattenedElement) end;
				this.ascending = ascending;
			}

			@Override
			public Iterator<FlattenedHolderIter> iterator() {
				return new Iterator<FlattenedHolderIter>() {
					private FlattenedHolder theHolder;
					private FlattenedHolderIter theIterStruct;

					{
						if (ascending) {
							if (start == null)
								theHolder = CollectionElement.get(theOuterElements.getTerminalElement(true));
							else
								theHolder = start.theHolder;
						} else {
							if (end == null)
								theHolder = CollectionElement.get(theOuterElements.getTerminalElement(false));
							else
								theHolder = end.theHolder;
						}
						while (theHolder != null && theHolder.manager == null)
							theHolder = CollectionElement.get(theOuterElements.getAdjacentElement(theHolder.holderElement, ascending));
						theIterStruct = new FlattenedHolderIter();
					}

					@Override
					public boolean hasNext() {
						if (theHolder == null)
							return false;
						FlattenedElement terminal = ascending ? end : start;
						if (terminal == null)
							return true;
						int comp = theHolder.holderElement.compareTo(terminal.theHolder.holderElement);
						if (comp == 0)
							return true;
						else
							return (comp < 0) == ascending;
					}

					@Override
					public FlattenedHolderIter next() {
						if (!hasNext())
							throw new NoSuchElementException();
						theIterStruct.holder = theHolder;
						if (start == null || !theHolder.equals(start.theHolder))
							theIterStruct.lowBound = null;
						else
							theIterStruct.lowBound = start;
						if (end == null || !theHolder.equals(end.theHolder))
							theIterStruct.highBound = null;
						else
							theIterStruct.highBound = end;
						do {
							theHolder = CollectionElement.get(theOuterElements.getAdjacentElement(theHolder.holderElement, ascending));
						} while (theHolder != null && theHolder.manager == null);
						return theIterStruct;
					}
				};
			}
		}

		@Override
		public String canAdd(T toAdd, DerivedCollectionElement<T> after, DerivedCollectionElement<T> before) {
			if (theOptions != null && theOptions.getReverse() == null)
				return StdMsg.UNSUPPORTED_OPERATION;
			String firstMsg = null;
			try (Transaction t = theParent.lock(false, null)) {
				for (FlattenedHolderIter holder : new InterElementIterable(after, before, true)) {
					FlatMapOptions.FlatMapReverseQueryResult<I, V> result;
					if (theOptions == null)
						result = FlatMapOptions.FlatMapReverseQueryResult.value((V) toAdd);
					else
						result = theOptions.getReverse().canReverse(holder.holder::getValue, null, toAdd);
					if (result.getError() != null) {
						if (firstMsg == null)
							firstMsg = result.getError();
					} else if (result.replaceSecondary()) {
						V reversed = result.getSecondary();
						String msg;
						if (!TypeTokens.get().isInstance(holder.holder.manager.getTargetType(), reversed)
							|| !holder.holder.manager.equivalence().isElement(reversed)) {
							msg = StdMsg.ILLEGAL_ELEMENT;
						} else
							msg = ((ActiveCollectionManager<?, ?, V>) holder.holder.manager).canAdd(reversed, holder.getBound(true),
								holder.getBound(false));
						if (msg == null)
							return null;
						else if (firstMsg == null)
							firstMsg = msg;
					}
					if (result.replaceSource())
						throw new UnsupportedOperationException("Source elements cannot be replaced with an add operation");
				}
			}
			if (firstMsg == null)
				firstMsg = StdMsg.UNSUPPORTED_OPERATION;
			return firstMsg;
		}

		@Override
		public DerivedCollectionElement<T> addElement(T value, DerivedCollectionElement<T> after, DerivedCollectionElement<T> before,
			boolean first) {
			if (theOptions != null && theOptions.getReverse() == null)
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			try (Transaction t = theParent.lock(false, null)) {
				String firstMsg = null;
				for (FlattenedHolderIter holder : new InterElementIterable(after, before, first)) {
					FlatMapOptions.FlatMapReverseQueryResult<I, V> result;
					if (theOptions == null)
						result = FlatMapOptions.FlatMapReverseQueryResult.value((V) value);
					else
						result = theOptions.getReverse().reverse(holder.holder::getValue, null, value);
					if (result.getError() != null) {
						if (firstMsg == null)
							firstMsg = result.getError();
					} else if (result.replaceSecondary()) {
						V reversed = result.getSecondary();
						String msg;
						if (!TypeTokens.get().isInstance(holder.holder.manager.getTargetType(), reversed)
							|| !holder.holder.manager.equivalence().isElement(reversed)) {
							msg = StdMsg.ILLEGAL_ELEMENT;
						} else
							msg = ((ActiveCollectionManager<?, ?, V>) holder.holder.manager).canAdd(reversed, holder.getBound(true),
								holder.getBound(false));
						if (msg == null) {
							DerivedCollectionElement<V> added = ((ActiveCollectionManager<?, ?, V>) holder.holder.manager)
								.addElement(reversed, holder.getBound(true), holder.getBound(false), first);
							if (added != null)
								return new FlattenedElement(holder.holder, added, true);
						} else if (firstMsg == null)
							firstMsg = msg;
					}
					if (result.replaceSource())
						throw new UnsupportedOperationException("Source elements cannot be replaced with an add operation");
				}
				if (firstMsg == null || firstMsg.equals(StdMsg.UNSUPPORTED_OPERATION))
					throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
				else
					throw new IllegalArgumentException(firstMsg);
			}
		}

		@Override
		public String canMove(DerivedCollectionElement<T> valueEl, DerivedCollectionElement<T> after, DerivedCollectionElement<T> before) {
			FlattenedElement flatV = (FlattenedManager<E, I, V, T>.FlattenedElement) valueEl;
			String firstMsg = null;
			try (Transaction t = theParent.lock(false, null)) {
				String removable = flatV.theParentEl.canRemove();
				V value = flatV.theParentEl.get();
				for (FlattenedHolderIter holder : new InterElementIterable(after, before, true)) {
					String msg;
					if (holder.holder.equals(flatV.theHolder))
						msg = ((ActiveCollectionManager<?, ?, V>) holder.holder.manager)
						.canMove((DerivedCollectionElement<V>) flatV.theParentEl, holder.getBound(true), holder.getBound(false));
					else if (holder.holder.manager.getIdentity().equals(flatV.theHolder.manager.getIdentity())) {
						// Don't support moving an element from 2 uses of the same collection--super messy
						msg = StdMsg.UNSUPPORTED_OPERATION;
					} else if (!TypeTokens.get().isInstance(holder.holder.manager.getTargetType(), value)
						|| !holder.holder.manager.equivalence().isElement(value)) {
						msg = StdMsg.ILLEGAL_ELEMENT;
					} else if (removable != null)
						msg = removable;
					else
						msg = ((ActiveCollectionManager<?, ?, V>) holder.holder.manager).canAdd(value, holder.getBound(true),
							holder.getBound(false));
					if (msg == null)
						return null;
					else if (firstMsg == null)
						firstMsg = msg;
				}
			}
			if (firstMsg == null)
				firstMsg = StdMsg.UNSUPPORTED_OPERATION;
			return firstMsg;
		}

		@Override
		public DerivedCollectionElement<T> move(DerivedCollectionElement<T> valueEl, DerivedCollectionElement<T> after,
			DerivedCollectionElement<T> before, boolean first, Runnable afterRemove) {
			if (first && after != null && valueEl.compareTo(after) == 0)
				return valueEl;
			else if (!first && before != null && valueEl.compareTo(before) == 0)
				return valueEl;
			FlattenedElement flatV = (FlattenedElement) valueEl;
			String firstMsg = null;
			try (Transaction t = theParent.lock(false, null)) {
				String removable = flatV.theParentEl.canRemove();
				V value = flatV.theParentEl.get();
				DerivedCollectionElement<V> moved = null;
				for (FlattenedHolderIter holder : new InterElementIterable(after, before, first)) {
					String msg;
					if (holder.holder.equals(flatV.theHolder)) {
						msg = ((ActiveCollectionManager<?, ?, V>) holder.holder.manager)
							.canMove((DerivedCollectionElement<V>) flatV.theParentEl, holder.getBound(true), holder.getBound(false));
						if (msg == null)
							moved = ((ActiveCollectionManager<?, ?, V>) holder.holder.manager).move(
								(DerivedCollectionElement<V>) flatV.theParentEl, holder.getBound(true), holder.getBound(false), first,
								afterRemove);
					} else if (holder.holder.manager.getIdentity().equals(flatV.theHolder.manager.getIdentity())) {
						// Don't support moving an element from 2 uses of the same collection--super messy
						msg = StdMsg.UNSUPPORTED_OPERATION;
					} else if (removable != null)
						msg = removable;
					else if (!TypeTokens.get().isInstance(holder.holder.manager.getTargetType(), value)
						|| !holder.holder.manager.equivalence().isElement(value)) {
						msg = StdMsg.ILLEGAL_ELEMENT;
					} else {
						msg = ((ActiveCollectionManager<?, ?, V>) holder.holder.manager).canAdd(value, holder.getBound(true),
							holder.getBound(false));
						if (msg == null) {
							flatV.theParentEl.remove();
							if (afterRemove != null)
								afterRemove.run();
							moved = ((ActiveCollectionManager<?, ?, V>) holder.holder.manager).addElement(value, holder.getBound(true),
								holder.getBound(false), first);
							if (moved == null)
								throw new IllegalStateException("Removed, but unable to re-add");
						}
					}
					if (moved != null)
						return new FlattenedElement(holder.holder, moved, true);
					else if (firstMsg == null)
						firstMsg = msg;
				}
				if (firstMsg == null || firstMsg.equals(StdMsg.UNSUPPORTED_OPERATION))
					throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
				else
					throw new IllegalArgumentException(firstMsg);
			}
		}

		@Override
		public void setValues(Collection<DerivedCollectionElement<T>> elements, T newValue)
			throws UnsupportedOperationException, IllegalArgumentException {
			if (theOptions == null) {
				// Group by flattened collection
				BetterMap<FlattenedHolder, List<DerivedCollectionElement<V>>> grouped = BetterHashMap.build().identity().build();
				for (DerivedCollectionElement<T> el : elements)
					grouped.computeIfAbsent(((FlattenedElement) el).theHolder, h -> new ArrayList<>())
					.add((DerivedCollectionElement<V>) ((FlattenedElement) el).theParentEl);

				for (Map.Entry<FlattenedHolder, List<DerivedCollectionElement<V>>> entry : grouped.entrySet())
					((ActiveCollectionManager<?, ?, V>) entry.getKey().manager).setValues(entry.getValue(), (V) newValue);
			} else if (theOptions.getReverse() == null || !theOptions.getReverse().isStateful()) {
				// Group by flattened collection
				BetterMap<FlattenedHolder, List<FlattenedElement>> grouped = BetterHashMap.build().identity().build();
				for (DerivedCollectionElement<T> el : elements)
					grouped.computeIfAbsent(((FlattenedElement) el).theHolder, h -> new ArrayList<>()).add((FlattenedElement) el);

				for (Map.Entry<FlattenedHolder, List<FlattenedElement>> entry : grouped.entrySet()) {
					if (theOptions.isCached()) {
						V oldValue = null;
						boolean first = true, allUpdates = true, allIdenticalUpdates = true;
						for (FlattenedElement el : entry.getValue()) {
							allUpdates &= equivalence().elementEquals(el.get(), newValue);
							allIdenticalUpdates &= allUpdates;
							if (!allUpdates)
								break;
							if (allIdenticalUpdates) {
								V elOldValue = el.theCacheHandler.getSourceCache();
								if (first) {
									oldValue = elOldValue;
									first = false;
								} else
									allIdenticalUpdates &= ((ActiveCollectionManager<?, ?, V>) entry.getKey().manager).equivalence()
									.elementEquals(oldValue, elOldValue);
							}
						}
						if (allIdenticalUpdates) {
							((ActiveCollectionManager<?, ?, V>) entry.getKey().manager).setValues(//
								BetterList.of(entry.getValue().stream().map(el -> (DerivedCollectionElement<V>) el.theParentEl)), oldValue);
							return;
						} else if (allUpdates) {
							for (DerivedCollectionElement<T> el : elements) {
								FlattenedElement flatEl = (FlattenedManager<E, I, V, T>.FlattenedElement) el;
								((DerivedCollectionElement<V>) flatEl.theParentEl).set(flatEl.theCacheHandler.getSourceCache());
							}
							return;
						}
					}
					if (theOptions.getReverse() != null) {
						I holderValue = entry.getKey().getValue();
						FlatMapOptions.FlatMapReverseQueryResult<I, V> result = theOptions.getReverse().reverse(//
							() -> holderValue, null, newValue);
						if (StdMsg.UNSUPPORTED_OPERATION.equals(result.getError()))
							throw new UnsupportedOperationException(result.getError());
						else if (result.getError() != null)
							throw new IllegalArgumentException(result.getError());
						if (result.replaceSecondary()) {
							V reversed = result.getSecondary();
							T reMapped = theOptions.map(holderValue, reversed, newValue);
							if (!equivalence().elementEquals(reMapped, newValue))
								throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
							((ActiveCollectionManager<?, ?, V>) entry.getKey().manager).setValues(//
								BetterList.of(entry.getValue().stream().map(el -> (DerivedCollectionElement<V>) el.theParentEl)), reversed);
						}
						if (result.replaceSource())
							entry.getKey().theParentEl.set(result.getSource());
					} else
						throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
				}
			} else {
				// Since the reversal depends on the previous value of each individual element here, we can't really do anything in bulk
				for (DerivedCollectionElement<T> element : elements) {
					if (((FlattenedElement) element).theElementId.isPresent())
						element.set(newValue);
				}
			}
		}

		@Override
		public void begin(boolean fromStart, ElementAccepter<T> onElement, WeakListening listening) {
			theAccepter = onElement;
			theListening = listening;
			boolean[] init = new boolean[] { true }; // Only honor fromStart for the initial collections
			theParent.begin(fromStart, (parentEl, cause) -> {
				FlattenedHolder holder = new FlattenedHolder(parentEl, listening, cause, !init[0] || fromStart);
				holder.holderElement = theOuterElements.addElement(holder, false).getElementId();
			}, listening);
			init[0] = false;
		}

		private class FlattenedHolder implements Transactable {
			private final DerivedCollectionElement<I> theParentEl;
			private final BetterTreeSet<FlattenedElement> theElements;
			private final WeakListening.Builder theChildListening = theListening.child();
			private final boolean isFromStart;
			ElementId holderElement;
			private CollectionDataFlow<?, ?, ? extends V> theFlow;
			ActiveCollectionManager<?, ?, ? extends V> manager;
			private final XformOptions.XformCacheHandler<I, Void> theCacheHandler;

			FlattenedHolder(DerivedCollectionElement<I> parentEl, WeakListening listening, Object cause, boolean fromStart) {
				theParentEl = parentEl;
				theElements = BetterTreeSet.<FlattenedElement> buildTreeSet(FlattenedElement::compareTo).build();
				isFromStart = fromStart;
				theCacheHandler = theOptions == null ? null
					: theOptions.createCacheHandler(new XformOptions.XformCacheHandlingInterface<I, Void>() {
						@Override
						public BiFunction<? super I, ? super Void, ? extends Void> map() {
							return (v, o) -> null;
						}

						@Override
						public Transaction lock() {
							// Should not be called, though
							return Lockable.lockable(manager, false, null).lock();
						}

						@Override
						public Void getDestCache() {
							return null;
						}

						@Override
						public void setDestCache(Void value) {}
					});
				if (theCacheHandler != null)
					theCacheHandler.initialize(theParentEl::get);
				updated(null, getValue(), cause, false);
				theParentEl.setListener(new CollectionElementListener<I>() {
					@Override
					public void update(I oldValue, I newValue, Object innerCause, boolean internalOnly) {
						updated(oldValue, newValue, innerCause, internalOnly);
					}

					@Override
					public void removed(I value, Object innerCause) {
						try (Transaction parentT = theParent.lock(false, null); Transaction innerT = lockLocal()) {
							clearSubElements(innerCause);
							theOuterElements.mutableElement(holderElement).remove();
						}
					}
				});
			}

			I getValue() {
				if (theOptions != null && theOptions.isCached())
					return theCacheHandler.getSourceCache();
				else
					return theParentEl.get();
			}

			void updated(I oldValue, I newValue, Object cause, boolean internalOnly) {
				try (Transaction parentT = theParent.lock(false, null); Transaction t = lockLocal()) {
					if (internalOnly) {
						if (manager == null)
							return;
						try (Transaction flatT = manager.lock(false, null)) {
							for (FlattenedElement flatEl : theElements) {
								V elValue = flatEl.getParentValue();
								flatEl.valueUpdated(elValue, elValue, cause, true);
							}
						}
						return;
					}
					CollectionDataFlow<?, ?, ? extends V> newFlow = theMap.apply(newValue);
					if (manager == null || !Objects.equals(theFlow, newFlow)) {
						ActiveCollectionManager<?, ?, ? extends V> newManager = newFlow.manageActive();
						clearSubElements(cause);
						theFlow = newFlow;
						manager = newManager;
						manager.begin(isFromStart, (childEl, innerCause) -> {
							try (Transaction innerParentT = theParent.lock(false, null); Transaction innerLocalT = lockLocal()) {
								FlattenedElement flatEl = new FlattenedElement(this, childEl, false);
								theAccepter.accept(flatEl, innerCause);
							}
						}, theChildListening.getListening());
					} else if (theOptions != null) {
						I oldSource = theOptions.isCached() ? theCacheHandler.getSourceCache() : oldValue;
						Ternian update = theCacheHandler.isSourceUpdate(oldValue, newValue);
						if (update == Ternian.NONE)
							return; // No change, no event
						try (Transaction flatT = manager.lock(false, null)) {
							for (FlattenedElement flatEl : theElements)
								flatEl.sourceUpdated(oldSource, newValue, update.value, cause, false);
						}
					}
				}
			}

			void clearSubElements(Object cause) {
				if (manager == null)
					return;
				try (Transaction t = manager.lock(true, cause)) {
					theChildListening.unsubscribe(); // unsubscribe here removes all elements
					manager = null;
				}
			}

			@Override
			public ThreadConstraint getThreadConstraint() {
				return manager.getThreadConstraint();
			}

			@Override
			public Transaction lock(boolean write, Object cause) {
				Transaction local = lockLocal();
				Transaction flowLock = manager.lock(write, cause);
				return () -> {
					flowLock.close();
					local.close();
				};
			}

			@Override
			public Transaction tryLock(boolean write, Object cause) {
				Transaction local = lockLocal();
				Transaction flowLock = manager.tryLock(write, cause);
				if (flowLock == null) {
					local.close();
					return null;
				}
				return () -> {
					flowLock.close();
					local.close();
				};
			}

			@Override
			public CoreId getCoreId() {
				return new CoreId(theLock).and(manager.getCoreId());
			}

			@Override
			public String toString() {
				return theParentEl.toString();
			}
		}

		FlattenedElement priorityUpdateReceiver;
		boolean wasPriorityNotified;

		private class FlattenedElement implements DerivedCollectionElement<T> {
			private final FlattenedHolder theHolder;
			private DerivedCollectionElement<? extends V> theParentEl;
			private final ElementId theElementId;
			private final XformOptions.XformCacheHandler<V, T> theCacheHandler;
			private CollectionElementListener<T> theListener;
			private T theDestCache;

			<X extends V> FlattenedElement(FlattenedHolder holder, DerivedCollectionElement<X> parentEl, boolean synthetic) {
				theHolder = holder;
				theParentEl = parentEl;
				if (!synthetic) {
					theElementId = theHolder.theElements.addElement(this, false).getElementId();
					theCacheHandler = theOptions == null ? null
						: theOptions.createCacheHandler(new XformOptions.XformCacheHandlingInterface<V, T>() {
							@Override
							public BiFunction<? super V, ? super T, ? extends T> map() {
								return (v, preValue) -> theOptions.map(theHolder.getValue(), v, preValue);
							}

							@Override
							public Transaction lock() {
								// No need to lock, as modifications only come from one source
								return Transaction.NONE;
							}

							@Override
							public T getDestCache() {
								return theDestCache;
							}

							@Override
							public void setDestCache(T value) {
								theDestCache = value;
							}
						});
					if (theCacheHandler != null)
						theCacheHandler.initialize(parentEl::get);
					installListener(parentEl);
				} else {
					theCacheHandler = null;
					theElementId = null;
				}
			}

			<X extends V> void installListener(DerivedCollectionElement<X> parentEl) {
				parentEl.setListener(new CollectionElementListener<X>() {
					@Override
					public void update(X oldValue, X newValue, Object cause, boolean internalOnly) {
						// Need to make sure that the flattened collection isn't firing at the same time as the child collection
						try (Transaction parentT = Lockable.lockAll(Lockable.lockable(theParent),
							() -> Arrays.asList(Lockable.lockable(theHolder)), LambdaUtils.identity())) {
							if (internalOnly) {
								valueUpdated(oldValue, newValue, cause, true);
								return;
							}
							if (!wasPriorityNotified && priorityUpdateReceiver != null) {
								wasPriorityNotified = true;
								priorityUpdateReceiver.valueUpdated(oldValue, newValue, cause, false);
							}
							if (priorityUpdateReceiver != FlattenedElement.this && theElementId.isPresent())
								valueUpdated(oldValue, newValue, cause, false);
						}
					}

					@Override
					public void removed(X value, Object cause) {
						theHolder.theElements.mutableElement(theElementId).remove();
						// Need to make sure that the flattened collection isn't firing at the same time as the child collection
						try (Transaction parentT = theParent.lock(false, null); Transaction localT = lockLocal()) {
							T val;
							if (theOptions == null)
								val = (T) value;
							else if (theOptions.isCached())
								val = theDestCache;
							else
								val = theOptions.map(theHolder.getValue(), value, null);
							ObservableCollectionActiveManagers.removed(theListener, val, cause);
							theListener = null;
						}
					}
				});
			}

			void sourceUpdated(I oldSource, I newSource, boolean update, Object cause, boolean internalOnly) {
				if (theListener == null)
					return;
				// This is not called without options
				T oldValue = theOptions.isCached() ? theDestCache : theOptions.map(oldSource, theParentEl.get(), null);
				T newValue;
				if (update && !theOptions.isReEvalOnUpdate())
					newValue = theDestCache;
				else if (theOptions.isCached())
					newValue = theOptions.map(newSource, theCacheHandler.getSourceCache(), theDestCache);
				else
					newValue = theOptions.map(newSource, theParentEl.get(), null);
				ObservableCollectionActiveManagers.update(theListener, oldValue, newValue, cause, internalOnly);
			}

			void valueUpdated(V oldValue, V newValue, Object cause, boolean internalOnly) {
				if (internalOnly || theOptions == null) {
					ObservableCollectionActiveManagers.update(theListener, (T) oldValue, (T) newValue, cause, internalOnly);
					return;
				}
				BiTuple<T, T> values = theCacheHandler.handleSourceChange(oldValue, newValue);
				if (values != null)
					ObservableCollectionActiveManagers.update(theListener, values.getValue1(), values.getValue2(), cause, false);
			}

			@Override
			public int compareTo(DerivedCollectionElement<T> o) {
				FlattenedElement flat = (FlattenedElement) o;
				int comp = theHolder.theParentEl.compareTo(flat.theHolder.theParentEl);
				if (comp == 0)
					comp = ((DerivedCollectionElement<T>) theParentEl).compareTo((DerivedCollectionElement<T>) flat.theParentEl);
				return comp;
			}

			@Override
			public void setListener(CollectionElementListener<T> listener) {
				theListener = listener;
			}

			V getParentValue() {
				if (theOptions != null && theOptions.isCached())
					return theCacheHandler.getSourceCache();
				else
					return theParentEl.get();
			}

			@Override
			public T get() {
				if (theOptions == null)
					return (T) theParentEl.get();
				else if (theOptions.isCached())
					return theDestCache;
				else
					return theOptions.map(theHolder.getValue(), theParentEl.get(), null);
			}

			@Override
			public String isEnabled() {
				return theParentEl.isEnabled();
			}

			@Override
			public String isAcceptable(T value) {
				if (value != null && !TypeTokens.get().isInstance(theHolder.manager.getTargetType(), value))
					return StdMsg.BAD_TYPE;
				String msg = null;
				FlatMapOptions.FlatMapReverseQueryResult<I, V> result;
				if (theOptions == null)
					result = FlatMapOptions.FlatMapReverseQueryResult.value((V) value);
				else if (theOptions.isCached() && equivalence().elementEquals(theDestCache, value))
					result = FlatMapOptions.FlatMapReverseQueryResult.value(theCacheHandler.getSourceCache());
				else {
					if (theOptions.getReverse() == null)
						return StdMsg.UNSUPPORTED_OPERATION;
					result = theOptions.getReverse()//
						.canReverse(//
							theHolder::getValue, this::getParentValue, value);
				}
				if (result.getError() != null)
					return result.getError();
				if (result.replaceSecondary()) {
					V reversed = result.getSecondary();
					if (!TypeTokens.get().isInstance(theHolder.manager.getTargetType(), reversed)
						|| !theHolder.manager.equivalence().isElement(reversed))
						return StdMsg.ILLEGAL_ELEMENT;
					if (theOptions != null) {
						T reMapped = theOptions.map(theHolder.getValue(), reversed, value);
						if (!equivalence().elementEquals(reMapped, value))
							return StdMsg.ILLEGAL_ELEMENT;
					}
					if ((theOptions == null || theOptions.isPropagatingUpdatesToParent()
						|| !((ActiveCollectionManager<?, ?, V>) theHolder.manager).equivalence().elementEquals(getParentValue(), reversed)))
						msg = ((DerivedCollectionElement<V>) theParentEl).isAcceptable(reversed);
				}
				if (msg == null && result.replaceSource()) {
					I reversed = result.getSource();
					if (!TypeTokens.get().isInstance(theParent.getTargetType(), reversed) || !theParent.equivalence().isElement(reversed))
						return StdMsg.ILLEGAL_ELEMENT;
					CollectionDataFlow<?, ?, ? extends V> newFlow = theMap.apply(reversed);
					if (!theHolder.theFlow.equals(newFlow))
						return StdMsg.ILLEGAL_ELEMENT;
					if ((theOptions == null || theOptions.isPropagatingUpdatesToParent()
						|| !theParent.equivalence().elementEquals(theHolder.getValue(), reversed)))
						msg = theHolder.theParentEl.isAcceptable(reversed);
				}
				return msg;
			}

			@Override
			public void set(T value) throws UnsupportedOperationException, IllegalArgumentException {
				if (value != null && !TypeTokens.get().isInstance(theHolder.manager.getTargetType(), value))
					throw new IllegalArgumentException(StdMsg.BAD_TYPE);
				try (Transaction t = FlattenedManager.this.lock(true, null)) {
					FlatMapOptions.FlatMapReverseQueryResult<I, V> result;
					if (theOptions == null)
						result = FlatMapOptions.FlatMapReverseQueryResult.value((V) value);
					else if (theOptions.isCached() && equivalence().elementEquals(theDestCache, value))
						result = FlatMapOptions.FlatMapReverseQueryResult.value(theCacheHandler.getSourceCache());
					else {
						if (theOptions.getReverse() == null)
							throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
						result = theOptions.getReverse()//
							.reverse(//
								theHolder::getValue, this::getParentValue, value);
					}
					if (StdMsg.UNSUPPORTED_OPERATION.equals(result.getError()))
						throw new UnsupportedOperationException(result.getError());
					else if (result.getError() != null)
						throw new IllegalArgumentException(result.getError());
					if (result.replaceSecondary()) {
						V reversed = result.getSecondary();
						if (!TypeTokens.get().isInstance(theHolder.manager.getTargetType(), reversed)
							|| !theHolder.manager.equivalence().isElement(reversed))
							throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
						if (theOptions != null) {
							T reMapped = theOptions.map(theHolder.getValue(), reversed, value);
							if (!equivalence().elementEquals(reMapped, value))
								throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
						}
						if ((theOptions == null || theOptions.isPropagatingUpdatesToParent()
							|| !((ActiveCollectionManager<?, ?, V>) theHolder.manager).equivalence().elementEquals(getParentValue(),
								reversed))) {
							// The purpose of this code wrapped around the set call
							// is to ensure that this method's listener gets called first.
							// This prevents any possible re-arranging or other operations that could result
							// in this set operation removing this element
							priorityUpdateReceiver = this;
							wasPriorityNotified = false;
							try {
								((DerivedCollectionElement<V>) theParentEl).set(reversed);
							} finally {
								priorityUpdateReceiver = null;
							}
						}
					}
					if (result.replaceSource()) {
						I reversed = result.getSource();
						if (!TypeTokens.get().isInstance(theParent.getTargetType(), reversed)
							|| !theParent.equivalence().isElement(reversed))
							throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
						CollectionDataFlow<?, ?, ? extends V> newFlow = theMap.apply(reversed);
						if (!theHolder.theFlow.equals(newFlow))
							throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
						if ((theOptions == null || theOptions.isPropagatingUpdatesToParent()
							|| !theParent.equivalence().elementEquals(theHolder.getValue(), reversed)))
							theHolder.theParentEl.set(reversed);
					}
				}
			}

			@Override
			public String canRemove() {
				return theParentEl.canRemove();
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				if (theElementId.isPresent())
					theParentEl.remove();
			}

			@Override
			public String toString() {
				StringBuilder str = new StringBuilder().append(theHolder.theParentEl).append('/').append(theParentEl);
				if (theOptions != null && theOptions.isCached())
					str.append('=').append(theDestCache);
				return str.toString();
			}
		}
	}
}
