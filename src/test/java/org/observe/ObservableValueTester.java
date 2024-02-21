package org.observe;

import static org.junit.Assert.assertEquals;

import java.util.Objects;

/**
 * A utility for testing an observable value
 *
 * @param <T> The type of the value
 */
public class ObservableValueTester<T> extends AbstractObservableTester<T> {
	private final ObservableValue<? extends T> theValue;
	private T theSynced;
	private double theTolerance;
	private boolean isCheckingOldValues;

	/** @param value The observable value to test */
	public ObservableValueTester(ObservableValue<? extends T> value) {
		this(value, Double.NaN);
		isCheckingOldValues = true;
	}

	/**
	 * @param value The observable value to test (must be a Number value)
	 * @param tolerance The tolerance to use when checking the observable's value against internal or external state
	 */
	public ObservableValueTester(ObservableValue<? extends T> value, double tolerance) {
		theValue = value;
		theTolerance = tolerance;
		setSynced(true);
	}

	/** @return The observable value being tested */
	protected ObservableValue<? extends T> getValue() {
		return theValue;
	}

	/** @return Whether this tester is comparing old values in events to the previous set value */
	public boolean isCheckingOldValues() {
		return isCheckingOldValues;
	}

	/**
	 * @param checkOldValues Whether this tester should compare old values in events to the previous set value
	 * @return This tester
	 */
	public ObservableValueTester<T> checkOldValues(boolean checkOldValues) {
		isCheckingOldValues = checkOldValues;
		return this;
	}

	@Override
	public void checkSynced() {
		if (Double.isNaN(theTolerance) || theSynced == null)
			assertEquals(theSynced, theValue.get());
		else
			assertEquals(((Number) theSynced).doubleValue(), ((Number) theValue.get()).doubleValue(), theTolerance);
	}

	@Override
	public void checkValue(T expected) {
		if (Double.isNaN(theTolerance) || expected == null || theSynced == null)
			assertEquals(expected, theSynced);
		else
			assertEquals(((Number) expected).doubleValue(), ((Number) theSynced).doubleValue(), theTolerance);
	}

	@Override
	protected Subscription sync() {
		return theValue.changes().act(evt -> {
			event(evt);
		});
	}

	/** @param evt The event that occurred */
	protected void event(ObservableValueEvent<? extends T> evt) {
		op();
		if (isCheckingOldValues && !Objects.equals(theSynced, evt.getOldValue()))
			throw new AssertionError("Expected " + theSynced + " but was " + evt.getOldValue());
		theSynced = evt.getNewValue();
	}
}
