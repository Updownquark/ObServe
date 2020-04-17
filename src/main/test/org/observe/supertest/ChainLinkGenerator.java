package org.observe.supertest;

import org.qommons.TestHelper;

/** A generator capable of making new {@link ObservableChainLink}s */
public interface ChainLinkGenerator {
	/**
	 * @param <T> The type of the source link
	 * @param sourceLink The source link to generate a derived link for
	 * @return A measure of the variety of derived links this generator is able to create with the given source link
	 */
	<T> double getAffinity(ObservableChainLink<?, T> sourceLink);

	/**
	 * @param <T> The type of the source link
	 * @param <X> The type of the link to create
	 * @param path The path for the new link
	 * @param sourceLink The source link to create the derived link for
	 * @param helper The source of randomness to use creating the link
	 * @return The new derived link
	 */
	<T, X> ObservableChainLink<T, X> deriveLink(String path, ObservableChainLink<?, T> sourceLink, TestHelper helper);
}