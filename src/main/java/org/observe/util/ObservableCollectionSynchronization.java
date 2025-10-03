package org.observe.util;

import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.observe.Subscription;
import org.observe.collect.ObservableCollection;
import org.qommons.Causable;
import org.qommons.Causable.CausableKey;
import org.qommons.Transaction;
import org.qommons.collect.BetterMap;
import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.CircularArrayList;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.ListElement;
import org.qommons.collect.MapEntryHandle;
import org.qommons.collect.MutableListElement;
import org.qommons.tree.BetterTreeSet;

/**
 * <p>
 * A class that is capable of synchronizing two collections of the same type but differing data tolerances.
 * </p>
 * <p>
 * Unlike previous iterations of this feature (see {@link ObservableUtils#link(ObservableCollection, ObservableCollection)}), this class can
 * handle synchronization of collections whose internal orderings differ, or whose data filtering (e.g. see
 * {@link ObservableCollection#canAdd(Object)}) differs.
 * </p>
 * <p>
 * If, for example, a value added to one collection cannot be added to the other collection in the same location, another location may be
 * tried (depending on the configured ordering constraint, see {@link Builder#strictOrder()}, {@link Builder#preferOrdered()}, and
 * {@link Builder#unordered()}). If this too fails, the value will dangle harmlessly. If then the value is added to the second collection
 * later from another source, these values will be matched up.
 * </p>
 * <p>
 * Similar functionality exists to handle removals or element set operations that are unsupported by the other collection. Thus, this class
 * may be used to synchronize any 2 collections "as closely as possible" without any knowledge of the ordering or data capabilities of
 * either.
 * </p>
 *
 * @param <V> The type of values in the collections being synchronized
 * @see #synchronize(ObservableCollection, ObservableCollection)
 */
public class ObservableCollectionSynchronization<V> implements Subscription {
	/** An intermediate cause for events linking 2 {@link ObservableCollection}s */
	public static class ObservableCollectionLinkEvent extends Causable.AbstractCausable {
		private final ObservableCollection<?> theSource;

		ObservableCollectionLinkEvent(ObservableCollection<?> source, Object cause) {
			super(cause);
			theSource = source;
		}

		/**
		 * @param potentialSource An ObservableCollection to test
		 * @return Whether the given collection is this event's source
		 */
		public boolean isSource(ObservableCollection<?> potentialSource) {
			return theSource == potentialSource;
		}
	}

	private final ObservableCollection<V> theLeft;
	private final ObservableCollection<V> theRight;
	private final Boolean isOrderEnforced;

	private final BetterMap<V, ValueElements<V>> theValues;
	private final List<CommonElement<V>> theLeftElements;
	private final List<CommonElement<V>> theRightElements;

	private boolean theCallbackLock;
	private final Subscription theLeftSub;
	private final Subscription theRightSub;

