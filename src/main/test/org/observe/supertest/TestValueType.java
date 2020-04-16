package org.observe.supertest;

import org.observe.util.TypeTokens;

import com.google.common.reflect.TypeToken;

/** A type of values in an observable structure to be tested with {@link ObservableChainTester} */
public enum TestValueType {
	/** Integer values */
	INT(TypeTokens.get().INT),
	/** Double values */
	DOUBLE(TypeTokens.get().DOUBLE),
	/** String values */
	STRING(TypeTokens.get().STRING),
	/** Boolean values */
	BOOLEAN(TypeTokens.get().BOOLEAN);

	private final TypeToken<?> type;

	private TestValueType(TypeToken<?> type) {
		this.type = type;
	}

	/** @return The type token associated with this test type */
	public TypeToken<?> getType() {
		return type;
	}
}