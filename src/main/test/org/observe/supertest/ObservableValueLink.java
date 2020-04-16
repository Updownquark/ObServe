package org.observe.supertest;

import java.util.List;

import org.observe.ObservableValue;
import org.observe.ObservableValueTester;
import org.observe.collect.ObservableElement;
import org.observe.collect.ObservableElementTester;
import org.qommons.TestHelper;
import org.qommons.Transaction;
import org.qommons.collect.ElementId;

/**
 * An {@link ObservableChainLink} whose observable structure is an {@link ObservableValue}
 * 
 * @param <S> The type of the source link
 * @param <T> The type of this link's value
 */
public abstract class ObservableValueLink<S, T> extends AbstractChainLink<S, T> {
	private final TestValueType theType;
	private ObservableValue<T> theValue;
	private ObservableValueTester<T> theTester;

	/**
	 * @param path The path for this link
	 * @param sourceLink The source for this link
	 * @param type The type for this link
	 */
	public ObservableValueLink(String path, ObservableChainLink<?, S> sourceLink, TestValueType type) {
		super(path, sourceLink);
		theType = type;
	}

	/** @return This link's value */
	public ObservableValue<T> getValue() {
		return theValue;
	}

	/** @return This link's value tester */
	public ObservableValueTester<T> getTester() {
		return theTester;
	}

	@Override
	public void initialize(TestHelper helper) {
		super.initialize(helper);
		theValue = createValue(helper);
		if (theValue instanceof ObservableElement)
			theTester = new ObservableElementTester<>((ObservableElement<T>) theValue);
		else
			theTester = new ObservableValueTester<>(theValue);
	}

	/**
	 * Creates the value for this link
	 * 
	 * @param helper The source of randomness to use to create the value
	 * @return The value for this link
	 */
	protected abstract ObservableValue<T> createValue(TestHelper helper);

	@Override
	public Transaction lock(boolean write, Object cause) {
		return getSourceLink().lock(write, cause);
	}

	@Override
	public Transaction tryLock(boolean write, Object cause) {
		return getSourceLink().tryLock(write, cause);
	}

	@Override
	public TestValueType getType() {
		return theType;
	}

	@Override
	public List<? extends ValueSourcedLink<T, ?>> getDerivedLinks() {
		return (List<? extends ValueSourcedLink<T, ?>>) super.getDerivedLinks();
	}

	@Override
	public String printValue() {
		if (theValue instanceof ObservableElement) {
			ElementId element = ((ObservableElement<T>) theValue).getElementId();
			if (element == null)
				return "none";
			else
				return "[" + ((ObservableCollectionLink<?, S>) getSourceLink()).getCollection().getElementsBefore(element) + "]: "
				+ theValue.get();
		} else
			return String.valueOf(theValue.get());
	}

	@Override
	public double getModificationAffinity() {
		// TODO If the value it settable, set the value
		return 0;
	}

	@Override
	public void tryModify(TestHelper.RandomAction action, TestHelper helper) {
		// TODO
	}
}
