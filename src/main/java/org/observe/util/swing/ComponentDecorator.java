package org.observe.util.swing;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.text.AttributedCharacterIterator.Attribute;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import javax.swing.AbstractButton;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.border.Border;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import javax.swing.text.JTextComponent;

public class ComponentDecorator extends BgFontAdjuster {
	private Border theBorder;
	private Icon theIcon;
	private Boolean isEnabled;
	private Cursor theCursor;

	private boolean isUsingImage;
	private BufferedImage theImage;

	public Border getBorder() {
		return theBorder;
	}

	@Override
	public ComponentDecorator reset() {
		super.reset();
		theBorder = null;
		theIcon = null;
		isEnabled = null;
		isUsingImage = false;
		return this;
	}

	@Override
	public ComponentDecorator deriveFont(UnaryOperator<Font> font) {
		return (ComponentDecorator) super.deriveFont(font);
	}

	@Override
	public ComponentDecorator deriveFont(int style, float size) {
		return (ComponentDecorator) super.deriveFont(style, size);
	}

	@Override
	public ComponentDecorator deriveFont(Attribute attr, Object value) {
		return (ComponentDecorator) super.deriveFont(attr, value);
	}

	@Override
	public ComponentDecorator withFontStyle(int style) {
		return (ComponentDecorator) super.withFontStyle(style);
	}

	@Override
	public ComponentDecorator withFontSize(float size) {
		return (ComponentDecorator) super.withFontSize(size);
	}

	@Override
	public ComponentDecorator withSizeAndStyle(int style, float fontSize) {
		return (ComponentDecorator) super.withSizeAndStyle(style, fontSize);
	}

	@Override
	public ComponentDecorator bold() {
		return (ComponentDecorator) super.bold();
	}

	@Override
	public ComponentDecorator bold(boolean bold) {
		return (ComponentDecorator) super.bold(bold);
	}

	@Override
	public ComponentDecorator underline() {
		return (ComponentDecorator) super.underline();
	}

	@Override
	public ComponentDecorator underline(boolean underline) {
		return (ComponentDecorator) super.underline(underline);
	}

	@Override
	public ComponentDecorator strikethrough() {
		return (ComponentDecorator) super.strikethrough();
	}

	@Override
	public ComponentDecorator strikethrough(boolean strikethrough) {
		return (ComponentDecorator) super.strikethrough(strikethrough);
	}

	@Override
	public ComponentDecorator plain() {
		return (ComponentDecorator) super.plain();
	}

	@Override
	public ComponentDecorator withForeground(Color foreground) {
		return (ComponentDecorator) super.withForeground(foreground);
	}

	@Override
	public ComponentDecorator alignH(int align) {
		return (ComponentDecorator) super.alignH(align);
	}

	@Override
	public ComponentDecorator alignV(int align) {
		return (ComponentDecorator) super.alignV(align);
	}

	@Override
	public ComponentDecorator align(int hAlign, int vAlign) {
		return (ComponentDecorator) super.align(hAlign, vAlign);
	}

	public ComponentDecorator withBorder(Border border) {
		theBorder = border;
		return this;
	}

	public ComponentDecorator withTitledBorder(String title, Color color) {
		withTitledBorder(title, color, font -> font.withForeground(color));
		return this;
	}

	public ComponentDecorator withTitledBorder(String title, Color borderColor, Consumer<FontAdjuster> font) {
		FontAdjuster adjuster;
		if (font != null) {
			adjuster = new FontAdjuster();
			font.accept(adjuster);
		} else
			adjuster = null;
		withTitledBorder(title, borderColor, adjuster);
		return this;
	}

	public Runnable withTitledBorder(String title, Color borderColor, FontAdjuster font) {
		TitledBorder b;
		if (theBorder instanceof TitledBorder) {
			b = (TitledBorder) theBorder;
			b.setTitle(title);
		} else if (theBorder instanceof ModifiableLineBorder)
			theBorder = b = new TitledBorder(theBorder, title);
		else
			theBorder = b = new TitledBorder(new ModifiableLineBorder(Color.black, 1, false), title);
		((ModifiableLineBorder) b.getBorder()).setColor(borderColor);
		if (font == null)
			return () -> {
			};
			else
				return font.adjust(b);
	}

	public ComponentDecorator withLineBorder(Color color, int thickness, boolean rounded) {
		ModifiableLineBorder border;
		if (theBorder instanceof ModifiableLineBorder) {
			border = (ModifiableLineBorder) theBorder;
			border.set(color, thickness, rounded);
		} else if (theBorder instanceof TitledBorder && ((TitledBorder) theBorder).getBorder() instanceof ModifiableLineBorder)
			border = (ModifiableLineBorder) ((TitledBorder) theBorder).getBorder();
		else
			theBorder = border = new ModifiableLineBorder(color, thickness, rounded);
		border.set(color, thickness, rounded);
		return this;
	}

	public static class ModifiableLineBorder extends LineBorder {
		public ModifiableLineBorder(Color color, int thickness, boolean roundedCorners) {
			super(color, thickness, roundedCorners);
		}

