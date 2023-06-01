package org.observe.supertest;

import org.observe.supertest.collect.ObservableCollectionLink;
import org.qommons.testing.TestHelper;

/** A generator capable of making new {@link ObservableChainLink}s */
public interface ChainLinkGenerator {
	/**
	 * @param <T> The type of the source link
	 * @param sourceLink The source link to generate a derived link for
	 * @param targetType The type of the link to generate, or null if unspecified
	 * @return A measure of the variety of derived links this generator is able to create with the given source link
	 */
	<T> double getAffinity(ObservableChainLink<?, T> sourceLink, TestValueType targetType);

	/**
	 * @param <T> The type of the source link
	 * @param <X> The type of the link to create
	 * @param path The path for the new link
	 * @param sourceLink The source link to create the derived link for
	 * @param targetType The type of the link to generate, or null if unspecified
	 * @param helper The source of randomness to use creating the link
	 * @return The new derived link
	 */
	<T, X> ObservableChainLink<T, X> deriveLink(String path, ObservableChainLink<?, T> sourceLink, TestValueType targetType,
		TestHelper helper);

	/** A sub-type of {@link ChainLinkGenerator} that generates {@link ObservableCollectionLink}s */
	public interface CollectionLinkGenerator extends ChainLinkGenerator {
		@Override
		<T, X> ObservableCollectionLink<T, X> deriveLink(String path, ObservableChainLink<?, T> sourceLink, TestValueType targetType,
			TestHelper helper);
	}
}
