package org.observe.util.swing;

import static org.observe.util.swing.TableContentControl.tryParseDouble;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.collect.ObservableCollection;
import org.observe.util.TypeTokens;
import org.qommons.Named;
import org.qommons.StringUtils;
import org.qommons.TimeUtils;
import org.qommons.TimeUtils.ParsedTime;
import org.qommons.collect.BetterSortedSet;
import org.qommons.io.Format;
import org.qommons.tree.BetterTreeSet;

import com.google.common.reflect.TypeToken;

/**
 * A structure that may be parsed from a String and allows easy control of a table, including row filtering by many powerful and intuitive
 * patterns, row sorting, and column sorting.
 */
public interface TableContentControl {
	public static final String TABLE_CONTROL_TOOLTIP = "<html>Enter text, numbers, or dates to filter the table rows.<br>\n"//
		+ "Use <b>XXX-XXX</b> to enter a range of numbers or dates.<br>\n"//
		+ "Use <b>abc*efg</b> to match any text with \"efg\" occurring after \"abc\".<br>\n"//
		+ "Use <b>\\XXX</b> to search via regular expressions.<br>\n"//
		+ "Use <b>sort:column1,column2</b> to sort the table rows.<br>\n"//
		+ "Use <b>columns:column1,column2</b> to sort the table columns.\n"//
		+ "</html>";

	public interface ValueRenderer<E> extends Named, Comparator<E> {
		boolean searchGeneral();

		CharSequence render(E value);
	}

	public static class FilteredValue<E> implements Comparable<FilteredValue<?>> {
		E value;
		int[][][] matches;
		boolean isFiltered;

		public FilteredValue(E value, int columns) {
			this.value = value;
			matches = new int[columns][][];
		}

		void columnsChanged(int columns) {
			if (matches.length != columns)
				matches = new int[columns][][];
			else
				Arrays.fill(matches, null);
		}

		public E getValue() {
			return value;
		}

		public void setValue(E value) {
			this.value = value;
		}

		public boolean hasMatch() {
			if (!isFiltered || matches == null)
				return true;
			for (int c = 0; c < matches.length; c++)
				if (matches(c))
					return true;
			return false;
		}

