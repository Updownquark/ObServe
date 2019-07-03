package org.observe.util.swing;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Predicate;

import javax.swing.RowFilter;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

import org.observe.Observable;
import org.observe.Subscription;
import org.observe.collect.CollectionChangeEvent;
import org.observe.collect.CollectionChangeEvent.ElementChange;
import org.observe.collect.CollectionChangeType;
import org.observe.collect.ObservableCollection;
import org.qommons.Transaction;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;

import com.google.common.reflect.TypeToken;

public class ObservableTableFiltering<R, M extends ObservableTableModel<R>> extends RowFilter<M, Integer> {
	private final M theModel;
	private final ObservableCollection<ObservableColumnFiltering<?>> theColumnFilters;
	private int[] theRowFilters;
	private int[][] theExtraRowFilters;

	public ObservableTableFiltering(M model, Observable<?> until) {
		theModel = model;
		// Both the column filter collection and the row model are only ever updated on the EDT
		// So we don't need to worry about concurrent changes out-of-order events
		theColumnFilters = ObservableCollection.create(new TypeToken<ObservableColumnFiltering<?>>() {});
		List<Subscription> subs = new LinkedList<>();
		boolean[] unsubscribed = new boolean[1];
		subs.add(() -> unsubscribed[0] = true);
		subs.add(theColumnFilters.onChange(evt -> { // Install the element ID for each column filter added
			if (evt.getType() == CollectionChangeType.add)
				evt.getNewValue().theFilterId = evt.getElementId();
		}));
		subs.add(theColumnFilters.changes().act(evt -> {
			switch (evt.type) {
			case add:
				columnsAdded(evt);
				break;
			case remove:
				columnsRemoved(evt);
				break;
			case set:
				for (ElementChange<ObservableColumnFiltering<?>> el : evt.elements)
					applyFilters(0, theRowFilters.length, el.index, el.index + 1);
				break;
			}
		}));
		ListDataListener columnListener = new ListDataListener() {
			@Override
			public void intervalAdded(ListDataEvent e) {
				try (Transaction t = theColumnFilters.lock(true, e)) {
					for (int c = e.getIndex0(); c <= e.getIndex1(); c++)
						theColumnFilters.add(new ObservableColumnFiltering<>(model.getColumn(c)));
				}
			}

			@Override
			public void intervalRemoved(ListDataEvent e) {
				try (Transaction t = theColumnFilters.lock(true, e)) {
					for (int c = e.getIndex1(); c >= e.getIndex0(); c--)
						theColumnFilters.remove(c);
				}
			}

			@Override
			public void contentsChanged(ListDataEvent e) {
				try (Transaction t = theColumnFilters.lock(true, e)) {
					for (int c = e.getIndex0(); c <= e.getIndex1(); c++) {
						ObservableColumnFiltering<?> cf = theColumnFilters.get(c);
						CategoryRenderStrategy<? super R, ?> column = model.getColumn(c);
						if (column == cf.getColumn()) {
							cf.updated();
							theColumnFilters.set(c, cf);
						} else
							theColumnFilters.set(c, new ObservableColumnFiltering<>(column));
					}
				}
			}
		};
		subs.add(() -> model.getColumnModel().removeListDataListener(columnListener));
		ObservableSwingUtils.onEQ(() -> {
			if (unsubscribed[0])
				return;
			// Initialize row index filters
			theRowFilters = new int[model.getRowCount()];
			{
				int extraRFCount = getExtraRFColumnCount(theColumnFilters.size());
				if (extraRFCount > 0)
					theExtraRowFilters = new int[model.getRowCount()][extraRFCount];
			}
			// Initialize column filters and begin listening for column changes
			try (Transaction t = theColumnFilters.lock(true, null)) {
				for (int i = 0; i < model.getColumnCount(); i++)
					theColumnFilters.add(new ObservableColumnFiltering<>(model.getColumn(i)));
			}
			model.getColumnModel().addListDataListener(columnListener);
		});
		ListDataListener rowListener = new ListDataListener() {
			@Override
			public void intervalAdded(ListDataEvent e) {
				int newRows = e.getIndex1() - e.getIndex0() + 1;
				int[] newRFs = new int[theRowFilters.length + newRows];
				System.arraycopy(theRowFilters, e.getIndex0(), newRFs, e.getIndex1() + 1, newRows);
				theRowFilters = newRFs;
				if (theExtraRowFilters != null) {
					int[][] newExtraRFs = new int[theRowFilters.length][getExtraRFColumnCount(theColumnFilters.size())];
					System.arraycopy(theExtraRowFilters, e.getIndex0(), newExtraRFs, e.getIndex1() + 1, newRows);
					theExtraRowFilters = newExtraRFs;
				}
				applyFilters(e.getIndex0(), e.getIndex1() + 1, 0, theColumnFilters.size());
			}

			@Override
			public void intervalRemoved(ListDataEvent e) {
				int removedRows = e.getIndex1() - e.getIndex0() + 1;
				int[] newRowFilters = new int[theRowFilters.length - removedRows];
				System.arraycopy(theRowFilters, 0, newRowFilters, 0, e.getIndex0());
				System.arraycopy(theRowFilters, e.getIndex1() + 1, newRowFilters, e.getIndex0(), theRowFilters.length - e.getIndex1() - 1);
				theRowFilters = newRowFilters;
				if (theExtraRowFilters != null) {
					int[][] newERF = new int[theRowFilters.length][getExtraRFColumnCount(theColumnFilters.size())];
					System.arraycopy(theExtraRowFilters, 0, newERF, 0, e.getIndex0());
					System.arraycopy(theExtraRowFilters, e.getIndex1() + 1, newERF, e.getIndex0(),
						theExtraRowFilters.length - e.getIndex1() - 1);
					theExtraRowFilters = newERF;
				}
			}

			@Override
			public void contentsChanged(ListDataEvent e) {
				applyFilters(e.getIndex0(), e.getIndex1() + 1, 0, theColumnFilters.size());
			}
		};
		model.getRowModel().addListDataListener(rowListener);
		subs.add(() -> model.getRowModel().removeListDataListener(rowListener));
		until.take(1).act(__ -> ObservableSwingUtils.onEQ(() -> {
			Subscription.forAll(subs).unsubscribe();
		}));
	}