	ObservableCollectionSynchronization(ObservableCollection<V> left, ObservableCollection<V> right, Boolean orderEnforced) {
		theLeft = left;
		theRight = right;
		isOrderEnforced = orderEnforced;

		theValues = theLeft.equivalence().createMap();
		theLeftElements = CircularArrayList.build().build();
		theRightElements = CircularArrayList.build().build();
		Causable syncCause = Causable.simpleCause(this);
		try (Transaction causeT = syncCause.use();
			Transaction leftT = theLeft.lock(true, syncCause); //
			Transaction rightT = theRight.lock(true, syncCause)) {
			// Populate the left elements first with no modifications to the right collection, as if right were empty and immutable
			CommonElement<V> prevLeft = null;
			for (ListElement<V> leftEl : theLeft.elements()) {
				MapEntryHandle<V, ValueElements<V>> valueEls = theValues.computeEntryIfAbsent(leftEl.get(), __ -> new ValueElements<>(),
					false);
				CommonElement<V> common = new CommonElement<>(valueEls.getElementId(), leftEl, null);
				if (prevLeft != null)
					prevLeft.link(true, true, common);
				prevLeft = common;
				theLeftElements.add(common);
				valueEls.get().addUnmatched(true, common);
			}
			theCallbackLock = true;
			try {
				// Now find all the common elements to use as anchors so we can preserve order as much as possible
				BitSet rightAdded = new BitSet();
				int r = 0, ra = 0;
				for (ListElement<V> rightEl : theRight.elements()) {
					if (added(false, ra, //
						rightEl, rightEl.get(), true, null) != null) {
						rightAdded.set(r);
						ra++;
					}
					r++;
				}
				// Now add the unmatched elements in the right collection
				r = 0;
				for (ListElement<V> rightEl : theRight.elements()) {
					if (!rightAdded.get(r)) {
						added(false, r, //
							rightEl, rightEl.get(), false, null);
					}
					r++;
				}

				// Now we fire the adds on all the unmatched left elements as if they were incoming, to see if they can be added to the
				// right
				for (int i = 0; i < theLeftElements.size(); i++) {
					CommonElement<V> leftEl = theLeftElements.get(i);
					if (leftEl.getElement(false) == null) {
						ListElement<V> leftId = leftEl.getElement(true);
						removed(true, i);
						added(true, i, leftId, //
							leftId.get(), false, null);
					}
				}
			} finally {
				theCallbackLock = false;
			}
			// We're all initialized, so now we need to listen for changes
			CausableKey ck = Causable.key((cause, values) -> {
				Transaction t = (Transaction) values.get("leftTransaction");
				if (t != null)
					t.close();
				t = (Transaction) values.get("rightTransaction");
				if (t != null)
					t.close();
			});

			// Debugging printlns
			// System.out.println(
			// Integer.toHexString(System.identityHashCode(this)) + ":\n\t" + theLeftElements + "\n\t<-->\n\t" + theRightElements);
			theLeftSub = theLeft.onChange(evt -> {
				if (theCallbackLock)
					return;
				// System.out.println(Integer.toHexString(System.identityHashCode(this)) + ":\n\t" + theLeftElements + "\n\t<-->\n\t"
				// + theRightElements + "\n\tleft: " + evt);
				// This outer transaction is because locking once for a series of changes
				// is much more efficient than repeatedly locking for each change
				Map<Object, Object> data = evt.getRootCausable().onFinish(ck);
				ObservableCollectionLinkEvent linkEvt = (ObservableCollectionLinkEvent) data.computeIfAbsent("rightLinkEvt",
					__ -> new ObservableCollectionLinkEvent(theLeft, evt.getRootCausable()));
				data.computeIfAbsent("rightTransaction", k -> {
					Transaction linkEvtT = linkEvt.use();
					Transaction cTrans = theRight.lock(true, linkEvt);
					return Transaction.and(cTrans, linkEvtT);
				});
				// The inner transaction is so that each left change is causably linked to a particular right change
				ObservableCollectionLinkEvent innerLinkEvt = new ObservableCollectionLinkEvent(theLeft, evt);
				try (Transaction linkEvtT = innerLinkEvt.use(); //
					Transaction evtT = theRight.lock(true, innerLinkEvt)) {
					theCallbackLock = !theRight.isEventing();
					switch (evt.getType()) {
					case add:
						added(true, //
							evt.getIndex(), theLeft.getElement(evt.getElementId()), evt.getNewValue(), false, null);
						break;
					case remove:
						removed(true, //
							evt.getIndex());
						break;
					case set:
						changed(true, //
							evt.getIndex(), evt.getNewValue());
						break;
					}
				} finally {
					theCallbackLock = false;
				}
				// System.out.println("Post:\n\t" + theLeftElements + "\n\t<-->\n\t" + theRightElements);
			});

			theRightSub = theRight.onChange(evt -> {
				if (theCallbackLock)
					return;
				// System.out.println(Integer.toHexString(System.identityHashCode(this)) + ":\n\t" + theLeftElements + "\n]t<-->\n\t"
				// + theRightElements + "\n\tright: " + evt);
				// This outer transaction is because locking once for a series of changes
				// is much more efficient than repeatedly locking for each change
				Map<Object, Object> data = evt.getRootCausable().onFinish(ck);
				ObservableCollectionLinkEvent linkEvt = (ObservableCollectionLinkEvent) data.computeIfAbsent("leftLinkEvt",
					__ -> new ObservableCollectionLinkEvent(theRight, evt.getRootCausable()));
				data.computeIfAbsent("leftTransaction", k -> {
					Transaction linkEvtT = linkEvt.use();
					Transaction cTrans = theLeft.lock(true, linkEvt);
					return Transaction.and(cTrans, linkEvtT);
				});
				// The inner transaction is so that each right change is causably linked to a particular left change
				ObservableCollectionLinkEvent innerLinkEvt = new ObservableCollectionLinkEvent(theRight, evt);
				try (Transaction linkEvtT = innerLinkEvt.use(); //
					Transaction evtT = theLeft.lock(true, innerLinkEvt)) {
					theCallbackLock = !theLeft.isEventing();
					switch (evt.getType()) {
					case add:
						added(false, //
							evt.getIndex(), theRight.getElement(evt.getElementId()), evt.getNewValue(), false, null);
						break;
					case remove:
						removed(false, //
							evt.getIndex());
						break;
					case set:
						changed(false, //
							evt.getIndex(), evt.getNewValue());
						break;
					}
				} finally {
					theCallbackLock = false;
				}
				// System.out.println("Post:\n\t" + theLeftElements + "\n\t<-->\n\t" + theRightElements);
			});
		}
	}

