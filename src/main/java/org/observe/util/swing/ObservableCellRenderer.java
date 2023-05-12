package org.observe.util.swing;

import java.awt.Color;
import java.awt.Component;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.swing.DefaultListCellRenderer;
import javax.swing.Icon;
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

	ObservableCellRenderer<M, C> modify(Function<Component, Runnable> rendered);

	Component getCellRendererComponent(Component parent, ModelCell<? extends M, ? extends C> cell, CellRenderContext ctx);

	public static <M, C> ObservableCellRenderer<M, C> fromTableRenderer(TableCellRenderer renderer,
		BiFunction<Supplier<? extends M>, C, String> asText) {
		class FlatTableCellRenderer extends AbstractObservableCellRenderer<M, C> {
			@Override
			public String renderAsText(ModelCell<? extends M, ? extends C> cell) {
				return asText.apply(cell::getModelValue, cell.getCellValue());
			}

			@Override
			protected Component renderCell(Component parent, ModelCell<? extends M, ? extends C> cell, CellRenderContext ctx) {
				return renderer.getTableCellRendererComponent(parent instanceof JTable ? (JTable) parent : null,
					cell.getCellValue(), cell.isSelected(), cell.hasFocus(), cell.getRowIndex(), cell.getColumnIndex());
			}
		}
		return new FlatTableCellRenderer();
	}

	public static <M, C> ObservableCellRenderer<M, C> fromTreeRenderer(TreeCellRenderer renderer,
		BiFunction<Supplier<? extends M>, C, String> asText) {
		class FlatTreeCellRenderer extends AbstractObservableCellRenderer<M, C> {
			@Override
			public String renderAsText(ModelCell<? extends M, ? extends C> cell) {
				return asText.apply(cell::getModelValue, cell.getCellValue());
			}

			@Override
			protected Component renderCell(Component parent, ModelCell<? extends M, ? extends C> cell, CellRenderContext ctx) {
				return renderer.getTreeCellRendererComponent(parent instanceof JTree ? (JTree) parent : null,
					cell.getCellValue(), cell.isSelected(), cell.isExpanded(), cell.isLeaf(), cell.getRowIndex(), cell.hasFocus());
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

	public static abstract class AbstractObservableCellRenderer<M, C> implements ObservableCellRenderer<M, C> {
		private CellDecorator<M, C> theDecorator;
		private ComponentDecorator theComponentDecorator;
		private Runnable theRevert;
		private Function<Component, Runnable> theModifier;

		@Override
		public Component getCellRendererComponent(Component parent, ModelCell<? extends M, ? extends C> cell, CellRenderContext ctx) {
			if (theRevert != null) {
				theRevert.run();
				theRevert = null;
			}
			Component c = renderCell(parent, cell, ctx);
			Runnable revert = null;
			if (theDecorator != null) {
				if (theComponentDecorator == null)
					theComponentDecorator = new ComponentDecorator();
				else
					theComponentDecorator.reset();
				theDecorator.decorate(cell, theComponentDecorator);
				revert = theComponentDecorator.decorate(c);
			}
			if (theModifier != null) {
				Runnable modRevert = theModifier.apply(c);
				if (revert == null)
					revert = modRevert;
				else if (modRevert != null) {
					Runnable decoRevert = revert;
					revert = () -> {
						modRevert.run();
						decoRevert.run();
					};
				}
			}
			theRevert = revert;
			c = tryEmphasize(c, ctx);
			c.setEnabled(cell.isEnabled() == null);
			if (cell.isEnabled() != null)
				c.setEnabled(false);
			return c;
		}

		@Override
		public AbstractObservableCellRenderer<M, C> modify(Function<Component, Runnable> modifier) {
			if (theModifier == null)
				theModifier = modifier;
			else {
				Function<Component, Runnable> old = theModifier;
				theModifier = comp -> {
					Runnable oldRevert = old.apply(comp);
					Runnable newRevert = modifier.apply(comp);
					return () -> {
						if (newRevert != null)
							newRevert.run();
						if (oldRevert != null)
							oldRevert.run();
					};
				};
			}
			return this;
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
		private Function<? super ModelCell<? extends M, ? extends C>, Icon> theIcon;

		public DefaultObservableCellRenderer(BiFunction<? super Supplier<? extends M>, C, String> textRenderer) {
			this(cell -> textRenderer.apply((Supplier<? extends M>) cell::getModelValue, cell.getCellValue()));
		}

		public DefaultObservableCellRenderer(Function<? super ModelCell<? extends M, ? extends C>, String> textRenderer) {
			theTextRenderer = textRenderer;
		}

		public DefaultObservableCellRenderer<M, C> setIcon(Function<? super ModelCell<? extends M, ? extends C>, Icon> icon) {
			theIcon = icon;
			return this;
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
				if (theIcon != null)
					theTableRenderer.setIcon(theIcon.apply(cell));
			} else if (parent instanceof JTree) {
				if (theTreeRenderer == null)
					theTreeRenderer = new DefaultTreeCellRenderer();
				c = theTreeRenderer.getTreeCellRendererComponent((JTree) parent, rendered, cell.isSelected(), cell.isExpanded(),
					cell.isLeaf(), cell.getRowIndex(), cell.hasFocus());
				if (theIcon != null)
					theTreeRenderer.setIcon(theIcon.apply(cell));
			} else if (parent instanceof JList) {
				if (theListRenderer == null)
					theListRenderer = new DefaultListCellRenderer();
				c = theListRenderer.getListCellRendererComponent((JList<? extends C>) parent, rendered, cell.getRowIndex(),
					cell.isSelected(), cell.hasFocus());
				if (theIcon != null)
					theListRenderer.setIcon(theIcon.apply(cell));
			} else {
				if (theLabel == null)
					theLabel = new JLabel();
				theLabel.setText(rendered);
				if (theIcon != null)
					theLabel.setIcon(theIcon.apply(cell));
				c = theLabel;
			}
			return c;
		}
	}

	class CheckCellRenderer<M, C> extends AbstractObservableCellRenderer<M, C> {
		private final Predicate<? super ModelCell<? extends M, ? extends C>> theRender;
		private final JCheckBox theCheckBox;

		private Function<? super ModelCell<? extends M, ? extends C>, String> theText;

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
		protected Component renderCell(Component parent, ModelCell<? extends M, ? extends C> cell, CellRenderContext ctx) {
			JCheckBox cb = theCheckBox;
			if (theText != null)
				cb.setText(theText.apply(cell));
			cb.setSelected(theRender.test(cell));
			cb.setEnabled(cell.isEnabled() == null);
			return cb;
		}
	}

	public class ButtonCellRenderer<M, C> extends AbstractObservableCellRenderer<M, C> {
		private final Function<? super ModelCell<? extends M, ? extends C>, String> theText;
		private final JButton theButton;

		public ButtonCellRenderer(Function<? super ModelCell<? extends M, ? extends C>, String> text) {
			theText = text;
			theButton = new JButton();
		}

		public JButton getButton() {
			return theButton;
		}

		@Override
		public String renderAsText(ModelCell<? extends M, ? extends C> cell) {
			return theText.apply(cell);
		}

		@Override
		protected Component renderCell(Component parent, ModelCell<? extends M, ? extends C> cell, CellRenderContext ctx) {
			JButton button = theButton;
			button.setText(theText.apply(cell));
			button.setEnabled(cell.isEnabled() == null);
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
			decorate((cell, deco) -> deco.withForeground(cell.isEnabled() == null ? Color.blue : Color.gray).underline());
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

	public static <M, C> ButtonCellRenderer<M, C> buttonRenderer(Function<? super ModelCell<? extends M, ? extends C>, String> text) {
		return new ButtonCellRenderer<>(text);
	}

	public static <M, C> ObservableCellRenderer<M, C> linkRenderer(Function<? super ModelCell<? extends M, ? extends C>, String> text) {
		return new LinkCellRenderer<>(text);
	}
}
