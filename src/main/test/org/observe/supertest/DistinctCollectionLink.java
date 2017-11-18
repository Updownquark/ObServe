package org.observe.supertest;

import java.util.Map;

import org.junit.Assert;
import org.observe.collect.FlowOptions;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.supertest.ObservableChainTester.TestValueType;
import org.qommons.TestHelper;
import org.qommons.ValueHolder;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterMap;
import org.qommons.collect.BetterSortedMap;
import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.ElementSpliterator;
import org.qommons.collect.MapEntryHandle;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.tree.BetterTreeList;
import org.qommons.tree.BetterTreeMap;
import org.qommons.tree.BetterTreeSet;

public class DistinctCollectionLink<E> extends AbstractObservableCollectionLink<E, E> {
	private final FlowOptions.GroupingDef theOptions;
	/** A map of value to equivalent values, grouped by source ID */
	private final BetterMap<E, BetterSortedMap<ElementId, E>> theValues;

	/** A parallel representation of the values in the source (parent) collection */
	private final BetterList<E> theSourceValues;
	/** A map of value element (in {@link #theValues}) to the source element representing the value (in {@link #theSourceValues}) */
	private final BetterMap<ElementId, ElementId> theRepresentativeElements;
	/**
	 * The sorted set of elements for the distinct-ified collection. If {@link FlowOptions.GroupingDef#isPreservingSourceOrder() source
	 * order} is preserved, then these elements are from {@link #theSourceValues}. Otherwise, they are from {@link #theValues}.
	 */
	private final BetterSortedSet<ElementId> theSortedRepresentatives;

	public DistinctCollectionLink(ObservableCollectionChainLink<?, E> parent, TestValueType type, CollectionDataFlow<?, ?, E> flow,
		TestHelper helper, FlowOptions.GroupingDef options) {
		super(parent, type, flow, helper);
		theOptions = options;
		theValues = flow.equivalence().createMap();
		theSourceValues = new BetterTreeList<>(false);

		theRepresentativeElements = new BetterTreeMap<>(true, ElementId::compareTo);
		theSortedRepresentatives = new BetterTreeSet<>(true, ElementId::compareTo);
	}

	protected BetterMap<E, BetterSortedMap<ElementId, E>> getValues() {
		return theValues;
	}

	@Override
	public void checkAddable(CollectionOp<E> add, int subListStart, int subListEnd, TestHelper helper) {
		MapEntryHandle<E, BetterSortedMap<ElementId, E>> valueEntry = theValues.getEntry(add.source);
		if (valueEntry != null) {
			add.message = StdMsg.ELEMENT_EXISTS;
		} else if (add.index >= 0) {
			add.message = StdMsg.UNSUPPORTED_OPERATION;
			add.isError = true;
		} else
			getParent().checkAddable(add, subListStart, subListEnd, helper);
	}

	@Override
	public void checkRemovable(CollectionOp<E> remove, int subListStart, int subListEnd, TestHelper helper) {
		MapEntryHandle<E, BetterSortedMap<ElementId, E>> valueEntry;
		if (remove.index < 0)
			valueEntry = theValues.getEntry(remove.source);
		else
			valueEntry = getValueHandle(remove.index);
		if (valueEntry == null) {
			remove.message = StdMsg.NOT_FOUND;
		} else if (subListStart > 0 || subListEnd < theValues.size()) {
			int index = getElementIndex(valueEntry.getElementId());
			if (index < subListStart || index >= subListEnd)
				remove.message = StdMsg.NOT_FOUND;
		}
		if (remove.message == null && getParent() != null) {
			for (ElementId srcId : valueEntry.get().keySet()) {
				CollectionOp<E> parentRemove = new CollectionOp<>(remove.equivalence, null, theSourceValues.getElementsBefore(srcId));
				getParent().checkRemovable(parentRemove, subListStart, subListEnd, helper);
				if (parentRemove.message != null) {
					remove.message = parentRemove.message;
					remove.isError = parentRemove.isError;
					break;
				}
			}
		}
	}

	private MapEntryHandle<E, BetterSortedMap<ElementId, E>> getValueHandle(int index) {
		if (theValues.keySet() instanceof BetterList) // True for sorted
			return theValues.getEntryById(((BetterList<?>) theValues.keySet()).getElement(index).getElementId());
		else {
			// No choice but to iterate
			boolean fromStart = index <= theValues.size() / 2;
			int i = fromStart ? -1 : theValues.size();
			ElementSpliterator<?> spliter = theValues.keySet().spliterator(fromStart);
			ValueHolder<ElementId> valueEl = new ValueHolder<>();
			while (i != index) {
				spliter.forElement(el -> valueEl.accept(el.getElementId()), fromStart);
				i += fromStart ? 1 : -1;
			}
			return theValues.getEntryById(valueEl.get());
		}
	}

