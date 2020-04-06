package org.observe.supertest.dev2;

import java.util.List;

import org.observe.ObservableValue;
import org.observe.ObservableValueTester;
import org.observe.collect.ObservableElement;
import org.observe.collect.ObservableElementTester;
import org.qommons.TestHelper;
import org.qommons.Transaction;
import org.qommons.collect.ElementId;

public abstract class ObservableValueLink<S, T> extends AbstractChainLink<S, T> {
	private final TestValueType theType;
	private ObservableValue<T> theValue;
	private ObservableValueTester<T> theTester;

	public ObservableValueLink(ObservableChainLink<?, S> sourceLink, TestValueType type) {
		super(sourceLink);
		theType = type;
	}

	public ObservableValue<T> getValue() {
		return theValue;
	}

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
