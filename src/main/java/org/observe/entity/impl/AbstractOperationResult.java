package org.observe.entity.impl;

import java.util.concurrent.atomic.AtomicReference;

import org.observe.Observable;
import org.observe.SimpleObservable;
import org.observe.entity.EntityOperationException;
import org.observe.entity.EntityQueryResult;
import org.observe.entity.ObservableEntityProvider.Cancelable;
import org.observe.entity.ObservableEntityResult;

/**
 * Abstract {@link ObservableEntityResult} implementation that handles status changes and {@link EntityQueryResult#cancel(boolean)
 * cancellation}
 *
 * @param <E> The entity type of the operation
 */
public abstract class AbstractOperationResult<E> implements ObservableEntityResult<E> {
	private final AtomicReference<ResultStatus> theStatus;
	private final SimpleObservable<AbstractOperationResult<E>> theStatusChanges;
	private volatile EntityOperationException theFailure;
	private volatile boolean isCanceled;
	private volatile boolean isCancelledWithInterrupt;
	private volatile Cancelable theCancelable;

	AbstractOperationResult() {
		theStatus = new AtomicReference<>(ResultStatus.WAITING);
		theStatusChanges = SimpleObservable.build().safe(true).build(obs -> obs.onNext(this));
	}

	void setCancelable(Cancelable cancelable) {
		theCancelable = cancelable;
		if (isCanceled)
			theCancelable.cancel(isCancelledWithInterrupt, () -> updateStatus(ResultStatus.CANCELLED));
	}

	@Override
	public ResultStatus getStatus() {
		return theStatus.get();
	}

	private void updateStatus(ResultStatus status) {
		if (theStatus.getAndSet(status) != status)
			theStatusChanges.onNext(this);
	}

	@Override
	public void cancel(boolean mayInterruptIfRunning) {
		if (!getStatus().isDone() && !isCanceled) {
			isCanceled = true;
			isCancelledWithInterrupt = mayInterruptIfRunning;
			if (theCancelable != null)
				theCancelable.cancel(mayInterruptIfRunning, () -> updateStatus(ResultStatus.CANCELLED));
		}
	}

	@Override
	public EntityOperationException getFailure() {
		return theFailure;
	}

	@Override
	public Observable<? extends ObservableEntityResult<E>> watchStatus() {
		return theStatusChanges.readOnly();
	}

	/**
	 * Marks this operation as {@link org.observe.config.ObservableOperationResult.ResultStatus#FAILED failed})
	 *
	 * @param failure The failure exception
	 * @return This operation
	 */
	public AbstractOperationResult<E> failed(EntityOperationException failure) {
		theFailure = failure;
		updateStatus(ResultStatus.FAILED);
		return this;
	}

	/**
	 * Marks this operation as {@link org.observe.config.ObservableOperationResult.ResultStatus#FULFILLED fulfilled})
	 *
	 * @return This operation
	 */
	protected AbstractOperationResult<E> fulfilled() {
		updateStatus(ResultStatus.FULFILLED);
		return this;
	}
}
