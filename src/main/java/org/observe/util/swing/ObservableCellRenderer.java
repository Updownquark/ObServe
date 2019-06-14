package org.observe.util.swing;

import java.awt.Component;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeCellRenderer;

public interface ObservableCellRenderer<M, C> {
	Component getCellRendererComponent(Component parent, Supplier<M> modelValue, C columnValue, boolean selected, boolean expanded,
		boolean leaf, boolean hasFocus, int row, int column);

	public static <M, C> ObservableCellRenderer<M, C> fromTableRenderer(TableCellRenderer renderer) {
		class FlatTableCellRenderer implements ObservableCellRenderer<M, C> {
			@Override
			public Component getCellRendererComponent(Component parent, Supplier<M> modelValue, C columnValue, boolean selected,
				boolean expanded, boolean leaf, boolean hasFocus, int row, int column) {
				return renderer.getTableCellRendererComponent(parent instanceof JTable ? (JTable) parent : null, columnValue, selected,
					hasFocus, row, column);
			}
		}
		return new FlatTableCellRenderer();
	}

	public static <M, C> ObservableCellRenderer<M, C> fromTreeRenderer(TreeCellRenderer renderer) {
		class FlatTreeCellRenderer implements ObservableCellRenderer<M, C> {
			@Override
			public Component getCellRendererComponent(Component parent, Supplier<M> modelValue, C columnValue, boolean selected,
				boolean expanded, boolean leaf, boolean hasFocus, int row, int column) {
				return renderer.getTreeCellRendererComponent(parent instanceof JTree ? (JTree) parent : null, columnValue, selected,
					expanded, leaf, row, hasFocus);
			}
		}
		return new FlatTreeCellRenderer();
	}

	public static abstract class SimpleObservableCellRenderer<M, C, R extends Component> implements ObservableCellRenderer<M, C> {
		private final R theComponent;

		public SimpleObservableCellRenderer(R component) {
			theComponent = component;
		}

		@Override
		public Component getCellRendererComponent(Component parent, Supplier<M> modelValue, C columnValue, boolean selected,
			boolean expanded, boolean leaf, boolean hasFocus, int row, int column) {
			render(theComponent, parent, modelValue, columnValue, selected, expanded, leaf, hasFocus, row, column);
			return theComponent;
		}

		protected abstract void render(R component, Component parent, Supplier<M> modelValue, C columnValue, boolean selected,
			boolean expanded, boolean leaf, boolean hasFocus, int row, int column);
	}

	public static class DefaultObservableCellRenderer<M, C> implements ObservableCellRenderer<M, C> {
		private DefaultTableCellRenderer theTableRenderer;
		private DefaultTreeCellRenderer theTreeRenderer;
		private JLabel theLabel;

		@Override
		public Component getCellRendererComponent(Component parent, Supplier<M> modelValue, C columnValue, boolean selected,
			boolean expanded, boolean leaf, boolean hasFocus, int row, int column) {
			Object rendered = getRenderValue(modelValue, columnValue, selected, expanded, leaf, hasFocus, row, column);
			Component c;
			if (parent instanceof JTable) {
				if (theTableRenderer == null)
					theTableRenderer = new DefaultTableCellRenderer();
				c = theTableRenderer.getTableCellRendererComponent((JTable) parent, rendered, selected, hasFocus, row, column);
			} else if (parent instanceof JTree) {
				if (theTreeRenderer == null)
					theTreeRenderer = new DefaultTreeCellRenderer();
				c = theTreeRenderer.getTreeCellRendererComponent((JTree) parent, rendered, selected, expanded, leaf, row, hasFocus);
			} else {
				if (theLabel == null)
					theLabel = new JLabel();
				theLabel.setText(String.valueOf(rendered));
				c = theLabel;
			}
			return c;
		}

		protected Object getRenderValue(Supplier<M> modelValue, C columnValue, boolean selected, boolean expanded, boolean leaf,
			boolean hasFocus, int row, int column) {
			return columnValue;
		}
	}

	public static <M, C, R extends Component> ObservableCellRenderer<M, C> formatted(BiFunction<? super M, ? super C, String> format) {
		class BiFormattedCellRenderer extends DefaultObservableCellRenderer<M, C> {
			@Override
			protected Object getRenderValue(Supplier<M> modelValue, C columnValue, boolean selected, boolean expanded, boolean leaf,
				boolean hasFocus, int row, int column) {
				return format.apply(modelValue.get(), columnValue);
			}
		}
		return new BiFormattedCellRenderer();
	}

	public static <M, C, R extends Component> ObservableCellRenderer<M, C> formatted(Function<? super C, String> format) {
		class BiFormattedCellRenderer extends DefaultObservableCellRenderer<M, C> {
			@Override
			protected Object getRenderValue(Supplier<M> modelValue, C columnValue, boolean selected, boolean expanded, boolean leaf,
				boolean hasFocus, int row, int column) {
				return format.apply(columnValue);
			}
		}
		return new BiFormattedCellRenderer();
	}
}
