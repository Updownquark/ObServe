package org.observe.util.swing;

import java.awt.EventQueue;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
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
import org.qommons.LambdaUtils;
import org.qommons.Named;
import org.qommons.StringUtils;
import org.qommons.TimeUtils;
import org.qommons.TimeUtils.ParsedDuration;
import org.qommons.TimeUtils.ParsedInstant;
import org.qommons.collect.BetterList;
import org.qommons.io.Format;

import com.google.common.reflect.TypeToken;

/**
 * A structure that may be parsed from a String and allows easy control of a table, including row filtering by many powerful and intuitive
 * patterns, row sorting, and column sorting.
 */
public interface TableContentControl {
	/** A sample tooltip to use for a text box whose value is a {@link TableContentControl} */
	public static final String TABLE_CONTROL_TOOLTIP = "<html>Enter text, numbers, or dates to filter the table rows.<br>\n"//
		+ "Use <b>XXX-XXX</b> to enter a range of numbers or dates.<br>\n"//
		+ "Use <b>abc*efg</b> to match any text with \"efg\" occurring after \"abc\".<br>\n"//
		+ "Use <b>\\XXX</b> to search via regular expressions.<br>\n"//
		+ "Use <b>sort:column1,column2</b> to sort the table rows.<br>\n"//
		+ "Use <b>columns:column1,column2</b> to sort the table columns.<br>\n"//
		+ "All filters are AND-ed.\n"//
		+ "</html>";

	/**
	 * Interface to interpret cell values from a filtering/sorting perspective. Typically a table column.
	 * 
	 * @param <E> The type of values this renderer understands
	 */
	public interface ValueRenderer<E> extends Named {
		/** @return Whether filtering should happen against values in this rendering */
		boolean searchGeneral();

		/**
		 * @param value The value to render
		 * @return The text to represent the value
		 */
		CharSequence render(E value);

		/**
		 * Compares two values for row sorting. This interface doesn't extend {@link Comparator} because of the <code>reverse</code>
		 * parameter, which is not just a trivial negation, but allows non-empty values to appear on top even for reversed sorting
		 *
		 * @param value1 The first value to compare
		 * @param value2 The second value to compare
		 * @param reverse Whether the sorting should be reversed
		 * @return The relative ordering of the two values
		 */
		int compare(E value1, E value2, boolean reverse);
	}

	/**
	 * A filtered table row
	 * 
	 * @param <E> The type of row value
	 */
	public static class FilteredValue<E> implements Comparable<FilteredValue<?>> {
		E value;
		SortedMatchSet[] matches;
		boolean hasMatch;

		/**
		 * @param value The row value
		 * @param columns The number of columns in the table
		 */
		public FilteredValue(E value, int columns) {
			this.value = value;
			matches = new SortedMatchSet[columns];
		}

		/** @return The row value */
		public E getValue() {
			return value;
		}

		/** @param value The row value */
		public void setValue(E value) {
			this.value = value;
		}

		/** @return Whether this match should be included in the filtered rows */
		public boolean hasMatch() {
			return hasMatch;
		}

		/** @return The number of columns in the table */
		public int getColumns() {
			return matches == null ? 0 : matches.length;
		}

		/**
		 * @param column The table column index
		 * @return The matched ranges of the rendered text in the given column
		 */
		public SortedMatchSet getMatches(int column) {
			return matches == null ? null : matches[column];
		}

