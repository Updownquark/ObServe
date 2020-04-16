package org.observe.supertest;

import org.observe.util.TypeTokens;

import com.google.common.reflect.TypeToken;

public enum TestValueType {
	INT(TypeTokens.get().INT), DOUBLE(TypeTokens.get().DOUBLE), STRING(TypeTokens.get().STRING), BOOLEAN(TypeTokens.get().BOOLEAN);
	// TODO Add an array type for each

	private final TypeToken<?> type;

	private TestValueType(TypeToken<?> type) {
		this.type = type;
	}

	public TypeToken<?> getType() {
		return type;
	}
}