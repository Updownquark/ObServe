package org.observe.supertest.collect;

import java.util.Set;

import org.observe.collect.ObservableCollection;
import org.observe.supertest.ChainLinkGenerator;
import org.observe.supertest.CollectionOpType;
import org.observe.supertest.ObservableChainLink;
import org.observe.supertest.OperationRejection;
import org.observe.supertest.TestValueType;
import org.qommons.TestHelper;
import org.qommons.TestHelper.RandomAction;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.MutableCollectionElement.StdMsg;

/**
 * Tests
 * {@link org.observe.collect.ObservableCollection.CollectionDataFlow#whereContained(org.observe.collect.ObservableCollection.CollectionDataFlow, boolean)}
 *
 * @param <T> The type of the collection
 */
public class ContainmentCollectionLink<T> extends ObservableCollectionLink<T, T> {
	/** Generates {@link ContainmentCollectionLink}s */
	public static final ChainLinkGenerator GENERATE = new ChainLinkGenerator() {
		@Override
		public <T> double getAffinity(ObservableChainLink<?, T> sourceLink, TestValueType targetType) {
			if (!(sourceLink instanceof ObservableCollectionLink))
				return 0;
			else if (targetType != null && targetType != sourceLink.getType())
				return 0;
			ObservableCollectionLink<?, T> sourceCL = (ObservableCollectionLink<?, T>) sourceLink;
			if (sourceCL.getValueSupplier() == null)
				return 0;
			return 1;
		}

		@Override
		public <T, X> ObservableChainLink<T, X> deriveLink(String path, ObservableChainLink<?, T> sourceLink, TestValueType targetType,
			TestHelper helper) {
			ObservableCollectionLink<?, T> sourceCL = (ObservableCollectionLink<?, T>) sourceLink;
			ObservableCollection<T> filter = ObservableCollection.build(sourceCL.getCollection().getType()).build();
			int values = sourceCL.getType() == TestValueType.BOOLEAN ? 1 : helper.getInt(0, 100);
			for (int i = 0; i < values; i++)
				filter.add(sourceCL.getValueSupplier().apply(helper));

			boolean include = helper.getBoolean();
			ObservableCollection.CollectionDataFlow<?, ?, T> oneStepFlow = sourceCL.getCollection().flow().whereContained(filter.flow(),
				include);
			ObservableCollection.CollectionDataFlow<?, ?, T> multiStepFlow = sourceCL.getDef().multiStepFlow.whereContained(filter.flow(),
				include);
			ObservableCollectionTestDef<T> def = new ObservableCollectionTestDef<>(sourceCL.getType(), oneStepFlow, multiStepFlow,
				sourceCL.getDef().orderImportant, sourceCL.getDef().checkOldValues);
			return (ObservableCollectionLink<T, X>) new ContainmentCollectionLink<>(path, sourceCL, def, helper, filter, include);
		}
	};

	private final ObservableCollection<T> theIntersection;
	private final boolean isInclude;

	/**
	 * @param path The path for this link
	 * @param sourceLink The source for this link
	 * @param def The collection definition for this link
	 * @param helper The randomness to use to initialize this link
	 * @param intersection The collection to use to filter the source collection
	 * @param include Whether elements from the source collection will be present in this collection as a function of their presence or
	 *        absence in the filter collection
	 */
	public ContainmentCollectionLink(String path, ObservableCollectionLink<?, T> sourceLink, ObservableCollectionTestDef<T> def,
		TestHelper helper, ObservableCollection<T> intersection, boolean include) {
		super(path, sourceLink, def, helper);
		theIntersection = intersection;
		isInclude = include;
	}

	@Override
	public boolean isAcceptable(T value) {
		boolean found = theIntersection.contains(value);
		if (isInclude ^ found)
			return false;
		return getSourceLink().isAcceptable(value);
	}

	@Override
	public T getUpdateValue(CollectionLinkElement<T, T> element, T value) {
		return ((ObservableCollectionLink<Object, T>) getSourceLink())
			.getUpdateValue((CollectionLinkElement<Object, T>) element.getFirstSource(), value);
	}

	@Override
	public double getModificationAffinity() {
		return super.getModificationAffinity() + 2;
	}

	@Override
	public void tryModify(RandomAction action, TestHelper helper) {
		super.tryModify(action, helper);
		action.or(2, () -> {
			if (helper.isReproducing())
				System.out.print("Adjusting filter: ");
			Set<T> filterCopy = getCollection().equivalence().createSet();
			filterCopy.addAll(theIntersection);
			RandomAction filterAction = helper.createAction()//
				.or(5, () -> {
					T value = getValueSupplier().apply(helper);
					if (helper.isReproducing())
						System.out.println("Adding " + value);
					theIntersection.add(value);
				});
			if (!theIntersection.isEmpty()) {
				filterAction.or(3, () -> {
					int index = helper.getInt(0, theIntersection.size());
					if (helper.isReproducing())
						System.out.println("Removing " + theIntersection.get(index) + "@" + index);
					theIntersection.remove(index);
				}).or(1, () -> {
					if (helper.isReproducing())
						System.out.println("Clearing");
					theIntersection.clear();
				}).or(3, () -> {
					int index = helper.getInt(0, theIntersection.size());
					T value = helper.getBoolean(0.1) ? theIntersection.get(index) : getValueSupplier().apply(helper);
					if (helper.isReproducing())
						System.out.println("Setting " + theIntersection.get(index) + "@" + index + " to " + value);
					theIntersection.set(index, value);
				});
			}
			filterAction.execute(null);
			expectFilterChange(filterCopy);
		});
	}

