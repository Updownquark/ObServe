package org.observe.util.swing;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.font.TextAttribute;
import java.text.AttributedCharacterIterator.Attribute;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.border.TitledBorder;
import javax.swing.text.JTextComponent;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;

import org.qommons.BiTuple;

/** Allows for simple chained modification of a font */
public class FontAdjuster implements Cloneable {
	private static final Map<Object, TextAttribute> STYLE_TO_TEXT;
	private static final Map<TextAttribute, Object> TEXT_TO_STYLE;
	private static final Map<BiTuple<Object, Object>, BiTuple<TextAttribute, Object>> INDIRECT_STYLE_TO_TEXT;
	private static final Map<BiTuple<TextAttribute, Object>, BiTuple<Object, Object>> INDIRECT_TEXT_TO_STYLE;

	static {
		STYLE_TO_TEXT = new HashMap<>();
		TEXT_TO_STYLE = new HashMap<>();
		INDIRECT_STYLE_TO_TEXT = new HashMap<>();
		INDIRECT_TEXT_TO_STYLE = new HashMap<>();

		addAttributeMapping(TextAttribute.BACKGROUND, StyleConstants.Background);
		addAttributeMapping(TextAttribute.FOREGROUND, StyleConstants.Foreground);
		addAttributeMapping(TextAttribute.FAMILY, StyleConstants.Family);
		addAttributeMapping(TextAttribute.SIZE, StyleConstants.Size);
		addAttributeMapping(TextAttribute.STRIKETHROUGH, StyleConstants.StrikeThrough);

		addAttributeMapping(TextAttribute.POSTURE, TextAttribute.POSTURE_OBLIQUE, StyleConstants.Italic, true);
		addAttributeMapping(TextAttribute.POSTURE, TextAttribute.POSTURE_REGULAR, StyleConstants.Italic, false);
		addAttributeMapping(TextAttribute.SUPERSCRIPT, TextAttribute.SUPERSCRIPT_SUPER, StyleConstants.Superscript, true);
		addAttributeMapping(TextAttribute.SUPERSCRIPT, TextAttribute.SUPERSCRIPT_SUB, StyleConstants.Subscript, true);
		addAttributeMapping(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON, StyleConstants.Underline, true);
		addAttributeMapping(TextAttribute.UNDERLINE, 0, StyleConstants.Underline, false);
		addAttributeMapping(TextAttribute.WEIGHT, TextAttribute.WEIGHT_BOLD, StyleConstants.Bold, true);
		addAttributeMapping(TextAttribute.WEIGHT, TextAttribute.WEIGHT_REGULAR, StyleConstants.Bold, false);
	}

	public static void addAttributeMapping(TextAttribute textAttr, Object styleAttr) {
		STYLE_TO_TEXT.put(styleAttr, textAttr);
		TEXT_TO_STYLE.put(textAttr, styleAttr);
	}

	public static void addAttributeMapping(TextAttribute textAttr, Object textValue, Object styleAttr, Object styleValue) {
		BiTuple<TextAttribute, Object> textTuple = new BiTuple<>(textAttr, textValue);
		BiTuple<Object, Object> styleTuple = new BiTuple<>(styleAttr, styleValue);
		INDIRECT_STYLE_TO_TEXT.put(styleTuple, textTuple);
		INDIRECT_TEXT_TO_STYLE.put(textTuple, styleTuple);
	}

	private MutableAttributeSet theFontAttributes;
	private Map<Attribute, Object> theFontAttributeMap;
	private Integer theVAlign;

	public FontAdjuster() {
		this(new SimpleAttributeSet());
	}

	public FontAdjuster(MutableAttributeSet fontAttributes) {
		theFontAttributes = fontAttributes;
		theFontAttributeMap = new HashMap<>();
	}

	public MutableAttributeSet getFontAttributes() {
		return theFontAttributes;
	}

	public Integer getHAlign() {
		return (Integer) theFontAttributes.getAttribute(StyleConstants.Alignment);
	}

	public Integer getVAlign() {
		return theVAlign;
	}

	public FontAdjuster configure(Consumer<? super FontAdjuster> configure) {
		configure.accept(this);
		return this;
	}

	public FontAdjuster reset() {
		// Oh my gosh it's so much work just to clear an attribute set
		if (theFontAttributes.getAttributeCount() > 0) {
			List<Object> attrs = new ArrayList<>(theFontAttributes.getAttributeCount());
			Enumeration<?> attrEnum = theFontAttributes.getAttributeNames();
			while (attrEnum.hasMoreElements())
				attrs.add(attrEnum.nextElement());
			theFontAttributes.removeAttributes(new IterEnum<>(attrs.iterator()));
		}
		theFontAttributeMap.clear();
		theVAlign = null;
		return this;
	}

	private static class IterEnum<T> implements Enumeration<T> { // Surely someone's done this before?
		private final Iterator<T> iterator;

		IterEnum(Iterator<T> iterator) {
			this.iterator = iterator;
		}

		@Override
		public boolean hasMoreElements() {
			return iterator.hasNext();
		}

		@Override
		public T nextElement() {
			return iterator.next();
		}
	}

	public FontAdjuster deriveFont(int style, float size) {
		withFontSize(size);
		withFontStyle(style);
		return this;
	}

