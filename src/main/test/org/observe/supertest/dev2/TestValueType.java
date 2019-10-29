package org.observe.supertest.dev2;

import org.qommons.TestHelper;
import org.qommons.ValueHolder;

import com.google.common.reflect.TypeToken;

public enum TestValueType {
	INT(TypeToken.of(int.class)), DOUBLE(TypeToken.of(double.class)), STRING(TypeToken.of(String.class));
	// TODO Add an array type for each

	private final TypeToken<?> type;

	private TestValueType(TypeToken<?> type) {
		this.type = type;
	}

	public TypeToken<?> getType() {
		return type;
	}

	public static TestValueType nextType(TestHelper helper) {
		// The DOUBLE type is much less performant. There may be some value, but we'll use it less often.
		ValueHolder<TestValueType> result = new ValueHolder<>();
		TestHelper.RandomAction action = helper.createAction();
		action.or(10, () -> result.accept(TestValueType.INT));
		action.or(5, () -> result.accept(TestValueType.STRING));
		action.or(2, () -> result.accept(TestValueType.DOUBLE));
		action.execute(null);
		return result.get();
		// return TestValueType.values()[helper.getInt(0, TestValueType.values().length)];
	}
}