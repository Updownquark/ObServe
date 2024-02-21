package org.observe.supertest.collect;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.junit.Assert;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.supertest.ChainLinkGenerator;
import org.observe.supertest.ObservableChainLink;
import org.observe.supertest.OperationRejection;
import org.observe.supertest.TestValueType;
import org.qommons.LambdaUtils;
import org.qommons.Primes;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.testing.TestHelper;

/** Simple flat-map test that maps integer-typed collections to the factorization of each element */
public class FactoringFlatMapCollectionLink extends AbstractFlatMappedCollectionLink<Integer, Integer> {
	/** Generates {@link FactoringFlatMapCollectionLink}s */
	public static final ChainLinkGenerator GENERATE = new ChainLinkGenerator.CollectionLinkGenerator() {
		@Override
		public <T> double getAffinity(ObservableChainLink<?, T> sourceLink, TestValueType targetType) {
			if (PRIME_INTS == null || !(sourceLink instanceof ObservableCollectionLink))
				return 0;
			else if (sourceLink.getType() != TestValueType.INT)
				return 0;
			else if (targetType != null && targetType != TestValueType.INT)
				return 0;
			return .5; // This link can be quite expensive, so we'll use it more sparingly
		}

		@Override
		public <T, X> ObservableCollectionLink<T, X> deriveLink(String path, ObservableChainLink<?, T> sourceLink, TestValueType targetType,
			TestHelper helper) {
			ObservableCollectionLink<?, Integer> sourceCL = (ObservableCollectionLink<?, Integer>) sourceLink;
			Function<Integer, CollectionDataFlow<Integer, Integer, Integer>> factorize = LambdaUtils.printableFn(//
				i -> getPrimeFactors(i).flow(), "factorize", null);
			ObservableCollection.CollectionDataFlow<?, ?, Integer> oneStepFlow = sourceCL.getCollection().flow()
				.flatMap(factorize);
			ObservableCollection.CollectionDataFlow<?, ?, Integer> multiStepFlow = sourceCL
			.getDef().multiStepFlow.flatMap(factorize);
			ObservableCollectionTestDef<Integer> def = new ObservableCollectionTestDef<>(TestValueType.INT, oneStepFlow, multiStepFlow,
				sourceCL.getDef().orderImportant, sourceCL.getDef().checkOldValues);
			return (ObservableCollectionLink<T, X>) new FactoringFlatMapCollectionLink(path, sourceCL, def, helper);
		}
	};

	/**
	 * @param path The path for the link
	 * @param sourceLink The source collection link
	 * @param def The collection definition for this link
	 * @param helper The randomness for this link
	 */
	public FactoringFlatMapCollectionLink(String path, ObservableCollectionLink<?, Integer> sourceLink,
		ObservableCollectionTestDef<Integer> def, TestHelper helper) {
		super(path, sourceLink, def, helper);
	}

	@Override
	public CollectionLinkElement<Integer, Integer> expectAdd(Integer value, CollectionLinkElement<?, Integer> after,
		CollectionLinkElement<?, Integer> before, boolean first, OperationRejection rejection, boolean execute) {
		rejection.reject(StdMsg.UNSUPPORTED_OPERATION);
		return null;
	}

	@Override
	public CollectionLinkElement<Integer, Integer> expectMove(CollectionLinkElement<?, Integer> source,
		CollectionLinkElement<?, Integer> after, CollectionLinkElement<?, Integer> before, boolean first, OperationRejection rejection,
		boolean execute) {
		if (after != null && source.getCollectionAddress().compareTo(after.getCollectionAddress()) < 0) {
			rejection.reject(StdMsg.UNSUPPORTED_OPERATION);
			return null;
		} else if (before != null && source.getCollectionAddress().compareTo(before.getCollectionAddress()) > 0) {
			rejection.reject(StdMsg.UNSUPPORTED_OPERATION);
			return null;
		} else
			return (CollectionLinkElement<Integer, Integer>) source;
	}

	@Override
	public void expect(ExpectedCollectionOperation<?, Integer> derivedOp, OperationRejection rejection, boolean execute) {
		rejection.reject(StdMsg.UNSUPPORTED_OPERATION);
	}

	@Override
	public void expectFromSource(ExpectedCollectionOperation<?, Integer> sourceOp) {
		List<CollectionLinkElement<Integer, ?>> derivedEls = sourceOp.getElement().getDerivedElements(getSiblingIndex());
		switch (sourceOp.getType()) {
		case add:
			List<Integer> factored = getPrimeFactors(sourceOp.getValue());
			Assert.assertEquals(factored.size(), derivedEls.size());
			for (int i = 0; i < factored.size(); i++)
				((CollectionLinkElement<Integer, Integer>) derivedEls.get(i)).expectAdded(factored.get(i));
			break;
		case remove:
			for (CollectionLinkElement<Integer, ?> derivedEl : derivedEls)
				derivedEl.expectRemoval();
			break;
		case move:
			throw new IllegalStateException();
		case set:
			if (!sourceOp.getOldValue().equals(sourceOp.getValue())) {
				factored = getPrimeFactors(sourceOp.getOldValue());
				int i;
				for (i = 0; i < factored.size(); i++)
					((CollectionLinkElement<Integer, Integer>) derivedEls.get(i)).expectRemoval();
				factored = getPrimeFactors(sourceOp.getValue());
				for (int j = 0; j < factored.size(); j++, i++)
					((CollectionLinkElement<Integer, Integer>) derivedEls.get(i)).expectAdded(factored.get(j));
			}
			break;
		}
	}

	@Override
	public boolean isAcceptable(Integer value) {
		return false;
	}

	@Override
	public Integer getUpdateValue(CollectionLinkElement<Integer, Integer> element, Integer value) {
		return ((ObservableCollectionLink<Object, Integer>)getSourceLink()).getUpdateValue((CollectionLinkElement<Object, Integer>) element.getFirstSource(), value);
	}

	@Override
	public String toString() {
		return "factor()";
	}

	private static final int[] PRIME_INTS;

	static {
		PRIME_INTS = new int[10000];
		Primes primes = new Primes();
		primes.generateAtLeast(PRIME_INTS.length);
		for (int i = 0, prime = 2; i < PRIME_INTS.length; i++, prime = primes.getPrimeGTE(prime + 1))
			PRIME_INTS[i] = prime;
	}

	static ObservableCollection<Integer> getPrimeFactors(int value) {
		return CACHED_FACTORIZATION.computeIfAbsent(value, FactoringFlatMapCollectionLink::_factor);
	}

	private static ObservableCollection<Integer> _factor(int value) {
		if (value == 1 || value == 0 || value == -1)
			return ObservableCollection.of(value);

		List<Integer> factors = new ArrayList<>(5);
		if (value < 0) {
			factors.add(-1);
			value=-value;
		}

		for (int factor : PRIME_INTS) {
			while (value % factor == 0) {
				factors.add(factor);
				value /= factor;
			}
		}
		// Possible to get a value not completely factorizable by the primes in my list
		if (value > 1)
			factors.add(value);
		return ObservableCollection.of(factors);
	}

	private static final Map<Integer, ObservableCollection<Integer>> CACHED_FACTORIZATION = new ConcurrentHashMap<>();
}