	@Override
	public void unsubscribe() {
		theLeftSub.unsubscribe();
		theRightSub.unsubscribe();
	}

	ObservableCollection<V> getCollection(boolean left) {
		return left ? theLeft : theRight;
	}

	List<CommonElement<V>> getElements(boolean left) {
		return left ? theLeftElements : theRightElements;
	}

	private CommonElement<V> added(boolean left, int index, ListElement<V> id, V newValue, boolean onlyMatch, Runnable preMatch) {
		MapEntryHandle<V, ValueElements<V>> valueEls;
		if (onlyMatch) {
			valueEls = theValues.getEntry(newValue);
			if (valueEls == null)
				return null;
		} else
			valueEls = theValues.computeEntryIfAbsent(newValue, __ -> new ValueElements<>(), false);
		List<CommonElement<V>> elements = getElements(left);
		CommonElement<V> prevOpp, nextOpp;
		{
			prevOpp = index > 0 ? elements.get(index - 1) : null;
			while (prevOpp != null && prevOpp.getElement(!left) == null)
				prevOpp = prevOpp.getLink(false, left);

			nextOpp = index < elements.size() ? elements.get(index) : null;
			while (nextOpp != null && nextOpp.getElement(!left) == null)
				nextOpp = nextOpp.getLink(true, left);
		}
		CommonElement<V> common;
		if (prevOpp != null && nextOpp != null && prevOpp.getElement(!left).compareTo(nextOpp.getElement(!left)) >= 0) {
			// Out of order
			if (Boolean.TRUE.equals(isOrderEnforced)) { // No hope, leave unmatched
				common = new CommonElement<>(valueEls.getElementId(), //
					left ? id : null, left ? null : id);
				valueEls.get().addUnmatched(left, common);
				if (index > 0)
					elements.get(index - 1).link(true, left, common);
				else if (index < elements.size())
					elements.get(index).link(false, left, common);
				elements.add(index, common);
				return common;
			} else // Maybe we can match to or add *somewhere*
				prevOpp = nextOpp = null;
		}
		CommonElement<V> unmatchedRight = valueEls.get().unmatched(!left, prevOpp, nextOpp).poll();
		if (unmatchedRight == null && (prevOpp != null || nextOpp != null) && !Boolean.TRUE.equals(isOrderEnforced)) {
			// If order is flexible, maybe we can match to *something*
			unmatchedRight = valueEls.get().unmatched(!left, null, null).poll();
		}
		ObservableCollection<V> opp = getCollection(!left);
		if (unmatchedRight != null) { // Match it up
			if (preMatch != null)
				preMatch.run();
			valueEls.get().addMatch();
			common = unmatchedRight;
			common.setElement(left, id);
		} else if (onlyMatch) {
			return null;
		} else { // No compatible match, try to add
			Boolean canAdd = null;
			if ((prevOpp != null || nextOpp != null) && !Boolean.TRUE.equals(isOrderEnforced)) {
				canAdd = opp.canAdd(newValue, //
					prevOpp == null ? null : CollectionElement.getElementId(prevOpp.getElement(!left)), //
						nextOpp == null ? null : CollectionElement.getElementId(nextOpp.getElement(!left))) == null;
				if (!canAdd) {// Can't add in order, but maybe we can add somewhere
					prevOpp = nextOpp = null;
					canAdd = null; // Try again
				}
			}
			boolean otherEventing = opp.isEventing();
			ListElement<V> oppEl = null;
			if (canAdd == null)
				canAdd = opp.canAdd(newValue, //
					prevOpp == null ? null : CollectionElement.getElementId(prevOpp.getElement(!left)), //
						nextOpp == null ? null : CollectionElement.getElementId(nextOpp.getElement(!left))) == null;
			if (canAdd && !otherEventing) {
				try {
					oppEl = opp.addElement(newValue, //
						prevOpp == null ? null : CollectionElement.getElementId(prevOpp.getElement(!left)), //
							nextOpp == null ? null : CollectionElement.getElementId(nextOpp.getElement(!left)), false);
					if (oppEl == null)
						System.err.println("Unadvertised failed add");
				} catch (RuntimeException | Error e) {
					System.err.println("Unadvertised failed add");
					e.printStackTrace();
				}
			}
			common = new CommonElement<>(valueEls.getElementId(), //
				left ? id : oppEl, left ? oppEl : id);
			// If the add succeeded, we may be able to do the linkage here.
			// But if it's already eventing, we may be out of sync.
			// In that case, behave as if the add failed and we'll link it up by value when we see it come in on the other side.
			if (oppEl != null && !otherEventing) {
				List<CommonElement<V>> oppElements = getElements(!left);
				int oppIndex = oppEl.getElementsBefore();
				if (oppIndex > 0)
					oppElements.get(oppIndex - 1).link(true, !left, common);
				else if (oppIndex < oppElements.size())
					oppElements.get(oppIndex).link(false, !left, common);
				oppElements.add(oppIndex, common);
				valueEls.get().addMatch();
			} else // Couldn't add (or may be out-of-sync), leave unmatched
				valueEls.get().addUnmatched(left, common);
		}
		if (index > 0)
			elements.get(index - 1).link(true, left, common);
		else if (index < elements.size())
			elements.get(index).link(false, left, common);
		elements.add(index, common);
		return common;
	}

