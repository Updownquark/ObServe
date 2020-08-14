package org.observe.util.swing;

import java.util.function.Supplier;

public interface ModelCell<R, C> {
	R getModelValue();
	C getCellValue();

	int getRowIndex();

	int getColumnIndex();

	boolean isSelected();
	boolean hasFocus();
	boolean isExpanded();
	boolean isLeaf();

	public static class Default<M, C> implements ModelCell<M, C> {
		private final Supplier<? extends M> theModelValue;
		private final C theCellValue;
		private final int theRowIndex;
		private final int theColumnIndex;
		private final boolean isSelected;
		private final boolean isFocused;
		private final boolean isExpanded;
		private final boolean isLeaf;

		public Default(Supplier<? extends M> modelValue, C cellValue, int rowIndex, int columnIndex, boolean selected, boolean focused,
			boolean expanded, boolean leaf) {
			theModelValue = modelValue;
			theCellValue = cellValue;
			theRowIndex = rowIndex;
			theColumnIndex = columnIndex;
			isSelected = selected;
			isFocused = focused;
			isExpanded = expanded;
			isLeaf = leaf;
		}

		@Override
		public M getModelValue() {
			return theModelValue.get();
		}

		@Override
		public C getCellValue() {
			return theCellValue;
		}

		@Override
		public int getRowIndex() {
			return theRowIndex;
		}

		@Override
		public int getColumnIndex() {
			return theColumnIndex;
		}

		@Override
		public boolean isSelected() {
			return isSelected;
		}

		@Override
		public boolean hasFocus() {
			return isFocused;
		}

		@Override
		public boolean isExpanded() {
			return isExpanded;
		}

		@Override
		public boolean isLeaf() {
			return isLeaf;
		}
	}
}
