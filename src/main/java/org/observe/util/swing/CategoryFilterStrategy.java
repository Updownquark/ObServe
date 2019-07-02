package org.observe.util.swing;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.swing.JCheckBox;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.SimpleSettableValue;
import org.observe.collect.Equivalence;
import org.observe.collect.Equivalence.ComparatorEquivalence;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableSet;
import org.observe.util.TypeTokens;
import org.qommons.BiTuple;
import org.qommons.collect.BetterSet;
import org.qommons.collect.CollectionElement;
import org.qommons.io.Format;

import com.google.common.reflect.TypeToken;

public interface CategoryFilterStrategy<C> {
	Equivalence<? super C> getEquivalence();

	CategoryFilterStrategy.CategoryFilter<C> createFilter();

	interface CategoryFilter<C> extends Predicate<C> {
		boolean isFiltered();

		Component getEditor(ObservableSet<? extends C> availableValues, ObservableValue<String> enabled, Observable<?> until,
			Runnable onChange);

		void clearFilters();
	}

	static <C> Function<? extends CategoryRenderStrategy<?, C>, DistinctValueFilterStrategy<C>> distinct() {
		return distinct(Equivalence.DEFAULT);
	}

	static <C> Function<? extends CategoryRenderStrategy<?, C>, DistinctValueFilterStrategy<C>> distinct(
		Equivalence<? super C> equivalence) {
		return category -> new DistinctValueFilterStrategy<>(category, equivalence);
	}

	public class DistinctValueFilterStrategy<C> implements CategoryFilterStrategy<C> {
		private final CategoryRenderStrategy<?, C> theCategory;
		private final Equivalence<? super C> theEquivalence;
		private Function<? super C, String> theFormat;

		public DistinctValueFilterStrategy(CategoryRenderStrategy<?, C> category, Equivalence<? super C> equivalence) {
			theCategory = category;
			theEquivalence = equivalence;
		}

		public CategoryRenderStrategy<?, C> getCategory() {
			return theCategory;
		}

		@Override
		public Equivalence<? super C> getEquivalence() {
			return theEquivalence;
		}

		public Function<? super C, String> getFormat() {
			return theFormat;
		}

		public void setFormat(Function<? super C, String> format) {
			theFormat = format;
		}

		@Override
		public CategoryFilterStrategy.CategoryFilter<C> createFilter() {
			return new DistinctValueFilter<>(this);
		}

		public static class DistinctValueFilter<C> implements CategoryFilter<C> {
			private final DistinctValueFilterStrategy<C> theStrategy;
			private final BetterSet<C> theValues;
			private final SettableValue<ObservableSet<? extends C>> theAvailableValues;
			private boolean isInclusive;
			private JPanel theEditorPanel;
			private SettableValue<String> theFilterText;
			private final SettableValue<ObservableValue<String>> isEnabled;
			private Runnable theChangeListener;

			public DistinctValueFilter(DistinctValueFilterStrategy<C> strategy) {
				theStrategy = strategy;
				theValues = theStrategy.getEquivalence().createSet();
				isInclusive = true;
				theAvailableValues = new SimpleSettableValue<>(
					TypeTokens.get().keyFor(ObservableSet.class).getCompoundType(strategy.getCategory().getType()), true);
				isEnabled = new SimpleSettableValue<>(
					TypeTokens.get().keyFor(ObservableValue.class).getCompoundType(TypeTokens.get().STRING), true);
			}

			@Override
			public boolean isFiltered() {
				return !theValues.isEmpty();
			}

			@Override
			public boolean test(C value) {
				if (!isFiltered())
					return true;
				boolean recognized = theValues.contains(value);
				return recognized ^ !isInclusive; // If inclusive, return recognized; otherwise return !recognized
			}

			@Override
			public Component getEditor(ObservableSet<? extends C> availableValues, ObservableValue<String> enabled, Observable<?> until,
				Runnable onChange) {
				if (theEditorPanel == null)
					theEditorPanel = createEditor();
				theAvailableValues.set(availableValues, null);
				isEnabled.set(enabled, null);
				theChangeListener = onChange;
				until.take(1).act(__ -> {
					theAvailableValues.set(null, null);
					isEnabled.set(null, null);
					theChangeListener = null;
					theFilterText.set("", null);
				});
				return theEditorPanel;
			}

