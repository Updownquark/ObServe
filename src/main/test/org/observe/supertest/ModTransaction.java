package org.observe.supertest;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

public class ModTransaction {
	private final ModTransaction theParent;
	private final ModTransaction theChild;
	private final Map<Object, Object> theValues;

	public ModTransaction(ModTransaction parent, Function<ModTransaction, ModTransaction> child) {
		theParent = parent;
		theChild = child.apply(this);
		theValues = new HashMap<>();
	}

	public <T> T get(Object key, Supplier<T> def) {
		return (T) theValues.computeIfAbsent(key, k -> def.get());
	}

	public ModTransaction getParent() {
		return theParent;
	}

	public ModTransaction getChild() {
		return theChild;
	}
}