	private void removed(boolean left, int index) {
		CommonElement<V> common = getElements(left).remove(index);
		ValueElements<V> values = theValues.getEntryById(common.valueElement).get();
		if (common.getElement(!left) == null)
			values.removeUnmatched(left, common);
		else {
			values.removeMatch();
			// Try to remove the value from the other side
			ObservableCollection<V> opp = getCollection(!left);
			ListElement<V> oppElId = common.getElement(!left);
			if (oppElId.getElementId().isPresent()) { // Could have already been removed by the same underlying cause
				MutableListElement<V> oppEl = opp.mutableElement(oppElId.getElementId());
				if (oppEl.canRemove() == null && !opp.isEventing()) {
					int oppIndex = oppElId.getElementsBefore();
					try {
						oppEl.remove();
						getElements(!left).remove(oppIndex).setElement(!left, null);
					} catch (RuntimeException | Error e) {
						System.err.println("Unadvertised failed remove");
						e.printStackTrace();
						values.addUnmatched(!left, common);
					}
				} else
					values.addUnmatched(!left, common);
			} else { // The other collection will tell us later
				values.addUnmatched(!left, common);
			}
		}
		common.setElement(left, null);
		if (values.isEmpty())
			theValues.mutableEntry(common.valueElement).remove();
	}

