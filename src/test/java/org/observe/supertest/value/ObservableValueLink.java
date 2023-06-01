package org.observe.supertest.value;

import java.util.List;

import org.junit.Assert;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.ObservableValueTester;
import org.observe.SettableValue;
import org.observe.collect.ObservableElement;
import org.observe.collect.ObservableElementTester;
import org.observe.supertest.AbstractChainLink;
import org.observe.supertest.ObservableChainLink;
import org.observe.supertest.ObservableChainTester;
import org.observe.supertest.OperationRejection;
import org.observe.supertest.TestValueType;
import org.observe.supertest.collect.ObservableCollectionLink;
import org.qommons.collect.ElementId;
import org.qommons.testing.TestHelper;

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
	private final boolean isCheckingOldValues;

	/**
	 * @param path The path for this link
	 * @param sourceLink The source for this link
	 * @param type The type for this link
	 * @param checkOldValues Whether this link should check event old values against the previous value
	 */
	public ObservableValueLink(String path, ObservableChainLink<?, S> sourceLink, TestValueType type, boolean checkOldValues) {
		super(path, sourceLink);
		theType = type;
		isCheckingOldValues = checkOldValues;
	}

	/** @return This link's value */
	public ObservableValue<T> getValue() {
		return theValue;
	}

	/** @return This link's value tester */
	public ObservableValueTester<T> getTester() {
		return theTester;
	}

	/** @return True if this link's actual value type may be different than its {@link #getType()} */
	public boolean isTypeCheat() {
		return false;
	}

	/**
	 * @return Whether the {@link ObservableValueEvent#getOldValue() old value} in events fired by this observable should tell an accurate
	 *         history of the value
	 */
	public boolean isCheckingOldValues() {
		return isCheckingOldValues;
	}

	/** @return Whether this value may delay consistency of event-synchronized data until the transaction completes */
	public boolean isTransactional() {
		return false;
	}

	@Override
	public void initialize(TestHelper helper) {
		theValue = createValue(helper);
		if (theValue instanceof ObservableElement)
			theTester = new ObservableElementTester<>((ObservableElement<T>) theValue);
		else
			theTester = new ObservableValueTester<>(theValue);
		theTester.checkOldValues(isCheckingOldValues);
		super.initialize(helper);
	}

	/**
	 * Creates the value for this link
	 *
	 * @param helper The source of randomness to use to create the value
	 * @return The value for this link
	 */
	protected abstract ObservableValue<T> createValue(TestHelper helper);

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
		if (theValue instanceof SettableValue && ObservableChainTester.SUPPLIERS.containsKey(getType()))
			return 5;
		return 0;
	}

	@Override
	public void tryModify(TestHelper.RandomAction action, TestHelper helper) {
		if (theValue instanceof SettableValue && ObservableChainTester.SUPPLIERS.containsKey(getType()))
			action.or(5, () -> {
				SettableValue<T> settable = (SettableValue<T>) theValue;
				T value = getSetValue(helper);
				if (helper.isReproducing())
					System.out.println("Setting value " + settable.get() + "->" + value);
				String message = settable.isAcceptable(value);
				try {
					settable.set(value, null);
					Assert.assertNull(message);
				} catch (UnsupportedOperationException | IllegalArgumentException e) {
					if (message == null)
						throw new AssertionError("Unexpected set error", e);
				}
				OperationRejection.Simple rejection = new OperationRejection.Simple().withActualRejection(message);
				expectSet(value, rejection);
				if (message != null && !rejection.isRejected())
					throw new AssertionError("Unexpected rejection with " + message);
				else if (rejection.isRejected() && message == null)
					throw new AssertionError("Expected rejection with " + rejection.getRejection());
			});
	}

	/**
	 * @param helper The randomness
	 * @return The value to set in this settable value
	 */
	protected T getSetValue(TestHelper helper) {
		return (T) ObservableChainTester.SUPPLIERS.get(getType()).apply(helper);
	}

	/**
	 * @param value The value for which a set was attempted in this settable value
	 * @param rejection The rejection for the operation
	 */
	public abstract void expectSet(T value, OperationRejection rejection);
}
