package org.observe.supertest;

import org.qommons.TestHelper;
import org.qommons.Transaction;

import com.google.common.reflect.TypeToken;

interface ObservableChainLink<T> {
	TypeToken<T> getType();

	ObservableChainLink<?> getParent();
	ObservableChainLink<?> getChild();

	default ModTransaction createTransaction(ModTransaction parent) {
		ObservableChainLink<?> parentLink = getParent();
		if (parent == null && parentLink != null)
			parent = parentLink.createTransaction(null);
		ObservableChainLink<?> childLink = getChild();
		return new ModTransaction(parent, pt -> childLink == null ? null : childLink.createTransaction(pt));
	}

	Transaction lock();
	void tryModify(TestHelper helper);
	void check(boolean transComplete);

	<X> ObservableChainLink<X> derive(TestHelper helper);

	String printValue();
}