package org.observe.supertest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.observe.collect.CollectionChangeType;
import org.observe.collect.Equivalence;
import org.observe.collect.FlowOptions;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.supertest.ObservableChainTester.TestValueType;
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
import org.qommons.debug.Debug;
import org.qommons.debug.Debug.DebugData;
import org.qommons.tree.BetterTreeList;
import org.qommons.tree.BetterTreeMap;
import org.qommons.tree.BetterTreeSet;

public class DistinctCollectionLink<E> extends AbstractObservableCollectionLink<E, E> {
	private final FlowOptions.UniqueOptions theOptions;
	/** A map of value to equivalent values, grouped by source ID */
	private final BetterMap<E, BetterSortedMap<ElementId, E>> theValues;

	/** A parallel representation of the values in the source (parent) collection */
	private final BetterList<E> theSourceValues;
	private final BetterList<Integer> theNewSourceValues;
	/** A map of value entry ID (in {@link #theValues}) to the source element representing the value (in {@link #theSourceValues}) */
	private final BetterMap<ElementId, ElementId> theRepresentativeElements;
	/**
	 * The sorted set of elements for the distinct-ified collection. If {@link FlowOptions.GroupingDef#isPreservingSourceOrder() source
	 * order} is preserved, then these elements are from {@link #theSourceValues}. Otherwise, they are from {@link #theValues}.
	 */
	private final BetterSortedSet<ElementId> theSortedRepresentatives;

	private final DebugData theDebug;

