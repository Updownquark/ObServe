package org.observe.supertest;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
	private final FlowOptions.UniqueOptions theOptions;
	/** A map of value to equivalent values, grouped by source ID */
	private final BetterMap<E, BetterSortedMap<ElementId, E>> theValues;

	/** A parallel representation of the values in the source (parent) collection */
	private final BetterList<E> theSourceValues;
	private final BetterSortedSet<ElementId> theNewSourceValues;
	/** A map of value entry ID (in {@link #theValues}) to the source element representing the value (in {@link #theSourceValues}) */
	private final BetterMap<ElementId, ElementId> theRepresentativeElements;
	/**
	 * The sorted set of elements for the distinct-ified collection. If {@link FlowOptions.GroupingDef#isPreservingSourceOrder() source
	 * order} is preserved, then these elements are from {@link #theSourceValues}. Otherwise, they are from {@link #theValues}.
	 */
	private final BetterSortedSet<ElementId> theSortedRepresentatives;

	public DistinctCollectionLink(ObservableCollectionChainLink<?, E> parent, TestValueType type, CollectionDataFlow<?, ?, E> flow,
		TestHelper helper, FlowOptions.UniqueOptions options) {
		super(parent, type, flow, helper);
		theOptions = options;
		theValues = flow.equivalence().createMap();
		theSourceValues = new BetterTreeList<>(false);
		theNewSourceValues = new BetterTreeSet<>(false, ElementId::compareTo);

		theRepresentativeElements = new BetterTreeMap<>(true, ElementId::compareTo);
		theSortedRepresentatives = new BetterTreeSet<>(true, ElementId::compareTo);

		getParent().getCollection().onChange(evt -> {
			switch (evt.getType()) {
			case add:
				theNewSourceValues.add(evt.getElementId());
				break;
			default:
			}
		});
		for (E src : getParent().getCollection()) {
			ElementId srcId = theSourceValues.addElement(src, false).getElementId();
			MapEntryHandle<E, BetterSortedMap<ElementId, E>> valueEntry = theValues.getEntry(src);
			if (valueEntry == null) {
				getExpected().add(src);
				valueEntry = theValues.putEntry(src, new BetterTreeMap<>(false, ElementId::compareTo), false);
				theRepresentativeElements.put(valueEntry.getElementId(), srcId);
				if (theOptions.isPreservingSourceOrder())
					theSortedRepresentatives.add(srcId);
				else
					theSortedRepresentatives.add(valueEntry.getElementId());
			}
			valueEntry.get().put(srcId, src);
		}
	}

	protected BetterMap<E, BetterSortedMap<ElementId, E>> getValues() {
		return theValues;
	}

	@Override
	public void checkAddable(List<CollectionOp<E>> add, int subListStart, int subListEnd, TestHelper helper) {
		Set<E> duplicates = getCollection().equivalence().createSet();
		add.stream().forEach(a -> {
			if (duplicates.add(a.source))
				checkAddable(a, subListStart, subListEnd, helper);
			else {
				a.message = StdMsg.ELEMENT_EXISTS;
				a.isError = true;
			}
		});
	}

	@Override
	public void checkAddable(CollectionOp<E> add, int subListStart, int subListEnd, TestHelper helper) {
		MapEntryHandle<E, BetterSortedMap<ElementId, E>> valueEntry = theValues.getEntry(add.source);
		MapEntryHandle<E, BetterSortedMap<ElementId, E>> valueHandle;
		// TODO All the index adding here is for isPreservingSourceOrder=false. Rewrite for true.
		if (valueEntry != null) {
			add.message = StdMsg.ELEMENT_EXISTS;
		} else if (add.index >= 0) {
			if ((subListStart + add.index) == 0 && theValues.isEmpty()) {
				add.message = theValues.keySet().canAdd(add.source);
			} else if (theOptions.isPreservingSourceOrder()) {
				throw new IllegalStateException("Not implemented");
			} else {
				boolean addBefore = add.index == 0;
				if (addBefore)
					valueHandle = getValueHandle(subListStart + add.index);
				else
					valueHandle = getValueHandle(subListStart + add.index - 1);
				add.message = theValues.keySet().mutableElement(valueHandle.getElementId()).canAdd(add.source, addBefore);
			}
		} else if (subListStart > 0 || subListEnd < theValues.size()) {
			if (theOptions.isPreservingSourceOrder()) {
				throw new IllegalStateException("Not implemented");
			} else {
				valueHandle = getValueHandle(subListStart);
				add.message = theValues.keySet().mutableElement(valueHandle.getElementId()).canAdd(add.source, true);
				if (add.message != null) {
					valueHandle = getValueHandle(subListEnd - 1);
					add.message = theValues.keySet().mutableElement(valueHandle.getElementId()).canAdd(add.source, false);
				}
			}
		}
		if (add.message != null) {
			add.isError = true;
			return;
		}
		CollectionOp<E> parentOp = new CollectionOp<>(add.source, -1);
		getParent().checkAddable(parentOp, 0, theSourceValues.size(), helper);
		add.message = parentOp.message;
		add.isError = parentOp.isError;
	}

	@Override
	public void checkRemovable(CollectionOp<E> remove, int subListStart, int subListEnd, TestHelper helper) {
		MapEntryHandle<E, BetterSortedMap<ElementId, E>> valueEntry;
		if (remove.index < 0)
			valueEntry = theValues.getEntry(remove.source);
		else
			valueEntry = getValueHandle(subListStart + remove.index);
		if (valueEntry == null) {
			remove.message = StdMsg.NOT_FOUND;
		} else if (subListStart > 0 || subListEnd < theValues.size()) {
			int index = getElementIndex(valueEntry.getElementId());
			if (index < subListStart || index >= subListEnd)
				remove.message = StdMsg.NOT_FOUND;
		}
		if (remove.message == null && getParent() != null) {
			for (ElementId srcId : valueEntry.get().keySet()) {
				CollectionOp<E> parentRemove = new CollectionOp<>(theSourceValues.getElement(srcId).get(),
					theSourceValues.getElementsBefore(srcId));
				getParent().checkRemovable(parentRemove, 0, theSourceValues.size(), helper);
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
			int valSize = theValues.size();
			if (index < 0 || index >= valSize)
				throw new IndexOutOfBoundsException(index + " of " + valSize);
			// No choice but to iterate
			boolean fromStart = index <= valSize / 2;
			int i = fromStart ? -1 : valSize;
			ElementSpliterator<?> spliter = theValues.keySet().spliterator(fromStart);
			ValueHolder<ElementId> valueEl = new ValueHolder<>();
			do {
				spliter.forElement(el -> valueEl.accept(el.getElementId()), fromStart);
				i += fromStart ? 1 : -1;
			} while (i != index);
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
			if (!elId.get().equals(id))
				throw new IllegalStateException();
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
			return;
		}
		if (!theOptions.isPreservingSourceOrder() && !getCollection().equivalence().elementEquals(oldValueEntry.getKey(), set.source)) {
			set.message = StdMsg.UNSUPPORTED_OPERATION;
			set.isError = true;
			return;
		}
		if (getParent() != null) {
			for (ElementId srcId : oldValueEntry.get().keySet()) {
				CollectionOp<E> parentSet = new CollectionOp<>(set.source, theSourceValues.getElementsBefore(srcId));
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
		Iterator<ElementId> nsvIter = theNewSourceValues.iterator();
		boolean found = false;
		int passed = 0;
		int srcIndex = 0;
		while (!found) {
			CollectionElement<E> el = getParent().getCollection().getElement(nsvIter.next());
			srcIndex = getParent().getCollection().getElementsBefore(el.getElementId()) - passed;
			if (index >= 0 && index == srcIndex)
				found = true;
			else if (index < 0 && getCollection().equivalence().elementEquals(el.get(), value))
				found = true;
			if (found)
				nsvIter.remove();
			else
				passed++;
		}
		add(srcIndex, value, -1, helper, true);
	}

	private void add(int srcIndex, E value, int destIndex, TestHelper helper, boolean propagateUp) {
		ElementId srcEl = theSourceValues.addElement(srcIndex, value).getElementId();
		MapEntryHandle<E, BetterSortedMap<ElementId, E>> valueEntry = theValues.getEntry(value);
		if (valueEntry != null && !valueEntry.getValue().isEmpty()) {
			MapEntryHandle<ElementId, E> valueSoruceEntry = valueEntry.get().putEntry(srcEl, value, false);
			if (theOptions.isUseFirst() && valueEntry.get().keySet().getElementsBefore(valueSoruceEntry.getElementId()) == 0) {
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
						removed(oldIndex, helper, propagateUp);
						added(newIndex, value, helper, propagateUp);
					} else
						set(oldIndex, value, helper, propagateUp);
				} else {
					// The order in the derived collection cannot have been changed
				}
			}
		} else {
			// The new value is the first in its category
			if (valueEntry == null) {
				// Add at the correct position if specified
				if (destIndex < 0 || theValues.isEmpty())
					valueEntry = theValues.putEntry(value, new BetterTreeMap<>(false, ElementId::compareTo), false);
				else {
					ElementId addedEntryId;
					if (destIndex == 0)
						addedEntryId = theValues.keySet().mutableElement(getValueHandle(0).getElementId()).add(value, true);
					else
						addedEntryId = theValues.keySet().mutableElement(getValueHandle(destIndex - 1).getElementId()).add(value, false);
					theValues.mutableEntry(addedEntryId).set(new BetterTreeMap<>(false, ElementId::compareTo));
					valueEntry = theValues.getEntryById(addedEntryId);
				}
			}
			valueEntry.get().put(srcEl, value);
			theRepresentativeElements.put(valueEntry.getElementId(), srcEl);
			ElementId orderEl;
			if (theOptions.isPreservingSourceOrder())
				orderEl = theSortedRepresentatives.addElement(srcEl, true).getElementId();
			else
				orderEl = theSortedRepresentatives.addElement(valueEntry.getElementId(), true).getElementId();
			int repIndex = theSortedRepresentatives.getElementsBefore(orderEl);
			added(repIndex, value, helper, propagateUp);
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
			if (valueEntry.get().isEmpty()) {
				// No more elements in the category.The element will be removed from the derived collection.
				theSortedRepresentatives.mutableElement(orderEl.getElementId()).remove();
				theRepresentativeElements.mutableEntry(repEntry.getElementId()).remove();
				theValues.mutableEntry(valueEntry.getElementId()).remove();
				removed(oldIndex, helper, true);
			} else {
				// Need to transition to the new first element in the category
				Map.Entry<ElementId, E> newFirstSrcEntry = valueEntry.get().firstEntry();
				theRepresentativeElements.mutableEntry(repEntry.getElementId()).set(newFirstSrcEntry.getKey());
				int newIndex;
				if (theOptions.isPreservingSourceOrder()) {
					theSortedRepresentatives.mutableElement(orderEl.getElementId()).remove();
					orderEl = theSortedRepresentatives.addElement(newFirstSrcEntry.getKey(), true);
					newIndex = theSortedRepresentatives.getElementsBefore(orderEl.getElementId());
				} else
					newIndex = oldIndex;
				if (oldIndex != newIndex) {
					removed(oldIndex, helper, true);
					added(newIndex, newFirstSrcEntry.getValue(), helper, true);
				} else
					set(oldIndex, newFirstSrcEntry.getValue(), helper, true);
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
		if (newValueEntry != null && oldValueEntry.getElementId().equals(newValueEntry.getElementId())) {
			theSourceValues.mutableElement(srcEl.getElementId()).set(value);
			newValueEntry.get().put(srcEl.getElementId(), value);
			// Category is unchanged
			if (theRepresentativeElements.get(newValueEntry.getElementId()).equals(srcEl.getElementId())) {
				ElementId repId = theOptions.isPreservingSourceOrder() ? srcEl.getElementId() : newValueEntry.getElementId();
				// The updated value is the representative for its category. Fire the update event.
				int repIndex = theSortedRepresentatives.indexOf(repId);
				set(repIndex, value, helper, true);
			} else {
				// The update value is not the representative for its category. No change to the derived collection.
			}
		} else {
			// Category has been changed. Same as a remove, then an add.
			theNewSourceValues.add(getParent().getCollection().getElement(index).getElementId());
			removedFromBelow(index, helper);
			addedFromBelow(index, value, helper);
		}
	}

	@Override
	public void addedFromAbove(int index, E value, TestHelper helper, boolean above) {
		BetterSortedSet<ElementId> subSet;
		if (theOptions.isPreservingSourceOrder()) {
			// The inserted element must be between the representatives at index-1 and index in the parent collection
			if (theSortedRepresentatives.isEmpty())
				subSet = theNewSourceValues;
			else if (index > 0 && index < theSortedRepresentatives.size())
				subSet = theNewSourceValues.subSet(theSortedRepresentatives.get(index - 1), false, theSortedRepresentatives.get(index),
					false);
			else if (index > 0)
				subSet = theNewSourceValues.tailSet(theSortedRepresentatives.get(index - 1), false);
			else
				subSet = theNewSourceValues.headSet(theSortedRepresentatives.get(index), false);
		} else {
			// If source order is not preserved, we don't care where the new value was inserted
			subSet = theNewSourceValues;
		}
		int[] sourceIndex = new int[] { -1 };
		subSet.spliterator().forEachElementM(el -> {
			if (sourceIndex[0] < 0
				&& getParent().getCollection().equivalence().elementEquals(getParent().getCollection().getElement(el.get()).get(), value)) {
				sourceIndex[0] = getParent().getCollection().getElementsBefore(el.get())
					- theNewSourceValues.getElementsBefore(el.getElementId());
				el.remove();
			}
		}, true);
		getParent().addedFromAbove(sourceIndex[0], value, helper, true);
		add(sourceIndex[0], value, index, helper, !above);
	}

	@Override
	public void removedFromAbove(int index, E value, TestHelper helper, boolean above) {
		ElementId valueEl = theValues.getEntry(value).getElementId();
		ElementId repEl = theRepresentativeElements.remove(valueEl);
		if (theOptions.isPreservingSourceOrder())
			theSortedRepresentatives.remove(repEl);
		else
			theSortedRepresentatives.remove(valueEl);
		for (ElementId srcEl : theValues.getEntryById(valueEl).get().keySet()){
			CollectionElement<E> srcValEntry = theSourceValues.getElement(srcEl);
			int srcIndex = theSourceValues.getElementsBefore(srcValEntry.getElementId());
			getParent().removedFromAbove(srcIndex, srcValEntry.get(), helper, true);
			theSourceValues.mutableElement(srcValEntry.getElementId()).remove();
		}
		theValues.mutableEntry(valueEl).remove();
		removed(index, helper, !above);
	}

	@Override
	public void setFromAbove(int index, E value, TestHelper helper, boolean above) {
		MapEntryHandle<E, BetterSortedMap<ElementId, E>> valueEntry = getValueHandle(index);
		BetterSortedMap<ElementId, E> values = valueEntry.get();
		if (!theOptions.isPreservingSourceOrder())
			theSortedRepresentatives.remove(valueEntry.getElementId());
		theValues.mutableEntry(valueEntry.getElementId()).remove();
		ElementId newEntry = theValues.putEntry(value, values, false).getElementId();
		for (Map.Entry<ElementId, E> entry : values.entrySet())
			theSourceValues.mutableElement(entry.getKey()).set(value);
		if (!theOptions.isPreservingSourceOrder())
			theSortedRepresentatives.add(newEntry);
	}

	@Override
	public void check(boolean transComplete) {
		super.check(transComplete);
		Assert.assertTrue(theNewSourceValues.isEmpty());
	}

	@Override
	public String toString() {
		return "distinct()";
	}
}