	private void columnsAdded(CollectionChangeEvent<ObservableColumnFiltering<?>> evt) {
		int newColumnCount = theColumnFilters.size();
		int oldColumnCount = newColumnCount - evt.elements.size();
		int oldExtraCC = getExtraRFColumnCount(oldColumnCount);
		int newExtraCC = getExtraRFColumnCount(newColumnCount);
		if (theExtraRowFilters == null && newExtraCC > 0)
			theExtraRowFilters = new int[theRowFilters.length][newExtraCC];
		// This event's added indexes aren't necessarily contiguous, but they are monotonically increasing
		for (int r = 0; r < theRowFilters.length; r++) {
			R row = theModel.getRow(r);
			if (newExtraCC == 0) {
				int bits = theRowFilters[r];
				int oldMask = 1 << (31 - oldColumnCount);
				int newMask = 1 << (31 - newColumnCount);

				int c = oldColumnCount - 1;
				for (ElementChange<ObservableColumnFiltering<?>> el : evt.getElementsReversed()) {
					// Move bits above the new element up
					for (; c >= el.index; c--) {
						if ((bits & oldMask) != 0)
							bits |= newMask;
						bits &= ~oldMask; // Clear the old bit
						oldMask <<= 1;
						newMask <<= 1;
					}
					if (el.newValue.test(row))
						bits |= newMask;
					newMask <<= 1;
				}
				theRowFilters[r] = bits;
			} else {
				if (oldExtraCC == 0) { // extra row filters created from null above, no need to add more spaces
				} else if (newExtraCC > oldExtraCC) {
					int[] newExtraRowFilters = new int[newExtraCC];
					System.arraycopy(theExtraRowFilters[r], 0, newExtraRowFilters, 0, oldExtraCC);
					theExtraRowFilters[r] = newExtraRowFilters;
				}
				int oldExtraIdx = oldExtraCC - 1;
				int oldBits = oldExtraIdx < 0 ? theRowFilters[r] : theExtraRowFilters[r][oldExtraIdx];
				int newExtraIdx = newExtraCC - 1;
				int newBits = theExtraRowFilters[r][newExtraIdx];
				int oldMask = 1 << (31 - (oldColumnCount & 0x1f));
				int newMask = 1 << (31 - (newColumnCount & 0x1f));

				int c = oldColumnCount - 1;
				for (ElementChange<ObservableColumnFiltering<?>> el : evt.getElementsReversed()) {
					// Move bits above the new element up
					while (c >= el.index) {
						if ((oldBits & oldMask) != 0)
							newBits |= newMask;
						if (oldExtraIdx == newExtraIdx)
							newBits &= ~oldMask; // Clear the old bit
						oldMask <<= 1;
						newMask <<= 1;

						c--;
						if (c >= 0) {
							if (oldMask == 0) { // Precess one int for the old
								theExtraRowFilters[r][oldExtraIdx] = oldBits; // Store the cleared data
								oldExtraIdx--;
								oldBits = oldExtraIdx < 0 ? theRowFilters[r] : theExtraRowFilters[r][oldExtraIdx];
								oldMask = 1;
							}
							if (newMask == 0) { // Precess one int for the new
								theExtraRowFilters[r][newExtraIdx] = newBits;
								newExtraIdx--;
								newBits = newExtraIdx < 0 ? theRowFilters[r] : theExtraRowFilters[r][newExtraIdx];
								newMask = 1;
							}
						}
					}
					if (el.newValue.test(row))
						newBits |= newMask;
					newMask <<= 1;
					if (newMask == 0) { // Precess one int for the new
						theExtraRowFilters[r][newExtraIdx] = newBits;
						newExtraIdx--;
						newBits = newExtraIdx < 0 ? theRowFilters[r] : theExtraRowFilters[r][newExtraIdx];
						newMask = 1;
					}
				}
				theRowFilters[r] = newBits;
			}
		}
	}

