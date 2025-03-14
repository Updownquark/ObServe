package org.observe.util.swing;

import java.util.function.Supplier;

public interface ModelCell<R, C> extends ModelRow<R> {
	C getCellValue();

	int getColumnIndex();

	boolean isCellHovered();

	boolean isCellFocused();

	@Override
	ModelCell<R, C> setEnabled(String enabled);

	public static class Default<M, C> extends ModelRow.Default<M> implements ModelCell<M, C> {
		private final C theCellValue;
		private final int theColumnIndex;
		private final boolean isCellHovered;
		private final boolean isCellFocused;

		public Default(Supplier<? extends M> modelValue, C cellValue, int rowIndex, int columnIndex, boolean selected, boolean focused,
			boolean rowHovered, boolean cellHovered, boolean expanded, boolean leaf) {
			super(modelValue, rowIndex, selected, focused, rowHovered, expanded, leaf);
			theCellValue = cellValue;
			theColumnIndex = columnIndex;
			isCellHovered = cellHovered;
			isCellFocused = focused;
		}

		@Override
		public C getCellValue() {
			return theCellValue;
		}

		@Override
		public int getColumnIndex() {
			return theColumnIndex;
		}

		@Override
		public boolean isCellHovered() {
			return isCellHovered;
		}

		@Override
		public boolean isCellFocused() {
			return isCellFocused;
		}

		@Override
		public ModelCell.Default<M, C> setEnabled(String enabled) {
			super.setEnabled(enabled);
			return this;
		}

		@Override
		public String toString() {
			return /*getModelValue() + ":" +*/ theCellValue + "@[" + getRowIndex() + "," + theColumnIndex + "]";
		}
	}

	public static class RowWrapper<R, C> implements ModelCell<R, C> {
		private final ModelRow<? extends R> theRow;
		private final C theCellValue;
		private final int theColumnIndex;
		private final boolean isCellHovered;
		private final boolean isCellFocused;

		public RowWrapper(ModelRow<? extends R> row, C cellValue, int columnIndex, boolean isCellHovered, boolean isCellFocused) {
			theRow = row;
			theCellValue = cellValue;
			theColumnIndex = columnIndex;
			this.isCellHovered = isCellHovered;
			this.isCellFocused = isCellFocused;
		}

		@Override
		public R getModelValue() {
			return theRow.getModelValue();
		}

		@Override
		public int getRowIndex() {
			return theRow.getRowIndex();
		}

		@Override
		public boolean isSelected() {
			return theRow.isSelected();
		}

		@Override
		public boolean hasFocus() {
			return theRow.hasFocus();
		}

		@Override
		public boolean isRowHovered() {
			return theRow.isRowHovered();
		}

		@Override
		public boolean isExpanded() {
			return theRow.isExpanded();
		}

		@Override
		public boolean isLeaf() {
			return theRow.isLeaf();
		}

		@Override
		public String isEnabled() {
			return theRow.isEnabled();
		}

		@Override
		public C getCellValue() {
			return theCellValue;
		}

		@Override
		public int getColumnIndex() {
			return theColumnIndex;
		}

		@Override
		public boolean isCellHovered() {
			return isCellHovered;
		}

		@Override
		public boolean isCellFocused() {
			return isCellFocused;
		}

		@Override
		public ModelCell<R, C> setEnabled(String enabled) {
			theRow.setEnabled(enabled);
			return this;
		}
	}
}