		@Override
		public int compareTo(FilteredValue<?> o) {
			int thisMin = -1;
			int thisCount = 0;
			if (matches == null) {
				return o.matches == null ? 0 : 1;
			} else if (o.matches == null)
				return -1;
			for (SortedMatchSet cm : matches) {
				if (cm == null)
					continue;
				for (int i = 0; i < cm.size(); i++) {
					int start = cm.getStart(i);
					if (thisMin < 0 || start < thisMin)
						thisMin = start;
					thisCount++;
				}
			}
			int thatMin = -1;
			int thatCount = 0;
			for (SortedMatchSet cm : o.matches) {
				if (cm == null)
					continue;
				for (int i = 0; i < cm.size(); i++) {
					if (thisCount == 0)
						return 1;
					int start = cm.getStart(i);
					if (thatMin < 0 || start < thatMin)
						thatMin = start;
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

	/**
	 * Compares the rendered values of two rows in a column
	 *
	 * @param v1 The first value to compare
	 * @param v2 The second value to compare
	 * @param reverse Whether the search is to be reversed. This parameter is not the same as applying a negative to the return value, as
	 *        non-empty values are prioritized even with a reverse search.
	 * @return The sorting between the two renderings
	 */
	public static int compareColumnRenders(CharSequence v1, CharSequence v2, boolean reverse) {
		// The null and empty comparisons here are switched from typical, under the assumption that if the user is sorting by a column,
		// they are most likely interested in rows with values in this column
		// Also, note that these do not respect the reverse parameter,
		// again assuming that non-empty values are more relevant even in a reverse search
		if (v1 == null)
			return v2 == null ? 0 : 1;
		else if (v2 == null)
			return -1;
		else if (v1.length() == 0)
			return v2.length() == 0 ? 0 : 1;
		else if (v2.length() == 0)
			return -1;
		else if (v1.charAt(0) == '-' && v2.charAt(0) != '-')
			return reverse ? 1 : -1;
		else if (v2.charAt(0) == '-' && v1.charAt(0) != '-')
			return reverse ? -1 : 1;
		else if (v1.equals(v2))
			return 0;

		/* A nice idea here, but there's no way to optimize instant parsing enough for this to be responsive.
		try {
			ParsedInstant time1 = TimeUtils.parseInstant(col1, false, false, null);
			if (time1 != null) {
				ParsedInstant time2 = TimeUtils.parseInstant(col2, false, false, null);
				if (time2 != null) {
					int comp = time1.compareTo(time2);
					if (comp != 0)
						return comp;
					else if (time1.toString().length() == col1.length()) {
						if (time2.toString().length() == col2.length())
							return 0;
						else
							return -1;
					} else if (time2.toString().length() == col2.length())
						return 1;
					else
						return compareColumnRenders(//
							StringUtils.cheapSubSequence(col1, time1.toString().length(), col1.length()),
							StringUtils.cheapSubSequence(col2, time2.toString().length(), col2.length()));
				}
			}

			Duration parsing isn't so bad performance-wise, but it's so niche I decided it would likely only add confusion.
			E.g. in random strings, a value starting with '1d' would appear after a value starting with '1m'
			TimeUtils.ParsedDuration duration1 = TimeUtils.parseDuration(col1, false, false);
			if (duration1 != null) {
				TimeUtils.ParsedDuration duration2 = TimeUtils.parseDuration(col2, false, false);
				if (duration2 != null) {
					int comp = duration1.compareTo(duration2);
					if (comp != 0)
						return comp;
					else if (duration1.toString().length() == col1.length()) {
						if (duration2.toString().length() == col2.length())
							return 0;
						else
							return -1;
					} else if (duration2.toString().length() == col2.length())
						return 1;
					else
						return compareColumnRenders(//
							StringUtils.cheapSubSequence(col1, duration1.toString().length(), col1.length()),
							StringUtils.cheapSubSequence(col2, duration2.toString().length(), col2.length()));
				}
			}
		} catch (ParseException e) {
			System.err.println("Should not get here!");
			e.printStackTrace();
		}*/

		int comp;
		FoundDouble double1 = tryParseDouble(v1, 0);
		if (double1 != null) {
			FoundDouble double2 = tryParseDouble(v2, 0);
			if (double2 != null) {
				comp = Double.compare(double1.minValue, double2.minValue);
				if (comp != 0) { //
				} else if (double1.end == v1.length()) {
					if (double2.end == v2.length())
						comp = 0;
					else
						comp = -1;
				} else if (double2.end == v2.length())
					comp = 1;
				else
					comp = compareColumnRenders(//
						StringUtils.cheapSubSequence(v1, double1.end, v1.length()), //
						StringUtils.cheapSubSequence(v2, double2.end, v2.length()), //
						reverse);
			} else
				comp = StringUtils.compareNumberTolerant(v1, v2, true, true);
		} else
			comp = StringUtils.compareNumberTolerant(v1, v2, true, true);
		return reverse ? -comp : comp;
	}

	/**
	 * @param categories The categories of the row to filter on
	 * @param texts The text for each category of the row
	 * @return An array of text matches for each category
	 */
	default SortedMatchSet[] findMatches(List<? extends ValueRenderer<?>> categories, CharSequence[] texts) {
		SortedMatchSet[] matches = null;
		for (int i = 0; i < texts.length; i++) {
			SortedMatchSet match = findMatches(categories.get(i), texts[i]);
			if (match != null) {
				if (matches == null)
					matches = new SortedMatchSet[texts.length];
				matches[i] = match;
			}
		}
		return matches;
	}

	/**
	 * @param category The column/renderer that rendered the value
	 * @param text The rendered value
	 * @return The matched ranges for the given category/value
	 */
	SortedMatchSet findMatches(ValueRenderer<?> category, CharSequence text);

	/** @return Whether this control object is a search/filter (as opposed to just a sort command) */
	boolean isSearch();

	/** @return The columns by which rows should be sorted for this control */
	List<String> getRowSorting();

	/** @return The columns that should be displayed first for this control */
	List<String> getColumnSorting();

	/**
	 * @param other Another control object
	 * @return A control object that displays rows only when both this and the other control display them
	 */
	default TableContentControl and(TableContentControl other) {
		return new AndFilter(this, other);
	}

	/**
	 * @param other Another control object
	 * @return A control object that displays rows when either this or the other control display them
	 */
	default TableContentControl or(TableContentControl other) {
		return new OrFilter(this, other);
	}

	/**
	 * @param columnName The column name to sort by
	 * @param root Whether this control object is the root control
	 * @return A control object that filters identically to this, but whose sort for the given column is toggled: no sort or descending to
	 *         ascending, ascending to descending
	 */
	default TableContentControl toggleSort(String columnName, boolean root) {
		if (root)
			return new RowSorter(Collections.singletonList(columnName.toLowerCase().replace(" ", ""))).and(this);
		return this;
	}

	/**
	 * @param category The column to filter on
	 * @param filter The filter for the given column
	 * @return A control object that implements the given filter
	 */
	public static TableContentControl of(String category, Predicate<CharSequence> filter) {
		return new PredicateFilter(category, filter);
	}

	/** Pattern to search for int ranges in text */
	public static final Pattern INT_RANGE_PATTERN = Pattern.compile("(?<i1>\\d+)\\-(?<i2>\\d+)");

	/** A {@link Format} to parse {@link TableContentControl}s */
	public static final Format<TableContentControl> FORMAT = new Format<TableContentControl>() {
		@Override
		public void append(StringBuilder text, TableContentControl value) {
			if (value != null)
				text.append(value.toString());
		}

		@Override
		public TableContentControl parse(CharSequence controlText) throws ParseException {
			return TableContentControl.parseContentControl(controlText);
		}
	};

	/**
	 * Does some standard configuration on a text field for parsing {@link TableContentControl}s
	 *
	 * @param <F> The sub-type of field
	 * @param field The field to configure
	 * @param commitOnType Whether the text field should fire its new value each time the user types anything, or only when the user presses
	 *        enter or de-focuses the field
	 * @return The field
	 */
	public static <F extends PanelPopulation.FieldEditor<ObservableTextField<TableContentControl>, ?>> F configureSearchField(F field,
		boolean commitOnType) {
		return (F) field.fill().withTooltip(TableContentControl.TABLE_CONTROL_TOOLTIP).modifyEditor(tf2 -> tf2//
			.setCommitOnType(commitOnType).setEmptyText("Search or Sort...")
			.setIcon(ObservableSwingUtils.getFixedIcon(null, "/icons/search.png", 16, 16)));
	}

	/**
	 * Parses a {@link TableContentControl} from text
	 *
	 * @param controlText The text to parse
	 * @return The parsed {@link TableContentControl} represented by the text
	 */
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
			return splitFilters[0];
		else
			return new AndFilter(splitFilters);
	}

	/**
	 * Parses a single element of a {@link TableContentControl} from text
	 *
	 * @param filterText The text to parse
	 * @return The parsed {@link TableContentControl} element
	 */
	public static TableContentControl _parseFilterElement(String filterText) {
		ArrayList<TableContentControl> filters = new ArrayList<>();
		filters.add(new SimpleFilter(filterText));
		Matcher m = INT_RANGE_PATTERN.matcher(filterText);
		if (m.matches())
			filters.add(new IntRangeFilter(filterText, m.group("i1"), m.group("i2")));
		else {
			FoundDouble flt = TableContentControl.tryParseDouble(filterText, 0);
			if (flt != null) {
				if (flt.end == filterText.length())
					filters.add(new NumberRangeFilter(filterText, flt.minValue, flt.maxValue));
				else if (filterText.charAt(flt.end) == '-') {
					FoundDouble maxFlt = TableContentControl.tryParseDouble(filterText, flt.end + 1);
					if (maxFlt != null && maxFlt.end == filterText.length())
						filters.add(new NumberRangeFilter(filterText, flt.minValue, maxFlt.maxValue));
				}
			}
		}
		ParsedInstant time;
		try {
			time = TimeUtils.parseInstant(filterText, false, false, null);
		} catch (ParseException e) {
			throw new IllegalStateException(e); // Shouldn't happen
		}
		if (time != null) {
			if (time.toString().length() == filterText.length())
				filters.add(new DateFilter(time));
			else if (filterText.charAt(time.toString().length()) == '-') {
				ParsedInstant maxTime;
				int maxStart = time.toString().length() + 1;
				try {
					maxTime = TimeUtils.parseInstant(filterText.substring(maxStart), true, false, null);
				} catch (ParseException e) {
					throw new IllegalStateException(e); // Shouldn't happen
				}
				if (maxTime != null && maxStart + maxTime.toString().length() == filterText.length()) {
					filters.add(new DateRangeFilter(filterText, time, maxTime));
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
				duration = TimeUtils.parseDuration(filterText, false);
			} catch (ParseException e) {
				duration = null;
			}
			if (duration != null)
				filters.add(new DurationFilter(duration));
		}
		if (dashIdx > 0) {
			try {
				ParsedDuration minDuration = TimeUtils.parseDuration(filterText.subSequence(0, dashIdx), false);
				ParsedDuration maxDuration = TimeUtils.parseDuration(filterText.subSequence(dashIdx + 1, filterText.length()), false);
				filters.add(new DurationRangeFilter(filterText, minDuration, maxDuration));
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

	/**
	 *
	 * @param <E> The type of rows in the table
	 * @param values The unfiltered rows for the table
	 * @param render Supplies a renderer for each column in the table
	 * @param filter The {@link TableContentControl} control value to apply
	 * @param until An observable to release the subscriptions that this method creates
	 * @return The filtered rows for the table
	 */
	public static <E> ObservableCollection<FilteredValue<E>> applyRowControl(ObservableCollection<E> values,
		Supplier<? extends Collection<? extends ValueRenderer<? super E>>> render, ObservableValue<? extends TableContentControl> filter,
			Observable<?> until) {
		List<ValueRenderer<? super E>> rendererList = new ArrayList<>();
		/*int[] tests = new int[2]; // DEBUGGING
		int[] compares = new int[2];
		Causable.CausableKey key = Causable.key((cause, __) -> {
			System.out.println((tests[0] + tests[1]) + " values tested, " + tests[0] + " passed");
			System.out.println((compares[0] + compares[1]) + " comparisons, " + compares[0] + " reordered");
			tests[0] = tests[1] = 0;
			compares[0] = compares[1] = 0;
		});
		filter.changes().act(evt -> {
			evt.getRootCausable().onFinish(key);
		});*/
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
				CharSequence[] texts = new CharSequence[rendererList.size()];
				i = 0;
				for (ValueRenderer<? super E> r : rendererList)
					texts[i++] = r.render(v.value);
				if (f.isSearch()) {
					v.hasMatch = false;
					v.matches = f.findMatches(rendererList, texts);
					if (v.matches != null) {
						for (i = 0; !v.hasMatch && i < texts.length; i++) {
							if (v.matches[i] != null && v.matches[i].size() > 0)
								v.hasMatch = true;
						}
					}
				} else
					v.hasMatch = true; // No filtering
				// tests[v.hasMatch ? 0 : 1]++;
				return v;
			}, "toFilterValue", null)))//
			.filter(LambdaUtils.printableFn(fv -> fv.hasMatch() ? null : "No match", "match", null))//
			.sorted(LambdaUtils.printableComparator((fv1, fv2) -> {
				List<String> sorting = filter.get().getRowSorting();
				if (sorting == null || sorting.isEmpty())
					return 0;
				int comp = 0;
				Collection<? extends ValueRenderer<? super E>> renders = render.get();
				for (String s : sorting) {
					for (ValueRenderer<? super E> r : renders) {
						int rSort = RowSorter.sortCategoryMatches(s, r.getName());
						if (rSort != 0) {
							comp = r.compare(fv1.value, fv2.value, rSort < 0);
							break;
						}
					}
					if (comp != 0)
						break;
				}
				// compares[comp <= 0 ? 0 : 1]++;
				return comp;
			}, () -> "rowSorting")).collectActive(until);
	}

	/**
	 * @param <R> The type of rows in the table
	 * @param <C> The type of columns in the table
	 * @param columns The table columns to sort
	 * @param filter The {@link TableContentControl} control value to apply
	 * @param until An observable to release the subscriptions that this method creates
	 * @return The sorted columns to use for the table
	 */
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

	/** Control object that does no filtering or sorting */
	public static final TableContentControl DEFAULT = new TableContentControl() {
		@Override
		public SortedMatchSet[] findMatches(List<? extends ValueRenderer<?>> category, CharSequence[] texts) {
			return null;
		}

		@Override
		public SortedMatchSet findMatches(ValueRenderer<?> category, CharSequence value) {
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
		public TableContentControl and(TableContentControl other) {
			return other;
		}

		@Override
		public TableContentControl or(TableContentControl other) {
			if (other.isSearch())
				return this;
			else
				return other;
		}

		@Override
		public TableContentControl toggleSort(String columnName, boolean root) {
			return new RowSorter(Collections.singletonList(columnName.toLowerCase().replace(" ", "")));
		}

		@Override
		public String toString() {
			return "";
		}
	};

	/** Simple text filter. Tolerates differences in whitespace and case. */
	public static class SimpleFilter implements TableContentControl {
		private final String theMatcher;

		/** @param matcher The search string */
		public SimpleFilter(String matcher) {
			if (matcher.isEmpty())
				throw new IllegalArgumentException("Cannot make an empty simple matcher");
			theMatcher = matcher;
		}

		@Override
		public SortedMatchSet findMatches(ValueRenderer<?> category, CharSequence toSearch) {
			if (!category.searchGeneral())
				return null;
			SortedMatchSet matches = null;
			for (int s = 0; s < toSearch.length(); s++) {
				int c = s, m = 0;
				while (m < theMatcher.length() && c < toSearch.length()) {
					char matchCh = theMatcher.charAt(m);
					char testCh = toSearch.charAt(c);
					int diff = matchCh - testCh;
					if (diff == 0) {//
					} else if (diff == StringUtils.a_MINUS_A && matchCh >= 'a' && matchCh <= 'z') {//
					} else if (diff == -StringUtils.a_MINUS_A && matchCh >= 'A' && matchCh <= 'Z') {//
					} else if (m > 0 && Character.isWhitespace(testCh) && !Character.isWhitespace(matchCh)) {
						c++;
						continue;
					} else
						break;
					c++;
					m++;
				}
				if (m == theMatcher.length()) {
					if (matches == null)
						matches = new SortedMatchSet();
					matches.add(s, c);
				}
			}
			return matches;
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

	/** Matches empty text */
	public static class EmptyFilter implements TableContentControl {
		@Override
		public SortedMatchSet findMatches(ValueRenderer<?> category, CharSequence text) {
			if (text == null || text.length() == 0)
				return new SortedMatchSet(1).add(0, 0); // [[0, 0]]
			return null;
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

	/** A filter on a specific category/column */
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
			public int compare(E o1, E o2, boolean reverse) {
				return renderer.compare(o1, o2, reverse);
			}
		}

		private final String theCategory;
		private final TableContentControl theFilter;

		/**
		 * @param category The name of the category to filter on
		 * @param filter The filter to apply for the given category
		 */
		public CategoryFilter(String category, TableContentControl filter) {
			theCategory = category;
			theFilter = filter;
		}

		/** @return The name of the category this filter looks for */
		public String getCategory() {
			return theCategory;
		}

		/** @return The filter applied to the category's values */
		public TableContentControl getFilter() {
			return theFilter;
		}

		@Override
		public boolean isSearch() {
			return true;
		}

		@Override
		public SortedMatchSet findMatches(ValueRenderer<?> category, CharSequence text) {
			if (!categoryMatches(theCategory, category.getName()))
				return null;
			return theFilter.findMatches(new UnfilteredRenderer<>(category), text);
		}

		/**
		 * @param category The name of the category to search for
		 * @param test The name of the category to test
		 * @return Whether the given test category matches the given category search string
		 */
		public static boolean categoryMatches(CharSequence category, CharSequence test) {
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
			StringBuilder str = new StringBuilder().append(theFilter.toString());
			boolean quote = str.length() >= 2 && str.charAt(0) == '"' && str.charAt(str.length() - 1) == '"';
			if (quote)
				str.deleteCharAt(0).deleteCharAt(str.length() - 1);
			str.insert(0, ':').insert(0, theCategory);
			if (quote)
				str.insert(0, '"').append('"');
			return str.toString();
		}
	}

	/** A simple pattern filter that interprets '*' as any character sequence */
	public static class SimplePatternFilter implements TableContentControl {
		private static final int CASE_DIFF = 'a' - 'A';

		private final List<String> theSequence;

		/** @param sequence The character sequences between '*' wildcards to match */
		public SimplePatternFilter(List<String> sequence) {
			theSequence = sequence;
		}

		/** @return The character sequences between '*' wildcards that this filter searches for */
		public List<String> getSequence() {
			return theSequence;
		}

		@Override
		public SortedMatchSet findMatches(ValueRenderer<?> category, CharSequence text) {
			if (!category.searchGeneral())
				return null;
			if (theSequence.isEmpty()) {
				return new SortedMatchSet(1).add(0, text.length());
			}
			SortedMatchSet matches = null;
			TextMatch match = find(text, 0);
			if (match != null) {
				matches = new SortedMatchSet();
			}
			while (match != null) {
				matches.add(match);
				match = find(text, match.end);
			}
			return matches;
		}

		private TextMatch find(CharSequence text, int start) {
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
			return start >= 0 ? new TextMatch(start, end) : null;
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
			return StringUtils.print("*", theSequence, s -> s).toString();
		}
	}

	/** A formal regex filter */
	public static class FullPatternFilter implements TableContentControl {
		private final Pattern thePattern;

		/** @param pattern The pattern to search with */
		public FullPatternFilter(Pattern pattern) {
			thePattern = pattern;
		}

		/** @return The pattern this filter uses to search */
		public Pattern getPattern() {
			return thePattern;
		}

		@Override
		public SortedMatchSet findMatches(ValueRenderer<?> category, CharSequence text) {
			if (!category.searchGeneral())
				return null;
			SortedMatchSet matches = null;
			Matcher matcher = thePattern.matcher(text);
			int start = 0;
			while (matcher.find(start)) {
				if (matcher.end() == matcher.start()) { // Don't report empty matches or get stuck in the loop
					start = matcher.end() + 1;
					continue;
				}
				if (matches == null)
					matches = new SortedMatchSet();
				matches.add(matcher.start(), matcher.end());
				start = matcher.end();
			}
			return matches;
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

	/** Represents a number in a text sequence */
	public class FoundDouble {
		/** The minimum double value that the text could represent */
		public final double minValue;
		/** The maximum double value that the text could represent */
		public final double maxValue;
		/** The end of the sub-sequence that matched to this number */
		public final int end;

		/**
		 * @param minValue The minimum double value that the text could represent
		 * @param maxValue The maximum double value that the text could represent
		 * @param end The end of the sub-sequence that matched to this number
		 */
		public FoundDouble(double minValue, double maxValue, int end) {
			this.minValue = minValue;
			this.maxValue = maxValue;
			this.end = end;
		}
	}

	/**
	 * @param text The text to search
	 * @param start The index to start the search at
	 * @return The number found in the text at the given index, or null if the sub-sequence at <code>start</code> does not start with a
	 *         number
	 */
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
			boolean hasExp=false;
			int preEnd=end;
			end++;
			expStart++;
			expEnd++;
			if (end < text.length() && text.charAt(end) == '-') {
				end++;
				expEnd++;
			}
			while (end < text.length() && text.charAt(end) >= '0' && text.charAt(end) <= '9') {
				hasExp=true;
				end++;
				expEnd++;
			}
			if (!hasExp || expEnd - expStart >= 20) // Can't accommodate massive exponents
				end = expStart = expEnd = preEnd;
		}
		double minValue = Double.parseDouble(text.subSequence(start, end).toString());
		long exp = (expEnd == expStart) ? 0 : Long.parseLong(text.subSequence(expStart, expEnd).toString());
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

	/** Searches for a real (decimal) number in a range */
	public static class NumberRangeFilter implements TableContentControl {
		private final String theText;
		private final double theMinValue;
		private final double theMaxValue;

		/**
		 * @param text The text that the filter was parsed from, or null to generate the text representation
		 * @param minValue The minimum value to search for
		 * @param maxValue The maximum value to search for
		 */
		public NumberRangeFilter(String text, double minValue, double maxValue) {
			int comp = Double.compare(minValue, maxValue);
			theMinValue = (comp <= 0 ? minValue : maxValue);
			theMaxValue = (comp <= 0 ? maxValue : minValue);
			if (text != null)
				theText = text;
			else if (theMinValue == theMaxValue)
				theText = String.valueOf(theMinValue);
			else
				theText = new StringBuilder().append(theMinValue).append('-').append(theMaxValue).toString();
		}

		/** @return The minimum value matched by this filter */
		public double getMinValue() {
			return theMinValue;
		}

		/** @return The maximum value matched by this filter */
		public double getMaxValue() {
			return theMaxValue;
		}

		@Override
		public SortedMatchSet findMatches(ValueRenderer<?> category, CharSequence text) {
			if (!category.searchGeneral())
				return null;
			SortedMatchSet matches = null;
			for (int i = 0; i < text.length(); i++) {
				FoundDouble found = TableContentControl.tryParseDouble(text, i);
				if (found == null)
					continue;
				int comp = Double.compare(found.maxValue, theMinValue);
				if (comp > 0 || (comp == 0 && theMinValue == theMaxValue)) {
					comp = Double.compare(found.minValue, theMaxValue);
					if (comp < 0 || (comp == 0 && theMinValue == theMaxValue)) {
						if (matches == null)
							matches = new SortedMatchSet();
						matches.add(i, found.end);
					}
				}
				i += found.end - 1; // Loop will increment
			}
			return matches;
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
			return theText;
		}
	}

	/** Searches for an integer in a range */
	public static class IntRangeFilter implements TableContentControl {
		private final String theText;
		private final String theLow;
		private final String theHigh;

		/**
		 * @param text The text that the filter was parsed from, or null to generate the text representation
		 * @param low The minimum value to search for
		 * @param high The maximum value to search for
		 */
		public IntRangeFilter(String text, String low, String high) {
			int comp = StringUtils.compareNumberTolerant(low, high, true, true);
			theLow = comp <= 0 ? low : high;
			theHigh = comp <= 0 ? high : low;
			if (text != null)
				theText = text;
			else if (theLow == theHigh)
				theText = String.valueOf(theLow);
			else
				theText = new StringBuilder().append(theLow).append('-').append(theHigh).toString();
		}

		/** @return The minimum value matched by this filter */
		public String getLow() {
			return theLow;
		}

		/** @return The maximum value matched by this filter */
		public String getHigh() {
			return theHigh;
		}

		@Override
		public SortedMatchSet findMatches(ValueRenderer<?> category, CharSequence toSearch) {
			if (!category.searchGeneral())
				return null;
			SortedMatchSet matches = null;
			int digitStart = -1;
			for (int c = 0; c < toSearch.length(); c++) {
				if (Character.isDigit(toSearch.charAt(c))) {
					if (digitStart < 0)
						digitStart = c;
				} else if (digitStart >= 0) {
					if (isIncluded(toSearch, digitStart, c)) {
						if (matches == null)
							matches = new SortedMatchSet();
						matches.add(digitStart, c);
					}
					digitStart = -1;
				}
			}
			if (digitStart >= 0 && isIncluded(toSearch, digitStart, toSearch.length())) {
				if (matches == null)
					matches = new SortedMatchSet();
				matches.add(digitStart, toSearch.length());
			}
			return matches;
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
			return theText;
		}
	}

	/** Searches for a date */
	public static class DateFilter implements TableContentControl {
		private final TimeUtils.ParsedInstant theTime;

		/** @param time The date to search for */
		public DateFilter(ParsedInstant time) {
			theTime = time;
		}

		/** @return The time that this filter searches for */
		public TimeUtils.ParsedInstant getTime() {
			return theTime;
		}

		@Override
		public SortedMatchSet findMatches(ValueRenderer<?> category, CharSequence text) {
			if (!category.searchGeneral())
				return null;
			SortedMatchSet matches = null;
			for (int i = 0; i < text.length();) {
				TimeUtils.ParsedInstant time;
				try {
					time = TimeUtils.parseInstant(//
						text.subSequence(i, text.length()), false, false, null);
				} catch (ParseException e) {
					e.printStackTrace();
					return null;
				}
				if (time != null && theTime.isComparable(time) && theTime.compareTo(time) == 0) {
					if (matches == null)
						matches = new SortedMatchSet();
					matches.add(i, i + time.toString().length());
					i += time.toString().length();
				} else
					i++;
			}
			return matches;
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

	/** Searches for a date in a range */
	public static class DateRangeFilter implements TableContentControl {
		private final String theText;
		private final TimeUtils.ParsedInstant theMinTime;
		private final TimeUtils.ParsedInstant theMaxTime;

		/**
		 * @param text The text that the filter was parsed from, or null to generate the text representation
		 * @param minTime The minimum time to search for
		 * @param maxTime The maximum time to search for
		 */
		public DateRangeFilter(String text, ParsedInstant minTime, ParsedInstant maxTime) {
			theMinTime = minTime;
			theMaxTime = maxTime;
			if (text != null)
				theText = text;
			else if (theMinTime == theMaxTime)
				theText = String.valueOf(theMinTime);
			else
				theText = new StringBuilder().append(theMinTime).append('-').append(theMaxTime).toString();
		}

		/** @return The minimum time matched by this filter */
		public TimeUtils.ParsedInstant getMinTime() {
			return theMinTime;
		}

		/** @return The maximum time matched by this filter */
		public TimeUtils.ParsedInstant getMaxTime() {
			return theMaxTime;
		}

		@Override
		public SortedMatchSet findMatches(ValueRenderer<?> category, CharSequence text) {
			if (!category.searchGeneral())
				return null;
			SortedMatchSet matches = null;
			for (int i = 0; i < text.length();) {
				if (Character.isDigit(text.charAt(i))) {
					TimeUtils.ParsedInstant time;
					try {
						time = TimeUtils.parseInstant(text.subSequence(i, text.length()), false, false, null);
					} catch (ParseException e) {
						throw new IllegalStateException();
					}
					if (time != null && theMinTime.isComparable(time) && theMinTime.compareTo(time) <= 0
						&& theMaxTime.compareTo(time) >= 0) {
						if (matches == null)
							matches = new SortedMatchSet();
						matches.add(i, i + time.toString().length());
						i += time.toString().length();
					} else
						i++;
				} else
					i++;
			}
			return matches;
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
			return theText;
		}
	}

	/** Searches for a duration */
	public static class DurationFilter implements TableContentControl {
		private final TimeUtils.ParsedDuration theDuration;

		/** @param duration The duration to search for */
		public DurationFilter(ParsedDuration duration) {
			theDuration = duration;
		}

		/** @return The date this filter searches for */
		public TimeUtils.ParsedDuration getDuration() {
			return theDuration;
		}

		@Override
		public SortedMatchSet findMatches(ValueRenderer<?> category, CharSequence text) {
			if (!category.searchGeneral())
				return null;
			TimeUtils.ParsedDuration duration;
			try {
				duration = TimeUtils.parseDuration(text, false);
				if (duration == null)
					return null;
			} catch (ParseException e) {
				e.printStackTrace();
				return null;
			}
			if (theDuration.compareTo(duration) == 0) {
				return new SortedMatchSet(1).add(0, text.length());
			}
			return null;
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

	/** Searches for a duration in a range */
	public static class DurationRangeFilter implements TableContentControl {
		private final String theText;
		private final TimeUtils.ParsedDuration theMinDuration;
		private final TimeUtils.ParsedDuration theMaxDuration;

		/**
		 * @param text The text that the filter was parsed from, or null to generate the text representation
		 * @param minDuration The minimum duration to search for
		 * @param maxDuration The maximum duration to search for
		 */
		public DurationRangeFilter(String text, ParsedDuration minDuration, ParsedDuration maxDuration) {
			theMinDuration = minDuration;
			theMaxDuration = maxDuration;
			if (text != null)
				theText = text;
			else if (theMinDuration == theMaxDuration)
				theText = String.valueOf(theMinDuration);
			else
				theText = new StringBuilder().append(theMinDuration).append('-').append(theMaxDuration).toString();
		}

		/** @return The minimum duration matched by this filter */
		public TimeUtils.ParsedDuration getMinDuration() {
			return theMinDuration;
		}

		/** @return The maximum duration matched by this filter */
		public TimeUtils.ParsedDuration getMaxDuration() {
			return theMaxDuration;
		}

		@Override
		public SortedMatchSet findMatches(ValueRenderer<?> category, CharSequence text) {
			if (!category.searchGeneral())
				return null;
			TimeUtils.ParsedDuration duration;
			try {
				duration = TimeUtils.parseDuration(text, false);
				if (duration == null)
					return null;
			} catch (ParseException e) {
				e.printStackTrace();
				return null;
			}
			if (theMinDuration.compareTo(duration) <= 0 && theMaxDuration.compareTo(duration) >= 0) {
				return new SortedMatchSet(1).add(0, text.length());
			}
			return null;
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
			return theText;
		}
	}

	/** A filter that matches rows that are matched by any of its component matchers */
	public static class OrFilter implements TableContentControl {
		private final List<TableContentControl> theContent;

		/** @param filters The component filters for this OR filter */
		public OrFilter(TableContentControl... filters) {
			theContent = BetterList.of(filters);
		}

		/** @return This OR filter's component filters */
		public List<TableContentControl> getContent() {
			return theContent;
		}

		@Override
		public SortedMatchSet[] findMatches(List<? extends ValueRenderer<?>> categories, CharSequence[] texts) {
			SortedMatchSet[] matches = null;
			for (TableContentControl content : theContent) {
				SortedMatchSet[] match = content.findMatches(categories, texts);
				if (match == null)
					continue;
				if (matches == null)
					matches = new SortedMatchSet[texts.length];
				for (int i = 0; i < match.length; i++) {
					SortedMatchSet cm = match[i];
					if (cm != null) {
						if (matches[i] == null)
							matches[i] = cm;
						else
							matches[i].addAll(cm);
					}
				}
			}
			return matches;
		}

		@Override
		public SortedMatchSet findMatches(ValueRenderer<?> category, CharSequence value) {
			SortedMatchSet matches = null;
			for (TableContentControl content : theContent) {
				SortedMatchSet match = content.findMatches(category, value);
				if (match != null) {
					if (matches == null)
						matches = match;
					else
						matches.addAll(match);
				}
			}
			return matches == null ? null : matches;
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
		public TableContentControl toggleSort(String columnName, boolean root) {
			TableContentControl[] newContent = null;
			for (int i = 0; i < theContent.size(); i++) {
				TableContentControl replaced = theContent.get(i).toggleSort(columnName, false);
				if (replaced != theContent.get(i)) {
					if (newContent == null)
						newContent = theContent.toArray(new TableContentControl[theContent.size()]);
					newContent[i] = replaced;
				}
			}
			if (newContent != null)
				return new OrFilter(newContent);
			return TableContentControl.super.toggleSort(columnName, root);
		}

		@Override
		public TableContentControl or(TableContentControl other) {
			TableContentControl[] newContent = new TableContentControl[theContent.size() + 1];
			theContent.toArray(newContent);
			newContent[theContent.size()] = other;
			return new OrFilter(newContent);
		}

		@Override
		public String toString() {
			Set<String> printed = new HashSet<>();
			StringBuilder str = new StringBuilder();
			for (int i = 0; i < theContent.size(); i++) {
				String contentStr = theContent.get(i).toString();
				if (printed.add(contentStr)) {
					if (i > 0)
						str.append(' ');
					str.append(theContent.get(i));
				}
			}
			return str.toString();
		}
	}

	/** A filter that matches rows that are matched by all of its component matchers */
	public static class AndFilter implements TableContentControl {
		private final List<TableContentControl> theContent;

		/** @param filters The component filters for this AND filter */
		public AndFilter(TableContentControl... filters) {
			theContent = BetterList.of(filters);
		}

		/** @return This AND filter's component filters */
		public List<TableContentControl> getContent() {
			return theContent;
		}

		@Override
		public SortedMatchSet[] findMatches(List<? extends ValueRenderer<?>> categories, CharSequence[] texts) {
			SortedMatchSet[] matches = null;
			for (TableContentControl content : theContent) {
				SortedMatchSet[] match = content.findMatches(categories, texts);
				if (match == null && content.isSearch())
					return null;
				if (match == null)
					continue;
				if (matches == null)
					matches = new SortedMatchSet[texts.length];
				for (int i = 0; i < match.length; i++) {
					SortedMatchSet cm = match[i];
					if (cm != null) {
						if (matches[i] == null)
							matches[i] = cm;
						else
							matches[i].addAll(cm);
					}
				}
			}
			return matches;
		}

		@Override
		public SortedMatchSet findMatches(ValueRenderer<?> category, CharSequence value) {
			SortedMatchSet matches = null;
			for (TableContentControl content : theContent) {
				SortedMatchSet match = content.findMatches(category, value);
				if (match == null)
					return null;
				if (matches == null)
					matches = match;
				else
					matches.addAll(match);
			}
			if (matches == null)
				return null;
			return matches;
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
		public TableContentControl and(TableContentControl other) {
			TableContentControl[] newContent = new TableContentControl[theContent.size() + 1];
			theContent.toArray(newContent);
			newContent[theContent.size()] = other;
			return new AndFilter(newContent);
		}

		@Override
		public TableContentControl toggleSort(String columnName, boolean root) {
			TableContentControl[] newContent = null;
			for (int i = 0; i < theContent.size(); i++) {
				TableContentControl replaced = theContent.get(i).toggleSort(columnName, false);
				if (replaced != theContent.get(i)) {
					if (newContent == null)
						newContent = theContent.toArray(new TableContentControl[theContent.size()]);
					newContent[i] = replaced;
				}
			}
			if (newContent != null)
				return new AndFilter(newContent);
			return TableContentControl.super.toggleSort(columnName, root);
		}

		@Override
		public String toString() {
			Set<String> printed = new HashSet<>();
			StringBuilder str = new StringBuilder();
			for (int i = 0; i < theContent.size(); i++) {
				String contentStr = theContent.get(i).toString();
				if (printed.add(contentStr)) {
					if (i > 0)
						str.append(' ');
					str.append(theContent.get(i));
				}
			}
			return str.toString();
		}
	}

	/** A filter that uses a custom predicate on rendered text */
	public static class PredicateFilter implements TableContentControl {
		private final String theCategory;
		private final Predicate<CharSequence> theFilter;

		/**
		 * @param category The category to search in
		 * @param filter The text filter to search with
		 */
		public PredicateFilter(String category, Predicate<CharSequence> filter) {
			theCategory = category;
			theFilter = filter;
		}

		/** @return The category this filter searches with */
		public String getCategory() {
			return theCategory;
		}

		/** @return The text filter used to search */
		public Predicate<CharSequence> getFilter() {
			return theFilter;
		}

		@Override
		public SortedMatchSet findMatches(ValueRenderer<?> category, CharSequence text) {
			if (!theCategory.equals(category.getName()))
				return null;
			return theFilter.test(text) ? new SortedMatchSet(1).add(0, text.length()) : null;
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

	/** Content control the sorts table rows by category values */
	public static class RowSorter implements TableContentControl {
		private final List<String> theSorting;

		/** @param sorting The categories to sort by */
		public RowSorter(List<String> sorting) {
			theSorting = sorting;
		}

		/** @return The category sorting of this content control */
		public List<String> getSorting() {
			return theSorting;
		}

		@Override
		public SortedMatchSet[] findMatches(List<? extends ValueRenderer<?>> categories, CharSequence[] texts) {
			return null;
		}

		@Override
		public SortedMatchSet findMatches(ValueRenderer<?> category, CharSequence text) {
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
		public TableContentControl toggleSort(String columnName, boolean root) {
			List<String> newSorting = null;
			for (int s = 0; s < theSorting.size(); s++) {
				String sort = theSorting.get(s);
				int match = sortCategoryMatches(sort, columnName);
				if (match != 0) {
					if (newSorting == null)
						newSorting = new ArrayList<>(theSorting);
					switch (sort.charAt(0)) {
					case '-':
						sort = sort.substring(1);
						break;
					case '+':
						sort = "-" + sort.substring(1);
						break;
					default:
						sort = "-" + sort;
						break;
					}
					newSorting.set(s, sort);
				}
			}
			if (newSorting == null && root) {
				newSorting = new ArrayList<>(theSorting.size() + 1);
				newSorting.add(columnName.toLowerCase().replace(" ", ""));
				newSorting.addAll(theSorting);
			}
			if (newSorting != null)
				return new RowSorter(Collections.unmodifiableList(newSorting));
			return TableContentControl.super.toggleSort(columnName, root);
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder("sort:");
			StringUtils.print(str, ",", theSorting, StringBuilder::append);
			if (str.indexOf(" ") >= 0)
				str.insert(0, '"').append('"');
			return str.toString();
		}

		/**
		 * @param category The category to sort by
		 * @param test The category to test
		 * @return 0 if the given sort category does not match the test category, otherwise 1 to sort the category ascending, -1 to sort
		 *         descending
		 */
		public static int sortCategoryMatches(String category, String test) {
			if (CategoryFilter.categoryMatches(category, test))
				return 1;
			int sortPrefix = 0;
			if (category.charAt(0) == '-')
				sortPrefix = -1;
			else if (category.charAt(0) == '+')
				sortPrefix = 1;
			if (sortPrefix == 0)
				return 0;
			else if (CategoryFilter.categoryMatches(StringUtils.cheapSubSequence(category, 1, category.length()), test))
				return sortPrefix;
			return 0;
		}
	}

	/** Prioritizes and sorts columns in a table */
	public static class ColumnSorter implements TableContentControl {
		private final List<String> theColumns;

		/** @param columns The columns to prioritize/sort */
		public ColumnSorter(List<String> columns) {
			theColumns = columns;
		}

		/** @return The columns prioritized/sorted by this control */
		public List<String> getColumns() {
			return theColumns;
		}

		@Override
		public SortedMatchSet[] findMatches(List<? extends ValueRenderer<?>> categories, CharSequence[] texts) {
			return null;
		}

		@Override
		public SortedMatchSet findMatches(ValueRenderer<?> category, CharSequence text) {
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
			StringUtils.print(str, ",", theColumns, StringBuilder::append);
			if (str.indexOf(" ") >= 0)
				str.insert(0, '"').append('"');
			return str.toString();
		}
	}

	/**
	 * Displays a frame containing a filterable table with pre-populated row and column data. For testing
	 *
	 * @param args Command-line arguments, ignored
	 */
	public static void main(String... args) {
		SettableValue<TableContentControl> control = SettableValue.build(TableContentControl.class).onEdt().withValue(DEFAULT).build();
		control.noInitChanges().act(evt -> {
			System.out.println(evt.getNewValue());
		});
		ObservableCollection<Map<String, String>> rows = ObservableCollection
			.build(TypeTokens.get().keyFor(Map.class).<Map<String, String>> parameterized(String.class, String.class)).onEdt().build();
		ObservableCollection<CategoryRenderStrategy<Map<String, String>, String>> columns = ObservableCollection
			.build(TypeTokens.get().keyFor(CategoryRenderStrategy.class)
				.<CategoryRenderStrategy<Map<String, String>, String>> parameterized(rows.getType(), TypeTokens.get().STRING))
			.onEdt().build();
		EventQueue.invokeLater(() -> {
			columns.add(new CategoryRenderStrategy<Map<String, String>, String>("A", TypeTokens.get().STRING, map -> {
				return map.get("A");
			})//
				.formatText(v -> v == null ? "" : v).withMutation(mut -> {
					mut.mutateAttribute((map, v) -> map.put("A", v)).asText(Format.TEXT).withRowUpdate(true);
				}).withWidths(50, 100, 150));
			columns.add(new CategoryRenderStrategy<Map<String, String>, String>("B", TypeTokens.get().STRING, map -> {
				return map.get("B");
			})//
				.formatText(v -> v == null ? "" : v).withMutation(mut -> {
					mut.mutateAttribute((map, v) -> map.put("B", v)).asText(Format.TEXT).withRowUpdate(true);
				}).withWidths(50, 100, 150));
			columns.add(new CategoryRenderStrategy<Map<String, String>, String>("C", TypeTokens.get().STRING, map -> {
				return map.get("C");
			})//
				.formatText(v -> v == null ? "" : v).withMutation(mut -> {
					mut.mutateAttribute((map, v) -> map.put("C", v)).asText(Format.TEXT).withRowUpdate(true);
				}).withWidths(50, 150, 550));
			columns.add(new CategoryRenderStrategy<Map<String, String>, String>("D", TypeTokens.get().STRING, map -> {
				return map.get("D");
			})//
				.formatText(v -> v == null ? "" : v).withMutation(mut -> {
					mut.mutateAttribute((map, v) -> map.put("D", v)).asText(Format.TEXT).withRowUpdate(true);
				}).withWidths(50, 100, 150));
			columns.add(new CategoryRenderStrategy<Map<String, String>, String>("OneSAF Type", TypeTokens.get().STRING, map -> {
				return map.get("OneSAF Type");
			})//
				.formatText(v -> v == null ? "" : v).withMutation(mut -> {
					mut.mutateAttribute((map, v) -> map.put("OneSAF Type", v)).asText(Format.TEXT).withRowUpdate(true);
				}).withWidths(50, 100, 150));
			SimpleDateFormat dateFormat = new SimpleDateFormat("EEE MMM dd, yyyy HH:mm:ss");
			Calendar cal = Calendar.getInstance();
			Random random = new Random();
			int rowCount = 10_000;
			int lastPct = 0;
			for (int i = 0; i < rowCount; i++) {
				int pct = i * 100 / rowCount;
				if (pct > lastPct) {
					if (pct % 10 == 0)
						System.out.print(pct);
					else
						System.out.print('.');
					System.out.flush();
					lastPct = pct;
				}
				long r = random.nextLong();
				Map<String, String> row = new HashMap<>();
				if (random.nextDouble() < 0.99)
					row.put("A", "" + r);
				if (random.nextDouble() < 0.99)
					row.put("B", Long.toHexString(r));
				if (random.nextDouble() < 0.99) {
					cal.setTimeInMillis(r);
					int year = cal.get(Calendar.YEAR);
					if (year > 99999999)
						year = year % 100000000;
					if (year < 1000)
						year += 1000;
					cal.set(Calendar.YEAR, year);
					row.put("C", dateFormat.format(cal.getTime()));
				}
				if (random.nextDouble() < 0.99) {
					char[] chs = new char[9];
					int mask = 0x7f;
					for (int j = 0; j < chs.length; j++) {
						chs[chs.length - j - 1] = (char) (r & mask);
						if (chs[chs.length - j - 1] < ' ')
							chs[chs.length - j - 1] = ' ';
						r >>>= 7;
					}
					row.put("D", new String(chs));
				}
				int t = (int) Math.round(random.nextDouble() * 6);
				String s;
				switch (t) {
				case 0:
					s = null;
					break;
				case 1:
					s = "";
					break;
				case 2:
					s = "RADAR";
					break;
				case 3:
					s = "JAMMER";
					break;
				case 4:
					s = "IADS";
					break;
				default:
					s = "TELARS";
					break;
				}
				row.put("OneSAF Type", s);
				rows.add(row);
			}
			System.out.println();
			ObservableSwingUtils.buildUI()//
			.systemLandF()//
			.withTitle(TableContentControl.class.getSimpleName() + " Tester")//
			.withSize(640, 900)//
			.withCloseAction(JFrame.EXIT_ON_CLOSE)//
			.withVContent(p -> p.fill().fillV()//
				// .addTextField("Categories:", categories, new Format.ListFormat<>(Format.TEXT, ",", null), f -> f.fill())//
				.<TableContentControl> addTextField("Filter", control, FORMAT, tf -> configureSearchField(tf.fill(), true))//
				.addTable(rows, table -> {
					table.fill().fillV().withCountTitle("displayed").withItemName("row").fill().withFiltering(control)
					.withColumns(columns)//
					.withAdd(() -> new HashMap<>(), null)//
					;
				})//
				).run(null);
		});
	}
}
