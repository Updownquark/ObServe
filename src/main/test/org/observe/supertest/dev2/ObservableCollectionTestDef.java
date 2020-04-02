package org.observe.supertest.dev2;

import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.qommons.Ternian;

public class ObservableCollectionTestDef<T> {
	public final TestValueType type;
	public final CollectionDataFlow<?, ?, T> oneStepFlow;
	public final CollectionDataFlow<?, ?, T> multiStepFlow;
	public final boolean orderImportant;
	public final boolean checkOldValues;
	public final boolean rebaseRequired;
	public final Ternian allowPassive;

	public ObservableCollectionTestDef(TestValueType type, CollectionDataFlow<?, ?, T> oneStepFlow,
		CollectionDataFlow<?, ?, T> multiStepFlow, boolean orderImportant, boolean checkOldValues) {
		this(type, oneStepFlow, multiStepFlow, orderImportant, checkOldValues, false, Ternian.NONE);
	}

	public ObservableCollectionTestDef(TestValueType type, CollectionDataFlow<?, ?, T> oneStepFlow,
		CollectionDataFlow<?, ?, T> multiStepFlow, boolean orderImportant, boolean checkOldValues, boolean rebaseRequired,
		Ternian allowPassive) {
		this.type = type;
		this.oneStepFlow = oneStepFlow;
		this.multiStepFlow = multiStepFlow;
		this.orderImportant = orderImportant;
		this.checkOldValues = checkOldValues;
		this.rebaseRequired = rebaseRequired;
		this.allowPassive = allowPassive;
	}
}
