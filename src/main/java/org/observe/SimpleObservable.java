package org.observe;

/**
 * A simple observable that can be controlled directly
 *
 * @param <T> The type of values from this observable
 */
public class SimpleObservable<T> extends DefaultObservable<T> implements Observer<T> {
	private Observer<T> theController = control(null);

	@Override
	public <V extends T> void onNext(V value) {
		theController.onNext(value);
	}

	@Override
	public <V extends T> void onCompleted(V value) {
		theController.onCompleted(value);
	}

	@Override
	public void onError(Throwable e) {
		theController.onError(e);
	}

	/** @return An observable that fires events from this SimpleObservable but cannot be used to initiate events */
	public Observable<T> readOnly() {
		return new ReadOnlyObservable<>(this);
	}

	static class ReadOnlyObservable<T> implements Observable<T> {
		private final SimpleObservable<T> theWrapped;

		ReadOnlyObservable(SimpleObservable<T> wrap) {
			theWrapped = wrap;
		}

		@Override
		public Subscription subscribe(Observer<? super T> observer) {
			return theWrapped.subscribe(observer);
		}

		@Override
		public boolean isSafe() {
			return theWrapped.isSafe();
		}
	}
}
