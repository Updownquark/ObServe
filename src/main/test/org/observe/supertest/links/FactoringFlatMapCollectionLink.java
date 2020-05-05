package org.observe.supertest.links;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.Assert;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.supertest.ChainLinkGenerator;
import org.observe.supertest.CollectionLinkElement;
import org.observe.supertest.ExpectedCollectionOperation;
import org.observe.supertest.ObservableChainLink;
import org.observe.supertest.ObservableCollectionLink;
import org.observe.supertest.ObservableCollectionTestDef;
import org.observe.supertest.OperationRejection;
import org.observe.supertest.TestValueType;
import org.observe.util.TypeTokens;
import org.qommons.TestHelper;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.io.CsvParser;
import org.qommons.io.TextParseException;

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
			ObservableCollection.CollectionDataFlow<?, ?, Integer> oneStepFlow = sourceCL.getCollection().flow()
				.flatMap(TypeTokens.get().INT, i -> getPrimeFactors(i).flow());
			// Eclipse is being stupid about whether this cast is needed or not. It definitely should not be necessary
			// and eclipse flags it with a warning, but if I remove it, there's an error.
			// But the error doesn't actually seem to be a compile error, because the class will still run.
			@SuppressWarnings("cast")
			ObservableCollection.CollectionDataFlow<?, ?, Integer> multiStepFlow = (CollectionDataFlow<?, ?, Integer>) sourceCL
			.getDef().multiStepFlow.flatMap(TypeTokens.get().INT, i -> getPrimeFactors(i).flow());
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
		CollectionLinkElement<?, Integer> before, boolean first, OperationRejection rejection) {
		rejection.reject(StdMsg.UNSUPPORTED_OPERATION);
		return null;
	}

	@Override
	public CollectionLinkElement<Integer, Integer> expectMove(CollectionLinkElement<?, Integer> source,
		CollectionLinkElement<?, Integer> after, CollectionLinkElement<?, Integer> before, boolean first, OperationRejection rejection) {
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
	public Integer getUpdateValue(Integer value) {
		return getSourceLink().getUpdateValue(value);
	}

	@Override
	public String toString() {
		return "factor()";
	}

	private static final int[] PRIME_INTS;

	static {
		List<Integer> intList = new ArrayList<>(10000);
		try (BufferedReader reader = new BufferedReader(
			new InputStreamReader(FactoringFlatMapCollectionLink.class.getResourceAsStream("primes.csv")))) {
			CsvParser parser = new CsvParser(reader, ',');
			String[] line = parser.parseNextLine();
			while (line != null) {
				for (String prime : line)
					intList.add(Integer.parseInt(prime.trim()));
				line = parser.parseNextLine();
			}
		} catch (IOException | TextParseException | NumberFormatException e) {
			e.printStackTrace();
			intList.clear();
		}
		if (intList.isEmpty())
			PRIME_INTS = null;
		else {
			Integer[] intArray = intList.toArray(new Integer[intList.size()]);
			PRIME_INTS = new int[intArray.length];
			for (int i = 0; i < PRIME_INTS.length; i++)
				PRIME_INTS[i] = intArray[i].intValue();
		}
	}

	static ObservableCollection<Integer> getPrimeFactors(int value) {
		return CACHED_FACTORIZATION.computeIfAbsent(value, FactoringFlatMapCollectionLink::_factor);
	}

	private static ObservableCollection<Integer> _factor(int value) {
		if (value == 1 || value == 0 || value == -1)
			return ObservableCollection.of(TypeTokens.get().INT, value);

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
		return ObservableCollection.of(TypeTokens.get().INT, factors);
	}

	private static Map<Integer, ObservableCollection<Integer>> CACHED_FACTORIZATION = new ConcurrentHashMap<>();
}
