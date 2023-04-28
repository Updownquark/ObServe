package org.observe.supertest.value;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

import org.junit.Assert;
import org.observe.Equivalence;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollectionImpl;
import org.observe.collect.ObservableCollectionImpl.ObservableCollectionFinder;
import org.observe.collect.ObservableElement;
import org.observe.collect.ObservableElementTester;
import org.observe.collect.ObservableSortedSet;
import org.observe.supertest.ChainLinkGenerator;
import org.observe.supertest.CollectionOpType;
import org.observe.supertest.ObservableChainLink;
import org.observe.supertest.OperationRejection;
import org.observe.supertest.TestValueType;
import org.observe.supertest.collect.CollectionDerivedValue;
import org.observe.supertest.collect.CollectionLinkElement;
import org.observe.supertest.collect.ExpectedCollectionOperation;
import org.observe.supertest.collect.FilteredCollectionLink;
import org.observe.supertest.collect.ObservableCollectionLink;
import org.observe.util.TypeTokens;
import org.qommons.Ternian;
import org.qommons.TestHelper;
import org.qommons.collect.BetterSortedList.SortedSearchFilter;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;

import com.google.common.reflect.TypeToken;

/** Container class for many {@link ObservableValueLink} classes derived from {@link ObservableCollectionLink}s */
public class CollectionDerivedValues {
	/** Generates {@link CollectionSize} links to test {@link ObservableCollection#observeSize()} */
	public static final ChainLinkGenerator SIZE_GENERATOR = new ChainLinkGenerator() {
		@Override
		public <T> double getAffinity(ObservableChainLink<?, T> sourceLink, TestValueType targetType) {
			if (!(sourceLink instanceof ObservableCollectionLink))
				return 0;
			else if (targetType != null && targetType != TestValueType.INT)
				return 0;
			return .05;
		}

		@Override
		public <T, X> ObservableChainLink<T, X> deriveLink(String path, ObservableChainLink<?, T> sourceLink, TestValueType targetType,
			TestHelper helper) {
			return (ObservableChainLink<T, X>) new CollectionSize<>(path, (ObservableCollectionLink<?, T>) sourceLink);
		}
	};

	/** Generates {@link CollectionContainsValue} links to test {@link ObservableCollection#observeContains(ObservableValue)} */
	public static final ChainLinkGenerator CONTAINS_VALUE_GENERATOR = new ChainLinkGenerator() {
		@Override
		public <T> double getAffinity(ObservableChainLink<?, T> sourceLink, TestValueType targetType) {
			if (!(sourceLink instanceof ObservableCollectionLink))
				return 0;
			else if (targetType != null && targetType != TestValueType.BOOLEAN)
				return 0;
			else if (((ObservableCollectionLink<?, ?>) sourceLink).getCollection()
				.equivalence() instanceof Equivalence.ComparatorEquivalence) {
				// The test link for sorted collections sometimes switches out the comparator to test the ability of the collection
				// to re-sort itself. Turns out this actually violates the contract of a collection, because its equivalence
				// should never change.
				// It's still a really good way to test that collections can re-sort themselves though.
				// But the mechanism for keeping track of containment here and elsewhere breaks when equivalence changes
				// So until I implement a contract-keeping way of testing that, we can't use this link on top of sorted collections
				return 0;
			}
			ObservableCollectionLink<?, T> sourceCL = (ObservableCollectionLink<?, T>) sourceLink;
			if (sourceCL.getValueSupplier() == null//
				|| !sourceCL.getDef().checkOldValues) // This derived value relies on the old values
				return 0;
			return .05;
		}

		@Override
		public <T, X> ObservableChainLink<T, X> deriveLink(String path, ObservableChainLink<?, T> sourceLink, TestValueType targetType,
			TestHelper helper) {
			return (ObservableChainLink<T, X>) new CollectionContainsValue<>(path, (ObservableCollectionLink<?, T>) sourceLink, helper);
		}
	};

	/**
	 * Generates {@link CollectionContainsValues} links to test {@link ObservableCollection#observeContainsAll(ObservableCollection)} and
	 * {@link ObservableCollection#observeContainsAll(ObservableCollection)}
	 */
	public static final ChainLinkGenerator CONTAINS_VALUES_GENERATOR = new ChainLinkGenerator() {
		@Override
		public <T> double getAffinity(ObservableChainLink<?, T> sourceLink, TestValueType targetType) {
			if (!(sourceLink instanceof ObservableCollectionLink))
				return 0;
			else if (targetType != null && targetType != TestValueType.BOOLEAN)
				return 0;
			else if (((ObservableCollectionLink<?, ?>) sourceLink).getCollection()
				.equivalence() instanceof Equivalence.ComparatorEquivalence) {
				// The test link for sorted collections sometimes switches out the comparator to test the ability of the collection
				// to re-sort itself. Turns out this actually violates the contract of a collection, because its equivalence
				// should never change.
				// It's still a really good way to test that collections can re-sort themselves though.
				// But the mechanism for keeping track of containment here and elsewhere breaks when equivalence changes
				// So until I implement a contract-keeping way of testing that, we can't use this link on top of sorted collections
				return 0;
			}
			ObservableCollectionLink<?, T> sourceCL = (ObservableCollectionLink<?, T>) sourceLink;
			if (sourceCL.getValueSupplier() == null//
				|| !sourceCL.getDef().checkOldValues) // This derived value relies on the old values
				return 0;
			return .05;
		}

		@Override
		public <T, X> ObservableChainLink<T, X> deriveLink(String path, ObservableChainLink<?, T> sourceLink, TestValueType targetType,
			TestHelper helper) {
			ObservableCollectionLink<?, T> sourceCL = (ObservableCollectionLink<?, T>) sourceLink;
			ObservableCollection<T> values = ObservableCollection.build(sourceCL.getCollection().getType()).build();
			boolean containsAny = helper.getBoolean();
			return (ObservableChainLink<T, X>) new CollectionContainsValues<>(path, sourceCL, values, containsAny);
		}
	};

