package org.observe.supertest.dev2;

import java.util.List;

import org.qommons.TestHelper;
import org.qommons.Transactable;

public interface ObservableChainLink<S, T> extends Transactable {
	String getPath();

	TestValueType getType();

	ObservableChainLink<?, S> getSourceLink();

	void initialize(TestHelper helper);

	List<? extends ObservableChainLink<T, ?>> getDerivedLinks();

	int getSiblingIndex();

	double getModificationAffinity();

	void tryModify(TestHelper.RandomAction action, TestHelper helper);

	void validate(boolean transactionEnd) throws AssertionError;

	String printValue();

	void setModification(int modification);

	default int getLinkCount() {
		int count = 1;
		for (ObservableChainLink<T, ?> link : getDerivedLinks()) {
			count += link.getLinkCount();
		}
		return count;
	}

	default int getDepth() {
		if (getSourceLink() == null)
			return 0;
		else
			return getSourceLink().getDepth() + 1;
	}
}
