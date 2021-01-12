package org.observe.util.swing;

import java.util.function.Supplier;

public interface ModelCell<R, C> extends ModelRow<R> {
	C getCellValue();

	int getColumnIndex();

	public static class Default<M, C> extends ModelRow.Default<M> implements ModelCell<M, C> {
		private final C theCellValue;
		private final int theColumnIndex;

		public Default(Supplier<? extends M> modelValue, C cellValue, int rowIndex, int columnIndex, boolean selected, boolean focused,
			boolean expanded, boolean leaf) {
			super(modelValue, rowIndex, selected, focused, expanded, leaf);
			theCellValue = cellValue;
			theColumnIndex = columnIndex;
		}

		@Override
		public C getCellValue() {
			return theCellValue;
		}

		@Override
		public int getColumnIndex() {
			return theColumnIndex;
		}
	}
}