			private JPanel createEditor() {
				JPanel editor = new JPanel(new BorderLayout());
				theFilterText = new SimpleSettableValue<>(String.class, false);
				theFilterText.set("", null);
				ObservableTextField<String> filterTextBox = new ObservableTextField<>(theFilterText, Format.TEXT, null);
				filterTextBox.setToolTipText("Enter text to filter the available values shown in the list here");
				ObservableCollection<C> listModel = ObservableCollection.flattenValue(theAvailableValues).flow()//
					.refresh(theFilterText.noInitChanges())//
					.map(new TypeToken<BiTuple<C, Integer>>() {}, val -> new BiTuple<>(val, textMatches(render(val), theFilterText.get())))//
					.filter(tuple -> tuple.getValue2() == null ? "Does not match filter" : null)//
					.sorted((t1, t2) -> t1.getValue2().compareTo(t2.getValue2()))//
					.map(theStrategy.getCategory().getType(), BiTuple::getValue1).collect();
				editor.add(filterTextBox, BorderLayout.NORTH);
				JList<C> uiList = new JList<>(new ObservableListModel<>(listModel));
				JScrollPane listScroll = new JScrollPane(uiList);
				listScroll.getVerticalScrollBar().setUnitIncrement(10); // scroll pane's default scroll rate is obnoxious
				uiList.setCellRenderer(new ListCellRenderer<C>() {
					private final JCheckBox theRenderer = new JCheckBox();

					@Override
					public Component getListCellRendererComponent(JList<? extends C> list, C value, int index, boolean isSelected,
						boolean cellHasFocus) {
						ObservableValue<String> enabled = isEnabled.get();
						theRenderer.setEnabled(enabled == null ? true : enabled.get() == null);
						theRenderer.setText(render(value));
						theRenderer.setSelected(test(value));
						return theRenderer;
					}
				});
				listScroll.addMouseListener(new MouseAdapter() {
					@Override
					public void mouseClicked(MouseEvent e) {
						int idx = uiList.locationToIndex(e.getPoint());
						Rectangle cellBounds = uiList.getCellBounds(idx, idx);
						if (cellBounds != null && cellBounds.contains(e.getPoint())) {
							// Toggle the inclusion of the selected value
							C clickedValue = listModel.get(idx);
							boolean[] added = new boolean[1];
							CollectionElement<C> el = theValues.getOrAdd(clickedValue, false, () -> added[0] = true);
							if (!added[0])
								theValues.mutableElement(el.getElementId()).remove();
							theChangeListener.run();
						}
					}
				});
				editor.add(listScroll, BorderLayout.CENTER);
				JCheckBox exclusiveCheck = new JCheckBox("Exclusive");
				exclusiveCheck.setToolTipText("If unselected, new values that appear later will be included;"
					+ " otherwise only the currently selected values will be included");
				ObservableValue.flatten(isEnabled, null).changes().act(evt -> exclusiveCheck.setEnabled(evt.getNewValue() == null));
				exclusiveCheck.addActionListener(evt -> setInclusive(!exclusiveCheck.isSelected()));
				editor.add(exclusiveCheck, BorderLayout.SOUTH);
				return editor;
			}

			String render(C value) {
				Function<? super C, String> format = theStrategy.getFormat();
				return format == null ? String.valueOf(value) : format.apply(value);
			}

			Integer textMatches(String text, String filter) {
				if (filter.length() == 0)
					return 0;
				boolean matched = false;
				int result = 0;
				int f = 0;
				boolean withCase = true;
				for (int i = 0; i < text.length(); i++) {
					if (text.charAt(i) == filter.charAt(f)) {
						f++;
					} else if (Character.toLowerCase(text.charAt(i)) == Character.toLowerCase(filter.charAt(f))) {
						withCase = false;
						f++;
					} else if (f > 0) {
						f = 0;
						withCase = true;
					}
					if (f == filter.length()) {
						matched = true;
						int pointsToAdd = 10;
						if (withCase)
							pointsToAdd += 5; // Extra points for case-matching
						pointsToAdd -= (i - f); // Dock points for appearing later in the text
						if (pointsToAdd <= 0)
							pointsToAdd = 1; // Any match is better than no match
						result += pointsToAdd;
						f = 0;
						withCase = true;
					}
				}
				return matched ? result : null;
			}

			@Override
			public void clearFilters() {
				theValues.clear();
			}

			public DistinctValueFilter<C> setInclusive(boolean inclusive) {
				isInclusive = inclusive;
				if (theChangeListener != null)
					theChangeListener.run();
				return this;
			}

			public DistinctValueFilter<C> recognize(C... values) {
				theValues.with(values);
				if (theChangeListener != null)
					theChangeListener.run();
				return this;
			}