	private int getElementIndex(ElementId id) {
		if (theValues.keySet() instanceof BetterList)
			return ((BetterList<?>) theValues.keySet()).getElementsBefore(id);
		else {
			// No choice but to iterate
			ValueHolder<ElementId> elId = new ValueHolder<>();
			ElementSpliterator<E> spliter = theValues.keySet().spliterator();
			int index = 0;
			while (spliter.forElement(el -> elId.accept(el.getElementId()), true) && !elId.get().equals(id)) {
				index++;
			}
			return index; // Assume the element was found
		}
	}

	@Override
	public void checkSettable(CollectionOp<E> set, int subListStart, int subListEnd, TestHelper helper) {
		MapEntryHandle<E, BetterSortedMap<ElementId, E>> oldValueEntry = getValueHandle(set.index);
		MapEntryHandle<E, BetterSortedMap<ElementId, E>> newValueEntry = theValues.getEntry(set.source);
		if (newValueEntry != null && !newValueEntry.getElementId().equals(oldValueEntry.getElementId())) {
			set.message = StdMsg.ELEMENT_EXISTS;
			set.isError = true;
		} else if (getParent() != null) {
			for (ElementId srcId : oldValueEntry.get().keySet()) {
				CollectionOp<E> parentSet = new CollectionOp<>(set.equivalence, null, theSourceValues.getElementsBefore(srcId));
				getParent().checkSettable(parentSet, subListStart, subListEnd, helper);
				if (parentSet.message != null) {
					set.message = parentSet.message;
					set.isError = parentSet.isError;
					break;
				}
			}
		}
	}

	@Override
	public void addedFromBelow(int index, E value, TestHelper helper) {
		ElementId srcEl = theSourceValues.addElement(index, value).getElementId();
		MapEntryHandle<E, BetterSortedMap<ElementId, E>> valueEntry = theValues.getEntry(value);
		if (valueEntry != null) {
			MapEntryHandle<ElementId, E> valueSoruceEntry = valueEntry.get().putEntry(srcEl, value);
			if (theOptions.isUsingFirst() && valueEntry.get().keySet().getElementsBefore(valueSoruceEntry.getElementId()) == 0) {
				// The new value is replacing the old value as the representative element
				ElementId oldRep = theRepresentativeElements.put(valueEntry.getElementId(), srcEl);
				if (theOptions.isPreservingSourceOrder()) {
					// The order of the representative element may have changed
					CollectionElement<ElementId> orderEl = theSortedRepresentatives.getElement(oldRep, true); // Get by value, not element
					int oldIndex = theSortedRepresentatives.getElementsBefore(orderEl.getElementId());
					theSortedRepresentatives.mutableElement(orderEl.getElementId()).remove();
					orderEl = theSortedRepresentatives.addElement(srcEl, true);
					int newIndex = theSortedRepresentatives.getElementsBefore(orderEl.getElementId());
					if (oldIndex != newIndex) {
						removed(oldIndex, helper);
						added(newIndex, value, helper);
					} else
						set(oldIndex, value, helper);
				} else {
					// The order in the derived collection cannot have been changed
				}
			}
		} else {
			// The new value is the first in its category
			valueEntry = theValues.putEntry(value,
				new BetterTreeMap<>(false, ElementId::compareTo));
			valueEntry.get().put(srcEl, value);
			theRepresentativeElements.put(valueEntry.getElementId(), srcEl);
			ElementId orderEl;
			if (theOptions.isPreservingSourceOrder())
				orderEl = theSortedRepresentatives.addElement(srcEl, true).getElementId();
			else
				orderEl = theSortedRepresentatives.addElement(valueEntry.getElementId(), true).getElementId();
			int repIndex = theSortedRepresentatives.getElementsBefore(orderEl);
			added(repIndex, value, helper);
		}
	}

