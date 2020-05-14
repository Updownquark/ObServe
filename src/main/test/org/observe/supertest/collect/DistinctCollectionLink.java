package org.observe.supertest.collect;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.Assert;
import org.observe.collect.Equivalence;
import org.observe.collect.FlowOptions;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.supertest.ChainLinkGenerator;
import org.observe.supertest.ObservableChainLink;
import org.observe.supertest.OperationRejection;
import org.observe.supertest.TestValueType;
import org.qommons.BiTuple;
import org.qommons.QommonsUtils;
import org.qommons.TestHelper;
import org.qommons.collect.BetterList;
import org.qommons.collect.ElementId;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.debug.Debug;
import org.qommons.tree.BetterTreeList;

/**
 * Tests {@link org.observe.collect.ObservableCollection.CollectionDataFlow#distinct()} and
 * {@link org.observe.collect.ObservableCollection.CollectionDataFlow#distinctSorted(Comparator, boolean)}
 *
 * @param <T> The type of the collection values
 */
public class DistinctCollectionLink<T> extends ObservableCollectionLink<T, T> {
	private static final String DEBUG_PATH = null;

	/** Generates {@link DistinctCollectionLink}s to test {@link org.observe.collect.ObservableCollection.CollectionDataFlow#distinct()} */
	public static final ChainLinkGenerator GENERATE = new ChainLinkGenerator.CollectionLinkGenerator() {
		@Override
		public <T> double getAffinity(ObservableChainLink<?, T> sourceLink, TestValueType targetType) {
			if (!(sourceLink instanceof ObservableCollectionLink))
				return 0;
			else if (targetType != null && targetType != sourceLink.getType())
				return 0;
			return 1;
		}

		@Override
		public <T, X> ObservableCollectionLink<T, X> deriveLink(String path, ObservableChainLink<?, T> sourceLink, TestValueType targetType,
			TestHelper helper) {
			ObservableCollectionLink<?, T> sourceCL = (ObservableCollectionLink<?, T>) sourceLink;
			FlowOptions.UniqueOptions options = new FlowOptions.SimpleUniqueOptions(true);
			options
			.useFirst(// TODO helper.getBoolean()
				false)//
			.preserveSourceOrder(// TODO helper.getBoolean()
				false);
			CollectionDataFlow<?, ?, T> oneStepFlow = sourceCL.getCollection().flow();
			CollectionDataFlow<?, ?, T> multiStepFlow = sourceCL.getDef().multiStepFlow;
			if (oneStepFlow.equivalence() instanceof Equivalence.ComparatorEquivalence) {
				oneStepFlow = oneStepFlow.withEquivalence(Equivalence.DEFAULT);
				multiStepFlow = multiStepFlow.withEquivalence(Equivalence.DEFAULT);
			}
			oneStepFlow = oneStepFlow.distinct(opts -> opts.isUseFirst());
			multiStepFlow = multiStepFlow.distinct(opts -> opts.isUseFirst());
			if(path.equals(DEBUG_PATH)){
				Debug.d().debug(multiStepFlow, true).onAction(action -> {
					System.out.println(Integer.toHexString(action.getData().getValue().hashCode()) + ":" + action);
				});
			}
			ObservableCollectionTestDef<T> def = new ObservableCollectionTestDef<>(sourceLink.getType(), oneStepFlow, multiStepFlow, false,
				true);
			DistinctCollectionLink<T> derived = new DistinctCollectionLink<>(path, sourceCL, def, null, options.isUseFirst(), helper);
			return (ObservableCollectionLink<T, X>) derived;
		}
	};

	/**
	 * Generates sorted {@link DistinctCollectionLink}s to test
	 * {@link org.observe.collect.ObservableCollection.CollectionDataFlow#distinctSorted(Comparator, boolean)}
	 */
	public static final ChainLinkGenerator GENERATE_SORTED = new ChainLinkGenerator.CollectionLinkGenerator() {
		@Override
		public <T> double getAffinity(ObservableChainLink<?, T> sourceLink, TestValueType targetType) {
			if (!(sourceLink instanceof ObservableCollectionLink))
				return 0;
			else if (targetType != null && targetType != sourceLink.getType())
				return 0;
			return 1;
		}

		@Override
		public <T, X> ObservableCollectionLink<T, X> deriveLink(String path, ObservableChainLink<?, T> sourceLink, TestValueType targetType,
			TestHelper helper) {
			ObservableCollectionLink<?, T> sourceCL = (ObservableCollectionLink<?, T>) sourceLink;
			Comparator<T> compare = SortedCollectionLink.compare(sourceCL.getDef().type, helper);
			FlowOptions.UniqueOptions options = new FlowOptions.SimpleUniqueOptions(true);
			options.useFirst(// TODO helper.getBoolean()
				false);
			CollectionDataFlow<?, ?, T> oneStepFlow = sourceCL.getCollection().flow();
			CollectionDataFlow<?, ?, T> multiStepFlow = sourceCL.getDef().multiStepFlow;
			oneStepFlow = oneStepFlow.distinctSorted(compare, options.isUseFirst());
			multiStepFlow = multiStepFlow.distinctSorted(compare, options.isUseFirst());
			ObservableCollectionTestDef<T> def = new ObservableCollectionTestDef<>(sourceLink.getType(), oneStepFlow, multiStepFlow, true,
				true);
			DistinctCollectionLink<T> derived = new DistinctCollectionLink<>(path, sourceCL, def, compare, options.isUseFirst(), helper);
			return (ObservableCollectionLink<T, X>) derived;
		}
	};

