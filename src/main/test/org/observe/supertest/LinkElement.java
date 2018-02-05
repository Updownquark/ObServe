package org.observe.supertest;

import org.qommons.collect.BetterList;
import org.qommons.collect.ElementId;

public final class LinkElement {
	private final BetterList<?> theList;
	private final ElementId theElement;

	public LinkElement(BetterList<?> list, ElementId element) {
		theList = list;
		theElement = element;
	}

	public int getIndex() {
		if (!theElement.isPresent())
			throw new IllegalStateException("Cannot be called once removed");
		return theList.getElementsBefore(theElement);
	}

	@Override
	public String toString() {
		return theElement.isPresent() ? ("[" + getIndex() + "]") : "removed";
	}
}