	private void columnsRemoved(CollectionChangeEvent<ObservableTableFiltering<R, M>.ObservableColumnFiltering<?>> evt) {
		int newColumnCount = theColumnFilters.size();
		int oldColumnCount = newColumnCount - evt.elements.size();
		int oldExtraCC = getExtraRFColumnCount(oldColumnCount);
		int newExtraCC = getExtraRFColumnCount(newColumnCount);
		for (int r = 0; r < theRowFilters.length; r++) {
			if (oldExtraCC == 0) {
				int bits = theRowFilters[r];
				int oldMask = 1 << 31;
				int newMask = 1 << 31;

				int c = evt.elements.get(0).index;
				for (ElementChange<ObservableColumnFiltering<?>> el : evt.elements) {
					// Move bits above the removed element down
					for (; c < el.index; c++) {
						if ((bits & oldMask) != 0)
							bits |= newMask;
						bits &= ~oldMask; // Clear the old bit
						oldMask >>>= 1;
				newMask >>>= 1;
					}
					oldMask >>>= 1;
				}
				theRowFilters[r] = bits;
			} else {
				int oldExtraIdx = 0;
				int oldBits = theRowFilters[r];
				int newExtraIdx = 0;
				int newBits = theRowFilters[r];
				int oldMask = 1 << 31;
				int newMask = 1 << 31;

				int c = 0;
				for (ElementChange<ObservableColumnFiltering<?>> el : evt.elements) {
					// Move bits above the removed element down
					while (c < el.index) {
						if ((oldBits & oldMask) != 0) {
							newBits |= newMask;
						}
						if (oldExtraIdx == newExtraIdx) {
							newBits &= ~oldMask; // Clear the old bit
						}
						oldMask >>>= 1;
				newMask >>>= 1;

				c++;
				if (c < newColumnCount) {
					if (oldMask == 0) { // Process one int for the old
						theExtraRowFilters[r][oldExtraIdx] = oldBits; // Store the cleared data
						oldExtraIdx--;
						oldBits = oldExtraIdx < 0 ? theRowFilters[r] : theExtraRowFilters[r][oldExtraIdx];
						oldMask = 1 << 31;
					}
					if (newMask == 0) { // Process one int for the new
						theExtraRowFilters[r][newExtraIdx] = newBits;
						newExtraIdx--;
						newBits = newExtraIdx < 0 ? theRowFilters[r] : theExtraRowFilters[r][newExtraIdx];
						newMask = 1 << 31;
					}
				}
					}
					oldMask >>>= 1;
						if (oldMask == 0) { // Process one int for the old
							theExtraRowFilters[r][oldExtraIdx] = oldBits; // Store the cleared data
							oldExtraIdx--;
							oldBits = oldExtraIdx < 0 ? theRowFilters[r] : theExtraRowFilters[r][oldExtraIdx];
							oldMask = 1 << 31;
						}
				}
				theExtraRowFilters[r][newExtraIdx] = newBits;
				if (oldExtraCC == 0) { // Row filters will be cleared out below, no need to remove extra spaces
				} else if (newExtraCC > oldExtraCC) {
					int[] newExtraRowFilters = new int[newExtraCC];
					System.arraycopy(theExtraRowFilters[r], 0, newExtraRowFilters, 0, newExtraCC);
					theExtraRowFilters[r] = newExtraRowFilters;
				}
			}
		}
		if (theExtraRowFilters != null && newExtraCC == 0)
			theExtraRowFilters = null;
	}

