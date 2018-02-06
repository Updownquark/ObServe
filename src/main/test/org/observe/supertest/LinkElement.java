package org.observe.supertest;

import org.qommons.collect.BetterList;
import org.qommons.collect.ElementId;

public final class LinkElement implements Comparable<LinkElement> {
	private final BetterList<?> theLinkElementList;
	private final ElementId theLinkElement;
	private final ElementId theCollectionElement;

	public LinkElement(BetterList<?> list, ElementId element, ElementId collectionElement) {
		theLinkElementList = list;
		theLinkElement = element;
		theCollectionElement = collectionElement;
	}

	@Override
	public int compareTo(LinkElement o) {
		return theLinkElement.compareTo(o.theLinkElement);
	}

	public boolean isPresent() {
		return theCollectionElement.isPresent();
	}

	public int getIndex() {
		if (!theLinkElement.isPresent())
			throw new IllegalStateException("Cannot be called once removed");
		return theLinkElementList.getElementsBefore(theLinkElement);
	}

	@Override
	public String toString() {
		return theLinkElement.isPresent() ? ("[" + getIndex() + "]") : "removed";
	}
}
