package org.observe.util.swing;

import java.util.List;
import java.util.stream.Collectors;

import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

import org.observe.Observable;
import org.observe.collect.CollectionChangeType;
import org.observe.collect.ObservableCollection;
import org.qommons.Transaction;
import org.qommons.collect.ElementId;

import com.google.common.reflect.TypeToken;

public class ObservableTableFiltering<R, M extends ObservableTableModel<R>> extends RowSorter<M> {
	private final M theModel;
	private final ObservableCollection<ObservableColumnFiltering<?>> theColumnFilters;

	public ObservableTableFiltering(M model, Observable<?> until) {
		theModel = model;
		theColumnFilters=ObservableCollection.create(new TypeToken<ObservableColumnFiltering<?>>() {});
		theColumnFilters.onChange(evt -> {
			if (evt.getType() == CollectionChangeType.add)
				evt.getNewValue().theFilterId = evt.getElementId();
		});
		ObservableSwingUtils.onEQ(()->{
			for(int i=0;i<model.getColumnModel().getSize();i++)
				theColumnFilters.add(new ObservableColumnFiltering<>(model.getColumnModel().getElementAt(i)));
			model.getColumnModel().addListDataListener(new ListDataListener() {
				@Override
				public void intervalAdded(ListDataEvent e) {
					for(int i=e.getIndex0();i<=e.getIndex1();i++)
						theColumnFilters.add(new ObservableColumnFiltering<>(model.getColumnModel().getElementAt(i)));
				}

				@Override
				public void intervalRemoved(ListDataEvent e) {
					for(int i=e.getIndex1();i>=e.getIndex0();i--)
						theColumnFilters.remove(i);
				}

				@Override
				public void contentsChanged(ListDataEvent e) {
					for(int i=e.getIndex0();i<=e.getIndex1();i++){
						CategoryRenderStrategy<? super R, ?> column = model.getColumnModel().getElementAt(i);
						if(column!=theColumnFilters.get(i).getColumn())
							theColumnFilters.set(i, new ObservableColumnFiltering<>(column));
						else
							theColumnFilters.get(i).columnUpdated();
					}
				}
			});
		});
	}

	@Override
	public M getModel() {
		return theModel;
	}

	@Override
	public void toggleSortOrder(int column) {
		theColumnFilters.get(column).toggleSortOrder();
	}

	@Override
	public int convertRowIndexToModel(int index) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int convertRowIndexToView(int index) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void setSortKeys(List<? extends SortKey> keys) {
		SortOrder[] orders = new SortOrder[theColumnFilters.size()];
		for (SortKey key : keys)
			orders[key.getColumn()] = key.getSortOrder();
		try (Transaction t = theColumnFilters.lock(true, null)) {
			for (int i = 0; i < theColumnFilters.size(); i++) {
				if (orders[i] != null)
					theColumnFilters.get(i).setSortOrder(orders[i]);
			}
		}
	}

	@Override
	public List<? extends SortKey> getSortKeys() {
		return theColumnFilters.stream().filter(f -> f.getSortOrder() != SortOrder.UNSORTED)
			.map(f -> new SortKey(f.getColumnIndex(), f.getSortOrder())).collect(Collectors.toList());
	}

	@Override
	public int getViewRowCount() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getModelRowCount() {
		return theModel.getRowCount();
	}

	// We don't need any of this--we're listening to the model directly
	@Override
	public void modelStructureChanged() {}

	@Override
	public void allRowsChanged() {}

	@Override
	public void rowsInserted(int firstRow, int endRow) {}

	@Override
	public void rowsDeleted(int firstRow, int endRow) {}

	@Override
	public void rowsUpdated(int firstRow, int endRow) {}

	@Override
	public void rowsUpdated(int firstRow, int endRow, int column) {}

	public class ObservableColumnFiltering<C> {
		private final CategoryRenderStrategy<R, C> theColumn;
		private SortOrder theSortOrder;
		ElementId theFilterId;

		ObservableColumnFiltering(CategoryRenderStrategy<R, C> column) {
			theColumn = column;
			theSortOrder = SortOrder.UNSORTED;
		}

		public CategoryRenderStrategy<R, C> getColumn() {
			return theColumn;
		}

		public int getColumnIndex() {
			for (int i = 0; i < theModel.getColumnModel().getSize(); i++)
				if (theModel.getColumnModel().getElementAt(i) == theColumn)
					return i;
			return -1;
		}

		public SortOrder getSortOrder() {
			return theSortOrder;
		}

		public ObservableColumnFiltering<C> setSortOrder(SortOrder sortOrder) {
			theSortOrder = sortOrder;
			// TODO event
			return this;
		}

		public ObservableColumnFiltering<C> toggleSortOrder() {
			switch (theSortOrder) {
			case ASCENDING:
				setSortOrder(SortOrder.DESCENDING);
				break;
			case DESCENDING:
			case UNSORTED:
				setSortOrder(SortOrder.ASCENDING);
				break;
			}
			return this;
		}

		void columnUpdated() {
			// TODO Auto-generated method stub

		}
	}
}