			public DistinctValueFilter<C> recognize(Collection<? extends C> values) {
				theValues.withAll(values);
				if (theChangeListener != null)
					theChangeListener.run();
				return this;
			}

			public DistinctValueFilter<C> forget(C... values) {
				theValues.removeAll(Arrays.asList(values));
				if (theChangeListener != null)
					theChangeListener.run();
				return this;
			}

			public DistinctValueFilter<C> forget(Collection<? extends C> values) {
				theValues.removeAll(values);
				if (theChangeListener != null)
					theChangeListener.run();
				return this;
			}
		}
	}

	public class RangeValueFilterStrategy<C> implements CategoryFilterStrategy<C> {
		private final TypeToken<C> theType;
		private final Equivalence.ComparatorEquivalence<? super C> theEquivalence;

		public RangeValueFilterStrategy(TypeToken<C> type, Comparator<? super C> compare, boolean nullable) {
			theType = type;
			theEquivalence = Equivalence.of(TypeTokens.getRawType(type), compare, nullable);
		}

		@Override
		public Equivalence<? super C> getEquivalence() {
			return theEquivalence;
		}

		@Override
		public CategoryFilter<C> createFilter() {
			return new RangeValueFilter<>(theType, theEquivalence);
		}

		public static class RangeValueFilter<C> implements CategoryFilter<C> {
			private final TypeToken<C> theType;
			private final Equivalence.ComparatorEquivalence<? super C> theEquivalence;
			private C theMinValue;
			private boolean isMinStrict;
			private C theMaxValue;
			private boolean isMaxStrict;

			public RangeValueFilter(TypeToken<C> type, ComparatorEquivalence<? super C> equivalence) {
				theType = type;
				theEquivalence = equivalence;
			}

			@Override
			public boolean isFiltered() {
				return theMinValue != null || theMaxValue != null;
			}

			@Override
			public boolean test(C value) {
				C min = theMinValue;
				if (min != null) {
					int comp = theEquivalence.comparator().compare(value, min);
					if (comp < 0 || (isMinStrict && comp == 0))
						return false;
				}
				C max = theMaxValue;
				if (max != null) {
					int comp = theEquivalence.comparator().compare(value, max);
					if (comp > 0 || (isMaxStrict && comp == 0))
						return false;
				}
				return true;
			}

			@Override
			public void clearFilters() {
				theMinValue = theMaxValue = null;
			}

			public RangeValueFilter<C> withMin(C minValue, boolean strict) {
				theMinValue = minValue;
				isMinStrict = strict;
				return this;
			}

			public RangeValueFilter<C> withMax(C maxValue, boolean strict) {
				theMaxValue = maxValue;
				isMaxStrict = strict;
				return this;
			}

			public RangeValueFilter<C> withRange(C minValue, boolean minStrict, C maxValue, boolean maxStrict) {
				theMinValue = minValue;
				isMinStrict = minStrict;
				theMaxValue = maxValue;
				isMaxStrict = maxStrict;
				return this;
			}
		}
	}

	public class OrValueFilterStrategy<C> implements CategoryFilterStrategy<C> {
		private final List<? extends CategoryFilterStrategy<? super C>> theStrategies;

		public OrValueFilterStrategy(List<? extends CategoryFilterStrategy<? super C>> strategies) {
			theStrategies = strategies;
		}

		@Override
		public Equivalence<? super C> getEquivalence() {
			return theStrategies.get(0).getEquivalence();
		}

		@Override
		public CategoryFilter<C> createFilter() {
			return new OrValueFilter<>(theStrategies.stream().map(s -> s.createFilter())
				.collect(Collectors.toCollection(() -> new ArrayList<>(theStrategies.size()))));
		}

		public static class OrValueFilter<C> implements CategoryFilter<C> {
			private final List<CategoryFilter<? super C>> theFilters;
			private int theActiveFilter;

			public OrValueFilter(List<org.observe.util.swing.CategoryFilterStrategy.CategoryFilter<? super C>> filters) {
				theFilters = filters;
			}

			public List<CategoryFilter<? super C>> getFilters() {
				return theFilters;
			}

			public int getActiveFilter() {
				return theActiveFilter;
			}

			@Override
			public boolean isFiltered() {
				return theFilters.get(theActiveFilter).isFiltered();
			}

			@Override
			public boolean test(C value) {
				return theFilters.get(theActiveFilter).test(value);
			}

			@Override
			public void clearFilters() {
				theFilters.get(theActiveFilter).clearFilters();
			}
		}
	}
}