		public ModifiableLineBorder set(Color color, int thickness, boolean rounderCorners) {
			lineColor = color;
			this.thickness = thickness;
			this.roundedCorners = rounderCorners;
			return this;
		}

		public ModifiableLineBorder setColor(Color color) {
			lineColor = color;
			return this;
		}

		public ModifiableLineBorder setThickness(int thickness) {
			this.thickness = thickness;
			return this;
		}
	}

	@Override
	public ComponentDecorator withBackground(Color bg) {
		super.withBackground(bg);
		return this;
	}

	public ComponentDecorator withIcon(Icon icon) {
		theIcon = icon;
		return this;
	}

	public ComponentDecorator withIcon(String iconRef, int width, int height) {
		return withIcon(null, iconRef, width, height);
	}

	public ComponentDecorator withIcon(Class<?> clazz, String iconRef, int width, int height) {
		return withIcon(ObservableSwingUtils.getFixedIcon(clazz, iconRef, width, height));
	}

	public ComponentDecorator withImageIcon(int width, int height, Consumer<Graphics2D> image) {
		if (theImage == null || theImage.getWidth() != width || theImage.getHeight() != height)
			theImage = new BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR);
		image.accept((Graphics2D) theImage.getGraphics());
		isUsingImage = true;
		return this;
	}

	public ComponentDecorator enabled(Boolean enabled) {
		isEnabled = enabled;
		return this;
	}

	public ComponentDecorator withCursor(int cursor) {
		return withCursor(Cursor.getPredefinedCursor(cursor));
	}

	public ComponentDecorator withCursor(Cursor cursor) {
		theCursor = cursor;
		return this;
	}

	@Override
	public Runnable decorate(Component c) {
		List<Runnable> revert = new ArrayList<>();
		revert.add(super.decorate(c));
		if (c instanceof JComponent && theBorder != null) {
			Border oldBorder = ((JComponent) c).getBorder();
			((JComponent) c).setBorder(theBorder);
			revert.add(() -> ((JComponent) c).setBorder(oldBorder));
		}

		if (c instanceof JLabel) {
			Icon oldIcon = ((JLabel) c).getIcon();
			if (theIcon != null)
				((JLabel) c).setIcon(theIcon);
			else if (isUsingImage)
				((JLabel) c).setIcon(new ImageIcon(theImage));
			else
				((JLabel) c).setIcon(null);
			revert.add(() -> ((JLabel) c).setIcon(oldIcon));
		} else if (c instanceof AbstractButton) {
			Icon oldIcon = ((AbstractButton) c).getIcon();
			if (theIcon != null)
				((AbstractButton) c).setIcon(theIcon);
			else if (isUsingImage)
				((AbstractButton) c).setIcon(new ImageIcon(theImage));
			else
				((AbstractButton) c).setIcon(null);
			revert.add(() -> ((AbstractButton) c).setIcon(oldIcon));
		}

		if (theCursor != null) {
			Cursor oldCursor = c.getCursor();
			c.setCursor(theCursor);
			revert.add(() -> c.setCursor(oldCursor));
		}
		if (isEnabled != null && isEnabled.booleanValue() != c.isEnabled()) {
			c.setEnabled(isEnabled.booleanValue());
			boolean revertEnabled = !isEnabled;
			revert.add(() -> c.setEnabled(revertEnabled));
		}

		return () -> {
			for (Runnable r : revert)
				r.run();
		};
	}

	public static ComponentDecorator capture(Component c) {
		ComponentDecorator cd = new ComponentDecorator()//
			.withBorder(c instanceof JComponent ? ((JComponent) c).getBorder() : null)//
			.withBackground(c.getBackground())//
			.withForeground(c.getForeground());
		Font f = c.getFont();
		cd.deriveFont(__ -> f);

		{
			Integer swingAlign = null;
			if (c instanceof JLabel)
				swingAlign = ((JLabel) c).getHorizontalAlignment();
			else if (c instanceof JTextField)
				swingAlign = ((JTextField) c).getHorizontalAlignment();
			else if (c instanceof JTextComponent) {
				float align = ((JTextComponent) c).getAlignmentX();
				cd.alignH(align == 0 ? -1 : (align == 1 ? 1 : 0));
			}
			if (swingAlign == null) {} else if (swingAlign == SwingConstants.LEADING)
				cd.alignH(-1);
			else if (swingAlign == SwingConstants.CENTER)
				cd.alignH(0);
			else
				cd.alignH(1);

			if (c instanceof JLabel)
				swingAlign = ((JLabel) c).getVerticalAlignment();
			else if (c instanceof JTextComponent) {
				float align = ((JTextComponent) c).getAlignmentX();
				cd.alignV(align == 0 ? -1 : (align == 1 ? 1 : 0));
			}
			if (swingAlign == null) {} else if (swingAlign == SwingConstants.LEADING)
				cd.alignV(-1);
			else if (swingAlign == SwingConstants.CENTER)
				cd.alignV(0);
			else
				cd.alignV(1);

			if (c.getCursor() != Cursor.getDefaultCursor())
				cd.theCursor = c.getCursor();
		}
		return cd;
	}
}
