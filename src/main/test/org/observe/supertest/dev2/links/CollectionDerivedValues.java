package org.observe.supertest.dev2.links;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

import org.junit.Assert;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.collect.ObservableElement;
import org.observe.collect.ObservableElementTester;
import org.observe.collect.ObservableSortedSet;
import org.observe.supertest.dev2.ChainLinkGenerator;
import org.observe.supertest.dev2.CollectionDerivedValue;
import org.observe.supertest.dev2.ExpectedCollectionOperation;
import org.observe.supertest.dev2.ObservableChainLink;
import org.observe.supertest.dev2.ObservableCollectionLink;
import org.observe.supertest.dev2.TestValueType;
import org.observe.util.TypeTokens;
import org.qommons.Ternian;
import org.qommons.TestHelper;
import org.qommons.collect.BetterSortedList.SortedSearchFilter;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;

import com.google.common.reflect.TypeToken;

public class CollectionDerivedValues {
	public static final ChainLinkGenerator SIZE_GENERATOR = new ChainLinkGenerator() {
		@Override
		public <T> double getAffinity(ObservableChainLink<?, T> sourceLink) {
			if (!(sourceLink instanceof ObservableCollectionLink))
				return 0;
			return .05;
		}

		@Override
		public <T, X> ObservableChainLink<T, X> deriveLink(String path, ObservableChainLink<?, T> sourceLink, TestHelper helper) {
			return (ObservableChainLink<T, X>) new CollectionSize<>(path, (ObservableCollectionLink<?, T>) sourceLink);
		}
	};

	public static final ChainLinkGenerator CONTAINS_VALUE_GENERATOR = new ChainLinkGenerator() {
		@Override
		public <T> double getAffinity(ObservableChainLink<?, T> sourceLink) {
			if (!(sourceLink instanceof ObservableCollectionLink))
				return 0;
			ObservableCollectionLink<?, T> sourceCL = (ObservableCollectionLink<?, T>) sourceLink;
			if (sourceCL.getValueSupplier() == null)
				return 0;
			return .05;
		}

		@Override
		public <T, X> ObservableChainLink<T, X> deriveLink(String path, ObservableChainLink<?, T> sourceLink, TestHelper helper) {
			return (ObservableChainLink<T, X>) new CollectionContainsValue<>(path, (ObservableCollectionLink<?, T>) sourceLink, helper);
		}
	};

	public static final ChainLinkGenerator OBSERVE_ELEMENT_GENERATOR = new ChainLinkGenerator() {
		@Override
		public <T> double getAffinity(ObservableChainLink<?, T> sourceLink) {
			if (!(sourceLink instanceof ObservableCollectionLink))
				return 0;
			ObservableCollectionLink<?, T> sourceCL = (ObservableCollectionLink<?, T>) sourceLink;
			if (sourceCL.getValueSupplier() == null)
				return 0;
			return .1;
		}

		@Override
		public <T, X> ObservableChainLink<T, X> deriveLink(String path, ObservableChainLink<?, T> sourceLink, TestHelper helper) {
			return (ObservableChainLink<T, X>) new CollectionObserveElement<>(path, (ObservableCollectionLink<?, T>) sourceLink, helper);
		}
	};

	public static final ChainLinkGenerator CONDITION_FINDER_GENERATOR = new ChainLinkGenerator() {
		@Override
		public <T> double getAffinity(ObservableChainLink<?, T> sourceLink) {
			if (!(sourceLink instanceof ObservableCollectionLink))
				return 0;
			return .1;
		}

		@Override
		public <T, X> ObservableChainLink<T, X> deriveLink(String path, ObservableChainLink<?, T> sourceLink, TestHelper helper) {
			return (ObservableChainLink<T, X>) new CollectionConditionFinder<>(path, (ObservableCollectionLink<?, T>) sourceLink, helper);
		}
	};

	public static final ChainLinkGenerator ONLY_GENERATOR = new ChainLinkGenerator() {
		@Override
		public <T> double getAffinity(ObservableChainLink<?, T> sourceLink) {
			if (!(sourceLink instanceof ObservableCollectionLink))
				return 0;
			return .05;
		}

		@Override
		public <T, X> ObservableChainLink<T, X> deriveLink(String path, ObservableChainLink<?, T> sourceLink, TestHelper helper) {
			return (ObservableChainLink<T, X>) new CollectionOnlyValue<>(path, (ObservableCollectionLink<?, T>) sourceLink);
		}
	};

