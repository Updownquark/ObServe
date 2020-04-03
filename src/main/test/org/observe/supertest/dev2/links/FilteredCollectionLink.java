package org.observe.supertest.dev2.links;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;

import org.observe.supertest.dev2.CollectionLinkElement;
import org.observe.supertest.dev2.ExpectedCollectionOperation;
import org.observe.supertest.dev2.ObservableCollectionLink;
import org.observe.supertest.dev2.ObservableCollectionTestDef;
import org.observe.supertest.dev2.TestValueType;
import org.qommons.TestHelper;

public class FilteredCollectionLink<T> extends ObservableCollectionLink<T, T> {
	public FilteredCollectionLink(ObservableCollectionLink<?, T> sourceLink, ObservableCollectionTestDef<T> def, TestHelper helper) {
		super(sourceLink, def, helper);
		// TODO Auto-generated constructor stub
	}

	@Override
	public void expectFromSource(ExpectedCollectionOperation<?, T> sourceOp) {
		// TODO Auto-generated method stub

	}

	@Override
	protected void validate(CollectionLinkElement<T, T> element) {
		// TODO Auto-generated method stub

	}

	@Override
	public void expect(ExpectedCollectionOperation<?, T> derivedOp, OperationRejection rejection, int derivedIndex) {
		// TODO Auto-generated method stub

	}

	@Override
	public CollectionLinkElement<T, T> expectAdd(T value, CollectionLinkElement<?, T> after, CollectionLinkElement<?, T> before,
		boolean first, OperationRejection rejection, int derivedIndex) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isAcceptable(T value) {
		// TODO Filter
		return getSourceLink().isAcceptable(value);
	}

	@Override
	public T getUpdateValue(T value) {
		return getSourceLink().getUpdateValue(value);
	}

	public static <T> Function<T, String> filterFor(TestValueType type, TestHelper helper) {
		List<Function<?, String>> typeFilters = FILTERS.get(type);
		return (Function<T, String>) typeFilters.get(helper.getInt(0, typeFilters.size()));
	}

	static final Map<TestValueType, List<Function<?, String>>> FILTERS;
	static {
		Map<TestValueType, List<Function<?, String>>> filters = new TreeMap<>();
		for (TestValueType type : TestValueType.values()) {
			List<Function<?, String>> typeFilters = new ArrayList<>();
			filters.put(type, Collections.unmodifiableList(typeFilters));
			switch (type) {
			case INT:
				typeFilters.add(filter((Integer i) -> i > 500, ">500 only"));
				typeFilters.add(filter((Integer i) -> i < 500, "<500 only"));
				typeFilters.add(filter((Integer i) -> i % 3 == 0, "x3 only"));
				typeFilters.add(filter((Integer i) -> i % 2 == 1, "odd only"));
				break;
			case DOUBLE:
				typeFilters.add(filter((Double d) -> d == Math.floor(d), "round numbers only"));
				typeFilters.add(filter((Double d) -> d != Math.floor(d), "decimal numbers only"));
				break;
			case STRING:
				typeFilters.add(filter((String s) -> s.length() <= 4, "length<=4 only"));
				typeFilters.add(filter((String s) -> s.length() == 0 ? false : s.charAt(0) % 2 == 0, "even first char only"));
				break;
			case BOOLEAN:
				typeFilters.add(filter((Boolean b) -> b, "true"));
				typeFilters.add(filter((Boolean b) -> b, "false"));
				break;
			}
		}
		FILTERS = Collections.unmodifiableMap(filters);
	}

	private static <T> Function<?, String> filter(Predicate<T> filter, String message) {
		return new Function<T, String>() {
			@Override
			public String apply(T value) {
				return filter.test(value) ? null : message;
			}

			@Override
			public String toString() {
				return message;
			}
		};
	}
}