	/** Generates {@link CollectionObserveElement} links to test {@link ObservableCollection#observeElement(Object, boolean)} */
	public static final ChainLinkGenerator OBSERVE_ELEMENT_GENERATOR = new ChainLinkGenerator() {
		@Override
		public <T> double getAffinity(ObservableChainLink<?, T> sourceLink, TestValueType targetType) {
			if (!(sourceLink instanceof ObservableCollectionLink))
				return 0;
			else if (targetType != null && targetType != sourceLink.getType())
				return 0;
			ObservableCollectionLink<?, T> sourceCL = (ObservableCollectionLink<?, T>) sourceLink;
			if (sourceCL.getValueSupplier() == null)
				return 0;
			return .1;
		}

		@Override
		public <T, X> ObservableChainLink<T, X> deriveLink(String path, ObservableChainLink<?, T> sourceLink, TestValueType targetType,
			TestHelper helper) {
			return (ObservableChainLink<T, X>) new CollectionObserveElement<>(path, (ObservableCollectionLink<?, T>) sourceLink, helper);
		}
	};

	/** Generates {@link ObservableCollectionFinder} links to test {@link ObservableCollection#observeFind(java.util.function.Predicate)} */
	public static final ChainLinkGenerator CONDITION_FINDER_GENERATOR = new ChainLinkGenerator() {
		@Override
		public <T> double getAffinity(ObservableChainLink<?, T> sourceLink, TestValueType targetType) {
			if (!(sourceLink instanceof ObservableCollectionLink))
				return 0;
			else if (targetType != null && targetType != sourceLink.getType())
				return 0;
			return .1;
		}

		@Override
		public <T, X> ObservableChainLink<T, X> deriveLink(String path, ObservableChainLink<?, T> sourceLink, TestValueType targetType,
			TestHelper helper) {
			return (ObservableChainLink<T, X>) new CollectionConditionFinder<>(path, (ObservableCollectionLink<?, T>) sourceLink, helper);
		}
	};

	/** Generates {@link CollectionOnlyValue} links to test {@link ObservableCollection#only()} */
	public static final ChainLinkGenerator ONLY_GENERATOR = new ChainLinkGenerator() {
		@Override
		public <T> double getAffinity(ObservableChainLink<?, T> sourceLink, TestValueType targetType) {
			if (!(sourceLink instanceof ObservableCollectionLink))
				return 0;
			else if (targetType != null && targetType != sourceLink.getType())
				return 0;
			return .05;
		}

		@Override
		public <T, X> ObservableChainLink<T, X> deriveLink(String path, ObservableChainLink<?, T> sourceLink, TestValueType targetType,
			TestHelper helper) {
			return (ObservableChainLink<T, X>) new CollectionOnlyValue<>(path, (ObservableCollectionLink<?, T>) sourceLink);
		}
	};

	/** Generates {@link CollectionMinMaxValue} links to test ObservableCollection reduction */
	public static final ChainLinkGenerator MIN_MAX_GENERATOR = new ChainLinkGenerator() {
		@Override
		public <T> double getAffinity(ObservableChainLink<?, T> sourceLink, TestValueType targetType) {
			if (!(sourceLink instanceof ObservableCollectionLink))
				return 0;
			else if (targetType != null && targetType != sourceLink.getType())
				return 0;
			ObservableCollectionLink<?, T> sourceCL = (ObservableCollectionLink<?, T>) sourceLink;
			if (!sourceCL.getDef().checkOldValues) // The reduction relies on the old values being correct
				return 0;
			return .1;
		}

		@Override
		public <T, X> ObservableChainLink<T, X> deriveLink(String path, ObservableChainLink<?, T> sourceLink, TestValueType targetType,
			TestHelper helper) {
			return (ObservableChainLink<T, X>) new CollectionMinMaxValue<>(path, (ObservableCollectionLink<?, T>) sourceLink, helper);
		}
	};

