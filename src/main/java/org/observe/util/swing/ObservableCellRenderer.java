package org.observe.util.swing;

import java.awt.Component;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeCellRenderer;

public interface ObservableCellRenderer<M, C> {
	public interface CellRenderContext {
		public static CellRenderContext DEFAULT = new CellRenderContext() {
			@Override
			public int[][] getEmphaticRegions() {
				return null;
			}
		};

		int[][] getEmphaticRegions();
	}

	String renderAsText(Supplier<M> modelValue, C columnValue);

	Component getCellRendererComponent(Component parent, Supplier<M> modelValue, C columnValue, boolean selected, boolean expanded,
		boolean leaf, boolean hasFocus, int row, int column, CellRenderContext ctx);

	public static <M, C> ObservableCellRenderer<M, C> fromTableRenderer(TableCellRenderer renderer,
		BiFunction<Supplier<M>, C, String> asText) {
		class FlatTableCellRenderer implements ObservableCellRenderer<M, C> {
			@Override
			public String renderAsText(Supplier<M> modelValue, C columnValue) {
				return asText.apply(modelValue, columnValue);
			}

			@Override
			public Component getCellRendererComponent(Component parent, Supplier<M> modelValue, C columnValue, boolean selected,
				boolean expanded, boolean leaf, boolean hasFocus, int row, int column, CellRenderContext ctx) {
				return tryEmphasize(//
					renderer.getTableCellRendererComponent(parent instanceof JTable ? (JTable) parent : null, columnValue, selected,
						hasFocus, row, column),
					ctx);
			}
		}
		return new FlatTableCellRenderer();
	}

	public static <M, C> ObservableCellRenderer<M, C> fromTreeRenderer(TreeCellRenderer renderer,
		BiFunction<Supplier<M>, C, String> asText) {
		class FlatTreeCellRenderer implements ObservableCellRenderer<M, C> {
			@Override
			public String renderAsText(Supplier<M> modelValue, C columnValue) {
				return asText.apply(modelValue, columnValue);
			}

			@Override
			public Component getCellRendererComponent(Component parent, Supplier<M> modelValue, C columnValue, boolean selected,
				boolean expanded, boolean leaf, boolean hasFocus, int row, int column, CellRenderContext ctx) {
				return tryEmphasize(//
					renderer.getTreeCellRendererComponent(parent instanceof JTree ? (JTree) parent : null, columnValue, selected, expanded,
						leaf, row, hasFocus),
					ctx);
			}
		}
		return new FlatTreeCellRenderer();
	}

	public static Component tryEmphasize(Component comp, CellRenderContext ctx) {
		if (!(comp instanceof JLabel))
			return comp;
		JLabel label = (JLabel) comp;
		String text = label.getText();
		if (text.startsWith("<hml>") || text.startsWith("<HTML>"))
			return comp;
		int[][] regions = ctx.getEmphaticRegions();
		if (regions == null || regions.length == 0)
			return comp;
		StringBuilder newText = new StringBuilder("<html>");
		boolean emphasized = false;
		int start = regions[0][0];
		if (start > 0)
			newText.append(text, 0, start);
		for (int c = start; c < text.length(); c++) {
			boolean newEmph = false;
			for (int[] region : regions) {
				if (c >= region[0] && c < region[1]) {
					newEmph = true;
					break;
				}
			}
			if (newEmph && !emphasized)
				newText.append("<b>");
			else if (!newEmph && emphasized)
				newText.append("</b>");
			emphasized = newEmph;
			newText.append(text.charAt(c));
		}
		if (emphasized)
			newText.append("</b>");
		newText.append("</html>");

		label.setText(newText.toString());
		return comp;
	}

	public static abstract class SimpleObservableCellRenderer<M, C, R extends Component> implements ObservableCellRenderer<M, C> {
		private final R theComponent;

		public SimpleObservableCellRenderer(R component) {
			theComponent = component;
		}

		@Override
		public Component getCellRendererComponent(Component parent, Supplier<M> modelValue, C columnValue, boolean selected,
			boolean expanded, boolean leaf, boolean hasFocus, int row, int column, CellRenderContext ctx) {
			render(theComponent, parent, modelValue, columnValue, selected, expanded, leaf, hasFocus, row, column, ctx);
			return theComponent;
		}

		protected abstract void render(R component, Component parent, Supplier<M> modelValue, C columnValue, boolean selected,
			boolean expanded, boolean leaf, boolean hasFocus, int row, int column, CellRenderContext ctx);
	}

	public static class DefaultObservableCellRenderer<M, C> implements ObservableCellRenderer<M, C> {
		private DefaultTableCellRenderer theTableRenderer;
		private DefaultTreeCellRenderer theTreeRenderer;
		private DefaultListCellRenderer theListRenderer;
		private JLabel theLabel;

