package org.observe.util.swing;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.font.TextAttribute;
import java.awt.image.BufferedImage;
import java.text.AttributedCharacterIterator.Attribute;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.border.Border;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import javax.swing.text.JTextComponent;

public class ComponentDecorator {
	private Border theBorder;
	private Color theBackground;
	private Color theForeground;
	private Function<Font, Font> theFont;
	private Integer theHAlign;
	private Integer theVAlign;
	private Icon theIcon;
	private Boolean isEnabled;
	private Cursor theCursor;

	private boolean isUsingImage;
	private BufferedImage theImage;

	public Border getBorder() {
		return theBorder;
	}

	public Color getBackground() {
		return theBackground;
	}

	public Color getForeground() {
		return theForeground;
	}

	public Function<Font, Font> getFont() {
		return theFont;
	}

	public ComponentDecorator reset() {
		theBorder = null;
		theBackground = theForeground = null;
		theFont = null;
		theHAlign = theVAlign = null;
		theIcon = null;
		isEnabled = null;
		isUsingImage = false;
		return this;
	}

	public ComponentDecorator withBorder(Border border) {
		theBorder = border;
		return this;
	}

	public ComponentDecorator withTitledBorder(String title, Color color) {
		TitledBorder border;
		if (theBorder instanceof TitledBorder) {
			border = (TitledBorder) theBorder;
			border.setTitle(title);
		} else
			border = BorderFactory.createTitledBorder(title);
		if (color != null)
			border.setTitleColor(color);
		theBorder = border;
		return this;
	}

