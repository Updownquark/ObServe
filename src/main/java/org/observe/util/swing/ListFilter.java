package org.observe.util.swing;

import java.text.ParseException;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.observe.ObservableValue;
import org.observe.collect.ObservableCollection;
import org.observe.util.TypeTokens;
import org.qommons.io.Format;

import com.google.common.reflect.TypeToken;

public abstract class ListFilter implements ToIntFunction<CharSequence> {
	public static final Pattern INT_RANGE_PATTERN = Pattern.compile("(?<i1>\\d)\\-(?<i2>\\d)");

	public static final Format<ListFilter> FORMAT = new Format<ListFilter>() {
		@Override
		public void append(StringBuilder text, ListFilter value) {
			text.append(value.toString());
		}

		@Override
		public ListFilter parse(CharSequence filterText) throws ParseException {
			if (filterText == null || filterText.length() == 0)
				return INCLUDE_ALL;
			List<String> splitList = new LinkedList<>();
			int contentStart = -1;
			for (int c = 0; c < filterText.length(); c++) {
				if (Character.isWhitespace(filterText.charAt(c))) {
					if (contentStart >= 0) {
						splitList.add(filterText.subSequence(contentStart, c).toString().toLowerCase());
						contentStart -= 1;
					}
				} else if (contentStart < 0)
					contentStart = c;
			}
			if (contentStart == 0)
				splitList.add(filterText.toString().toLowerCase());
			else if (contentStart >= 0)
				splitList.add(filterText.subSequence(contentStart, filterText.length()).toString().toLowerCase());
			else
				splitList.add(filterText.toString()); // If all the text is whitespace, then search for whitespace
			String[] split = splitList.toArray(new String[splitList.size()]);
			ListFilter[] splitFilter = new ListFilter[split.length];
			for (int i = 0; i < split.length; i++) {
				Matcher m = INT_RANGE_PATTERN.matcher(split[i]);
				if (m.matches())
					splitFilter[i] = new RangeFilter(m.group("i1"), m.group("i2"));
				else
					splitFilter[i] = new SimpleFilter(split[i]);
			}
			if (splitFilter.length == 1)
				return splitFilter[0];
			else
				return new OrFilter(splitFilter);
		}
	};

	private static final class FilteredValue<E> {
		E value;
		int matchIndex;

		FilteredValue(E value, int matchIndex) {
			this.value = value;
			this.matchIndex = matchIndex;
		}
	}

	public static <E> ObservableCollection<E> filterBy(ObservableCollection<E> values,
		Function<? super E, ? extends Iterable<? extends CharSequence>> render, ObservableValue<? extends ListFilter> filter) {
		return values.flow().combine((TypeToken<FilteredValue<E>>) (TypeToken<?>) TypeTokens.get().of(FilteredValue.class), //
			combine -> combine.with(filter).build((cv, old) -> {
				if (old == null) {
					old.value = cv.getElement();
					old.matchIndex = cv.get(filter).applyAsInt(cv.getElement());
					return old;
				} else
					return new FilteredValue<>(cv.getElement(), cv.get(filter).applyAsInt(cv.getElement()));
			})).filter(fv -> fv.matchIndex >= 0 ? null : "No match").map(values.getType(), fv -> fv.value).collect();
	}

	public static final ListFilter INCLUDE_ALL = new ListFilter() {
		@Override
		public int applyAsInt(CharSequence value) {
			return 0;
		}
	};

	public static class SimpleFilter extends ListFilter {
		private final String theMatcher;

		public SimpleFilter(String matcher) {
			theMatcher = matcher;
		}

		@Override
		public int applyAsInt(CharSequence toSearch) {
			int c, textIdx;
			for (c = 0, textIdx = 0; c < toSearch.length() && textIdx < theMatcher.length(); c++) {
				if (Character.toLowerCase(toSearch.charAt(c)) == theMatcher.charAt(textIdx))
					textIdx++;
			}
			if (textIdx == theMatcher.length())
				return c - textIdx;
			else
				return -1;
		}

		@Override
		public String toString() {
			return theMatcher;
		}
	}

	public static class RangeFilter extends ListFilter {
		private final String theLow;
		private final String theHigh;

		public RangeFilter(String low, String high) {
			int comp = low.compareTo(high);
			theLow = comp <= 0 ? low : high;
			theHigh = comp <= 0 ? high : low;
		}

		@Override
		public int applyAsInt(CharSequence toSearch) {
			int digitStart = -1;
			for (int c = 0; c < toSearch.length(); c++) {
				if (Character.isDigit(toSearch.charAt(c))) {
					if (digitStart < 0)
						digitStart = c;
				} else if (digitStart >= 0) {
					if (isIncluded(toSearch, digitStart, c))
						return digitStart;
					digitStart = -1;
				}
			}
			if (digitStart >= 0 && isIncluded(toSearch, digitStart, toSearch.length()))
				return digitStart;
			else
				return -1;
		}

		private boolean isIncluded(CharSequence toSearch, int start, int end) {
			int digLen = end - start;
			if (digLen < theLow.length() || digLen > theHigh.length())
				return false;
			boolean included = true;
			if (digLen == theLow.length()) {
				for (int i = start; i < end; i++) {
					int comp = toSearch.charAt(i) - theLow.charAt(i - start);
					if (comp < 0) {
						included = false;
						break;
					} else if (comp > 0)
						break;
				}
			}
			if (included && digLen == theHigh.length()) {
				for (int i = start; i < end; i++) {
					int comp = toSearch.charAt(i) - theHigh.charAt(i - start);
					if (comp > 0) {
						included = false;
						break;
					} else if (comp < 0)
						break;
				}
			}
			return included;
		}

		@Override
		public String toString() {
			return new StringBuilder(theLow).append('-').append(theHigh).toString();
		}
	}

	public static class OrFilter extends ListFilter {
		private final ListFilter[] theFilters;

		public OrFilter(ListFilter[] filters) {
			theFilters = filters;
		}

		@Override
		public int applyAsInt(CharSequence value) {
			int minIndex = -1;
			for (ListFilter filter : theFilters) {
				int index = filter.applyAsInt(value);
				if (index >= 0 && (minIndex < 0 || index < minIndex))
					minIndex = index;
			}
			return minIndex;
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder();
			for (int i = 0; i < theFilters.length; i++) {
				if (i > 0)
					str.append(' ');
				str.append(theFilters[i]);
			}
			return str.toString();
		}
	}
}