	private final SortedLinkHelper<T> theHelper;
	private final Map<T, ValueElement> theValues;
	private final boolean isUsingFirst;

	/**
	 * @param path The path for this link
	 * @param sourceLink The source for this link
	 * @param def The collection definition for this link
	 * @param compare The sorting to use (if sorted)
	 * @param useFirst Whether the collection should be using the first source link associated with each value as the active element
	 * @param helper The randomness to use to initialize this link
	 */
	public DistinctCollectionLink(String path, ObservableCollectionLink<?, T> sourceLink, ObservableCollectionTestDef<T> def,
		Comparator<? super T> compare, boolean useFirst, TestHelper helper) {
		super(path, sourceLink, def, helper);
		theHelper = compare == null ? null : new SortedLinkHelper<>(compare, useFirst);
		if (compare != null)
			theValues = new TreeMap<>(compare);
		else
			theValues = new HashMap<>();
		isUsingFirst = useFirst;
	}

	@Override
	public boolean isAcceptable(T value) {
		if (theValues.containsKey(value))
			return false;
		return getSourceLink().isAcceptable(value);
	}

	@Override
	public T getUpdateValue(CollectionLinkElement<T, T> element, T value) {
		return ((ObservableCollectionLink<Object, T>) getSourceLink())
			.getUpdateValue((CollectionLinkElement<Object, T>) element.getFirstSource(), value);
	}

	private boolean isControllingOperation;

	@Override
	public CollectionLinkElement<T, T> expectAdd(T value, CollectionLinkElement<?, T> after, CollectionLinkElement<?, T> before,
		boolean first, OperationRejection rejection, boolean execute) {
		if (theHelper != null) {
			BiTuple<CollectionLinkElement<?, T>, CollectionLinkElement<?, T>> afterBefore = theHelper.expectAdd(value, after, before, first,
				rejection);
			if (afterBefore == null)
				return null;
			after = afterBefore.getValue1();
			before = afterBefore.getValue2();
		}
		if (theValues.containsKey(value)) {
			rejection.reject(StdMsg.ELEMENT_EXISTS);
			return null;
		}

		CollectionLinkElement<?, T> sourceEl;
		if (theHelper != null)
			sourceEl = getSourceLink().expectAdd(value, //
				after == null ? null : (CollectionLinkElement<?, T>) after.getFirstSource(),
					before == null ? null : (CollectionLinkElement<?, T>) before.getFirstSource(), //
						first, rejection, execute);
		else
			sourceEl = getSourceLink().expectAdd(value, null, null, first, rejection, execute);
		if (sourceEl == null)
			return null;
		CollectionLinkElement<T, T> element = (CollectionLinkElement<T, T>) sourceEl.getDerivedElements(getSiblingIndex()).getFirst();
		checkOrder(element, after, before);
		return element;
	}

	@Override
	public CollectionLinkElement<T, T> expectMove(CollectionLinkElement<?, T> source, CollectionLinkElement<?, T> after,
		CollectionLinkElement<?, T> before, boolean first, OperationRejection rejection, boolean execute) {
		if (theHelper != null)
			theHelper.expectMove(source, after, before, first, rejection);
		if (!rejection.isRejected()) {
			CollectionLinkElement<T, T> newSource;
			if (!source.isPresent()) {
				BetterList<CollectionLinkElement<T, ?>> sourceDerived = ((CollectionLinkElement<T, T>) source.getFirstSource())
					.getDerivedElements(getSiblingIndex());
				Assert.assertEquals(2, sourceDerived.size());
				newSource = (CollectionLinkElement<T, T>) sourceDerived.getLast();
				if (execute) {
					source.expectRemoval();
					newSource.expectAdded(source.getValue());
				}
			} else
				newSource = (CollectionLinkElement<T, T>) source;
			theValues.get(source.getValue()).element = newSource;
			checkOrder(newSource, after == source ? null : after, before == source ? null : before);
		}
		return rejection.isRejected() ? null : (CollectionLinkElement<T, T>) source; // Doesn't delegate to source for a move operation
	}

