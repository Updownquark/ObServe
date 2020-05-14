package org.observe.supertest.collect;

import org.observe.supertest.TestValueType;
import org.observe.supertest.value.ObservableValueLink;

/**
 * An {@link ObservableValueLink} whose source is an {@link ObservableCollectionLink}
 *
 * @param <S> The type of values in the source collection
 * @param <T> The type of this value
 */
public abstract class CollectionDerivedValue<S, T> extends ObservableValueLink<S, T> implements CollectionSourcedLink<S, T> {
	/**
	 * @param path The path for this link
	 * @param sourceLink The source collection link
	 * @param type The type of this link
	 * @param checkOldValues Whether this link should check event old values against the previous value
	 */
	public CollectionDerivedValue(String path, ObservableCollectionLink<?, S> sourceLink, TestValueType type, boolean checkOldValues) {
		super(path, sourceLink, type, checkOldValues);
	}

	@Override
	public ObservableCollectionLink<?, S> getSourceLink() {
		return (ObservableCollectionLink<?, S>) super.getSourceLink();
	}
}
