package org.observe.collect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.observe.Observable;
import org.observe.Subscription;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.collect.ObservableCollectionActiveManagers.ActiveCollectionManager;
import org.observe.collect.ObservableCollectionDataFlowImpl.ModFilterer;
import org.observe.util.TypeTokens;
import org.observe.util.WeakListening;
import org.qommons.Identifiable;
import org.qommons.Lockable;
import org.qommons.QommonsUtils;
import org.qommons.Transaction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterHashMap;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterMap;
import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
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

	static class IntersectionManager<E, T, X> implements ObservableCollectionActiveManagers.ActiveCollectionManager<E, T, T> {
		private class IntersectionElement {
			private final T value;
			final List<IntersectedCollectionElement> leftElements;
			final List<ElementId> rightElements;

			IntersectionElement(T value) {
				this.value = value;
				leftElements = new ArrayList<>();
				rightElements = new ArrayList<>();
			}

			boolean isPresent() {
				return rightElements.isEmpty() == isExclude;
			}

			void incrementRight(ElementId rightEl, Object cause) {
				boolean preEmpty = rightElements.isEmpty();
				rightElements.add(rightEl);
				if (preEmpty)
					presentChanged(cause);
			}

			void decrementRight(ElementId rightEl, Object cause) {
				rightElements.remove(rightEl);
				if (rightElements.isEmpty()) {
					presentChanged(cause);
					if (leftElements.isEmpty())
						theValues.remove(value);
				}
			}

			void addLeft(IntersectedCollectionElement element) {
				leftElements.add(element);
			}

			void removeLeft(IntersectedCollectionElement element) {
				leftElements.remove(element);
				if (leftElements.isEmpty() && rightElements.isEmpty())
					theValues.remove(value);
			}

			private void presentChanged(Object cause) {
				if (isPresent()) {
					for (IntersectedCollectionElement el : leftElements)
						theAccepter.accept(el, cause);
				} else {
					for (IntersectedCollectionElement el : leftElements)
						el.fireRemove(cause);
				}
			}
		}

		class IntersectedCollectionElement implements ObservableCollectionActiveManagers.DerivedCollectionElement<T> {
			private final ObservableCollectionActiveManagers.DerivedCollectionElement<T> theParentEl;
			private IntersectionElement intersection;
			private ObservableCollectionActiveManagers.CollectionElementListener<T> theListener;

			IntersectedCollectionElement(ObservableCollectionActiveManagers.DerivedCollectionElement<T> parentEl,
				IntersectionElement intersect, boolean synthetic) {
				theParentEl = parentEl;
				intersection = intersect;
				if (!synthetic)
					theParentEl.setListener(new ObservableCollectionActiveManagers.CollectionElementListener<T>() {
						@Override
						public void update(T oldValue, T newValue, Object cause) {
							if (theEquivalence.elementEquals(intersection.value, newValue)) {
								ObservableCollectionActiveManagers.update(theListener, oldValue, newValue, cause);
							} else {
								boolean oldPresent = intersection.isPresent();
								intersection.removeLeft(IntersectedCollectionElement.this);
								intersection = theValues.computeIfAbsent(newValue, v -> new IntersectionElement(newValue));
								intersection.addLeft(IntersectedCollectionElement.this);
								boolean newPresent = intersection.isPresent();
								if (oldPresent && newPresent)
									ObservableCollectionActiveManagers.update(theListener, oldValue, newValue, cause);
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
							if (intersection.isPresent())
								ObservableCollectionActiveManagers.removed(theListener, value, cause);
							intersection.removeLeft(IntersectedCollectionElement.this);
							intersection = null;
						}
					});
			}

			void fireRemove(Object cause) {
				ObservableCollectionActiveManagers.removed(theListener, theParentEl.get(), cause);
				theListener = null;
			}

			@Override
			public int compareTo(ObservableCollectionActiveManagers.DerivedCollectionElement<T> o) {
				return theParentEl.compareTo(((IntersectedCollectionElement) o).theParentEl);
			}

			@Override
			public void setListener(ObservableCollectionActiveManagers.CollectionElementListener<T> listener) {
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
				String msg = theParentEl.isAcceptable(value);
				if (msg != null)
					return msg;
				if (theEquivalence.elementEquals(theParentEl.get(), value))
					return null;
				IntersectionElement intersect = theValues.get(value);

				boolean filterHas = intersect == null ? false : !intersect.rightElements.isEmpty();
				if (filterHas == isExclude)
					return StdMsg.ILLEGAL_ELEMENT;
				return null;
			}

			@Override
			public void set(T value) throws UnsupportedOperationException, IllegalArgumentException {
				if (!theEquivalence.elementEquals(theParentEl.get(), value)) {
					IntersectionElement intersect = theValues.get(value);
					boolean filterHas = intersect == null ? false : !intersect.rightElements.isEmpty();
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

		private final ObservableCollectionActiveManagers.ActiveCollectionManager<E, ?, T> theParent;
		private final ObservableCollection<X> theFilter;
		private final Equivalence<? super T> theEquivalence; // Make this a field since we'll need it often
		/** Whether a value's presence in the right causes the value in the left to be present (false) or absent (true) in the result */
		private final boolean isExclude;
		private Map<T, IntersectionElement> theValues;
		// The following two fields are needed because the values may mutate
		private Map<ElementId, IntersectionElement> theRightElementValues;

		private ObservableCollectionActiveManagers.ElementAccepter<T> theAccepter;

		IntersectionManager(ObservableCollectionActiveManagers.ActiveCollectionManager<E, ?, T> parent, CollectionDataFlow<?, ?, X> filter, boolean exclude) {
			theParent = parent;
			theFilter = filter.collect();
			theEquivalence = parent.equivalence();
			isExclude = exclude;
			theValues = theEquivalence.createMap();
		}

		@Override
		public Object getIdentity() {
			return Identifiable.wrap(theParent.getIdentity(), isExclude ? "without" : "intersect", theFilter);
		}

		@Override
		public TypeToken<T> getTargetType() {
			return theParent.getTargetType();
		}

		@Override
		public boolean isLockSupported() {
			return theParent.isLockSupported() || theFilter.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return Lockable.lockAll(Lockable.lockable(ObservableCollectionActiveManagers.structureAffectedPassLockThroughToParent(theParent), write, cause),
				Lockable.lockable(theFilter));
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return Lockable.tryLockAll(Lockable.lockable(ObservableCollectionActiveManagers.structureAffectedPassLockThroughToParent(theParent), write, cause),
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
		public Comparable<ObservableCollectionActiveManagers.DerivedCollectionElement<T>> getElementFinder(T value) {
			Comparable<ObservableCollectionActiveManagers.DerivedCollectionElement<T>> parentFinder = theParent.getElementFinder(value);
			if (parentFinder == null)
				return null;
			return el -> parentFinder.compareTo(((IntersectedCollectionElement) el).theParentEl);
		}

		@Override
		public BetterList<ObservableCollectionActiveManagers.DerivedCollectionElement<T>> getElementsBySource(ElementId sourceEl,
			BetterCollection<?> sourceCollection) {
			return BetterList.of(Stream.concat(//
				theParent.getElementsBySource(sourceEl, sourceCollection).stream()
				.map(el -> new IntersectedCollectionElement(el, null, true)), //
				theFilter.getElementsBySource(sourceEl, sourceCollection).stream().flatMap(el -> {
					IntersectionElement intEl = theRightElementValues.get(el.getElementId());
					return intEl == null ? Stream.empty() : intEl.leftElements.stream();
				}).distinct()));
		}

		@Override
		public BetterList<ElementId> getSourceElements(ObservableCollectionActiveManagers.DerivedCollectionElement<T> localElement,
			BetterCollection<?> sourceCollection) {
			return BetterList.of(Stream.concat(//
				theParent.getSourceElements(((IntersectedCollectionElement) localElement).theParentEl, sourceCollection).stream(), //
				((IntersectedCollectionElement) localElement).intersection.rightElements.stream().flatMap(//
					el -> theFilter.getSourceElements(el, sourceCollection).stream())//
				));
		}

		@Override
		public String canAdd(T toAdd, ObservableCollectionActiveManagers.DerivedCollectionElement<T> after, ObservableCollectionActiveManagers.DerivedCollectionElement<T> before) {
			IntersectionElement intersect = theValues.get(toAdd);
			boolean filterHas = intersect == null ? false : !intersect.rightElements.isEmpty();
			if (filterHas == isExclude)
				return StdMsg.ILLEGAL_ELEMENT;
			return theParent.canAdd(toAdd, strip(after), strip(before));
		}

		@Override
		public ObservableCollectionActiveManagers.DerivedCollectionElement<T> addElement(T value, ObservableCollectionActiveManagers.DerivedCollectionElement<T> after, ObservableCollectionActiveManagers.DerivedCollectionElement<T> before,
			boolean first) {
			IntersectionElement intersect = theValues.get(value);
			boolean filterHas = intersect == null ? false : !intersect.rightElements.isEmpty();
			if (filterHas == isExclude)
				throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
			ObservableCollectionActiveManagers.DerivedCollectionElement<T> parentEl = theParent.addElement(value, strip(after),
				strip(before), first);
			return parentEl == null ? null : new IntersectedCollectionElement(parentEl, null, true);
		}

		@Override
		public String canMove(ObservableCollectionActiveManagers.DerivedCollectionElement<T> valueEl, ObservableCollectionActiveManagers.DerivedCollectionElement<T> after, ObservableCollectionActiveManagers.DerivedCollectionElement<T> before) {
			return theParent.canMove(strip(valueEl), strip(after), strip(before));
		}

		@Override
		public ObservableCollectionActiveManagers.DerivedCollectionElement<T> move(ObservableCollectionActiveManagers.DerivedCollectionElement<T> valueEl, ObservableCollectionActiveManagers.DerivedCollectionElement<T> after,
			ObservableCollectionActiveManagers.DerivedCollectionElement<T> before, boolean first, Runnable afterRemove) {
			return new IntersectedCollectionElement(theParent.move(strip(valueEl), strip(after), strip(before), first, afterRemove), null,
				true);
		}

		private ObservableCollectionActiveManagers.DerivedCollectionElement<T> strip(ObservableCollectionActiveManagers.DerivedCollectionElement<T> el) {
			return el == null ? null : ((IntersectedCollectionElement) el).theParentEl;
		}

		@Override
		public Equivalence<? super T> equivalence() {
			return theEquivalence;
		}

		@Override
		public void setValues(Collection<ObservableCollectionActiveManagers.DerivedCollectionElement<T>> elements, T newValue)
			throws UnsupportedOperationException, IllegalArgumentException {
			IntersectionElement intersect = theValues.get(newValue);
			boolean filterHas = intersect == null ? false : !intersect.rightElements.isEmpty();
			if (filterHas == isExclude)
				throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
			theParent.setValues(elements.stream().map(el -> ((IntersectedCollectionElement) el).theParentEl).collect(Collectors.toList()),
				newValue);
		}

		@Override
		public void begin(boolean fromStart, ObservableCollectionActiveManagers.ElementAccepter<T> onElement, WeakListening listening) {
			listening.withConsumer((ObservableCollectionEvent<? extends X> evt) -> {
				// We're not modifying, but we want to obtain an exclusive lock
				// to ensure that nothing above or below us is firing events at the same time.
				try (Transaction t = theParent.lock(true, evt)) {
					IntersectionElement element;
					switch (evt.getType()) {
					case add:
						if (!theEquivalence.isElement(evt.getNewValue()))
							return;
						element = theValues.computeIfAbsent((T) evt.getNewValue(), v -> new IntersectionElement(v));
						element.incrementRight(evt.getElementId(), evt);
						theRightElementValues.put(evt.getElementId(), element);
						break;
					case remove:
						element = theRightElementValues.remove(evt.getElementId());
						if (element == null)
							return; // Must not have belonged to the flow's equivalence
						element.decrementRight(evt.getElementId(), evt);
						break;
					case set:
						element = theRightElementValues.get(evt.getElementId());
						if (element != null && theEquivalence.elementEquals(element.value, evt.getNewValue()))
							return; // No change;
						boolean newIsElement = equivalence().isElement(evt.getNewValue());
						if (element != null) {
							theRightElementValues.remove(evt.getElementId());
							element.decrementRight(evt.getElementId(), evt);
						}
						if (newIsElement) {
							element = theValues.computeIfAbsent((T) evt.getNewValue(), v -> new IntersectionElement(v));
							element.incrementRight(evt.getElementId(), evt);
							theRightElementValues.put(evt.getElementId(), element);
						}
						break;
					}
				}
			}, action -> theFilter.subscribe(action, fromStart).removeAll());
			theParent.begin(fromStart, (parentEl, cause) -> {
				IntersectionElement element = theValues.computeIfAbsent(parentEl.get(), v -> new IntersectionElement(v));
				IntersectedCollectionElement el = new IntersectedCollectionElement(parentEl, element, false);
				element.addLeft(el);
				if (element.isPresent())
					onElement.accept(el, cause);
			}, listening);
		}
	}

	static class ActiveModFilteredManager<E, T> implements ObservableCollectionActiveManagers.ActiveCollectionManager<E, T, T> {
		private final ObservableCollectionActiveManagers.ActiveCollectionManager<E, ?, T> theParent;

		private final ModFilterer<T> theFilter;

		ActiveModFilteredManager(ObservableCollectionActiveManagers.ActiveCollectionManager<E, ?, T> parent, ModFilterer<T> filter) {
			theParent = parent;
			theFilter = filter;
		}

		@Override
		public Object getIdentity() {
			return theParent.getIdentity();
		}

		@Override
		public TypeToken<T> getTargetType() {
			return theParent.getTargetType();
		}

		@Override
		public Equivalence<? super T> equivalence() {
			return theParent.equivalence();
		}

		@Override
		public boolean isLockSupported() {
			return theParent.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theParent.lock(write, cause);
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return theParent.tryLock(write, cause);
		}

		@Override
		public boolean clear() {
			if (!theFilter.isRemoveFiltered() && theFilter.getUnmodifiableMessage() == null)
				return theParent.clear();
			if (theFilter.getUnmodifiableMessage() != null || theFilter.getRemoveMessage() != null)
				return true;
			else
				return false;
		}

		@Override
		public boolean isContentControlled() {
			return !theFilter.isEmpty() || theParent.isContentControlled();
		}

		@Override
		public Comparable<ObservableCollectionActiveManagers.DerivedCollectionElement<T>> getElementFinder(T value) {
			Comparable<ObservableCollectionActiveManagers.DerivedCollectionElement<T>> parentFinder = theParent.getElementFinder(value);
			if (parentFinder == null)
				return null;
			return el -> parentFinder.compareTo(((ModFilteredElement) el).theParentEl);
		}

		@Override
		public BetterList<ObservableCollectionActiveManagers.DerivedCollectionElement<T>> getElementsBySource(ElementId sourceEl,
			BetterCollection<?> sourceCollection) {
			return QommonsUtils.map2(theParent.getElementsBySource(sourceEl, sourceCollection), el -> new ModFilteredElement(el));
		}

		@Override
		public BetterList<ElementId> getSourceElements(ObservableCollectionActiveManagers.DerivedCollectionElement<T> localElement,
			BetterCollection<?> sourceCollection) {
			return theParent.getSourceElements(((ModFilteredElement) localElement).theParentEl, sourceCollection);
		}

		@Override
		public String canAdd(T toAdd, ObservableCollectionActiveManagers.DerivedCollectionElement<T> after,
			ObservableCollectionActiveManagers.DerivedCollectionElement<T> before) {
			String msg = theFilter.canAdd(toAdd);
			if (msg == null)
				msg = theParent.canAdd(toAdd, strip(after), strip(before));
			return msg;
		}

		@Override
		public ObservableCollectionActiveManagers.DerivedCollectionElement<T> addElement(T value,
			ObservableCollectionActiveManagers.DerivedCollectionElement<T> after,
			ObservableCollectionActiveManagers.DerivedCollectionElement<T> before, boolean first) {
			theFilter.assertAdd(value);
			ObservableCollectionActiveManagers.DerivedCollectionElement<T> parentEl = theParent.addElement(value, strip(after),
				strip(before), first);
			return parentEl == null ? null : new ModFilteredElement(parentEl);
		}

		@Override
		public String canMove(ObservableCollectionActiveManagers.DerivedCollectionElement<T> valueEl,
			ObservableCollectionActiveManagers.DerivedCollectionElement<T> after,
			ObservableCollectionActiveManagers.DerivedCollectionElement<T> before) {
			String msg = theFilter.canMove();
			if (msg == null)
				return theParent.canMove(//
					strip(valueEl), strip(after), strip(before));
			else if ((after == null || valueEl.compareTo(after) >= 0) && (before == null || valueEl.compareTo(before) <= 0))
				return null;
			return msg;
		}

		@Override
		public ObservableCollectionActiveManagers.DerivedCollectionElement<T> move(
			ObservableCollectionActiveManagers.DerivedCollectionElement<T> valueEl,
			ObservableCollectionActiveManagers.DerivedCollectionElement<T> after,
			ObservableCollectionActiveManagers.DerivedCollectionElement<T> before, boolean first, Runnable afterRemove) {
			if (theFilter.canMove() != null && (after == null || valueEl.compareTo(after) >= 0)
				&& (before == null || valueEl.compareTo(before) <= 0))
				return valueEl;
			theFilter.assertMovable();
			return new ModFilteredElement(theParent.move(//
				strip(valueEl), strip(after), strip(before), first, afterRemove));
		}

		private ObservableCollectionActiveManagers.DerivedCollectionElement<T> strip(
			ObservableCollectionActiveManagers.DerivedCollectionElement<T> el) {
			return el == null ? null : ((ModFilteredElement) el).theParentEl;
		}

		@Override
		public void setValues(Collection<ObservableCollectionActiveManagers.DerivedCollectionElement<T>> elements, T newValue)
			throws UnsupportedOperationException, IllegalArgumentException {
			for (ObservableCollectionActiveManagers.DerivedCollectionElement<T> el : elements)
				theFilter.assertSet(newValue, el::get);
			theParent.setValues(//
				elements.stream().map(el -> ((ModFilteredElement) el).theParentEl).collect(Collectors.toList()), newValue);
		}

		@Override
		public void begin(boolean fromStart, ObservableCollectionActiveManagers.ElementAccepter<T> onElement, WeakListening listening) {
			theParent.begin(fromStart, (parentEl, cause) -> onElement.accept(new ModFilteredElement(parentEl), cause), listening);
		}

		private class ModFilteredElement implements ObservableCollectionActiveManagers.DerivedCollectionElement<T> {
			final ObservableCollectionActiveManagers.DerivedCollectionElement<T> theParentEl;

			ModFilteredElement(ObservableCollectionActiveManagers.DerivedCollectionElement<T> parentEl) {
				theParentEl = parentEl;
			}

			@Override
			public int compareTo(ObservableCollectionActiveManagers.DerivedCollectionElement<T> o) {
				return theParentEl.compareTo(((ModFilteredElement) o).theParentEl);
			}

			@Override
			public void setListener(ObservableCollectionActiveManagers.CollectionElementListener<T> listener) {
				theParentEl.setListener(listener);
			}

			@Override
			public T get() {
				return theParentEl.get();
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

			@Override
			public String toString() {
				return theParentEl.toString();
			}
		}
	}

	static class ActiveRefreshingCollectionManager<E, T> implements ObservableCollectionActiveManagers.ActiveCollectionManager<E, T, T> {
		private final ObservableCollectionActiveManagers.ActiveCollectionManager<E, ?, T> theParent;
		private final Observable<?> theRefresh;
		private final BetterList<RefreshingElement> theElements;

		ActiveRefreshingCollectionManager(ObservableCollectionActiveManagers.ActiveCollectionManager<E, ?, T> parent, Observable<?> refresh) {
			theParent = parent;
			theRefresh = refresh;
			theElements = new BetterTreeList<>(false);
		}

		@Override
		public Object getIdentity() {
			return Identifiable.wrap(theParent.getIdentity(), "refresh", theRefresh.getIdentity());
		}

		@Override
		public TypeToken<T> getTargetType() {
			return theParent.getTargetType();
		}

		@Override
		public Equivalence<? super T> equivalence() {
			return theParent.equivalence();
		}

		@Override
		public boolean isLockSupported() {
			return theParent.isLockSupported() && theRefresh.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return Lockable.lockAll(Lockable.lockable(theParent, write, cause), theRefresh);
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return Lockable.tryLockAll(Lockable.lockable(theParent, write, cause), theRefresh);
		}

		@Override
		public Comparable<ObservableCollectionActiveManagers.DerivedCollectionElement<T>> getElementFinder(T value) {
			Comparable<ObservableCollectionActiveManagers.DerivedCollectionElement<T>> parentFinder = theParent.getElementFinder(value);
			if (parentFinder == null)
				return null;
			return el -> parentFinder.compareTo(((RefreshingElement) el).theParentEl);
		}

		@Override
		public BetterList<ObservableCollectionActiveManagers.DerivedCollectionElement<T>> getElementsBySource(ElementId sourceEl,
			BetterCollection<?> sourceCollection) {
			return QommonsUtils.map2(theParent.getElementsBySource(sourceEl, sourceCollection), el -> new RefreshingElement(el, true));
		}

		@Override
		public BetterList<ElementId> getSourceElements(ObservableCollectionActiveManagers.DerivedCollectionElement<T> localElement,
			BetterCollection<?> sourceCollection) {
			return theParent.getSourceElements(((RefreshingElement) localElement).theParentEl, sourceCollection);
		}

		@Override
		public String canAdd(T toAdd, ObservableCollectionActiveManagers.DerivedCollectionElement<T> after, ObservableCollectionActiveManagers.DerivedCollectionElement<T> before) {
			return theParent.canAdd(toAdd, strip(after), strip(before));
		}

		@Override
		public boolean clear() {
			return theParent.clear();
		}

		@Override
		public boolean isContentControlled() {
			return theParent.isContentControlled();
		}

		@Override
		public ObservableCollectionActiveManagers.DerivedCollectionElement<T> addElement(T value, ObservableCollectionActiveManagers.DerivedCollectionElement<T> after, ObservableCollectionActiveManagers.DerivedCollectionElement<T> before,
			boolean first) {
			ObservableCollectionActiveManagers.DerivedCollectionElement<T> parentEl = theParent.addElement(value, strip(after),
				strip(before), first);
			return parentEl == null ? null : new RefreshingElement(parentEl, true);
		}

		@Override
		public String canMove(ObservableCollectionActiveManagers.DerivedCollectionElement<T> valueEl, ObservableCollectionActiveManagers.DerivedCollectionElement<T> after, ObservableCollectionActiveManagers.DerivedCollectionElement<T> before) {
			return theParent.canMove(strip(valueEl), strip(after), strip(before));
		}

		@Override
		public ObservableCollectionActiveManagers.DerivedCollectionElement<T> move(ObservableCollectionActiveManagers.DerivedCollectionElement<T> valueEl, ObservableCollectionActiveManagers.DerivedCollectionElement<T> after,
			ObservableCollectionActiveManagers.DerivedCollectionElement<T> before, boolean first, Runnable afterRemove) {
			return new RefreshingElement(theParent.move(strip(valueEl), strip(after), strip(before), first, afterRemove), true);
		}

		private ObservableCollectionActiveManagers.DerivedCollectionElement<T> strip(ObservableCollectionActiveManagers.DerivedCollectionElement<T> el) {
			return el == null ? null : ((RefreshingElement) el).theParentEl;
		}

		@Override
		public void setValues(Collection<ObservableCollectionActiveManagers.DerivedCollectionElement<T>> elements, T newValue)
			throws UnsupportedOperationException, IllegalArgumentException {
			theParent.setValues(//
				elements.stream().map(el -> ((RefreshingElement) el).theParentEl).collect(Collectors.toList()), newValue);
		}

		@Override
		public void begin(boolean fromStart, ObservableCollectionActiveManagers.ElementAccepter<T> onElement, WeakListening listening) {
			theParent.begin(fromStart, (parentEl, cause) -> {
				// Make sure the refresh doesn't fire while we're firing notifications from the parent change
				try (Transaction t = theRefresh.lock()) {
					onElement.accept(new RefreshingElement(parentEl, false), cause);
				}
			}, listening);
			listening.withConsumer((Object r) -> {
				// Make sure the parent doesn't fire while we're firing notifications from the refresh
				try (Transaction t = theParent.lock(false, r)) {
					// Refreshing should be done in element order
					Collections.sort(theElements);
					CollectionElement<RefreshingElement> el = theElements.getTerminalElement(true);
					while (el != null) {
						// But now need to re-set the correct element ID for each element
						el.get().theElementId = el.getElementId();
						el.get().refresh(r);
						el = theElements.getAdjacentElement(el.getElementId(), true);
					}
				}
			}, theRefresh::act);
		}

		private class RefreshingElement implements ObservableCollectionActiveManagers.DerivedCollectionElement<T> {
			final ObservableCollectionActiveManagers.DerivedCollectionElement<T> theParentEl;
			private ElementId theElementId;
			private ObservableCollectionActiveManagers.CollectionElementListener<T> theListener;

			RefreshingElement(ObservableCollectionActiveManagers.DerivedCollectionElement<T> parent, boolean synthetic) {
				theParentEl = parent;
				if (!synthetic) {
					theElementId = theElements.addElement(this, false).getElementId();
					theParentEl.setListener(new ObservableCollectionActiveManagers.CollectionElementListener<T>() {
						@Override
						public void update(T oldValue, T newValue, Object cause) {
							try (Transaction t = theRefresh.lock()) {
								ObservableCollectionActiveManagers.update(theListener, oldValue, newValue, cause);
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
				ObservableCollectionActiveManagers.update(theListener, value, value, cause);
			}

			@Override
			public int compareTo(ObservableCollectionActiveManagers.DerivedCollectionElement<T> o) {
				return theParentEl.compareTo(((RefreshingElement) o).theParentEl);
			}

			@Override
			public void setListener(ObservableCollectionActiveManagers.CollectionElementListener<T> listener) {
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
				return theParentEl.isAcceptable(value);
			}

			@Override
			public void set(T value) throws UnsupportedOperationException, IllegalArgumentException {
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
			public String toString() {
				return theParentEl.toString();
			}
		}
	}

	static class ElementRefreshingCollectionManager<E, T> implements ObservableCollectionActiveManagers.ActiveCollectionManager<E, T, T> {
		private class RefreshHolder {
			private final ElementId theElementId;
			private final Subscription theSub;
			final BetterSortedSet<RefreshingElement> elements;

			RefreshHolder(Observable<?> refresh) {
				theElementId = theRefreshObservables.putEntry(refresh, this, false).getElementId();
				elements = new BetterTreeSet<>(false, RefreshingElement::compareTo);
				theSub = theListening.withConsumer(r -> {
					try (Transaction t = Lockable.lockAll(Lockable.lockable(theLock, true), Lockable.lockable(theParent, false, null))) {
						RefreshingElement setting = (RefreshingElement) theSettingElement.get();
						if (setting != null && elements.contains(setting))
							setting.refresh(r);
						for (RefreshingElement el : elements) {
							if (el != setting)
								el.refresh(r);
						}
					}
				}, action -> refresh.act(action));
			}

			void remove(ElementId element) {
				elements.mutableElement(element).remove();
				if (elements.isEmpty()) {
					theSub.unsubscribe();
					theRefreshObservables.mutableEntry(theElementId).remove();
				}
			}
		}

		private final ObservableCollectionActiveManagers.ActiveCollectionManager<E, ?, T> theParent;
		private final Function<? super T, ? extends Observable<?>> theRefresh;
		private final BetterMap<Observable<?>, RefreshHolder> theRefreshObservables;
		private WeakListening theListening;
		private final ReentrantReadWriteLock theLock;
		Supplier<ObservableCollectionActiveManagers.DerivedCollectionElement<? extends T>> theSettingElement;

		ElementRefreshingCollectionManager(ObservableCollectionActiveManagers.ActiveCollectionManager<E, ?, T> parent, Function<? super T, ? extends Observable<?>> refresh) {
			theParent = parent;
			theRefresh = refresh;
			theRefreshObservables = BetterHashMap.build().unsafe().buildMap();
			theLock = new ReentrantReadWriteLock();
		}

		void withSettingElement(Supplier<ObservableCollectionActiveManagers.DerivedCollectionElement<? extends T>> settingElement) {
			theSettingElement = settingElement;
		}

		@Override
		public Object getIdentity() {
			return Identifiable.wrap(theParent.getIdentity(), "refresh", theRefresh);
		}

		@Override
		public TypeToken<T> getTargetType() {
			return theParent.getTargetType();
		}

		@Override
		public Equivalence<? super T> equivalence() {
			return theParent.equivalence();
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
				return theParent.lock(write, cause);
			else
				return Lockable.lockAll(Lockable.lockable(theParent, write, cause), Lockable.lockable(theLock, false));
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			// The purpose of the refresh lock is solely to prevent simultaneous refresh events,
			// or any refresh events that would violate the contract of a held lock
			// If this lock method will obtain any exclusive locks, then locking the refresh lock is unnecessary,
			// because incoming refresh updates obtain a read lock on the parent
			if (write)
				return theParent.tryLock(write, cause);
			else
				return Lockable.tryLockAll(Lockable.lockable(theParent, write, cause), Lockable.lockable(theLock, false));
		}

		Transaction lockRefresh(boolean exclusive) {
			return Lockable.lock(theLock, exclusive);
		}

		@Override
		public boolean isContentControlled() {
			return theParent.isContentControlled();
		}

		@Override
		public Comparable<ObservableCollectionActiveManagers.DerivedCollectionElement<T>> getElementFinder(T value) {
			Comparable<ObservableCollectionActiveManagers.DerivedCollectionElement<T>> parentFinder = theParent.getElementFinder(value);
			if (parentFinder == null)
				return null;
			return el -> parentFinder.compareTo(((RefreshingElement) el).theParentEl);
		}

		@Override
		public BetterList<ObservableCollectionActiveManagers.DerivedCollectionElement<T>> getElementsBySource(ElementId sourceEl,
			BetterCollection<?> sourceCollection) {
			return QommonsUtils.map2(theParent.getElementsBySource(sourceEl, sourceCollection), el -> new RefreshingElement(el));
		}

		@Override
		public BetterList<ElementId> getSourceElements(ObservableCollectionActiveManagers.DerivedCollectionElement<T> localElement,
			BetterCollection<?> sourceCollection) {
			return theParent.getSourceElements(((RefreshingElement) localElement).theParentEl, sourceCollection);
		}

		@Override
		public String canAdd(T toAdd, ObservableCollectionActiveManagers.DerivedCollectionElement<T> after, ObservableCollectionActiveManagers.DerivedCollectionElement<T> before) {
			return theParent.canAdd(toAdd, strip(after), strip(before));
		}

		@Override
		public boolean clear() {
			return theParent.clear();
		}

		@Override
		public ObservableCollectionActiveManagers.DerivedCollectionElement<T> addElement(T value, ObservableCollectionActiveManagers.DerivedCollectionElement<T> after, ObservableCollectionActiveManagers.DerivedCollectionElement<T> before,
			boolean first) {
			ObservableCollectionActiveManagers.DerivedCollectionElement<T> parentEl = theParent.addElement(value, strip(after),
				strip(before), first);
			return parentEl == null ? null : new RefreshingElement(parentEl);
		}

		@Override
		public String canMove(ObservableCollectionActiveManagers.DerivedCollectionElement<T> valueEl, ObservableCollectionActiveManagers.DerivedCollectionElement<T> after, ObservableCollectionActiveManagers.DerivedCollectionElement<T> before) {
			return theParent.canMove(strip(valueEl), strip(after), strip(before));
		}

		@Override
		public ObservableCollectionActiveManagers.DerivedCollectionElement<T> move(ObservableCollectionActiveManagers.DerivedCollectionElement<T> valueEl, ObservableCollectionActiveManagers.DerivedCollectionElement<T> after,
			ObservableCollectionActiveManagers.DerivedCollectionElement<T> before, boolean first, Runnable afterRemove) {
			return new RefreshingElement(theParent.move(strip(valueEl), strip(after), strip(before), first, afterRemove));
		}

		private ObservableCollectionActiveManagers.DerivedCollectionElement<T> strip(ObservableCollectionActiveManagers.DerivedCollectionElement<T> el) {
			return el == null ? null : ((RefreshingElement) el).theParentEl;
		}

		@Override
		public void setValues(Collection<ObservableCollectionActiveManagers.DerivedCollectionElement<T>> elements, T newValue)
			throws UnsupportedOperationException, IllegalArgumentException {
			theParent.setValues(elements.stream().map(el -> ((RefreshingElement) el).theParentEl).collect(Collectors.toList()), newValue);
		}

		@Override
		public void begin(boolean fromStart, ObservableCollectionActiveManagers.ElementAccepter<T> onElement, WeakListening listening) {
			theListening = listening;
			theParent.begin(fromStart, (parentEl, cause) -> {
				try (Transaction t = lockRefresh(false)) {
					onElement.accept(new RefreshingElement(parentEl), cause);
				}
			}, listening);
		}

		private class RefreshingElement implements ObservableCollectionActiveManagers.DerivedCollectionElement<T> {
			private final ObservableCollectionActiveManagers.DerivedCollectionElement<T> theParentEl;
			private Observable<?> theCurrentRefresh;
			private RefreshHolder theCurrentHolder;
			private ElementId theElementId;
			private ObservableCollectionActiveManagers.CollectionElementListener<T> theListener;
			private boolean isInstalled;

			RefreshingElement(ObservableCollectionActiveManagers.DerivedCollectionElement<T> parentEl) {
				theParentEl = parentEl;
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
					theParentEl.setListener(new ObservableCollectionActiveManagers.CollectionElementListener<T>() {
						@Override
						public void update(T oldValue, T newValue, Object cause) {
							try (Transaction t = lockRefresh(false)) {
								ObservableCollectionActiveManagers.update(theListener, oldValue, newValue, cause);
								updated(newValue);
							}
						}

						@Override
						public void removed(T value, Object cause) {
							try (Transaction t = lockRefresh(false)) {
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
				ObservableCollectionActiveManagers.update(theListener, value, value, cause);
			}

			@Override
			public int compareTo(ObservableCollectionActiveManagers.DerivedCollectionElement<T> o) {
				return theParentEl.compareTo(((RefreshingElement) o).theParentEl);
			}

			@Override
			public void setListener(ObservableCollectionActiveManagers.CollectionElementListener<T> listener) {
				theListener = listener;
				installOrUninstall();
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
				return theParentEl.isAcceptable(value);
			}

			@Override
			public void set(T value) throws UnsupportedOperationException, IllegalArgumentException {
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
			public String toString() {
				return theParentEl.toString();
			}
		}
	}

	static class FlattenedManager<E, I, T> implements ObservableCollectionActiveManagers.ActiveCollectionManager<E, I, T> {
		private final ObservableCollectionActiveManagers.ActiveCollectionManager<E, ?, I> theParent;
		private final TypeToken<T> theTargetType;
		private final Function<? super I, ? extends CollectionDataFlow<?, ?, ? extends T>> theMap;

		private ObservableCollectionActiveManagers.ElementAccepter<T> theAccepter;
		private WeakListening theListening;
		private final BetterTreeList<FlattenedHolder> theOuterElements;
		private final ReentrantReadWriteLock theLock;

		public FlattenedManager(ObservableCollectionActiveManagers.ActiveCollectionManager<E, ?, I> parent, TypeToken<T> targetType,
			Function<? super I, ? extends CollectionDataFlow<?, ?, ? extends T>> map) {
			theParent = parent;
			theTargetType = targetType;
			theMap = map;

			theOuterElements = new BetterTreeList<>(false);
			theLock = new ReentrantReadWriteLock();
		}

		@Override
		public Object getIdentity() {
			return Identifiable.wrap(theParent.getIdentity(), "flatten", theMap);
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
		public boolean isContentControlled() {
			try (Transaction t = theParent.lock(false, null)) {
				boolean anyControlled = false;
				for (FlattenedHolder outerEl : theOuterElements) {
					if (outerEl.manager == null)
						continue;
					anyControlled |= outerEl.manager.isContentControlled();
				}
				return anyControlled;
			}
		}

		@Override
		public Comparable<ObservableCollectionActiveManagers.DerivedCollectionElement<T>> getElementFinder(T value) {
			return null;
		}

		@Override
		public BetterList<ObservableCollectionActiveManagers.DerivedCollectionElement<T>> getElementsBySource(ElementId sourceEl,
			BetterCollection<?> sourceCollection) {
			try (Transaction t = lock(false, null)) {
				BetterList<ObservableCollectionActiveManagers.DerivedCollectionElement<I>> parentEBS = theParent
					.getElementsBySource(sourceEl, sourceCollection);
				return BetterList.of(Stream.concat(//
					parentEBS.stream().flatMap(outerEl -> {
						BinaryTreeNode<FlattenedHolder> fh = theOuterElements.getRoot()
							.findClosest(fhEl -> outerEl.compareTo(fhEl.get().theParentEl), true, true, OptimisticContext.TRUE);
						return fh == null ? Stream.empty() : fh.get().theElements.stream();
					}), //
					// Unfortunately, I think the only way to do this reliably is to ask every outer element
					theOuterElements.stream().flatMap(holder -> {
						for (ObservableCollectionActiveManagers.DerivedCollectionElement<I> fromParent : parentEBS)
							if (fromParent.compareTo(holder.theParentEl) == 0)
								return Stream.empty();
						return holder.manager.getElementsBySource(sourceEl, sourceCollection).stream().flatMap(innerEl -> {
							BinaryTreeNode<FlattenedElement> fe = holder.theElements.getRoot().findClosest(
								feEl -> ((ObservableCollectionActiveManagers.DerivedCollectionElement<T>) innerEl)
								.compareTo((ObservableCollectionActiveManagers.DerivedCollectionElement<T>) feEl.get().theParentEl),
								true, true, OptimisticContext.TRUE);
							if (fe != null && fe.get().theParentEl.equals(innerEl))
								return Stream.of(fe.get());
							else
								return Stream.empty();
						});
					})//
					));
			}
		}

		@Override
		public BetterList<ElementId> getSourceElements(ObservableCollectionActiveManagers.DerivedCollectionElement<T> localElement,
			BetterCollection<?> sourceCollection) {
			FlattenedElement flatEl = (FlattenedElement) localElement;
			return BetterList.of(Stream.concat(//
				theParent.getSourceElements(flatEl.theHolder.theParentEl, sourceCollection).stream(), //
				((ObservableCollectionActiveManagers.ActiveCollectionManager<?, ?, T>) ((FlattenedElement) localElement).theHolder.manager).getSourceElements(
					(ObservableCollectionActiveManagers.DerivedCollectionElement<T>) flatEl.theParentEl, sourceCollection).stream()//
				));
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
			ObservableCollectionActiveManagers.DerivedCollectionElement<T> lowBound;
			ObservableCollectionActiveManagers.DerivedCollectionElement<T> highBound;
		}

		class InterElementIterable implements Iterable<FlattenedHolderIter> {
			private final FlattenedElement start;
			private final FlattenedElement end;
			private final boolean ascending;

			InterElementIterable(ObservableCollectionActiveManagers.DerivedCollectionElement<T> start, ObservableCollectionActiveManagers.DerivedCollectionElement<T> end, boolean ascending) {
				this.start = (FlattenedManager<E, I, T>.FlattenedElement) start;
				this.end = (FlattenedManager<E, I, T>.FlattenedElement) end;
				this.ascending = ascending;
			}

			@Override
			public Iterator<FlattenedHolderIter> iterator() {
				return new Iterator<FlattenedHolderIter>() {
					private FlattenedHolder theHolder;
					private FlattenedHolderIter theIterStruct;

					{
						if (start == null)
							theHolder = CollectionElement.get(theOuterElements.getTerminalElement(ascending));
						else
							theHolder = start.theHolder;
						theIterStruct = new FlattenedHolderIter();
					}

					@Override
					public boolean hasNext() {
						if (theHolder == null)
							return false;
						else if (end == null)
							return true;
						int comp = theHolder.holderElement.compareTo(end.theHolder.holderElement);
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
						if (ascending) {
							if (start == null || !theHolder.equals(start.theHolder))
								theIterStruct.lowBound = null;
							else
								theIterStruct.lowBound = (ObservableCollectionActiveManagers.DerivedCollectionElement<T>) start.theParentEl;
							if (end == null || !theHolder.equals(end.theHolder))
								theIterStruct.highBound = null;
							else
								theIterStruct.highBound = (ObservableCollectionActiveManagers.DerivedCollectionElement<T>) end.theParentEl;
						} else {
							if (end == null || !theHolder.equals(end.theHolder))
								theIterStruct.lowBound = null;
							else
								theIterStruct.lowBound = (ObservableCollectionActiveManagers.DerivedCollectionElement<T>) end.theParentEl;
							if (start == null || !theHolder.equals(start.theHolder))
								theIterStruct.highBound = null;
							else
								theIterStruct.highBound = (ObservableCollectionActiveManagers.DerivedCollectionElement<T>) start.theParentEl;
						}
						do {
							theHolder = CollectionElement.get(theOuterElements.getAdjacentElement(theHolder.holderElement, ascending));
						} while (theHolder.manager == null);
						return theIterStruct;
					}
				};
			}
		}

		@Override
		public String canAdd(T toAdd, ObservableCollectionActiveManagers.DerivedCollectionElement<T> after, ObservableCollectionActiveManagers.DerivedCollectionElement<T> before) {
			String firstMsg = null;
			try (Transaction t = theParent.lock(false, null)) {
				for (FlattenedHolderIter holder : new InterElementIterable(after, before, true)) {
					String msg;
					if (!TypeTokens.get().isInstance(holder.holder.manager.getTargetType(), toAdd)
						|| !holder.holder.manager.equivalence().isElement(toAdd)) {
						msg = StdMsg.ILLEGAL_ELEMENT;
					} else
						msg = ((ObservableCollectionActiveManagers.ActiveCollectionManager<?, ?, T>) holder.holder.manager).canAdd(toAdd, holder.lowBound, holder.highBound);
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
		public ObservableCollectionActiveManagers.DerivedCollectionElement<T> addElement(T value, ObservableCollectionActiveManagers.DerivedCollectionElement<T> after, ObservableCollectionActiveManagers.DerivedCollectionElement<T> before,
			boolean first) {
			try (Transaction t = theParent.lock(false, null)) {
				String firstMsg = null;
				for (FlattenedHolderIter holder : new InterElementIterable(after, before, first)) {
					String msg;
					if (!TypeTokens.get().isInstance(holder.holder.manager.getTargetType(), value)
						|| !holder.holder.manager.equivalence().isElement(value)) {
						msg = StdMsg.ILLEGAL_ELEMENT;
					} else
						msg = ((ObservableCollectionActiveManagers.ActiveCollectionManager<?, ?, T>) holder.holder.manager).canAdd(value, holder.lowBound, holder.highBound);
					if (msg == null) {
						ObservableCollectionActiveManagers.DerivedCollectionElement<T> added = ((ObservableCollectionActiveManagers.ActiveCollectionManager<?, ?, T>) holder.holder.manager)
							.addElement(value, holder.lowBound, holder.highBound, first);
						if (added != null)
							return new FlattenedElement(holder.holder, added, true);
					} else if (firstMsg == null)
						firstMsg = msg;
				}
				if (firstMsg == null || firstMsg.equals(StdMsg.UNSUPPORTED_OPERATION))
					throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
				else
					throw new IllegalArgumentException(firstMsg);
			}
		}

		@Override
		public String canMove(ObservableCollectionActiveManagers.DerivedCollectionElement<T> valueEl, ObservableCollectionActiveManagers.DerivedCollectionElement<T> after, ObservableCollectionActiveManagers.DerivedCollectionElement<T> before) {
			FlattenedElement flatV = (FlattenedManager<E, I, T>.FlattenedElement) valueEl;
			String firstMsg = null;
			try (Transaction t = theParent.lock(false, null)) {
				String removable = flatV.theParentEl.canRemove();
				T value = flatV.theParentEl.get();
				for (FlattenedHolderIter holder : new InterElementIterable(after, before, true)) {
					String msg;
					if (holder.holder.equals(flatV.theHolder))
						msg = ((ObservableCollectionActiveManagers.ActiveCollectionManager<?, ?, T>) holder.holder.manager).canMove(
							(ObservableCollectionActiveManagers.DerivedCollectionElement<T>) flatV.theParentEl, holder.lowBound,
							holder.highBound);
					else if (!TypeTokens.get().isInstance(holder.holder.manager.getTargetType(), value)
						|| !holder.holder.manager.equivalence().isElement(value)) {
						msg = StdMsg.ILLEGAL_ELEMENT;
					} else if (removable != null)
						msg = removable;
					else
						msg = ((ObservableCollectionActiveManagers.ActiveCollectionManager<?, ?, T>) holder.holder.manager).canAdd(value, holder.lowBound, holder.highBound);
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
		public ObservableCollectionActiveManagers.DerivedCollectionElement<T> move(ObservableCollectionActiveManagers.DerivedCollectionElement<T> valueEl, ObservableCollectionActiveManagers.DerivedCollectionElement<T> after,
			ObservableCollectionActiveManagers.DerivedCollectionElement<T> before, boolean first, Runnable afterRemove) {
			FlattenedElement flatV = (FlattenedManager<E, I, T>.FlattenedElement) valueEl;
			String firstMsg = null;
			try (Transaction t = theParent.lock(false, null)) {
				String removable = flatV.theParentEl.canRemove();
				T value = flatV.theParentEl.get();
				ObservableCollectionActiveManagers.DerivedCollectionElement<T> moved = null;
				for (FlattenedHolderIter holder : new InterElementIterable(after, before, true)) {
					String msg;
					if (holder.holder.equals(flatV.theHolder)) {
						msg = ((ObservableCollectionActiveManagers.ActiveCollectionManager<?, ?, T>) holder.holder.manager).canMove(
							(ObservableCollectionActiveManagers.DerivedCollectionElement<T>) flatV.theParentEl, holder.lowBound,
							holder.highBound);
						if (msg == null)
							moved = ((ObservableCollectionActiveManagers.ActiveCollectionManager<?, ?, T>) holder.holder.manager).move(
								(ObservableCollectionActiveManagers.DerivedCollectionElement<T>) flatV.theParentEl, holder.lowBound,
								holder.highBound, first, afterRemove);
					} else if (!TypeTokens.get().isInstance(holder.holder.manager.getTargetType(), value)
						|| !holder.holder.manager.equivalence().isElement(value)) {
						msg = StdMsg.ILLEGAL_ELEMENT;
					} else if (removable != null)
						msg = removable;
					else {
						msg = ((ObservableCollectionActiveManagers.ActiveCollectionManager<?, ?, T>) holder.holder.manager).canAdd(value, holder.lowBound, holder.highBound);
						if (msg == null) {
							flatV.theParentEl.remove();
							if (afterRemove != null)
								afterRemove.run();
							moved = ((ObservableCollectionActiveManagers.ActiveCollectionManager<?, ?, T>) holder.holder.manager).addElement(value, holder.lowBound,
								holder.highBound, first);
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
		public void setValues(Collection<ObservableCollectionActiveManagers.DerivedCollectionElement<T>> elements, T newValue)
			throws UnsupportedOperationException, IllegalArgumentException {
			// Group by collection
			BetterMap<ObservableCollectionActiveManagers.ActiveCollectionManager<?, ?, T>, List<ObservableCollectionActiveManagers.DerivedCollectionElement<T>>> grouped = BetterHashMap
				.build().identity().unsafe().buildMap();
			for (ObservableCollectionActiveManagers.DerivedCollectionElement<T> el : elements)
				grouped
				.computeIfAbsent((ObservableCollectionActiveManagers.ActiveCollectionManager<?, ?, T>) ((FlattenedElement) el).theHolder.manager, h -> new ArrayList<>())
				.add((ObservableCollectionActiveManagers.DerivedCollectionElement<T>) ((FlattenedElement) el).theParentEl);

			for (Map.Entry<ObservableCollectionActiveManagers.ActiveCollectionManager<?, ?, T>, List<ObservableCollectionActiveManagers.DerivedCollectionElement<T>>> entry : grouped
				.entrySet())
				entry.getKey().setValues(entry.getValue(), newValue);
		}

		@Override
		public void begin(boolean fromStart, ObservableCollectionActiveManagers.ElementAccepter<T> onElement, WeakListening listening) {
			theAccepter = onElement;
			theListening = listening;
			boolean[] init = new boolean[] { true }; // Only honor fromStart for the initial collections
			theParent.begin(fromStart, (parentEl, cause) -> {
				FlattenedHolder holder = new FlattenedHolder(parentEl, listening, cause, !init[0] || fromStart);
				holder.holderElement = theOuterElements.addElement(holder, false).getElementId();
			}, listening);
			init[0] = false;
		}

		private class FlattenedHolder {
			private final ObservableCollectionActiveManagers.DerivedCollectionElement<I> theParentEl;
			private final BetterTreeList<FlattenedElement> theElements;
			private final WeakListening.Builder theChildListening = theListening.child();
			private final boolean isFromStart;
			ElementId holderElement;
			private CollectionDataFlow<?, ?, ? extends T> theFlow;
			ObservableCollectionActiveManagers.ActiveCollectionManager<?, ?, ? extends T> manager;

			FlattenedHolder(ObservableCollectionActiveManagers.DerivedCollectionElement<I> parentEl, WeakListening listening, Object cause,
				boolean fromStart) {
				theParentEl = parentEl;
				theElements = new BetterTreeList<>(false);
				isFromStart = fromStart;
				updated(theParentEl.get(), cause);
				theParentEl.setListener(new ObservableCollectionActiveManagers.CollectionElementListener<I>() {
					@Override
					public void update(I oldValue, I newValue, Object innerCause) {
						updated(newValue, innerCause);
					}

					@Override
					public void removed(I value, Object innerCause) {
						try (Transaction parentT = theParent.lock(false, null); Transaction innerT = lockLocal()) {
							clearSubElements(innerCause);
						}
					}
				});
			}

			void updated(I newValue, Object cause) {
				try (Transaction parentT = theParent.lock(false, null); Transaction t = lockLocal()) {
					CollectionDataFlow<?, ?, ? extends T> newFlow = theMap.apply(newValue);
					if (newFlow == theFlow)
						return;
					clearSubElements(cause);
					theFlow = newFlow;
					manager = newFlow.manageActive();
					manager.begin(isFromStart, (childEl, innerCause) -> {
						try (Transaction innerParentT = theParent.lock(false, null); Transaction innerLocalT = lockLocal()) {
							FlattenedElement flatEl = new FlattenedElement(this, childEl, false);
							theAccepter.accept(flatEl, innerCause);
						}
					}, theChildListening.getListening());
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
		}

		private class FlattenedElement implements ObservableCollectionActiveManagers.DerivedCollectionElement<T> {
			private final FlattenedHolder theHolder;
			private final ObservableCollectionActiveManagers.DerivedCollectionElement<? extends T> theParentEl;
			private final ElementId theElementId;
			private ObservableCollectionActiveManagers.CollectionElementListener<T> theListener;

			<X extends T> FlattenedElement(FlattenedHolder holder, ObservableCollectionActiveManagers.DerivedCollectionElement<X> parentEl,
				boolean synthetic) {
				theHolder = holder;
				theParentEl = parentEl;
				if (!synthetic) {
					theElementId = theHolder.theElements.addElement(this, false).getElementId();
					parentEl.setListener(new ObservableCollectionActiveManagers.CollectionElementListener<X>() {
						@Override
						public void update(X oldValue, X newValue, Object cause) {
							// Need to make sure that the flattened collection isn't firing at the same time as the child collection
							try (Transaction parentT = theParent.lock(false, null); Transaction localT = lockLocal()) {
								ObservableCollectionActiveManagers.update(theListener, oldValue, newValue, cause);
							}
						}

						@Override
						public void removed(X value, Object cause) {
							theHolder.theElements.mutableElement(theElementId).remove();
							// Need to make sure that the flattened collection isn't firing at the same time as the child collection
							try (Transaction parentT = theParent.lock(false, null); Transaction localT = lockLocal()) {
								ObservableCollectionActiveManagers.removed(theListener, value, cause);
							}
						}
					});
				} else
					theElementId = null;
			}

			@Override
			public int compareTo(ObservableCollectionActiveManagers.DerivedCollectionElement<T> o) {
				FlattenedElement flat = (FlattenedElement) o;
				int comp = theHolder.theParentEl.compareTo(flat.theHolder.theParentEl);
				if (comp == 0)
					comp = ((ObservableCollectionActiveManagers.DerivedCollectionElement<T>) theParentEl).compareTo((ObservableCollectionActiveManagers.DerivedCollectionElement<T>) flat.theParentEl);
				return comp;
			}

			@Override
			public void setListener(ObservableCollectionActiveManagers.CollectionElementListener<T> listener) {
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
				if (value != null && !TypeTokens.get().isInstance(theHolder.manager.getTargetType(), value))
					return StdMsg.BAD_TYPE;
				return ((ObservableCollectionActiveManagers.DerivedCollectionElement<T>) theParentEl).isAcceptable(value);
			}

			@Override
			public void set(T value) throws UnsupportedOperationException, IllegalArgumentException {
				if (value != null && !TypeTokens.get().isInstance(theHolder.manager.getTargetType(), value))
					throw new IllegalArgumentException(StdMsg.BAD_TYPE);
				((ObservableCollectionActiveManagers.DerivedCollectionElement<T>) theParentEl).set(value);
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
			public String toString() {
				return theHolder.theParentEl.toString() + "/" + theParentEl.toString();
			}
		}
	}
}
