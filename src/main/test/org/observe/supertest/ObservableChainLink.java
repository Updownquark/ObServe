package org.observe.supertest;

import org.qommons.TestHelper;

import com.google.common.reflect.TypeToken;

interface ObservableChainLink<T> {
	TypeToken<T> getType();

	void tryModify(TestHelper helper);
	void check();

	<X> ObservableChainLink<X> derive(TestHelper helper);
}