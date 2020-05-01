package org.observe.util.swing;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.util.function.Function;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.border.Border;
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
		return this;
	}

	public ComponentDecorator withBorder(Border border) {
		theBorder = border;
		return this;
	}

	public ComponentDecorator withTitledBorder(String title, Color color) {
		TitledBorder border = BorderFactory.createTitledBorder(title);
		if (color != null)
			border.setTitleColor(color);
		theBorder = border;
		return this;
	}

	public ComponentDecorator withLineBorder(Color color, int thickness, boolean rounded) {
		theBorder = BorderFactory.createLineBorder(color, thickness, rounded);
		return this;
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

	public ComponentDecorator alignH(int align) {
		theHAlign = align;
		return this;
	}

	public ComponentDecorator alignV(int align) {
		theVAlign = align;
		return this;
	}

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

	public ComponentDecorator enabled(Boolean enabled) {
		isEnabled = enabled;
		return this;
	}

	public <C extends Component> C decorate(C c) {
		if (c instanceof JComponent && theBorder != null)
			((JComponent) c).setBorder(theBorder);
		if (theBackground != null)
			c.setBackground(theBackground);
		if (theForeground != null)
			c.setForeground(theForeground);
		if (theFont != null)
			c.setFont(theFont.apply(c.getFont()));

		if (theHAlign != null) {
			int align = theHAlign;
			int swingAlign = align < 0 ? SwingConstants.LEADING : (align > 0 ? SwingConstants.TRAILING : SwingConstants.CENTER);
			if (c instanceof JLabel)
				((JLabel) c).setHorizontalAlignment(swingAlign);
			else if (c instanceof JTextField)
				((JTextField) c).setHorizontalAlignment(//
					align < 0 ? JTextField.LEADING : (align > 0 ? JTextField.TRAILING : JLabel.CENTER));
			else if (c instanceof JTextComponent)
				((JTextComponent) c).setAlignmentX(//
					align < 0 ? 0f : (align > 0 ? 1f : 0.5f));
			else if (c instanceof AbstractButton)
				((AbstractButton) c).setAlignmentY(//
					align < 0 ? 0f : (align > 0 ? 1f : 0.5f));
		}
		if (theVAlign != null) {
			int align = theVAlign;
			int swingAlign = align < 0 ? SwingConstants.LEADING : (align > 0 ? SwingConstants.TRAILING : SwingConstants.CENTER);
			if (c instanceof JLabel)
				((JLabel) c).setVerticalAlignment(swingAlign);
			else if (c instanceof JTextComponent)
				((JTextComponent) c).setAlignmentX(//
					align < 0 ? 0f : (align > 0 ? 1f : 0.5f));
			else if (c instanceof AbstractButton)
				((AbstractButton) c).setAlignmentX(//
					align < 0 ? 0f : (align > 0 ? 1f : 0.5f));
		}

		if (c instanceof JLabel)
			((JLabel) c).setIcon(theIcon);
		else if (c instanceof AbstractButton)
			((JButton) c).setIcon(theIcon);

		return c;
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
		}
		return cd;
	}
}
