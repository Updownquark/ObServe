package org.observe.supertest.dev2.links;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.observe.supertest.dev2.ObservableCollectionLink;
import org.observe.supertest.dev2.TestValueType;
import org.qommons.TestHelper;

public class SortedCollectionLink<T> extends ObservableCollectionLink<T, T> {
	private static final Map<TestValueType, List<? extends Comparator<?>>> COMPARATORS;

	static {
		COMPARATORS = new HashMap<>();
		for (TestValueType type : TestValueType.values()) {
			switch (type) {
			case INT:
				COMPARATORS.put(type, Arrays.asList((Comparator<Integer>) Integer::compareTo));
				break;
			case DOUBLE:
				COMPARATORS.put(type, Arrays.asList((Comparator<Double>) Double::compareTo));
				break;
			case STRING:
				COMPARATORS.put(type, Arrays.asList((Comparator<String>) String::compareTo));
			}
		}
	}

	public static <E> Comparator<E> compare(TestValueType type, TestHelper helper) {
		List<Comparator<E>> typeCompares = (List<Comparator<E>>) COMPARATORS.get(type);
		return typeCompares.get(helper.getInt(0, typeCompares.size()));
	}
}
