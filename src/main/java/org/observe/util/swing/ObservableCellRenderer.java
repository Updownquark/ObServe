package org.observe.util.swing;

import java.awt.Color;
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
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeCellRenderer;

import org.qommons.LambdaUtils;

public interface ObservableCellRenderer<M, C> {
	public interface CellRenderContext {
		public static CellRenderContext DEFAULT = new CellRenderContext() {
			@Override
			public SortedMatchSet getEmphaticRegions() {
				return null;
			}
		};

		SortedMatchSet getEmphaticRegions();
	}

	String renderAsText(ModelCell<? extends M, ? extends C> cell);

	ObservableCellRenderer<M, C> decorate(CellDecorator<M, C> decorator);

	Component getCellRendererComponent(Component parent, ModelCell<? extends M, ? extends C> cell, CellRenderContext ctx);

	public static <M, C> ObservableCellRenderer<M, C> fromTableRenderer(TableCellRenderer renderer,
		BiFunction<Supplier<? extends M>, C, String> asText) {
		class FlatTableCellRenderer implements ObservableCellRenderer<M, C> {
			private CellDecorator<M, C> theDecorator;
			private ComponentDecorator theComponentDecorator;
			private Runnable theDecorationUndo;

			@Override
			public String renderAsText(ModelCell<? extends M, ? extends C> cell) {
				return asText.apply(cell::getModelValue, cell.getCellValue());
			}

			@Override
			public ObservableCellRenderer<M, C> decorate(CellDecorator<M, C> decorator) {
				theDecorator = decorator;
				return this;
			}

			@Override
			public Component getCellRendererComponent(Component parent, ModelCell<? extends M, ? extends C> cell, CellRenderContext ctx) {
				Component rendered = renderer.getTableCellRendererComponent(parent instanceof JTable ? (JTable) parent : null,
					cell.getCellValue(), cell.isSelected(), cell.hasFocus(), cell.getRowIndex(), cell.getColumnIndex());
				rendered = tryEmphasize(rendered, ctx);
				if (theDecorationUndo != null) {
					theDecorationUndo.run();
					theDecorationUndo = null;
				}
				if (theDecorator != null) {
					if (theComponentDecorator == null)
						theComponentDecorator = new ComponentDecorator();
					theDecorator.decorate(cell, theComponentDecorator);
					theDecorationUndo = theComponentDecorator.decorate(rendered);
				}
				return rendered;
			}
		}
		return new FlatTableCellRenderer();
	}