	public static final ChainLinkGenerator MIN_MAX_GENERATOR = new ChainLinkGenerator() {
		@Override
		public <T> double getAffinity(ObservableChainLink<?, T> sourceLink) {
			if (!(sourceLink instanceof ObservableCollectionLink))
				return 0;
			return .1;
		}

		@Override
		public <T, X> ObservableChainLink<T, X> deriveLink(String path, ObservableChainLink<?, T> sourceLink, TestHelper helper) {
			return (ObservableChainLink<T, X>) new CollectionMinMaxValue<>(path, (ObservableCollectionLink<?, T>) sourceLink, helper);
		}
	};

	public static final ChainLinkGenerator SUM_GENERATOR = new ChainLinkGenerator() {
		@Override
		public <T> double getAffinity(ObservableChainLink<?, T> sourceLink) {
			if (!(sourceLink instanceof ObservableCollectionLink))
				return 0;
			ObservableCollectionLink<?, T> sourceCL = (ObservableCollectionLink<?, T>) sourceLink;
			if (sourceCL.getType() != TestValueType.INT)
				return 0;
			return .1;
		}

		@Override
		public <T, X> ObservableChainLink<T, X> deriveLink(String path, ObservableChainLink<?, T> sourceLink, TestHelper helper) {
			return (ObservableChainLink<T, X>) new CollectionSum(path, (ObservableCollectionLink<?, Integer>) sourceLink);
		}
	};

