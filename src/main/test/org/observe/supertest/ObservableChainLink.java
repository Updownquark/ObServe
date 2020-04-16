package org.observe.supertest;

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

	int getModSet();

	int getModification();

	int getOverallModification();

	void setModification(int modSet, int modification, int overall);

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