	static int getExtraRFColumnCount(int columnCount) {
		int extraRFCount = columnCount >> 5;
							if (extraRFCount > 0 && (columnCount & 0x1f) != 0)
								extraRFCount++;
							return extraRFCount;
	}

	public ObservableColumnFiltering<?> getFilter(int column) {
		return theColumnFilters.get(column);
	}

	public ObservableCollection<ObservableColumnFiltering<?>> getColumnFilters() {
		return theColumnFilters.flow().unmodifiable(false).collectPassive();
	}

	public int getFilteredColumns(int row) {
		return theRowFilters[row];
	}

	public int[] getExtraFilteredColumns(int row) {
		return theExtraRowFilters == null ? new int[0] : Arrays.copyOf(theExtraRowFilters[row], theExtraRowFilters[row].length);
	}

	@Override
	public boolean include(RowFilter.Entry<? extends M, ? extends Integer> entry) {
		int rowIndex = entry.getIdentifier();
		boolean included = theRowFilters[rowIndex] == 0;
		if (included && theExtraRowFilters != null) {
			int extras = theExtraRowFilters[0].length; // Must be at least one row if we're getting called
			for (int i = 0; i < extras && included; i++)
				included = theExtraRowFilters[rowIndex][i] == 0;
		}
		return included;
	}

	void applyFilters(int fromRow, int toRow, int fromColumn, int toColumn) {
		for (int r = fromRow; r < toRow; r++) {
			R row = theModel.getRow(r);
			CollectionElement<ObservableColumnFiltering<?>> columnEl = theColumnFilters.getElement(fromColumn);
			int bits = theRowFilters[r];
			int rfIndex = -1;
			int mask = 1 << 31; // Start at the MSB
			for (int c = fromColumn; c < toColumn; c++) {
				if ((c & 0x1f) == 0 && c != fromColumn) {
					if (rfIndex < 0)
						theRowFilters[r] = bits;
					else
						theExtraRowFilters[r][rfIndex] = bits;
					rfIndex++;
					bits = theExtraRowFilters[r][rfIndex];
					mask = 1 << 31;
				}
				if (columnEl.get().test(row))
					bits |= mask;
				else
					bits &= ~mask;

				if (c < toColumn - 1)
					columnEl = theColumnFilters.getAdjacentElement(columnEl.getElementId(), true);
				mask >>>= 1;
			}
			if (rfIndex < 0)
				theRowFilters[r] = bits;
			else
				theExtraRowFilters[r][rfIndex] = bits;
		}
	}

	public class ObservableColumnFiltering<C> implements Predicate<R> {
		private final CategoryRenderStrategy<R, C> theColumn;
		ElementId theFilterId;
		private CategoryFilterStrategy<C> theFilterStrategy;
		private CategoryFilterStrategy.CategoryFilter<C> theFilter;

		ObservableColumnFiltering(CategoryRenderStrategy<R, C> column) {
			theColumn = column;
			theFilterStrategy = column.getFilterability();
			theFilter = theFilterStrategy == null ? null : theFilterStrategy.createFilter();
		}

		public CategoryRenderStrategy<R, C> getColumn() {
			return theColumn;
		}

		public int getColumnIndex() {
			return theColumnFilters.getElementsBefore(theFilterId);
		}

		public CategoryFilterStrategy.CategoryFilter<C> getFilter() {
			return theFilter;
		}

		void updated() {
			if (theFilterStrategy != theColumn.getFilterability()) {
				// TODO Should there be some way to update the existing filter instead?
				theFilter.clearFilters();
				theFilter = theFilterStrategy == null ? null : theFilterStrategy.createFilter();
			}
		}

		@Override
		public boolean test(R row) {
			return theFilter == null || theFilter.test(theColumn.getCategoryValue(row));
		}
	}
}
