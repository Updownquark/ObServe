package org.observe.util.swing;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.observe.Subscription;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableList;
import org.observe.collect.ObservableSortedSet;
import org.observe.collect.impl.ObservableTreeSet;
import org.qommons.Transaction;

import com.google.common.reflect.TypeToken;

/**
 * A ListSelectionModel backed by observable collections
 *
 * @param <E> The type of item to select
 */
public class ObservableListSelectionModel<E> implements ListSelectionModel {
	private final ObservableList<E> theValues;
	private final ObservableSortedSet<Integer> theSelectedIndexes;
	private final ObservableCollection<E> theSelectedValues;
	private final Map<ListSelectionListener, Subscription> theListeners;
	private int theSelectionMode;
	private int theAnchor;
	private int theLead;
	private boolean isValueAdjusting;

	/** @param values The list of values to be selected from */
	public ObservableListSelectionModel(ObservableList<E> values) {
		theValues = values;
		theSelectedIndexes = new ObservableTreeSet<>(new TypeToken<Integer>() {}, Integer::compareTo).filterAdd(index -> {
			if (index < 0)
				throw new IllegalArgumentException("" + index);
			return true;
		});
		theSelectedValues = theSelectedIndexes.filter(index -> index < theValues.size())
				.map(theValues.getType(), index -> theValues.get(index), value -> {
					int index;
					for (index = 0; index < theValues.size(); index++) {
						if (value == theValues.get(index) && !theSelectedIndexes.contains(index))
							break;
					}
					if (index == theValues.size())
						throw new IllegalStateException("This should be prevented");
					return index;
				}).filterAdd(value -> {
					int index;
					for (index = 0; index < theValues.size(); index++) {
						if (value == theValues.get(index) && !theSelectedIndexes.contains(index))
							break;
					}
					return index < theValues.size();
				});
		theListeners = new LinkedHashMap<>();
	}

	/** @return The list of values being selected from */
	public ObservableList<E> getValues() {
		return theValues;
	}

	/** @return The set of selected indexes */
	public ObservableSortedSet<Integer> getSelectedIndexes() {
		return theSelectedIndexes;
	}

	/** @return The selected values */
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
		if (before)
			addSelectionInterval(index - length + 1, index);
		else
			addSelectionInterval(index, index + length - 1);
	}

	@Override
	public void removeIndexInterval(int index0, int index1) {
		removeSelectionInterval(index0, index1);
	}

	@Override
	public void setValueIsAdjusting(boolean valueIsAdjusting) {
		isValueAdjusting = valueIsAdjusting;
	}

	@Override
	public boolean getValueIsAdjusting() {
		return isValueAdjusting;
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
			int[][] intervals = ObservableSwingUtils.getContinuousIntervals(toArray(evt.values),
					true);
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
