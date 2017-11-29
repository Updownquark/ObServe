package org.observe.supertest;

import org.qommons.TestHelper;
import org.qommons.Transaction;

import com.google.common.reflect.TypeToken;

interface ObservableChainLink<T> {
	TypeToken<T> getType();

	ObservableChainLink<?> getParent();
	Transaction lock();
	void tryModify(TestHelper helper);
	void check(boolean transComplete);

	<X> ObservableChainLink<X> derive(TestHelper helper);

	String printValue();
}