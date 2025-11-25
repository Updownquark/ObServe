package org.observe.util.swing;

import java.awt.Color;
import java.awt.Component;
import java.awt.font.TextAttribute;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import javax.swing.JComponent;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.StyleConstants;

public class BgFontAdjuster extends FontAdjuster {
	private Supplier<Color> theDefaultBackground;

	public BgFontAdjuster() {
		super();
	}

	public BgFontAdjuster(MutableAttributeSet fontAttributes) {
		super(fontAttributes);
	}

	public Color getBackground() {
		return (Color) getFontAttributes().getAttribute(StyleConstants.Background);
	}

	public BgFontAdjuster withBackground(Color bg) {
		return deriveFont(TextAttribute.BACKGROUND, bg);
	}

	public BgFontAdjuster withDefaultBackground(Supplier<Color> defaultBG) {
		theDefaultBackground = defaultBG;
		return this;
	}

	@Override
	public BgFontAdjuster reset() {
		super.reset();
		theDefaultBackground = null;
		return this;
	}

	@Override
	public BgFontAdjuster deriveFont(Object attr, Object value) {
		super.deriveFont(attr, value);
		return this;
	}

	@Override
	public BgFontAdjuster withFontWeight(float weight) {
		super.withFontWeight(weight);
		return this;
	}

	@Override
	public BgFontAdjuster withFontSlant(float slant) {
		super.withFontSlant(slant);
		return this;
	}

	@Override
	public BgFontAdjuster italic() {
		super.italic();
		return this;
	}

	@Override
	public BgFontAdjuster italic(boolean italic) {
		super.italic(italic);
		return this;
	}

	@Override
	public BgFontAdjuster deriveFont(int style, float size) {
		return (BgFontAdjuster) super.deriveFont(style, size);
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
		Color bg = getBackground();
		if (bg != null) {
			boolean oldNonOpaque = c instanceof JComponent && !c.isOpaque();
			Color oldBG = c.getBackground();
			if (c instanceof JComponent)
				((JComponent) c).setOpaque(true);
			c.setBackground(bg);
			revert.add(() -> {
				if (!oldNonOpaque)
					((JComponent) c).setOpaque(false);
				if (theDefaultBackground != null)
					c.setBackground(theDefaultBackground.get());
				else
					c.setBackground(oldBG);
			});
		}
		return () -> {
			for (Runnable r : revert)
				r.run();
		};
	}

	@Override
	public BgFontAdjuster clone() {
		return (BgFontAdjuster) super.clone();
	}
}
