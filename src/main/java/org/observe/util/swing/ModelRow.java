package org.observe.util.swing;

import java.util.function.Supplier;

public interface ModelRow<R> {
	R getModelValue();

	int getRowIndex();

	boolean isSelected();
	boolean hasFocus();
	boolean isRowHovered();
	boolean isExpanded();
	boolean isLeaf();

	public static class Default<M> implements ModelRow<M> {
		private final Supplier<? extends M> theModelValue;
		private final int theRowIndex;
		private final boolean isSelected;
		private final boolean isFocused;
		private final boolean isRowHovered;
		private final boolean isExpanded;
		private final boolean isLeaf;

		public Default(Supplier<? extends M> modelValue, int rowIndex, boolean selected, boolean focused, boolean rowHovered,
			boolean expanded, boolean leaf) {
			theModelValue = modelValue;
			theRowIndex = rowIndex;
			isSelected = selected;
			isFocused = focused;
			isRowHovered = rowHovered;
			isExpanded = expanded;
			isLeaf = leaf;
		}

		@Override
		public M getModelValue() {
			return theModelValue.get();
		}

		@Override
		public int getRowIndex() {
			return theRowIndex;
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
		public boolean isRowHovered() {
			return isRowHovered;
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
