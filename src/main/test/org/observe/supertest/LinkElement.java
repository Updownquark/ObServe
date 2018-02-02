package org.observe.supertest;

import java.util.function.IntSupplier;

public final class LinkElement {
	private final IntSupplier index;

	LinkElement(IntSupplier idx) {
		index = idx;
	}

	public int getIndex() {
		return index.getAsInt();
	}
}
