package org.observe;

public class ObservableTester extends AbstractObservableTester<Void> {
	private final Observable<?> theObservable;

	public ObservableTester(Observable<?> value) {
		theObservable = value;
		setSynced(true);
	}

	@Override
	public void checkSynced() {
	}

	@Override
	public void checkValue(Void expected) {
	}

	@Override
	protected Subscription sync() {
		return theObservable.act(evt -> {
			op();
		});
	}
}
