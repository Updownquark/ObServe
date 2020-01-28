package org.observe.entity.impl;

import org.observe.entity.ConfigurableDeletion;
import org.observe.entity.EntityOperationException;
import org.observe.entity.EntitySelection;
import org.observe.entity.PreparedDeletion;
import org.qommons.collect.QuickSet.QuickMap;

class ConfigurableDeletionImpl<E> extends AbstractConfigurableOperation<E> implements ConfigurableDeletion<E> {
	private final EntitySelection<E> theSelection;

	ConfigurableDeletionImpl(EntitySelection<E> selection) {
		super(selection.getEntityType(), QuickMap.of(selection.getVariables(), String::compareTo));
		theSelection = selection;
	}

	@Override
	public EntitySelection<E> getSelection() {
		return theSelection;
	}

	@Override
	public PreparedDeletion<E> prepare() throws IllegalStateException, EntityOperationException {
		return new PreparedDeletionImpl<>(this,
			((ObservableEntityDataSetImpl) getEntityType().getEntitySet()).getImplementation().prepare(this),
			getVariables().keySet().createMap(), theSelection);
	}

	@Override
	public long execute() throws IllegalStateException, EntityOperationException {
		if (getVariables().keySize() > 0)
			throw new IllegalStateException("This deletion has variables and must be prepared");
		return ((ObservableEntityTypeImpl<E>) getEntityType()).delete(this, null);
	}
}
