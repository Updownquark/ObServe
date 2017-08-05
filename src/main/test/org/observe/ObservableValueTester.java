package org.observe;

import static org.junit.Assert.assertEquals;

import com.google.common.reflect.TypeToken;

/**
 * A utility for testing an observable value
 *
 * @param <T> The type of the value
 */
public class ObservableValueTester<T> extends AbstractObservableTester<T> {
	private final ObservableValue<? extends T> theValue;
	private T theSynced;
	private double theTolerance;

	/** @param value The observable value to test */
	public ObservableValueTester(ObservableValue<? extends T> value) {
		this(value, Double.NaN);
	}

	/**
	 * @param value The observable value to test (must be a Number value)
	 * @param tolerance The tolerance to use when checking the observable's value against internal or external state
	 */
	public ObservableValueTester(ObservableValue<? extends T> value, double tolerance) {
		if (!Double.isNaN(tolerance) && !TypeToken.of(Number.class).isAssignableFrom(value.getType().wrap()))
			throw new IllegalArgumentException("Cannot use a tolerance with a non-number value type: " + value.getType());
		theValue = value;
		theTolerance = tolerance;
		setSynced(true);
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
		return theValue.act(evt -> {
			op();
			theSynced = evt.getNewValue();
		});
	}
}
