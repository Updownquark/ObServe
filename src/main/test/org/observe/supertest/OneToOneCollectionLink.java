package org.observe.supertest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.observe.collect.CollectionChangeType;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.collect.ObservableCollectionEvent;
import org.observe.supertest.ObservableChainTester.TestValueType;
import org.qommons.Ternian;
import org.qommons.TestHelper;

/**
 * A type of link whose derived elements have a one-to-one relationship with elements in the parent collection. It is not required that each
 * source element be represented in the derived collection.
 *
 * @param <E> The type of the parent link
 * @param <T> The type of this link
 */
public abstract class OneToOneCollectionLink<E, T> extends AbstractObservableCollectionLink<E, T> {
	private final Map<LinkElement, LinkElement> theSourceToDest;
	private final Map<LinkElement, LinkElement> theDestToSource;
	private final List<LinkElement> theElementsToRemove;
	private LinkElement theLastAddedSource;

	public OneToOneCollectionLink(ObservableCollectionChainLink<?, E> parent, TestValueType type, CollectionDataFlow<?, ?, T> flow,
		TestHelper helper, boolean checkRemovedValues, boolean rebaseRequired, Ternian allowPassive) {
		super(parent, type, flow, helper, checkRemovedValues, rebaseRequired, allowPassive);
		theSourceToDest = new HashMap<>();
		theDestToSource = new HashMap<>();
		theElementsToRemove = new ArrayList<>();
	}

	public OneToOneCollectionLink(ObservableCollectionChainLink<?, E> parent, TestValueType type, CollectionDataFlow<?, ?, T> flow,
		TestHelper helper, boolean checkRemovedValues) {
		super(parent, type, flow, helper, checkRemovedValues);
		theSourceToDest = new HashMap<>();
		theDestToSource = new HashMap<>();
		theElementsToRemove = new ArrayList<>();
	}

	@Override
	protected void change(ObservableCollectionEvent<? extends T> evt) {
		if (evt.getType() == CollectionChangeType.remove)
			theElementsToRemove.add(getElements().get(evt.getIndex()));
		super.change(evt);
		if (evt.getType() == CollectionChangeType.add) {
			LinkElement srcEl = getParent().getLastAddedOrModifiedElement();
			if (srcEl != theLastAddedSource) {
				theLastAddedSource = srcEl;
				LinkElement destEl = getAddedElements().getLast();
				mapSourceElement(srcEl, destEl);
			}
		}
	}

	protected LinkElement getSourceElement(LinkElement ours) {
		return theDestToSource.get(ours);
	}

	protected LinkElement getDestElement(LinkElement src) {
		return theSourceToDest.get(src);
	}

	protected void mapSourceElement(LinkElement srcEl, LinkElement destEl) {
		theSourceToDest.put(srcEl, destEl);
		theDestToSource.put(destEl, srcEl);
	}

	@Override
	public void check(boolean transComplete) {
		super.check(transComplete);
		theLastAddedSource = null;
		for (LinkElement el : theElementsToRemove) {
			LinkElement srcEl = theDestToSource.remove(el);
			theSourceToDest.remove(srcEl);
		}
		theElementsToRemove.clear();
	}
}