	/** Generates {@link CollectionSum} links to test ObservableCollection reduction */
	public static final ChainLinkGenerator SUM_GENERATOR = new ChainLinkGenerator() {
		@Override
		public <T> double getAffinity(ObservableChainLink<?, T> sourceLink, TestValueType targetType) {
			if (!(sourceLink instanceof ObservableCollectionLink))
				return 0;
			else if (targetType != null)
				return 0; // Since the sum is a type-cheating link, we can't truly satisfy any specified type
			ObservableCollectionLink<?, T> sourceCL = (ObservableCollectionLink<?, T>) sourceLink;
			if (sourceCL.getType() != TestValueType.INT//
				|| !sourceCL.getDef().checkOldValues) // The sum relies on the old values being correct
				return 0;
			return .1;
		}

		@Override
		public <T, X> ObservableChainLink<T, X> deriveLink(String path, ObservableChainLink<?, T> sourceLink, TestValueType targetType,
			TestHelper helper) {
			return (ObservableChainLink<T, X>) new CollectionSum(path, (ObservableCollectionLink<?, Integer>) sourceLink);
		}
	};

	/**
	 * Generates {@link SortedSetObserveRelative} links to test
	 * {@link ObservableSortedSet#observeRelative(Comparable, SortedSearchFilter, java.util.function.Supplier)}
	 */
	public static final ChainLinkGenerator OBSERVE_RELATIVE_GENERATOR = new ChainLinkGenerator() {
		@Override
		public <T> double getAffinity(ObservableChainLink<?, T> sourceLink, TestValueType targetType) {
			if (!(sourceLink instanceof ObservableCollectionLink))
				return 0;
			else if (targetType != null && targetType != sourceLink.getType())
				return 0;
			else if (((ObservableCollectionLink<?, ?>) sourceLink).getValueSupplier() == null)
				return 0;
			else if (((ObservableCollectionLink<?, ?>) sourceLink).getCollection() instanceof ObservableSortedSet)
				return .25;
			else
				return 0;
		}

		@Override
		public <T, X> ObservableChainLink<T, X> deriveLink(String path, ObservableChainLink<?, T> sourceLink, TestValueType targetType,
			TestHelper helper) {
			ObservableCollectionLink<?, T> sourceCL = (ObservableCollectionLink<?, T>) sourceLink;
			T value = sourceCL.getValueSupplier().apply(helper);
			int onExact;
			if (helper.getBoolean())
				onExact = 0;
			else
				onExact = helper.getBoolean() ? -1 : 1;
			SortedSearchFilter filter = SortedSearchFilter.values()[helper.getInt(0, SortedSearchFilter.values().length)];
			return (ObservableChainLink<T, X>) new SortedSetObserveRelative<>(path, sourceCL, sourceCL.getType(), value, onExact, filter);
		}
	};

	/** All generators in this container class */
	public static final List<ChainLinkGenerator> GENERATORS;
	static {
		List<ChainLinkGenerator> generators = new ArrayList<>();
		// These are listed individually so I can easily comment out any of them
		generators.add(SIZE_GENERATOR);
		generators.add(CONTAINS_VALUE_GENERATOR);
		generators.add(CONTAINS_VALUES_GENERATOR);
		generators.add(OBSERVE_ELEMENT_GENERATOR);
		generators.add(CONDITION_FINDER_GENERATOR);
		generators.add(ONLY_GENERATOR);
		generators.add(MIN_MAX_GENERATOR);
		generators.add(SUM_GENERATOR);
		generators.add(OBSERVE_RELATIVE_GENERATOR);
		GENERATORS = Collections.unmodifiableList(generators);
	}

	/**
	 * {@link ObservableCollection#observeSize()} tester
	 *
	 * @param <T> The type of the source collection
	 */
	public static class CollectionSize<T> extends CollectionDerivedValue<T, Integer> {
		CollectionSize(String path, ObservableCollectionLink<?, T> sourceLink) {
			super(path, sourceLink, TestValueType.INT, true);
		}

		@Override
		public boolean isTransactional() {
			return true;
		}

		@Override
		protected ObservableValue<Integer> createValue(TestHelper __) {
			return getSourceLink().getCollection().observeSize();
		}

		@Override
		public void expectFromSource(ExpectedCollectionOperation<?, T> sourceOp) {}

		@Override
		public void validate(boolean transactionEnd) throws AssertionError {
			Assert.assertEquals(getSourceLink().getCollection().size(), getValue().get().intValue());
		}

		@Override
		public void expectSet(Integer value, OperationRejection rejection) {
			throw new IllegalStateException(); // Size is not settable
		}

		@Override
		public String toString() {
			return "size()";
		}
	}

	/**
	 * {@link ObservableCollection#observeContains(ObservableValue)} tester
	 *
	 * @param <T> The type of the source collection
	 */
	public static class CollectionContainsValue<T> extends CollectionDerivedValue<T, Boolean> {
		private final SettableValue<T> theValue;

		CollectionContainsValue(String path, ObservableCollectionLink<?, T> sourceLink, TestHelper helper) {
			super(path, sourceLink, TestValueType.BOOLEAN, true);
			theValue = SettableValue.build((TypeToken<T>) getSourceLink().getType().getType()).build();
			theValue.set(sourceLink.getValueSupplier().apply(helper), null);
		}

		@Override
		protected ObservableValue<Boolean> createValue(TestHelper __) {
			return getSourceLink().getCollection().observeContains(theValue);
		}

		@Override
		public boolean isTransactional() {
			return true;
		}

		@Override
		public double getModificationAffinity() {
			return super.getModificationAffinity() + 1;
		}