	public static final ChainLinkGenerator OBSERVE_RELATIVE_GENERATOR = new ChainLinkGenerator() {
		@Override
		public <T> double getAffinity(ObservableChainLink<?, T> sourceLink) {
			if (!(sourceLink instanceof ObservableCollectionLink))
				return 0;
			else if (((ObservableCollectionLink<?, ?>) sourceLink).getValueSupplier() == null)
				return 0;
			else if (((ObservableCollectionLink<?, ?>) sourceLink).getCollection() instanceof ObservableSortedSet)
				return .25;
			else
				return 0;
		}

		@Override
		public <T, X> ObservableChainLink<T, X> deriveLink(String path, ObservableChainLink<?, T> sourceLink, TestHelper helper) {
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

	public static final List<ChainLinkGenerator> GENERATORS;
	static {
		List<ChainLinkGenerator> generators = new ArrayList<>();
		// These are listed individually so I can easily comment out any of them
		generators.add(SIZE_GENERATOR);
		generators.add(CONTAINS_VALUE_GENERATOR);
		generators.add(OBSERVE_ELEMENT_GENERATOR);
		generators.add(CONDITION_FINDER_GENERATOR);
		generators.add(ONLY_GENERATOR);
		generators.add(MIN_MAX_GENERATOR);
		generators.add(SUM_GENERATOR);
		// generators.add(OBSERVE_ELEMENT_GENERATOR);
		GENERATORS = Collections.unmodifiableList(generators);
	}

	public static class CollectionSize<T> extends CollectionDerivedValue<T, Integer> {
		CollectionSize(String path, ObservableCollectionLink<?, T> sourceLink) {
			super(path, sourceLink, TestValueType.INT);
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
		public String toString() {
			return "size()";
		}
	}

	public static class CollectionContainsValue<T> extends CollectionDerivedValue<T, Boolean> {
		private final SettableValue<T> theValue;

		CollectionContainsValue(String path, ObservableCollectionLink<?, T> sourceLink, TestHelper helper) {
			super(path, sourceLink, TestValueType.BOOLEAN);
			theValue = SettableValue.build((TypeToken<T>) getSourceLink().getType().getType()).safe(false).build();
			theValue.set(sourceLink.getValueSupplier().apply(helper), null);
		}

		@Override
		protected ObservableValue<Boolean> createValue(TestHelper __) {
			return getSourceLink().getCollection().observeContains(theValue);
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
			CollectionElement<T> found = getSourceLink().getCollection().getElement(theValue.get(), true);
			Assert.assertEquals(found != null, getValue().get().booleanValue());
			if (found != null)
				Assert.assertTrue(getSourceLink().getCollection().equivalence().elementEquals(found.get(), theValue.get()));
		}

		@Override
		public String toString() {
			return "contains(" + theValue.get() + ")";
		}
	}

	public static class CollectionObserveElement<T> extends CollectionDerivedValue<T, T> {
		private final T theValue;
		private final boolean isFirst;

		CollectionObserveElement(String path, ObservableCollectionLink<?, T> sourceLink, TestHelper helper) {
			super(path, sourceLink, sourceLink.getType());
			theValue = sourceLink.getValueSupplier().apply(helper);
			isFirst = helper.getBoolean();
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
		public String toString() {
			return "observeElement(" + theValue + ")";
		}
	}

	public static class CollectionConditionFinder<T> extends CollectionDerivedValue<T, T> {
		private final SettableValue<Function<T, String>> theConditionValue;
		private final Ternian theLocation;

		CollectionConditionFinder(String path, ObservableCollectionLink<?, T> sourceLink, TestHelper helper) {
			super(path, sourceLink, sourceLink.getType());
			theConditionValue = SettableValue.build((TypeToken<Function<T, String>>) (TypeToken<?>) TypeTokens.get().OBJECT).safe(false)
				.build();
			theConditionValue.set(FilteredCollectionLink.filterFor(getType(), helper), null);
			theLocation = Ternian.values()[helper.getInt(0, 3)];
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
				.refresh(theConditionValue.noInitChanges())//
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
				theConditionValue.set(newFilter, null);
			});
		}

		@Override
		public void expectFromSource(ExpectedCollectionOperation<?, T> sourceOp) {}

		@Override
		public void validate(boolean transactionEnd) throws AssertionError {
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
		}

		@Override
		public String toString() {
			return "find(" + theConditionValue.get() + ")";
		}
	}

	public static class CollectionOnlyValue<T> extends CollectionDerivedValue<T, T> {
		CollectionOnlyValue(String path, ObservableCollectionLink<?, T> sourceLink) {
			super(path, sourceLink, TestValueType.INT);
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
		public String toString() {
			return "only()";
		}
	}

	public static class CollectionMinMaxValue<T> extends CollectionDerivedValue<T, T> {
		private final boolean isMin;
		private final Ternian theLocation;
		private final Comparator<T> theCompare;

		CollectionMinMaxValue(String path, ObservableCollectionLink<?, T> sourceLink, TestHelper helper) {
			super(path, sourceLink, TestValueType.INT);
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
		public String toString() {
			return (isMin ? "min" : "max") + "()";
		}
	}

	public static class CollectionSum extends CollectionDerivedValue<Integer, Long> {
		CollectionSum(String path, ObservableCollectionLink<?, Integer> sourceLink) {
			super(path, sourceLink, TestValueType.INT);
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
		public String toString() {
			return "sum()";
		}
	}

	public static class SortedSetObserveRelative<T> extends CollectionDerivedValue<T, T> {
		private final T theValue;
		private final int onExact;
		private final Comparable<? super T> theSearch;
		private final SortedSearchFilter theFilter;

		public SortedSetObserveRelative(String path, ObservableCollectionLink<?, T> sourceLink, TestValueType type, T value, int onExact,
			SortedSearchFilter filter) {
			super(path, sourceLink, type);
			theValue = value;
			this.onExact = onExact;
			theFilter = filter;
			theSearch = ((ObservableSortedSet<T>) getSourceLink().getCollection()).searchFor(theValue, onExact);
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
			// This derived value is only consistent when the transaction ends
			if (transactionEnd)
				getTester().checkSynced();

			ObservableSortedSet<T> set=(ObservableSortedSet<T>) getSourceLink().getCollection();
			CollectionElement<T> result=set.search(theSearch, theFilter);
			getTester().check(result==null ? null : result.getElementId(), result==null ? null : result.get());
			if(result==null){
				if(theFilter==SortedSearchFilter.OnlyMatch)
					Assert.assertFalse(set.contains(theValue));
				else
					Assert.assertTrue(set.isEmpty());
			} else if(set.equivalence().elementEquals(result.get(), theValue)){//
				Assert.assertEquals(0, onExact);
			} else{
				int comp=set.comparator().compare(result.get(), theValue);
				CollectionElement<T> adj=set.getAdjacentElement(result.getElementId(), comp>0);
				if(adj!=null)
					Assert.assertNotEquals(comp<0, set.comparator().compare(adj.get(), theValue));
				switch(theFilter){
				case OnlyMatch:
					throw new AssertionError("Should not have found a non-matching value");
				case PreferLess:
					if(comp>0)
						Assert.assertNull(adj);
					break;
				case PreferGreater:
					if(comp<0)
						Assert.assertNull(adj);
					break;
				case Less:
					Assert.assertTrue(comp < 0);
					break;
				case Greater:
					Assert.assertTrue(comp > 0);
					break;
				}
			}
		}
	}
}
