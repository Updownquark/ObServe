package org.observe.supertest;

import java.util.ArrayList;
import java.util.List;

import org.qommons.TestHelper;

/**
 * {@link ObservableChainLink} implementation that takes care of a few low-level things
 * 
 * @param <S> The type of the source link
 * @param <T> The type of this link
 */
public abstract class AbstractChainLink<S, T> implements ObservableChainLink<S, T> {
	private final String thePath;
	private final ObservableChainLink<?, S> theSourceLink;
	private final List<? extends ObservableChainLink<T, ?>> theDerivedLinks;
	private final int theSiblingIndex;

	/**
	 * @param path The path for this link
	 * @param sourceLink The source for this link
	 */
	public AbstractChainLink(String path, ObservableChainLink<?, S> sourceLink) {
		thePath = path;
		theSourceLink = sourceLink;
		theDerivedLinks = new ArrayList<>();
		theSiblingIndex = theSourceLink == null ? -1 : theSourceLink.getDerivedLinks().size(); // Assume we'll be added at the end
	}

	@Override
	public String getPath() {
		return thePath;
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
	public void initialize(TestHelper helper) {
		for (ObservableChainLink<T, ?> derived : theDerivedLinks)
			derived.initialize(helper);
	}

	@Override
	public int getModSet() {
		return getSourceLink().getModSet();
	}

	@Override
	public int getModification() {
		return getSourceLink().getModification();
	}

	@Override
	public int getOverallModification() {
		return getSourceLink().getOverallModification();
	}

	@Override
	public void setModification(int modSet, int modification, int overall) {
		((AbstractChainLink<?, S>) getSourceLink()).setModification(modSet, modification, overall);
	}

	@Override
	public abstract String toString();
}