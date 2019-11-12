package org.observe.util.swing;

import java.text.ParseException;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.collect.ObservableCollection;
import org.observe.util.TypeTokens;
import org.qommons.Named;
import org.qommons.StringUtils;
import org.qommons.TimeUtils;
import org.qommons.TimeUtils.ParsedTime;
import org.qommons.io.Format;

import com.google.common.reflect.TypeToken;

public interface ListFilter {
	public interface ValueRenderer<E> extends Named {
		boolean searchGeneral();

		CharSequence render(E value);
	}

	public static class FilteredValue<E> implements Comparable<FilteredValue<?>> {
		E value;
		int[][][] matches;
		boolean isTrivial;

		public FilteredValue(E value, int columns) {
			this.value = value;
			matches = new int[columns][][];
		}

		void columnsChanged(int columns) {
			if (matches.length != columns)
				matches = new int[columns][][];
		}

		public E getValue() {
			return value;
		}

		public void setValue(E value) {
			this.value = value;
		}

		public boolean hasMatch() {
			if (matches == null)
				return true;
			for (int c = 0; c < matches.length; c++)
				if (matches(c))
					return true;
			return false;
		}

		public boolean isTrivial() {
			return isTrivial;
		}

		public int getColumns() {
			return matches.length;
		}

		public boolean matches(int column) {
			return matches[column] != null && matches[column].length > 0;
		}

		public int[][] getMatches(int column) {
			return matches[column];
		}

		@Override
		public int compareTo(FilteredValue<?> o) {
			int thisMin = -1;
			int thisCount = 0;
			for (int[][] cm : matches) {
				if (cm == null)
					continue;
				for (int[] m : cm) {
					if (thisMin < 0 || m[0] < thisMin)
						thisMin = m[0];
					thisCount++;
				}
			}
			int thatMin = -1;
			int thatCount = 0;
			for (int[][] cm : o.matches) {
				if (cm == null)
					continue;
				for (int[] m : cm) {
					if (thisCount == 0)
						return 1;
					if (thatMin < 0 || m[0] < thatMin)
						thatMin = m[0];
					thatCount++;
				}
			}
			int comp = -Integer.compare(thisCount, thatCount);
			if (comp == 0)
				comp = Integer.compare(thisMin, thatMin);
			return comp;
		}
	}

	int[][] findMatches(ValueRenderer<?> category, CharSequence text);

	default boolean isTrivial() {
		return false;
	}

	public static final Pattern INT_RANGE_PATTERN = Pattern.compile("(?<i1>\\d+)\\-(?<i2>\\d+)");

	public static final Format<ListFilter> FORMAT = new Format<ListFilter>() {
		@Override
		public void append(StringBuilder text, ListFilter value) {
			text.append(value.toString());
		}

		@Override
		public ListFilter parse(CharSequence filterText) throws ParseException {
			return ListFilter.parseFilterText(filterText);
		}
	};

