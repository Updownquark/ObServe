package org.observe.util.swing;

import java.util.BitSet;
import java.util.LinkedList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.ListSelectionListener;

import org.observe.Observable;
import org.observe.SettableValue;
import org.observe.SimpleObservable;
import org.observe.SimpleSettableValue;
import org.observe.Subscription;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableSortedSet;
import org.qommons.Transaction;
import org.qommons.collect.BetterSortedSet.SortedSearchFilter;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.io.Format;

import com.google.common.reflect.TypeToken;

/**
 * <p>
 * A widget containing 2 tables side-by-side, separated by a panel containing 4 buttons.
 * </p>
 *
 * <p>
 * The left table contains a list of available values. These values can optionally be filtered down by a search text field with good default
 * behavior that can also be customized.
 * </p>
 *
 * <p>
 * The right table contains the set of those available values that have been selected by the user for some purpose. Values from the left may
 * be automatically added to the right or not.
 * </p>
 *
 * <p>
 * The buttons allow the user to move either all or only the selected items into and out of the included table. The selection of the 2
 * tables is kept synchronized for simplicity.
 * </p>
 *
 * @param <T> The type of value to select
 */
public class ObservableValueSelector<T, X> extends JPanel {
	private static final String INCLUDE_ALL_TT = "Include all items";
	private static final String INCLUDE_TT = "Include selected items";
	private static final String EXCLUDE_TT = "Exclude selected items";
	private static final String EXCLUDE_ALL_TT = "Exclude all items";

	public static class SelectableValue<T, X> implements Comparable<SelectableValue<T, X>> {
		final CollectionElement<T> sourceElement;
		MutableCollectionElement<SelectableValue<T, X>> selectableElement;
		ElementId displayedAddress;
		ElementId includedAddress;
		X theDestValue;

		boolean displayed;
		boolean included;
		boolean selected;

		SelectableValue(CollectionElement<T> sourceElement, boolean included) {
			this.sourceElement = sourceElement;
			this.included = included;
		}

		public T getSource() {
			return sourceElement.get();
		}

		public X getDest() {
			return theDestValue;
		}

		public CollectionElement<T> getSourceElement() {
			return sourceElement;
		}

		public boolean isDisplayed() {
			return displayed;
		}

		public boolean isIncluded() {
			return included;
		}

		public void setDisplayed(boolean displayed) {
			if (this.displayed == displayed)
				return;
			this.displayed = displayed;
			update();
		}

		public void setIncluded(boolean included) {
			if (this.included == included)
				return;
			this.included = included;
			update();
		}

		@Override
		public int compareTo(SelectableValue<T, X> o) {
			return sourceElement.compareTo(o.sourceElement);
		}

		void remove() {
			selectableElement.remove();
		}

		void update() {
			selectableElement.set(this);
		}

		@Override
		public String toString() {
			String str = sourceElement.toString();
			if (getSource() != theDestValue)
				str += "->" + theDestValue;
			return str;
		}
	}

	private final JTable theSourceTable;
	private final JTable theDestTable;
	private final ObservableCollection<T> theSourceRows;
	private final ObservableTableModel<SelectableValue<T, X>> theSourceModel;
	private final ObservableSortedSet<SelectableValue<T, X>> theSelectableValues;
	private final ObservableCollection<SelectableValue<T, X>> theDisplayedValues;
	private final ObservableCollection<SelectableValue<T, X>> theIncludedValues;
	private final ObservableTableModel<SelectableValue<T, X>> theDestModel;

	private final Function<? super T, ? extends X> theMap;
	private final boolean reEvalOnUpdate;

	private final JButton theIncludeAllButton;
	private final JButton theIncludeButton;
	private final JButton theExcludeButton;
	private final JButton theExcludeAllButton;
	private final ObservableTextField<String> theSearchField;
	private final SimpleObservable<Void> theSelectionChanges;

	private final SettableValue<String> theFilterText;
	private BiFunction<SelectableValue<T, X>, String, Boolean> theFilter;

	private boolean isIncludedByDefault;

	private int theSelected;
	private int theSelectedExcluded;
	private int theSelectedIncluded;

