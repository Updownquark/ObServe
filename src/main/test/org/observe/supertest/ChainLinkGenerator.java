package org.observe.supertest;

import org.qommons.TestHelper;

public interface ChainLinkGenerator {
	<T> double getAffinity(ObservableChainLink<?, T> sourceLink);

	<T, X> ObservableChainLink<T, X> deriveLink(String path, ObservableChainLink<?, T> sourceLink, TestHelper helper);
}
