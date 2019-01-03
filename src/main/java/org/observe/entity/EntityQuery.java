package org.observe.entity;

import org.observe.ObservableValue;
import org.observe.collect.ObservableSortedSet;

public interface EntityQuery<E> extends EntitySetOperation<E> {
	/**
	 * Affects the order of entities returned by the query. The last order operation takes priority.
	 *
	 * @param value The valueto order by
	 * @param ascending Whether to order entities by the ascending or descending order of the given value
	 * @return A copy of this query whose results will be ordered as specified
	 */
	EntityQuery<E> orderBy(EntityValueAccess<? super E, ?> value, boolean ascending);

	EntityQuery<E> withPreLoaded(ObservableEntityFieldType<E, ?> field, boolean preLoaded);

	EntityQuery<E> withAllPreLoaded(boolean preLoaded, boolean deep);

	@Override
	EntityQuery<E> prepare() throws IllegalStateException, EntityOperationException;

	@Override
	EntityQuery<E> satisfy(String variableName, Object value) throws IllegalStateException, IllegalArgumentException;

	/**
	 * Same as {@link #count()}.{@link ObservableValue#get() get()}
	 *
	 * @see org.observe.entity.EntitySetOperation#execute()
	 */
	@Override
	long execute() throws IllegalStateException, EntityOperationException;
	ObservableValue<Long> count() throws IllegalStateException, EntityOperationException;
	ObservableSortedSet<E> collect() throws IllegalStateException, EntityOperationException;
	ObservableSortedSet<ObservableEntity<? extends E>> collectObservable(boolean withUpdates)
		throws IllegalStateException, EntityOperationException;
	ObservableSortedSet<EntityIdentity<? super E>> collectIdentities() throws IllegalStateException, EntityOperationException;
}