	@Override
	public void expect(ExpectedCollectionOperation<?, T> derivedOp, OperationRejection rejection, boolean execute) {
		if (rejection.isRejectable() && theHelper != null && !theHelper.expectSet(derivedOp, rejection, getElements()))
			return;
		T oldValue = derivedOp.getElement().getValue();
		ValueElement valueEl = theValues.computeIfAbsent(oldValue,
			__ -> new ValueElement((CollectionLinkElement<T, T>) derivedOp.getElement()));
		boolean set = derivedOp.getType() == ExpectedCollectionOperation.CollectionOpType.set;
		if (rejection.isRejectable()) { // If not rejectable, presumably this has already been tested
			for (CollectionLinkElement<?, T> sourceEl : valueEl.sourceElements) {
				getSourceLink().expect(
					new ExpectedCollectionOperation<>(sourceEl, derivedOp.getType(), sourceEl.getValue(), derivedOp.getValue()), rejection,
					false);
				if (rejection.isRejected())
					return;
			}
			if (set) {
				boolean equal;
				if (theHelper != null)
					equal = theHelper.getCompare().compare(oldValue, derivedOp.getValue()) == 0;
				else
					equal = oldValue.equals(derivedOp.getValue());
				if (!equal && rejection.isRejectable() && theValues.containsKey(derivedOp.getValue())) {
					rejection.reject(StdMsg.ELEMENT_EXISTS);
					return;
				}
			}
		}
		if (execute) {
			if (set)
				isControllingOperation = true;

			// The distinct manager calls the active parent element first, but which one is active involves a lot of state to figure out
			// The order of operations here isn't always important, but it can affect other things
			ElementId activeEl = getCollection().getSourceElements(valueEl.element.getCollectionAddress(), getSourceLink().getCollection())
				.getFirst();
			rejection.unrejectable();
			// Because each source.expect method below has the potential to remove multiple source elements,
			// we may need to make a copy of the list
			List<CollectionLinkElement<?, T>> sourceEls = valueEl.sourceElements;
			if (isComposite())
				sourceEls = QommonsUtils.unmodifiableCopy(sourceEls);
			for (CollectionLinkElement<?, T> sourceEl : sourceEls) {
				if (sourceEl.isRemoveExpected() || !sourceEl.getCollectionAddress().equals(activeEl))
					continue;
				getSourceLink().expect(
					new ExpectedCollectionOperation<>(sourceEl, derivedOp.getType(), sourceEl.getValue(), derivedOp.getValue()), rejection,
					true);
			}
			for (CollectionLinkElement<?, T> sourceEl : sourceEls) {
				if (sourceEl.isRemoveExpected() || sourceEl.getCollectionAddress().equals(activeEl))
					continue;
				getSourceLink().expect(
					new ExpectedCollectionOperation<>(sourceEl, derivedOp.getType(), sourceEl.getValue(), derivedOp.getValue()), rejection,
					true);
			}
			if (set) {
				theValues.remove(oldValue);
				theValues.put(derivedOp.getValue(), valueEl);
				isControllingOperation = false;
				valueEl.element.expectSet(derivedOp.getValue());
			}
		}
	}

	private int theLastRefreshedModification = -2;

	private void refreshElements() {
		for (CollectionLinkElement<T, T> el : getElements())
			el.updateSourceLinks(false);
	}

