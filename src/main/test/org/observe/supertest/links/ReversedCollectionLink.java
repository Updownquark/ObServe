package org.observe.supertest.links;

import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.supertest.ChainLinkGenerator;
import org.observe.supertest.CollectionLinkElement;
import org.observe.supertest.ObservableChainLink;
import org.observe.supertest.ObservableCollectionLink;
import org.observe.supertest.ObservableCollectionTestDef;
import org.observe.supertest.OperationRejection;
import org.observe.supertest.TestValueType;
import org.qommons.TestHelper;

/**
 * Tests {@link ObservableCollection#reverse()} and {@link org.observe.collect.ObservableCollection.CollectionDataFlow#reverse()}
 *
 * @param <T> The type of values in the collection
 */
public class ReversedCollectionLink<T> extends OneToOneCollectionLink<T, T> {
	/** Generates {@link ReversedCollectionLink}s */
	public static final ChainLinkGenerator GENERATE = new ChainLinkGenerator.CollectionLinkGenerator() {
		@Override
		public <T> double getAffinity(ObservableChainLink<?, T> link, TestValueType targetType) {
			if (!(link instanceof ObservableCollectionLink))
				return 0;
			else if (targetType != null && targetType != link.getType())
				return 0;
			return 1;
		}

		@Override
		public <T, X> ObservableCollectionLink<T, X> deriveLink(String path, ObservableChainLink<?, T> sourceLink, TestValueType targetType,
			TestHelper helper) {
			ObservableCollectionLink<?, T> sourceCL = (ObservableCollectionLink<?, T>) sourceLink;
			CollectionDataFlow<?, ?, T> oneStepFlow;
			if (helper.getBoolean())
				oneStepFlow = sourceCL.getCollection().reverse().flow();
			else
				oneStepFlow = sourceCL.getCollection().flow().reverse();
			CollectionDataFlow<?, ?, T> multiStepFlow = sourceCL.getDef().multiStepFlow.reverse();
			return (ObservableCollectionLink<T, X>) new ReversedCollectionLink<>(path, sourceCL,
				new ObservableCollectionTestDef<>(sourceCL.getDef().type, oneStepFlow, multiStepFlow, sourceCL.getDef().orderImportant,
					sourceCL.getDef().checkOldValues),
				helper);
		}
	};

	/**
	 * @param path The path for this link
	 * @param sourceLink The source for this link
	 * @param def The collection definition for this link
	 * @param helper The randomness to use to initialize this link
	 */
	public ReversedCollectionLink(String path, ObservableCollectionLink<?, T> sourceLink, ObservableCollectionTestDef<T> def,
		TestHelper helper) {
		super(path, sourceLink, def, helper);
	}

	@Override
	protected T map(T sourceValue) {
		return sourceValue;
	}

	@Override
	protected T reverse(T value) {
		return value;
	}

	@Override
	protected boolean isReversible() {
		return true;
	}

	@Override
	public boolean isAcceptable(T value) {
		return getSourceLink().isAcceptable(value);
	}

	@Override
	public T getUpdateValue(T value) {
		return getSourceLink().getUpdateValue(value);
	}

	@Override
	public CollectionLinkElement<T, T> expectAdd(T value, CollectionLinkElement<?, T> after, CollectionLinkElement<?, T> before,
		boolean first, OperationRejection rejection, boolean execute) {
		return super.expectAdd(value, before, after, !first, rejection, execute);
	}

	@Override
	public CollectionLinkElement<T, T> expectMove(CollectionLinkElement<?, T> source, CollectionLinkElement<?, T> after,
		CollectionLinkElement<?, T> before, boolean first, OperationRejection rejection, boolean execute) {
		return super.expectMove(source, before, after, !first, rejection, execute);
	}

	@Override
	protected void checkOrder(CollectionLinkElement<T, T> element) {
		int elIndex = element.getIndex();
		int reversedSourceIndex = getSourceLink().getElements().size() - element.getFirstSource().getIndex() - 1;
		if (elIndex != reversedSourceIndex)
			element.error(
				err -> err.append("Expected at [").append(reversedSourceIndex).append("] but found at [").append(elIndex).append(']'));
	}

	@Override
	public String toString() {
		return "reverse()";
	}
}