	private void changed(boolean left, int index, V newValue) {
		CommonElement<V> common = getElements(left).get(index);
		MapEntryHandle<V, ValueElements<V>> valuesEl = theValues.getEntryById(common.valueElement);
		V oldValue = valuesEl.getKey();
		// Equivalence is always the left
		if (oldValue == newValue || theLeft.equivalence().elementEquals(oldValue, newValue)) { // Just an update
			if (common.getElement(!left) != null) {
				// Try to update the value on the right side as well
				ObservableCollection<V> opp = getCollection(!left);
				MutableListElement<V> oppEl = opp.mutableElement(common.getElement(!left).getElementId());
				if (oppEl.isAcceptable(oppEl.get()) == null && !opp.isEventing()) {
					try {
						oppEl.set(oppEl.get());
					} catch (RuntimeException | Error e) {
						System.err.println("Unadvertised failed update");
						e.printStackTrace();
						// Nothing to do about it, though
					}
				} // else nothing to do
			}
		} else { // Don't think there's anything we can do better here than to remove and re-add
			if (common.getElement(!left) != null) { // There's a match. See if we can do a set on the other collection
				ObservableCollection<V> opp = getCollection(!left);
				MutableListElement<V> oppEl = opp.mutableElement(common.getElement(!left).getElementId());
				if (oppEl.isAcceptable(newValue) == null && !opp.isEventing()) {
					try {
						oppEl.set(newValue);
						valuesEl.get().removeMatch();
						if (valuesEl.get().isEmpty())
							theValues.mutableEntry(valuesEl.getElementId()).remove();
						MapEntryHandle<V, ValueElements<V>> newValuesEl = theValues.computeEntryIfAbsent(newValue,
							__ -> new ValueElements<>(),
							false);
						CommonElement<V> newCommon = new CommonElement<>(newValuesEl.getElementId(), common.getElement(true),
							common.getElement(false));
						newValuesEl.get().addMatch();
						getElements(left).set(index, newCommon);
						getElements(!left).set(oppEl.getElementsBefore(), newCommon);
					} catch (RuntimeException | Error e) {
						System.err.println("Unadvertised failed set");
						e.printStackTrace();
						ListElement<V> id = common.getElement(left);
						removed(left, index);
						added(left, index, id, newValue, false, null);
					}
				} else {
					ListElement<V> id = common.getElement(left);
					removed(left, index);
					added(left, index, id, newValue, false, null);
				}
			}
		}
	}

	/**
	 * Creates a builder to configure synchronization for 2 observable collections
	 *
	 * @param <V> The type of values to synchronize
	 * @param left The first collection to synchronize
	 * @param right The second collection to synchronize
	 * @return A builder to configure the synchronization
	 */
	public static <V> Builder<V> synchronize(ObservableCollection<V> left, ObservableCollection<V> right) {
		return new Builder<>(left, right, Boolean.FALSE); // Default is preferred order
	}

	/**
	 * Configures synchronization between 2 observable collections
	 *
	 * @param <V> The type of values to synchronize
	 */
	public static class Builder<V> {
		private final ObservableCollection<V> theLeft;
		private final ObservableCollection<V> theRight;
		private final Boolean isOrderEnforced;

		Builder(ObservableCollection<V> left, ObservableCollection<V> right, Boolean orderEnforced) {
			theLeft = left;
			theRight = right;
			isOrderEnforced = orderEnforced;
		}

		/**
		 * Produces a builder whose {@link #synchronize()} operation will ensure that all elements present in both collections are in the
		 * same order. Elements added to one collection that cannot be added into the other collection in the same relative position will
		 * not be added at all.
		 *
		 * @return The new builder
		 */
		public Builder<V> strictOrder() {
			if (Boolean.TRUE.equals(isOrderEnforced))
				return this;
			return new Builder<>(theLeft, theRight, Boolean.TRUE);
		}

		/**
		 * Produces a builder whose {@link #synchronize()} operation will attempt to preserve order between elements present in both
		 * collections. Elements added to one collection that cannot be added into the other collection in the same relative position will
		 * be added wherever they are allowed.
		 *
		 * @return The new builder
		 */
		public Builder<V> preferOrdered() {
			if (Boolean.FALSE.equals(isOrderEnforced))
				return this;
			return new Builder<>(theLeft, theRight, Boolean.FALSE);
		}

		/**
		 * Produces a builder whose {@link #synchronize()} operation makes no attempt at preserving order between the two collections.
		 * Elements added to one collection will be added into the other collection wherever they are allowed.
		 *
		 * @return The new builder
		 */
		public Builder<V> unordered() {
			if (null == isOrderEnforced)
				return this;
			return new Builder<>(theLeft, theRight, null);
		}

		/**
		 * Begins the synchronization operation
		 *
		 * @return The operation to use to {@link ObservableCollectionSynchronization#unsubscribe() terminate} the synchronization
		 * @see ObservableCollectionSynchronization
		 */
		public ObservableCollectionSynchronization<V> synchronize() {
			return new ObservableCollectionSynchronization<>(theLeft, theRight, isOrderEnforced);
		}
	}

	static class CommonElement<V> {
		final ElementId valueElement;
		private ListElement<V> theLeft;
		private ListElement<V> theRight;
		private CommonElement<V> thePreviousLeft;
		private CommonElement<V> theNextLeft;
		private CommonElement<V> thePreviousRight;
		private CommonElement<V> theNextRight;