		public boolean isFiltered() {
			return isFiltered;
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

	static int[][] NO_MATCH = new int[0][];

	int[][] findMatches(ValueRenderer<?> category, CharSequence text);

	List<String> getRowSorting();

	List<String> getColumnSorting();

	public static final Pattern INT_RANGE_PATTERN = Pattern.compile("(?<i1>\\d+)\\-(?<i2>\\d+)");

	public static final Format<TableContentControl> FORMAT = new Format<TableContentControl>() {
		@Override
		public void append(StringBuilder text, TableContentControl value) {
			text.append(value.toString());
		}

		@Override
		public TableContentControl parse(CharSequence controlText) throws ParseException {
			return TableContentControl.parseContentControl(controlText);
		}
	};

	public static TableContentControl parseContentControl(CharSequence controlText) {
		if (controlText == null || controlText.length() == 0)
			return DEFAULT;
		List<String> splitList = new LinkedList<>();
		int contentStart = -1;
		for (int c = 0; c < controlText.length(); c++) {
			if (Character.isWhitespace(controlText.charAt(c))) {
				if (contentStart >= 0) {
					splitList.add(controlText.subSequence(contentStart, c).toString().toLowerCase());
					contentStart -= 1;
				}
			} else if (contentStart < 0)
				contentStart = c;
		}
		if (contentStart == 0)
			splitList.add(controlText.toString().toLowerCase());
		else if (contentStart >= 0)
			splitList.add(controlText.subSequence(contentStart, controlText.length()).toString().toLowerCase());
		else
			splitList.add(controlText.toString()); // If all the text is whitespace, then search for whitespace
		String[] split = splitList.toArray(new String[splitList.size()]);
		List<TableContentControl> filters = new ArrayList<>();
		for (int i = 0; i < split.length; i++) {
			int colonIndex = split[i].indexOf(':');
			if (colonIndex > 0) {
				String category = split[i].substring(0, colonIndex);
				if (colonIndex < split[i].length() - 1) {
					String catFilter = split[i].substring(colonIndex + 1);
					filters.add(new CategoryFilter(category, _parseFilterElement(catFilter)));
					if (category.equalsIgnoreCase("sort"))
						filters.add(new RowSorter(Arrays.asList(catFilter.split(","))));
					else if (category.equalsIgnoreCase("columns"))
						filters.add(new ColumnSorter(Arrays.asList(catFilter.split(","))));
					else
						filters.add(_parseFilterElement(split[i]));
				} else if (category.equalsIgnoreCase("sort") || category.equalsIgnoreCase("columns")) {
					// If the user enters "sort:" or "columns:", we'll assume they're preparing to sort and not searching for that text
				} else
					filters.add(_parseFilterElement(split[i]));
			} else
				filters.add(_parseFilterElement(split[i]));
		}
		if (filters.isEmpty())
			return DEFAULT;
		else if (filters.size() == 1)
			return filters.get(0);
		else
			return new OrFilter(filters.toArray(new TableContentControl[filters.size()]));
	}

	public static TableContentControl _parseFilterElement(String filterText) {
		ArrayList<TableContentControl> filters = new ArrayList<>();
		filters.add(new SimpleFilter(filterText));
		Matcher m = INT_RANGE_PATTERN.matcher(filterText);
		if (m.matches())
			filters.add(new IntRangeFilter(m.group("i1"), m.group("i2")));
		else {
			FoundDouble flt = tryParseDouble(filterText, 0);
			if (flt != null) {
				if (flt.end == filterText.length())
					filters.add(new FloatRangeFilter(flt.minValue, flt.maxValue));
				else if (filterText.charAt(flt.end) == '-') {
					FoundDouble maxFlt = tryParseDouble(filterText, flt.end + 1);
					if (maxFlt != null && maxFlt.end == filterText.length())
						filters.add(new FloatRangeFilter(flt.minValue, maxFlt.maxValue));
				}
			}
		}
		ParsedTime time;
		try {
			time = TimeUtils.parseFlexFormatTime(filterText, false, false);
		} catch (ParseException e) {
			throw new IllegalStateException(e); // Shouldn't happen
		}
		if (time != null) {
			if (time.toString().length() == filterText.length())
				filters.add(new DateFilter(time));
			else if (filterText.charAt(time.toString().length()) == '-') {
				ParsedTime maxTime;
				int maxStart = time.toString().length() + 1;
				try {
					maxTime = TimeUtils.parseFlexFormatTime(filterText.substring(maxStart), true, false);
				} catch (ParseException e) {
					throw new IllegalStateException(e); // Shouldn't happen
				}
				if (maxTime != null && maxStart + maxTime.toString().length() == filterText.length()) {
					filters.add(new DateRangeFilter(time, maxTime));
				}
			}
		}
		int starIndex = filterText.indexOf('*');
		if (starIndex >= 0 && starIndex < filterText.length() - 1) {
			ArrayList<String> sequence = new ArrayList<>();
			int start = 0;
			do {
				sequence.add(filterText.substring(start, starIndex));
				start = starIndex + 1;
				starIndex = filterText.indexOf('*', start);
			} while (starIndex >= 0);
			if (start < filterText.length())
				sequence.add(filterText.substring(start));
			sequence.trimToSize();
			filters.add(new SimplePatternFilter(sequence));
		}
		if (filterText.length() > 1 && filterText.charAt(0) == '\\') {
			try {
				Pattern pattern = Pattern.compile(filterText.substring(1), Pattern.CASE_INSENSITIVE | Pattern.DOTALL | Pattern.MULTILINE);
				filters.add(new FullPatternFilter(pattern));
			} catch (PatternSyntaxException e) {
				// Ignore
			}
		}
		if (filters.size() == 1)
			return filters.get(0);
		filters.trimToSize();
		return new OrFilter(filters.toArray(new TableContentControl[filters.size()]));
	}

	public static <E> ObservableCollection<FilteredValue<E>> applyRowControl(ObservableCollection<E> values,
		Supplier<? extends Collection<? extends ValueRenderer<? super E>>> render, ObservableValue<? extends TableContentControl> filter,
			Observable<?> until) {
		return values.flow().transform((TypeToken<FilteredValue<E>>) (TypeToken<?>) TypeTokens.get().of(FilteredValue.class), //
			combine -> combine.combineWith(filter).build((x, cv) -> {
				Collection<? extends ValueRenderer<? super E>> renders = render.get();
				FilteredValue<E> v;
				if (cv.hasPreviousResult()) {
					v = cv.getPreviousResult();
					v.setValue(x);
					v.columnsChanged(renders.size());
				} else
					v = new FilteredValue<>(x, renders.size());
				TableContentControl f = cv.get(filter);
				int i = 0;
				v.isFiltered=false;
				for (ValueRenderer<? super E> r : renders) {
					CharSequence rendered = r.render(v.value);
					int[][] matches = f.findMatches(r, rendered);
					if (matches != null) {
						v.isFiltered = true;
						v.matches[i] = f.findMatches(r, rendered);
					}
					i++;
				}
				return v;
			})).filter(fv -> fv.hasMatch() ? null : "No match").sorted((fv1, fv2)->{
				int comp=fv1.compareTo(fv2);
				if(comp!=0)
					return comp;
				List<String> sorting = filter.get().getRowSorting();
				if (sorting != null) {
					Collection<? extends ValueRenderer<? super E>> renders = render.get();
					for (String s : sorting) {
						for (ValueRenderer<? super E> r : renders) {
							int rSort = RowSorter.sortCategoryMatches(s, r.getName());
							if (rSort != 0) {
								comp = r.compare(fv1.value, fv2.value);
								if (comp != 0) {
									if (rSort < 0)
										return -comp;
									else
										return comp;
								}
							}
						}
					}
				}
				return 0;
			}).collectActive(until);
	}

	public static <R, C extends CategoryRenderStrategy<? super R, ?>> ObservableCollection<C> applyColumnControl(
		ObservableCollection<C> columns, ObservableValue<? extends TableContentControl> filter, Observable<?> until) {
		return columns.flow().refresh(filter.noInitChanges()).sorted((c1, c2) -> {
			List<String> columnSorting = filter.get().getColumnSorting();
			if (columnSorting == null)
				return 0;
			for (int c = 0; c < columnSorting.size(); c++) {
				boolean c1Match = CategoryFilter.categoryMatches(columnSorting.get(c), c1.getName());
				boolean c2Match = CategoryFilter.categoryMatches(columnSorting.get(c), c2.getName());
				if (c1Match) {
					if (!c2Match)
						return -1;
				} else if (c2Match)
					return 1;
			}
			return 0;
		}).collectActive(until);
	}

	public static final TableContentControl DEFAULT = new TableContentControl() {
		@Override
		public int[][] findMatches(ValueRenderer<?> category, CharSequence value) {
			return null;
		}

		@Override
		public List<String> getRowSorting() {
			return null;
		}

		@Override
		public List<String> getColumnSorting() {
			return null;
		}

		@Override
		public String toString() {
			return "";
		}
	};

	public static class SimpleFilter implements TableContentControl {
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
				if (Character.toLowerCase(toSearch.charAt(c)) == theMatcher.charAt(textIdx)) {
					textIdx++;
					if (textIdx == theMatcher.length()) {
						if (matches == null)
							matches = new LinkedList<>();
						matches.add(new int[] { c - textIdx + 1, c + 1 });
						textIdx = 0;
					}
				} else
					textIdx = 0;

			}
			return matches == null ? NO_MATCH : matches.toArray(new int[matches.size()][]);
		}

