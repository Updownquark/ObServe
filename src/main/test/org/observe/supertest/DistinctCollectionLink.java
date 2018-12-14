package org.observe.supertest;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.dbug.DBug;
import org.dbug.DBugAnchor;
import org.dbug.DBugAnchorType;
import org.junit.Assert;
import org.observe.collect.CollectionChangeType;
import org.observe.collect.Equivalence;
import org.observe.collect.FlowOptions;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.collect.ObservableCollectionEvent;
import org.observe.supertest.ObservableChainTester.TestValueType;
import org.observe.util.TypeTokens;
import org.qommons.ArrayUtils;
import org.qommons.BreakpointHere;
import org.qommons.QommonsTestUtils;
import org.qommons.Ternian;
import org.qommons.TestHelper;
import org.qommons.Transaction;
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
import org.qommons.collect.MutableMapEntryHandle;
import org.qommons.tree.BetterTreeMap;
import org.qommons.tree.BetterTreeSet;

import com.google.common.reflect.TypeToken;

public class DistinctCollectionLink<E> extends AbstractObservableCollectionLink<E, E> {
	class GroupedValues {
		E value;
		ElementId valueId;
		LinkElement distinctEl;
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

	/** A parallel representation of the elements in the source (parent) collection */
	private final BetterSortedMap<LinkElement, GroupedValues> theSourceElements;
	private final BetterSortedSet<ElementId> theSortedRepresentatives;
	private final Map<E, LinkElement> theDistinctElementsByValue;

	private final boolean isRoot;
	private static final DBugAnchorType<DistinctCollectionLink<?>> DEBUG_TYPE = (DBugAnchorType<DistinctCollectionLink<?>>) (DBugAnchorType<?>) DBug
		.declare("oct", DistinctCollectionLink.class,
			b -> b//
			.withEvent("fromBelow", e -> e.withEventField("ops", new TypeToken<List<? extends CollectionOp<?>>>() {}))//
			.withEvent("fromAbove", e -> e.withEventField("ops", new TypeToken<List<? extends CollectionOp<?>>>() {}))//
			.withEvent("addSource", e -> e.withEventField("val", TypeTokens.get().OBJECT))//
			.withEvent("remove", e -> e.withEventField("val", TypeTokens.get().OBJECT))//
			.withEvent("update",
				e -> e.withEventField("oldValue", TypeTokens.get().OBJECT).withEventField("newValue", TypeTokens.get().OBJECT))//
			.withEvent("trueUpdate", null)//
			.withEvent("updateNoEffect", null)//
			.withEvent("updateMove", null)//
			.withEvent("addMove", e -> e.withEventField("val", TypeTokens.get().OBJECT))//
			.withEvent("addUpdate", e -> e.withEventField("val", TypeTokens.get().OBJECT))//
			.withEvent("addNoEffect", e -> e.withEventField("val", TypeTokens.get().OBJECT))//
			.withEvent("addNew", e -> e.withEventField("val", TypeTokens.get().OBJECT))//
			.withEvent("removeRemove", e -> e.withEventField("val", TypeTokens.get().OBJECT))//
			.withEvent("removeMove", e -> e.withEventField("val", TypeTokens.get().OBJECT))//
			.withEvent("removeRepChange", null)//
			.withEvent("removeNoEffect", null)//
			);
	private final DBugAnchor<DistinctCollectionLink<?>> debug;
	// private final DebugData theDebug;

	public DistinctCollectionLink(ObservableCollectionChainLink<?, E> parent, TestValueType type, CollectionDataFlow<?, ?, E> flow,
		TestHelper helper, boolean checkRemovedValues, FlowOptions.UniqueOptions options,
		boolean root) {
		super(parent, type, flow, helper, checkRemovedValues, !isOrderImportant(options, flow.equivalence()), Ternian.FALSE);
		theOptions = options;
		isRoot = root;
		theValues = flow.equivalence().createMap();
		theSourceElements = new BetterTreeMap<>(false, LinkElement::compareTo);
		theSortedRepresentatives = new BetterTreeSet<>(false, ElementId::compareTo);
		theDistinctElementsByValue = flow.equivalence().createMap();

		// theDebug = Debug.d().add("distinctLink");
		debug = DEBUG_TYPE.debug(this).build();
	}

