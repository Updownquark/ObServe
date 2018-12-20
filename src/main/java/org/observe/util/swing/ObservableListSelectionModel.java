package org.observe.util.swing;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.observe.Observable;
import org.observe.Subscription;
import org.observe.collect.CollectionChangeEvent;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableSortedSet;
import org.observe.util.TypeTokens;
import org.qommons.Transaction;
import org.qommons.collect.CollectionElement;

/**
 * A ListSelectionModel backed by observable collections
 *
 * @param <E> The type of item to select
 */
public class ObservableListSelectionModel<E> implements ListSelectionModel {
	private final ObservableCollection<E> theValues;
	private final ObservableSortedSet<Integer> theSelectedIndexes;
	private final ObservableCollection<E> theSelectedValues;
	private final Map<ListSelectionListener, Subscription> theListeners;
	private int theSelectionMode;
	private int theAnchor;
	private int theLead;
	private boolean isValueAdjusting;

	/**
	 * @param values The list of values to be selected from
	 * @param until The observable that, when fired, will dispose of this model's persistent resources
	 */
	public ObservableListSelectionModel(ObservableCollection<E> values, Observable<?> until) {
		theValues = values;
		theSelectedIndexes = ObservableSortedSet.create(TypeTokens.get().of(Integer.TYPE), Integer::compare).flow()
			.filterMod(fm -> fm.filterAdd(idx -> {
				if (idx < 0)
					return "Negative index";
				else if (idx >= theValues.size())
					return "Index>size";
				else
					return null;
			})).collect();
		theSelectedValues = theSelectedIndexes.flow().map(values.getType(), idx -> theValues.get(idx)).collect();
		theListeners = new LinkedHashMap<>();

		Subscription sub = theValues.changes().act(evt -> {
			switch (evt.type) {
			case add:
				break;
			case remove:
				try (Transaction t = theSelectedIndexes.lock(true, evt)) {
					theSelectedIndexes.removeAll(evt.getElementsReversed().stream().map(el -> el.index).collect(Collectors.toList()));
				}
				break;
			case set:
				try (Transaction t = theSelectedIndexes.lock(true, evt)) {
					for (CollectionChangeEvent.ElementChange<E> el : evt.elements) {
						CollectionElement<Integer> selectedEl = theSelectedIndexes.getElement(Integer.valueOf(el.index), true);
						if (selectedEl != null)
							theSelectedIndexes.mutableElement(selectedEl.getElementId()).set(el.index); // Update
					}
				}
			}
		});
		if (until != null)
			until.take(1).act(v -> sub.unsubscribe());
	}

	/** @return The list of values being selected from */
	public ObservableCollection<E> getValues() {
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

	/* This is not working
	public static <T> ObservableCollection<T> observableSelection(ObservableCollection<T> dataModel, ListSelectionModel selectionModel,
		Observable<?> until) {
		if (until == null)
			until = Observable.empty;
		ObservableSortedSet<ElementId> selectedElements = ObservableSortedSet.create(TypeTokens.get().of(ElementId.class),
			ElementId::compareTo);
		BitSet selection = new BitSet();
		for (int i = selectionModel.getMinSelectionIndex(); i <= selectionModel.getMaxSelectionIndex(); i++) {
			if (selectionModel.isSelectedIndex(i)) {
				selection.set(i);
				selectedElements.add(dataModel.getElement(i).getElementId());
			}
		}
		Subscription modelSub = dataModel.onChange(evt -> {
			switch (evt.getType()) {
			case add:
			case remove:
				break; // Should be taken care of by the UI
			case set:
				CollectionElement<ElementId> found = selectedElements.getElement(evt.getElementId(), true);
				if (found != null)
					try (Transaction t = selectedElements.lock(true, false, evt)) {
						selectedElements.mutableElement(found.getElementId()).set(evt.getElementId());
					}
				break;
			}
		});
		until.take(1).act(v -> modelSub.unsubscribe());
		Causable[] cause = new Causable[1];
		Transaction[] causeFinish = new Transaction[1];
		ListSelectionListener listener = evt -> {
			Object c;
			if (evt.getValueIsAdjusting()) {
				if (cause[0] == null) {
					c = cause[0] = Causable.simpleCause(null);
					causeFinish[0] = Causable.use(cause[0]);
				} else
					c = cause[0];
			} else if (cause[0] != null) {
				c = cause[0];
				cause[0] = null;
			} else
				c = evt;
			try (Transaction t = selectedElements.lock(true, c)) {
				if (selectionModel.isSelectedIndex(evt.getFirstIndex())) {
					// Selection added
					int i = selection.nextClearBit(evt.getFirstIndex());
					if (i <= evt.getLastIndex()) {
						CollectionElement<T> el = dataModel.getElement(i);
						CollectionElement<ElementId> idEl = selectedElements.search(el.getElementId(), SortedSearchFilter.Less);
						for (; i <= evt.getLastIndex(); i++) {
							if (idEl == null) {
								// Means the new selection will be at the beginning of the selection list
								if (selectedElements.isEmpty())
									idEl = selectedElements.addElement(el.getElementId(), true);
								else
									idEl = selectedElements.addElement(el.getElementId(),
										selectedElements.getTerminalElement(true).getElementId(), null, true);
							} else
								idEl = selectedElements.addElement(el.getElementId(), idEl.getElementId(), null, true);
						}
						selection.set(evt.getFirstIndex(), evt.getLastIndex() + 1);
					}
				} else {
					// Selection removed
					int i = selection.previousSetBit(evt.getLastIndex());
					int post = selection.nextSetBit(i + 1);
					CollectionElement<ElementId> idEl;
					if (post < 0)
						idEl = selectedElements.getTerminalElement(false);
					else {
						BitSet copy = (BitSet) selection.clone();
						copy.clear(0, i + 1);
						// Now copy's cardinality is the number of selected elements beyond the event's range
						idEl = selectedElements.getElement(selectedElements.size() - copy.cardinality() - 1);
					}
					for (; i >= evt.getFirstIndex(); i--) {
						if (!selection.get(i))
							continue;
						CollectionElement<ElementId> prev = selectedElements.getAdjacentElement(idEl.getElementId(), false);
						selectedElements.mutableElement(idEl.getElementId()).remove();
						idEl = prev;
					}
					selection.clear(evt.getFirstIndex(), evt.getLastIndex() + 1);
				}
			} finally {
				if (causeFinish[0] != null && !evt.getValueIsAdjusting()) {
					causeFinish[0].close();
					causeFinish[0] = null;
				}
			}
		};
		selectionModel.addListSelectionListener(listener);
		until.act(v -> selectionModel.removeListSelectionListener(listener));
		selectedElements.changes().takeUntil(until).act(evt -> {
			int[][] intervals = ObservableSwingUtils.getContinuousIntervals(evt.elements, evt.type != CollectionChangeType.remove);
			switch (evt.type) {
			case add:
				for (int[] interval : intervals)
					selectionModel.addSelectionInterval(interval[0], interval[1]);
				break;
			case remove:
				for (int[] interval : intervals)
					selectionModel.removeSelectionInterval(interval[0], interval[1]);
				break;
			case set:
				break;
			}
		});
		return selectedElements.flow()
			.map(dataModel.getType(), el -> dataModel.getElement(el).get(), opts -> opts.cache(false).withReverse(v -> {
				CollectionElement<T> el = dataModel.getElement(v, true);
				if (el == null)
					throw new IllegalArgumentException("Value not present in model");
				else
					return el.getElementId();
			})).filterMod(fm -> fm.filterAdd(v -> dataModel.contains(v) ? null : "Value not present in model")).collectPassive();
	}*/
}