		@Override
		public void tryModify(TestHelper.RandomAction action, TestHelper h) throws AssertionError {
			super.tryModify(action, h);
			action.or(1, () -> {
				T newValue = getSourceLink().getValueSupplier().apply(h);
				if (h.isReproducing())
					System.out.println("Value " + theValue.get() + " -> " + newValue);
				theValue.set(getSourceLink().getValueSupplier().apply(h), null);
			});
		}

		@Override
		public void expectFromSource(ExpectedCollectionOperation<?, T> sourceOp) {}

		@Override
		public void validate(boolean transactionEnd) throws AssertionError {
			if (!transactionEnd)
				return; // May not be valid until the transaction end
			CollectionElement<T> found = getSourceLink().getCollection().getElement(theValue.get(), true);
			Assert.assertEquals(found != null, getValue().get().booleanValue());
			if (found != null)
				Assert.assertTrue(getSourceLink().getCollection().equivalence().elementEquals(found.get(), theValue.get()));
		}

		@Override
		public void expectSet(Boolean value, OperationRejection rejection) {
			throw new IllegalStateException(); // Contains is not settable
		}

		@Override
		public String toString() {
			return "contains(" + theValue.get() + ")";
		}
	}

	/**
	 * {@link ObservableCollection#observeContainsAll(ObservableCollection)} and
	 * {@link ObservableCollection#observeContainsAny(ObservableCollection)} tester
	 *
	 * @param <T> The type of the source collection
	 */
	public static class CollectionContainsValues<T> extends CollectionDerivedValue<T, Boolean> {
		private final ObservableCollection<T> theSearchValues;
		private final boolean isContainsAny;

		CollectionContainsValues(String path, ObservableCollectionLink<?, T> sourceLink, ObservableCollection<T> searchValues,
			boolean containsAny) {
			super(path, sourceLink, TestValueType.BOOLEAN, true);
			theSearchValues = searchValues;
			isContainsAny = containsAny;
		}

		@Override
		public boolean isTransactional() {
			return true;
		}

		@Override
		protected ObservableValue<Boolean> createValue(TestHelper h) {
			if (isContainsAny)
				return getSourceLink().getCollection().observeContainsAny(theSearchValues);
			else
				return getSourceLink().getCollection().observeContainsAll(theSearchValues);
		}

		@Override
		public double getModificationAffinity() {
			return super.getModificationAffinity() + 6;
		}

		@Override
		public void tryModify(TestHelper.RandomAction action, TestHelper h) throws AssertionError {
			super.tryModify(action, h);
			action.or(1, () -> { // Add a value
				T newValue = getSourceLink().getValueSupplier().apply(h);
				if (h.isReproducing())
					System.out.println("Add search value " + newValue);
				theSearchValues.add(newValue);
			}).or(.5, () -> { // Add multiple values
				int size = h.getInt(0, 10);
				List<T> newValues = new ArrayList<>(size);
				for (int i = 0; i < size; i++)
					newValues.add(getSourceLink().getValueSupplier().apply(h));
				if (h.isReproducing())
					System.out.println("Add search values " + newValues);
				theSearchValues.addAll(newValues);
			});
			if (!theSearchValues.isEmpty()) {
				action.or(1, () -> { // Remove a value
					int index = h.getInt(0, theSearchValues.size());
					if (h.isReproducing())
						System.out.println("Remove search value [" + index + "]" + theSearchValues.get(index));
					theSearchValues.remove(index);
				}).or(.5, () -> { // Remove a range
					int index1 = h.getInt(0, theSearchValues.size());
					int index2 = h.getInt(0, theSearchValues.size());
					if (index1 > index2) {
						int temp = index2;
						index2 = index1;
						index1 = temp;
					}
					if (h.isReproducing())
						System.out.println("Remove search values [" + index1 + ".." + index2 + "]");
					theSearchValues.removeRange(index1, index2);
				});
			}
		}

		@Override
		public void expectFromSource(ExpectedCollectionOperation<?, T> sourceOp) {}

		@Override
		public void validate(boolean transactionEnd) throws AssertionError {
			boolean expect;
			if (isContainsAny)
				expect = getSourceLink().getCollection().containsAny(theSearchValues);
			else
				expect = getSourceLink().getCollection().containsAll(theSearchValues);
			Assert.assertEquals(expect, getValue().get().booleanValue());
			boolean actual;
			if (isContainsAny) {
				actual = false;
				for (T value : theSearchValues) {
					if (getSourceLink().getCollection().contains(value)) {
						actual = true;
						break;
					}
				}
			} else {
				actual = true;
				for (T value : theSearchValues) {
					if (!getSourceLink().getCollection().contains(value)) {
						actual = false;
						break;
					}
				}
			}
			Assert.assertEquals(actual, expect);
			if (transactionEnd)
				getTester().check(expect);
		}

		@Override
		public void expectSet(Boolean value, OperationRejection rejection) {
			throw new IllegalStateException(); // Contains is not settable
		}

		@Override
		public String toString() {
			return "contains" + (isContainsAny ? "Any" : "All") + "(" + theSearchValues + ")";
		}
	}

