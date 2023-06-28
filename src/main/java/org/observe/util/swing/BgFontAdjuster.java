package org.observe.util.swing;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.text.AttributedCharacterIterator.Attribute;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;

import javax.swing.JComponent;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.StyleConstants;

public class BgFontAdjuster extends FontAdjuster {
	private Color theBackground;

	public BgFontAdjuster() {
		super();
	}

	public BgFontAdjuster(MutableAttributeSet fontAttributes) {
		super(fontAttributes);
	}

	public Color getBackground() {
		return theBackground;
	}

	public BgFontAdjuster withBackground(Color bg) {
		theBackground = bg;
		if (getFontAttributes() != null)
			StyleConstants.setBackground(getFontAttributes(), bg);
		return this;
	}

	@Override
	public BgFontAdjuster reset() {
		super.reset();
		theBackground = null;
		return this;
	}

	@Override
	public BgFontAdjuster deriveFont(UnaryOperator<Font> font) {
		return (BgFontAdjuster) super.deriveFont(font);
	}

	@Override
	public BgFontAdjuster deriveFont(int style, float size) {
		return (BgFontAdjuster) super.deriveFont(style, size);
	}

	@Override
	public BgFontAdjuster deriveFont(Attribute attr, Object value) {
		return (BgFontAdjuster) super.deriveFont(attr, value);
	}

	@Override
	public BgFontAdjuster withFontStyle(int style) {
		return (BgFontAdjuster) super.withFontStyle(style);
	}

	@Override
	public BgFontAdjuster withFontSize(float size) {
		return (BgFontAdjuster) super.withFontSize(size);
	}

	@Override
	public BgFontAdjuster withSizeAndStyle(int style, float fontSize) {
		return (BgFontAdjuster) super.withSizeAndStyle(style, fontSize);
	}

	@Override
	public BgFontAdjuster bold() {
		return (BgFontAdjuster) super.bold();
	}

	@Override
	public BgFontAdjuster bold(boolean bold) {
		return (BgFontAdjuster) super.bold(bold);
	}

	@Override
	public BgFontAdjuster underline() {
		return (BgFontAdjuster) super.underline();
	}

	@Override
	public BgFontAdjuster underline(boolean underline) {
		return (BgFontAdjuster) super.underline(underline);
	}

	@Override
	public BgFontAdjuster strikethrough() {
		return (BgFontAdjuster) super.strikethrough();
	}

	@Override
	public BgFontAdjuster strikethrough(boolean strikethrough) {
		return (BgFontAdjuster) super.strikethrough(strikethrough);
	}

	@Override
	public BgFontAdjuster plain() {
		return (BgFontAdjuster) super.plain();
	}

	@Override
	public BgFontAdjuster withForeground(Color foreground) {
		return (BgFontAdjuster) super.withForeground(foreground);
	}

	@Override
	public BgFontAdjuster alignH(int align) {
		return (BgFontAdjuster) super.alignH(align);
	}

	@Override
	public BgFontAdjuster alignV(int align) {
		return (BgFontAdjuster) super.alignV(align);
	}

	@Override
	public BgFontAdjuster align(int hAlign, int vAlign) {
		return (BgFontAdjuster) super.align(hAlign, vAlign);
	}

	@Override
	public Runnable decorate(Component c) {
		List<Runnable> revert = new ArrayList<>();
		revert.add(super.decorate(c));
		if (theBackground != null) {
			boolean oldNonOpaque = c instanceof JComponent && !c.isOpaque();
			Color oldBG = c.getBackground();
			if (c instanceof JComponent)
				((JComponent) c).setOpaque(true);
			c.setBackground(theBackground);
			revert.add(() -> {
				if (!oldNonOpaque)
					((JComponent) c).setOpaque(false);
				c.setBackground(oldBG);
			});
		}
		return () -> {
			for (Runnable r : revert)
				r.run();
		};
	}

	@Override
	public int hashCode() {
		return super.hashCode() ^ Objects.hashCode(theBackground);
	}

	@Override
	public boolean equals(Object obj) {
		if (!super.equals(obj))
			return false;
		return obj instanceof BgFontAdjuster && Objects.equals(theBackground, ((BgFontAdjuster) obj).theBackground);
	}

	@Override
	public BgFontAdjuster clone() {
		return (BgFontAdjuster) super.clone();
	}
}
