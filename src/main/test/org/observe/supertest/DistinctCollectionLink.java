package org.observe.supertest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.observe.collect.CollectionChangeType;
import org.observe.collect.Equivalence;
import org.observe.collect.FlowOptions;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.supertest.ObservableChainTester.TestValueType;
import org.qommons.ArrayUtils;
import org.qommons.Ternian;
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
import org.qommons.collect.MutableElementSpliterator;
import org.qommons.collect.MutableMapEntryHandle;
import org.qommons.debug.Debug;
import org.qommons.debug.Debug.DebugData;
import org.qommons.tree.BetterTreeList;
import org.qommons.tree.BetterTreeMap;
import org.qommons.tree.BetterTreeSet;

public class DistinctCollectionLink<E> extends AbstractObservableCollectionLink<E, E> {
	class GroupedValues {
		E value;
		final ElementId valueId;
		final LinkElement distinctEl;
		final BetterSortedMap<ElementId, LinkElement> sourceEls;
		ElementId representative;

		GroupedValues(E value, ElementId valueId, ElementId sourceId, LinkElement sourceEl, LinkElement distinctEl) {
			this.value = value;
			this.valueId = valueId;
			this.distinctEl = distinctEl;
			sourceEls = new BetterTreeMap<>(false, ElementId::compareTo);
			sourceEls.put(sourceId, sourceEl);
			representative = sourceId;
		}

		public void add(ElementId srcId, LinkElement srcEl) {
			sourceEls.put(srcId, srcEl);
			if (theOptions.isUseFirst() && sourceEls.keySet().first() == srcId)
				representative = srcId;
		}

		@Override
		public String toString() {
			return String.valueOf(value);
		}
	}
	private final FlowOptions.UniqueOptions theOptions;
	/** A map of value to equivalent values, grouped by source ID */
	private final BetterMap<E, GroupedValues> theValues;

	/** A parallel representation of the values in the source (parent) collection */
	private final BetterList<GroupedValues> theSourceElements;
	private final BetterSortedSet<ElementId> theSortedRepresentatives;

	private final boolean isRoot;
	private final DebugData theDebug;

	public DistinctCollectionLink(ObservableCollectionChainLink<?, E> parent, TestValueType type, CollectionDataFlow<?, ?, E> flow,
		TestHelper helper, boolean checkRemovedValues, FlowOptions.UniqueOptions options,
		boolean root) {
		super(parent, type, flow, helper, checkRemovedValues, !isOrderImportant(options, flow.equivalence()), Ternian.FALSE);
		theOptions = options;
		isRoot = root;
		theValues = flow.equivalence().createMap();
		theSourceElements = new BetterTreeList<>(false);
		theSortedRepresentatives = new BetterTreeSet<>(false, ElementId::compareTo);

		theDebug = Debug.d().add("distinctLink");
	}

	private boolean isOrderImportant() {
		return isOrderImportant(theOptions, getCollection().equivalence());
	}

	private static boolean isOrderImportant(FlowOptions.UniqueOptions options, Equivalence<?> equivalence) {
		/* The order of sets with hash-based distinctness is very complicated and depends on the order in which elements are encountered
		 * This order may be vastly different from the order of the source collection.
		 * Element encounter order is very difficult to keep track of in the tester and is not important. */
		if (options.isPreservingSourceOrder())
			return true;
		return equivalence instanceof org.observe.collect.Equivalence.ComparatorEquivalence;
	}

	@Override
	public void initialize(TestHelper helper) {
		if (isRoot)
			getParent().initialize(helper);
		super.initialize(helper);
		// This is not completely perfect. Which elements are representative are sometimes based on the temporal order in which
		// elements are encountered, which is not always the same as the order in the collection, even on initialization
		// As long as "equivalent" elements are also equal, this is good enough.
		// If I ever test other kinds of equivalence, this may cause failure.
		theValues.keySet().addAll(getCollection());
		Map<E, LinkElement> distinctEls = new HashMap<>();
		for (int i = 0; i < theValues.size(); i++)
			distinctEls.put(getCollection().get(i), getElements().get(i));
		int i = 0;
		for (E src : getParent().getCollection()) {
			ElementId srcId = theSourceElements.addElement(null, false).getElementId();
			MutableMapEntryHandle<E, GroupedValues> valueEntry = theValues.mutableEntry(theValues.getEntry(src).getElementId());
			LinkElement srcEl = getParent().getElements().get(i);
			GroupedValues values;
			if (valueEntry.get() == null) {
				values = new GroupedValues(src, valueEntry.getElementId(), srcId, srcEl, distinctEls.get(src));
				valueEntry.set(values);
			} else {
				values = valueEntry.get();
				values.add(srcId, srcEl);
			}
			theSourceElements.mutableElement(srcId).set(values);
			i++;
		}
		theValues.values().stream().map(v -> theOptions.isPreservingSourceOrder() ? v.representative : v.valueId)
		.forEach(theSortedRepresentatives::add);
		getExpected().addAll(theValues.keySet());
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
				ops.stream().map(op -> new CollectionOp<>(op, op.type, -1, op.value)).collect(Collectors.toList()), 0,
				theSourceElements.size(), helper);
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

