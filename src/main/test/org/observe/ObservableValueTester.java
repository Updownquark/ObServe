package org.observe;

import static org.junit.Assert.assertEquals;

public class ObservableValueTester<T> extends AbstractObservableTester<T> {
	private final ObservableValue<? extends T> theValue;
	private T theSynced;
	private double theTolerance;

	public ObservableValueTester(ObservableValue<? extends T> value) {
		this(value, Double.NaN);
	}

	public ObservableValueTester(ObservableValue<? extends T> value, double tolerance) {
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
			theSynced = evt.getValue();
		});
	}
}
