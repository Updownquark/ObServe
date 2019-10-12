package org.observe.supertest.dev2;

import org.qommons.StructuredTransactable;
import org.qommons.TestHelper;

public interface ObservableChainLink<S, T> extends StructuredTransactable {
	TestValueType getType();

	ObservableChainLink<?, S> getSourceLink();

	ObservableChainLink<T, ?> getDerivedLink();

	void initialize(TestHelper helper);

	void tryModify(TestHelper helper) throws AssertionError;

	void validate(boolean transactionEnd) throws AssertionError;

	String printValue();

	<X> ObservableChainLink<T, X> derive(TestHelper helper);
}