	public FontAdjuster deriveFont(Object attr, Object value) {
		Attribute textAttr = STYLE_TO_TEXT.get(attr);
		if (textAttr != null)
			theFontAttributeMap.put(textAttr, value);
		else {
			BiTuple<TextAttribute, Object> textAttrValue = INDIRECT_STYLE_TO_TEXT.get(new BiTuple<>(attr, value));
			if (textAttrValue != null)
				theFontAttributeMap.put(textAttrValue.getValue1(), textAttrValue.getValue2());
			else if (attr instanceof Attribute)
				theFontAttributeMap.put((Attribute) attr, value);
		}

		Object styleAttr = TEXT_TO_STYLE.get(attr);
		if (styleAttr != null)
			theFontAttributes.addAttribute(styleAttr, value);
		else {
			BiTuple<Object, Object> styleAttrValue = INDIRECT_TEXT_TO_STYLE.get(new BiTuple<>(attr, value));
			if (styleAttrValue != null)
				theFontAttributes.addAttribute(styleAttrValue.getValue1(), styleAttrValue.getValue2());
			else
				theFontAttributes.addAttribute(attr, value);
		}

		return this;
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
		bold((style & Font.BOLD) != 0);
		italic((style & Font.ITALIC) != 0);
		return this;
	}

	/**
	 * @param size The point size for the label's font
	 * @return This adjuster
	 */
	public FontAdjuster withFontSize(float size) {
		return deriveFont(TextAttribute.SIZE, Math.round(size));
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
		return deriveFont(TextAttribute.WEIGHT, bold ? TextAttribute.WEIGHT_BOLD : TextAttribute.WEIGHT_REGULAR);
	}

	public FontAdjuster underline() {
		return underline(true);
	}

	public FontAdjuster underline(boolean underline) {
		return deriveFont(TextAttribute.UNDERLINE, underline);
	}

	public FontAdjuster strikethrough() {
		return strikethrough(true);
	}

	public FontAdjuster strikethrough(boolean strikethrough) {
		return deriveFont(TextAttribute.STRIKETHROUGH, strikethrough);
	}

	public FontAdjuster italic() {
		return italic(true);
	}

	public FontAdjuster italic(boolean italic) {
		return withFontSlant(italic ? TextAttribute.POSTURE_OBLIQUE : TextAttribute.POSTURE_REGULAR);
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
		return (Color) theFontAttributes.getAttribute(StyleConstants.Foreground);
	}

	/**
	 * @param foreground The font color for the label
	 * @return This holder
	 */
	public FontAdjuster withForeground(Color foreground) {
		return deriveFont(TextAttribute.FOREGROUND, foreground);
	}

	/**
	 * Changes the horizontal alignment of the component (if supported)
	 *
	 * @param align Negative for left, zero for center, positive for right
	 * @return This holder
	 */
	public FontAdjuster alignH(int align) {
		return deriveFont(StyleConstants.Alignment, //
			align < 0 ? StyleConstants.ALIGN_LEFT : (align > 0 ? StyleConstants.ALIGN_RIGHT : StyleConstants.ALIGN_CENTER));
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
		alignH(hAlign);
		theVAlign = vAlign;
		return this;
	}

	public Font adjust(Font font) {
		if (font == null || theFontAttributeMap.isEmpty())
			return font;
		return font.deriveFont(theFontAttributeMap);
	}

	public <C extends Component> C adjust(C C) {
		decorate(C);
		return C;
	}

	public Runnable decorate(Component c) {
		List<Runnable> revert = new ArrayList<>();
		Color fg = getForeground();
		if (fg != null) {
			// Color oldFG = c.getForeground();
			c.setForeground(fg);
			// revert.add(() -> c.setForeground(oldFG));
		}
		Font oldFont = c.getFont();
		Font newFont = adjust(oldFont);
		if (oldFont != newFont) {
			c.setFont(newFont);
			revert.add(() -> {
				c.setFont(oldFont);
			});
		}

		Integer hAlign = getHAlign();
		if (hAlign != null) {
			int align = hAlign;
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
		Color fg = getForeground();
		if (fg != null) {
			Color oldFG = border.getTitleColor();
			border.setTitleColor(fg);
			revert.add(() -> border.setTitleColor(oldFG));
		}
		Font oldFont = border.getTitleFont();
		Font newFont = adjust(oldFont);
		if (oldFont != newFont) {
			border.setTitleFont(newFont);
			revert.add(() -> {
				border.setTitleFont(oldFont);
			});
		}
		Integer hAlign = getHAlign();
		if (hAlign != null) {
			int oldJ = border.getTitleJustification();
			border.setTitleJustification(hAlign < 0 ? TitledBorder.LEFT : (hAlign > 0 ? TitledBorder.RIGHT : TitledBorder.CENTER));
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
	public FontAdjuster clone() {
		FontAdjuster cloned;
		try {
			cloned = (FontAdjuster) super.clone();
		} catch (CloneNotSupportedException e) {
			throw new IllegalStateException(e);
		}
		cloned.theFontAttributes = new SimpleAttributeSet(theFontAttributes);
		cloned.theFontAttributeMap = new HashMap<>(theFontAttributeMap);
		return cloned;
	}

	@Override
	public int hashCode() {
		return Objects.hash(theFontAttributeMap, theVAlign);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this)
			return true;
		else if (!(obj instanceof FontAdjuster))
			return false;
		FontAdjuster other = (FontAdjuster) obj;
		return Objects.equals(theFontAttributeMap, other.theFontAttributeMap) && theVAlign == other.theVAlign;
	}

	@Override
	public String toString() {
		return theFontAttributes.toString();
	}
}