	public ObservableValueSelector(JTable sourceTable, JTable destTable, ObservableCollection<T> sourceRows, //
		ObservableCollection<? extends CategoryRenderStrategy<? super SelectableValue<T, X>, ?>> sourceColumns, //
			ObservableCollection<? extends CategoryRenderStrategy<? super SelectableValue<T, X>, ?>> destColumns, //
				Function<? super T, ? extends X> map, boolean reEvalOnUpdate, Observable<?> until, //
				boolean includedByDefault, boolean withFiltering) {
		super(new JustifiedBoxLayout(false).crossJustified().mainJustified());
		theSourceTable = sourceTable;
		theDestTable = destTable;
		theSourceRows = sourceRows;
		theMap = map;
		this.reEvalOnUpdate = reEvalOnUpdate;
		theSelectableValues = ObservableSortedSet.create(new TypeToken<SelectableValue<T, X>>() {
		}, SelectableValue::compareTo);
		theFilterText = new SimpleSettableValue<>(String.class, false).withValue("", null);
		theDisplayedValues = theSelectableValues.flow().filter(sv -> sv.isDisplayed() ? null : "Not Displayed").unmodifiable()
			.collectActive(until);
		theIncludedValues = theSelectableValues.flow().filter(sv -> sv.isIncluded() ? null : "Not Included").unmodifiable()
			.collectActive(until);
		isIncludedByDefault = includedByDefault;

		List<Subscription> subs = new LinkedList<>();
		subs.add(theSourceRows.subscribe(evt -> {
			CollectionElement<SelectableValue<T, X>> selectableEl;
			switch (evt.getType()) {
			case add:
				SelectableValue<T, X> newSV = new SelectableValue<>(theSourceRows.getElement(evt.getElementId()), isIncludedByDefault);
				newSV.theDestValue = theMap.apply(evt.getNewValue());
				newSV.displayed = theFilter == null || theFilterText.get().length() == 0 || theFilter.apply(newSV, theFilterText.get());
				selectableEl = theSelectableValues.addElement(newSV, false);
				selectableEl.get().selectableElement = theSelectableValues.mutableElement(selectableEl.getElementId());
				break;
			case remove:
				selectableEl = theSelectableValues.search(sv -> evt.getElementId().compareTo(sv.sourceElement.getElementId()),
					SortedSearchFilter.OnlyMatch);
				selectableEl.get().remove();
				break;
			case set:
				selectableEl = theSelectableValues
				.getElement(new SelectableValue<>(theSourceRows.getElement(evt.getElementId()), true), true);
				if (evt.getOldValue() != evt.getNewValue() || reEvalOnUpdate)
					selectableEl.get().theDestValue = theMap.apply(evt.getNewValue());
				selectableEl.get().update();
				break;
			}
		}, true));
		subs.add(theDisplayedValues.subscribe(evt -> {
			switch (evt.getType()) {
			case add:
				evt.getNewValue().displayedAddress = evt.getElementId();
				break;
			case remove:
				evt.getNewValue().displayedAddress = null;
				if (!evt.getNewValue().isIncluded())
					evt.getNewValue().selected = false;
				break;
			default:
				break;
			}
		}, true));
		subs.add(theIncludedValues.subscribe(evt -> {
			switch (evt.getType()) {
			case add:
				evt.getNewValue().includedAddress = evt.getElementId();
				if (evt.getNewValue().selected) {
					if (evt.getNewValue().isDisplayed())
						theSelectedExcluded--;
					theSelectedIncluded++;
				}
				break;
			case remove:
				evt.getNewValue().includedAddress = null;
				if (evt.getNewValue().selected) {
					if (evt.getNewValue().isDisplayed())
						theSelectedExcluded++;
					theSelectedIncluded--;
				}
				if (!evt.getNewValue().isDisplayed())
					evt.getNewValue().selected=false;
				break;
			default:
				break;
			}
		}, true));
		theSourceModel = new ObservableTableModel<>(theDisplayedValues, sourceColumns);
		sourceTable.setModel(theSourceModel);
		subs.add(ObservableTableModel.hookUp(sourceTable, theSourceModel));

		theDestModel = new ObservableTableModel<>(theIncludedValues, destColumns);
		destTable.setModel(theDestModel);
		subs.add(ObservableTableModel.hookUp(destTable, theDestModel));
		until.take(1).act(__ -> {
			Subscription.forAll(subs);
			subs.clear();
		});

		ListDataListener sourceDataListener = new ListDataListener() {
			@Override
			public void intervalAdded(ListDataEvent e) {
				theSourceTable.getSelectionModel().setValueIsAdjusting(true);
				try {
					for (int i = e.getIndex0(); i <= e.getIndex1(); i++) {
						SelectableValue<T, X> sv = theSourceModel.getRowModel().getElementAt(i);
						if (sv.selected)
							theSourceTable.getSelectionModel().addSelectionInterval(i, i);
						else
							theSourceTable.getSelectionModel().removeSelectionInterval(i, i);
					}
				} finally {
					theSourceTable.getSelectionModel().setValueIsAdjusting(false);
				}
				checkButtonStates();
			}

			@Override
			public void intervalRemoved(ListDataEvent e) {
				checkButtonStates();
			}

			@Override
			public void contentsChanged(ListDataEvent e) {
			}
		};
		theSourceModel.getRowModel().addListDataListener(sourceDataListener);
		subs.add(() -> theSourceModel.getRowModel().removeListDataListener(sourceDataListener));
		ListDataListener destDataListener = new ListDataListener() {
			@Override
			public void intervalAdded(ListDataEvent e) {
				theDestTable.getSelectionModel().setValueIsAdjusting(true);
				try {
					for (int i = e.getIndex0(); i <= e.getIndex1(); i++) {
						SelectableValue<T, X> sv = theDestModel.getRowModel().getElementAt(i);
						if (sv.selected)
							theDestTable.getSelectionModel().addSelectionInterval(i, i);
						else
							theDestTable.getSelectionModel().removeSelectionInterval(i, i);
					}
				} finally {
					theDestTable.getSelectionModel().setValueIsAdjusting(false);
				}
				checkButtonStates();
			}

			@Override
			public void intervalRemoved(ListDataEvent e) {
				checkButtonStates();
			}

			@Override
			public void contentsChanged(ListDataEvent e) {
			}
		};
		theDestModel.getRowModel().addListDataListener(destDataListener);
		subs.add(() -> theDestModel.getRowModel().removeListDataListener(destDataListener));

		theSelectionChanges = new SimpleObservable<>();

		boolean[] selectCallbackLock = new boolean[1];
		ListSelectionListener leftLSL = evt -> {
			if (selectCallbackLock[0])
				return;
			boolean change = false;
			selectCallbackLock[0] = true;
			try {
				for (int i = evt.getFirstIndex(); i <= evt.getLastIndex() && i < theSourceModel.getRowModel().getSize(); i++) {
					SelectableValue<T, X> sv = theSourceModel.getRowModel().getElementAt(i);
					if (sv.selected == theSourceTable.getSelectionModel().isSelectedIndex(i))
						continue;
					change = true;
					sv.selected = !sv.selected;
					theSelected += sv.selected ? 1 : -1;
					if (sv.isIncluded()) {
						theSelectedIncluded += sv.selected ? 1 : -1;
						int includedIndex = theIncludedValues.getElementsBefore(sv.includedAddress);
						if (sv.selected)
							theDestTable.getSelectionModel().addSelectionInterval(includedIndex, includedIndex);
						else
							theDestTable.getSelectionModel().removeSelectionInterval(includedIndex, includedIndex);
					} else
						theSelectedExcluded += sv.selected ? 1 : -1;
				}
			} finally {
				selectCallbackLock[0] = false;
			}
			if (change)
				theSelectionChanges.onNext(null);
		};
		sourceTable.getSelectionModel().addListSelectionListener(leftLSL);
		subs.add(() -> sourceTable.getSelectionModel().removeListSelectionListener(leftLSL));
		ListSelectionListener rightLSL = evt -> {
			if (selectCallbackLock[0])
				return;
			boolean change = false;
			selectCallbackLock[0] = true;
			try {
				for (int i = evt.getFirstIndex(); i <= evt.getLastIndex() && i < theDestModel.getRowModel().getSize(); i++) {
					SelectableValue<T, X> sv = theDestModel.getRowModel().getElementAt(i);
					if (sv.selected == theDestTable.getSelectionModel().isSelectedIndex(i))
						continue;
					change = true;
					sv.selected = !sv.selected;
					theSelected += sv.selected ? 1 : -1;
					theSelectedIncluded += sv.selected ? 1 : -1;
					int displayedIndex = theDisplayedValues.getElementsBefore(sv.displayedAddress);
					if (sv.selected)
						theSourceTable.getSelectionModel().addSelectionInterval(displayedIndex, displayedIndex);
					else
						theSourceTable.getSelectionModel().removeSelectionInterval(displayedIndex, displayedIndex);
				}
			} finally {
				selectCallbackLock[0] = false;
			}
			if (change)
				theSelectionChanges.onNext(null);
		};
		destTable.getSelectionModel().addListSelectionListener(rightLSL);
		subs.add(() -> destTable.getSelectionModel().removeListSelectionListener(rightLSL));

		theIncludeAllButton = new JButton(">>");
		theIncludeButton = new JButton(">");
		theExcludeButton = new JButton("<");
		theExcludeAllButton = new JButton("<<");
		checkButtonStates();
		theSelectionChanges.act(__ -> checkButtonStates());

		theIncludeAllButton.addActionListener(evt -> {
			selectCallbackLock[0] = true;
			try {
				for (SelectableValue<T, X> sv : theDisplayedValues) {
					if (!sv.included)
						sv.setIncluded(true);
				}
			} finally {
				selectCallbackLock[0] = false;
			}
		});
		theIncludeButton.addActionListener(evt -> {
			selectCallbackLock[0] = true;
			try {
				for (SelectableValue<T, X> sv : theDisplayedValues) {
					if (sv.selected && !sv.included)
						sv.setIncluded(true);
				}
			} finally {
				selectCallbackLock[0] = false;
			}
		});
		theExcludeButton.addActionListener(evt -> {
			selectCallbackLock[0] = true;
			try {
				for (SelectableValue<T, X> sv : theIncludedValues) {
					if (sv.selected)
						sv.setIncluded(false);
				}
			} finally {
				selectCallbackLock[0] = false;
			}
		});
		theExcludeAllButton.addActionListener(evt -> {
			selectCallbackLock[0] = true;
			try {
				for (SelectableValue<T, X> sv : theIncludedValues)
					sv.setIncluded(false);
			} finally {
				selectCallbackLock[0] = false;
			}
		});

		JScrollPane scroll = new JScrollPane(sourceTable);
		scroll.getVerticalScrollBar().setUnitIncrement(8);
		if (withFiltering) {
			withColumnFiltering(c -> true);
			Icon searchIcon = ObservableSwingUtils.getFixedIcon(getClass(), "/icons/search.png", 16, 16);
			theSearchField = new ObservableTextField<>(theFilterText, Format.TEXT, until).setCommitOnType(true)//
				.setEmptyText("Search...");
			if (searchIcon != null)
				theSearchField.setIcon(searchIcon);
			theFilterText.noInitChanges().act(evt -> {
				try (Transaction t = theSelectableValues.lock(true, evt)) {
					filterChanged(evt.getNewValue());
				}
			});
			JPanel leftPanel = new JPanel(new JustifiedBoxLayout(true).crossJustified().mainJustified());
			leftPanel.add(theSearchField);
			leftPanel.add(scroll);
			add(leftPanel);
		} else {
			theSearchField = null;
			add(scroll);
		}

		JPanel buttonPanel = new JPanel(new JustifiedBoxLayout(true));
		buttonPanel.add(theIncludeAllButton);
		buttonPanel.add(theIncludeButton);
		buttonPanel.add(theExcludeButton);
		buttonPanel.add(theExcludeAllButton);
		JPanel buttonPanelHolder = new JPanel(new JustifiedBoxLayout(false).mainJustified().crossCenter());
		buttonPanelHolder.add(buttonPanel);
		add(buttonPanelHolder);

		scroll = new JScrollPane(destTable);
		scroll.getVerticalScrollBar().setUnitIncrement(8);
		add(scroll);
	}

