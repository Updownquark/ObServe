package org.observe.util.swing;

import java.awt.Component;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.ListCellRenderer;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeCellRenderer;

public interface ObservableCellRenderer<M, C> extends ListCellRenderer<C> {
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

	Component getCellRendererComponent(Component parent, ModelCell<M, C> cell, CellRenderContext ctx);

	@Override
	default Component getListCellRendererComponent(JList<? extends C> list, C value, int index, boolean isSelected, boolean cellHasFocus) {
		return getCellRendererComponent(list,
			new ModelCell.Default<>(() -> (M) value, value, index, 0, isSelected, cellHasFocus, true, true), CellRenderContext.DEFAULT);
	}

	public static <M, C> ObservableCellRenderer<M, C> fromTableRenderer(TableCellRenderer renderer,
		BiFunction<Supplier<M>, C, String> asText) {
		class FlatTableCellRenderer implements ObservableCellRenderer<M, C> {
			@Override
			public String renderAsText(Supplier<M> modelValue, C columnValue) {
				return asText.apply(modelValue, columnValue);
			}

			@Override
			public Component getCellRendererComponent(Component parent, ModelCell<M, C> cell, CellRenderContext ctx) {
				return tryEmphasize(//
					renderer.getTableCellRendererComponent(parent instanceof JTable ? (JTable) parent : null, cell.getCellValue(),
						cell.isSelected(), cell.hasFocus(), cell.getRowIndex(), cell.getColumnIndex()),
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
			public Component getCellRendererComponent(Component parent, ModelCell<M, C> cell, CellRenderContext ctx) {
				return tryEmphasize(//
					renderer.getTreeCellRendererComponent(parent instanceof JTree ? (JTree) parent : null, cell.getCellValue(),
						cell.isSelected(), cell.isExpanded(), cell.isLeaf(), cell.getRowIndex(), cell.hasFocus()),
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
		if (text == null || text.isEmpty() || text.startsWith("<html>") || text.startsWith("<HTML>"))
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
		public Component getCellRendererComponent(Component parent, ModelCell<M, C> cell, CellRenderContext ctx) {
			render(theComponent, parent, cell, ctx);
			return theComponent;
		}

		protected abstract void render(R component, Component parent, ModelCell<M, C> cell, CellRenderContext ctx);
	}

	public static class DefaultObservableCellRenderer<M, C> implements ObservableCellRenderer<M, C> {
		private DefaultTableCellRenderer theTableRenderer;
		private DefaultTreeCellRenderer theTreeRenderer;
		private DefaultListCellRenderer theListRenderer;
		private JLabel theLabel;

		private CellDecorator<M, C> theDecorator;
		private ComponentDecorator theComponentDecorator;
		private final BiFunction<Supplier<M>, C, String> theTextRenderer;

		public DefaultObservableCellRenderer(BiFunction<Supplier<M>, C, String> textRenderer) {
			theTextRenderer = textRenderer;
		}

		@Override
		public String renderAsText(Supplier<M> modelValue, C columnValue) {
			return theTextRenderer.apply(modelValue, columnValue);
		}

		@Override
		public Component getCellRendererComponent(Component parent, ModelCell<M, C> cell, CellRenderContext ctx) {
			Object rendered = getRenderValue(cell);
			Component c;
			if (parent instanceof JTable) {
				if (theTableRenderer == null)
					theTableRenderer = new DefaultTableCellRenderer();
				c = theTableRenderer.getTableCellRendererComponent((JTable) parent, rendered, cell.isSelected(), cell.hasFocus(),
					cell.getRowIndex(), cell.getColumnIndex());
			} else if (parent instanceof JTree) {
				if (theTreeRenderer == null)
					theTreeRenderer = new DefaultTreeCellRenderer();
				c = theTreeRenderer.getTreeCellRendererComponent((JTree) parent, rendered, cell.isSelected(), cell.isExpanded(),
					cell.isLeaf(), cell.getRowIndex(), cell.hasFocus());
			} else if (parent instanceof JList) {
				if (theListRenderer == null)
					theListRenderer = new DefaultListCellRenderer();
				c = theListRenderer.getListCellRendererComponent((JList<? extends C>) parent, rendered, cell.getRowIndex(),
					cell.isSelected(), cell.hasFocus());
			} else {
				if (theLabel == null)
					theLabel = new JLabel();
				theLabel.setText(renderAsText(cell::getModelValue, cell.getCellValue()));
				c = theLabel;
			}
			if (theDecorator != null) {
				if (theComponentDecorator == null)
					theComponentDecorator = new ComponentDecorator();
				else
					theComponentDecorator.reset();
				theDecorator.decorate(cell, theComponentDecorator);
				c = theComponentDecorator.decorate(c);
			}
			return tryEmphasize(c, ctx);
		}

		protected Object getRenderValue(ModelCell<M, C> cell) {
			return cell.getCellValue();
		}

		public DefaultObservableCellRenderer<M, C> decorate(CellDecorator<M, C> decorator) {
			if (theDecorator == null)
				theDecorator = decorator;
			else
				theDecorator = theDecorator.modify(decorator);
			return this;
		}
	}

	public static <M, C, R extends JLabel> DefaultObservableCellRenderer<M, C> formatted(BiFunction<? super M, ? super C, String> format) {
		class BiFormattedCellRenderer extends DefaultObservableCellRenderer<M, C> {
			public BiFormattedCellRenderer() {
				super((m, c) -> format.apply(m.get(), c));
			}

			@Override
			protected Object getRenderValue(ModelCell<M, C> cell) {
				return format.apply(cell.getModelValue(), cell.getCellValue());
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
			protected Object getRenderValue(ModelCell<M, C> cell) {
				return format.apply(cell.getCellValue());
			}
		}
		return new BiFormattedCellRenderer();
	}
}
