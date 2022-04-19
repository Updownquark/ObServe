package org.observe.supertest.collect;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.observe.SettableValue;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.supertest.ChainLinkGenerator;
import org.observe.supertest.ObservableChainLink;
import org.observe.supertest.OperationRejection;
import org.observe.supertest.TestValueType;
import org.qommons.BiTuple;
import org.qommons.LambdaUtils;
import org.qommons.StringUtils;
import org.qommons.TestHelper;
import org.qommons.TestHelper.RandomAction;
import org.qommons.collect.CollectionElement;

/**
 * Tests {@link org.observe.collect.ObservableCollection.CollectionDataFlow#sorted(Comparator)}
 *
 * @param <T> The type of values in the collection
 */
public class SortedCollectionLink<T> extends ObservableCollectionLink<T, T> implements CollectionSourcedLink<T, T> {
	/** Generates {@link SortedCollectionLink}s */
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
			Comparator<T> compare = compare(sourceLink.getType(), helper);
			SettableValue<SortedLinkHelper<T>> sort = SettableValue.build((Class<SortedLinkHelper<T>>) (Class<?>) SortedLinkHelper.class)//
				.withValue(new SortedLinkHelper<>(compare, true)).build();
			Comparator<T> wrappedCompare = LambdaUtils.printableComparator((v1, v2) -> sort.get().getCompare().compare(v1, v2),
				() -> sort.get().getCompare().toString() + "*", null);
			CollectionDataFlow<?, ?, T> derivedOneStepFlow = sourceCL.getCollection().flow();
			CollectionDataFlow<?, ?, T> derivedMultiStepFlow = sourceCL.getDef().multiStepFlow;
			derivedOneStepFlow = derivedOneStepFlow.refresh(sort.noInitChanges()).sorted(wrappedCompare);
			derivedMultiStepFlow = derivedMultiStepFlow.refresh(sort.noInitChanges()).sorted(wrappedCompare);
			ObservableCollectionTestDef<T> def = new ObservableCollectionTestDef<>(sourceCL.getType(), derivedOneStepFlow,
				derivedMultiStepFlow, false, sourceCL.getDef().checkOldValues);
			return (ObservableCollectionLink<T, X>) new SortedCollectionLink<>(path, sourceCL, def, sort, helper);
		}
	};

	private final SettableValue<SortedLinkHelper<T>> theSort;

	/**
	 * @param path The path for this link
	 * @param sourceLink The source for this link
	 * @param def The collection definition for this link
	 * @param sort The sorting for the collection
	 * @param helper The randomness to use to initialize this link
	 */
	public SortedCollectionLink(String path, ObservableCollectionLink<?, T> sourceLink, ObservableCollectionTestDef<T> def,
		SettableValue<SortedLinkHelper<T>> sort, TestHelper helper) {
		super(path, sourceLink, def, helper);
		theSort = sort;
	}

	@Override
	public ObservableCollectionLink<?, T> getSourceLink() {
		return (ObservableCollectionLink<?, T>) super.getSourceLink();
	}

	@Override
	public double getModificationAffinity() {
		return super.getModificationAffinity() + 1;
	}

	@Override
	public void tryModify(RandomAction action, TestHelper helper) {
		super.tryModify(action, helper);
		action.or(1, () -> {
			Comparator<T> compare = compare(getSourceLink().getType(), helper);
			SortedLinkHelper<T> newSort = new SortedLinkHelper<>(compare, true);
			if (helper.isReproducing())
				System.out.println("Sort changed from " + theSort.get().getCompare() + " to " + compare);
			theSort.set(newSort, null);
			// All we need to do here is ensure that the sorted collection obeys the new ordering now.
			// We don't care the order in which things were removed and re-added.
			// We'll go through all the elements and verify that all of them that are present after the sort change
			// are in the right order. Then we'll just report expectations in line with what actually happened.
			CollectionLinkElement<T, T> prevEl = null;
			CollectionLinkElement<T, T> el = getElements().peekFirst();
			while (el != null) {
				while (el != null && !el.isPresent()) {
					if (el.wasAdded()) {
						// Due to the chaos that is tree repair, elements may bubble into and out of existence
						// in their search for their rightful place
						el.expectAdded(el.getCollectionValue());
					}
					el.expectRemoval();
					el = CollectionElement.get(getElements().getAdjacentElement(el.getElementAddress(), true));
				}
				if (el == null)
					break;
				T elValue = el.getCollectionValue();
				if (prevEl != null && compare.compare(prevEl.getValue(), elValue) > 0) {
					T prevValue = prevEl.getValue();
					el.error(s -> s.append("Reorder failed: ").append(prevValue).append(", ").append(elValue));
				}
				if (el.wasAdded())
					el.expectAdded(elValue);
				else
					el.expectSet(elValue);
				prevEl = el;
				el = CollectionElement.get(getElements().getAdjacentElement(el.getElementAddress(), true));
			}
		});
	}

	@Override
	public boolean isAcceptable(T value) {
		return getSourceLink().isAcceptable(value);
	}

	@Override
	public T getUpdateValue(CollectionLinkElement<T, T> element, T value) {
		return ((ObservableCollectionLink<Object, T>) getSourceLink())
			.getUpdateValue((CollectionLinkElement<Object, T>) element.getFirstSource(), value);
	}

	@Override
	public CollectionLinkElement<T, T> expectAdd(T value, CollectionLinkElement<?, T> after, CollectionLinkElement<?, T> before,
		boolean first, OperationRejection rejection, boolean execute) {
		BiTuple<CollectionLinkElement<?, T>, CollectionLinkElement<?, T>> afterBefore = theSort.get().expectAdd(value, after, before, first,
			rejection);
		if (afterBefore == null)
			return null;

		after = afterBefore.getValue1();
		before = afterBefore.getValue2();

		CollectionLinkElement<?, T> sourceEl = getSourceLink().expectAdd(value, //
			after == null ? null : (CollectionLinkElement<?, T>) after.getFirstSource(),
				before == null ? null : (CollectionLinkElement<?, T>) before.getFirstSource(), //
					first, rejection, execute);
		if (sourceEl == null)
			return null;
		CollectionLinkElement<T, T> element = (CollectionLinkElement<T, T>) sourceEl.getDerivedElements(getSiblingIndex()).getFirst();
		checkOrder(element);
		return element;
	}

	@Override
	public CollectionLinkElement<T, T> expectMove(CollectionLinkElement<?, T> source, CollectionLinkElement<?, T> after,
		CollectionLinkElement<?, T> before, boolean first, OperationRejection rejection, boolean execute) {
		BiTuple<CollectionLinkElement<?, T>, CollectionLinkElement<?, T>> afterBefore = theSort.get().expectMove(source, after, before,
			first, rejection);
		if (afterBefore == null)
			return null;
		after = afterBefore.getValue1();
		before = afterBefore.getValue2();
		CollectionLinkElement<?, T> sourceEl = getSourceLink().expectMove(//
			(CollectionLinkElement<?, T>) source.getFirstSource(), //
			after == null ? null : (CollectionLinkElement<?, T>) after.getFirstSource(),
				before == null ? null : (CollectionLinkElement<?, T>) before.getFirstSource(), //
					first, rejection, execute);
		return sourceEl == null ? null : (CollectionLinkElement<T, T>) sourceEl.getDerivedElements(getSiblingIndex()).getFirst();
	}

	@Override
	public void expect(ExpectedCollectionOperation<?, T> derivedOp, OperationRejection rejection, boolean execute) {
		if (rejection.isRejectable() && !theSort.get().expectSet(derivedOp, rejection, getElements()))
			return;
		CollectionLinkElement<T, T> element = (CollectionLinkElement<T, T>) derivedOp.getElement();
		CollectionLinkElement<?, T> sourceEl = element.getFirstSource();
		getSourceLink().expect(new ExpectedCollectionOperation<>(//
			sourceEl, derivedOp.getType(), derivedOp.getOldValue(), derivedOp.getValue()), rejection, execute);
	}

	@Override
	public void expectFromSource(ExpectedCollectionOperation<?, T> sourceOp) {
		CollectionLinkElement<T, T> element = (CollectionLinkElement<T, T>) sourceOp.getElement().getDerivedElements(getSiblingIndex())
			.getFirst();
		switch (sourceOp.getType()) {
		case add:
			element.expectAdded(sourceOp.getValue());
			checkOrder(element);
			break;
		case remove:
			element.expectRemoval();
			break;
		case set:
			// Order of events can determine whether an element needs to be moved or not,
			// so we'll accommodate if it's moved unexpectedly
			if (element.getFirstSource().getDerivedElements(getSiblingIndex()).size() > 1) {
				// This can happen multiple times in some cases, e.g. because of flattened values and distinctness
				Assert.assertTrue(!element.isPresent());
				if (!element.isRemoveExpected())
					element.expectRemoval();
				element = (CollectionLinkElement<T, T>) sourceOp.getElement().getDerivedElements(getSiblingIndex()).getLast();
				Assert.assertTrue(element.wasAdded());
				if (!element.isAddExpected())
					element.expectAdded(sourceOp.getValue());
				else
					Assert.assertTrue(getCollection().equivalence().elementEquals(sourceOp.getValue(), element.getValue()));
				return;
			} else {
				if (theSort.get().expectMoveFromSource(sourceOp, getSiblingIndex(), getElements()))
					throw new AssertionError("Expected move");
				element.expectSet(sourceOp.getValue());
			}
			break;
		case move:
			throw new IllegalStateException();
		}
	}

	@Override
	protected void validate(CollectionLinkElement<T, T> element, boolean transactionEnd) {
		checkOrder(element);
	}

	private void checkOrder(CollectionLinkElement<T, T> element) {
		theSort.get().checkOrder(getElements(), element);
	}

	@Override
	public String toString() {
		return "sorted(" + theSort.get().getCompare() + ")";
	}

	private static final Map<TestValueType, List<? extends Comparator<?>>> COMPARATORS;

	static {
		COMPARATORS = new HashMap<>();
		for (TestValueType type : TestValueType.values()) {
			switch (type) {
			case INT:
				COMPARATORS.put(type, Arrays.asList(//
					LambdaUtils.printableComparator(Integer::compareTo, () -> "int asc"), //
					LambdaUtils.<Integer> printableComparator((i1, i2) -> -Integer.compare(i1, i2), () -> "int desc"), //
					LambdaUtils.<Integer> printableComparator((i1, i2) -> compareIntAbs(i1, i2), () -> "int abs asc"), //
					LambdaUtils.<Integer> printableComparator((i1, i2) -> -compareIntAbs(i1, i2),
						() -> "int abs desc")//
					));
				break;
			case DOUBLE:
				COMPARATORS.put(type, Arrays.asList(//
					LambdaUtils.printableComparator(Double::compareTo, () -> "double asc"), //
					LambdaUtils.<Double> printableComparator((i1, i2) -> -Double.compare(i1, i2), () -> "double desc"), //
					LambdaUtils.<Double> printableComparator((i1, i2) -> compareDoubleAbs(i1, i2),
						() -> "double abs asc"), //
					LambdaUtils.<Double> printableComparator((i1, i2) -> -compareDoubleAbs(i1, i2),
						() -> "double abs desc")//
					));
				break;
			case STRING:
				COMPARATORS.put(type, Arrays.asList(//
					LambdaUtils.printableComparator(String::compareTo, () -> "string asc"), //
					LambdaUtils.<String> printableComparator((i1, i2) -> -i1.compareTo(i2), () -> "string desc"), //
					LambdaUtils.<String> printableComparator((i1, i2) -> StringUtils.compareNumberTolerant(i1, i2, true, true),
						() -> "string asc(i)"), //
					LambdaUtils.<String> printableComparator((i1, i2) -> -StringUtils.compareNumberTolerant(i1, i2, true, true),
						() -> "string desc(i)")//
					));
				break;
			case BOOLEAN:
				COMPARATORS.put(type, Arrays.asList(//
					LambdaUtils.printableComparator(Boolean::compareTo, () -> "boolean asc"), //
					LambdaUtils.<Boolean> printableComparator((v1, v2) -> -Boolean.compare(v1, v2), () -> "boolean desc")//
					));
				break;
			}
		}
	}

	// These differ from just comparing the absolute values in that if the absolute values are equal but the values themselves aren't,
	// these methods return non-zero. This is actually critical, since i being equivalent to -i can cause issues.
	private static int compareIntAbs(int i1, int i2) {
		boolean i1Neg = i1 < 0;
		if (i1Neg)
			i1 = -i1;
		boolean i2Neg = i2 < 0;
		if (i2Neg)
			i2 = -i2;
		int comp = Integer.compare(i1, i2);
		if (comp == 0 && i1Neg != i2Neg)
			return i1Neg ? -1 : 1;
		return comp;
	}

	private static int compareDoubleAbs(double i1, double i2) {
		boolean i1Neg = i1 < 0;
		if (i1Neg)
			i1 = -i1;
		boolean i2Neg = i2 < 0;
		if (i2Neg)
			i2 = -i2;
		int comp = Double.compare(i1, i2);
		if (comp == 0 && i1Neg != i2Neg)
			return i1Neg ? -1 : 1;
		return comp;
	}

	/**
	 * @param type The type to sort values of
	 * @param helper The randomness to get the sorting with
	 * @return A sorting scheme for the given type
	 */
	public static <E> Comparator<E> compare(TestValueType type, TestHelper helper) {
		List<Comparator<E>> typeCompares = (List<Comparator<E>>) COMPARATORS.get(type);
		return typeCompares.get(helper.getInt(0, typeCompares.size()));
	}
}