	public ObservableCollection<SelectableValue<T, X>> getDisplayed() {
		return theDisplayedValues;
	}

	public ObservableCollection<SelectableValue<T, X>> getIncluded() {
		return theIncludedValues;
	}

	public Observable<Void> selectionChanges() {
		return theSelectionChanges.readOnly();
	}

	public ObservableValueSelector<T, X> setIncludeByDefault(boolean included) {
		isIncludedByDefault = included;
		return this;
	}

	public ObservableTextField<String> getSearchField(){
		return theSearchField;
	}

	public ObservableValueSelector<T, X> withCustomFiltering(BiFunction<SelectableValue<T, X>, String, Boolean> filter) {
		theFilter = filter;
		String text = theFilterText.get();
		if (text.length() > 0)
			filterChanged(text);
		return this;
	}

	public ObservableValueSelector<T, X> withColumnFiltering(
		Predicate<? super CategoryRenderStrategy<? super SelectableValue<T, X>, ?>> columnFilter) {
		theFilter = new BiFunction<SelectableValue<T, X>, String, Boolean>() {
			private final BitSet theColumnFilter = new BitSet();
			private long theColumnsStamp = -1;

			@Override
			public Boolean apply(SelectableValue<T, X> value, String text) {
				boolean passed = true;
				try (Transaction t = theSourceModel.getColumns().lock(false, null)) {
					long newStamp = theSourceModel.getColumns().getStamp(false);
					if (theColumnsStamp != newStamp) {
						theColumnsStamp = newStamp;
						theColumnFilter.clear();
						int i = 0;
						for (CategoryRenderStrategy<? super SelectableValue<T, X>, ?> column : theSourceModel.getColumns()) {
							if (columnFilter.test(column)) {
								theColumnFilter.set(i);
								if (passed)
									passed = testWithColumnFiltering(column, value, text);
							}
							i++;
						}
					} else {
						int i = 0;
						for (CategoryRenderStrategy<? super SelectableValue<T, X>, ?> column : theSourceModel.getColumns()) {
							if (passed && theColumnFilter.get(i))
								passed = testWithColumnFiltering(column, value, text);
							i++;
						}
					}
				}
				return passed;
			}
		};
		return this;
	}