	private boolean isOrderImportant() {
		return isOrderImportant(theOptions, getCollection().equivalence());
	}

	private static boolean isOrderImportant(FlowOptions.UniqueOptions options, Equivalence<?> equivalence) {
		/* The order of sets with hash-based distinctness is very complicated and depends on the order in which elements are encountered
		 * This order may be vastly different from the order of the source collection.
		 * Element encounter order is very difficult to keep track of in the tester and is not important. */
		if (options.isPreservingSourceOrder() && options.isUseFirst())
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
			LinkElement srcEl = getParent().getElements().get(i);
			ElementId srcId = theSourceElements.putEntry(srcEl, null, false).getElementId();
			MutableMapEntryHandle<E, GroupedValues> valueEntry = theValues.mutableEntry(theValues.getEntry(src).getElementId());
			GroupedValues values;
			if (valueEntry.get() == null) {
				values = new GroupedValues(src, valueEntry.getElementId(), srcId, srcEl, distinctEls.get(src));
				valueEntry.set(values);
			} else {
				values = valueEntry.get();
				values.add(srcId, srcEl);
			}
			theSourceElements.mutableEntry(srcId).set(values);
			i++;
		}
		theValues.values().stream().map(v -> theOptions.isPreservingSourceOrder() ? v.representative : v.valueId)
		.forEach(theSortedRepresentatives::add);
		getExpected().addAll(theValues.keySet());
	}

