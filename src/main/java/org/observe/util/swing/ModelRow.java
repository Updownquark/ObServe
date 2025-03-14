package org.observe.util.swing;

import java.util.function.Function;
import java.util.function.Supplier;

public interface ModelRow<R> {
	R getModelValue();

	int getRowIndex();

	boolean isSelected();

	boolean hasFocus();

	boolean isRowHovered();

	boolean isExpanded();

	boolean isLeaf();

	String isEnabled();

	ModelRow<R> setEnabled(String enabled);

	public static class Default<M> implements ModelRow<M> {
		private final Supplier<? extends M> theModelValue;
		private final int theRowIndex;
		private final boolean isSelected;
		private final boolean isFocused;
		private final boolean isRowHovered;
		private final boolean isExpanded;
		private final boolean isLeaf;
		private String isEnabled;

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

		@Override
		public String isEnabled() {
			return isEnabled;
		}

		@Override
		public Default<M> setEnabled(String enabled) {
			isEnabled = enabled;
			return this;
		}
	}

	public static class Mapped<M1, M2> implements ModelRow<M2> {
		private final ModelRow<M1> theSource;
		private final Function<? super M1, ? extends M2> theMap;
		private M2 theModelValue;
		public Mapped(ModelRow<M1> source, Function<? super M1, ? extends M2> map) {
			theSource = source;
			theMap = map;
		}

		@Override
		public M2 getModelValue() {
			if (theModelValue == null)
				theModelValue = theMap.apply(theSource.getModelValue());
			return theModelValue;
		}

		@Override
		public int getRowIndex() {
			return theSource.getRowIndex();
		}

		@Override
		public boolean isSelected() {
			return theSource.isSelected();
		}

		@Override
		public boolean hasFocus() {
			return theSource.hasFocus();
		}

		@Override
		public boolean isRowHovered() {
			return theSource.isRowHovered();
		}

		@Override
		public boolean isExpanded() {
			return theSource.isExpanded();
		}

		@Override
		public boolean isLeaf() {
			return theSource.isLeaf();
		}

		@Override
		public String isEnabled() {
			return theSource.isEnabled();
		}

		@Override
		public ModelRow<M2> setEnabled(String enabled) {
			theSource.setEnabled(enabled);
			return this;
		}

		@Override
		public String toString() {
			return theSource.toString();
		}
	}

	public static class Bare<M> implements ModelRow<M> {
		private final M theModelValue;

		public Bare(M modelValue) {
			theModelValue = modelValue;
		}

		@Override
		public M getModelValue() {
			return theModelValue;
		}

		@Override
		public int getRowIndex() {
			return 0;
		}

		@Override
		public boolean isSelected() {
			return false;
		}

		@Override
		public boolean hasFocus() {
			return false;
		}

		@Override
		public boolean isRowHovered() {
			return false;
		}

		@Override
		public boolean isExpanded() {
			return false;
		}

		@Override
		public boolean isLeaf() {
			return false;
		}

		@Override
		public String isEnabled() {
			return null;
		}

		@Override
		public ModelRow<M> setEnabled(String enabled) {
			return this;
		}
	}
}
