package org.observe.entity;

public interface ConfigurableQuery<E> extends ConfigurableOperation<E>, EntityQuery<E> {
	@Override
	ConfigurableQuery<E> loadField(ObservableEntityFieldType<E, ?> field, FieldLoadType type);

	@Override
	ConfigurableQuery<E> loadAllFields(FieldLoadType type);

	/**
	 * Affects the order of entities returned by the query. The last order operation takes priority.
	 *
	 * @param value The value to order by
	 * @param ascending Whether to order entities by the ascending or descending order of the given value
	 * @return A copy of this query whose results will be ordered as specified
	 */
	ConfigurableQuery<E> orderBy(EntityValueAccess<E, ?> value, boolean ascending);

	@Override
	PreparedQuery<E> prepare() throws IllegalStateException, EntityOperationException;
}
