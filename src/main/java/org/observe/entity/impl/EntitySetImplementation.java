package org.observe.entity.impl;

import org.observe.ObservableValue;
import org.observe.entity.ConfigurableOperation;
import org.observe.entity.EntityQuery;

public interface EntitySetImplementation {
	Object prepare(ConfigurableOperation<?> op);

	ObservableValue<Long> count(EntityQuery<?> query, Object prepared);
}