	void expectFilterChange(Set<T> oldFilter) {
		for (CollectionLinkElement<?, T> sourceEl : getSourceLink().getElements()) {
			boolean wasFound = oldFilter.contains(sourceEl.getValue());
			boolean isFound = theIntersection.contains(sourceEl.getValue());
			if (wasFound == isFound) {//
			} else if (wasFound ^ isInclude)
				((CollectionLinkElement<T, T>) sourceEl.getDerivedElements(getSiblingIndex()).getFirst()).expectAdded(sourceEl.getValue());
			else
				sourceEl.getDerivedElements(getSiblingIndex()).getFirst().expectRemoval();
		}
	}

	@Override
	public CollectionLinkElement<T, T> expectAdd(T value, CollectionLinkElement<?, T> after, CollectionLinkElement<?, T> before,
		boolean first, OperationRejection rejection, boolean execute) {
		boolean found = theIntersection.contains(value);
		if (isInclude ^ found) {
			rejection.reject(StdMsg.ILLEGAL_ELEMENT);
			return null;
		}
		CollectionLinkElement<?, T> sourceEl = getSourceLink().expectAdd(value, //
			after == null ? null : (CollectionLinkElement<?, T>) after.getFirstSource(), //
				before == null ? null : (CollectionLinkElement<?, T>) before.getFirstSource(), first, rejection, execute);
		if (sourceEl == null)
			return null;
		return (CollectionLinkElement<T, T>) sourceEl.getDerivedElements(getSiblingIndex()).getFirst();
	}

	@Override
	public CollectionLinkElement<T, T> expectMove(CollectionLinkElement<?, T> source, CollectionLinkElement<?, T> after,
		CollectionLinkElement<?, T> before, boolean first, OperationRejection rejection, boolean execute) {
		CollectionLinkElement<?, T> sourceEl = getSourceLink().expectMove(//
			(CollectionLinkElement<?, T>) source.getFirstSource(), //
			after == null ? null : (CollectionLinkElement<?, T>) after.getFirstSource(), //
				before == null ? null : (CollectionLinkElement<?, T>) before.getFirstSource(), first, rejection, execute);
		if (sourceEl == null)
			return null;
		return (CollectionLinkElement<T, T>) sourceEl.getDerivedElements(getSiblingIndex()).getFirst();
	}

	@Override
	public void expect(ExpectedCollectionOperation<?, T> derivedOp, OperationRejection rejection, boolean execute) {
		if (derivedOp.getType() == CollectionOpType.set) {
			boolean found = theIntersection.contains(derivedOp.getValue());
			if (isInclude ^ found) {
				rejection.reject(StdMsg.ILLEGAL_ELEMENT);
				return;
			}
		}
		getSourceLink().expect(//
			new ExpectedCollectionOperation<>((CollectionLinkElement<?, T>) derivedOp.getElement().getFirstSource(), derivedOp.getType(),
				derivedOp.getOldValue(), derivedOp.getValue()),
			rejection, execute);
	}

	@Override
	public void expectFromSource(ExpectedCollectionOperation<?, T> sourceOp) {
		CollectionLinkElement<T, T> derivedEl = (CollectionLinkElement<T, T>) sourceOp.getElement().getDerivedElements(getSiblingIndex())
			.peekFirst();
		switch (sourceOp.getType()) {
		case move:
			throw new IllegalStateException();
		case add:
			boolean found = theIntersection.contains(sourceOp.getValue());
			if (isInclude ^ found)
				return;
			derivedEl.expectAdded(sourceOp.getValue());
			break;
		case remove:
			found = theIntersection.contains(sourceOp.getValue());
			if (isInclude ^ found)
				return;
			derivedEl.expectRemoval();
			break;
		case set:
			boolean wasFound = theIntersection.contains(sourceOp.getOldValue());
			found = theIntersection.contains(sourceOp.getValue());
			if (wasFound == found) {
				if (isInclude ^ found)
					return;
				derivedEl.expectSet(sourceOp.getValue());
			} else if (isInclude ^ found) // Removed
				derivedEl.expectRemoval();
			else // Added
				derivedEl.expectAdded(sourceOp.getValue());
			break;
		}
	}

	@Override
	protected void validate(CollectionLinkElement<T, T> element, boolean transactionEnd) {
		if (element.isPresent()) {
			CollectionElement<CollectionLinkElement<T, T>> adj = getElements().getAdjacentElement(element.getElementAddress(), false);
			while (adj != null && !adj.get().isPresent())
				adj = getElements().getAdjacentElement(adj.getElementId(), false);
			if (adj != null) {
				int comp = adj.get().getFirstSource().getElementAddress().compareTo(//
					element.getFirstSource().getElementAddress());
				if (comp >= 0)
					throw new AssertionError("Filtered elements not in source order");
			}
		}
	}

	@Override
	public String toString() {
		return "where" + (isInclude ? "Present" : "Absent") + "(" + theIntersection.size() + ")";
	}
}