		CommonElement(ElementId valueElement, ListElement<V> left, ListElement<V> right) {
			this.valueElement = valueElement;
			theLeft = left;
			theRight = right;
		}

		ListElement<V> getElement(boolean left) {
			return left ? theLeft : theRight;
		}

		void setElement(boolean left, ListElement<V> id) {
			if (left)
				theLeft = id;
			else
				theRight = id;
			if (id == null) {
				CommonElement<V> next = getLink(true, left);
				CommonElement<V> prev = getLink(false, left);
				if (next != null)
					next.setLink(false, left, prev);
				if (prev != null)
					prev.setLink(true, left, next);
			}
		}

		CommonElement<V> getLink(boolean next, boolean left) {
			if (left)
				return next ? theNextLeft : thePreviousLeft;
			else
				return next ? theNextRight : thePreviousRight;
		}

		void link(boolean next, boolean left, CommonElement<V> element) {
			CommonElement<V> adj = getLink(next, left);
			element.setLink(next, left, adj);
			if (adj != null)
				adj.setLink(!next, left, element);
			setLink(next, left, element);
			element.setLink(!next, left, this);
		}

		private void setLink(boolean next, boolean left, CommonElement<V> element) {
			if (left) {
				if (next)
					theNextLeft = element;
				else
					thePreviousLeft = element;
			} else {
				if (next)
					theNextRight = element;
				else
					thePreviousRight = element;
			}
		}

		@Override
		public String toString() {
			if (theLeft != null) {
				if (theRight != null)
					return theLeft + "<-->" + theRight;
				else
					return theLeft + " (left)";
			} else
				return theRight + " (right)";
		}
	}

	static Comparator<CommonElement<?>> LEFT_COMPARE = (el1, el2) -> el1.theLeft.getElementId().compareTo(el2.theLeft.getElementId());
	static Comparator<CommonElement<?>> RIGHT_COMPARE = (el1, el2) -> el1.theRight.getElementId().compareTo(el2.theRight.getElementId());

	static class ValueElements<V> {
		private int theMatches;
		private BetterSortedSet<CommonElement<V>> theUnmatchedLeft;
		private BetterSortedSet<CommonElement<V>> theUnmatchedRight;

		boolean isEmpty() {
			if (theMatches > 0)
				return false;
			else if (theUnmatchedLeft != null && !theUnmatchedLeft.isEmpty())
				return false;
			else if (theUnmatchedRight != null && !theUnmatchedRight.isEmpty())
				return false;
			else
				return true;
		}

		void addMatch() {
			theMatches++;
		}

		void removeMatch() {
			theMatches--;
		}

		BetterSortedSet<CommonElement<V>> unmatched(boolean left, CommonElement<V> after, CommonElement<V> before) {
			if (left) {
				if (theUnmatchedLeft == null)
					return BetterSortedSet.empty(LEFT_COMPARE);
				else if (after != null) {
					if (before != null)
						return theUnmatchedLeft.subSequence(after, before);
					else
						return theUnmatchedLeft.tailSequence(after);
				} else if (before != null)
					return theUnmatchedLeft.headSequence(before);
				else
					return theUnmatchedLeft;
			} else {
				if (theUnmatchedRight == null)
					return BetterSortedSet.empty(RIGHT_COMPARE);
				else if (after != null) {
					if (before != null)
						return theUnmatchedRight.subSequence(after, before);
					else
						return theUnmatchedRight.tailSequence(after);
				} else if (before != null)
					return theUnmatchedRight.headSequence(before);
				else
					return theUnmatchedRight;
			}
		}

		void addUnmatched(boolean left, CommonElement<V> unmatched) {
			if (left) {
				if (theUnmatchedLeft == null)
					theUnmatchedLeft = BetterTreeSet.<CommonElement<V>> buildTreeSet(LEFT_COMPARE).build();
				theUnmatchedLeft.add(unmatched);
			} else {
				if (theUnmatchedRight == null)
					theUnmatchedRight = BetterTreeSet.<CommonElement<V>> buildTreeSet(RIGHT_COMPARE).build();
				theUnmatchedRight.add(unmatched);
			}
		}

		void removeUnmatched(boolean left, CommonElement<V> unmatched) {
			if (left)
				theUnmatchedLeft.remove(unmatched);
			else
				theUnmatchedRight.remove(unmatched);
		}
	}
}
