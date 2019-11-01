package org.observe.util.swing;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
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
import org.qommons.collect.BetterSortedList;
import org.qommons.collect.BetterSortedList.SortedSearchFilter;
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

		public T getSource() {
			return sourceElement.get();
		}

		public X getDest() {
			return theDestValue;
		}

		public CollectionElement<T> getSourceElement() {
			return sourceElement;
		}

		public boolean isIncluded() {
			return included;
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

	private JTable theSourceTable;
	private JTable theDestTable;
	private final ObservableCollection<T> theSourceRows;
	private final ObservableSortedSet<SelectableValue<T, X>> theSelectableValues;
	private final ObservableCollection<SelectableValue<T, X>> theDisplayedValues;
	private final ObservableCollection<SelectableValue<T, X>> theIncludedValues;

	private final Function<? super T, ? extends X> theMap;
	private final boolean reEvalOnUpdate;

	private final JButton theIncludeAllButton;
	private final JButton theIncludeButton;
	private final JButton theExcludeButton;
	private final JButton theExcludeAllButton;
	private final JLabel theSelectionCountLabel;
	private ObservableTextField<ListFilter> theSearchField;
	private final SimpleObservable<Void> theSelectionChanges;

	private final SettableValue<ListFilter> theFilterText;

	private boolean isIncludedByDefault;

	public ObservableValueSelector(ObservableCollection<T> sourceRows, //
		ObservableCollection<? extends CategoryRenderStrategy<? super SelectableValue<T, X>, ?>> sourceColumns, //
			ObservableCollection<? extends CategoryRenderStrategy<? super SelectableValue<T, X>, ?>> destColumns, //
				Function<? super T, ? extends X> map, boolean reEvalOnUpdate, Observable<?> until, //
				boolean includedByDefault, Format<ListFilter> filterFormat) {
		super(new JustifiedBoxLayout(false).crossJustified().mainJustified());
		theSourceRows = sourceRows;
		theMap = map;
		this.reEvalOnUpdate = reEvalOnUpdate;
		theSelectableValues = ObservableSortedSet.create(new TypeToken<SelectableValue<T, X>>() {
		}, SelectableValue::compareTo);
		theFilterText = new SimpleSettableValue<>(ListFilter.class, false).withValue(ListFilter.INCLUDE_ALL, null);
		theIncludedValues = theSelectableValues.flow().filter(sv -> sv.isIncluded() ? null : "Not Included").unmodifiable()
			.collectActive(until);
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
		theSelectionCountLabel = new JLabel();

		PanelPopulation.populateHPanel(this, getLayout(), until)//
		.addVPanel(
			srcPanel -> srcPanel.addTextField(null, theFilterText, filterFormat == null ? ListFilter.FORMAT : filterFormat, tf -> tf//
				.modifyEditor(tfe -> {
					theSearchField = tfe;
					tfe.setCommitOnType(true).setEmptyText("Search...")
					.setIcon(ObservableSwingUtils.getFixedIcon(getClass(), "/icons/search.png", 16, 16));
				}).fill())//
			.addTable(theSelectableValues, srcTbl -> {
				theSourceTable = srcTbl.getEditor();
				srcTbl.withColumns(sourceColumns).withFiltering(theFilterText).fill();
			}).fill())//
		.addVPanel(buttonPanel -> {
			buttonPanel.getContainer().setLayout(new JustifiedBoxLayout(true));
			buttonPanel.addComponent(null, theIncludeAllButton, null)//
			.addComponent(null, theIncludeButton, null)//
			.addComponent(null, theExcludeButton, null)//
			.addComponent(null, theExcludeAllButton, null)//
			.fill();
		})
		.addVPanel(destPanel -> destPanel//
			.addComponent(null, theSelectionCountLabel, null)//
			.addTable(theIncludedValues, destTbl -> {
				theDestTable = destTbl.getEditor();
				destTbl.withColumns(destColumns).fill();
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
		ListSelectionListener leftLSL = evt -> {
			if (selectCallbackLock[0])
				return;
			boolean change = false;
			selectCallbackLock[0] = true;
			try {
				for (int i = evt.getFirstIndex(); i <= evt.getLastIndex() && i < sourceModel.getRowModel().getSize(); i++) {
					SelectableValue<T, X> sv = sourceModel.getRowModel().getElementAt(i);
					if (sv.selected == theSourceTable.getSelectionModel().isSelectedIndex(i))
						continue;
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
		};
		theSourceTable.getSelectionModel().addListSelectionListener(leftLSL);
		subs.add(() -> theSourceTable.getSelectionModel().removeListSelectionListener(leftLSL));
		ListSelectionListener rightLSL = evt -> {
			if (selectCallbackLock[0])
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

	public ObservableTextField<ListFilter> getSearchField() {
		return theSearchField;
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

	void checkButtonStates() {
		int totalCount = theSourceRows.size();
		int includedCount = theIncludedValues.size();
		theSelectionCountLabel.setText(includedCount + " of " + totalCount);

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

		theIncludeAllButton.setEnabled(excluded > 0);
		if (excluded == 0)
			theIncludeAllButton.setToolTipText("All items included");
		else if (excluded == 0)
			theIncludeAllButton.setToolTipText("Include 1 item");
		else
			theIncludeAllButton.setToolTipText("Include all " + excluded + " excluded items");

		theIncludeButton.setEnabled(selectedExcluded > 0);
		theIncludeButton
		.setToolTipText(selectedExcluded > 0 ? "Include " + selectedExcluded + " selected item" + (selectedExcluded == 1 ? "" : "s")
			: (hasSelection ? "All selected items are included" : "No items selected"));

		theExcludeButton.setEnabled(selectedIncluded > 0);
		theExcludeButton
		.setToolTipText(selectedIncluded > 0 ? "Exclude " + selectedIncluded + " selected item" + (selectedIncluded == 1 ? "" : "s")
			: (hasSelection ? "No included items selected" : "No items selected"));

		theExcludeAllButton.setEnabled(includedCount > 0);
		if (includedCount == 0)
			theExcludeAllButton.setToolTipText("No items included");
		else if (includedCount == 0)
			theExcludeAllButton.setToolTipText("Exclude 1 included item");
		else
			theExcludeAllButton.setToolTipText("Exclude all " + includedCount + " included items");
	}
}
