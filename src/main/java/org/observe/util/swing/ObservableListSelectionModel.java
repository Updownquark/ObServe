package org.observe.util.swing;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.ListModel;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.observe.Subscription;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableSortedSet;
import org.observe.util.TypeTokens;
import org.qommons.Transaction;
import org.qommons.collect.BetterSortedSet.SortedSearchFilter;
import org.qommons.collect.CollectionElement;

import com.google.common.reflect.TypeToken;

/**
 * A ListSelectionModel backed by observable collections
 *
 * @param <E> The type of item to select
 */
public class ObservableListSelectionModel<E> implements ListSelectionModel {
	private final ListModel<E> theValues;
	private final ObservableSortedSet<Integer> theSelectedIndexes;
	private final ObservableCollection<E> theSelectedValues;
	private final Map<ListSelectionListener, Subscription> theListeners;
	private int theSelectionMode;
	private int theAnchor;
	private int theLead;
	private Transaction valueAdjusting;

	/**
	 * @param type The type of the model values
	 * @param values The list of values to be selected from
	 */
	public ObservableListSelectionModel(TypeToken<E> type, ListModel<E> values) {
		theValues = values;
		ObservableSortedSet<Integer>[] selectedIndexes = new ObservableSortedSet[1];
		theSelectedIndexes = selectedIndexes[0] = ObservableSortedSet.create(TypeTokens.get().of(Integer.TYPE), Integer::compare).flow()
			.filterMod(fm -> fm.filterAdd(idx -> {
				if (idx < 0)
					return "Negative index";
				else if (idx >= theValues.getSize())
					return "Index>size";
				if (!selectedIndexes[0].isEmpty()) {
					switch (theSelectionMode) {
					case ListSelectionModel.SINGLE_SELECTION:
						return "Single-selection only";
					case ListSelectionModel.SINGLE_INTERVAL_SELECTION:
						if (idx < selectedIndexes[0].first() - 1 || idx > selectedIndexes[0].last() + 1)
							return "Selection would not be contiguous";
						break;
					}
				}
				return null;
			})).collect();
		theSelectedValues = theSelectedIndexes.flow().map(type, index -> theValues.getElementAt(index), opts -> opts.cache(false))
			.unmodifiable().collect();
		theListeners = new LinkedHashMap<>();
	}

	/** @return The values being selected from */
	public ListModel<E> getValues() {
		return theValues;
	}

	/** @return The set of selected indexes */
	public ObservableSortedSet<Integer> getSelectedIndexes() {
		return theSelectedIndexes;
	}

	/** @return The selected values -- unmodifiable */
	public ObservableCollection<E> getSelectedValues() {
		return theSelectedValues;
	}

	@Override
	public void setSelectionInterval(int index0, int index1) {
		try (Transaction t = theSelectedIndexes.lock(true, null)) {
			theSelectedIndexes.clear();
			for (int i = Math.min(index0, index1); i <= Math.max(index0, index1); i++)
				theSelectedIndexes.add(i);
		}
		theAnchor = index0;
		theLead = index1;
	}

	@Override
	public void addSelectionInterval(int index0, int index1) {
		if (index0 == -1 || index1 == -1) {
			return;
		}
		// If we only allow a single selection, channel through
		// setSelectionInterval() to enforce the rule.
		if (getSelectionMode() == SINGLE_SELECTION) {
			setSelectionInterval(index0, index1);
			return;
		}
		try (Transaction t = theSelectedIndexes.lock(true, null)) {
			for (int i = Math.min(index0, index1); i <= Math.max(index0, index1); i++)
				theSelectedIndexes.add(i);
		}
		theAnchor = index0;
		theLead = index1;
	}

	@Override
	public void removeSelectionInterval(int index0, int index1) {
		try (Transaction t = theSelectedIndexes.lock(true, null)) {
			for (int i = Math.min(index0, index1); i <= Math.max(index0, index1); i++)
				theSelectedIndexes.remove(i);
		}
		theAnchor = index0;
		theLead = index1;
	}

	@Override
	public int getMinSelectionIndex() {
		return theSelectedIndexes.isEmpty() ? -1 : theSelectedIndexes.first();
	}

	@Override
	public int getMaxSelectionIndex() {
		return theSelectedIndexes.isEmpty() ? -1 : theSelectedIndexes.last();
	}

	@Override
	public boolean isSelectedIndex(int index) {
		return theSelectedIndexes.contains(index);
	}

	@Override
	public int getAnchorSelectionIndex() {
		return theAnchor;
	}

	@Override
	public void setAnchorSelectionIndex(int index) {
		theAnchor = index;
	}

	@Override
	public int getLeadSelectionIndex() {
		return theLead;
	}

	@Override
	public void setLeadSelectionIndex(int index) {
		theLead = index;
	}

	@Override
	public void clearSelection() {
		theSelectedIndexes.clear();
	}

	@Override
	public boolean isSelectionEmpty() {
		return theSelectedIndexes.isEmpty();
	}

	@Override
	public void insertIndexInterval(int index, int length, boolean before) {
		CollectionElement<Integer> el = theSelectedIndexes.getTerminalElement(false);
		while (el != null) {
			int comp = Integer.compare(el.get(), index);
			if (comp > 0 || (comp == 0 && before))
				theSelectedIndexes.mutableElement(el.getElementId()).set(el.get() + length);
			else
				break;
			el = theSelectedIndexes.getAdjacentElement(el.getElementId(), false);
		}
	}

	@Override
	public void removeIndexInterval(int index0, int index1) {
		int length = index1 - index0 + 1;
		CollectionElement<Integer> el = theSelectedIndexes.search(i -> Integer.compare(index0, i), SortedSearchFilter.Greater);
		while (el != null) {
			if (el.get() <= index1)
				theSelectedIndexes.mutableElement(el.getElementId()).remove();
			else
				theSelectedIndexes.mutableElement(el.getElementId()).set(el.get() - length);
			el = theSelectedIndexes.getAdjacentElement(el.getElementId(), true);
		}
	}

	@Override
	public void setValueIsAdjusting(boolean valueIsAdjusting) {
		if (valueIsAdjusting) {
			if (valueAdjusting == null)
				valueAdjusting = theSelectedIndexes.lock(true, null);
		} else if (valueAdjusting != null) {
			valueAdjusting.close();
			valueAdjusting = null;
		}
	}

	@Override
	public boolean getValueIsAdjusting() {
		return valueAdjusting != null;
	}

	@Override
	public void setSelectionMode(int selectionMode) {
		theSelectionMode = selectionMode;
	}

	@Override
	public int getSelectionMode() {
		return theSelectionMode;
	}

	@Override
	public void addListSelectionListener(ListSelectionListener x) {
		theListeners.put(x, theSelectedIndexes.changes().act(evt -> {
			int[][] intervals = ObservableSwingUtils.getContinuousIntervals(toArray(evt.getValues()), true);
			for (int i = 0; i < intervals.length; i++)
				x.valueChanged(new ListSelectionEvent(this, intervals[i][0], intervals[i][1], i < intervals.length - 1));
		}));
	}

	private int[] toArray(List<Integer> values) {
		int[] ret = new int[values.size()];
		for (int i = 0; i < ret.length; i++)
			ret[i] = values.get(i);
		return ret;
	}

	@Override
	public void removeListSelectionListener(ListSelectionListener x) {
		Subscription sub = theListeners.remove(x);
		if (sub != null)
			sub.unsubscribe();
	}
}
