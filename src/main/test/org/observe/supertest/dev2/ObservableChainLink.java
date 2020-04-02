package org.observe.supertest.dev2;

import java.util.List;

import org.qommons.TestHelper;
import org.qommons.Transactable;

public interface ObservableChainLink<S, T> extends Transactable {
	TestValueType getType();

	ObservableChainLink<?, S> getSourceLink();

	List<? extends ObservableChainLink<T, ?>> getDerivedLinks();

	void initialize(TestHelper helper);

	void tryModify(TestHelper helper) throws AssertionError;

	void validate(boolean transactionEnd) throws AssertionError;

	String printValue();

	<X> void derive(TestHelper helper);
}