	/**
	 * {@link ObservableCollection#observeElement(Object, boolean)} tester
	 *
	 * @param <T> The type of the source collection
	 */
	public static class CollectionObserveElement<T> extends CollectionDerivedValue<T, T> {
		private final T theValue;
		private final boolean isFirst;

		CollectionObserveElement(String path, ObservableCollectionLink<?, T> sourceLink, TestHelper helper) {
			super(path, sourceLink, sourceLink.getType(), true);
			theValue = sourceLink.getValueSupplier().apply(helper);
			isFirst = helper.getBoolean();
		}

		@Override
		public boolean isTransactional() {
			return true;
		}

		@Override
		public ObservableElement<T> getValue() {
			return (ObservableElement<T>) super.getValue();
		}

		@Override
		public ObservableElementTester<T> getTester() {
			return (ObservableElementTester<T>) super.getTester();
		}

		@Override
		protected ObservableValue<T> createValue(TestHelper h) {
			return getSourceLink().getCollection().observeElement(theValue, isFirst);
		}

		@Override
		public void expectFromSource(ExpectedCollectionOperation<?, T> sourceOp) {}

		@Override
		public void validate(boolean transactionEnd) throws AssertionError {
			// This derived value is only consistent when the transaction ends
			if (transactionEnd)
				getTester().checkSynced();

			CollectionElement<T> found;
			if (isFirst) {
				found = null;
				for (CollectionElement<T> el : getSourceLink().getCollection().elements()) {
					if (getSourceLink().getCollection().equivalence().elementEquals(el.get(), theValue)) {
						found = el;
						break;
					}
				}
				if (found == null)
					Assert.assertNull(getValue().getElementId());
				else
					Assert.assertEquals(found.getElementId(), getValue().getElementId());
			} else {
				found = null;
				for (CollectionElement<T> el : getSourceLink().getCollection().elements().reverse()) {
					if (getSourceLink().getCollection().equivalence().elementEquals(el.get(), theValue)) {
						found = el;
						break;
					}
				}
				if (found == null)
					Assert.assertNull(getValue().getElementId());
				else
					Assert.assertEquals(found.getElementId(), getValue().getElementId());
			}
		}

		@Override
		public void expectSet(T value, OperationRejection rejection) {
			throw new IllegalStateException(); // observeElement is not settable
		}

		@Override
		public String toString() {
			return "observeElement(" + theValue + ")";
		}
	}

	/**
	 * {@link ObservableCollection#observeFind(java.util.function.Predicate)} tester
	 *
	 * @param <T> The type of the source collection
	 */
	public static class CollectionConditionFinder<T> extends CollectionDerivedValue<T, T> {
		private final SettableValue<Function<T, String>> theConditionValue;
		private final Ternian theLocation;
		private ElementId theElement;
		private boolean isRefreshed;

		CollectionConditionFinder(String path, ObservableCollectionLink<?, T> sourceLink, TestHelper helper) {
			super(path, sourceLink, sourceLink.getType(), true);
			theConditionValue = SettableValue.build((TypeToken<Function<T, String>>) (TypeToken<?>) TypeTokens.get().OBJECT)
				.build();
			theConditionValue.set(FilteredCollectionLink.filterFor(getType(), helper), null);
			theLocation = Ternian.values()[helper.getInt(0, 3)];
		}

		@Override
		public boolean isTransactional() {
			return true;
		}

		@Override
		public ObservableElement<T> getValue() {
			return (ObservableElement<T>) super.getValue();
		}

		@Override
		public ObservableElementTester<T> getTester() {
			return (ObservableElementTester<T>) super.getTester();
		}

		@Override
		protected ObservableValue<T> createValue(TestHelper h) {
			return getSourceLink().getCollection().observeFind(v -> theConditionValue.get().apply(v) == null).at(theLocation)
				.refresh(theConditionValue.noInitChanges(), theConditionValue::getStamp)//
				.find();
		}

		@Override
		public double getModificationAffinity() {
			return super.getModificationAffinity() + 1;
		}

		@Override
		public void tryModify(TestHelper.RandomAction action, TestHelper h) throws AssertionError {
			super.tryModify(action, h);
			action.or(1, () -> {
				Function<T, String> newFilter = FilteredCollectionLink.filterFor(getType(), h);
				if (h.isReproducing())
					System.out.println("Condition " + theConditionValue.get() + " -> " + newFilter);
				isRefreshed = true;
				theConditionValue.set(newFilter, null);
			});
		}

		@Override
		public void expectFromSource(ExpectedCollectionOperation<?, T> sourceOp) {}

