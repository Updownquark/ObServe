package org.observe.supertest.value;

import org.observe.supertest.ObservableChainLink;

/**
 * An {@link ObservableChainLink} whose source is an {@link ObservableValueLink}
 *
 * @param <S> The type of the source link's value
 * @param <T> The type of this link
 */
public interface ValueSourcedLink<S, T> extends ObservableChainLink<S, T> {
	@Override
	public ObservableValueLink<?, S> getSourceLink();
}
