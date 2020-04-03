package org.observe.supertest.dev2;

import java.util.ArrayList;
import java.util.List;

import org.qommons.TestHelper;

public abstract class AbstractChainLink<S, T> implements ObservableChainLink<S, T> {
	private final ObservableChainLink<?, S> theSourceLink;
	private final List<? extends ObservableChainLink<T, ?>> theDerivedLinks;
	private int theSiblingIndex = -1;

	public AbstractChainLink(ObservableChainLink<?, S> sourceLink) {
		theSourceLink = sourceLink;
		theDerivedLinks = new ArrayList<>();
	}

	@Override
	public void initialize(TestHelper helper) {
		if (theSourceLink == null)
			theSiblingIndex = 0;
		else
			theSiblingIndex = theSourceLink.getDerivedLinks().indexOf(this);
	}

	@Override
	public ObservableChainLink<?, S> getSourceLink() {
		return theSourceLink;
	}

	@Override
	public List<? extends ObservableChainLink<T, ?>> getDerivedLinks() {
		return theDerivedLinks;
	}

	@Override
	public int getSiblingIndex() {
		return theSiblingIndex;
	}

	@Override
	public <X> void derive(TestHelper helper, int maxLinkCount) {
		// Go for length, not breadth
		if (helper.getBoolean(.25)) {
			return;
		}
		int derivedCount;
		if (maxLinkCount == 1)
			derivedCount = 1;
		else
			derivedCount = (int) Math.round(helper.getDouble(1, 1 + maxLinkCount / 5.0, maxLinkCount));
		maxLinkCount -= derivedCount;
		for (int i = 0; i < derivedCount; i++) {
			ObservableChainLink<T, X> derived = deriveOne(helper);
			if (derived == null)
				break; // The link can choose not to derive
			((List<ObservableChainLink<T, X>>) theDerivedLinks).add(derived);
			derived.initialize(helper);
			if (maxLinkCount > 0)
				derived.derive(helper, maxLinkCount);
			maxLinkCount -= derived.getLinkCount() - 1;
		}
	}

	protected abstract <X> ObservableChainLink<T, X> deriveOne(TestHelper helper);
}