		@Override
		public void validate(boolean transactionEnd) throws AssertionError {
			if (transactionEnd)
				isRefreshed = false;
			else if (isRefreshed) {
				theElement = getValue().getElementId();
				return; // After a refresh, this value won't be valid until the transaction ends
			}
			// This derived value is only consistent when the transaction ends, and if there are multiple passing values,
			// the specific result found is not consistent with a Ternian.NONE location
			if (transactionEnd && theLocation != Ternian.NONE)
				getTester().checkSynced();
			CollectionElement<T> found;
			switch (theLocation) {
			case TRUE:
				found = null;
				for (CollectionElement<T> el : getSourceLink().getCollection().elements()) {
					if (theConditionValue.get().apply(el.get()) == null) {
						found = el;
						break;
					}
				}
				if (found == null)
					Assert.assertNull(getValue().getElementId());
				else
					Assert.assertEquals(found.getElementId(), getValue().getElementId());
				break;
			case FALSE:
				found = null;
				for (CollectionElement<T> el : getSourceLink().getCollection().elements().reverse()) {
					if (theConditionValue.get().apply(el.get()) == null) {
						found = el;
						break;
					}
				}
				if (found == null)
					Assert.assertNull(getValue().getElementId());
				else
					Assert.assertEquals(found.getElementId(), getValue().getElementId());
				break;
			case NONE:
				if (getValue().getElementId() == null) {
					for (T v : getSourceLink().getCollection())
						Assert.assertNotNull(theConditionValue.get().apply(v));
				} else {
					Assert.assertNull(
						theConditionValue.get().apply(getSourceLink().getCollection().getElement(getValue().getElementId()).get()));
					Assert.assertNull(theConditionValue.get().apply(getValue().get()));
				}
				break;
			}
			theElement = getValue().getElementId();
		}

		@Override
		public void expectSet(T value, OperationRejection rejection) {
			String msg = theConditionValue.get().apply(value);
			if (msg != null) {
				rejection.reject(msg);
				return;
			}
			CollectionLinkElement<?, T> found = null;
			switch (theLocation) {
			case TRUE:
				for (CollectionLinkElement<?, T> el : getSourceLink().getElements()) {
					if (!el.wasAdded() && theConditionValue.get().apply(el.getValue()) == null) {
						found = el;
						break;
					}
				}
				break;
			case FALSE:
				for (CollectionLinkElement<?, T> el : getSourceLink().getElements().reverse()) {
					if (!el.wasAdded() && theConditionValue.get().apply(el.getValue()) == null) {
						found = el;
						break;
					}
				}
				break;
			case NONE:
				if (theElement != null)
					found = getSourceLink().getElement(theElement);
				else
					found = null;
				break;
			}

			if (found != null) {
				ExpectedCollectionOperation<?, T> op = new ExpectedCollectionOperation<>(found, CollectionOpType.set, found.getValue(),
					value);
				getSourceLink().expect(op, rejection, false);
				if (rejection.isRejected()) {
					rejection.reset();
					getSourceLink().expectAdd(value, //
						theLocation == Ternian.FALSE ? found : null, //
							theLocation == Ternian.TRUE ? found : null, //
								true, rejection, true);
				} else
					getSourceLink().expect(op, rejection.unrejectable(), true);
			} else
				getSourceLink().expectAdd(value, null, null, true, rejection, true);
		}

		@Override
		public String toString() {
			String str = "find(" + theConditionValue.get() + ", ";
			switch (theLocation) {
			case TRUE:
				str += "first";
				break;
			case FALSE:
				str += "last";
				break;
			case NONE:
				str += "anywhere";
				break;
			}
			return str + ")";
		}
	}

	/**
	 * {@link ObservableCollection#only()} tester
	 *
	 * @param <T> The type of the source collection
	 */
	public static class CollectionOnlyValue<T> extends CollectionDerivedValue<T, T> {
		CollectionOnlyValue(String path, ObservableCollectionLink<?, T> sourceLink) {
			super(path, sourceLink, sourceLink.getType(), true);
		}

		@Override
		protected ObservableValue<T> createValue(TestHelper __) {
			return getSourceLink().getCollection().only();
		}

		@Override
		public void expectFromSource(ExpectedCollectionOperation<?, T> sourceOp) {}

		@Override
		public void validate(boolean transactionEnd) throws AssertionError {
			if (!transactionEnd)
				return; // Only is only consistent after the transaction ends
			boolean only = getSourceLink().getCollection().size() == 1;
			Assert.assertEquals(only, getValue().get() != null);
			if (only)
				Assert.assertTrue(
					getSourceLink().getCollection().equivalence().elementEquals(getSourceLink().getCollection().get(0), getValue().get()));
		}

		@Override
		public void expectSet(T value, OperationRejection rejection) {
			if(getSourceLink().getCollection().size()==1){
				CollectionLinkElement<?, T> element=getSourceLink().getElements().getFirst();
				getSourceLink().expect(new ExpectedCollectionOperation<>(element, CollectionOpType.set, element.getValue(), value),
					rejection, true);
			} else
				rejection.reject(ObservableCollectionImpl.OnlyElement.COLL_SIZE_NOT_1);
		}

		@Override
		public String toString() {
			return "only()";
		}
	}

	/**
	 * Reduction tester, using {@link ObservableCollection#minBy(Comparator, java.util.function.Supplier, Ternian)} and
	 * {@link ObservableCollection#maxBy(Comparator, java.util.function.Supplier, Ternian)}
	 *
	 * @param <T> The type of the source collection
	 */
	public static class CollectionMinMaxValue<T> extends CollectionDerivedValue<T, T> {
		private final boolean isMin;
		private final Ternian theLocation;
		private final Comparator<T> theCompare;