	@Override
	protected void change(ObservableCollectionEvent<? extends E> evt) {
		super.change(evt);
		switch (evt.getType()) {
		case add:
			theDistinctElementsByValue.put(evt.getNewValue(), getLastAddedOrModifiedElement());
			break;
		case remove:
			theDistinctElementsByValue.remove(evt.getOldValue());
			break;
		case set:
			theDistinctElementsByValue.remove(evt.getOldValue());
			theDistinctElementsByValue.put(evt.getNewValue(), getLastAddedOrModifiedElement());
			break;
		}
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
						parentOps.add(new CollectionOp<>(op, op.type, theSourceElements.keySet().getElementsBefore(srcId), op.value));
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
					CollectionOp<E> parentSet = new CollectionOp<>(op, op.type, theSourceElements.keySet().getElementsBefore(srcId),
						op.value);
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
		try (Transaction t = debug.event("fromBelow").with("ops", ops).begin()) {
			List<CollectionOp<E>> distinctOps = new ArrayList<>(ops.size());
			for (CollectionOp<E> op : ops) {
				switch (op.type) {
				case add:
					try (Transaction t2 = debug.event("addSource").with("val", op.value).begin()) {
						add(op.elementId, op.value, -1, distinctOps);
					}
					break;
				case remove:
					try (Transaction t2 = debug.event("remove").with("val", op.value).begin()) {
						remove(op.elementId, distinctOps);
					}
					break;
				case set:
					CollectionElement<GroupedValues> srcEl = theSourceElements.getEntry(op.elementId);
					Assert.assertEquals(theSourceElements.keySet().getElementsBefore(srcEl.getElementId()), op.index);
					GroupedValues oldValues = srcEl.get();
					GroupedValues newValues = theValues.get(op.value);
					try (Transaction t2 = debug.event("update").with("oldValue", oldValues).with("newValue", op.value).begin()) {
						if (newValues != null && oldValues == newValues) {
							// Category is unchanged
							if (oldValues.representative.equals(srcEl.getElementId())) {
								ElementId repId = theOptions.isPreservingSourceOrder() ? srcEl.getElementId() : newValues.valueId;
								// The updated value is the representative for its category. Fire the update event.
								debug.event("trueUpdate").occurred();
								int repIndex = theSortedRepresentatives.indexOf(repId);
								distinctOps.add(new CollectionOp<>(CollectionChangeType.set, oldValues.distinctEl, repIndex, op.value));
							} else {
								debug.event("updateNoEffect").occurred();
								// The update value is not the representative for its category. No change to the derived collection.
							}
						} else {
							// Category has been changed.
							// Due to differences in encounter order between the actual collection and the test, this may actually represent
							// an
							// add/remove. We detect this by testing if the outgoing link ID is no longer present.
							if (newValues == null && oldValues.sourceEls.size() == 1 && oldValues.distinctEl.isPresent()
								&& theValues.keySet().mutableElement(oldValues.valueId).isAcceptable(op.value) == null) {
								try (Transaction t3 = debug.event("updateMove").begin()) {
									theSourceElements.mutableEntry(srcEl.getElementId()).set(oldValues);
									oldValues.value = op.value;
									theValues.keySet().mutableElement(oldValues.valueId).set(op.value);
									ElementId repId = theOptions.isPreservingSourceOrder() ? srcEl.getElementId() : oldValues.valueId;
									// The updated value is the representative for its category. Fire the update event.
									int repIndex = theSortedRepresentatives.indexOf(repId);
									distinctOps.add(new CollectionOp<>(CollectionChangeType.set, oldValues.distinctEl, repIndex, op.value));
								}
							} else {
								// Same as a remove, then an add.
								remove(op.elementId, distinctOps);
								add(op.elementId, op.value, -1, distinctOps);
							}
						}
					}
				}
			}
			if (!isOrderImportant()) {
				/* The encounter-order-dependent nature of hash-based distinctness makes it extremely difficult to expect the correct element
				 * order unless the encounter order of the actual distinct manager can be exactly replicated in the testing.
				 * This also is difficult to reproduce from some types of links, like reversibility or flattening (where operations from above
				 * may propagate back up the chain due to shared values between elements).
				 *
				 * The order of a hash-based distinct collection is not significant of anything, so I do not feel a need to test for its
				 * consistency.
				 * So here, I directly ensure that the order of the expected collection (and the internal structure of the link) is consistent
				 * with the actual distinct collection.
				 *
				 * This code assumes that if order is not important for a map, then entry insertion at arbitrary positions is supported.
				 */
				Map<E, ElementId> standard = getCollection().equivalence().createMap();
				getCollection().spliterator().forEachElement(el -> {
					standard.put(el.get(), el.getElementId());
				}, true);
				E[] values = theValues.keySet()
					.toArray((E[]) Array.newInstance(getCollection().getType().wrap().getRawType(), theValues.size()));
				ArrayUtils.sort(values, new ArrayUtils.SortListener<E>() {
					@Override
					public int compare(E o1, E o2) {
						ElementId id1 = standard.get(o1);
						ElementId id2 = standard.get(o2);
						if (id1 == null || id2 == null) {
							// This should not happen, but we'll let the tester throw the exception when the check fails
							BreakpointHere.breakpoint(); // Debug if enabled
						}
						return id1.compareTo(id2);
					}

					@Override
					public void swapped(E o1, int idx1, E o2, int idx2) {
						MutableMapEntryHandle<E, GroupedValues> valueEntry1 = theValues.mutableEntry(theValues.getEntry(o1).getElementId());
						MutableMapEntryHandle<E, GroupedValues> valueEntry2 = theValues.mutableEntry(theValues.getEntry(o2).getElementId());
						if (!theOptions.isPreservingSourceOrder()) {
							theSortedRepresentatives.remove(valueEntry1.getElementId());
							theSortedRepresentatives.remove(valueEntry2.getElementId());
						}
						// Note that the sort method always calls this method with idx1<idx2
						if (idx2 == idx1 + 1) {
							// If they're next to each other, this is easier
							GroupedValues tempGV = valueEntry1.get();
							valueEntry1.remove();
							valueEntry1 = theValues
								.mutableEntry(theValues.keySet().mutableElement(valueEntry2.getElementId()).add(o1, false));
							valueEntry1.set(tempGV);

							// Now I need to add modifications to adjust the expected collection and the links downstream
							distinctOps.add(new CollectionOp<>(CollectionChangeType.remove, valueEntry1.get().distinctEl, idx1, o1));
							valueEntry1.get().distinctEl = theDistinctElementsByValue.get(o1);
							distinctOps.add(new CollectionOp<>(CollectionChangeType.add, valueEntry1.get().distinctEl, idx2, o1));
						} else {
							// Get the adjacent element to the first entry
							boolean adjacentFirstLeft;
							CollectionElement<E> adjacentFirst = theValues.keySet().getAdjacentElement(valueEntry1.getElementId(), true);
							if (adjacentFirst != null)
								adjacentFirstLeft = false;
							else {
								adjacentFirst = theValues.keySet().getAdjacentElement(valueEntry1.getElementId(), false);
								adjacentFirstLeft = true;
							}
							// Remove the first entry
							GroupedValues tempGV = valueEntry1.get();
							valueEntry1.remove();

							// Insert the first entry again, adjacent to the second entry
							valueEntry1 = theValues
								.mutableEntry(theValues.keySet().mutableElement(valueEntry2.getElementId()).add(o1, true));
							valueEntry1.set(tempGV);

							// Remove the second entry
							tempGV = valueEntry2.get();
							valueEntry2.remove();

							// Insert the second entry again, in the first entry's old position
							valueEntry2 = theValues
								.mutableEntry(theValues.keySet().mutableElement(adjacentFirst.getElementId()).add(o2, !adjacentFirstLeft));
							valueEntry2.set(tempGV);

							// Now I need to add modifications to adjust the expected collection and the links downstream
							distinctOps.add(new CollectionOp<>(CollectionChangeType.remove, valueEntry2.get().distinctEl, idx2, o2));
							distinctOps.add(new CollectionOp<>(CollectionChangeType.remove, valueEntry1.get().distinctEl, idx1, o1));
							valueEntry1.get().distinctEl = theDistinctElementsByValue.get(o1);
							valueEntry2.get().distinctEl = theDistinctElementsByValue.get(o2);
							distinctOps.add(new CollectionOp<>(CollectionChangeType.add, valueEntry2.get().distinctEl, idx1, o2));
							distinctOps.add(new CollectionOp<>(CollectionChangeType.add, valueEntry1.get().distinctEl, idx2, o1));
						}
						// Bookkeeping
						valueEntry1.get().valueId = valueEntry1.getElementId();
						valueEntry2.get().valueId = valueEntry2.getElementId();
						if (!theOptions.isPreservingSourceOrder()) {
							theSortedRepresentatives.add(valueEntry1.getElementId());
							theSortedRepresentatives.add(valueEntry2.getElementId());
						}
					}
				});
				Assert.assertThat(theValues.keySet(), QommonsTestUtils.collectionsEqual(getCollection(), true));
			}
			modified(distinctOps, helper, true);
		}
	}

