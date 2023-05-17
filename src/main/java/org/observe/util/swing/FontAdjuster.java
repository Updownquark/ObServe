package org.observe.util.swing;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.font.TextAttribute;
import java.text.AttributedCharacterIterator.Attribute;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.border.TitledBorder;
import javax.swing.text.JTextComponent;

/** Allows for simple chained modification of a font */
public class FontAdjuster {
	private UnaryOperator<Font> theFont;
	private Color theForeground;
	private Integer theHAlign;
	private Integer theVAlign;

	public Function<Font, Font> getFont() {
		return theFont;
	}

	public Color getColor() {
		return theForeground;
	}

	public Integer getHAlign() {
		return theHAlign;
	}

	public Integer getVAlign() {
		return theVAlign;
	}

	public FontAdjuster configure(Consumer<? super FontAdjuster> configure) {
		configure.accept(this);
		return this;
	}

	public FontAdjuster reset() {
		theFont = null;
		theForeground = null;
		theHAlign = theVAlign = null;
		return this;
	}

	public FontAdjuster deriveFont(UnaryOperator<Font> font) {
		if (theFont == null)
			theFont = font;
		else if (font != null) {
			UnaryOperator<Font> oldFont = theFont;
			theFont = f -> font.apply(oldFont.apply(f));
		}
		return this;
	}

	public FontAdjuster deriveFont(int style, float size) {
		return deriveFont(font -> font.deriveFont(style, size));
	}

	public FontAdjuster deriveFont(Attribute attr, Object value) {
		Map<Attribute, Object> attrs = Collections.singletonMap(attr, value);
		return deriveFont(font -> font.deriveFont(attrs));
	}

	public FontAdjuster withFontWeight(float weight) {
		return deriveFont(TextAttribute.WEIGHT, weight);
	}

	public FontAdjuster withFontSlant(float slant) {
		return deriveFont(TextAttribute.POSTURE, slant);
	}

	/**
	 * @param style The font {@link Font#getStyle() style} for the label
	 * @return This adjuster
	 */
	public FontAdjuster withFontStyle(int style) {
		return deriveFont(font -> font.deriveFont(style));
	}

	/**
	 * @param size The point size for the label's font
	 * @return This adjuster
	 */
	public FontAdjuster withFontSize(float size) {
		return deriveFont(font -> font.deriveFont(size));
	}

	/**
	 * @param style The font {@link Font#getStyle() style} for the label
	 * @param fontSize The point size for the label's font
	 * @return This holder
	 */
	public FontAdjuster withSizeAndStyle(int style, float fontSize) {
		return deriveFont(font -> font.deriveFont(style, fontSize));
	}

	/**
	 * Makes the font {@link Font#BOLD bold}
	 *
	 * @return This adjuster
	 */
	public FontAdjuster bold() {
		return bold(true);
	}

	/**
	 * @param bold Whether the label should be {@link Font#BOLD bold}
	 * @return This adjuster
	 */
	public FontAdjuster bold(boolean bold) {
		return withFontStyle(bold ? Font.BOLD : Font.PLAIN);
	}

	public FontAdjuster underline() {
		return underline(true);
	}

	public FontAdjuster underline(boolean underline) {
		return deriveFont(TextAttribute.UNDERLINE, underline ? TextAttribute.UNDERLINE_ON : -1);
	}

	public FontAdjuster strikethrough() {
		return strikethrough(true);
	}

	public FontAdjuster strikethrough(boolean strikethrough) {
		return deriveFont(TextAttribute.STRIKETHROUGH, strikethrough ? TextAttribute.STRIKETHROUGH_ON : false);
	}

	/**
	 * Makes the label's font {@link Font#PLAIN plain}
	 *
	 * @return This holder
	 */
	public FontAdjuster plain() {
		return withFontStyle(Font.PLAIN);
	}

	public Color getForeground() {
		return theForeground;
	}

	/**
	 * @param foreground The font color for the label
	 * @return This holder
	 */
	public FontAdjuster withForeground(Color foreground) {
		theForeground = foreground;
		return this;
	}
	/**
	 * Changes the horizontal alignment of the component (if supported)
	 *
	 * @param align Negative for left, zero for center, positive for right
	 * @return This holder
	 */
	public FontAdjuster alignH(int align) {
		theHAlign = align;
		return this;
	}

	/**
	 * Changes the horizontal alignment of the component (if supported)
	 *
	 * @param align Negative for left, zero for center, positive for right
	 * @return This holder
	 */
	public FontAdjuster alignV(int align) {
		theVAlign = align;
		return this;
	}

	/**
	 * @param hAlign -1 for left, 0 for center, 1 for right
	 * @param vAlign -1 for top, 0 for center, 1 for bottom
	 * @return This decorator
	 */
	public FontAdjuster align(int hAlign, int vAlign) {
		theHAlign = hAlign;
		theVAlign = vAlign;
		return this;
	}

	public Font adjust(Font font) {
		if (theFont == null)
			return font;
		else
			return theFont.apply(font);
	}

	public <C extends Component> C adjust(C C) {
		decorate(C);
		return C;
	}

	public Runnable decorate(Component c) {
		List<Runnable> revert = new ArrayList<>();
		if (theForeground != null) {
			Color oldFG = c.getForeground();
			c.setForeground(theForeground);
			revert.add(() -> c.setForeground(oldFG));
		}
		if (theFont != null) {
			Font oldFont = c.getFont();
			Font newFont = theFont.apply(c.getFont());
			c.setFont(newFont);
			System.out.println("Setting font " + newFont.getStyle());
			revert.add(() -> {
				System.out.println("reverting font to " + oldFont.getStyle());
				c.setFont(oldFont);
			});
		}

		if (theHAlign != null) {
			int align = theHAlign;
			if (c instanceof JLabel) {
				int oldAlign = ((JLabel) c).getHorizontalAlignment();
				((JLabel) c).setHorizontalAlignment(//
					align < 0 ? SwingConstants.LEADING : (align > 0 ? SwingConstants.TRAILING : SwingConstants.CENTER));
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
		return () -> {
			for (Runnable r : revert)
				r.run();
		};
	}

	public Runnable adjust(TitledBorder border) {
		List<Runnable> revert = new ArrayList<>();
		if (theForeground != null) {
			Color oldFG = border.getTitleColor();
			border.setTitleColor(theForeground);
			revert.add(() -> border.setTitleColor(oldFG));
		}
		if (theFont != null) {
			Font oldFont = border.getTitleFont();
			border.setTitleFont(theFont.apply(oldFont));
			revert.add(() -> border.setTitleFont(oldFont));
		}
		if (theHAlign != null) {
			int oldJ = border.getTitleJustification();
			border.setTitleJustification(theHAlign < 0 ? TitledBorder.LEFT : (theHAlign > 0 ? TitledBorder.RIGHT : TitledBorder.CENTER));
			revert.add(() -> border.setTitleJustification(oldJ));
		}
		if (theVAlign != null) {
			int oldAlign = border.getTitlePosition();
			border
			.setTitlePosition(theVAlign < 0 ? TitledBorder.TOP : (theVAlign > 0 ? TitledBorder.BOTTOM : TitledBorder.DEFAULT_POSITION));
			revert.add(() -> border.setTitlePosition(oldAlign));
		}
		return () -> {
			for (Runnable r : revert)
				r.run();
		};
	}

	@Override
	public String toString() {
		return theFont.toString();
	}
}