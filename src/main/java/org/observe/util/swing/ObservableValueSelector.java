package org.observe.util.swing;

import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.LayoutManager2;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.ListSelectionListener;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.SimpleObservable;
import org.observe.Subscription;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableSortedSet;
import org.observe.util.TypeTokens;
import org.qommons.Colors;
import org.qommons.StringUtils;
import org.qommons.ThreadConstraint;
import org.qommons.collect.BetterSortedList;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.io.Format;
import org.qommons.threading.QommonsTimer;

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
 * @param <X> The type of the included value
 */
public class ObservableValueSelector<T, X> extends JPanel {
	/**
	 * A value in an {@link ObservableValueSelector}
	 *
	 * @param <T> The type of value to select
	 * @param <X> The type of the included value
	 */
	public static class SelectableValue<T, X> implements Comparable<SelectableValue<T, X>> {
		final CollectionElement<T> sourceElement;
		MutableCollectionElement<SelectableValue<T, X>> selectableElement;
		ElementId displayedAddress;
		ElementId includedAddress;
		X theDestValue;

		boolean included;
		boolean selected;

		SelectableValue(CollectionElement<T> sourceElement, boolean included) {
			this.sourceElement = sourceElement;
			this.included = included;
		}

		/** @return The selectable value */
		public T getSource() {
			return sourceElement.get();
		}

		/** @return The value for the included collection */
		public X getDest() {
			return theDestValue;
		}

		/** @return This value's element in the source collection */
		public CollectionElement<T> getSourceElement() {
			return sourceElement;
		}

		/** @return Whether the value is currently included */
		public boolean isIncluded() {
			return included;
		}

		/** @param included Whether the value should be included */
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

	private JTable theSourceTable;
	private JTable theDestTable;
	private final ObservableCollection<T> theSourceRows;
	private final ObservableSortedSet<SelectableValue<T, X>> theSelectableValues;
	private final ObservableCollection<SelectableValue<T, X>> theDisplayedValues;
	private final ObservableCollection<SelectableValue<T, X>> theIncludedValues;

	private final Function<? super T, ? extends X> theMap;

	private final JButton theIncludeAllButton;
	private final JButton theIncludeButton;
	private final JButton theExcludeButton;
	private final JButton theExcludeAllButton;
	private ObservableTextField<TableContentControl> theSearchField;
	private final SimpleObservable<Void> theSelectionChanges;

	private final SettableValue<TableContentControl> theFilterText;
	private final String theItemName;

	private boolean isIncludedByDefault;

