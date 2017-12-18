package org.observe.supertest;

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
import org.qommons.tree.BetterTreeMap;
import org.qommons.tree.BetterTreeSet;

public class DistinctCollectionLink<E> extends AbstractObservableCollectionLink<E, E> {
	private final FlowOptions.UniqueOptions theOptions;
	/** A map of value to equivalent values, grouped by source ID */
	private final BetterMap<E, BetterSortedMap<ElementId, E>> theValues;

	/** A parallel representation of the values in the source (parent) collection */
	private final BetterSortedMap<ElementId, E> theSourceValues;
	private final BetterSortedSet<ElementId> theNewSourceValues;
	/** A map of value element (in {@link #theValues}) to the source element representing the value (in {@link #theSourceValues}) */
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
		theSourceValues = new BetterTreeMap<>(false, ElementId::compareTo);
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
			if (theOptions.isPreservingSourceOrder()) {
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
		getParent().checkAddable(new CollectionOp<>(add.source, -1), 0, theSourceValues.size(), helper);
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
				CollectionOp<E> parentRemove = new CollectionOp<>(null, theSourceValues.headMap(srcId).size());
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
			return;
		}
		if (!theOptions.isPreservingSourceOrder() && !getCollection().equivalence().elementEquals(oldValueEntry.getKey(), set.source)) {
			set.message = StdMsg.UNSUPPORTED_OPERATION;
			set.isError = true;
			return;
		}
		if (getParent() != null) {
			for (ElementId srcId : oldValueEntry.get().keySet()) {
				CollectionOp<E> parentSet = new CollectionOp<>(null, theSourceValues.headMap(srcId).size());
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
		ElementId srcEl;
		if (index >= 0) {
			CollectionElement<ElementId> newElementId;
			if (theSourceValues.isEmpty())
				newElementId = theNewSourceValues.getElement(0);
			else if (index == 0)
				newElementId = theNewSourceValues.search(theSourceValues.firstKey(), BetterSortedSet.SortedSearchFilter.Less);
			else
				newElementId = theNewSourceValues.search(theSourceValues.keySet().get(index - 1),
					BetterSortedSet.SortedSearchFilter.Greater);
			srcEl = newElementId.get();
			while (!getCollection().equivalence().elementEquals(getCollection().getElement(srcEl).get(), value))
				newElementId = theNewSourceValues.getAdjacentElement(newElementId.getElementId(), true);
			theNewSourceValues.mutableElement(newElementId.getElementId()).remove();
		} else {
			ValueHolder<ElementId> newElementId = new ValueHolder<>();
			theNewSourceValues.spliterator().forEachElementM(el -> {
				if (getParent().getCollection().equivalence().elementEquals(//
					getParent().getCollection().getElement(el.get()).get(), value)) {
					newElementId.accept(el.get());
					el.remove();
				}
			}, true);
			srcEl = newElementId.get();
		}
		add(srcEl, value, -1, helper);
	}

	private void add(ElementId srcEl, E value, int destIndex, TestHelper helper) {
		theSourceValues.put(srcEl, value);
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
						removed(oldIndex, helper, true);
						added(newIndex, value, helper, true);
					} else
						set(oldIndex, value, helper, true);
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
			added(repIndex, value, helper, true);
		}
	}

	@Override
	public void removedFromBelow(int index, TestHelper helper) {
		MapEntryHandle<ElementId, E> srcEl = theSourceValues.getEntryById(theSourceValues.keySet().getElement(index).getElementId());
		E value=srcEl.get();
		MapEntryHandle<E, BetterSortedMap<ElementId, E>> valueEntry = theValues.getEntry(value);
		valueEntry.get().remove(srcEl.getKey());
		MapEntryHandle<ElementId, ElementId> repEntry = theRepresentativeElements.getEntry(valueEntry.getElementId());
		if (repEntry.get().equals(srcEl.getKey())) {
			// The removed element was the representative for its category. Need to transition to a different element.
			CollectionElement<ElementId> orderEl;
			if (theOptions.isPreservingSourceOrder())
				orderEl = theSortedRepresentatives.getElement(srcEl.getKey(), true);
			else
				orderEl = theSortedRepresentatives.getElement(valueEntry.getElementId(), true);
			int oldIndex = theSortedRepresentatives.getElementsBefore(orderEl.getElementId());
			theSortedRepresentatives.mutableElement(orderEl.getElementId()).remove();
			if (valueEntry.get().isEmpty()) {
				// No more elements in the category.The element will be removed from the derived collection.
				theRepresentativeElements.mutableEntry(repEntry.getElementId()).remove();
				theValues.mutableEntry(valueEntry.getElementId()).remove();
				removed(oldIndex, helper, true);
			} else {
				// Need to transition to the new first element in the category
				Map.Entry<ElementId, E> newFirstSrcEntry = valueEntry.get().firstEntry();
				theRepresentativeElements.mutableEntry(repEntry.getElementId()).set(newFirstSrcEntry.getKey());
				orderEl = theSortedRepresentatives.addElement(newFirstSrcEntry.getKey(), true);
				int newIndex = theSortedRepresentatives.getElementsBefore(orderEl.getElementId());
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
		theSourceValues.mutableEntry(srcEl.getElementId()).remove();
	}

	@Override
	public void setFromBelow(int index, E value, TestHelper helper) {
		MapEntryHandle<ElementId, E> srcEl = theSourceValues.getEntryById(theSourceValues.keySet().getElement(index).getElementId());
		E oldValue = srcEl.get();
		MapEntryHandle<E, BetterSortedMap<ElementId, E>> oldValueEntry = theValues.getEntry(oldValue);
		MapEntryHandle<E, BetterSortedMap<ElementId, E>> newValueEntry = theValues.getEntry(value);
		if (newValueEntry != null && oldValueEntry.getElementId().equals(newValueEntry.getElementId())) {
			theSourceValues.mutableEntry(srcEl.getElementId()).set(value);
			newValueEntry.get().put(srcEl.getElementId(), value);
			// Category is unchanged
			if (theRepresentativeElements.get(newValueEntry.getElementId()).equals(srcEl.getKey())) {
				// The updated value is the representative for its category. Fire the update event.
				int repIndex = theSortedRepresentatives.headSet(srcEl.getKey()).size();
				set(repIndex, value, helper, true);
			} else {
				// The update value is not the representative for its category. No change to the derived collection.
			}
		} else {
			// Category has been changed. Same as a remove, then an add.
			theNewSourceValues.add(theSourceValues.keySet().get(index));
			removedFromBelow(index, helper);
			addedFromBelow(index, value, helper);
		}
	}

	@Override
	public void addedAll(int index, List<E> values, TestHelper helper) {
		if (index == 0) {
			for (int i = values.size() - 1; i >= 0; i--)
				addedFromAbove(index, values.get(i), helper, false);
		} else
			super.addedAll(index, values, helper);
	}

	@Override
	public void addedFromAbove(int index, E value, TestHelper helper, boolean above) {
		ValueHolder<ElementId> newSourceEl = new ValueHolder<>();
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
		subSet.spliterator().forEachElementM(el -> {
			if (getParent().getCollection().equivalence().elementEquals(getParent().getCollection().getElement(el.get()).get(), value)) {
				newSourceEl.accept(el.get());
				el.remove();
			}
		}, true);
		Assert.assertTrue(newSourceEl.isPresent());
		int sourceIndex = theSourceValues.keySet()
			.getElementsBefore(theSourceValues.putEntry(newSourceEl.get(), value, false).getElementId());
		getParent().addedFromAbove(sourceIndex, value, helper, true);
		add(newSourceEl.get(), value, index, helper);
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
			int srcIndex = theSourceValues.headMap(srcEl, false).size();
			getParent().removedFromAbove(srcIndex, theSourceValues.get(srcEl), helper, true);
			theSourceValues.remove(srcEl);
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
			theSourceValues.put(entry.getKey(), value);
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
