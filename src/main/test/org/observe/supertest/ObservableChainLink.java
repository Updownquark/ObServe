package org.observe.supertest;

import java.util.List;

import org.qommons.TestHelper;
import org.qommons.Transactable;

/**
 * A link in a chain (actually a tree) of observable structures in an {@link ObservableChainTester} test case
 *
 * @param <S> The type of the source link
 * @param <T> The type of this link's value, collection, etc.
 */
public interface ObservableChainLink<S, T> extends Transactable {
	/** @return A string identifying this link within the test case chain */
	String getPath();

	/** @return This link's type */
	TestValueType getType();

	/** @return The source link whose values are feeding this link */
	ObservableChainLink<?, S> getSourceLink();

	/** @param helper The source of randomness to use to initialize this link */
	void initialize(TestHelper helper);

	/** @return All links with this link as their source */
	List<? extends ObservableChainLink<T, ?>> getDerivedLinks();

	/** @return The index of this link in the {@link #getSourceLink() source link}'s {@link #getDerivedLinks() derived links} */
	int getSiblingIndex();

	/** @return A double describing the variety of modifications this chain link can perform on its observable structure */
	double getModificationAffinity();

	/**
	 * @param action The action to {@link org.qommons.TestHelper.RandomAction#or(double, Runnable) or} potential modifications into
	 * @param helper The source of randomness for the modifications
	 */
	void tryModify(TestHelper.RandomAction action, TestHelper helper);

	/**
	 * Validates this link's observable data at the end of a modification anywhere in the chain
	 *
	 * @param transactionEnd Whether any transaction begun before the modification has been closed
	 * @throws AssertionError If this link's data is not valid
	 */
	void validate(boolean transactionEnd) throws AssertionError;

	/** @return A string representation of this link's content data */
	String printValue();

	/** @return The current modification set */
	int getModSet();

	/** @return The current modification within the current modification set */
	int getModification();

	/** @return The overall number of modifications completed so far */
	int getOverallModification();

	/**
	 * @param modSet The current modification set
	 * @param modification The current modification within the current modification set
	 * @param overall The overall number of modifications completed so far
	 */
	void setModification(int modSet, int modification, int overall);
}
