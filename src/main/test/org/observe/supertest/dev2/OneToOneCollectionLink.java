package org.observe.supertest.dev2;

import java.util.List;
import java.util.function.Function;

import org.junit.Assert;
import org.observe.collect.CollectionChangeType;
import org.qommons.TestHelper;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterSortedList;
import org.qommons.collect.BetterSortedList.SortedSearchFilter;
import org.qommons.tree.BinaryTreeNode;

public abstract class OneToOneCollectionLink<S, T> extends ObservableCollectionLink<S, T> {
	protected static final Function<CollectionLinkElement<?, ?>, Comparable<? super CollectionLinkElement<?, ?>>> SOURCE_ORDERED;
	static {
		SOURCE_ORDERED = sourceEl -> node -> sourceEl.getExpectedAddress()
			.compareTo(node.getSourceElements().getFirst().getExpectedAddress());
	}

	private final Function<? super CollectionLinkElement<?, S>, ? extends Comparable<? super CollectionLinkElement<S, T>>> theExpectedElementFinder;

	public OneToOneCollectionLink(ObservableCollectionLink<?, S> sourceLink, ObservableCollectionTestDef<T> def, TestHelper helper) {
		this(sourceLink, def, helper, SOURCE_ORDERED);
	}

	public OneToOneCollectionLink(ObservableCollectionLink<?, S> sourceLink, ObservableCollectionTestDef<T> def, TestHelper helper,
		Function<? super CollectionLinkElement<?, S>, ? extends Comparable<? super CollectionLinkElement<S, T>>> expectedElementFinder) {
		super(sourceLink, def, helper);
		theExpectedElementFinder = expectedElementFinder;
	}

	protected abstract T map(S sourceValue);

	protected abstract S reverse(T value);

	@Override
	public void initialize(TestHelper helper) {
		for (CollectionLinkElement<?, S> sourceEl : theSourceLink.getElements())
			expectAddFromSource(sourceEl);
	}

	@Override
	public List<ExpectedCollectionOperation<S, T>> expectFromSource(ExpectedCollectionOperation<?, S> sourceOp) {
		switch (sourceOp.getType()) {
		case add:
			return expectAddFromSource(sourceOp.getElement());
		case remove:
			return expectRemoveFromSource(sourceOp.getElement(), sourceOp.getOldValue());
		case set:
			return expectChangeFromSource(sourceOp.getElement(), sourceOp.getOldValue());
		}
		throw new IllegalStateException();
	}

	@Override
	public void expect(ExpectedCollectionOperation<?, T> derivedOp, OperationRejection rejection) {
		switch (derivedOp.getType()) {
		case add:
			throw new IllegalStateException("Should be using expectAdd");
		case remove:
			theSourceLink.expect(new ExpectedCollectionOperation<>(//
				(CollectionLinkElement<Object, S>) derivedOp.getElement().getSourceElements().getFirst(), CollectionChangeType.remove,
				reverse(derivedOp.getElement().get()), reverse(derivedOp.getElement().get())), rejection);
			if (!rejection.isRejected())
				theExpectedElements.mutableElement(derivedOp.getElement().getExpectedAddress()).remove();
			break;
		case set:
			theSourceLink.expect(new ExpectedCollectionOperation<>(//
				(CollectionLinkElement<Object, S>) derivedOp.getElement().getSourceElements().getFirst(), CollectionChangeType.set,
				reverse(derivedOp.getElement().get()), reverse(derivedOp.getValue())), rejection);
			if (!rejection.isRejected())
				derivedOp.getElement().setValue(derivedOp.getValue());
			break;
		}
	}

	@Override
	public CollectionLinkElement<S, T> expectAdd(T value, CollectionLinkElement<?, T> after, CollectionLinkElement<?, T> before,
		boolean first, OperationRejection rejection) {
		CollectionLinkElement<S, T> newElement;
		CollectionLinkElement<?, S> sourceEl = theSourceLink.expectAdd(reverse(value), //
			after == null ? null : ((CollectionLinkElement<S, T>) after).getSourceElements().getFirst(), //
				before == null ? null : ((CollectionLinkElement<S, T>) before).getSourceElements().getFirst(), //
					first, rejection);
		if (rejection.isRejected())
			return null;
		newElement = addFromSource(sourceEl);
		return newElement;
	}