	@Override
	public void removedFromBelow(int index, TestHelper helper) {
		CollectionElement<E> srcEl = theSourceValues.getElement(index);
		E value=srcEl.get();
		MapEntryHandle<E, BetterSortedMap<ElementId, E>> valueEntry = theValues.getEntry(value);
		valueEntry.get().remove(srcEl.getElementId());
		MapEntryHandle<ElementId, ElementId> repEntry = theRepresentativeElements.getEntry(valueEntry.getElementId());
		if (repEntry.get().equals(srcEl.getElementId())) {
			// The removed element was the representative for its category. Need to transition to a different element.
			CollectionElement<ElementId> orderEl;
			if (theOptions.isPreservingSourceOrder())
				orderEl = theSortedRepresentatives.getElement(srcEl.getElementId(), true);
			else
				orderEl = theSortedRepresentatives.getElement(valueEntry.getElementId(), true);
			int oldIndex = theSortedRepresentatives.getElementsBefore(orderEl.getElementId());
			theSortedRepresentatives.mutableElement(orderEl.getElementId()).remove();
			if (valueEntry.get().isEmpty()) {
				// No more elements in the category.The element will be removed from the derived collection.
				theRepresentativeElements.mutableEntry(repEntry.getElementId()).remove();
				theValues.mutableEntry(valueEntry.getElementId()).remove();
				removed(oldIndex, helper);
			} else {
				// Need to transition to the new first element in the category
				Map.Entry<ElementId, E> newFirstSrcEntry = valueEntry.get().firstEntry();
				theRepresentativeElements.mutableEntry(repEntry.getElementId()).set(newFirstSrcEntry.getKey());
				orderEl = theSortedRepresentatives.addElement(newFirstSrcEntry.getKey(), true);
				int newIndex = theSortedRepresentatives.getElementsBefore(orderEl.getElementId());
				if (oldIndex != newIndex) {
					removed(oldIndex, helper);
					added(newIndex, newFirstSrcEntry.getValue(), helper);
				} else {
					set(oldIndex, newFirstSrcEntry.getValue(), helper);
				}
			}
		} else {
			// The removed element was not the representative for its category. No change to the derived collection.
			Assert.assertFalse(valueEntry.get().isEmpty());
		}
		theSourceValues.mutableElement(srcEl.getElementId()).remove();
	}

	@Override
	public void setFromBelow(int index, E value, TestHelper helper) {
		CollectionElement<E> srcEl = theSourceValues.getElement(index);
		E oldValue = srcEl.get();
		MapEntryHandle<E, BetterSortedMap<ElementId, E>> oldValueEntry = theValues.getEntry(oldValue);
		MapEntryHandle<E, BetterSortedMap<ElementId, E>> newValueEntry = theValues.getEntry(value);
		if (oldValueEntry.getElementId().equals(newValueEntry.getElementId())) {
			theSourceValues.mutableElement(srcEl.getElementId()).set(value);
			newValueEntry.get().put(srcEl.getElementId(), value);
			// Category is unchanged
			if (theRepresentativeElements.get(newValueEntry.getElementId()).equals(srcEl.getElementId())) {
				// The updated value is the representative for its category. Fire the update event.
				int repIndex = theSortedRepresentatives
					.getElementsBefore(theSortedRepresentatives.getElement(srcEl.getElementId()).getElementId());
				set(repIndex, value, helper);
			} else {
				// The update value is not the representative for its category. No change to the derived collection.
			}
		} else {
			// Category has been changed. Same as a remove, then an add.
			removedFromBelow(index, helper);
			addedFromBelow(index, value, helper);
		}
	}

	@Override
	public void addedFromAbove(int index, E value, TestHelper helper) {
		if (theSourceValues.isEmpty() || index < 0) {
			addedFromBelow(-1, value, helper);
		} else if (index >= 0) {
			boolean addBefore = index > 0;
			ElementId addRep = theRepresentativeElements.get(getValueHandle(addBefore ? index : index - 1).getElementId());
			addedFromBelow(theSourceValues.getElementsBefore(addRep) + (addBefore ? 0 : 1), value, helper);
		}
	}

	@Override
	public int removedFromAbove(int index, E value, TestHelper helper) {
		ElementId valueEl = theValues.getEntry(value).getElementId();
		ElementId repEl = theRepresentativeElements.remove(valueEl);
		int srcIndex = theSourceValues.getElementsBefore(repEl);
		if (theOptions.isPreservingSourceOrder())
			theSortedRepresentatives.remove(repEl);
		else
			theSortedRepresentatives.remove(valueEl);
		for (ElementId srcEl : theValues.getEntryById(valueEl).get().keySet())
			theSourceValues.mutableElement(srcEl).remove();
		theValues.mutableEntry(valueEl).remove();
		return srcIndex;
	}

	@Override
	public void setFromAbove(int index, E value, TestHelper helper) {
		MapEntryHandle<E, BetterSortedMap<ElementId, E>> valueEntry = getValueHandle(index);
		BetterSortedMap<ElementId, E> values = valueEntry.get();
		if (!theOptions.isPreservingSourceOrder())
			theSortedRepresentatives.remove(valueEntry.getElementId());
		theValues.mutableEntry(valueEntry.getElementId()).remove();
		ElementId newEntry = theValues.putEntry(value, values).getElementId();
		for (Map.Entry<ElementId, E> entry : values.entrySet())
			theSourceValues.mutableElement(entry.getKey()).set(value);
		if (!theOptions.isPreservingSourceOrder())
			theSortedRepresentatives.add(newEntry);
	}
}