	public JButton getIncludeAllButton() {
		return theIncludeAllButton;
	}

	public JButton getIncludeButton() {
		return theIncludeButton;
	}

	public JButton getExcludeButton() {
		return theExcludeButton;
	}

	public JButton getExcludeAllButton() {
		return theExcludeAllButton;
	}

	public JTable getSourceTable() {
		return theSourceTable;
	}

	public JTable getDestTable() {
		return theDestTable;
	}

	<C> boolean testWithColumnFiltering(CategoryRenderStrategy<? super SelectableValue<T, X>, C> column, SelectableValue<T, X> value,
		String text) {
		String rendered;
		if (column.getRenderer() != null)
			rendered = column.getRenderer().renderAsText(() -> value, column.getCategoryValue(value));
		else
			rendered = String.valueOf(column.getCategoryValue(value));
		int textIdx = 0;
		for (int i = 0; i < rendered.length() && textIdx < text.length(); i++) {
			char rc = rendered.charAt(i);
			char tc = text.charAt(textIdx);
			if (Character.toLowerCase(rc) == Character.toLowerCase(tc))
				textIdx++;
			else if (textIdx > 0)
				textIdx = 0;
		}
		return textIdx == text.length();
	}

	void filterChanged(String text) {
		try (Transaction t = theSelectableValues.lock(true, null)) {
			for (SelectableValue<T, X> sv : theSelectableValues)
				sv.setDisplayed(text.length() == 0 || theFilter.apply(sv, text));
		}
	}

