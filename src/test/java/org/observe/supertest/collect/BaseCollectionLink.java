package org.observe.supertest.collect;

import org.observe.collect.ObservableCollection;
import org.observe.supertest.ChainLinkGenerator;
import org.observe.supertest.CollectionOpType;
import org.observe.supertest.ObservableChainLink;
import org.observe.supertest.OperationRejection;
import org.observe.supertest.TestValueType;
import org.qommons.Transactable;
import org.qommons.collect.CollectionElement;
import org.qommons.testing.TestHelper;

import com.google.common.reflect.TypeToken;

/**
 * Simple base collection link
 *
 * @param <T> The type of values in the collection
 */
public class BaseCollectionLink<T> extends ObservableCollectionLink<T, T> {
	/** Generator for {@link BaseCollectionLink}s */
	public static final ChainLinkGenerator GENERATE = new ChainLinkGenerator.CollectionLinkGenerator() {
		@Override
		public <T> double getAffinity(ObservableChainLink<?, T> link, TestValueType targetType) {
			if (link != null)
				return 0;
			return 1;
		}

		@Override
		public <T, X> ObservableCollectionLink<T, X> deriveLink(String path, ObservableChainLink<?, T> sourceLink, TestValueType targetType,
			TestHelper helper) {
			TestValueType type = targetType != null ? targetType : nextType(helper);
			ObservableCollection<X> base = ObservableCollection.build((TypeToken<X>) type.getType()).build();
			ObservableCollectionTestDef<X> def = new ObservableCollectionTestDef<>(type, base.flow(), base.flow(), true, true);
			return (ObservableCollectionLink<T, X>) new BaseCollectionLink<>(path, def, helper);
		}
	};

	private int theModSet;
	private int theModificationNumber;
	private int theOverallModification;

	/**
	 * @param path The path for this link (generally "root")
	 * @param def The collection definition for this link
	 * @param helper The randomness to initialize this link
	 */
	public BaseCollectionLink(String path, ObservableCollectionTestDef<T> def, TestHelper helper) {
		super(path, null, def, helper);
		theOverallModification = -1;
	}

	/**
	 * @param path The path for this link (generally "root")
	 * @param def The collection definition for this link
	 * @param collection The collection
	 * @param helper The randomness to use to initialize this link
	 */
	public BaseCollectionLink(String path, ObservableCollectionTestDef<T> def, ObservableCollection<T> collection, TestHelper helper) {
		super(path, null, def, collection, collection, helper);
		theOverallModification = -1;
	}

	@Override
	public int getModSet() {
		return theModSet;
	}

	@Override
	public int getModification() {
		return theModificationNumber;
	}

	@Override
	public int getOverallModification() {
		return theOverallModification;
	}

	@Override
	public void setModification(int modSet, int modification, int overallModification) {
		theModSet = modSet;
		theModificationNumber = modification;
		theOverallModification = overallModification;
	}

	@Override
	protected Transactable getLocking() {
		return getCollection();
	}

	@Override
	public void initialize(TestHelper helper) {
		CollectionElement<T> el = getCollection().getTerminalElement(true);
		CollectionLinkElement<T, T> linkEl = getElements().peekFirst();
		while (el != null) {
			linkEl.expectAdded(el.get());

			el = getCollection().getAdjacentElement(el.getElementId(), true);
			linkEl = CollectionElement.get(getElements().getAdjacentElement(linkEl.getElementAddress(), true));
		}
		super.initialize(helper);
	}

	@Override
	public boolean isAcceptable(T value) {
		return true;
	}

	@Override
	public T getUpdateValue(CollectionLinkElement<T, T> element, T value) {
		return value;
	}

	@Override
	public void expect(ExpectedCollectionOperation<?, T> derivedOp, OperationRejection rejection, boolean execute) {
		if (!execute)
			return;
		switch (derivedOp.getType()) {
		case add:
		case move:
			throw new IllegalStateException("Should be using expectAdd");
		case remove:
			derivedOp.getElement().expectRemoval();
			break;
		case set:
			derivedOp.getElement().expectSet(derivedOp.getValue());
			break;
		}
	}

	@Override
	public CollectionLinkElement<T, T> expectAdd(T value, CollectionLinkElement<?, T> after, CollectionLinkElement<?, T> before,
		boolean first, OperationRejection rejection, boolean execute) {
		for (CollectionElement<CollectionLinkElement<T, T>> el : getElements().elementsBetween(
			after == null ? null : after.getElementAddress(), false, //
				before == null ? null : before.getElementAddress(), false)) {
			if (el.get().wasAdded() && !el.get().isAddExpected()
				&& getCollection().equivalence().elementEquals(el.get().getCollectionValue(), value)) {
				if (execute)
					el.get().expectAdded(value);
				return el.get();
			}
		}
		if (execute)
			throw new AssertionError("No new elements found to expect between " + after + " and " + before);
		else {
			return null;
		}
	}

	@Override
	public CollectionLinkElement<T, T> expectMove(CollectionLinkElement<?, T> source, CollectionLinkElement<?, T> after,
		CollectionLinkElement<?, T> before, boolean first, OperationRejection rejection, boolean execute) {
		if (source.isPresent())
			return (CollectionLinkElement<T, T>) source;
		expect(//
			new ExpectedCollectionOperation<>(source, CollectionOpType.remove, source.getValue(),
				source.getValue()),
			rejection, execute);
		while (after != null && (after == source || !after.isPresent() || after.wasAdded()))
			after = CollectionElement.get(getElements().getAdjacentElement(after.getElementAddress(), false));
		while (before != null && (before == source || !before.isPresent() || before.wasAdded()))
			before = CollectionElement.get(getElements().getAdjacentElement(before.getElementAddress(), true));
		return expectAdd(//
			source.getValue(), after, before, first, rejection, execute);
	}

	@Override
	protected void validate(CollectionLinkElement<T, T> element, boolean transactionEnd) {}

	/**
	 * @param helper The randomness to use to get the type
	 * @return A random test type
	 */
	public static TestValueType nextType(TestHelper helper) {
		// The DOUBLE type is much less performant. There may be some value, but we'll use it less often.
		TestHelper.RandomSupplier<TestValueType> action = helper.createSupplier();
		action.or(10, () -> TestValueType.INT);
		action.or(5, () -> TestValueType.STRING);
		action.or(2, () -> TestValueType.DOUBLE);
		return action.get(null);
	}

	@Override
	public String toString() {
		return "base(" + getType() + ")";
	}
}