	@Override
	public void fromAbove(List<CollectionOp<E>> ops, TestHelper helper, boolean above) {
		try (Transaction t = debug.event("fromAbove").with("ops", ops).begin()) {
			List<CollectionOp<E>> parentOps = new ArrayList<>(ops.size());
			List<CollectionOp<E>> distinctOps = new ArrayList<>();
			for (CollectionOp<E> op : ops) {
				switch (op.type) {
				case add:
					LinkElement srcLinkEl = getSourceElement(op.elementId);
					int srcIndex = add(srcLinkEl, op.value, op.index, distinctOps);
					parentOps.add(new CollectionOp<>(CollectionChangeType.add, srcLinkEl, srcIndex, op.value));
					break;
				case remove:
					GroupedValues values = getValueHandle(op.index).get();
					if (theOptions.isPreservingSourceOrder())
						theSortedRepresentatives.remove(values.representative);
					else
						theSortedRepresentatives.remove(values.valueId);
					for (Map.Entry<ElementId, LinkElement> srcEl : values.sourceEls.entrySet()) {
						srcIndex = theSourceElements.keySet().getElementsBefore(srcEl.getKey());
						parentOps.add(new CollectionOp<>(CollectionChangeType.remove, srcEl.getValue(), srcIndex, op.value));
						theSourceElements.mutableEntry(srcEl.getKey()).remove();
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
							remove(srcId.getValue(), distinctOps);
							add(srcId.getValue(), op.value, op.index, distinctOps);
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
							Assert.assertEquals(op.index, getElementIndex(values.valueId)); // Set operations are not allowed to modify
							// order
						}
						// Need to copy the entries, because set operations from above can cause unintended side effects (e.g. removal)
						// Representative element goes first
						ElementId repId = values.representative;
						parentOps.add(new CollectionOp<>(CollectionChangeType.set, values.sourceEls.get(repId),
							theSourceElements.keySet().getElementsBefore(repId), op.value));
						for (Map.Entry<ElementId, LinkElement> entry : new ArrayList<>(values.sourceEls.entrySet())) {
							values.value = op.value;
							if (!entry.getKey().equals(repId))
								parentOps.add(new CollectionOp<>(CollectionChangeType.set, entry.getValue(),
									theSourceElements.keySet().getElementsBefore(entry.getKey()), op.value));
						}
						distinctOps.add(op);
					}
					break;
				}
			}
			getParent().fromAbove(parentOps, helper, true);
			modified(distinctOps, helper, !above);
		}
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