	public static ListFilter parseFilterText(CharSequence filterText) {
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
			splitFilter[i] = _parseFilterElement(split[i]);
			int colonIndex = split[i].indexOf(':');
			if (colonIndex >= 0 && colonIndex < split[i].length() - 1) {
				CategoryFilter catFilter = new CategoryFilter(split[i].substring(0, colonIndex),
					_parseFilterElement(split[i].substring(colonIndex + 1)));
				splitFilter[i] = new OrFilter(catFilter, splitFilter[i]);
			}
		}
		if (splitFilter.length == 1)
			return splitFilter[0];
		else
			return new OrFilter(splitFilter);
	}

	public static ListFilter _parseFilterElement(String filterText) {
		ListFilter filter = new SimpleFilter(filterText);
		Matcher m = INT_RANGE_PATTERN.matcher(filterText);
		if (m.matches())
			filter = new OrFilter(new IntRangeFilter(m.group("i1"), m.group("i2")), filter);
		ParsedTime time;
		try {
			time = TimeUtils.parseFlexFormatTime(filterText, false, false);
		} catch (ParseException e) {
			throw new IllegalStateException(e); // Shouldn't happen
		}
		if (time != null) {
			if (time.toString().length() == filterText.length())
				filter = new OrFilter(new DateFilter(time), filter);
			else if (filterText.charAt(time.toString().length()) == '-') {
				ParsedTime maxTime;
				try {
					maxTime = TimeUtils.parseFlexFormatTime(filterText.substring(time.toString().length() + 1), true, false);
				} catch (ParseException e) {
					throw new IllegalStateException(e); // Shouldn't happen
				}
				if (maxTime != null)
					filter = new OrFilter(new DateRangeFilter(time, maxTime), filter);
			}
		}
		return filter;
	}

	public static <E> ObservableCollection<FilteredValue<E>> applyFilterText(ObservableCollection<E> values,
		Supplier<? extends Collection<? extends ValueRenderer<? super E>>> render, ObservableValue<? extends CharSequence> filter,
			Observable<?> until) {
		return applyFilter(values, render, filter.map(ListFilter::parseFilterText), until);
	}

	public static <E> ObservableCollection<FilteredValue<E>> applyFilter(ObservableCollection<E> values,
		Supplier<? extends Collection<? extends ValueRenderer<? super E>>> render, ObservableValue<? extends ListFilter> filter,
			Observable<?> until) {
		return values.flow().combine((TypeToken<FilteredValue<E>>) (TypeToken<?>) TypeTokens.get().of(FilteredValue.class), //
			combine -> combine.with(filter).build((cv, old) -> {
				Collection<? extends ValueRenderer<? super E>> renders = render.get();
				FilteredValue<E> v;
				if (old != null) {
					v = old;
					v.setValue(cv.getElement());
					v.columnsChanged(renders.size());
				} else
					v = new FilteredValue<>(cv.getElement(), renders.size());
				ListFilter f = cv.get(filter);
				int i = 0;
				boolean trivial = f.isTrivial();
				for (ValueRenderer<? super E> r : renders) {
					v.matches[i++] = f.findMatches(r, r.render(v.value));
					v.isTrivial = trivial;
				}
				return v;
			})).filter(fv -> fv.hasMatch() ? null : "No match").sorted(FilteredValue::compareTo).collectActive(until);
	}

	public static final ListFilter INCLUDE_ALL = new ListFilter() {
		@Override
		public int[][] findMatches(ValueRenderer<?> category, CharSequence value) {
			return new int[][] { new int[] { 0, value.length() } };
		}

		@Override
		public boolean isTrivial() {
			return true;
		}

		@Override
		public String toString() {
			return "";
		}
	};

	public static class SimpleFilter implements ListFilter {
		private final String theMatcher;

		public SimpleFilter(String matcher) {
			theMatcher = matcher;
		}

		@Override
		public int[][] findMatches(ValueRenderer<?> category, CharSequence toSearch) {
			if (!category.searchGeneral())
				return null;
			int c, textIdx;
			List<int[]> matches = null;
			for (c = 0, textIdx = 0; c < toSearch.length(); c++) {
				if (Character.toLowerCase(toSearch.charAt(c)) == theMatcher.charAt(textIdx))
					textIdx++;
				else
					textIdx = 0;
				if (textIdx == theMatcher.length()) {
					if (matches == null)
						matches = new LinkedList<>();
					matches.add(new int[] { c - textIdx, c });
					textIdx = 0;
				}
			}
			return matches == null ? null : matches.toArray(new int[matches.size()][]);
		}

		@Override
		public String toString() {
			return theMatcher;
		}
	}

	public static class CategoryFilter implements ListFilter {
		static class UnfilteredRenderer<E> implements ValueRenderer<E> {
			final ValueRenderer<E> renderer;

			UnfilteredRenderer(ValueRenderer<E> renderer) {
				this.renderer = renderer;
			}

			@Override
			public String getName() {
				return renderer.getName();
			}

			@Override
			public boolean searchGeneral() {
				return true;
			}

			@Override
			public CharSequence render(E value) {
				return renderer.render(value);
			}
		}

		private final String theCategory;
		private final ListFilter theFilter;

		public CategoryFilter(String category, ListFilter filter) {
			theCategory = category;
			theFilter = filter;
		}

		@Override
		public int[][] findMatches(ValueRenderer<?> category, CharSequence text) {
			if (!theCategory.equalsIgnoreCase(category.getName()))
				return null;
			return theFilter.findMatches(new UnfilteredRenderer<>(category), text);
		}
	}

	public static class IntRangeFilter implements ListFilter {
		private final String theLow;
		private final String theHigh;

		public IntRangeFilter(String low, String high) {
			int comp = StringUtils.compareNumberTolerant(low, high, true, true);
			theLow = comp <= 0 ? low : high;
			theHigh = comp <= 0 ? high : low;
		}

		@Override
		public int[][] findMatches(ValueRenderer<?> category, CharSequence toSearch) {
			if (!category.searchGeneral())
				return null;
			List<int[]> matches = null;
			int digitStart = -1;
			for (int c = 0; c < toSearch.length(); c++) {
				if (Character.isDigit(toSearch.charAt(c))) {
					if (digitStart < 0)
						digitStart = c;
				} else if (digitStart >= 0) {
					if (isIncluded(toSearch, digitStart, c)) {
						if (matches == null)
							matches = new LinkedList<>();
						matches.add(new int[] { digitStart, c });
					}
					digitStart = -1;
				}
			}
			if (digitStart >= 0 && isIncluded(toSearch, digitStart, toSearch.length())) {
				if (matches == null)
					matches = new LinkedList<>();
				matches.add(new int[] { digitStart, toSearch.length() });
			}
			return matches == null ? null : matches.toArray(new int[matches.size()][]);
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

	public static class DateFilter implements ListFilter {
		private final TimeUtils.ParsedTime theTime;

		public DateFilter(ParsedTime time) {
			theTime = time;
		}

		@Override
		public int[][] findMatches(ValueRenderer<?> category, CharSequence text) {
			if (!category.searchGeneral())
				return null;
			List<int[]> matches = null;
			for (int i = 0; i < text.length();) {
				if (Character.isDigit(text.charAt(i))) {
					TimeUtils.ParsedTime time;
					try {
						time = TimeUtils.parseFlexFormatTime(text.subSequence(i, text.length()), false, false);
					} catch (ParseException e) {
						throw new IllegalStateException();
					}
					if (time != null && theTime.isComparable(time) && theTime.compareTo(time) == 0) {
						if (matches == null)
							matches = new LinkedList<>();
						matches.add(new int[] { i, i + time.toString().length() });
						i += time.toString().length();
					} else
						i++;
				} else
					i++;
			}
			return matches == null ? null : matches.toArray(new int[matches.size()][]);
		}
	}

	public static class DateRangeFilter implements ListFilter {
		private final TimeUtils.ParsedTime theMinTime;
		private final TimeUtils.ParsedTime theMaxTime;

		public DateRangeFilter(ParsedTime minTime, ParsedTime maxTime) {
			theMinTime = minTime;
			theMaxTime = maxTime;
		}

		@Override
		public int[][] findMatches(ValueRenderer<?> category, CharSequence text) {
			if (!category.searchGeneral())
				return null;
			List<int[]> matches = null;
			for (int i = 0; i < text.length();) {
				if (Character.isDigit(text.charAt(i))) {
					TimeUtils.ParsedTime time;
					try {
						time = TimeUtils.parseFlexFormatTime(text.subSequence(i, text.length()), false, false);
					} catch (ParseException e) {
						throw new IllegalStateException();
					}
					if (time != null && theMinTime.isComparable(time) && theMinTime.compareTo(time) <= 0
						&& theMaxTime.compareTo(time) >= 0) {
						if (matches == null)
							matches = new LinkedList<>();
						matches.add(new int[] { i, i + time.toString().length() });
						i += time.toString().length();
					} else
						i++;
				} else
					i++;
			}
			return matches == null ? null : matches.toArray(new int[matches.size()][]);
		}
	}

	public static class OrFilter implements ListFilter {
		private final ListFilter[] theFilters;

		public OrFilter(ListFilter... filters) {
			theFilters = filters;
		}

		@Override
		public int[][] findMatches(ValueRenderer<?> category, CharSequence value) {
			List<int[]> matches = null;
			for (ListFilter filter : theFilters) {
				int[][] match = filter.findMatches(category, value);
				if (match != null && match.length > 0) {
					if (matches == null)
						matches = new LinkedList<>();
					for (int[] m : match)
						matches.add(m);
				}
			}
			if (matches == null)
				return null;
			int[][] ret = matches.toArray(new int[matches.size()][]);
			Arrays.sort(ret, (m1, m2) -> Integer.compare(m1[0], m2[0]));
			return ret;
		}

		@Override
		public boolean isTrivial() {
			for (ListFilter f : theFilters)
				if (!f.isTrivial())
					return false;
			return true;
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