				MapEntryHandle<E, GroupedValues> valueEntry = theValues.getEntry(op.value);
				MapEntryHandle<E, GroupedValues> valueHandle;
				// TODO All the index adding here is for isPreservingSourceOrder=false. Rewrite for true.
				if (valueEntry != null) {
					op.reject(StdMsg.ELEMENT_EXISTS, false);
				} else if (op.index >= 0) {
					if ((subListStart + op.index) == 0 && theValues.isEmpty()) {
						op.reject(theValues.keySet().canAdd(op.value), true);
					} else if (theOptions.isPreservingSourceOrder()) {
						throw new IllegalStateException("Not implemented"); // TODO
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
						throw new IllegalStateException("Not implemented"); // TODO
					} else if (subListStart > 0 || subListEnd < theValues.size()) {
						ElementId after = subListStart == 0 ? null : getValueHandle(subListStart - 1).getElementId();
						ElementId before = subListEnd == theValues.size() ? null : getValueHandle(subListEnd).getElementId();
						op.reject(theValues.keySet().canAdd(op.value, after, before), true);
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
					for (ElementId srcId : valueEntry.get().sourceEls.keySet()) {
						parentOps.add(new CollectionOp<>(op, op.type, theSourceElements.getElementsBefore(srcId), op.value));
					}
				}
				break;
			case set:
				MapEntryHandle<E, GroupedValues> oldValueEntry = getValueHandle(subListStart + op.index);
				MapEntryHandle<E, GroupedValues> newValueEntry = theValues.getEntry(op.value);
				if (newValueEntry != null && !newValueEntry.getElementId().equals(oldValueEntry.getElementId())) {
					op.reject(StdMsg.ELEMENT_EXISTS, true);
					continue;
				}
				if (!theOptions.isPreservingSourceOrder()) {
					op.reject(theValues.keySet().mutableElement(oldValueEntry.getElementId()).isAcceptable(op.value), true);
					if (op.getMessage() != null)
						continue;
				}
				for (ElementId srcId : oldValueEntry.get().sourceEls.keySet()) {
					CollectionOp<E> parentSet = new CollectionOp<>(op, op.type, theSourceElements.getElementsBefore(srcId), op.value);
					parentOps.add(parentSet);
				}
				break;
			}
		}
		if (parentOps != null)
			getParent().checkModifiable(parentOps, 0, theSourceElements.size(), helper);
	}

	@Override
	public void fromBelow(List<CollectionOp<E>> ops, TestHelper helper) {
		theDebug.act("fromBelow").param("ops", ops).exec();
		List<CollectionOp<E>> distinctOps = new ArrayList<>(ops.size());
		for (CollectionOp<E> op : ops) {
			switch (op.type) {
			case add:
				theDebug.act("addSource").param("@", op.value).exec();
				add(op.elementId, op.index, op.value, distinctOps);
				break;
			case remove:
				theDebug.act("remove").param("@", op.value).exec();
				remove(op.index, distinctOps);
				break;
			case set:
				CollectionElement<GroupedValues> srcEl = theSourceElements.getElement(op.index);
				GroupedValues oldValues = srcEl.get();
				GroupedValues newValues = theValues.get(op.value);
				theDebug.act("update").param("@", oldValues).param("newValue", op.value).exec();
				if (newValues != null && oldValues == newValues) {
					// Category is unchanged
					if (oldValues.representative.equals(srcEl.getElementId())) {
						ElementId repId = theOptions.isPreservingSourceOrder() ? srcEl.getElementId() : newValues.valueId;
						// The updated value is the representative for its category. Fire the update event.
						theDebug.act("update:trueUpdate").exec();
						int repIndex = theSortedRepresentatives.indexOf(repId);
						distinctOps.add(new CollectionOp<>(CollectionChangeType.set, oldValues.distinctEl, repIndex, op.value));
					} else {
						theDebug.act("update:no-effect").exec();
						// The update value is not the representative for its category. No change to the derived collection.
					}
				} else {
					// Category has been changed.
					if (newValues == null && oldValues.sourceEls.size() == 1
						&& theValues.keySet().mutableElement(oldValues.valueId).isAcceptable(op.value) == null) {
						theDebug.act("update:move").exec();
						theSourceElements.mutableElement(srcEl.getElementId()).set(oldValues);
						oldValues.value = op.value;
						theValues.keySet().mutableElement(oldValues.valueId).set(op.value);
						ElementId repId = theOptions.isPreservingSourceOrder() ? srcEl.getElementId() : oldValues.valueId;
						// The updated value is the representative for its category. Fire the update event.
						int repIndex = theSortedRepresentatives.indexOf(repId);
						distinctOps.add(new CollectionOp<>(CollectionChangeType.set, oldValues.distinctEl, repIndex, op.value));
					} else {
						// Same as a remove, then an add.
						remove(op.index, distinctOps);
						add(op.elementId, op.index, op.value, distinctOps);
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
				LinkElement srcLinkEl = getSourceElement(op.elementId);
				int srcIndex = add(srcLinkEl, -1, op.value, distinctOps);
				parentOps.add(new CollectionOp<>(CollectionChangeType.add, srcLinkEl, srcIndex, op.value));
				break;
			case remove:
				GroupedValues values = getValueHandle(op.index).get();
				if (theOptions.isPreservingSourceOrder())
					theSortedRepresentatives.remove(values.representative);
				else
					theSortedRepresentatives.remove(values.valueId);
				for (Map.Entry<ElementId, LinkElement> srcEl : values.sourceEls.entrySet()) {
					srcIndex = theSourceElements.getElementsBefore(srcEl.getKey());
					parentOps.add(new CollectionOp<>(CollectionChangeType.remove, srcEl.getValue(), srcIndex, op.value));
					theSourceElements.mutableElement(srcEl.getKey()).remove();
				}
				distinctOps.add(op);
				theValues.mutableEntry(values.valueId).remove();
				break;
			case set:
				values = getValueHandle(op.index).get();
				GroupedValues newValues = theValues.get(op.value);
				if (newValues != null && newValues != values) {
					if (!above)
						Assert.assertTrue("This should not happen", false);
					// It is permissible for a set operation from a derived collection to result in an element in a distinct collection
					// being merged with another (i.e. disappearing)
					List<Map.Entry<ElementId, LinkElement>> srcIds = new ArrayList<>(values.sourceEls.size());
					srcIds.addAll(values.sourceEls.entrySet());
					for (Map.Entry<ElementId, LinkElement> srcId : srcIds) {
						srcIndex = theSourceElements.getElementsBefore(srcId.getKey());
						remove(srcIndex, distinctOps);
						add(srcId.getValue(), srcIndex, op.value, distinctOps);
					}
				} else {
					if (theOptions.isPreservingSourceOrder()) {
						if (theValues.keySet().mutableElement(values.valueId).isAcceptable(op.value) == null)
							theValues.keySet().mutableElement(values.valueId).set(op.value);
						else {
							theValues.mutableEntry(values.valueId).remove();
							theValues.putEntry(op.value, values, false).getElementId();
						}
					} else {
						theValues.keySet().mutableElement(values.valueId).set(op.value);
						Assert.assertEquals(op.index, getElementIndex(values.valueId)); // Set operations are not allowed to modify order
					}
					// Need to copy the entries, because set operations from above can cause unintended side effects (e.g. removal)
					// Representative element goes first
					ElementId repId = values.representative;
					parentOps.add(new CollectionOp<>(CollectionChangeType.set, values.sourceEls.get(repId),
						theSourceElements.getElementsBefore(repId), op.value));
					for (Map.Entry<ElementId, LinkElement> entry : new ArrayList<>(values.sourceEls.entrySet())) {
						values.value = op.value;
						if (!entry.getKey().equals(repId))
							parentOps.add(new CollectionOp<>(CollectionChangeType.set, entry.getValue(),
								theSourceElements.getElementsBefore(entry.getKey()), op.value));
					}
					distinctOps.add(op);
				}
				break;
			}
		}
		getParent().fromAbove(parentOps, helper, true);
		modified(ops, helper, !above);
	}

	private MapEntryHandle<E, GroupedValues> getValueHandle(int index) {
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

	private ElementId getSourceInsertHandle(LinkElement sourceEl) {
		int[] minMax = new int[] { 0, theSourceElements.size() - 1 };
		int srcIdx = ArrayUtils.binarySearch(minMax, //
			index -> {
				ElementId srcId = theSourceElements.getElement(index).getElementId();
				LinkElement srcEl = theSourceElements.getElement(srcId).get().sourceEls.get(srcId);
				for (int i = index - 1; !srcEl.isPresent() && i > minMax[0]; i--) {
					srcId = theSourceElements.getElement(i).getElementId();
					srcEl = theSourceElements.getElement(srcId).get().sourceEls.get(srcId);
				}
				for (int i = index + 1; !srcEl.isPresent() && i < minMax[1]; i++) {
					srcId = theSourceElements.getElement(i).getElementId();
					srcEl = theSourceElements.getElement(srcId).get().sourceEls.get(srcId);
				}
				if (!srcEl.isPresent())
					return 0; // No more present elements, so doesn't matter where the new one goes
				return sourceEl.getIndex() - srcEl.getIndex();
			});
		if (srcIdx >= 0) {
			// No more present elements
			return null;
		}
		srcIdx = -srcIdx - 1;
		return srcIdx < theSourceElements.size() ? theSourceElements.getElement(srcIdx).getElementId() : null;
	}

	private ElementId getDistinctInsertHandle(LinkElement distinctEl) {
		MutableElementSpliterator<Map.Entry<E, GroupedValues>> spliter = theValues.entrySet().spliterator(false);
		ValueHolder<ElementId> result = new ValueHolder<>();
		boolean[] passed = new boolean[1];
		while (!passed[0] && spliter.forElement(el -> {
			if (!el.get().getValue().distinctEl.isPresent())
				return;
			int diff = distinctEl.getIndex() - el.get().getValue().distinctEl.getIndex();
			if (diff < 0)
				result.accept(el.getElementId());
			else
				passed[0] = true;
		}, false)) {}
		return result.get();
	}

	private int add(LinkElement srcLinkEl, int srcIndex, E value, List<CollectionOp<E>> distinctOps) {
		ElementId srcEl;
		if (srcIndex >= 0)
			srcEl = theSourceElements.addElement(srcIndex, null).getElementId();
		else {
			// The caller doesn't have the index at which the source element should be added. Figure it out
			ElementId srcInsertHandle = getSourceInsertHandle(srcLinkEl);
			if (srcInsertHandle != null) // Add before the insert handle
				srcEl = theSourceElements.mutableElement(srcInsertHandle).add(null, true);
			else // Add as the last entry
				srcEl = theSourceElements.addElement(null, false).getElementId();
		}
		GroupedValues values = theValues.get(value);
		if (values != null && !values.sourceEls.isEmpty()) {
			ElementId valueSourceId = values.sourceEls.putEntry(srcEl, srcLinkEl, false).getElementId();
			if (theOptions.isUseFirst() && values.sourceEls.keySet().getElementsBefore(valueSourceId) == 0) {
				// The new value is replacing the old value as the representative element
				ElementId oldRep = values.representative;
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
					oldIndex = newIndex = getElementIndex(values.valueId);
				}
				if (oldIndex != newIndex) {
					theDebug.act("add:move").param("value", value)//
					// .param("oldIndex", oldIndex).param("newIndex", newIndex).param("srcIndex", srcIndex)//
					.exec();
					distinctOps.add(new CollectionOp<>(CollectionChangeType.remove, values.distinctEl, oldIndex, value));
					distinctOps.add(new CollectionOp<>(CollectionChangeType.add, values.distinctEl, newIndex, value));
				} else {
					theDebug.act("add:update").param("value", value)//
					// .param("index", oldIndex).param("srcIndex", srcIndex)//
					.exec();
					distinctOps.add(new CollectionOp<>(CollectionChangeType.set, values.distinctEl, oldIndex, value));
				}
			} else {
				// No effect
				theDebug.act("add:no-effect").param("value", value)//
				// .param("srcIndex", srcIndex)//
				.exec();
			}
		} else {
			// The new value is the first in its category
			if (values == null) {
				// Add at the correct position if specified
				theDebug.act("add:new").param("value", value)//
				.exec();
				LinkElement distinctEl = getDestElement(srcLinkEl);
				if (theValues.isEmpty()) {
					ElementId valueId = theValues.putEntry(value, null, false).getElementId();
					values = new GroupedValues(value, valueId, srcEl, srcLinkEl, distinctEl);
					theValues.mutableEntry(valueId).set(values);
				} else {
					ElementId distinctInsertHandle = getDistinctInsertHandle(distinctEl);
					ElementId addedEntryId;
					if (distinctInsertHandle != null) // Add before the insert handle
						addedEntryId = theValues.keySet().mutableElement(distinctInsertHandle).add(value, true);
					else // Add as the last entry
						addedEntryId = theValues.keySet().mutableElement(theValues.keySet().getTerminalElement(false).getElementId())
						.add(value, false);
					values = new GroupedValues(value, addedEntryId, srcEl, srcLinkEl, distinctEl);
					theValues.mutableEntry(addedEntryId).set(values);
				}
			}
			ElementId orderEl;
			if (theOptions.isPreservingSourceOrder())
				orderEl = theSortedRepresentatives.addElement(srcEl, true).getElementId();
			else
				orderEl = theSortedRepresentatives.addElement(values.valueId, true).getElementId();
			int repIndex = theSortedRepresentatives.getElementsBefore(orderEl);
			distinctOps.add(new CollectionOp<>(CollectionChangeType.add, values.distinctEl, repIndex, value));
		}
		theSourceElements.mutableElement(srcEl).set(values);
		return theSourceElements.getElementsBefore(srcEl);
	}

	private void remove(int srcIndex, List<CollectionOp<E>> distinctOps) {
		ElementId srcId = theSourceElements.getElement(srcIndex).getElementId();
		GroupedValues values = theSourceElements.getElement(srcId).get();
		values.sourceEls.remove(srcId);
		if (values.representative.equals(srcId)) {
			// The removed element was the representative for its category. Need to transition to a different element.
			ElementId orderId;
			if (theOptions.isPreservingSourceOrder())
				orderId = theSortedRepresentatives.getElement(srcId, true).getElementId();
			else
				orderId = theSortedRepresentatives.getElement(values.valueId, true).getElementId();
			int oldIndex = theSortedRepresentatives.getElementsBefore(orderId);
			if (values.sourceEls.isEmpty()) {
				// No more elements in the category.The element will be removed from the derived collection.
				theDebug.act("remove:remove").param("value", values.value)//
				// .param("index", oldIndex).param("srcIndex", srcIndex)//
				.exec();
				theSortedRepresentatives.mutableElement(orderId).remove();
				theValues.mutableEntry(values.valueId).remove();
				distinctOps.add(new CollectionOp<>(CollectionChangeType.remove, values.distinctEl, oldIndex, values.value));
			} else {
				// Need to transition to the new first element in the category
				Map.Entry<ElementId, LinkElement> newFirstSrcEntry = values.sourceEls.firstEntry();
				values.representative = newFirstSrcEntry.getKey();
				int newIndex;
				if (theOptions.isPreservingSourceOrder()) {
					theSortedRepresentatives.mutableElement(orderId).remove();
					orderId = theSortedRepresentatives.addElement(newFirstSrcEntry.getKey(), true).getElementId();
					newIndex = theSortedRepresentatives.getElementsBefore(orderId);
				} else
					newIndex = oldIndex;
				if (oldIndex != newIndex) {
					theDebug.act("remove:move").param("value", values.value)//
					// .param("oldIndex", oldIndex).param("newIndex", newIndex).param("srcIndex", srcIndex)//
					.exec();
					distinctOps.add(new CollectionOp<>(CollectionChangeType.remove, values.distinctEl, oldIndex, values.value));
					distinctOps.add(new CollectionOp<>(CollectionChangeType.add, values.distinctEl, newIndex, values.value));
				} else {
					theDebug.act("remove:representativeChange")//
					// .param("index", oldIndex).param("srcIndex", srcIndex)//
					.exec();
					distinctOps.add(new CollectionOp<>(CollectionChangeType.set, values.distinctEl, oldIndex, values.value));
				}
			}
		} else {
			theDebug.act("remove:no-effect")//
			// .param("value", value).param("srcIndex", srcIndex)//
			.exec();
			// The removed element was not the representative for its category. No change to the derived collection.
			Assert.assertFalse(values.sourceEls.isEmpty());
		}
		theSourceElements.mutableElement(srcId).remove();
	}

	@Override
	protected int getLinkIndex() {
		if (isRoot)
			return 0;
		return super.getLinkIndex();
	}

	@Override
	public void check(boolean transComplete) {
		if (isRoot)
			getParent().check(transComplete);
		super.check(transComplete);
	}

	@Override
	public String toString() {
		StringBuilder str = new StringBuilder("distinct(");
		str.append(getCollection().equivalence() instanceof Equivalence.ComparatorEquivalence ? "sorted" : "hash");
		if (isRoot)
			str.append(" ").append(getTestType());
		str.append(getExtras());
		str.append(")");
		return str.toString();
	}
}
