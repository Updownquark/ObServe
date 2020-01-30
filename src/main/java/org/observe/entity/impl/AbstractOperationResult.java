package org.observe.entity.impl;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

import org.observe.Subscription;
import org.observe.entity.EntityOperationException;
import org.observe.entity.EntityQueryResult;
import org.observe.entity.ObservableEntityResult;
import org.qommons.collect.ListenerList;

/**
 * Abstract {@link ObservableEntityResult} implementation that handles status changes and {@link EntityQueryResult#dispose() disposal}
 *
 * @param <E> The entity type of the operation
 */
public abstract class AbstractOperationResult<E> implements ObservableEntityResult<E> {
	private final boolean isQuery;
	private final AtomicReference<ResultStatus> theStatus;
	private final ListenerList<Runnable> theStatusChangeListeners;
	private volatile EntityOperationException theFailure;
	private volatile boolean isDisposed;

	AbstractOperationResult(boolean query) {
		isQuery = query;
		theStatus = new AtomicReference<>(ResultStatus.WAITING);
		theStatusChangeListeners = ListenerList.build().build();
	}

	@Override
	public ResultStatus getStatus() {
		return theStatus.get();
	}

	private void updateStatus(UnaryOperator<ResultStatus> op) {
		boolean[] changed = new boolean[1];
		theStatus.updateAndGet(status -> {
			ResultStatus newStatus = op.apply(status);
			changed[0] = newStatus != status;
			return newStatus;
		});
		if (changed[0]) {
			theStatusChangeListeners.forEach(//
				Runnable::run);
		}
	}

	@Override
	public boolean cancel(boolean mayInterruptIfRunning) {
		boolean[] cancelled = new boolean[1];
		boolean[] disposed = new boolean[1];
		updateStatus(status -> {
			cancelled[0] = disposed[0] = false;
			switch (status) {
			case WAITING:
				cancelled[0] = true;
				return ResultStatus.CANCELLED;
			case EXECUTING:
				if (mayInterruptIfRunning) {
					cancelled[0] = true;
					return ResultStatus.CANCELLED;
				} else {
					disposed[0] = true;
					return status;
				}
			case FULFILLED:
				if (isQuery) {
					disposed[0] = true;
					return ResultStatus.DISPOSED;
				} else
					return status;
			default:
				cancelled[0] = false;
				return status;
			}
		});
		if (isQuery && disposed[0])
			isDisposed = true;
		return cancelled[0];
	}

	/** Marks this operation as disposed (for {@link EntityQueryResult}s) */
	protected void dispose() {
		isDisposed = true;
		updateStatus(status -> {
			switch (status) {
			case FULFILLED:
				return ResultStatus.DISPOSED;
			default:
				return status;
			}
		});
	}

	@Override
	public EntityOperationException getFailure() {
		return theFailure;
	}

	/**
	 * @param action The action to perform when the status changes
	 * @return A subscription to cancel listening
	 */
	protected Subscription onStatusChange(Runnable action) {
		return theStatusChangeListeners.add(action, true)::run;
	}

	/**
	 * Marks this operation as {@link org.observe.entity.ObservableEntityResult.ResultStatus#FAILED failed})
	 *
	 * @param failure The failure exception
	 * @return This operation
	 */
	public AbstractOperationResult<E> failed(EntityOperationException failure) {
		theFailure = failure;
		updateStatus(status -> ResultStatus.FAILED);
		return this;
	}

	/**
	 * Marks this operation as {@link org.observe.entity.ObservableEntityResult.ResultStatus#FULFILLED fulfilled})
	 *
	 * @return This operation
	 */
	protected AbstractOperationResult<E> fulfilled() {
		updateStatus(status -> isDisposed ? ResultStatus.DISPOSED : ResultStatus.FULFILLED);
		return this;
	}
}
