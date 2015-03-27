package org.observe.collect;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

public class CollectionChangeEvent<E> {
	public final CollectionChangeType type;
	public final Collection<E> values;

	public CollectionChangeEvent(CollectionChangeType aType, Collection<E> val) {
		type = aType;
		values = Collections.unmodifiableCollection(val);
	}

	public CollectionChangeEvent(CollectionChangeType aType, E... val) {
		type = aType;
		values = Collections.unmodifiableCollection(Arrays.asList(val));
	}
}
