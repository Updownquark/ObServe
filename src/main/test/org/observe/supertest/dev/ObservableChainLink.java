package org.observe.supertest.dev;

import org.qommons.TestHelper;
import org.qommons.Transaction;

import com.google.common.reflect.TypeToken;

interface ObservableChainLink<T> {
	TypeToken<T> getType();

	ObservableChainLink<?> getParent();
	ObservableChainLink<?> getChild();

	Transaction lock();
	void tryModify(TestHelper helper);
	void check(boolean transComplete);

	<X> ObservableChainLink<X> derive(TestHelper helper);

	String printValue();
}