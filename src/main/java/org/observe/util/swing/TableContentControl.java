package org.observe.util.swing;

import static org.observe.util.swing.TableContentControl.textMatches;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.swing.JFrame;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.util.TypeTokens;
import org.qommons.ArrayUtils;
import org.qommons.BreakpointHere;
import org.qommons.LambdaUtils;
import org.qommons.Named;
import org.qommons.StringUtils;
import org.qommons.TimeUtils;
import org.qommons.TimeUtils.ParsedDuration;
import org.qommons.TimeUtils.ParsedTime;
import org.qommons.collect.CollectionUtils;
import org.qommons.io.Format;

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
		+ "Use <b>columns:column1,column2</b> to sort the table columns.<br>\n"//
		+ "All filters are AND-ed.\n"//
		+ "</html>";

	public interface ValueRenderer<E> extends Named, Comparator<E> {
		boolean searchGeneral();

		CharSequence render(E value);
	}

	public static class FilteredValue<E> implements Comparable<FilteredValue<?>> {
		E value;
		int[][][] matches;
		boolean hasMatch;
		boolean isFiltered;

		public FilteredValue(E value, int columns) {
			this.value = value;
			matches = new int[columns][][];
		}

		public E getValue() {
			return value;
		}

		public void setValue(E value) {
			this.value = value;
		}

		public boolean hasMatch() {
			return hasMatch;
		}

		public boolean isFiltered() {
			return isFiltered;
		}

		public int getColumns() {
			return matches == null ? 0 : matches.length;
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
			if (matches == null) {
				return o.matches == null ? 0 : 1;
			} else if (o.matches == null)
				return -1;
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

		@Override
		public String toString() {
			return String.valueOf(value);
		}
	}

	static int[][] NO_MATCH = new int[0][];

	static int[][][] noMatch(int categories) {
		int[][][] match = new int[categories][][];
		Arrays.fill(match, NO_MATCH);
		return match;
	}

	static boolean isMatch(int[][][] match) {
		if (match == null)
			return false;
		for (int i = 0; i < match.length; i++) {
			if (match[i] != null && match[i].length > 0)
				return true;
		}
		return false;
	}

	default int[][][] findMatches(List<? extends ValueRenderer<?>> categories, CharSequence[] texts) {
		int[][][] matches = null;
		for (int i = 0; i < texts.length; i++) {
			int[][] catMatches = findMatches(categories.get(i), texts[i]);
			if (catMatches != null) {
				if (matches == null)
					matches = new int[texts.length][][];
				matches[i] = catMatches;
			}
		}
		return matches;
	}

	int[][] findMatches(ValueRenderer<?> category, CharSequence text);

	boolean isSearch();

	List<String> getRowSorting();

	List<String> getColumnSorting();

	default TableContentControl or(TableContentControl other) {
		return new OrFilter(this, other);
	}

	public static TableContentControl of(String category, Predicate<CharSequence> filter) {
		return new PredicateFilter(category, filter);
	}

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

	public static <F extends PanelPopulation.FieldEditor<ObservableTextField<TableContentControl>, F>> F configureSearchField(F field) {
		return field.withTooltip(TableContentControl.TABLE_CONTROL_TOOLTIP).modifyEditor(tf2 -> tf2//
			.setCommitOnType(true).setEmptyText("Search or Sort...")
			.setIcon(ObservableSwingUtils.getFixedIcon(null, "/icons/search.png", 16, 16)));
	}

	public static TableContentControl parseContentControl(CharSequence controlText) {
		if (controlText == null || controlText.length() == 0)
			return DEFAULT;
		List<String> splitList = new LinkedList<>();
		int contentStart = 0;
		boolean quoted = false;
		for (int c = 0; c < controlText.length(); c++) {
			if (controlText.charAt(c) == '"') {
				if (quoted) {
					splitList.add(controlText.subSequence(contentStart, c).toString().toLowerCase());
					contentStart = c + 1;
					continue;
				} else if (contentStart == c) {
					quoted = true;
					contentStart++;
					continue;
				}
			}
			if (!quoted && Character.isWhitespace(controlText.charAt(c))) {
				if (contentStart < c) {
					splitList.add(controlText.subSequence(contentStart, c).toString().toLowerCase());
				}
				contentStart = c + 1;
			}
		}
		if (contentStart < controlText.length())
			splitList.add(controlText.subSequence(contentStart, controlText.length()).toString().toLowerCase());
		else if (splitList.isEmpty())
			splitList.add(controlText.toString()); // If all the text is whitespace, then search for whitespace
		String[] split = splitList.toArray(new String[splitList.size()]);
		TableContentControl[] splitFilters = new TableContentControl[split.length];
		for (int i = 0; i < split.length; i++) {
			TableContentControl splitFilter;
			int colonIndex = split[i].indexOf(':');
			if (colonIndex > 0) {
				String category = split[i].substring(0, colonIndex);
				if (colonIndex < split[i].length() - 1) {
					String catFilter = split[i].substring(colonIndex + 1);
					if (category.equalsIgnoreCase("sort")) {
						splitFilter = new RowSorter(Arrays.asList(catFilter.split(",")));
					} else if (category.equalsIgnoreCase("columns")) {
						splitFilter = new ColumnSorter(Arrays.asList(catFilter.split(",")));
					} else {
						splitFilter = new CategoryFilter(category, _parseFilterElement(catFilter))//
							.or(_parseFilterElement(split[i])); // Also search as if the colon was part of the search
					}
				} else {
					splitFilter = new CategoryFilter(category, new EmptyFilter())//
						.or(_parseFilterElement(split[i])); // Also search as if the colon was part of the search
				}
			} else
				splitFilter = _parseFilterElement(split[i]);
			splitFilters[i] = splitFilter;
		}
		if (splitFilters.length == 1)
			return new RootFilter(controlText.toString().trim(), splitFilters[0]);
		else
			return new RootFilter(controlText.toString().trim(), new AndFilter(splitFilters));
	}

	public static TableContentControl _parseFilterElement(String filterText) {
		ArrayList<TableContentControl> filters = new ArrayList<>();
		filters.add(new SimpleFilter(filterText));
		Matcher m = INT_RANGE_PATTERN.matcher(filterText);
		if (m.matches())
			filters.add(new IntRangeFilter(m.group("i1"), m.group("i2")));
		else {
			FoundDouble flt = TableContentControl.tryParseDouble(filterText, 0);
			if (flt != null) {
				if (flt.end == filterText.length())
					filters.add(new FloatRangeFilter(flt.minValue, flt.maxValue));
				else if (filterText.charAt(flt.end) == '-') {
					FoundDouble maxFlt = TableContentControl.tryParseDouble(filterText, flt.end + 1);
					if (maxFlt != null && maxFlt.end == filterText.length())
						filters.add(new FloatRangeFilter(flt.minValue, maxFlt.maxValue));
				}
			}
		}
		ParsedTime time;
		try {
			time = TimeUtils.parseFlexFormatTime(filterText, false, false, null);
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
					maxTime = TimeUtils.parseFlexFormatTime(filterText.substring(maxStart), true, false, null);
				} catch (ParseException e) {
					throw new IllegalStateException(e); // Shouldn't happen
				}
				if (maxTime != null && maxStart + maxTime.toString().length() == filterText.length()) {
					filters.add(new DateRangeFilter(time, maxTime));
				}
			}
		}
		int dashIdx = -1;
		for (int i = 0; i < filterText.length(); i++) {
			if (filterText.charAt(i) == '-') {
				dashIdx = i;
				break;
			}
		}
		if (dashIdx <= 0) {
			ParsedDuration duration;
			try {
				duration = TimeUtils.parseDuration(filterText);
			} catch (ParseException e) {
				duration = null;
			}
			if (duration != null)
				filters.add(new DurationFilter(duration));
		}
		if (dashIdx > 0) {
			try {
				ParsedDuration minDuration = TimeUtils.parseDuration(filterText.subSequence(0, dashIdx));
				ParsedDuration maxDuration = TimeUtils.parseDuration(filterText.subSequence(dashIdx + 1, filterText.length()));
				filters.add(new DurationRangeFilter(minDuration, maxDuration));
			} catch (ParseException e) {
			}
		}
		int starIndex = filterText.indexOf('*');
		if (starIndex > 0 && starIndex < filterText.length() - 1) {
			ArrayList<String> sequence = new ArrayList<>();
			int start = 0;
			do {
				if (starIndex > start)
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
		else
			return new OrFilter(filters.toArray(new TableContentControl[filters.size()]));
	}

	public static <E> ObservableCollection<FilteredValue<E>> applyRowControl(ObservableCollection<E> values,
		Supplier<? extends Collection<? extends ValueRenderer<? super E>>> render, ObservableValue<? extends TableContentControl> filter,
			Observable<?> until) {
		List<ValueRenderer<? super E>> rendererList = new ArrayList<>();
		return values.flow().transform((TypeToken<FilteredValue<E>>) (TypeToken<?>) TypeTokens.get().of(FilteredValue.class), //
			combine -> combine.combineWith(filter).build(LambdaUtils.printableBiFn((x, cv) -> {
				Collection<? extends ValueRenderer<? super E>> renders = render.get();
				int i = 0;
				for (ValueRenderer<? super E> r : renders) {
					if (i == rendererList.size())
						rendererList.add(r);
					else
						rendererList.set(i, r);
					i++;
				}
				while (rendererList.size() > i)
					rendererList.remove(rendererList.size() - 1);
				FilteredValue<E> v;
				if (cv.hasPreviousResult()) {
					v = cv.getPreviousResult();
					v.setValue(x);
				} else
					v = new FilteredValue<>(x, rendererList.size());
				TableContentControl f = cv.get(filter);
				v.isFiltered = false;
				v.hasMatch = true;
				CharSequence[] texts = new CharSequence[rendererList.size()];
				i = 0;
				for (ValueRenderer<? super E> r : rendererList)
					texts[i++] = r.render(v.value);
				v.matches = f.findMatches(rendererList, texts);
				if (v.matches != null) {
					v.isFiltered = true;
					v.hasMatch = false;
					for (i = 0; !v.hasMatch && i < texts.length; i++) {
						if (v.matches[i] != null && v.matches[i].length > 0)
							v.hasMatch = true;
					}
				}
				return v;
			}, "toFilterValue", null))).filter(LambdaUtils.printableFn(fv -> fv.hasMatch() ? null : "No match", "match", null))//
			.sorted(LambdaUtils.printableComparator((fv1, fv2) -> {
				int comp = fv1.compareTo(fv2);
				if (comp != 0)
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
			}, () -> "rowSorting")).collectActive(until);
	}

	public static <R, C extends CategoryRenderStrategy<? super R, ?>> ObservableCollection<C> applyColumnControl(
		ObservableCollection<C> columns, ObservableValue<? extends TableContentControl> filter, Observable<?> until) {
		ObservableValue<List<String>> colSorting = filter.transform(tx -> tx.cache(true).map(f -> f == null ? null : f.getColumnSorting()));
		return columns.flow().refresh(colSorting.noInitChanges().filter(evt -> !Objects.equals(evt.getOldValue(), evt.getNewValue())))//
			.sorted(LambdaUtils.printableComparator((c1, c2) -> {
				List<String> columnSorting = colSorting.get();
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
			}, () -> "sortedColumns")).collectActive(until);
	}

	public static final TableContentControl DEFAULT = new TableContentControl() {
		@Override
		public int[][][] findMatches(List<? extends ValueRenderer<?>> category, CharSequence[] texts) {
			return null;
		}

		@Override
		public int[][] findMatches(ValueRenderer<?> category, CharSequence value) {
			return null;
		}

		@Override
		public boolean isSearch() {
			return false;
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

	static int[] textMatches(CharSequence matcher, CharSequence toSearch, int searchStart) {
		int c, m, matchStart = 0;
		for (c = searchStart, m = 0; c < toSearch.length(); c++) {
			char matchCh = matcher.charAt(m);
			char testCh = toSearch.charAt(c);
			int diff = matchCh - testCh;
			boolean match;
			if (diff == 0)
				match = true;
			else if (diff == StringUtils.a_MINUS_A && matchCh >= 'a' && matchCh <= 'z')
				match = true;
			else if (diff == -StringUtils.a_MINUS_A && matchCh >= 'A' && matchCh <= 'Z')
				match = true;
			else if (Character.isWhitespace(testCh) && !Character.isWhitespace(matchCh)) {
				continue;
			} else
				match = false;
			if (match) {
				m++;
				if (m == matcher.length())
					return new int[] { matchStart, c + 1 };
			} else
				matchStart = m = 0;
		}
		return null;
	}

	public static class SimpleFilter implements TableContentControl {
		private final String theMatcher;

		public SimpleFilter(String matcher) {
			if (matcher.isEmpty())
				throw new IllegalArgumentException("Cannot make an empty simple matcher");
			theMatcher = matcher;
		}

		@Override
		public int[][] findMatches(ValueRenderer<?> category, CharSequence toSearch) {
			if (!category.searchGeneral())
				return null;
			int[] match;
			int lastMatch = 0;
			SortedIntArraySet matches = null;
			do {
				match = textMatches(theMatcher, toSearch, lastMatch);
				if (match != null) {
					lastMatch = match[1];
					if (matches == null)
						matches = SortedIntArraySet.get();
					matches.add(match);
				}
			} while (match != null);
			return matches == null ? NO_MATCH : matches.flush();
		}

		@Override
		public boolean isSearch() {
			return true;
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

	public static class RootFilter implements TableContentControl {
		private final String theText;
		private final TableContentControl theFilter;

		public RootFilter(String text, TableContentControl filter) {
			theText = text;
			theFilter = filter;
		}

		@Override
		public int[][][] findMatches(List<? extends ValueRenderer<?>> categories, CharSequence[] texts) {
			return theFilter.findMatches(categories, texts);
		}

		@Override
		public int[][] findMatches(ValueRenderer<?> category, CharSequence text) {
			return theFilter.findMatches(category, text);
		}

		@Override
		public boolean isSearch() {
			return theFilter.isSearch();
		}

		@Override
		public List<String> getRowSorting() {
			return theFilter.getRowSorting();
		}

		@Override
		public List<String> getColumnSorting() {
			return theFilter.getColumnSorting();
		}

		@Override
		public String toString() {
			return theText;
		}
	}

	public static class EmptyFilter implements TableContentControl {
		@Override
		public int[][] findMatches(ValueRenderer<?> category, CharSequence text) {
			if (text == null || text.length() == 0)
				return new int[1][2]; // [[0, 0]]
			return NO_MATCH;
		}

		@Override
		public boolean isSearch() {
			return true;
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
		public boolean isSearch() {
			return true;
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
				if (t == test.length())
					return false;
				char catCh = category.charAt(c);
				char testCh = test.charAt(t);
				int diff = catCh - testCh;
				if (diff == 0) {//
				} else if (diff == StringUtils.a_MINUS_A && catCh >= 'a' && catCh <= 'z') {//
				} else if (diff == -StringUtils.a_MINUS_A && catCh >= 'A' && catCh <= 'Z') {//
				} else if (Character.isWhitespace(testCh) && !Character.isWhitespace(catCh)) {//
					t++;
					continue;
				} else
					return false;
				c++;
				t++;
			}
			return true;
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
		private static final int CASE_DIFF = 'a' - 'A';

		private final List<String> theSequence;

		public SimplePatternFilter(List<String> sequence) {
			theSequence = sequence;
		}

		@Override
		public int[][] findMatches(ValueRenderer<?> category, CharSequence text) {
			if (!category.searchGeneral())
				return null;
			if (theSequence.isEmpty()) {
				return new int[] []{new int[] {0, text.length()}};
			}
			SortedIntArraySet matches = null;
			int[] match = find(text, 0);
			if (match != null) {
				matches = SortedIntArraySet.get();
			}
			while (match != null) {
				matches.add(match);
				match = find(text, match[0] + match[1]);
			}
			return matches == null ? NO_MATCH : matches.flush();
		}

		private int[] find(CharSequence text, int start) {
			int end = start;
			for (String seq : theSequence) {
				if (seq.length() == 0)
					continue;
				int index = find(text, seq, end);
				if (index < 0) {
					start = -1;
					break;
				} else
					end = index + seq.length();
			}
			return start >= 0 ? new int[] { start, end } : null;
		}

		private static int find(CharSequence text, String seq, int start) {
			while (start + seq.length() <= text.length()) {
				int c;
				for (c = 0; c < seq.length(); c++) {
					char ch1 = text.charAt(start + c);
					char ch2 = seq.charAt(c);
					if (ch1 == ch2)
						continue;
					else if (ch1 >= 'A' && ch1 <= 'Z' && ch2 - ch1 == CASE_DIFF)
						continue;
					else if (ch2 >= 'A' && ch2 <= 'Z' && ch1 - ch2 == CASE_DIFF)
						continue;
					else
						break;
				}
				if (c == seq.length())
					return start;
				start++;
			}
			return -1;
		}

		@Override
		public boolean isSearch() {
			return true;
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
			SortedIntArraySet matches = null;
			Matcher matcher = thePattern.matcher(text);
			int start = 0;
			while (matcher.find(start)) {
				if (matcher.end() == matcher.start()) { // Don't report empty matches or get stuck in the loop
					start = matcher.end() + 1;
					continue;
				}
				if (matches == null)
					matches = SortedIntArraySet.get();
				matches.add(new int[] { matcher.start(), matcher.end() });
				start = matcher.end();
			}
			return matches == null ? NO_MATCH : matches.flush();
		}

		@Override
		public boolean isSearch() {
			return true;
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
		boolean trivial = true;
		while (end < text.length() && text.charAt(end) >= '0' && text.charAt(end) <= '9') {
			if (trivial && text.charAt(end) != '0')
				trivial = false;
			end++;
			wholeEnd++;
		}
		int decimalStart = end, decimalEnd = end;
		if (end < text.length() && text.charAt(end) == '.') {
			trivial = false;
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
			if (trivial)
				return null;
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
			SortedIntArraySet matches = null;
			for (int i = 0; i < text.length(); i++) {
				FoundDouble found = TableContentControl.tryParseDouble(text, i);
				if (found == null)
					continue;
				int comp = Double.compare(found.maxValue, theMinValue);
				if (comp > 0 || (comp == 0 && theMinValue == theMaxValue)) {
					comp = Double.compare(found.minValue, theMaxValue);
					if (comp < 0 || (comp == 0 && theMinValue == theMaxValue)) {
						if (matches == null)
							matches = SortedIntArraySet.get();
						matches.add(new int[] { i, found.end });
					}
				}
				i += found.end - 1; // Loop will increment
			}
			return matches == null ? NO_MATCH : matches.flush();
		}

		@Override
		public boolean isSearch() {
			return true;
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
			SortedIntArraySet matches = null;
			int digitStart = -1;
			for (int c = 0; c < toSearch.length(); c++) {
				if (Character.isDigit(toSearch.charAt(c))) {
					if (digitStart < 0)
						digitStart = c;
				} else if (digitStart >= 0) {
					if (isIncluded(toSearch, digitStart, c)) {
						if (matches == null)
							matches = SortedIntArraySet.get();
						matches.add(new int[] { digitStart, c });
					}
					digitStart = -1;
				}
			}
			if (digitStart >= 0 && isIncluded(toSearch, digitStart, toSearch.length())) {
				if (matches == null)
					matches = SortedIntArraySet.get();
				matches.add(new int[] { digitStart, toSearch.length() });
			}
			return matches == null ? NO_MATCH : matches.flush();
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
		public boolean isSearch() {
			return true;
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
			SortedIntArraySet matches = null;
			for (int i = 0; i < text.length();) {
				TimeUtils.ParsedTime time;
				try {
					time = TimeUtils.parseFlexFormatTime(//
						text.subSequence(i, text.length()), false, false, null);
				} catch (ParseException e) {
					e.printStackTrace();
					return NO_MATCH;
				}
				if (time != null && theTime.isComparable(time) && theTime.compareTo(time) == 0) {
					if (matches == null)
						matches = SortedIntArraySet.get();
					matches.add(new int[] { i, i + time.toString().length() });
					i += time.toString().length();
				} else
					i++;
			}
			return matches == null ? NO_MATCH : matches.flush();
		}

		@Override
		public boolean isSearch() {
			return true;
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
			SortedIntArraySet matches = null;
			for (int i = 0; i < text.length();) {
				if (Character.isDigit(text.charAt(i))) {
					TimeUtils.ParsedTime time;
					try {
						time = TimeUtils.parseFlexFormatTime(text.subSequence(i, text.length()), false, false, null);
					} catch (ParseException e) {
						throw new IllegalStateException();
					}
					if (time != null && theMinTime.isComparable(time) && theMinTime.compareTo(time) <= 0
						&& theMaxTime.compareTo(time) >= 0) {
						if (matches == null)
							matches = SortedIntArraySet.get();
						matches.add(new int[] { i, i + time.toString().length() });
						i += time.toString().length();
					} else
						i++;
				} else
					i++;
			}
			return matches == null ? NO_MATCH : matches.flush();
		}

		@Override
		public boolean isSearch() {
			return true;
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

	public static class DurationFilter implements TableContentControl {
		private final TimeUtils.ParsedDuration theDuration;

		public DurationFilter(ParsedDuration duration) {
			theDuration = duration;
		}

		@Override
		public int[][] findMatches(ValueRenderer<?> category, CharSequence text) {
			if (!category.searchGeneral())
				return null;
			TimeUtils.ParsedDuration duration;
			try {
				duration = TimeUtils.parseDuration(text, false);
				if (duration == null)
					return NO_MATCH;
			} catch (ParseException e) {
				return NO_MATCH;
			}
			if (theDuration.compareTo(duration) == 0) {
				return new int[][] { { 0, text.length() } };
			}
			return NO_MATCH;
		}

		@Override
		public boolean isSearch() {
			return true;
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
			return theDuration.toString();
		}
	}

	public static class DurationRangeFilter implements TableContentControl {
		private final TimeUtils.ParsedDuration theMinDuration;
		private final TimeUtils.ParsedDuration theMaxDuration;

		public DurationRangeFilter(ParsedDuration minDuration, ParsedDuration maxDuration) {
			theMinDuration = minDuration;
			theMaxDuration = maxDuration;
		}

		@Override
		public int[][] findMatches(ValueRenderer<?> category, CharSequence text) {
			if (!category.searchGeneral())
				return null;
			TimeUtils.ParsedDuration duration;
			try {
				duration = TimeUtils.parseDuration(text);
			} catch (ParseException e) {
				return NO_MATCH;
			}
			if (theMinDuration.compareTo(duration) <= 0 && theMaxDuration.compareTo(duration) >= 0) {
				return new int[][] { { 0, text.length() } };
			}
			return NO_MATCH;
		}

		@Override
		public boolean isSearch() {
			return true;
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
			return new StringBuilder().append(theMinDuration).append('-').append(theMaxDuration).toString();
		}
	}

	public static class OrFilter implements TableContentControl {
		private final TableContentControl[] theContent;

		public OrFilter(TableContentControl... filters) {
			theContent = filters;
			for (TableContentControl c : filters)
				if (c == null)
					BreakpointHere.breakpoint();
		}

		@Override
		public int[][][] findMatches(List<? extends ValueRenderer<?>> categories, CharSequence[] texts) {
			SortedIntArraySet[] matches = null;
			for (TableContentControl content : theContent) {
				int[][][] match = content.findMatches(categories, texts);
				if (match == null)
					continue;
				if (matches == null)
					matches = new SortedIntArraySet[texts.length];
				for (int i = 0; i < match.length; i++) {
					int[][] cm = match[i];
					if (cm != null) {
						if (matches[i] == null)
							matches[i] = SortedIntArraySet.get();
						for (int[] m : cm) {
							matches[i].add(m);
						}
					}
				}
			}
			return SortedIntArraySet.flush(matches);
		}

		@Override
		public int[][] findMatches(ValueRenderer<?> category, CharSequence value) {
			SortedIntArraySet matches = null;
			for (TableContentControl content : theContent) {
				int[][] match = content.findMatches(category, value);
				if (match != null) {
					if (matches == null)
						matches = SortedIntArraySet.get();
					for (int[] m : match)
						matches.add(m);
				}
			}
			return matches == null ? null : matches.flush();
		}

		@Override
		public boolean isSearch() {
			for (TableContentControl c : theContent) {
				if (!c.isSearch())
					return false;
			}
			return true;
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
		public TableContentControl or(TableContentControl other) {
			return new OrFilter(ArrayUtils.add(theContent, other));
		}

		@Override
		public String toString() {
			Set<String> printed = new HashSet<>();
			StringBuilder str = new StringBuilder();
			for (int i = 0; i < theContent.length; i++) {
				String contentStr = theContent[i].toString();
				if (printed.add(contentStr)) {
					if (i > 0)
						str.append(' ');
					str.append(theContent[i]);
				}
			}
			return str.toString();
		}
	}

	public static class AndFilter implements TableContentControl {
		private final TableContentControl[] theContent;

		public AndFilter(TableContentControl... filters) {
			theContent = filters;
		}

		@Override
		public int[][][] findMatches(List<? extends ValueRenderer<?>> categories, CharSequence[] texts) {
			SortedIntArraySet[] matches = null;
			for (TableContentControl content : theContent) {
				int[][][] match = content.findMatches(categories, texts);
				if (!TableContentControl.isMatch(match) && content.isSearch())
					return TableContentControl.noMatch(texts.length);
				if (match == null)
					continue;
				if (matches == null)
					matches = new SortedIntArraySet[texts.length];
				for (int i = 0; i < match.length; i++) {
					int[][] cm = match[i];
					if (cm != null) {
						if (matches[i] == null)
							matches[i] = SortedIntArraySet.get();
						for (int[] m : cm) {
							matches[i].add(m);
						}
					}
				}
			}
			return SortedIntArraySet.flush(matches);
		}

		@Override
		public int[][] findMatches(ValueRenderer<?> category, CharSequence value) {
			SortedIntArraySet matches = null;
			for (TableContentControl content : theContent) {
				int[][] match = content.findMatches(category, value);
				if (match == null)
					return null;
				if (matches == null)
					matches = SortedIntArraySet.get();
				for (int[] m : match)
					matches.add(m);
			}
			if (matches == null)
				return null;
			return matches.flush();
		}

		@Override
		public boolean isSearch() {
			for (TableContentControl c : theContent) {
				if (c.isSearch())
					return true;
			}
			return false;
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
			Set<String> printed = new HashSet<>();
			StringBuilder str = new StringBuilder();
			for (int i = 0; i < theContent.length; i++) {
				String contentStr = theContent[i].toString();
				if (printed.add(contentStr)) {
					if (i > 0)
						str.append(' ');
					str.append(theContent[i]);
				}
			}
			return str.toString();
		}
	}

	public static class PredicateFilter implements TableContentControl {
		private final String theCategory;
		private final Predicate<CharSequence> theFilter;

		public PredicateFilter(String category, Predicate<CharSequence> filter) {
			theCategory = category;
			theFilter = filter;
		}

		@Override
		public int[][] findMatches(ValueRenderer<?> category, CharSequence text) {
			if (!theCategory.equals(category.getName()))
				return NO_MATCH;
			return theFilter.test(text) ? new int[][] { { 0, text.length() } } : NO_MATCH;
		}

		@Override
		public boolean isSearch() {
			return true;
		}

		@Override
		public List<String> getRowSorting() {
			return null;
		}

		@Override
		public List<String> getColumnSorting() {
			return null;
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
		public boolean isSearch() {
			return false;
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
		public boolean isSearch() {
			return false;
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

	/**
	 * A simple sorted set of 2-length int array matches. This highly-specialized and highly-optimized class makes combining matches fast
	 * and easy. It assumes single-threaded usage, as it should only be ever used on the Java AWT Event Queue thread. For performance
	 * reasons however, this is never checked.
	 */
	public static class SortedIntArraySet {
		private static final int MAX_LIST_SIZE_FOR_CACHE = 40; // No sets with >20 matches will be put back into the cache
		private static final int MAX_LIST_QUEUE_SIZE = 20_000; // 10,000 matches
		private static final ArrayList<SortedIntArraySet> LIST_CACHE = new ArrayList<>();
		private static int LIST_CACHE_SIZE = 0;

		public static SortedIntArraySet get() {
			if (LIST_CACHE.isEmpty())
				return new SortedIntArraySet();
			else
				return LIST_CACHE.remove(LIST_CACHE.size() - 1);
		}

		public static int[][][] flush(SortedIntArraySet[] tableMatches) {
			if (tableMatches == null)
				return null;
			int[][][] matches = new int[tableMatches.length][][];
			for (int i = 0; i < tableMatches.length; i++)
				matches[i] = tableMatches[i] == null ? null : tableMatches[i].flush();
			return matches;
		}

		private int[] theMatches = new int[10];
		private int theSize; // This is double the number of matches in the set, as each match takes two spots

		public SortedIntArraySet add(int[] match) {
			int index = ArrayUtils.binarySearch(0, (theSize >> 1) - 1, idx -> {
				int dblIdx = idx << 1;
				int comp = Integer.compare(match[0], theMatches[dblIdx]);
				if (comp == 0)
					comp = Integer.compare(match[1], theMatches[dblIdx + 1]);
				return comp;
			});
			if (index >= 0)
				return this; // Duplicate
			boolean newCap = theSize == theMatches.length;
			int[] newMatches;
			if (newCap)
				newMatches = new int[theMatches.length << 1];
			else
				newMatches = theMatches;
			index = -index - 1;
			index = index << 1; // Double it
			int move = theSize - index;
			if (move >= 6) {
				System.arraycopy(theMatches, index, newMatches, index + 2, move);
			} else if (move > 0) {
				for (int i = theSize; i > index; i--)
					newMatches[i] = theMatches[i - 1];
			}
			if (newCap) {
				if (index >= 6) {
					System.arraycopy(theMatches, 0, newMatches, 0, index);
				} else if (index > 0) {
					for (int i = 0; i < index; i++)
						newMatches[i] = theMatches[i];
				}
				theMatches = newMatches;
			}
			newMatches[index] = match[0];
			newMatches[index + 1] = match[1];
			theSize += 2;
			return this;
		}

		public int[][] flush() {
			int matchCount = theSize >> 1;
					int[][] copy = new int[matchCount][2];
					for (int i = 0, j = 0; i < matchCount; i++, j++) {
						copy[i][0] = theMatches[j];
						j++;
						copy[i][1] = theMatches[j];
					}
					theSize = 0;
					int cap = theMatches.length;
					if (cap <= MAX_LIST_SIZE_FOR_CACHE && LIST_CACHE_SIZE < MAX_LIST_QUEUE_SIZE) {
						LIST_CACHE_SIZE += cap;
						LIST_CACHE.add(this);
					}
					return copy;
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder();
			for (int i = 0; i < theSize; i++) {
				if (i > 0)
					str.append(',');
				str.append('[');
				str.append(theMatches[i]);
				str.append(',');
				i++;
				str.append(theMatches[i]);
				str.append(']');
			}
			return str.toString();
		}
	}

	public static void main(String... args) {
		SettableValue<TableContentControl> control = SettableValue.build(TableContentControl.class).withValue(DEFAULT).build();
		control.noInitChanges().act(evt -> {
			System.out.println(evt.getNewValue());
		});
		ObservableCollection<Map<String, String>> rows = ObservableCollection
			.build(TypeTokens.get().keyFor(Map.class).<Map<String, String>> parameterized(String.class, String.class)).build();
		ObservableCollection<CategoryRenderStrategy<Map<String, String>, String>> columns = ObservableCollection
			.build(TypeTokens.get().keyFor(CategoryRenderStrategy.class)
				.<CategoryRenderStrategy<Map<String, String>, String>> parameterized(rows.getType(), TypeTokens.get().STRING))
			.build();
		SettableValue<List<String>> categories = SettableValue
			.build(TypeTokens.get().keyFor(List.class).<List<String>> parameterized(String.class)).withValue(new ArrayList<>())
			.build();
		categories.noInitChanges().act(evt -> {
			CollectionUtils.synchronize(columns, evt.getNewValue(), (crs, cat) -> crs.getName().equals(cat))//
			.simple(cat -> new CategoryRenderStrategy<Map<String, String>, String>(cat, TypeTokens.get().STRING, map -> {
				return map.get(cat);
			})//
				.formatText(v -> v == null ? "" : v).withMutation(mut -> {
					mut.mutateAttribute((map, v) -> map.put(cat, v)).asText(Format.TEXT).withRowUpdate(true);
				}))//
			.rightOrder()//
			.commonUses(true, false)//
			.adjust();
		});
		JFrame frame = ObservableSwingUtils.buildUI()//
			.systemLandF()//
			.withTitle(TableContentControl.class.getSimpleName() + " Tester")//
			.withSize(640, 900)//
			.withVContent(p -> p//
				.addTextField("Categories:", categories, new Format.ListFormat<>(Format.TEXT, ",", null), f -> f.fill())//
				.addTextField("Filter", control, FORMAT, f -> f.fill().modifyEditor(tf -> tf.setCommitOnType(true)))//
				.addTable(rows, table -> {
					table.fill().withFiltering(control).withColumns(columns)//
					.withAdd(() -> new HashMap<>(), null)//
					;
				})//
				).getWindow();
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setVisible(true);
		/*class TestValueRenderer implements ValueRenderer<String> {
			private final String theName;

			TestValueRenderer(String name) {
				theName = name;
			}

			@Override
			public int compare(String o1, String o2) {
				return StringUtils.compareNumberTolerant(o1, o2, true, true);
			}

			@Override
			public String getName() {
				return theName;
			}

			@Override
			public boolean searchGeneral() {
				return true;
			}

			@Override
			public CharSequence render(String value) {
				return value;
			}

			@Override
			public String toString() {
				return theName;
			}
		}
		try (Scanner scanner = new Scanner(System.in)) {
			String line = scanner.nextLine();
			List<ValueRenderer<String>> categories = Collections.emptyList();
			TableContentControl control = null;
			while (line != null) {
				if (line.startsWith("control:")) {
					control = parseContentControl(line.substring("control:".length()));
					System.out.println(control);
				} else if (line.startsWith("cat:")) {
					String[] split = line.substring("cat:".length()).split(",");
					categories = Arrays.stream(split).map(s -> new TestValueRenderer(s.trim())).collect(Collectors.toList());
					System.out.println("Categories set to " + categories);
				} else if (control == null) {
					System.err.println("No content control specified");
				} else {
					String[] split = line.split(",");
					if (split.length != categories.size()) {
						System.err.println("There are currently " + categories.size() + " categor" + (categories.size() == 1 ? "y" : "ies")
							+ ": " + categories + "--you gave " + split.length + ": " + Arrays.toString(split));
					} else {
						// TODO
						int[][][] match = control.findMatches(categories, split);
						if (match == null)
							System.out.println("No matches");
						else {
							System.out.println("Matches: " + ArrayUtils.toString(match));
							for (int i = 0; i < match.length; i++) {
								if (i > 0)
									System.out.print(", ");
								if (match[i] == null)
									System.out.print("()");
								else {
									int m = 0;
									for (int c = 0; c < split[i].length(); c++) {
										if (m < match[i].length && c == match[i][m][0])
											System.out.print("[");
										System.out.print(split[i].charAt(c));
										if (m < match[i].length && c == match[i][m][1]) {
											System.out.print("]");
											m++;
										}
									}
								}
								System.out.flush();
							}
							System.out.println();
						}
					}
				}
				line = scanner.nextLine();
			}
		}*/
	}
}
