package org.observe.supertest;

import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.collect.ObservableCollectionEvent;

/**
 * Defines a collection to be tested
 *
 * @param <T> The type of values in the collection
 */
public class ObservableCollectionTestDef<T> {
	/** The test type for the collection */
	public final TestValueType type;
	/** The flow for the collection, derived from the source link's flow */
	public final CollectionDataFlow<?, ?, T> oneStepFlow;
	/** The multi-step flow for the collection, derived from the root link's flow */
	public final CollectionDataFlow<?, ?, T> multiStepFlow;
	/**
	 * Whether order is important for the collection. E.g. hash-based distinct links may not manage their order strictly, so the order of
	 * elements may differ between the one-step and multi-step derived collections
	 */
	public final boolean orderImportant;
	/**
	 * Whether {@link ObservableCollectionEvent#getOldValue() old values} from events should be checked against the actual element value.
	 * Some collections, such as uncached variable mapping collections, do not reliably provide this information by necessity.
	 */
	public final boolean checkOldValues;

	/**
	 * @param type The test type for the collection
	 * @param oneStepFlow The flow for the collection, derived from the source link's flow
	 * @param multiStepFlow The multi-step flow for the collection, derived from the root link's flow
	 * @param orderImportant Whether order is important for the collection. E.g. hash-based distinct links may not manage their order
	 *        strictly, so the order of elements may differ between the one-step and multi-step derived collections
	 * @param checkOldValues Whether {@link ObservableCollectionEvent#getOldValue() old values} from events should be checked against the
	 *        actual element value. Some collections, such as uncached variable mapping collections, do not reliably provide this
	 *        information by necessity.
	 */
	public ObservableCollectionTestDef(TestValueType type, CollectionDataFlow<?, ?, T> oneStepFlow,
		CollectionDataFlow<?, ?, T> multiStepFlow, boolean orderImportant, boolean checkOldValues) {
		this.type = type;
		this.oneStepFlow = oneStepFlow;
		this.multiStepFlow = multiStepFlow;
		this.orderImportant = orderImportant;
		this.checkOldValues = checkOldValues;
	}
}