	public ComponentDecorator withLineBorder(Color color, int thickness, boolean rounded) {
		ModifiableLineBorder border;
		if (theBorder instanceof ModifiableLineBorder) {
			border = (ModifiableLineBorder) theBorder;
			border.set(color, thickness, rounded);
		} else
			border = new ModifiableLineBorder(color, thickness, rounded);
		theBorder = border;
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

	public ComponentDecorator withBackground(Color bg) {
		theBackground = bg;
		return this;
	}

	public ComponentDecorator withForeground(Color fg) {
		theForeground = fg;
		return this;
	}

	public ComponentDecorator deriveFont(Function<Font, Font> font) {
		if (theFont == null)
			theFont = font;
		else if (font != null) {
			Function<Font, Font> oldFont = theFont;
			theFont = f -> font.apply(oldFont.apply(f));
		}
		return this;
	}

	public ComponentDecorator deriveFont(int style, float size) {
		return deriveFont(font -> font.deriveFont(style, size));
	}

	public ComponentDecorator deriveFont(Attribute attr, Object value) {
		Map<Attribute, Object> attrs = new HashMap<>(1);
		attrs.put(attr, value);
		return deriveFont(font -> font.deriveFont(attrs));
	}

	public ComponentDecorator withFontStyle(int style) {
		return deriveFont(font -> font.deriveFont(style));
	}

	public ComponentDecorator withFontSize(float size) {
		return deriveFont(font -> font.deriveFont(size));
	}

	public ComponentDecorator bold() {
		return bold(true);
	}

	public ComponentDecorator bold(boolean bold) {
		return withFontStyle(bold ? Font.BOLD : Font.PLAIN);
	}

	public ComponentDecorator underline() {
		return underline(true);
	}

	public ComponentDecorator underline(boolean underline) {
		return deriveFont(TextAttribute.UNDERLINE, underline ? TextAttribute.UNDERLINE_ON : -1);
	}

	public ComponentDecorator strikethrough() {
		return strikethrough(true);
	}

	public ComponentDecorator strikethrough(boolean strikethrough) {
		return deriveFont(TextAttribute.STRIKETHROUGH, strikethrough ? TextAttribute.STRIKETHROUGH_ON : false);
	}

	/**
	 * @param align -1 for left, 0 for center, 1 for right
	 * @return This decorator
	 */
	public ComponentDecorator alignH(int align) {
		theHAlign = align;
		return this;
	}

	/**
	 * @param align -1 for top, 0 for center, 1 for bottom
	 * @return This decorator
	 */
	public ComponentDecorator alignV(int align) {
		theVAlign = align;
		return this;
	}

	/**
	 * @param hAlign -1 for left, 0 for center, 1 for right
	 * @param vAlign -1 for top, 0 for center, 1 for bottom
	 * @return This decorator
	 */
	public ComponentDecorator align(int hAlign, int vAlign) {
		theHAlign = hAlign;
		theVAlign = vAlign;
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

	public <C extends Component> Runnable decorate(C c) {
		List<Runnable> revert = new ArrayList<>();
		if (c instanceof JComponent && theBorder != null) {
			Border oldBorder = ((JComponent) c).getBorder();
			((JComponent) c).setBorder(theBorder);
			revert.add(() -> ((JComponent) c).setBorder(oldBorder));
		}
		if (theBackground != null) {
			Color oldBG = c.getBackground();
			c.setBackground(theBackground);
			revert.add(() -> c.setBackground(oldBG));
		}
		if (theForeground != null) {
			Color oldFG = c.getForeground();
			c.setForeground(theForeground);
			revert.add(() -> c.setForeground(oldFG));
		}
		if (theFont != null) {
			Font oldFont = c.getFont();
			c.setFont(theFont.apply(c.getFont()));
			revert.add(() -> c.setFont(oldFont));
		}

		if (theHAlign != null) {
			int align = theHAlign;
			int swingAlign = align < 0 ? SwingConstants.LEADING : (align > 0 ? SwingConstants.TRAILING : SwingConstants.CENTER);
			if (c instanceof JLabel) {
				int oldAlign = ((JLabel) c).getHorizontalAlignment();
				((JLabel) c).setHorizontalAlignment(swingAlign);
				revert.add(() -> ((JLabel) c).setHorizontalAlignment(oldAlign));
			} else if (c instanceof JTextField) {
				int oldAlign = ((JTextField) c).getHorizontalAlignment();
				((JTextField) c).setHorizontalAlignment(//
					align < 0 ? JTextField.LEADING : (align > 0 ? JTextField.TRAILING : JLabel.CENTER));
				revert.add(() -> ((JTextField) c).setHorizontalAlignment(oldAlign));
			} else if (c instanceof JTextComponent) {
				float oldAlign = ((JTextComponent) c).getAlignmentX();
				((JTextComponent) c).setAlignmentX(//
					align < 0 ? 0f : (align > 0 ? 1f : 0.5f));
				revert.add(() -> ((JTextComponent) c).setAlignmentX(oldAlign));
			} else if (c instanceof AbstractButton) {
				float oldAlign = ((AbstractButton) c).getAlignmentX();
				((AbstractButton) c).setAlignmentY(//
					align < 0 ? 0f : (align > 0 ? 1f : 0.5f));
				revert.add(() -> ((AbstractButton) c).setAlignmentX(oldAlign));
			}
		}
		if (theVAlign != null) {
			int align = theVAlign;
			int swingAlign = align < 0 ? SwingConstants.LEADING : (align > 0 ? SwingConstants.TRAILING : SwingConstants.CENTER);
			if (c instanceof JLabel) {
				int oldAlign = ((JLabel) c).getVerticalAlignment();
				((JLabel) c).setVerticalAlignment(swingAlign);
				revert.add(() -> ((JLabel) c).setVerticalAlignment(oldAlign));
			} else if (c instanceof JTextComponent) {
				float oldAlign = ((JTextComponent) c).getAlignmentY();
				((JTextComponent) c).setAlignmentX(//
					align < 0 ? 0f : (align > 0 ? 1f : 0.5f));
				revert.add(() -> ((JTextComponent) c).setAlignmentY(oldAlign));
			} else if (c instanceof AbstractButton) {
				float oldAlign = ((AbstractButton) c).getAlignmentY();
				((AbstractButton) c).setAlignmentX(//
					align < 0 ? 0f : (align > 0 ? 1f : 0.5f));
				revert.add(() -> ((AbstractButton) c).setAlignmentY(oldAlign));
			}
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
			Icon oldIcon = ((JButton) c).getIcon();
			if (theIcon != null)
				((JButton) c).setIcon(theIcon);
			else if (isUsingImage)
				((JButton) c).setIcon(new ImageIcon(theImage));
			else
				((JButton) c).setIcon(null);
			revert.add(() -> ((JButton) c).setIcon(oldIcon));
		}

		if (theCursor != null) {
			Cursor oldCursor = c.getCursor();
			c.setCursor(theCursor);
			revert.add(() -> c.setCursor(oldCursor));
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