		private FormattedValue<M, C> theFormattedValue;
		private Consumer<? super FormattedValue<M, C>> theLabelModifier;
		private final BiFunction<Supplier<M>, C, String> theTextRenderer;

		public DefaultObservableCellRenderer(BiFunction<Supplier<M>, C, String> textRenderer) {
			theTextRenderer = textRenderer;
		}

		@Override
		public String renderAsText(Supplier<M> modelValue, C columnValue) {
			return theTextRenderer.apply(modelValue, columnValue);
		}

		@Override
		public Component getCellRendererComponent(Component parent, Supplier<M> modelValue, C columnValue, boolean selected,
			boolean expanded, boolean leaf, boolean hasFocus, int row, int column, CellRenderContext ctx) {
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
			} else if (parent instanceof JList) {
				if (theListRenderer == null)
					theListRenderer = new DefaultListCellRenderer();
				c = theListRenderer.getListCellRendererComponent((JList<? extends C>) parent, rendered, row, selected, hasFocus);
			} else {
				if (theLabel == null)
					theLabel = new JLabel();
				theLabel.setText(String.valueOf(rendered));
				c = theLabel;
			}
			if (theLabelModifier != null) {
				if (theFormattedValue == null)
					theFormattedValue = new FormattedValue<>();
				theLabelModifier
					.accept(theFormattedValue.forRender(c, modelValue, columnValue, selected, expanded, leaf, hasFocus, row, column, ctx));
			}
			return tryEmphasize(c, ctx);
		}

		protected Object getRenderValue(Supplier<M> modelValue, C columnValue, boolean selected, boolean expanded, boolean leaf,
			boolean hasFocus, int row, int column) {
			return columnValue;
		}

		public DefaultObservableCellRenderer<M, C> modify(Consumer<? super FormattedValue<M, C>> modifier) {
			if (theLabelModifier == null)
				theLabelModifier = modifier;
			else {
				theLabelModifier = label -> {
					theLabelModifier.accept(label);
					modifier.accept(label);
				};
			}
			return this;
		}
	}

	public static class FormattedValue<M, C> extends ObservableSwingUtils.FontAdjuster<Component> {
		private Supplier<M> theModelValue;
		private C theColumnValue;
		private boolean isSelected;
		private boolean isExpanded;
		private boolean isLeaf;
		private boolean hasFocus;
		private int theRow;
		private int theColumn;
		private CellRenderContext theContext;

		public FormattedValue() {
			super(null);
		}

		FormattedValue<M, C> forRender(Component lbl, Supplier<M> modelValue, C columnValue, boolean selected, boolean expanded,
			boolean leaf, boolean focused, int row, int column, CellRenderContext ctx) {
			setLabel(lbl);
			theModelValue = modelValue;
			theColumnValue = columnValue;
			isSelected = selected;
			isExpanded = expanded;
			isLeaf = leaf;
			hasFocus = focused;
			theRow = row;
			theColumn = column;
			theContext = ctx;
			return this;
		}

		public M getModelValue() {
			return theModelValue.get();
		}

		public C getColumnValue() {
			return theColumnValue;
		}

		public boolean isSelected() {
			return isSelected;
		}

		public boolean isExpanded() {
			return isExpanded;
		}

		public boolean isLeaf() {
			return isLeaf;
		}

		public boolean hasFocus() {
			return hasFocus;
		}

		public int getRow() {
			return theRow;
		}

		public int getColumn() {
			return theColumn;
		}

		public CellRenderContext getContext() {
			return theContext;
		}
	}

	public static <M, C, R extends JLabel> DefaultObservableCellRenderer<M, C> formatted(BiFunction<? super M, ? super C, String> format) {
		class BiFormattedCellRenderer extends DefaultObservableCellRenderer<M, C> {
			public BiFormattedCellRenderer() {
				super((m, c) -> format.apply(m.get(), c));
			}

			@Override
			protected Object getRenderValue(Supplier<M> modelValue, C columnValue, boolean selected, boolean expanded, boolean leaf,
				boolean hasFocus, int row, int column) {
				return format.apply(modelValue.get(), columnValue);
			}
		}
		return new BiFormattedCellRenderer();
	}

	public static <M, C, R extends JLabel> DefaultObservableCellRenderer<M, C> formatted(Function<? super C, String> format) {
		class BiFormattedCellRenderer extends DefaultObservableCellRenderer<M, C> {
			public BiFormattedCellRenderer() {
				super((m, c) -> format.apply(c));
			}

			@Override
			protected Object getRenderValue(Supplier<M> modelValue, C columnValue, boolean selected, boolean expanded, boolean leaf,
				boolean hasFocus, int row, int column) {
				return format.apply(columnValue);
			}
		}
		return new BiFormattedCellRenderer();
	}
}