		@Override
		public List<String> getRowSorting() {
			return null;
		}

		@Override
		public List<String> getColumnSorting() {
			return null;
		}

		@Override
		public String toString() {
			return theMatcher;
		}
	}

	public static class CategoryFilter implements TableContentControl {
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

			@Override
			public int compare(E o1, E o2) {
				return renderer.compare(o1, o2);
			}
		}

		private final String theCategory;
		private final TableContentControl theFilter;

		public CategoryFilter(String category, TableContentControl filter) {
			theCategory = category;
			theFilter = filter;
		}

		@Override
		public int[][] findMatches(ValueRenderer<?> category, CharSequence text) {
			if (!categoryMatches(theCategory, category.getName()))
				return null;
			return theFilter.findMatches(new UnfilteredRenderer<>(category), text);
		}

		public static boolean categoryMatches(String category, String test) {
			int c, t;
			for (c = 0, t = 0; c < category.length() && t < test.length();) {
				if (Character.isWhitespace(test.charAt(t)))
					t++;
				else if (Character.toLowerCase(category.charAt(c)) != Character.toLowerCase(test.charAt(t)))
					break;
				else {
					c++;
					t++;
				}
			}
			return c == category.length(); // Don't need to match the entire text, just the start
		}

		@Override
		public List<String> getRowSorting() {
			return null;
		}