	protected CollectionLinkElement<S, T> addFromSource(CollectionLinkElement<?, S> sourceEl) {
		Comparable<? super CollectionLinkElement<S, T>> finder = theExpectedElementFinder.apply(sourceEl);
		BinaryTreeNode<CollectionLinkElement<S, T>> adjacent = theExpectedElements.search(finder, BetterSortedList.SortedSearchFilter.PreferLess);
		BinaryTreeNode<CollectionLinkElement<S, T>> newNode;
		if (adjacent == null)
			newNode = theExpectedElements.addElement(null, false);
		else {
			int comp = finder.compareTo(adjacent.get());
			if (comp == 0)
				throw new IllegalStateException("Accounting error: Derived element for " + sourceEl + " already found: " + adjacent.get());
			else if (comp > 0)
				newNode = theExpectedElements.addElement(null, adjacent.getElementId(), null, true);
			else
				newNode = theExpectedElements.addElement(null, null, adjacent.getElementId(), false);
		}
		CollectionLinkElement<S, T> newElement = new CollectionLinkElement<>(this, map(sourceEl.getValue()))
			.setExpectedAddress(newNode.getElementId()).withSourceElement(sourceEl);
		theExpectedElements.mutableElement(newNode.getElementId()).set(newElement);
		return newElement;
	}

	private List<ExpectedCollectionOperation<S, T>> expectAddFromSource(CollectionLinkElement<?, S> sourceEl) {
		CollectionLinkElement<S, T> newElement = addFromSource(sourceEl);
		ExpectedCollectionOperation<S, T> result = new ExpectedCollectionOperation<>(newElement, CollectionChangeType.add, null,
			newElement.get());
		if (getDerivedLink() != null)
			newElement.applyDerivedChanges(getDerivedLink().expectFromSource(result));
		return BetterList.of(result);
	}

	private List<ExpectedCollectionOperation<S, T>> expectRemoveFromSource(CollectionLinkElement<?, S> sourceEl, S oldSrcValue) {
		Comparable<? super CollectionLinkElement<S, T>> finder = theExpectedElementFinder.apply(sourceEl);
		BinaryTreeNode<CollectionLinkElement<S, T>> found = theExpectedElements.search(finder, BetterSortedList.SortedSearchFilter.OnlyMatch);
		if (found == null)
			throw new IllegalStateException("Accounting error: Derived element for " + sourceEl + " not found");
		CollectionLinkElement<S, T> element = found.get();
		if (!getCollection().equivalence().elementEquals(element.get(), map(oldSrcValue)))
			Assert.assertTrue("Wrong value removed", false);
		theExpectedElements.mutableElement(found.getElementId()).remove();
		ExpectedCollectionOperation<S, T> result = new ExpectedCollectionOperation<>(element, CollectionChangeType.remove, element.get(),
			element.get());
		if (getDerivedLink() != null)
			element.applyDerivedChanges(getDerivedLink().expectFromSource(result));
		return BetterList.of(result);
	}

	private List<ExpectedCollectionOperation<S, T>> expectChangeFromSource(CollectionLinkElement<?, S> sourceEl, S oldSrcValue) {
		Comparable<? super CollectionLinkElement<S, T>> finder = theExpectedElementFinder.apply(sourceEl);
		BinaryTreeNode<CollectionLinkElement<S, T>> found = theExpectedElements.search(finder, BetterSortedList.SortedSearchFilter.OnlyMatch);
		if (found == null)
			throw new IllegalStateException("Accounting error: Derived element for " + sourceEl + " not found");
		CollectionLinkElement<S, T> element = found.get();
		T oldValue = element.get();
		if (!getCollection().equivalence().elementEquals(oldValue, map(oldSrcValue)))
			Assert.assertTrue("Wrong value updated", false);
		element.setValue(map(sourceEl.get()));
		ExpectedCollectionOperation<S, T> result = new ExpectedCollectionOperation<>(element, CollectionChangeType.set, oldValue,
			element.get());
		if (getDerivedLink() != null)
			element.applyDerivedChanges(getDerivedLink().expectFromSource(result));
		return BetterList.of(result);
	}
}