	@Override
	public void expectFromSource(ExpectedCollectionOperation<?, T> sourceOp) {
		if (isControllingOperation)
			return;
		if (getOverallModification() != theLastRefreshedModification) {
			// There are very unique circumstances which can cause a source element encountered here to not have any
			// derived elements mapped to this link.
			// This is not a bug, but a consequence of the order of eventing and the fact that the distinct manager swallows
			// duplicate values (by design)
			// So we need to do this manually
			theLastRefreshedModification = getOverallModification();
			refreshElements();
		}
		T searchValue;
		if (sourceOp.getType() == ExpectedCollectionOperation.CollectionOpType.add)
			searchValue = sourceOp.getValue();
		else
			searchValue = sourceOp.getOldValue();
		ValueElement valueEl = theValues.get(searchValue);
		CollectionLinkElement<T, T> element;
		switch (sourceOp.getType()) {
		case add:
			if (valueEl == null) {
				element = (CollectionLinkElement<T, T>) sourceOp.getElement().getDerivedElements(getSiblingIndex()).getFirst();
				valueEl = new ValueElement(element);
				theValues.put(sourceOp.getValue(), valueEl);
			} else
				element = valueEl.element;
			boolean add = valueEl.sourceElements.isEmpty();
			valueEl.sourceElements.add(sourceOp.getElement());
			if (add)
				element.expectAdded(sourceOp.getValue());
			break;
		case remove:
			element = valueEl.element;
			Assert.assertTrue(valueEl.sourceElements.remove(sourceOp.getElement()));
			if (valueEl.sourceElements.isEmpty()) {
				T oldValue = element.getValue();
				theValues.remove(oldValue);
				element.expectRemoval();
			}
			break;
		case set:
			boolean equal;
			if (theHelper != null)
				equal = theHelper.getCompare().compare(sourceOp.getOldValue(), sourceOp.getValue()) == 0;
			else
				equal = sourceOp.getOldValue().equals(sourceOp.getValue());
			if (equal) {
				element = valueEl.element;
				element.expectSet(sourceOp.getValue());
			} else {
				BetterList<CollectionLinkElement<T, ?>> derivedEls = sourceOp.getElement().getDerivedElements(getSiblingIndex());
				ValueElement newValueEl;
				T oldValue = valueEl.element.getValue();
				if (getSourceLink().getDef().checkOldValues)
					Assert.assertEquals(oldValue, sourceOp.getOldValue());
				if (theValues.containsKey(sourceOp.getValue())) {
					newValueEl = theValues.get(sourceOp.getValue());
					if (derivedEls.size() != 2)
						Assert.assertEquals(getPath() + this + ":" + sourceOp.getElement().toString(), 2, derivedEls.size());
					Assert.assertEquals(newValueEl.element, derivedEls.getLast());
				} else if (valueEl.sourceElements.size() > 1
					|| (theHelper != null && theHelper.expectMoveFromSource(sourceOp, getSiblingIndex(), getElements()))) {
					if (derivedEls.size() != 2)
						Assert.assertEquals(getPath() + this + ":" + sourceOp.getElement().toString(), 2, derivedEls.size());
					newValueEl = new ValueElement((CollectionLinkElement<T, T>) derivedEls.getLast());
					theValues.put(sourceOp.getValue(), newValueEl);
				} else if (derivedEls.size() > 1) {
					// Order of events can determine whether an element needs to be moved or not,
					// so we'll accommodate if it's moved unexpectedly
					newValueEl = theValues.computeIfAbsent(sourceOp.getValue(),
						__ -> new ValueElement((CollectionLinkElement<T, T>) derivedEls.getLast()));
				} else {
					valueEl.element.expectSet(sourceOp.getValue());
					theValues.remove(oldValue);
					theValues.put(sourceOp.getValue(), valueEl);
					return;
				}

				Assert.assertTrue(valueEl.sourceElements.remove(sourceOp.getElement()));
				if (valueEl.sourceElements.isEmpty()) {
					theValues.remove(oldValue);
					valueEl.element.expectRemoval();
				}
				add = newValueEl.sourceElements.isEmpty();
				newValueEl.sourceElements.add(sourceOp.getElement());
				if (add) {
					newValueEl.element.expectAdded(sourceOp.getValue());
				}
			}
			break;
		case move:
			break;
		}
	}

	@Override
	protected void validate(CollectionLinkElement<T, T> element) {
		checkOrder(element, null, null);
	}

	void checkOrder(CollectionLinkElement<?, T> element, CollectionLinkElement<?, T> after, CollectionLinkElement<?, T> before) {
		if (theHelper != null)
			theHelper.checkOrder(getElements(), (CollectionLinkElement<T, T>) element);
		else {
			if (after != null && element.getElementAddress().compareTo(after.getElementAddress()) <= 0)
				element.error("Added before " + after);
			if (before != null && element.getElementAddress().compareTo(before.getElementAddress()) >= 0)
				element.error("Added after " + before);
		}
	}

	@Override
	public String toString() {
		return "distinct" + (theHelper != null ? "Sorted" : "") + "(" + (isUsingFirst ? "first" : "") + ")";
	}

	class ValueElement {
		CollectionLinkElement<T, T> element;
		final List<CollectionLinkElement<?, T>> sourceElements;

		ValueElement(CollectionLinkElement<T, T> element) {
			this.element = element;
			sourceElements = new BetterTreeList<>(false);
		}

		@Override
		public String toString() {
			return sourceElements.toString();
		}
	}
}