		CollectionMinMaxValue(String path, ObservableCollectionLink<?, T> sourceLink, TestHelper helper) {
			super(path, sourceLink, sourceLink.getType(), true);
			isMin = helper.getBoolean();
			theLocation = Ternian.values()[helper.getInt(0, 3)];
			switch (getSourceLink().getType()) { // Reductions
			case INT:
				theCompare = (Comparator<T>) (Comparator<Integer>) Integer::compare;
				break;
			case DOUBLE:
				theCompare = (Comparator<T>) (Comparator<Double>) Double::compare;
				break;
			case STRING:
				theCompare = (Comparator<T>) (Comparator<String>) String::compareTo;
				break;
			case BOOLEAN:
				theCompare = (Comparator<T>) (Comparator<Boolean>) Boolean::compare;
				break;
			default:
				throw new IllegalStateException();
			}
		}

		@Override
		public boolean isTransactional() {
			return true;
		}

		@Override
		public ObservableElement<T> getValue() {
			return (ObservableElement<T>) super.getValue();
		}

		@Override
		public ObservableElementTester<T> getTester() {
			return (ObservableElementTester<T>) super.getTester();
		}

		@Override
		protected ObservableValue<T> createValue(TestHelper __) {
			if (isMin)
				return getSourceLink().getCollection().minBy(theCompare, null, theLocation);
			else
				return getSourceLink().getCollection().maxBy(theCompare, null, theLocation);
		}

		@Override
		public void expectFromSource(ExpectedCollectionOperation<?, T> sourceOp) {}

		@Override
		public void validate(boolean transactionEnd) throws AssertionError {
			// This derived value is only consistent when the transaction ends, and if there are multiple equivalent max/min values,
			// the specific result found is not consistent with a Ternian.NONE location
			if (transactionEnd && theLocation != Ternian.NONE)
				getTester().checkSynced();
			CollectionElement<T> element = null;
			switch (theLocation) {
			case TRUE:
				for (CollectionElement<T> el : getSourceLink().getCollection().elements()) {
					if (element == null)
						element = el;
					else {
						int comp = theCompare.compare(el.get(), element.get());
						if (comp != 0 && (comp < 0) == isMin)
							element = el;
					}
				}
				if (element == null)
					Assert.assertNull(getValue().getElementId());
				else
					Assert.assertEquals(element.getElementId(), getValue().getElementId());
				break;
			case FALSE:
				for (CollectionElement<T> el : getSourceLink().getCollection().elements().reverse()) {
					if (element == null)
						element = el;
					else {
						int comp = theCompare.compare(el.get(), element.get());
						if (comp != 0 && (comp < 0) == isMin)
							element = el;
					}
				}
				if (element == null)
					Assert.assertNull(getValue().getElementId());
				else
					Assert.assertEquals(element.getElementId(), getValue().getElementId());
				break;
			case NONE:
				ElementId el = getValue().getElementId();
				if (el == null)
					Assert.assertTrue(getSourceLink().getCollection().isEmpty());
				else {
					T v = getValue().get();
					Assert.assertTrue(getSourceLink().getCollection().equivalence()
						.elementEquals(getSourceLink().getCollection().getElement(el).get(), v));
					for (CollectionElement<T> e : getSourceLink().getCollection().elements()) {
						int comp = theCompare.compare(v, e.get());
						if (comp == 0) {//
						} else if (isMin != (comp < 0))
							throw new AssertionError(e + " " + (comp < 0 ? ">" : "<") + v);
					}
				}
				break;
			}
		}

		@Override
		public void expectSet(T value, OperationRejection rejection) {
			throw new IllegalStateException(); // Reduction is not settable
		}

		@Override
		public String toString() {
			return (isMin ? "min" : "max") + "()";
		}
	}

	/** Reduction (sum) tester for int collections */
	public static class CollectionSum extends CollectionDerivedValue<Integer, Long> {
		CollectionSum(String path, ObservableCollectionLink<?, Integer> sourceLink) {
			super(path, sourceLink, TestValueType.INT, true);
		}

		@Override
		public boolean isTransactional() {
			return true;
		}

		@Override
		public boolean isTypeCheat() {
			return true;
		}

		@Override
		protected ObservableValue<Long> createValue(TestHelper h) {
			return getSourceLink().getCollection().reduce(0L, (sum, v) -> sum + v, (sum, v) -> sum - v);
		}

		@Override
		public void expectFromSource(ExpectedCollectionOperation<?, Integer> sourceOp) {}

		@Override
		public void validate(boolean transactionEnd) throws AssertionError {
			long sum = 0;
			for (Integer v : getSourceLink().getCollection())
				sum += v;
			if (transactionEnd)
				getTester().check(sum);
			else
				Assert.assertEquals(sum, getValue().get().longValue());
		}

		@Override
		public void expectSet(Long value, OperationRejection rejection) {
			throw new IllegalStateException(); // Reduction is not settable
		}

		@Override
		public String toString() {
			return "sum()";
		}
	}

	/**
	 * {@link ObservableSortedSet#observeRelative(Comparable, SortedSearchFilter, java.util.function.Supplier)} tester
	 *
	 * @param <T> The type of the source set
	 */
	public static class SortedSetObserveRelative<T> extends CollectionDerivedValue<T, T> {
		private final T theValue;
		private final int onExact;
		private final Comparable<? super T> theSearch;
		private final SortedSearchFilter theFilter;