	private int add(LinkElement srcLinkEl, E value, int destIdx, List<CollectionOp<E>> distinctOps) {
		ElementId srcEl = theSourceElements.putEntry(srcLinkEl, null, false).getElementId();
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
					debug.event("addMove").with("val", value)//
					// .param("oldIndex", oldIndex).param("newIndex", newIndex).param("srcIndex", srcIndex)//
					.occurred();
					distinctOps.add(new CollectionOp<>(CollectionChangeType.remove, values.distinctEl, oldIndex, value));
					distinctOps.add(new CollectionOp<>(CollectionChangeType.add, values.distinctEl, newIndex, value));
				} else {
					debug.event("addUpdate").with("val", value)//
					// .param("index", oldIndex).param("srcIndex", srcIndex)//
					.occurred();
					distinctOps.add(new CollectionOp<>(CollectionChangeType.set, values.distinctEl, oldIndex, value));
				}
			} else {
				// No effect
				debug.event("addNoEffect").with("val", value)//
				// .param("srcIndex", srcIndex)//
				.occurred();
			}
		} else {
			// The new value is the first in its category
			if (values == null) {
				// Add at the correct position if specified
				try (Transaction t = debug.event("addNew").with("val", value).begin()) {
					// Differences in encounter order may have caused the distinct element mapped to the source element to have been removed
					// in the ObservableCollection. So we need to get the empirical element mapped to the value instead.
					LinkElement distinctEl = theDistinctElementsByValue.get(value);
					ElementId valueId;
					if (destIdx >= 0 && !theValues.isEmpty()) {
						if (destIdx == 0)
							valueId = theValues.keySet().mutableElement(theValues.keySet().getTerminalElement(true).getElementId())
							.add(value, true);
						else
							valueId = theValues.keySet().mutableElement(getValueHandle(destIdx - 1).getElementId()).add(value, false);
					} else
						valueId = theValues.putEntry(value, null, false).getElementId();
					values = new GroupedValues(value, valueId, srcEl, srcLinkEl, distinctEl);
					theValues.mutableEntry(valueId).set(values);
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
		theSourceElements.mutableEntry(srcEl).set(values);
		return theSourceElements.keySet().getElementsBefore(srcEl);
	}

	private void remove(LinkElement srcLinkEl, List<CollectionOp<E>> distinctOps) {
		ElementId srcId = theSourceElements.getEntry(srcLinkEl).getElementId();
		GroupedValues values = theSourceElements.getEntryById(srcId).get();
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
				try (Transaction t = debug.event("removeRemove").with("val", values.value)//
					// .param("index", oldIndex).param("srcIndex", srcIndex)//
					.begin()) {
					theSortedRepresentatives.mutableElement(orderId).remove();
					theValues.mutableEntry(values.valueId).remove();
					distinctOps.add(new CollectionOp<>(CollectionChangeType.remove, values.distinctEl, oldIndex, values.value));
				}
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
					debug.event("removeMove").with("val", values.value)//
					// .param("oldIndex", oldIndex).param("newIndex", newIndex).param("srcIndex", srcIndex)//
					.occurred();
					distinctOps.add(new CollectionOp<>(CollectionChangeType.remove, values.distinctEl, oldIndex, values.value));
					distinctOps.add(new CollectionOp<>(CollectionChangeType.add, values.distinctEl, newIndex, values.value));
				} else {
					debug.event("removeRepChange")//
					// .param("index", oldIndex).param("srcIndex", srcIndex)//
					.occurred();
					distinctOps.add(new CollectionOp<>(CollectionChangeType.set, values.distinctEl, oldIndex, values.value));
				}
			}
		} else {
			debug.event("removeNoEffect")//
			// .param("value", value).param("srcIndex", srcIndex)//
			.occurred();
			// The removed element was not the representative for its category. No change to the derived collection.
			Assert.assertFalse(values.sourceEls.isEmpty());
		}
		theSourceElements.mutableEntry(srcId).remove();
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
		Assert.assertThat(theSourceElements.values().stream().map(v -> v.value).collect(Collectors.toList()),
			QommonsTestUtils.collectionsEqual(getParent().getCollection(), true));
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
