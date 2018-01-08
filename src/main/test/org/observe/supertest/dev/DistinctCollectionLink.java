package org.observe.supertest.dev;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.junit.Assert;
import org.observe.collect.Equivalence;
import org.observe.collect.FlowOptions;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.supertest.dev.ObservableChainTester.TestValueType;
import org.qommons.TestHelper;
import org.qommons.ValueHolder;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterMap;
import org.qommons.collect.BetterSet;
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
		TestHelper helper, boolean checkRemovedValues, FlowOptions.UniqueOptions options) {
		super(parent, type, flow, helper, isRebasedFlowRequired(options, flow.equivalence()), checkRemovedValues);
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

	/**
	 * This is a hack. If the flow is not rebased for hash-based distinctness, the order for different collections off of the same distinct
	 * flow may not be consistent.
	 *
	 * @see org.observe.supertest.AbstractObservableCollectionLink#isRebasedFlowRequired()
	 */
	private static boolean isRebasedFlowRequired(FlowOptions.UniqueOptions options, Equivalence<?> equivalence) {
		return !options.isPreservingSourceOrder() && !(equivalence instanceof org.observe.collect.Equivalence.ComparatorEquivalence);
	}

	protected BetterMap<E, BetterSortedMap<ElementId, E>> getValues() {
		return theValues;
	}

	@Override
	public void checkAddable(List<CollectionOp<E>> adds, int subListStart, int subListEnd, TestHelper helper) {
		if (adds.isEmpty())
			return;
		// Check the parent first, since if the parent rejects an add, that op shouldn't interfere with future adds
		if(getParent()!=null)
			getParent().checkAddable(adds, 0, theSourceValues.size(), helper);
		BetterSet<E> addedSet = getCollection().equivalence().createSet();
		for (CollectionOp<E> add : adds) {
			if (add.getMessage() != null)
				continue;
			if (!addedSet.add(add.source)) {
				add.reject(StdMsg.ELEMENT_EXISTS, true);
				continue;
			}

			MapEntryHandle<E, BetterSortedMap<ElementId, E>> valueEntry = theValues.getEntry(add.source);
			MapEntryHandle<E, BetterSortedMap<ElementId, E>> valueHandle;
			// TODO All the index adding here is for isPreservingSourceOrder=false. Rewrite for true.
			if (valueEntry != null) {
				add.reject(StdMsg.ELEMENT_EXISTS, false);
			} else if (add.index >= 0) {
				if ((subListStart + add.index) == 0 && theValues.isEmpty()) {
					add.reject(theValues.keySet().canAdd(add.source), true);
				} else if (theOptions.isPreservingSourceOrder()) {
					throw new IllegalStateException("Not implemented");
				} else {
					boolean addBefore = subListStart + add.index == 0;
					if (addBefore)
						valueHandle = getValueHandle(subListStart + add.index);
					else
						valueHandle = getValueHandle(subListStart + add.index - 1);
					add.reject(theValues.keySet().mutableElement(valueHandle.getElementId()).canAdd(add.source, addBefore), true);
				}
			} else if (subListStart > 0 || subListEnd < theValues.size()) {
				if (theOptions.isPreservingSourceOrder()) {
					throw new IllegalStateException("Not implemented");
				} else {
					valueHandle = getValueHandle(subListStart);

					add.reject(theValues.keySet().mutableElement(valueHandle.getElementId()).canAdd(add.source, true), true);
					if (add.getMessage() != null) {
						valueHandle = getValueHandle(subListEnd - 1);
						add.reject(theValues.keySet().mutableElement(valueHandle.getElementId()).canAdd(add.source, false), true);
					}
				}
			}
		}
	}

	@Override
	public void checkRemovable(List<CollectionOp<E>> removes, int subListStart, int subListEnd, TestHelper helper) {
		List<CollectionOp<E>> parentRemoves=getParent()==null ? null :  new ArrayList<>(removes.size());
		for(CollectionOp<E> remove : removes){
			MapEntryHandle<E, BetterSortedMap<ElementId, E>> valueEntry;
			if (remove.index < 0)
				valueEntry = theValues.getEntry(remove.source);
			else
				valueEntry = getValueHandle(subListStart + remove.index);
			if (valueEntry == null) {
				remove.reject(StdMsg.NOT_FOUND, false);
			} else if (subListStart > 0 || subListEnd < theValues.size()) {
				int index = getElementIndex(valueEntry.getElementId());
				if (index < subListStart || index >= subListEnd)
					remove.reject(StdMsg.NOT_FOUND, false);
			}
			if (remove.getMessage() == null && getParent() != null) {
				for (ElementId srcId : valueEntry.get().keySet()) {
					parentRemoves.add(new CollectionOp<>(remove, theSourceValues.getElement(srcId).get(),
						theSourceValues.getElementsBefore(srcId)));
				}
			}
		}
		getParent().checkRemovable(parentRemoves, 0, theSourceValues.size(), helper);
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
	public void checkSettable(List<CollectionOp<E>> sets, int subListStart, TestHelper helper) {
		List<CollectionOp<E>> parentSets = getParent() == null ? null : new ArrayList<>();
		for (CollectionOp<E> set : sets) {
			MapEntryHandle<E, BetterSortedMap<ElementId, E>> oldValueEntry = getValueHandle(subListStart + set.index);
			MapEntryHandle<E, BetterSortedMap<ElementId, E>> newValueEntry = theValues.getEntry(set.source);
			if (newValueEntry != null && !newValueEntry.getElementId().equals(oldValueEntry.getElementId())) {
				set.reject(StdMsg.ELEMENT_EXISTS, true);
				continue;
			}
			if (!theOptions.isPreservingSourceOrder()) {
				set.reject(theValues.keySet().mutableElement(oldValueEntry.getElementId()).isAcceptable(set.source), true);
				if (set.getMessage() != null)
					continue;
			}
			if (parentSets != null) {
				for (ElementId srcId : oldValueEntry.get().keySet()) {
					CollectionOp<E> parentSet = new CollectionOp<>(set, set.source, theSourceValues.getElementsBefore(srcId));
					parentSets.add(parentSet);
				}
			}
		}
		if (parentSets != null)
			getParent().checkSettable(parentSets, 0, helper);
	}

	@Override
	public void addedFromBelow(List<CollectionOp<E>> adds, TestHelper helper) {
		List<CollectionOp<E>> distinctAdds = new ArrayList<>();
		for (CollectionOp<E> add : adds) {
			Iterator<ElementId> nsvIter = theNewSourceValues.iterator();
			boolean found = false;
			int passed = 0;
			int srcIndex = 0;
			while (!found) {
				CollectionElement<E> el;
				try {
					el = getParent().getCollection().getElement(nsvIter.next());
				} catch (NoSuchElementException e) {
					System.err.println("On Link " + getLinkIndex() + " for " + add);
					throw e;
				}
				if (getCollection().equivalence().elementEquals(el.get(), add.source)) {
					srcIndex = getParent().getCollection().getElementsBefore(el.getElementId()) - passed;
					if (add.index < 0 || add.index == srcIndex)
						found = true;
				}
				if (found)
					nsvIter.remove();
				else
					passed++;
			}
			CollectionOp<E> da = add(srcIndex, add.source, -1, helper, true);
			if (da != null)
				distinctAdds.add(da);
		}
		added(distinctAdds, helper, true);
	}

	private CollectionOp<E> add(int srcIndex, E value, int destIndex, TestHelper helper, boolean propagateUp) {
		ElementId srcEl = theSourceValues.addElement(srcIndex, value).getElementId();
		MapEntryHandle<E, BetterSortedMap<ElementId, E>> valueEntry = theValues.getEntry(value);
		if (valueEntry != null && !valueEntry.getValue().isEmpty()) {
			MapEntryHandle<ElementId, E> valueSourceEntry = valueEntry.get().putEntry(srcEl, value, false);
			if (theOptions.isUseFirst() && valueEntry.get().keySet().getElementsBefore(valueSourceEntry.getElementId()) == 0) {
				// The new value is replacing the old value as the representative element
				ElementId oldRep = theRepresentativeElements.put(valueEntry.getElementId(), srcEl);
				int oldIndex, newIndex;
				if (theOptions.isPreservingSourceOrder()) {
					// The order of the representative element may have changed
					CollectionElement<ElementId> orderEl = theSortedRepresentatives.getElement(oldRep, true); // Get by value, not element
					oldIndex = theSortedRepresentatives.getElementsBefore(orderEl.getElementId());
					theSortedRepresentatives.mutableElement(orderEl.getElementId()).remove();
					orderEl = theSortedRepresentatives.addElement(srcEl, true);
					newIndex = theSortedRepresentatives.getElementsBefore(orderEl.getElementId());
				} else {
					// The order in the derived collection cannot have been changed
					oldIndex = newIndex = getElementIndex(valueEntry.getElementId());
				}
				if (oldIndex != newIndex) {
					removed(oldIndex, helper, propagateUp);
					return new CollectionOp<>(null, value, newIndex);
				} else {
					set(oldIndex, value, helper, propagateUp);
					return null;
				}
			} else
				return null;
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
			return new CollectionOp<>(null, value, repIndex);
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
					added(Arrays.asList(new CollectionOp<>(null, newFirstSrcEntry.getValue(), newIndex)), helper, true);
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
			// Category has been changed.
			if (oldValueEntry.get().size() == 1
				&& theValues.keySet().mutableElement(oldValueEntry.getElementId()).isAcceptable(value) == null) {
				theSourceValues.mutableElement(srcEl.getElementId()).set(value);
				theValues.keySet().mutableElement(oldValueEntry.getElementId()).set(value);
				ElementId repId = theOptions.isPreservingSourceOrder() ? srcEl.getElementId() : oldValueEntry.getElementId();
				// The updated value is the representative for its category. Fire the update event.
				int repIndex = theSortedRepresentatives.indexOf(repId);
				set(repIndex, value, helper, true);
			} else {
				// Same as a remove, then an add.
				removedFromBelow(index, helper);
				CollectionOp<E> distinctAdd = add(index, value, -1, helper, true);
				if (distinctAdd != null)
					added(Arrays.asList(distinctAdd), helper, true);
			}
		}
	}

	@Override
	public void addedFromAbove(List<CollectionOp<E>> adds, TestHelper helper, boolean above) {
		List<CollectionOp<E>> parentAdds = getParent() == null ? null : new ArrayList<>(adds.size());
		List<CollectionOp<E>> distinctAdds = new ArrayList<>(adds.size());
		for (CollectionOp<E> add : adds) {
			BetterSortedSet<ElementId> subSet;
			if (theOptions.isPreservingSourceOrder()) {
				// The inserted element must be between the representatives at index-1 and index in the parent collection
				if (theSortedRepresentatives.isEmpty())
					subSet = theNewSourceValues;
				else if (add.index > 0 && add.index < theSortedRepresentatives.size())
					subSet = theNewSourceValues.subSet(theSortedRepresentatives.get(add.index - 1), false,
						theSortedRepresentatives.get(add.index), false);
				else if (add.index > 0)
					subSet = theNewSourceValues.tailSet(theSortedRepresentatives.get(add.index - 1), false);
				else
					subSet = theNewSourceValues.headSet(theSortedRepresentatives.get(add.index), false);
			} else {
				// If source order is not preserved, we don't care where the new value was inserted
				subSet = theNewSourceValues;
			}
			int[] sourceIndex = new int[] { -1 };
			subSet.spliterator().forEachElementM(el -> {
				if (sourceIndex[0] < 0 && getParent().getCollection().equivalence()
					.elementEquals(getParent().getCollection().getElement(el.get()).get(), add.source)) {
					sourceIndex[0] = getParent().getCollection().getElementsBefore(el.get())
						- theNewSourceValues.getElementsBefore(el.getElementId());
					el.remove();
				}
			}, true);
			parentAdds.add(new CollectionOp<>(null, add.source, sourceIndex[0]));
			CollectionOp<E> da = add(sourceIndex[0], add.source, add.index, helper, !above);
			if (da != null)
				distinctAdds.add(da);
		}
		getParent().addedFromAbove(parentAdds, helper, true);
		added(distinctAdds, helper, !above);
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
		MapEntryHandle<E, BetterSortedMap<ElementId, E>> newValueEntry = theValues.getEntry(value);
		if (newValueEntry != null && !newValueEntry.getElementId().equals(valueEntry.getElementId())) {
			if (!above)
				Assert.assertTrue("This should not happen", false);
			// It is permissible for a set operation from a derived collection to result in an element in a distinct collection being
			// merged with another (i.e. disappearing)
			ElementId srcId = theRepresentativeElements.remove(valueEntry.getElementId());
			if (theOptions.isPreservingSourceOrder())
				theSortedRepresentatives.remove(srcId);
			else
				theSortedRepresentatives.remove(valueEntry.getElementId());
			theValues.mutableEntry(valueEntry.getElementId()).remove();
			removed(index, helper, true);
			for (Map.Entry<ElementId, E> entry : values.entrySet()) {
				int sourceIndex = theSourceValues.getElementsBefore(entry.getKey());
				theSourceValues.mutableElement(entry.getKey()).remove();
				add(sourceIndex, value, -1, helper, true);
				getParent().setFromAbove(sourceIndex, value, helper, true);
			}
			return;
		}
		if (theOptions.isPreservingSourceOrder()) {
			if (theValues.keySet().mutableElement(valueEntry.getElementId()).isAcceptable(value) == null)
				theValues.keySet().mutableElement(valueEntry.getElementId()).set(value);
			else {
				theValues.mutableEntry(valueEntry.getElementId()).remove();
				theValues.putEntry(value, values, false).getElementId();
			}
		} else {
			theValues.keySet().mutableElement(valueEntry.getElementId()).set(value);
			Assert.assertEquals(index, getElementIndex(valueEntry.getElementId())); // Set operations are not allowed to modify order
		}
		set(index, value, helper, !above);
		// Need to copy the entries, because set operations from above can cause unintended side effects (e.g. removal)
		// Representative element goes first
		ElementId repId = theRepresentativeElements.get(valueEntry.getElementId());
		getParent().setFromAbove(//
			theSourceValues.getElementsBefore(repId), value, helper, true);
		for (Map.Entry<ElementId, E> entry : new ArrayList<>(values.entrySet())) {
			theSourceValues.mutableElement(entry.getKey()).set(value);
			entry.setValue(value);
			if (!entry.getKey().equals(repId))
				getParent().setFromAbove(//
					theSourceValues.getElementsBefore(entry.getKey()), value, helper, true);
		}
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
