package org.observe.util.swing;

import java.awt.Component;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.ListCellRenderer;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeCellRenderer;

import org.qommons.LambdaUtils;

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

	String renderAsText(Supplier<? extends M> modelValue, C columnValue);

	ObservableCellRenderer<M, C> decorate(CellDecorator<M, C> decorator);

	Component getCellRendererComponent(Component parent, ModelCell<M, C> cell, CellRenderContext ctx);

	@Override
	default Component getListCellRendererComponent(JList<? extends C> list, C value, int index, boolean isSelected, boolean cellHasFocus) {
		return getCellRendererComponent(list,
			new ModelCell.Default<>(() -> (M) value, value, index, 0, isSelected, cellHasFocus, true, true), CellRenderContext.DEFAULT);
	}

	public static <M, C> ObservableCellRenderer<M, C> fromTableRenderer(TableCellRenderer renderer,
		BiFunction<? super Supplier<? extends M>, C, String> asText) {
		class FlatTableCellRenderer implements ObservableCellRenderer<M, C> {
			private CellDecorator<M, C> theDecorator;

			@Override
			public String renderAsText(Supplier<? extends M> modelValue, C columnValue) {
				return asText.apply(modelValue, columnValue);
			}

			@Override
			public ObservableCellRenderer<M, C> decorate(CellDecorator<M, C> decorator) {
				theDecorator = decorator;
				return this;
			}

			@Override
			public Component getCellRendererComponent(Component parent, ModelCell<M, C> cell, CellRenderContext ctx) {
				Component rendered = renderer.getTableCellRendererComponent(parent instanceof JTable ? (JTable) parent : null,
					cell.getCellValue(), cell.isSelected(), cell.hasFocus(), cell.getRowIndex(), cell.getColumnIndex());
				rendered = tryEmphasize(rendered, ctx);
				return rendered;
			}
		}
		return new FlatTableCellRenderer();
	}

	public static <M, C> ObservableCellRenderer<M, C> fromTreeRenderer(TreeCellRenderer renderer,
		BiFunction<? super Supplier<? extends M>, C, String> asText) {
		class FlatTreeCellRenderer implements ObservableCellRenderer<M, C> {
			private CellDecorator<M, C> theDecorator;

			@Override
			public String renderAsText(Supplier<? extends M> modelValue, C columnValue) {
				return asText.apply(modelValue, columnValue);
			}

			@Override
			public ObservableCellRenderer<M, C> decorate(CellDecorator<M, C> decorator) {
				theDecorator = decorator;
				return this;
			}

			@Override
			public Component getCellRendererComponent(Component parent, ModelCell<M, C> cell, CellRenderContext ctx) {
				Component rendered = renderer.getTreeCellRendererComponent(parent instanceof JTree ? (JTree) parent : null,
					cell.getCellValue(), cell.isSelected(), cell.isExpanded(), cell.isLeaf(), cell.getRowIndex(), cell.hasFocus());
				rendered = tryEmphasize(rendered, ctx);
				return rendered;
			}
		}
		return new FlatTreeCellRenderer();
	}

	public static Component tryEmphasize(Component comp, CellRenderContext ctx) {
		if (!(comp instanceof JLabel))
			return comp;
		JLabel label = (JLabel) comp;
		String text = label.getText();
		String newText = tryEmphasize(text, ctx);
		label.setText(newText);
		return comp;
	}

	public static String tryEmphasize(String text, CellRenderContext ctx) {
		if (text == null || text.isEmpty() || text.startsWith("<html>") || text.startsWith("<HTML>"))
			return text;
		int[][] regions = ctx == null ? null : ctx.getEmphaticRegions();
		if (regions == null || regions.length == 0)
			return text;
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
		return newText.toString();
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
		private final BiFunction<? super Supplier<? extends M>, C, String> theTextRenderer;

		public DefaultObservableCellRenderer(BiFunction<? super Supplier<? extends M>, C, String> textRenderer) {
			theTextRenderer = textRenderer;
		}

		@Override
		public String renderAsText(Supplier<? extends M> modelValue, C columnValue) {
			return theTextRenderer.apply(modelValue, columnValue);
		}

		@Override
		public Component getCellRendererComponent(Component parent, ModelCell<M, C> cell, CellRenderContext ctx) {
			String rendered = renderAsText(cell::getModelValue, cell.getCellValue());
			rendered = tryEmphasize(rendered, ctx);
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
				theLabel.setText(rendered);
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

		@Override
		public DefaultObservableCellRenderer<M, C> decorate(CellDecorator<M, C> decorator) {
			theDecorator = decorator;
			return this;
		}
	}

	class CheckCellRenderer<M, C> implements ObservableCellRenderer<M, C> {
		private final Predicate<? super ModelCell<? extends M, ? extends C>> theRender;
		private final JCheckBox theCheckBox;

		private Function<? super ModelCell<? extends M, ? extends C>, String> theText;
		private CellDecorator<M, C> theDecorator;
		private ComponentDecorator theComponentDecorator;

		public CheckCellRenderer(Predicate<? super ModelCell<? extends M, ? extends C>> render) {
			theRender = render;
			theCheckBox = new JCheckBox();
			theCheckBox.setHorizontalAlignment(JCheckBox.CENTER);
		}

		public CheckCellRenderer<M, C> setText(String text) {
			return withText(LambdaUtils.constantFn(text, () -> text, null));
		}

		public CheckCellRenderer<M, C> withValueText(Function<? super C, String> text) {
			return withText(cell -> text.apply(cell.getCellValue()));
		}

		public CheckCellRenderer<M, C> withText(Function<? super ModelCell<? extends M, ? extends C>, String> text) {
			theText = text;
			return this;
		}

		@Override
		public String renderAsText(Supplier<? extends M> modelValue, C columnValue) {
			if (theText != null)
				return theText.apply(new ModelCell.Default<M, C>(modelValue, columnValue, 0, 0, false, false, false, false));
			return String.valueOf(columnValue);
		}

		@Override
		public CheckCellRenderer<M, C> decorate(CellDecorator<M, C> decorator) {
			theDecorator = decorator;
			return this;
		}

		@Override
		public Component getCellRendererComponent(Component parent, ModelCell<M, C> cell, CellRenderContext ctx) {
			JCheckBox cb = theCheckBox;
			if (theText != null)
				cb.setText(theText.apply(cell));
			cb.setSelected(theRender.test(cell));
			if (theDecorator != null) {
				if (theComponentDecorator == null)
					theComponentDecorator = new ComponentDecorator();
				else
					theComponentDecorator.reset();
				theDecorator.decorate(cell, theComponentDecorator);
				cb = theComponentDecorator.decorate(cb);
			}
			return cb;
		}
	}

	class ButtonCellRenderer<M, C> implements ObservableCellRenderer<M, C> {
		private final Function<? super ModelCell<? extends M, ? extends C>, String> theText;
		private final JButton theButton;
		private CellDecorator<M, C> theDecorator;
		private ComponentDecorator theComponentDecorator;

		public ButtonCellRenderer(Function<? super ModelCell<? extends M, ? extends C>, String> text) {
			theText = text;
			theButton = new JButton();
		}

		@Override
		public String renderAsText(Supplier<? extends M> modelValue, C columnValue) {
			return theText.apply(new ModelCell.Default<M, C>(modelValue, columnValue, 0, 0, false, false, false, false));
		}

		@Override
		public ObservableCellRenderer<M, C> decorate(CellDecorator<M, C> decorator) {
			theDecorator = decorator;
			return this;
		}

		@Override
		public Component getCellRendererComponent(Component parent, ModelCell<M, C> cell, CellRenderContext ctx) {
			JButton button = theButton;
			button.setText(theText.apply(cell));
			if (theDecorator != null) {
				if (theComponentDecorator == null)
					theComponentDecorator = new ComponentDecorator();
				else
					theComponentDecorator.reset();
				theDecorator.decorate(cell, theComponentDecorator);
				button = theComponentDecorator.decorate(button);
			}
			return button;
		}
	}

	public static <M, C, R extends JLabel> DefaultObservableCellRenderer<M, C> formatted(BiFunction<? super M, ? super C, String> format) {
		class BiFormattedCellRenderer extends DefaultObservableCellRenderer<M, C> {
			public BiFormattedCellRenderer() {
				super((m, c) -> format.apply(m.get(), c));
			}

			@Override
			public String renderAsText(Supplier<? extends M> modelValue, C columnValue) {
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
			public String renderAsText(Supplier<? extends M> modelValue, C columnValue) {
				return format.apply(columnValue);
			}
		}
		return new BiFormattedCellRenderer();
	}

	public static <M, C> ObservableCellRenderer<M, C> checkRenderer(Predicate<? super ModelCell<? extends M, ? extends C>> value) {
		return new CheckCellRenderer<>(value);
	}

	public static <M, C> ObservableCellRenderer<M, C> buttonRenderer(Function<? super ModelCell<? extends M, ? extends C>, String> text) {
		return new ButtonCellRenderer<>(text);
	}
}
