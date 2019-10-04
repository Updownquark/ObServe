package org.observe.supertest.dev2;

import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.qommons.Ternian;

public class ObservableCollectionTestDef<T> {
	public final TestValueType type;
	public final CollectionDataFlow<?, ?, T> flow;
	public final boolean orderImportant;
	public final boolean checkOldValues;
	public final boolean rebaseRequired;
	public final Ternian allowPassive;

	public ObservableCollectionTestDef(TestValueType type, CollectionDataFlow<?, ?, T> flow,
		boolean orderImportant, boolean checkOldValues) {
		this(type, flow, orderImportant, checkOldValues, false, Ternian.NONE);
	}

	public ObservableCollectionTestDef(TestValueType type, CollectionDataFlow<?, ?, T> flow,
		boolean orderImportant, boolean checkOldValues, boolean rebaseRequired, Ternian allowPassive) {
		this.type = type;
		this.flow = flow;
		this.orderImportant = orderImportant;
		this.checkOldValues = checkOldValues;
		this.rebaseRequired = rebaseRequired;
		this.allowPassive = allowPassive;
	}
}
