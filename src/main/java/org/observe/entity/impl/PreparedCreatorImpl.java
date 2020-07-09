package org.observe.entity.impl;

import org.observe.entity.PreparedCreator;
import org.qommons.collect.QuickSet.QuickMap;

class PreparedCreatorImpl<E, E2 extends E> extends AbstractPreparedOperation<E2, PreparedCreatorImpl<E, E2>>
implements PreparedCreator<E, E2>, EntityCreatorHelper<E, E2> {
	private final QueryResults<E> theQuery;

	PreparedCreatorImpl(ConfigurableCreatorImpl<?, E2> definition, Object preparedObject, QuickMap<String, Object> variableValues,
		QueryResults<E> query) {
		super(definition, preparedObject, variableValues);
		theQuery = query;
	}

	@Override
	public QueryResults<E> getQuery() {
		return theQuery;
	}

	@Override
	public ConfigurableCreatorImpl<E, E2> getDefinition() {
		return (ConfigurableCreatorImpl<E, E2>) super.getDefinition();
	}

	@Override
	protected PreparedCreatorImpl<E, E2> copy(QuickMap<String, Object> variableValues) {
		return new PreparedCreatorImpl<>(getDefinition(), getPreparedObject(), variableValues, theQuery);
	}
}
