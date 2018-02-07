package org.observe.supertest;

import org.qommons.collect.BetterList;
import org.qommons.collect.ElementId;

public final class LinkElement implements Comparable<LinkElement> {
	private final BetterList<?> theLinkElementList;
	private final ElementId theLinkElement;
	private final BetterList<?> theCollection;
	private final ElementId theCollectionElement;

	public LinkElement(BetterList<?> list, ElementId element, BetterList<?> collection, ElementId collectionElement) {
		theLinkElementList = list;
		theLinkElement = element;
		theCollection = collection;
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

	public int getObservableIndex() {
		if (!theCollectionElement.isPresent())
			return -1;
		return theCollection.getElementsBefore(theCollectionElement);
	}

	@Override
	public String toString() {
		StringBuilder str = new StringBuilder();
		str.append('[');
		str.append(theLinkElement.isPresent() ? "" + getIndex() : "*");
		str.append('/');
		str.append(theCollectionElement.isPresent() ? "" + getObservableIndex() : "*");
		str.append(']');
		return str.toString();
	}
}