		@Override
		public List<String> getColumnSorting() {
			return null;
		}

		@Override
		public String toString() {
			return new StringBuilder(theCategory).append(':').append(theFilter).toString();
		}
	}

	public static class SimplePatternFilter implements TableContentControl {
		private final List<String> theSequence;

		public SimplePatternFilter(List<String> sequence) {
			theSequence = sequence;
		}

		@Override
		public int[][] findMatches(ValueRenderer<?> category, CharSequence text) {
			if (!category.searchGeneral())
				return null;
			List<int[]> matches = null;
			String toSearch = text.toString();
			int start = 0;
			while (true) {
				int end = start;
				for (String seq : theSequence) {
					if (seq.length() == 0)
						continue;
					int index = toSearch.indexOf(seq, end);
					if (index < 0) {
						start = -1;
						break;
					} else
						end = index + seq.length();
				}
				if (start >= 0) {
					if (matches == null)
						matches = new LinkedList<>();
					matches.add(new int[] { start, end });
					start = end;
				} else
					break;
			}
			return matches == null ? NO_MATCH : matches.toArray(new int[matches.size()][]);
		}

		@Override
		public List<String> getRowSorting() {
			return null;
		}

		@Override
		public List<String> getColumnSorting() {
			return null;
		}

		@Override
		public String toString() {
			return StringUtils.conversational("*", null).print(theSequence, StringBuilder::append).toString();
		}
	}

	public static class FullPatternFilter implements TableContentControl {
		private final Pattern thePattern;

		public FullPatternFilter(Pattern pattern) {
			thePattern = pattern;
		}

		@Override
		public int[][] findMatches(ValueRenderer<?> category, CharSequence text) {
			if (!category.searchGeneral())
				return null;
			List<int[]> matches = null;
			Matcher matcher = thePattern.matcher(text);
			int start = 0;
			while (matcher.find(start)) {
				if (matcher.end() == matcher.start()) { // Don't report empty matches or get stuck in the loop
					start = matcher.end() + 1;
					continue;
				}
				if (matches == null)
					matches = new LinkedList<>();
				matches.add(new int[] { matcher.start(), matcher.end() });
				start = matcher.end();
			}
			return matches == null ? NO_MATCH : matches.toArray(new int[matches.size()][]);
		}

		@Override
		public List<String> getRowSorting() {
			return null;
		}

		@Override
		public List<String> getColumnSorting() {
			return null;
		}

		@Override
		public String toString() {
			return new StringBuilder().append('\\').append(thePattern.pattern()).toString();
		}
	}

	class FoundDouble {
		public final double minValue;
		public final double maxValue;
		public final int end;

		public FoundDouble(double minValue, double maxValue, int end) {
			this.minValue = minValue;
			this.maxValue = maxValue;
			this.end = end;
		}
	}

	public static FoundDouble tryParseDouble(CharSequence text, int start) {
		if (start >= text.length())
			return null;
		int wholeStart = start;
		boolean neg = text.charAt(wholeStart) == '-';
		if (neg)
			wholeStart++;
		if (text.length() >= wholeStart + 3) {
			if (Character.toLowerCase(text.charAt(wholeStart)) == 'n' && Character.toLowerCase(text.charAt(wholeStart + 1)) == 'a'
				&& Character.toLowerCase(text.charAt(wholeStart + 2)) == 'n')
				return new FoundDouble(Double.NaN, Double.NaN, wholeStart + 3);
			String inf = "infinity";
			int infI;
			for (infI = 0; infI < inf.length() && wholeStart + infI < text.length(); infI++) {
				if (inf.charAt(infI) != Character.toLowerCase(text.charAt(wholeStart + infI)))
					break;
			}
			if (infI >= 3) {
				double d = neg ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
				return new FoundDouble(d, d, wholeStart + infI);
			}
		}
		int end = wholeStart;
		int wholeEnd = end;
		while (end < text.length() && text.charAt(end) >= '0' && text.charAt(end) <= '9') {
			end++;
			wholeEnd++;
		}
		int decimalStart = end, decimalEnd = end;
		if (end < text.length() && text.charAt(end) == '.') {
			end++;
			decimalStart++;
			decimalEnd++;
			while (end < text.length() && text.charAt(end) >= '0' && text.charAt(end) <= '9') {
				end++;
				decimalEnd++;
			}
		}
		if (wholeEnd == wholeStart && decimalEnd == decimalStart)
			return null;
		int expStart = end, expEnd = end;
		if (end < text.length() && (text.charAt(end) == 'E' || text.charAt(end) == 'e')) {
			end++;
			expStart++;
			expEnd++;
			if (end < text.length() && text.charAt(end) == '-') {
				end++;
				expEnd++;
			}
			while (end < text.length() && text.charAt(end) >= '0' && text.charAt(end) <= '9') {
				end++;
				expEnd++;
			}
		}
		double minValue = Double.parseDouble(text.subSequence(start, end).toString());
		int exp = (expEnd == expStart) ? 0 : Integer.parseInt(text.subSequence(expStart, expEnd).toString());
		exp -= (decimalEnd - decimalStart);
		double tolerance;
		if (exp > -10 && exp < 10) {
			int t = 1;
			int i = 0;
			for (; i < exp; i++)
				t *= 10;
			for (; i > exp; i--)
				t /= 10;
			tolerance = t;
		} else
			tolerance = Math.pow(10, exp);
		tolerance /= 2;
		double maxValue = minValue + tolerance;
		minValue -= tolerance;
		return new FoundDouble(minValue, maxValue, end);
	}

	public static class FloatRangeFilter implements TableContentControl {
		private final double theMinValue;
		private final double theMaxValue;

		public FloatRangeFilter(double minValue, double maxValue) {
			int comp = Double.compare(minValue, maxValue);
			theMinValue = (comp <= 0 ? minValue : maxValue);
			theMaxValue = (comp <= 0 ? maxValue : minValue);
		}

		@Override
		public int[][] findMatches(ValueRenderer<?> category, CharSequence text) {
			if (!category.searchGeneral())
				return null;
			List<int[]> matches = null;
			for (int i = 0; i < text.length(); i++) {
				FoundDouble found = tryParseDouble(text, i);
				if (found == null)
					continue;
				int comp = Double.compare(found.maxValue, theMinValue);
				if (comp > 0 || (comp == 0 && theMinValue == theMaxValue)) {
					comp = Double.compare(found.minValue, theMaxValue);
					if (comp < 0 || (comp == 0 && theMinValue == theMaxValue)) {
						if (matches == null)
							matches = new LinkedList<>();
						matches.add(new int[] { i, found.end });
					}
				}
				i += found.end - 1; // Loop will increment
			}
			return matches == null ? NO_MATCH : matches.toArray(new int[matches.size()][]);
		}

		@Override
		public List<String> getRowSorting() {
			return null;
		}

		@Override
		public List<String> getColumnSorting() {
			return null;
		}

		@Override
		public String toString() {
			return new StringBuilder().append(theMinValue).append('-').append(theMaxValue).toString();
		}
	}

	public static class IntRangeFilter implements TableContentControl {
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
			return matches == null ? NO_MATCH : matches.toArray(new int[matches.size()][]);
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
		public List<String> getRowSorting() {
			return null;
		}

		@Override
		public List<String> getColumnSorting() {
			return null;
		}

		@Override
		public String toString() {
			return new StringBuilder(theLow).append('-').append(theHigh).toString();
		}
	}

	public static class DateFilter implements TableContentControl {
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
			return matches == null ? NO_MATCH : matches.toArray(new int[matches.size()][]);
		}

		@Override
		public List<String> getRowSorting() {
			return null;
		}

		@Override
		public List<String> getColumnSorting() {
			return null;
		}

		@Override
		public String toString() {
			return theTime.toString();
		}
	}

	public static class DateRangeFilter implements TableContentControl {
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
			return matches == null ? NO_MATCH : matches.toArray(new int[matches.size()][]);
		}

		@Override
		public List<String> getRowSorting() {
			return null;
		}

		@Override
		public List<String> getColumnSorting() {
			return null;
		}

		@Override
		public String toString() {
			return new StringBuilder().append(theMinTime).append('-').append(theMaxTime).toString();
		}
	}

	public static class OrFilter implements TableContentControl {
		private final TableContentControl[] theContent;

		public OrFilter(TableContentControl... filters) {
			theContent = filters;
		}

		@Override
		public int[][] findMatches(ValueRenderer<?> category, CharSequence value) {
			BetterSortedSet<int[]> matches = null;
			for (TableContentControl content : theContent) {
				int[][] match = content.findMatches(category, value);
				if (match != null) {
					if (matches == null)
						matches = new BetterTreeSet<>(false, (i1, i2) -> {
							int comp = Integer.compare(i1[0], i2[0]);
							if (comp == 0)
								comp = Integer.compare(i1[1], i2[1]);
							return comp;
						});
					for (int[] m : match)
						matches.add(m);
				}
			}
			if (matches == null)
				return null;
			return matches.toArray(new int [matches.size()][]);
		}

		@Override
		public List<String> getRowSorting() {
			List<String> sorting = null;
			for (TableContentControl c : theContent) {
				List<String> cSorting = c.getRowSorting();
				if (cSorting != null) {
					if (sorting == null)
						sorting = new ArrayList<>();
					sorting.addAll(cSorting);
				}
			}
			return sorting;
		}

		@Override
		public List<String> getColumnSorting() {
			List<String> sorting = null;
			for (TableContentControl c : theContent) {
				List<String> cSorting = c.getColumnSorting();
				if (cSorting != null) {
					if (sorting == null)
						sorting = new ArrayList<>();
					sorting.addAll(cSorting);
				}
			}
			return sorting;
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder();
			for (int i = 0; i < theContent.length; i++) {
				if (i > 0)
					str.append(' ');
				str.append(theContent[i]);
			}
			return str.toString();
		}
	}

	public static class RowSorter implements TableContentControl {
		private final List<String> theSorting;

		public RowSorter(List<String> sorting) {
			theSorting = sorting;
		}

		@Override
		public int[][] findMatches(ValueRenderer<?> category, CharSequence text) {
			return null;
		}

		@Override
		public List<String> getRowSorting() {
			return theSorting;
		}

		@Override
		public List<String> getColumnSorting() {
			return null;
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder("sort:");
			return StringUtils.conversational(",", null).print(str, theSorting, StringBuilder::append).toString();
		}

		public static int sortCategoryMatches(String category, String test) {
			if (sortCategoryMatches(category, 0, test))
				return 1;
			int sortPrefix = 0;
			if (category.charAt(0) == '-')
				sortPrefix = -1;
			else if (category.charAt(0) == '+')
				sortPrefix = 1;
			if (sortPrefix == 0)
				return 0;
			else if (sortCategoryMatches(category, 1, test))
				return sortPrefix;
			return 0;
		}

		private static boolean sortCategoryMatches(String category, int start, String test) {
			int c, t;
			for (c = start, t = 0; c < category.length() && t < test.length();) {
				if (Character.isWhitespace(test.charAt(t)))
					t++;
				else if (Character.toLowerCase(category.charAt(c)) != Character.toLowerCase(test.charAt(t)))
					break;
				else {
					c++;
					t++;
				}
			}
			return c == category.length(); // Don't need to match the entire text, just the start
		}
	}

	public static class ColumnSorter implements TableContentControl {
		private final List<String> theColumns;

		public ColumnSorter(List<String> columns) {
			theColumns = columns;
		}

		@Override
		public int[][] findMatches(ValueRenderer<?> category, CharSequence text) {
			return null;
		}

		@Override
		public List<String> getRowSorting() {
			return null;
		}

		@Override
		public List<String> getColumnSorting() {
			return theColumns;
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder("columns:");
			return StringUtils.conversational(",", null).print(str, theColumns, StringBuilder::append).toString();
		}
	}
}
