package org.observe;

/**
 * A default implementation of observable value
 *
 * @param <T> The type of value that this value manages
 */
public abstract class DefaultObservableValue<T> extends DefaultObservable<ObservableValueEvent<T>> implements ObservableValue<T> {
	@Override
	public Observer<ObservableValueEvent<T>> control(DefaultObservable.OnSubscribe<ObservableValueEvent<T>> onSubscribe)
		throws IllegalStateException {
		return super.control(observer -> {
			fire(observer);
			if(onSubscribe != null)
				onSubscribe.onsubscribe(observer);
		});
	}

	private void fire(Observer<? super ObservableValueEvent<T>> observer) {
		ObservableValueEvent<T> event = new ObservableValueEvent<>(this, null, get(), null);
		try {
			observer.onNext(event);
		} catch(Throwable e) {
			observer.onError(e);
		}
	}
}