	public static <M, C> ObservableCellRenderer<M, C> fromTreeRenderer(TreeCellRenderer renderer,
		BiFunction<Supplier<? extends M>, C, String> asText) {
		class FlatTreeCellRenderer implements ObservableCellRenderer<M, C> {
			private CellDecorator<M, C> theDecorator;
			private ComponentDecorator theComponentDecorator;
			private Runnable theDecorationUndo;

			@Override
			public String renderAsText(ModelCell<? extends M, ? extends C> cell) {
				return asText.apply(cell::getModelValue, cell.getCellValue());
			}

			@Override
			public ObservableCellRenderer<M, C> decorate(CellDecorator<M, C> decorator) {
				theDecorator = decorator;
				return this;
			}

			@Override
			public Component getCellRendererComponent(Component parent, ModelCell<? extends M, ? extends C> cell, CellRenderContext ctx) {
				Component rendered = renderer.getTreeCellRendererComponent(parent instanceof JTree ? (JTree) parent : null,
					cell.getCellValue(), cell.isSelected(), cell.isExpanded(), cell.isLeaf(), cell.getRowIndex(), cell.hasFocus());
				rendered = tryEmphasize(rendered, ctx);
				if (theDecorationUndo != null) {
					theDecorationUndo.run();
					theDecorationUndo = null;
				}
				if (theDecorator != null) {
					if (theComponentDecorator == null)
						theComponentDecorator = new ComponentDecorator();
					theDecorator.decorate(cell, theComponentDecorator);
					theDecorationUndo = theComponentDecorator.decorate(rendered);
				}
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
		SortedMatchSet regions = ctx == null ? null : ctx.getEmphaticRegions();
		if (regions == null || regions.size() == 0)
			return text;
		StringBuilder newText = new StringBuilder("<html>");
		int end = 0;
		for (TextMatch match : regions.getDisjointMatches()) {
			if (match.start > end)
				newText.append(text, end, match.start);
			newText.append("<b>");
			newText.append(text, match.start, match.end);
			newText.append("</b>");
			end = match.end;
		}
		if (end < text.length())
			newText.append(text, end, text.length());
		return newText.toString();
	}

	public static abstract class SimpleObservableCellRenderer<M, C, R extends Component> implements ObservableCellRenderer<M, C> {
		private final R theComponent;

		public SimpleObservableCellRenderer(R component) {
			theComponent = component;
		}

		@Override
		public Component getCellRendererComponent(Component parent, ModelCell<? extends M, ? extends C> cell, CellRenderContext ctx) {
			render(theComponent, parent, cell, ctx);
			return theComponent;
		}

		protected abstract void render(R component, Component parent, ModelCell<? extends M, ? extends C> cell, CellRenderContext ctx);
	}

	public static abstract class AbstractObservableCellRenderer<M, C> implements ObservableCellRenderer<M, C> {
		private CellDecorator<M, C> theDecorator;
		private ComponentDecorator theComponentDecorator;
		private Runnable theRevert;

		@Override
		public Component getCellRendererComponent(Component parent, ModelCell<? extends M, ? extends C> cell, CellRenderContext ctx) {
			if (theRevert != null) {
				theRevert.run();
				theRevert = null;
			}
			Component c = renderCell(parent, cell, ctx);
			if (theDecorator != null) {
				if (theComponentDecorator == null)
					theComponentDecorator = new ComponentDecorator();
				else
					theComponentDecorator.reset();
				theDecorator.decorate(cell, theComponentDecorator);
				theRevert = theComponentDecorator.decorate(c);
			}
			return tryEmphasize(c, ctx);
		}

		protected abstract Component renderCell(Component parent, ModelCell<? extends M, ? extends C> cell, CellRenderContext ctx);

		@Override
		public AbstractObservableCellRenderer<M, C> decorate(CellDecorator<M, C> decorator) {
			if (theDecorator == null)
				theDecorator = decorator;
			else
				theDecorator = theDecorator.modify(decorator);
			return this;
		}
	}

	public static class DefaultObservableCellRenderer<M, C> extends AbstractObservableCellRenderer<M, C> {
		private DefaultTableCellRenderer theTableRenderer;
		private DefaultTreeCellRenderer theTreeRenderer;
		private DefaultListCellRenderer theListRenderer;
		private JLabel theLabel;

		private final Function<? super ModelCell<? extends M, ? extends C>, String> theTextRenderer;

		public DefaultObservableCellRenderer(BiFunction<? super Supplier<? extends M>, C, String> textRenderer) {
			this(cell -> textRenderer.apply((Supplier<? extends M>) cell::getModelValue, cell.getCellValue()));
		}

		public DefaultObservableCellRenderer(Function<? super ModelCell<? extends M, ? extends C>, String> textRenderer) {
			theTextRenderer = textRenderer;
		}

		@Override
		public String renderAsText(ModelCell<? extends M, ? extends C> cell) {
			return theTextRenderer.apply(cell);
		}

		@Override
		protected Component renderCell(Component parent, ModelCell<? extends M, ? extends C> cell, CellRenderContext ctx) {
			String rendered = renderAsText(cell);
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
			return c;
		}
	}

	class CheckCellRenderer<M, C> implements ObservableCellRenderer<M, C> {
		private final Predicate<? super ModelCell<? extends M, ? extends C>> theRender;
		private final JCheckBox theCheckBox;

		private Function<? super ModelCell<? extends M, ? extends C>, String> theText;
		private CellDecorator<M, C> theDecorator;
		private ComponentDecorator theComponentDecorator;
		private Runnable theRevert;

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
		public String renderAsText(ModelCell<? extends M, ? extends C> cell) {
			if (theText != null)
				return theText.apply(cell);
			return String.valueOf(cell.getCellValue());
		}

		@Override
		public CheckCellRenderer<M, C> decorate(CellDecorator<M, C> decorator) {
			if (theDecorator == null)
				theDecorator = decorator;
			else
				theDecorator = theDecorator.modify(decorator);
			return this;
		}

		@Override
		public Component getCellRendererComponent(Component parent, ModelCell<? extends M, ? extends C> cell, CellRenderContext ctx) {
			if (theRevert != null) {
				theRevert.run();
				theRevert = null;
			}
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
				theRevert = theComponentDecorator.decorate(cb);
			}
			return cb;
		}
	}

	class ButtonCellRenderer<M, C> implements ObservableCellRenderer<M, C> {
		private final Function<? super ModelCell<? extends M, ? extends C>, String> theText;
		private final JButton theButton;
		private CellDecorator<M, C> theDecorator;
		private ComponentDecorator theComponentDecorator;
		private Runnable theRevert;

		public ButtonCellRenderer(Function<? super ModelCell<? extends M, ? extends C>, String> text) {
			theText = text;
			theButton = new JButton();
		}

		@Override
		public String renderAsText(ModelCell<? extends M, ? extends C> cell) {
			return theText.apply(cell);
		}

		@Override
		public ObservableCellRenderer<M, C> decorate(CellDecorator<M, C> decorator) {
			if (theDecorator == null)
				theDecorator = decorator;
			else
				theDecorator = theDecorator.modify(decorator);
			return this;
		}

		@Override
		public Component getCellRendererComponent(Component parent, ModelCell<? extends M, ? extends C> cell, CellRenderContext ctx) {
			if (theRevert != null) {
				theRevert.run();
				theRevert = null;
			}
			JButton button = theButton;
			button.setText(theText.apply(cell));
			if (theDecorator != null) {
				if (theComponentDecorator == null)
					theComponentDecorator = new ComponentDecorator();
				else
					theComponentDecorator.reset();
				theDecorator.decorate(cell, theComponentDecorator);
				theRevert = theComponentDecorator.decorate(button);
			}
			return button;
		}
	}

	class LinkCellRenderer<M, C> extends DefaultObservableCellRenderer<M, C> {
		public LinkCellRenderer(BiFunction<? super Supplier<? extends M>, C, String> textRenderer) {
			super(textRenderer);
			decorateLink();
		}

		public LinkCellRenderer(Function<? super ModelCell<? extends M, ? extends C>, String> textRenderer) {
			super(textRenderer);
			decorateLink();
		}

		protected void decorateLink() {
			decorate((cell, deco) -> deco.withForeground(Color.blue).underline());
		}
	}

	public static <M, C> DefaultObservableCellRenderer<M, C> formatted(BiFunction<? super M, ? super C, String> format) {
		class BiFormattedCellRenderer extends DefaultObservableCellRenderer<M, C> {
			public BiFormattedCellRenderer() {
				super((m, c) -> format.apply(m.get(), c));
			}

			@Override
			public String renderAsText(ModelCell<? extends M, ? extends C> cell) {
				return format.apply(cell.getModelValue(), cell.getCellValue());
			}

			@Override
			public String toString() {
				return format.toString();
			}
		}
		return new BiFormattedCellRenderer();
	}

	public static <M, C> DefaultObservableCellRenderer<M, C> formatted(Function<? super C, String> format) {
		class BiFormattedCellRenderer extends DefaultObservableCellRenderer<M, C> {
			public BiFormattedCellRenderer() {
				super((m, c) -> format.apply(c));
			}

			@Override
			public String renderAsText(ModelCell<? extends M, ? extends C> cell) {
				return format.apply(cell.getCellValue());
			}

			@Override
			public String toString() {
				return format.toString();
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

	public static <M, C> ObservableCellRenderer<M, C> linkRenderer(Function<? super ModelCell<? extends M, ? extends C>, String> text) {
		return new LinkCellRenderer<>(text);
	}
}