	void checkButtonStates() {
		if (theSourceModel.getRowModel().getSize() > theDestModel.getRowModel().getSize()) {
			theIncludeAllButton.setEnabled(true);
			theIncludeAllButton.setToolTipText(INCLUDE_ALL_TT);
		} else {
			theIncludeAllButton.setEnabled(false);
			theIncludeAllButton.setToolTipText("All items included");
		}

		if (theSelectedExcluded > 0) {
			theIncludeButton.setEnabled(true);
			theIncludeButton.setToolTipText(INCLUDE_TT);
		} else {
			theIncludeButton.setEnabled(false);
			theIncludeButton.setToolTipText(theSelected == 0 ? "No items selected" : "All selected items are included");
		}
		if (theSelectedIncluded > 0) {
			theExcludeButton.setEnabled(true);
			theExcludeButton.setToolTipText(EXCLUDE_TT);
		} else {
			theExcludeButton.setEnabled(false);
			theExcludeButton.setToolTipText(theSelected == 0 ? "No items selected" : "No included items selected");
		}

		if (theDestModel.getRowModel().getSize() > 0) {
			theExcludeAllButton.setEnabled(true);
			theExcludeAllButton.setToolTipText(EXCLUDE_ALL_TT);
		} else {
			theExcludeAllButton.setEnabled(false);
			theExcludeAllButton.setToolTipText("No items included");
		}
	}
}
