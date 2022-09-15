package org.observe.util.swing;

import java.util.function.Supplier;

public interface ModelCell<R, C> extends ModelRow<R> {
	C getCellValue();

	int getColumnIndex();

	boolean isCellHovered();

	public static class Default<M, C> extends ModelRow.Default<M> implements ModelCell<M, C> {
		private final C theCellValue;
		private final int theColumnIndex;
		private final boolean isCellHovered;

		public Default(Supplier<? extends M> modelValue, C cellValue, int rowIndex, int columnIndex, boolean selected, boolean focused,
			boolean rowHovered, boolean cellHovered, boolean expanded, boolean leaf) {
			super(modelValue, rowIndex, selected, focused, rowHovered, expanded, leaf);
			theCellValue = cellValue;
			theColumnIndex = columnIndex;
			isCellHovered = cellHovered;
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
		public String toString() {
			return getModelValue() + ":" + theCellValue + "@[" + getRowIndex() + "," + theColumnIndex + "]";
		}
	}
}
