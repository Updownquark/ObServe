package org.observe.entity;

/**
 * <p>
 * An entity result that keeps its value up-to-date with changes to the entity set.
 * </p>
 * <p>
 * An updating result may be {@link #dispose() disposed} explicitly or garbage-collected if unused. The {@link #cancel(boolean)} method has
 * identical functionality to {@link #dispose()} after the result is fulfilled.
 * </p>
 * <p>
 * A disposed result's content will remain valid as of the moment {@link #dispose()} was called. If {@link #dispose()} is called before the
 * result is fulfilled, fulfillment will not be cancelled, but the result will not be kept up-to-date.
 * </p>
 *
 * @param <E> The entity type of the result
 */
public interface EntityQueryResult<E> extends ObservableEntityResult<E>, AutoCloseable {
	/**
	 * Terminates the updating of this result as the entity set changes
	 *
	 * @return This result
	 */
	EntityQueryResult<E> dispose();

	@Override
	default void close() {
		dispose();
	}
}