		SortedSetObserveRelative(String path, ObservableCollectionLink<?, T> sourceLink, TestValueType type, T value, int onExact,
			SortedSearchFilter filter) {
			super(path, sourceLink, type, true);
			theValue = value;
			this.onExact = onExact;
			theFilter = filter;
			theSearch = ((ObservableSortedSet<T>) getSourceLink().getCollection()).searchFor(theValue, onExact);
		}

		@Override
		public boolean isTransactional() {
			return true;
		}

		@Override
		public ObservableElement<T> getValue() {
			return (ObservableElement<T>) super.getValue();
		}

		@Override
		public ObservableElementTester<T> getTester() {
			return (ObservableElementTester<T>) super.getTester();
		}

		@Override
		protected ObservableValue<T> createValue(TestHelper h) {
			return ((ObservableSortedSet<T>) getSourceLink().getCollection()).observeRelative(theSearch, theFilter, null);
		}

		@Override
		public void expectFromSource(ExpectedCollectionOperation<?, T> sourceOp) {}

		@Override
		public void validate(boolean transactionEnd) throws AssertionError {
			ObservableSortedSet<T> set = (ObservableSortedSet<T>) getSourceLink().getCollection();
			CollectionElement<T> result = set.search(theSearch, theFilter);
			int compare = result == null ? 0 : set.comparator().compare(result.get(), theValue);
			switch (theFilter) {
			case OnlyMatch:
				if (result == null) {
					if (onExact == 0)
						Assert.assertFalse(set.contains(theValue));
				} else if (compare == 0)
					Assert.assertEquals(0, onExact);
				else
					throw new AssertionError("Should not have found a non-matching value");
				break;
			case Less:
				if (result == null) {
					if (!set.isEmpty()) {
						int firstComp = set.comparator().compare(set.getFirst(), theValue);
						if (firstComp < 0 || (onExact == 0 && firstComp == 0))
							throw new AssertionError("Should have found " + set.getFirst());
					}
				} else if (compare == 0)
					Assert.assertTrue(onExact >= 0); // onExact>0 means that the search treats equal values as greater
				else if (compare > 0)
					throw new AssertionError("Should not have returned a greater result");
				break;
			case Greater:
				if (result == null) {
					if (!set.isEmpty()) {
						int lastComp = set.comparator().compare(set.getLast(), theValue);
						if (lastComp > 0 || (onExact == 0 && lastComp == 0))
							throw new AssertionError("Should have found " + set.getLast());
					}
				} else if (compare == 0)
					Assert.assertTrue(onExact <= 0); // onExact<0 means that the search treats equal values as less
				else if (compare < 0)
					throw new AssertionError("Should not have returned a lesser result");
				break;
			case PreferLess:
				if (result == null)
					Assert.assertTrue(set.isEmpty());
				else if (compare == 0 && onExact >= 0) {// onExact>0 means that the search treats equal values as greater
				} else if (compare >= 0)
					Assert.assertNull(set.getAdjacentElement(result.getElementId(), false));
				else {
					CollectionElement<T> adj = set.getAdjacentElement(result.getElementId(), true);
					if (adj != null) {
						int adjComp = set.comparator().compare(adj.get(), theValue);
						if (adjComp < 0 || (adjComp == 0 && onExact == 0))
							throw new AssertionError("Should have found " + adj + " instead of " + result);
					}
				}
				break;
			case PreferGreater:
				if (result == null)
					Assert.assertTrue(set.isEmpty());
				else if (compare == 0 && onExact <= 0) { // onExact<0 means that the search treats equal values as less
				} else if (compare < 0)
					Assert.assertNull(set.getAdjacentElement(result.getElementId(), true));
				else {
					CollectionElement<T> adj = set.getAdjacentElement(result.getElementId(), false);
					if (adj != null) {
						int adjComp = set.comparator().compare(adj.get(), theValue);
						if (adjComp > 0 || (adjComp == 0 && onExact == 0))
							throw new AssertionError("Should have found " + adj + " instead of " + result);
					}
				}
				break;
			}

			switch (theFilter) {
			case Less:
				if (onExact < 0)
					Assert.assertEquals(CollectionElement.get(result), set.lower(theValue));
				else if (onExact == 0)
					Assert.assertEquals(CollectionElement.get(result), set.floor(theValue));
				break;
			case Greater:
				if (onExact > 0)
					Assert.assertEquals(CollectionElement.get(result), set.higher(theValue));
				else if (onExact == 0)
					Assert.assertEquals(CollectionElement.get(result), set.ceiling(theValue));
				break;
			default:
				break;
			}

			Assert.assertEquals(result == null ? null : result.getElementId(), getValue().getElementId());
			Assert.assertEquals(result == null ? null : result.get(), getValue().get());
			// This derived value is only consistent when the transaction ends
			if (transactionEnd)
				getTester().checkSynced();
		}

		@Override
		public void expectSet(T value, OperationRejection rejection) {
			throw new IllegalStateException(); // observeRelative is not settable
		}

		@Override
		public String toString() {
			return "observeRelative(" + theSearch + ", " + theFilter + ")";
		}
	}
}