	private ObservableValueSelector(ObservableCollection<T> sourceRows, //
		ObservableCollection<? extends CategoryRenderStrategy<SelectableValue<T, X>, ?>> sourceColumns, //
			ObservableCollection<? extends CategoryRenderStrategy<SelectableValue<T, X>, ?>> destColumns, //
				Function<? super T, ? extends X> map, boolean reEvalOnUpdate, Observable<?> until, //
				boolean includedByDefault, Format<TableContentControl> filterFormat, boolean commitOnType, String itemName) {
		super(new JustifiedBoxLayout(false).crossJustified().mainJustified().forceFill(true));
		theSourceRows = sourceRows;
		theMap = map;
		theSelectableValues = ObservableSortedSet.create(new TypeToken<SelectableValue<T, X>>() {
		}, SelectableValue::compareTo);
		theFilterText = SettableValue.build(TableContentControl.class).build().withValue(TableContentControl.DEFAULT, null);
		theIncludedValues = theSelectableValues.flow().filter(sv -> sv.isIncluded() ? null : "Not Included").unmodifiable()
			.collectActive(until);
		theItemName = itemName;
		isIncludedByDefault = includedByDefault;

		List<Subscription> subs = new LinkedList<>();
		until.take(1).act(__ -> {
			Subscription.forAll(subs);
			subs.clear();
		});
		subs.add(theSourceRows.subscribe(evt -> {
			CollectionElement<SelectableValue<T, X>> selectableEl;
			switch (evt.getType()) {
			case add:
				SelectableValue<T, X> newSV = new SelectableValue<>(theSourceRows.getElement(evt.getElementId()), isIncludedByDefault);
				newSV.theDestValue = theMap.apply(evt.getNewValue());
				selectableEl = theSelectableValues.addElement(newSV, false);
				selectableEl.get().selectableElement = theSelectableValues.mutableElement(selectableEl.getElementId());
				break;
			case remove:
				selectableEl = theSelectableValues.search(sv -> evt.getElementId().compareTo(sv.sourceElement.getElementId()),
					BetterSortedList.SortedSearchFilter.OnlyMatch);
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

		theIncludeAllButton = new JButton(">>");
		theIncludeButton = new JButton(">");
		theExcludeButton = new JButton("<");
		theExcludeAllButton = new JButton("<<");

		PanelPopulation.populateHPanel(this, getLayout(), until)//
		.addHPanel(null, new JustifiedBoxLayout(true).mainJustified().crossJustified().forceFill(true), //
			srcPanel -> srcPanel.withName("OVS Source")
			.addTextField(null, theFilterText, filterFormat == null ? TableContentControl.FORMAT : filterFormat,
				tf -> TableContentControl.configureSearchField(tf.fill(), commitOnType).modifyEditor(tfe -> {
					theSearchField = tfe;
				}))//
			.addTable(theSelectableValues, srcTbl -> {
				theSourceTable = srcTbl.getEditor();
				if (itemName != null)
					srcTbl.withItemName(itemName);
				srcTbl.withCountTitle("available").withColumns(sourceColumns).withFiltering(theFilterText).fill();
			}).fill())//
		.addHPanel(null, new JustifiedBoxLayout(false).mainJustified().forceFill(true).crossCenter().setMargin(2, 2, 2, 2),
			buttonPanel -> buttonPanel.withName("OVS Buttons").fillV()//
			.addHPanel(null, new JustifiedBoxLayout(true).mainJustified().crossJustified(), bp -> bp.withName("OVS Buttons 2")//
				.addComponent(null, theIncludeAllButton, null)//
				.addComponent(null, theIncludeButton, null)//
				.addComponent(null, theExcludeButton, null)//
				.addComponent(null, theExcludeAllButton, null)//
				)//
			).addHPanel(null,
				new JustifiedBoxLayout(true).mainJustified().crossJustified().forceFill(true).setShowingInvisible(true),
				destPanel -> destPanel.withName("OVS Dest")//
				// Invisible placeholder here to make the available and included tables the same height
				.addTextField(null, SettableValue.build(TableContentControl.class).build(),
					filterFormat == null ? TableContentControl.FORMAT : filterFormat,
						f -> TableContentControl.configureSearchField(f.fill(), commitOnType).visibleWhen(ObservableValue.of(false)))//
				.addTable(theIncludedValues, destTbl -> {
					theDestTable = destTbl.getEditor();
					if (itemName != null)
						destTbl.withItemName(itemName);
					destTbl.withCountTitle("included").withColumns(destColumns).fill();
				}).fill());

		ObservableTableModel<SelectableValue<T, X>> sourceModel = (ObservableTableModel<SelectableValue<T, X>>) theSourceTable.getModel();
		ObservableTableModel<SelectableValue<T, X>> destModel = (ObservableTableModel<SelectableValue<T, X>>) theDestTable.getModel();
		theDisplayedValues = sourceModel.getRows();

		subs.add(theDisplayedValues.subscribe(evt -> {
			switch (evt.getType()) {
			case add:
				if (!evt.getNewValue().included)
					evt.getNewValue().selected = false;
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
				break;
			case remove:
				evt.getNewValue().includedAddress = null;
				break;
			default:
				break;
			}
		}, true));

		ListDataListener sourceDataListener = new ListDataListener() {
			@Override
			public void intervalAdded(ListDataEvent e) {
				theSourceTable.getSelectionModel().setValueIsAdjusting(true);
				try {
					for (int i = e.getIndex0(); i <= e.getIndex1(); i++) {
						SelectableValue<T, X> sv = sourceModel.getRowModel().getElementAt(i);
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
		sourceModel.getRowModel().addListDataListener(sourceDataListener);
		subs.add(() -> sourceModel.getRowModel().removeListDataListener(sourceDataListener));
		ListDataListener destDataListener = new ListDataListener() {
			@Override
			public void intervalAdded(ListDataEvent e) {
				theDestTable.getSelectionModel().setValueIsAdjusting(true);
				try {
					for (int i = e.getIndex0(); i <= e.getIndex1(); i++) {
						SelectableValue<T, X> sv = destModel.getRowModel().getElementAt(i);
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
		destModel.getRowModel().addListDataListener(destDataListener);
		subs.add(() -> destModel.getRowModel().removeListDataListener(destDataListener));

		theSelectionChanges = new SimpleObservable<>();

		boolean[] selectCallbackLock = new boolean[1];
		int[] minMaxSelectionChange = new int[] { -1, -1 };
		Object selectionUpdateId = new Object();
		ListSelectionListener leftLSL = evt -> {
			if (selectCallbackLock[0])
				return;
			if (minMaxSelectionChange[0] < 0 || evt.getFirstIndex() < minMaxSelectionChange[0])
				minMaxSelectionChange[0] = evt.getFirstIndex();
			if (minMaxSelectionChange[1] < 0 || evt.getLastIndex() > minMaxSelectionChange[1])
				minMaxSelectionChange[1] = evt.getLastIndex();
			QommonsTimer.getCommonInstance().doAfterInactivity(selectionUpdateId, () -> ObservableSwingUtils.onEQ(() -> {
				int min = minMaxSelectionChange[0];
				int max = minMaxSelectionChange[1];
				if (min < 0 || max < 0)
					return;
				minMaxSelectionChange[0] = -1;
				minMaxSelectionChange[1] = -1;
				adjustSelectionFromLeft(min, max, selectCallbackLock);
			}), 100);
		};
		theSourceTable.getSelectionModel().addListSelectionListener(leftLSL);
		subs.add(() -> theSourceTable.getSelectionModel().removeListSelectionListener(leftLSL));
		ListSelectionListener rightLSL = evt -> {
			if (selectCallbackLock[0] || evt.getValueIsAdjusting())
				return;
			boolean change = false;
			selectCallbackLock[0] = true;
			try {
				for (int i = evt.getFirstIndex(); i <= evt.getLastIndex() && i < destModel.getRowModel().getSize(); i++) {
					SelectableValue<T, X> sv = destModel.getRowModel().getElementAt(i);
					if (sv.selected == theDestTable.getSelectionModel().isSelectedIndex(i))
						continue;
					change = true;
					sv.selected = !sv.selected;
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
		theDestTable.getSelectionModel().addListSelectionListener(rightLSL);
		subs.add(() -> theDestTable.getSelectionModel().removeListSelectionListener(rightLSL));

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
	}

	private void adjustSelectionFromLeft(int min, int max, boolean[] selectCallbackLock) {
		// This may be called after a whole lot of events, so while the indexes should be the super set of the range,
		// anything may have happened to the rows, so we need to be careful with these indexes
		if (min >= theDisplayedValues.size())
			return;
		boolean change = false;
		selectCallbackLock[0] = true;
		// If we can obtain a lock on the source collection
		try {
			SelectableValue<T, X> sv = theDisplayedValues.get(min);
			for (int i = min; i <= max && sv != null; //
				i++, sv = CollectionElement.get(theDisplayedValues.getAdjacentElement(sv.displayedAddress, true))) {
				if (sv.selected == theSourceTable.getSelectionModel().isSelectedIndex(i))
					continue; // No change here
				change = true;
				sv.selected = !sv.selected;
				if (sv.isIncluded()) {
					int includedIndex = theIncludedValues.getElementsBefore(sv.includedAddress);
					if (sv.selected)
						theDestTable.getSelectionModel().addSelectionInterval(includedIndex, includedIndex);
					else
						theDestTable.getSelectionModel().removeSelectionInterval(includedIndex, includedIndex);
				}
			}
		} finally {
			selectCallbackLock[0] = false;
		}
		if (change)
			theSelectionChanges.onNext(null);
	}

	/** @return The collection of source values that are currently displayed to the user (i.e. not filtered) */
	public ObservableCollection<SelectableValue<T, X>> getDisplayed() {
		return theDisplayedValues;
	}

	/** @return The collection of included values */
	public ObservableCollection<SelectableValue<T, X>> getIncluded() {
		return theIncludedValues;
	}

	/** @return An observable that fires whenever the list selection (not inclusion) changes */
	public Observable<Void> selectionChanges() {
		return theSelectionChanges.readOnly();
	}

	/**
	 * @param included Whether initial and new values in the source collection will automatically be included
	 * @return This selector
	 */
	public ObservableValueSelector<T, X> setIncludeByDefault(boolean included) {
		isIncludedByDefault = included;
		return this;
	}

	/** @return This selector's search field */
	public ObservableTextField<TableContentControl> getSearchField() {
		return theSearchField;
	}

	/** @return This selector's include all button */
	public JButton getIncludeAllButton() {
		return theIncludeAllButton;
	}

	/** @return This selector's include selected button */
	public JButton getIncludeButton() {
		return theIncludeButton;
	}

	/** @return This selector's exclude selected button */
	public JButton getExcludeButton() {
		return theExcludeButton;
	}

	/** @return This selector's exclude all button */
	public JButton getExcludeAllButton() {
		return theExcludeAllButton;
	}

	/** @return This selector's source table widget */
	public JTable getSourceTable() {
		return theSourceTable;
	}

	/** @return This selector's included table widget */
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

	void checkButtonStates() {
		int totalCount = theSourceRows.size();
		int includedCount = theIncludedValues.size();

		int excluded = totalCount - includedCount;
		boolean hasSelection = !theSourceTable.getSelectionModel().isSelectionEmpty();
		int selectedIncluded = 0;
		for (int i = theDestTable.getSelectionModel().getMinSelectionIndex(); i <= theDestTable.getSelectionModel()
			.getMaxSelectionIndex(); i++) {
			if (theDestTable.getSelectionModel().isSelectedIndex(i))
				selectedIncluded++;
		}
		int selectedExcluded = 0;
		ObservableTableModel<SelectableValue<T, X>> srcModel = (ObservableTableModel<SelectableValue<T, X>>) theSourceTable.getModel();
		for (int i = theSourceTable.getSelectionModel().getMinSelectionIndex(); i >= 0
			&& i <= theSourceTable.getSelectionModel().getMaxSelectionIndex(); i++) {
			if (theSourceTable.getSelectionModel().isSelectedIndex(i) && !srcModel.getRowModel().getElementAt(i).included)
				selectedExcluded++;
		}

		String singItem = theItemName != null ? theItemName : "item";
		String plurItem = theItemName != null ? StringUtils.pluralize(singItem) : "items";
		theIncludeAllButton.setEnabled(excluded > 0);
		if (excluded == 0)
			theIncludeAllButton.setToolTipText("All " + plurItem + " included");
		else if (excluded == 0)
			theIncludeAllButton.setToolTipText("Include 1 " + singItem);
		else
			theIncludeAllButton.setToolTipText("Include all " + excluded + " excluded " + plurItem);

		theIncludeButton.setEnabled(selectedExcluded > 0);
		theIncludeButton
		.setToolTipText(
			selectedExcluded > 0 ? "Include " + selectedExcluded + " selected " + (selectedExcluded == 1 ? singItem : plurItem)
				: (hasSelection ? "All selected " + plurItem + " are included" : "No " + plurItem + " selected"));

		theExcludeButton.setEnabled(selectedIncluded > 0);
		theExcludeButton
		.setToolTipText(
			selectedIncluded > 0 ? "Exclude " + selectedIncluded + " selected " + (selectedIncluded == 1 ? singItem : plurItem)
				: (hasSelection ? "No included " + plurItem + " selected" : "No " + plurItem + " selected"));

		theExcludeAllButton.setEnabled(includedCount > 0);
		if (includedCount == 0)
			theExcludeAllButton.setToolTipText("No " + plurItem + " included");
		else if (includedCount == 0)
			theExcludeAllButton.setToolTipText("Exclude 1 included " + singItem);
		else
			theExcludeAllButton.setToolTipText("Exclude all " + includedCount + " included " + plurItem);
	}

	@Override
	public Dimension getPreferredSize() {
		return getLayout().preferredLayoutSize(this);
	}

	@Override
	public Dimension getMinimumSize() {
		return getLayout().minimumLayoutSize(this);
	}

	@Override
	public Dimension getMaximumSize() {
		return ((LayoutManager2) getLayout()).maximumLayoutSize(this);
	}

	/**
	 * Builds a value selector widget
	 *
	 * @param <T> The type of values to select from
	 * @param <X> The type of selected values
	 * @param sourceRows The collection of values to select from
	 * @param sourceColumns The columns for the values to select from
	 * @param destColumns The columns for the selected values
	 * @param map The function to produce selected values from selectable ones
	 * @return The builder to build the widget
	 */
	public static <T, X> Builder<T, X> build(ObservableCollection<T> sourceRows, //
		ObservableCollection<? extends CategoryRenderStrategy<SelectableValue<T, X>, ?>> sourceColumns, //
			ObservableCollection<? extends CategoryRenderStrategy<SelectableValue<T, X>, ?>> destColumns, //
				Function<? super T, ? extends X> map) {
		return new Builder<>(sourceRows, sourceColumns, destColumns, map);
	}

	/**
	 * Builds an {@link ObservableValueSelector}
	 *
	 * @param <T> The type of values to select from
	 * @param <X> The type of selected values
	 */
	public static class Builder<T, X> {
		private final ObservableCollection<T> theSourceRows;
		private final ObservableCollection<? extends CategoryRenderStrategy<SelectableValue<T, X>, ?>> theSourceColumns;
		private final ObservableCollection<? extends CategoryRenderStrategy<SelectableValue<T, X>, ?>> theDestColumns;
		private final Function<? super T, ? extends X> theMap;
		private boolean isReEvalOnUpdate;
		private Observable<?> theUntil;
		private boolean isIncludedByDefault;
		private Format<TableContentControl> theFilterFormat;
		private boolean isFilterCommitOnType;
		private String theItemName;

		Builder(ObservableCollection<T> sourceRows,
			ObservableCollection<? extends CategoryRenderStrategy<SelectableValue<T, X>, ?>> sourceColumns,
				ObservableCollection<? extends CategoryRenderStrategy<SelectableValue<T, X>, ?>> destColumns,
					Function<? super T, ? extends X> map) {
			theSourceRows = sourceRows;
			theSourceColumns = sourceColumns;
			theDestColumns = destColumns;
			theMap = map;

			isReEvalOnUpdate = true;
			theUntil = Observable.empty();
			isIncludedByDefault = false;
			theFilterFormat = TableContentControl.FORMAT;
			isFilterCommitOnType = true;
			theItemName = null;
		}

		/**
		 * @param reEvalOnUpdate Whether to re-evaluate the mapping function in response to an update event on the selectable values
		 * @return This builder
		 */
		public Builder<T, X> reEvalOnUpdate(boolean reEvalOnUpdate) {
			isReEvalOnUpdate = reEvalOnUpdate;
			return this;
		}

		/**
		 * @param until The observable to release all the widget's resources when it fires
		 * @return This builder
		 */
		public Builder<T, X> withUntil(Observable<?> until) {
			theUntil = until;
			return this;
		}

		/**
		 * @param includedByDefault Whether the selector should initially include all selectable values
		 * @return This builder
		 */
		public Builder<T, X> includeAllByDefault(boolean includedByDefault) {
			isIncludedByDefault = includedByDefault;
			return this;
		}

		/**
		 * @param filterFormat Override for the table content control format
		 * @return This builder
		 */
		public Builder<T, X> withFilterFormat(Format<TableContentControl> filterFormat) {
			theFilterFormat = filterFormat;
			return this;
		}

		public Builder<T, X> withFilterCommitOnType(boolean filterCommitOnType) {
			isFilterCommitOnType = filterCommitOnType;
			return this;
		}

		public Builder<T, X> withItemName(String itemName) {
			theItemName = itemName;
			return this;
		}

		/** @return The build value selector */
		public ObservableValueSelector<T, X> build() {
			return new ObservableValueSelector<>(theSourceRows.safe(ThreadConstraint.EDT, theUntil), //
				theSourceColumns.safe(ThreadConstraint.EDT, theUntil), theDestColumns.safe(ThreadConstraint.EDT, theUntil),
				theMap, isReEvalOnUpdate, theUntil, isIncludedByDefault, theFilterFormat, isFilterCommitOnType, theItemName);
		}
	}

	/**
	 * Simple program to pop up an {@link ObservableValueSelector} to test layouts and functionality
	 *
	 * @param args Command-line arguments, ignored
	 */
	public static void main(String... args) {
		ObservableCollection<Map<String, String>> rows = ObservableCollection
			.build(TypeTokens.get().keyFor(Map.class).<Map<String, String>> parameterized(String.class, String.class)).onEdt().build();
		TypeToken<SelectableValue<Map<String, String>, Map<String, String>>> selValueType = TypeTokens.get().keyFor(SelectableValue.class)//
			.parameterized(rows.getType(), rows.getType());
		ObservableCollection<CategoryRenderStrategy<SelectableValue<Map<String, String>, Map<String, String>>, String>> columns = ObservableCollection
			.build(TypeTokens.get().keyFor(CategoryRenderStrategy.class)
				.<CategoryRenderStrategy<SelectableValue<Map<String, String>, Map<String, String>>, String>> parameterized(selValueType,
					TypeTokens.get().STRING))
			.onEdt().build();
		EventQueue.invokeLater(() -> {
			columns.add(new CategoryRenderStrategy<SelectableValue<Map<String, String>, Map<String, String>>, String>("A",
				TypeTokens.get().STRING, map -> {
					return map.getSource().get("A");
				})//
				.formatText(v -> v == null ? "" : v).withMutation(mut -> {
					mut.mutateAttribute((map, v) -> map.getSource().put("A", v)).asText(Format.TEXT).withRowUpdate(true);
				}).withWidths(50, 100, 150));
			columns.add(new CategoryRenderStrategy<SelectableValue<Map<String, String>, Map<String, String>>, String>("B",
				TypeTokens.get().STRING, map -> {
					return map.getSource().get("B");
				})//
				.formatText(v -> v == null ? "" : v).withMutation(mut -> {
					mut.mutateAttribute((map, v) -> map.getSource().put("B", v)).asText(Format.TEXT).withRowUpdate(true);
				}).withWidths(50, 100, 150));
			columns.add(new CategoryRenderStrategy<SelectableValue<Map<String, String>, Map<String, String>>, String>("C",
				TypeTokens.get().STRING, map -> {
					return map.getSource().get("C");
				})//
				.formatText(v -> v == null ? "" : v).withMutation(mut -> {
					mut.mutateAttribute((map, v) -> map.getSource().put("D", v)).asText(Format.TEXT).withRowUpdate(true);
				}).withWidths(50, 150, 550));
			columns.add(new CategoryRenderStrategy<SelectableValue<Map<String, String>, Map<String, String>>, String>("D",
				TypeTokens.get().STRING, map -> {
					return map.getSource().get("D");
				})//
				.formatText(v -> v == null ? "" : v).withMutation(mut -> {
					mut.mutateAttribute((map, v) -> map.getSource().put("A", v)).asText(Format.TEXT).withRowUpdate(true);
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
				rows.add(row);
			}
			System.out.println();
			ObservableValueSelector<Map<String, String>, Map<String, String>> ovs = ObservableValueSelector
				.<Map<String, String>, Map<String, String>> build(rows, columns,
					ObservableCollection.of(columns.getType(),
						new CategoryRenderStrategy<SelectableValue<Map<String, String>, Map<String, String>>, String>("Value",
							TypeTokens.get().STRING, m -> m.getSource().toString()).withWidths(100, 200, 300)),
					v -> v)
				.build();
			JFrame w = ObservableSwingUtils.buildUI()//
				.systemLandF()//
				.withTitle(ObservableValueSelector.class.getSimpleName() + " Tester")//
				.withSize(1020, 900)//
				.withCloseAction(JFrame.EXIT_ON_CLOSE)//
				.withHContent(new JustifiedBoxLayout(false).mainJustified().crossJustified().forceFill(true),
					p -> p.fill().fillV()//
					.addComponent(null, ovs, c -> c.fill().fillV()))
				.run(null).getWindow();
			w.setOpacity(1.0f);
			w.setBackground(Colors.tomato);
			if (w.getContentPane() instanceof JComponent)
				((JComponent) w.getContentPane()).setOpaque(true);
			w.getContentPane().setBackground(Colors.orange);
		});
	}
}