	public DistinctCollectionLink(ObservableCollectionChainLink<?, E> parent, TestValueType type, CollectionDataFlow<?, ?, E> flow,
		TestHelper helper, boolean checkRemovedValues, FlowOptions.UniqueOptions options) {
		super(parent, type, flow, helper, isRebasedFlowRequired(options, flow.equivalence()), checkRemovedValues);
		theOptions = options;
		theValues = flow.equivalence().createMap();
		theSourceValues = new BetterTreeList<>(false);
		theNewSourceValues = new BetterTreeList<>(false);

		theRepresentativeElements = new BetterTreeMap<>(true, ElementId::compareTo);
		theSortedRepresentatives = new BetterTreeSet<>(true, ElementId::compareTo);

		getParent().getCollection().onChange(evt -> {
			switch (evt.getType()) {
			case add:
				theNewSourceValues.add(evt.getIndex());
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

		theDebug = Debug.d().add("distinctLink");
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
	public void checkModifiable(List<CollectionOp<E>> ops, int subListStart, int subListEnd, TestHelper helper) {
		if (ops.isEmpty())
			return;
		// Assuming all ops are of the same type
		// Check the parent first for adds, since if the parent rejects an add, that op shouldn't interfere with future adds
		BetterSet<E> addedSet;
		List<CollectionOp<E>> parentOps;
		if (ops.get(0).type == CollectionChangeType.add) {
			addedSet = getCollection().equivalence().createSet();
			getParent().checkModifiable(//
				ops.stream().map(op -> new CollectionOp<>(op, op.type, op.value, -1)).collect(Collectors.toList()), 0,
				theSourceValues.size(), helper);
			parentOps = null;
		} else {
			parentOps = new ArrayList<>();
			addedSet = null;
		}
		for (CollectionOp<E> op : ops) {
			switch (op.type) {
			case add:
				if (op.getMessage() != null)
					continue;
				if (!addedSet.add(op.value)) {
					op.reject(StdMsg.ELEMENT_EXISTS, false);
					continue;
				}

				MapEntryHandle<E, BetterSortedMap<ElementId, E>> valueEntry = theValues.getEntry(op.value);
				MapEntryHandle<E, BetterSortedMap<ElementId, E>> valueHandle;
				// TODO All the index adding here is for isPreservingSourceOrder=false. Rewrite for true.
				if (valueEntry != null) {
					op.reject(StdMsg.ELEMENT_EXISTS, false);
				} else if (op.index >= 0) {
					if ((subListStart + op.index) == 0 && theValues.isEmpty()) {
						op.reject(theValues.keySet().canAdd(op.value), true);
					} else if (theOptions.isPreservingSourceOrder()) {
						throw new IllegalStateException("Not implemented");
					} else {
						boolean addBefore = subListStart + op.index == 0;
						if (addBefore)
							valueHandle = getValueHandle(subListStart + op.index);
						else
							valueHandle = getValueHandle(subListStart + op.index - 1);
						op.reject(theValues.keySet().mutableElement(valueHandle.getElementId()).canAdd(op.value, addBefore), true);
					}
				} else if (subListStart > 0 || subListEnd < theValues.size()) {
					if (theOptions.isPreservingSourceOrder()) {
						throw new IllegalStateException("Not implemented");
					} else {
						valueHandle = getValueHandle(subListStart);

						op.reject(theValues.keySet().mutableElement(valueHandle.getElementId()).canAdd(op.value, true), true);
						if (op.getMessage() != null) {
							valueHandle = getValueHandle(subListEnd - 1);
							op.reject(theValues.keySet().mutableElement(valueHandle.getElementId()).canAdd(op.value, false), true);
						}
					}
				}
				break;
			case remove:
				if (op.index < 0)
					valueEntry = theValues.getEntry(op.value);
				else
					valueEntry = getValueHandle(subListStart + op.index);
				if (valueEntry == null) {
					op.reject(StdMsg.NOT_FOUND, false);
				} else if (subListStart > 0 || subListEnd < theValues.size()) {
					int index = getElementIndex(valueEntry.getElementId());
					if (index < subListStart || index >= subListEnd)
						op.reject(StdMsg.NOT_FOUND, false);
				}
				if (op.getMessage() == null && getParent() != null) {
					for (ElementId srcId : valueEntry.get().keySet()) {
						parentOps.add(new CollectionOp<>(op, op.type, theSourceValues.getElement(srcId).get(),
							theSourceValues.getElementsBefore(srcId)));
					}
				}
				break;
			case set:
				MapEntryHandle<E, BetterSortedMap<ElementId, E>> oldValueEntry = getValueHandle(subListStart + op.index);
				MapEntryHandle<E, BetterSortedMap<ElementId, E>> newValueEntry = theValues.getEntry(op.value);
				if (newValueEntry != null && !newValueEntry.getElementId().equals(oldValueEntry.getElementId())) {
					op.reject(StdMsg.ELEMENT_EXISTS, true);
					continue;
				}
				if (!theOptions.isPreservingSourceOrder()) {
					op.reject(theValues.keySet().mutableElement(oldValueEntry.getElementId()).isAcceptable(op.value), true);
					if (op.getMessage() != null)
						continue;
				}
				for (ElementId srcId : oldValueEntry.get().keySet()) {
					CollectionOp<E> parentSet = new CollectionOp<>(op, op.type, op.value, theSourceValues.getElementsBefore(srcId));
					parentOps.add(parentSet);
				}
				break;
			}
		}
		if (parentOps != null)
			getParent().checkModifiable(parentOps, 0, theSourceValues.size(), helper);
	}

	@Override
	public void fromBelow(List<CollectionOp<E>> ops, TestHelper helper) {
		theDebug.act("fromBelow").param("ops", ops).exec();
		List<CollectionOp<E>> distinctOps = new ArrayList<>(ops.size());
		for (CollectionOp<E> op : ops) {
			switch (op.type) {
			case add:
				theDebug.act("addSource").param("@", op.value).exec();
				add(op.index, op.value, -1, distinctOps);
				break;
			case remove:
				theDebug.act("remove").param("@", op.value).exec();
				remove(op.index, distinctOps);
				break;
			case set:
				CollectionElement<E> srcEl = theSourceValues.getElement(op.index);
				E oldValue = srcEl.get();
				theDebug.act("update").param("@", oldValue).param("newValue", op.value).exec();
				MapEntryHandle<E, BetterSortedMap<ElementId, E>> oldValueEntry = theValues.getEntry(oldValue);
				MapEntryHandle<E, BetterSortedMap<ElementId, E>> newValueEntry = theValues.getEntry(op.value);
				if (newValueEntry != null && oldValueEntry.getElementId().equals(newValueEntry.getElementId())) {
					theSourceValues.mutableElement(srcEl.getElementId()).set(op.value);
					newValueEntry.get().put(srcEl.getElementId(), op.value);
					// Category is unchanged
					if (theRepresentativeElements.get(newValueEntry.getElementId()).equals(srcEl.getElementId())) {
						ElementId repId = theOptions.isPreservingSourceOrder() ? srcEl.getElementId() : newValueEntry.getElementId();
						// The updated value is the representative for its category. Fire the update event.
						theDebug.act("update:trueUpdate").exec();
						int repIndex = theSortedRepresentatives.indexOf(repId);
						distinctOps.add(new CollectionOp<>(CollectionChangeType.set, op.value, repIndex));
					} else {
						theDebug.act("update:no-effect").exec();
						// The update value is not the representative for its category. No change to the derived collection.
					}
				} else {
					// Category has been changed.
					if (oldValueEntry.get().size() == 1
						&& theValues.keySet().mutableElement(oldValueEntry.getElementId()).isAcceptable(op.value) == null) {
						theDebug.act("update:move").exec();
						theSourceValues.mutableElement(srcEl.getElementId()).set(op.value);
						theValues.keySet().mutableElement(oldValueEntry.getElementId()).set(op.value);
						ElementId repId = theOptions.isPreservingSourceOrder() ? srcEl.getElementId() : oldValueEntry.getElementId();
						// The updated value is the representative for its category. Fire the update event.
						int repIndex = theSortedRepresentatives.indexOf(repId);
						distinctOps.add(new CollectionOp<>(CollectionChangeType.set, op.value, repIndex));
					} else {
						// Same as a remove, then an add.
						remove(op.index, distinctOps);
						add(op.index, op.value, -1, distinctOps);
					}
				}
			}
		}
		modified(distinctOps, helper, true);
	}

	@Override
	public void fromAbove(List<CollectionOp<E>> ops, TestHelper helper, boolean above) {
		theDebug.act("fromAbove").param("ops", ops).exec();
		List<CollectionOp<E>> parentOps = new ArrayList<>(ops.size());
		List<CollectionOp<E>> distinctOps = new ArrayList<>();
		for (CollectionOp<E> op : ops) {
			switch (op.type) {
			case add:
				int srcIndex = theNewSourceValues.removeFirst();
				parentOps.add(new CollectionOp<>(CollectionChangeType.add, op.value, srcIndex));
				add(srcIndex, op.value, op.index, distinctOps);
				break;
			case remove:
				ElementId valueEl = getValueHandle(op.index).getElementId();
				ElementId repEl = theRepresentativeElements.remove(valueEl);
				if (theOptions.isPreservingSourceOrder())
					theSortedRepresentatives.remove(repEl);
				else
					theSortedRepresentatives.remove(valueEl);
				for (ElementId srcEl : theValues.getEntryById(valueEl).get().keySet()) {
					CollectionElement<E> srcValEntry = theSourceValues.getElement(srcEl);
					srcIndex = theSourceValues.getElementsBefore(srcValEntry.getElementId());
					parentOps.add(new CollectionOp<>(CollectionChangeType.remove, srcValEntry.get(), srcIndex));
					theSourceValues.mutableElement(srcValEntry.getElementId()).remove();
				}
				distinctOps.add(op);
				theValues.mutableEntry(valueEl).remove();
				break;
			case set:
				MapEntryHandle<E, BetterSortedMap<ElementId, E>> valueEntry = getValueHandle(op.index);
				BetterSortedMap<ElementId, E> values = valueEntry.get();
				MapEntryHandle<E, BetterSortedMap<ElementId, E>> newValueEntry = theValues.getEntry(op.value);
				if (newValueEntry != null && !newValueEntry.getElementId().equals(valueEntry.getElementId())) {
					if (!above)
						Assert.assertTrue("This should not happen", false);
					// It is permissible for a set operation from a derived collection to result in an element in a distinct collection
					// being merged with another (i.e. disappearing)
					List<ElementId> srcIds = new ArrayList<>(valueEntry.get().size());
					srcIds.addAll(valueEntry.get().keySet());
					for (ElementId srcId : srcIds) {
						srcIndex = theSourceValues.getElementsBefore(srcId);
						remove(srcIndex, distinctOps);
						add(srcIndex, op.value, -1, distinctOps);
					}
				} else {
					if (theOptions.isPreservingSourceOrder()) {
						if (theValues.keySet().mutableElement(valueEntry.getElementId()).isAcceptable(op.value) == null)
							theValues.keySet().mutableElement(valueEntry.getElementId()).set(op.value);
						else {
							theValues.mutableEntry(valueEntry.getElementId()).remove();
							theValues.putEntry(op.value, values, false).getElementId();
						}
					} else {
						theValues.keySet().mutableElement(valueEntry.getElementId()).set(op.value);
						Assert.assertEquals(op.index, getElementIndex(valueEntry.getElementId())); // Set operations are not allowed to
						// modify order
					}
					// Need to copy the entries, because set operations from above can cause unintended side effects (e.g. removal)
					// Representative element goes first
					ElementId repId = theRepresentativeElements.get(valueEntry.getElementId());
					parentOps.add(new CollectionOp<>(CollectionChangeType.set, op.value, theSourceValues.getElementsBefore(repId)));
					for (Map.Entry<ElementId, E> entry : new ArrayList<>(values.entrySet())) {
						theSourceValues.mutableElement(entry.getKey()).set(op.value);
						entry.setValue(op.value);
						if (!entry.getKey().equals(repId))
							parentOps.add(
								new CollectionOp<>(CollectionChangeType.set, op.value, theSourceValues.getElementsBefore(entry.getKey())));
					}
					distinctOps.add(op);
				}
				break;
			}
		}
		getParent().fromAbove(parentOps, helper, true);
		modified(ops, helper, !above);
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

	private void add(int srcIndex, E value, int destIndex, List<CollectionOp<E>> distinctOps) {
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
					theDebug.act("add:move").param("value", value)//
					// .param("oldIndex", oldIndex).param("newIndex", newIndex).param("srcIndex", srcIndex)//
					.exec();
					distinctOps.add(new CollectionOp<>(CollectionChangeType.remove, value, oldIndex));
					distinctOps.add(new CollectionOp<>(CollectionChangeType.add, value, newIndex));
				} else {
					theDebug.act("add:update").param("value", value)//
					// .param("index", oldIndex).param("srcIndex", srcIndex)//
					.exec();
					distinctOps.add(new CollectionOp<>(CollectionChangeType.set, value, oldIndex));
				}
			} else {
				// No effect
				theDebug.act("add:no-effect").param("value", value)//
				// .param("srcIndex", srcIndex)//
				.exec();
			}
		} else {
			// The new value is the first in its category
			if (valueEntry == null) {
				// Add at the correct position if specified
				if (destIndex < 0 || theValues.isEmpty()) {
					valueEntry = theValues.putEntry(value, new BetterTreeMap<>(false, ElementId::compareTo), false);
					ElementId valueEl = valueEntry.getElementId();
					theDebug.act("add:new").param("value", value)//
					// .param("srcIndex", srcIndex).param("index", () -> getElementIndex(valueEl))//
					.exec();
				} else {
					ElementId addedEntryId;
					theDebug.act("add:insert").param("value", value)//
					// .param("index", destIndex).param("srcIndex", srcIndex)//
					.exec();
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
			distinctOps.add(new CollectionOp<>(CollectionChangeType.add, value, repIndex));
		}
	}

	private void remove(int srcIndex, List<CollectionOp<E>> distinctOps) {
		CollectionElement<E> srcEl = theSourceValues.getElement(srcIndex);
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
				theDebug.act("remove:remove").param("value", value)//
				// .param("index", oldIndex).param("srcIndex", srcIndex)//
				.exec();
				theSortedRepresentatives.mutableElement(orderEl.getElementId()).remove();
				theRepresentativeElements.mutableEntry(repEntry.getElementId()).remove();
				theValues.mutableEntry(valueEntry.getElementId()).remove();
				distinctOps.add(new CollectionOp<>(CollectionChangeType.remove, value, oldIndex));
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
					theDebug.act("remove:move").param("value", value)//
					// .param("oldIndex", oldIndex).param("newIndex", newIndex).param("srcIndex", srcIndex)//
					.exec();
					distinctOps.add(new CollectionOp<>(CollectionChangeType.remove, value, oldIndex));
					distinctOps.add(new CollectionOp<>(CollectionChangeType.add, newFirstSrcEntry.getValue(), newIndex));
				} else {
					theDebug.act("remove:representativeChange")//
					// .param("index", oldIndex).param("srcIndex", srcIndex)//
					.exec();
					if (valueEntry.getKey() != newFirstSrcEntry.getValue()) {
						theValues.keySet().mutableElement(valueEntry.getElementId()).set(newFirstSrcEntry.getValue());
						distinctOps.add(new CollectionOp<>(CollectionChangeType.set, newFirstSrcEntry.getValue(), oldIndex));
					}
				}
			}
		} else {
			theDebug.act("remove:no-effect")//
			// .param("value", value).param("srcIndex", srcIndex)//
			.exec();
			// The removed element was not the representative for its category. No change to the derived collection.
			Assert.assertFalse(valueEntry.get().isEmpty());
		}
		theSourceValues.mutableElement(srcEl.getElementId()).remove();
	}

	@Override
	public void check(boolean transComplete) {
		super.check(transComplete);
		theNewSourceValues.clear();
	}

	@Override
	public String toString() {
		return "distinct()";
	}
}
