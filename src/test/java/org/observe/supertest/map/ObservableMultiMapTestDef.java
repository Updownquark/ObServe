package org.observe.supertest.map;

import org.observe.assoc.ObservableMapEvent;
import org.observe.assoc.ObservableMultiMap;
import org.observe.assoc.ObservableMultiMap.MultiMapFlow;
import org.observe.collect.ObservableCollectionEvent;
import org.observe.supertest.TestValueType;

/**
 * Defines a multi map to be tested
 *
 * @param <K> The type of keys values in the multi map
 * @param <V> The type of values values in the multi map
 */
public class ObservableMultiMapTestDef<K, V> {
	/** The test type for the key set */
	public final TestValueType keyType;
	/** The test type for the values */
	public final TestValueType valueType;
	/** The one-step flow for the multi map, derived from the source's flow */
	public final ObservableMultiMap.MultiMapFlow<K, V> oneStepFlow;
	/** The multi-step flow for the multi map, derived from the root's flow */
	public final ObservableMultiMap.MultiMapFlow<K, V> multiStepFlow;
	/**
	 * Whether order is important for the map. E.g. hash-based distinct links may not manage their order strictly, so the order of elements
	 * may differ between the one-step and multi-step derived maps
	 */
	public final boolean orderImportant;
	/**
	 * Whether {@link ObservableCollectionEvent#getOldValue() old values} from events should be checked against the actual element value.
	 * Some collections, such as uncached variable mapping collections, do not reliably provide this information by necessity.
	 */
	public final boolean checkOldValues;

	/**
	 * @param keyType The key type for the multi map
	 * @param valueType The value type for the multi map
	 * @param oneStepFlow The one-step flow for the multi map, derived from the source's flow
	 * @param multiStepFlow The multi-step flow for the multi map, derived from the root's flow
	 * @param orderImportant Whether order is important for the map. E.g. hash-based distinct links may not manage their order strictly, so
	 *        the order of elements may differ between the one-step and multi-step derived maps
	 * @param checkOldValues Whether {@link ObservableMapEvent#getOldValue() old values} from events should be checked against the actual
	 *        element value. Some maps, such as where uncached variable mapping is in the flow, do not reliably provide this information by
	 *        necessity.
	 */
	public ObservableMultiMapTestDef(TestValueType keyType, TestValueType valueType, MultiMapFlow<K, V> oneStepFlow,
		MultiMapFlow<K, V> multiStepFlow, boolean orderImportant, boolean checkOldValues) {
		this.keyType = keyType;
		this.valueType = valueType;
		this.oneStepFlow = oneStepFlow;
		this.multiStepFlow = multiStepFlow;
		this.orderImportant = orderImportant;
		this.checkOldValues = checkOldValues;
	}
}
