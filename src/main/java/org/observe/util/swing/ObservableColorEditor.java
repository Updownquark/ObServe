package org.observe.util.swing;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.LayoutManager;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.util.swing.MultiRangeSlider.MRSliderRenderer;
import org.qommons.Colors;
import org.qommons.Colors.HsbColor;
import org.qommons.io.Format;
import org.qommons.io.SpinnerFormat;

import net.miginfocom.swing.MigLayout;

/** A panel with many tools to help the user choose a color */
public class ObservableColorEditor extends JPanel {
	private static final Format<Color> COLOR_FORMAT = new Format<Color>() {
		@Override
		public void append(StringBuilder text, Color value) {
			if (value != null)
				text.append(Colors.toHTML(value));
		}

		@Override
		public Color parse(CharSequence text) throws ParseException {
			return Colors.parseColor(text.toString());
		}
	};

	private final SettableValue<Color> theSelectedColor;
	private final SettableValue<HsbColor> theSelectedHsbColor;
	private final ObservableValue<Boolean> isWithAlpha;
	private final ColorHexPanel theHexPanel;
	private final ColorHistoryPanel theAllHistoryPanel;
	private final ColorHistoryPanel thePersistentHistoryPanel;

	/**
	 * @param selectedValue The color value to edit
	 * @param withHistory Whether to preserve color history (enabled {@link #addPersistentHistory(Color)})
	 * @param withAlpha Whether to allow the user to edit the alpha (opacity) value as well
	 * @param until An observable that, when it fires, will release all of this editor's listeners
	 */
	public ObservableColorEditor(SettableValue<Color> selectedValue, boolean withHistory, ObservableValue<Boolean> withAlpha,
		Observable<?> until) {
		theSelectedColor = selectedValue;
		isWithAlpha = withAlpha;
		theSelectedHsbColor = SettableValue.build(Colors.HsbColor.class)
			.withValue(new Colors.HsbColor(selectedValue.get(), withAlpha.get())).build()//
			.disableWith(selectedValue.isEnabled())//
			.refresh(withAlpha.noInitChanges())//
			.filterAccept(color -> {
				if (!withAlpha.get() && color.getAlpha() < 255)
					return "This color editor does not support transparency";
				return selectedValue.isAcceptable(color.toColor());
			});
		boolean[] valueCallbackLock = new boolean[1];
		theSelectedHsbColor.noInitChanges().takeUntil(until).act(evt -> {
			if (valueCallbackLock[0])
				return;
			valueCallbackLock[0] = true;
			try {
				selectedValue.set(evt.getNewValue().toColor(), evt);
			} finally {
				valueCallbackLock[0] = false;
			}
		});
		selectedValue.noInitChanges().takeUntil(until).act(evt -> {
			Colors.HsbColor hsb = new Colors.HsbColor(evt.getNewValue(), withAlpha.get());
			if (valueCallbackLock[0] || theSelectedHsbColor.get().equals(hsb))
				return;
			valueCallbackLock[0] = true;
			try {
				theSelectedHsbColor.set(hsb, evt);
			} finally {
				valueCallbackLock[0] = false;
			}
		});

		theHexPanel = new ColorHexPanel();
		theAllHistoryPanel = new ColorHistoryPanel("Local History", 16);
		theAllHistoryPanel.setToolTipText("Colors that have been selected in this editor");
		if (withHistory) {
			thePersistentHistoryPanel = new ColorHistoryPanel("Selection History", 16);
			thePersistentHistoryPanel.selected(theSelectedHsbColor.get(), false);
			thePersistentHistoryPanel.setToolTipText("Colors that have recently been selected and confirmed");
		} else
			thePersistentHistoryPanel = null;

		SettableValue<Boolean> rgbOrHsb = SettableValue.build(boolean.class).withValue(true).build();
		SettableValue<Integer> red = theSelectedHsbColor.transformReversible(int.class, tx -> tx//
			.map(Colors.HsbColor::getRed).replaceSourceWith((r, txv) -> txv.getCurrentSource().setRed(r)));
		SettableValue<Integer> green = theSelectedHsbColor.transformReversible(int.class, tx -> tx//
			.map(Colors.HsbColor::getGreen).replaceSourceWith((g, txv) -> txv.getCurrentSource().setGreen(g)));
		SettableValue<Integer> blue = theSelectedHsbColor.transformReversible(int.class, tx -> tx//
			.map(Colors.HsbColor::getBlue).replaceSourceWith((b, txv) -> txv.getCurrentSource().setBlue(b)));
		SettableValue<Integer> alpha = theSelectedHsbColor.transformReversible(int.class, tx -> tx//
			.map(Colors.HsbColor::getAlpha).replaceSourceWith((a, txv) -> txv.getCurrentSource().setAlpha(a)));
		SettableValue<Double> redDouble = red.transformReversible(double.class, tx -> tx//
			.map(Number::doubleValue).replaceSource(d -> (int) Math.round(d), rev -> rev.allowInexactReverse(true)));
		SettableValue<Double> greenDouble = green.transformReversible(double.class, tx -> tx//
			.map(Number::doubleValue).replaceSource(d -> (int) Math.round(d), rev -> rev.allowInexactReverse(true)));
		SettableValue<Double> blueDouble = blue.transformReversible(double.class, tx -> tx//
			.map(Number::doubleValue).replaceSource(d -> (int) Math.round(d), rev -> rev.allowInexactReverse(true)));
		SettableValue<Double> alphaDouble = alpha.transformReversible(double.class, tx -> tx//
			.map(Number::doubleValue).replaceSource(d -> (int) Math.round(d), rev -> rev.allowInexactReverse(true)));

		SettableValue<Double> huePercent = theSelectedHsbColor.transformReversible(float.class, tx -> tx//
			.map(hsb -> hsb.getHue()).replaceSourceWith((h, txv) -> txv.getCurrentSource().setHue(h)))//
			.transformReversible(double.class, tx -> tx//
				.map(f -> f * 100.0).replaceSource(d -> (float) (d / 100), rev -> rev.allowInexactReverse(true)));
		SettableValue<Double> saturationPercent = theSelectedHsbColor.transformReversible(float.class, tx -> tx//
			.map(hsb -> hsb.getSaturation()).replaceSourceWith((s, txv) -> txv.getCurrentSource().setSaturation(s)))//
			.transformReversible(double.class, tx -> tx//
				.map(f -> f * 100.0).replaceSource(d -> (float) (d / 100), rev -> rev.allowInexactReverse(true)));
		SettableValue<Double> brightnessPercent = theSelectedHsbColor.transformReversible(float.class, tx -> tx//
			.map(hsb -> hsb.getBrightness()).replaceSourceWith((b, txv) -> txv.getCurrentSource().setBrightness(b)))//
			.transformReversible(double.class, tx -> tx//
				.map(f -> f * 100.0).replaceSource(d -> (float) (d / 100), rev -> rev.allowInexactReverse(true)));
		SettableValue<Double> alphaPercent = alpha.transformReversible(double.class, tx -> tx//
			.map(a -> a * 100.0 / 255.0).replaceSource(d -> (int) Math.round(d / 100.0 * 255.0), rev -> rev.allowInexactReverse(true)));

		SettableValue<String> rgbOrHsbName = rgbOrHsb.transformReversible(String.class, tx -> tx//
			.map(rgb -> rgb ? "RGB" : "HSB").withReverse(str -> "RGB".equals(str)));
		SettableValue<String> colorName = theSelectedHsbColor.transformReversible(String.class, tx -> tx//
			.map(c -> {
				return Colors.getColorName(c.toOpaqueColor());
			}).replaceSource(name -> {
				Color c = Colors.getColorByName(name);
				Colors.HsbColor prev = theSelectedHsbColor.get();
				return prev.setRGB(c.getRed(), c.getGreen(), c.getBlue());
			}, rev -> rev.allowInexactReverse(true) // Inexact reverse is needed here because there are several synonyms in Colors
				));
		SettableValue<Color> color = theSelectedHsbColor.transformReversible(Color.class, tx -> tx//
			.map(Colors.HsbColor::toColor).replaceSourceWith((c, txv) -> txv.getCurrentSource().setRGB(c.getRed(), c.getGreen(), c.getBlue())));
		ObservableValue<Boolean> hsbSelected = rgbOrHsb.map(rgb -> !rgb);
		List<MultiRangeSlider> sliders = new ArrayList<>();

		setLayout(new MigLayout());
		add(theHexPanel, "skip 1");
		JPanel sliderPanel = PanelPopulation.populateHPanel((JPanel) null, new JustifiedBoxLayout(true).mainJustified(), until)//
			.addHPanel(null, new JustifiedBoxLayout(false).mainCenter(),
				p3 -> p3.addRadioField(null, rgbOrHsbName, new String[] { "RGB", "HSB" }, //
					f -> f.withTooltip("Whether to show/edit the color's red/green/blue or hue/saturation/brightness")))//
			.addVPanel(rgbPanel -> rgbPanel.visibleWhen(rgbOrHsb)//
				.addHPanel("Red:", new JustifiedBoxLayout(false).mainJustified().crossJustified(), p3 -> p3.fill()//
					.addSlider(null, redDouble, slider -> slider.withBounds(0, 255).modifyEditor(s -> {
						s.setValidator(MultiRangeSlider.RangeValidator.ENFORCE_RANGE);
						((MRSliderRenderer.Default) s.getRenderer()).setMainSize(200).setLabeledTicks(0, 64, 128, 192, 255);
						sliders.add(s);
					}).withTooltip("The color's red component, between 0 and 255"))//
					.addTextField(null, red, SpinnerFormat.INT, f -> f.modifyEditor(tf -> tf.withColumns(3))//
						.withTooltip("The color's red component, between 0 and 255")//
						)//
					)//
				.addHPanel("Green:", new JustifiedBoxLayout(false).mainJustified().crossJustified(), p3 -> p3.fill()//
					.addSlider(null, greenDouble, slider -> slider.withBounds(0, 255).modifyEditor(s -> {
						s.setValidator(MultiRangeSlider.RangeValidator.ENFORCE_RANGE);
						((MRSliderRenderer.Default) s.getRenderer()).setMainSize(200).setLabeledTicks(0, 64, 128, 192, 255);
						sliders.add(s);
					}).withTooltip("The color's green component, between 0 and 255"))//
					.addTextField(null, green, SpinnerFormat.INT, f -> f.modifyEditor(tf -> tf.withColumns(3))//
						.withTooltip("The color's green component, between 0 and 255")//
						)//
					)//
				.addHPanel("Blue:", new JustifiedBoxLayout(false).mainJustified().crossJustified(), p3 -> p3.fill()//
					.addSlider(null, blueDouble, slider -> slider.withBounds(0, 255).modifyEditor(s -> {
						s.setValidator(MultiRangeSlider.RangeValidator.ENFORCE_RANGE);
						((MRSliderRenderer.Default) s.getRenderer()).setMainSize(200).setLabeledTicks(0, 64, 128, 192, 255);
						sliders.add(s);
					}).withTooltip("The color's blue component, between 0 and 255"))//
					.addTextField(null, blue, SpinnerFormat.INT, f -> f.modifyEditor(tf -> tf.withColumns(3))//
						.withTooltip("The color's blue component, between 0 and 255")//
						)//
					)//
				.addHPanel("Alpha:", new JustifiedBoxLayout(false).mainJustified().crossJustified(), p3 -> p3.fill().visibleWhen(withAlpha)//
					.addSlider(null, alphaDouble, slider -> slider.withBounds(0, 255).modifyEditor(s -> {
						s.setValidator(MultiRangeSlider.RangeValidator.ENFORCE_RANGE);
						((MRSliderRenderer.Default) s.getRenderer()).setMainSize(200).setLabeledTicks(0, 64, 128, 192, 255);
						sliders.add(s);
					}).withTooltip("The color's alpha, or opacity, between 0 (completely transparent) and 255 (completely opaque)"))//
					.addTextField(null, alpha, SpinnerFormat.INT, f -> f//
						.modifyEditor(tf -> tf.withColumns(3))//
						.visibleWhen(rgbOrHsb)//
						.withTooltip("The color's alpha, or opacity, between 0 (completely transparent) and 255 (completely opaque)")//
						)//
					)//
				)//
			.addVPanel(hsbPanel -> hsbPanel.visibleWhen(hsbSelected)//
				.addHPanel("Hue:", new JustifiedBoxLayout(false).mainJustified().crossJustified(), p3 -> p3.fill()//
					.addSlider(null, huePercent, slider -> slider.withBounds(0, 100).modifyEditor(s -> {
						s.setValidator(MultiRangeSlider.RangeValidator.ENFORCE_RANGE);
						((MRSliderRenderer.Default) s.getRenderer()).setMainSize(200).setLabeledTicks(0, 25, 50, 75, 100);
						sliders.add(s);
					}).withTooltip("The color's hue, on a scale of red-yellow-green-cyan-blue-magenta-red, between 0 and 100%"))//
					.addTextField(null, huePercent, SpinnerFormat.doubleFormat("0.#", 1.0), f -> f//
						.modifyEditor(tf -> tf.withColumns(4))//
						.withPostLabel("%")//
						.withTooltip("The color's hue, on a scale of red-yellow-green-cyan-blue-magenta-red, between 0 and 100%")//
						)//
					)//
				.addHPanel("Saturation:", new JustifiedBoxLayout(false).mainJustified().crossJustified(), p3 -> p3.fill()//
					.addSlider(null, saturationPercent, slider -> slider.withBounds(0, 100).modifyEditor(s -> {
						s.setValidator(MultiRangeSlider.RangeValidator.ENFORCE_RANGE);
						((MRSliderRenderer.Default) s.getRenderer()).setMainSize(200).setLabeledTicks(0, 25, 50, 75, 100);
						sliders.add(s);
					}).withTooltip("The color's saturation--how \"colored\" it is, as opposed to how \"washed out\""))//
					.addTextField(null, saturationPercent, SpinnerFormat.doubleFormat("0.#", 1.0), f -> f//
						.modifyEditor(tf -> tf.withColumns(4))//
						.withPostLabel("%")//
						.withTooltip("The color's saturation--how \"colored\" it is, as opposed to how \"washed out\"")//
						)//
					)//
				.addHPanel("Brightness:", new JustifiedBoxLayout(false).mainJustified().crossJustified(), p3 -> p3.fill()//
					.addSlider(null, brightnessPercent, slider -> slider.withBounds(0, 100).modifyEditor(s -> {
						s.setValidator(MultiRangeSlider.RangeValidator.ENFORCE_RANGE);
						((MRSliderRenderer.Default) s.getRenderer()).setMainSize(200).setLabeledTicks(0, 25, 50, 75, 100);
						sliders.add(s);
					}).withTooltip("The color's brightness, between darkest (black, 0%) and brightest (100%)"))//
					.addTextField(null, brightnessPercent, SpinnerFormat.doubleFormat("0.#", 1.0), f -> f//
						.modifyEditor(tf -> tf.withColumns(4))//
						.withPostLabel("%")//
						.withTooltip("The color's brightness, between darkest (black, 0%) and brightest (100%)")//
						)//
					)//
				.addHPanel("Opacity:", new JustifiedBoxLayout(false).mainJustified().crossJustified(),
					p3 -> p3.fill().visibleWhen(withAlpha)//
					.addSlider(null, alphaPercent, slider -> slider.withBounds(0, 100).modifyEditor(s -> {
						s.setValidator(MultiRangeSlider.RangeValidator.ENFORCE_RANGE);
						((MRSliderRenderer.Default) s.getRenderer()).setMainSize(200).setLabeledTicks(0, 25, 50, 75, 100);
						sliders.add(s);
					}).withTooltip("The color's opacity, between 0% (completely transparent) and 100% (completely opaque)"))//
					.addTextField(null, alphaPercent, SpinnerFormat.doubleFormat("0.#", 1.0), f -> f//
						.modifyEditor(tf -> tf.withColumns(4))//
						.withPostLabel("%")//
						.withTooltip("The color's opacity, between 0% (completely transparent) and 100% (completely opaque)")//
						)//
					)//
				)//
			.getContainer();
		add(sliderPanel, "aligny top, wrap");
		PanelPopulation.populateHPanel(this, (LayoutManager) null, until)//
		.addTextField("HTML:", color, COLOR_FORMAT, f -> f.fill()//
			.withTooltip("The HTML hex representation of the color"));
		add(theAllHistoryPanel, "growx, wrap");
		PanelPopulation.populateHPanel(this, (LayoutManager) null, until)//
		.addComboField("Name:", colorName, new ArrayList<>(Colors.getColorNames()),
			f -> f.fill().renderWith(ObservableCellRenderer.<String, String> formatted(n -> n).decorate((cell, deco) -> {
				Color bg = Colors.getColorByName(cell.getCellValue());
				if (bg == null)
					return;
				deco.withBackground(bg);
				if (Colors.getDarkness(bg) > 0.5f)
					deco.withForeground(Color.white);
			}))//
			.withTooltip("The HTML name of the color, if any"))//
		;
		if (thePersistentHistoryPanel != null)
			add(thePersistentHistoryPanel, "growx");

		theSelectedHsbColor.changes().takeUntil(until).act(evt -> {
			boolean editing = theHexPanel.isDragging;
			for (MultiRangeSlider slider : sliders) {
				if (editing)
					break;
				editing = slider.isAdjusting();
			}
			theAllHistoryPanel.selected(evt.getNewValue(), editing);
			if (!editing)
				theAllHistoryPanel.finalized();
		});
	}

	/** @return The color value that this editor is editing */
	public SettableValue<Color> getSelectedColor() {
		return theSelectedColor;
	}

	/** @return The {@link HsbColor} that this editor is editing */
	public SettableValue<HsbColor> getSelectedHsbColor() {
		return theSelectedHsbColor;
	}

	/** @return Whether the user is allowed to edit the alpha value in this editor */
	public ObservableValue<Boolean> isWithAlpha() {
		return isWithAlpha;
	}

	/** Clears all local history in this editor. Useful for using this editor in a modal dialog. */
	public void clearLocalHistory() {
		theAllHistoryPanel.removeAll();
	}

	/**
	 * Adds a color into the persistent history of this editor. Useful for using this editor in a modal dialog.
	 * 
	 * @param color The color to add to the history
	 */
	public void addPersistentHistory(Color color) {
		if (thePersistentHistoryPanel != null)
			thePersistentHistoryPanel.selected(new HsbColor(color, false), false);
	}

	static final double SQRT3OVER2 = Math.sqrt(3) / 2;

	private class ColorHexPanel extends JComponent {
		private final Colors.ColorHex theHexData;
		private final Image theColorHex;
		private final Image theAlphaHex;
		private final Image theShadeHex;

		boolean isDragging;

		ColorHexPanel() {
			theHexData = new Colors.ColorHex(175);
			Image[] hexes = theHexData.genColorHexImages();
			theColorHex = hexes[0];
			theShadeHex = hexes[1];
			theAlphaHex = hexes[2];

			Dimension size = new Dimension(theHexData.getWidth(), theHexData.getHeight());
			size.width += 8;
			size.height += 8;
			setMinimumSize(size);
			setPreferredSize(size);
			setMaximumSize(size);
			theSelectedHsbColor.noInitChanges().act(evt -> ObservableSwingUtils.onEQ(this::repaint));
			MouseAdapter mouseListener=new MouseAdapter() {
				@Override
				public void mouseClicked(MouseEvent e) {
					isDragging=false;
					updateColor(e.getX(), e.getY(), e);
				}

				@Override
				public void mousePressed(MouseEvent e) {
					isDragging = true;
					updateColor(e.getX(), e.getY(), e);
				}

				@Override
				public void mouseReleased(MouseEvent e) {
					isDragging=false;
					updateColor(e.getX(), e.getY(), e);
				}

				@Override
				public void mouseDragged(MouseEvent e) {
					updateColor(e.getX(), e.getY(), e);
				}

				private void updateColor(int x, int y, Object cause) {
					int rgb = theHexData.getRGB(x - 4, getHeight() - y - 4);
					if (rgb == 0)
						return;
					theSelectedHsbColor.set(theSelectedHsbColor.get()//
						.setHueAndSaturationFrom(rgb), cause);
				}
			};
			addMouseListener(mouseListener);
			addMouseMotionListener(mouseListener);
			setToolTipText("Click anywhere on the hexagon to choose a color");
		}

		@Override
		public void paint(Graphics g) {
			Graphics2D g2d = (Graphics2D) g;
			Colors.HsbColor hsb = theSelectedHsbColor.get();
			Color color = hsb.toColor();
			int alpha = color.getAlpha();
			float brightness = hsb.getBrightness();
			if (alpha < 255) {
				g2d.drawImage(theAlphaHex, 4, 4, null);
				if (alpha > 0) {
					g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha / 255.0f));
					if (brightness == 0.0f)
						g2d.drawImage(theShadeHex, 4, 4, null);
					else {
						g2d.drawImage(theColorHex, 4, 4, null);
						if (brightness < 1.0f) {
							g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (1 - brightness) * alpha / 255.0f));
							g2d.drawImage(theShadeHex, 4, 4, null);
						}
					}
				}
			} else {
				g2d.drawImage(theColorHex, 4, 4, null);
				if (brightness < 1.0f) {
					g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (1.0f - brightness)));
					g2d.drawImage(theShadeHex, 4, 4, null);
				}
			}
			g2d.setComposite(AlphaComposite.SrcOver);

			//Now draw a little circle around the selected
			Point sel = theHexData.getLocation(color);

			// A little heuristic adjustment to make selection on the corners look good
			if (sel.x < getWidth() / 4)
				sel.x++;
			else if (sel.x > getWidth() * 3 / 4)
				sel.x--;
			if (sel.y < getHeight() / 4)
				sel.y++;
			else if (sel.y > getHeight() * 3 / 4)
				sel.y--;

			g2d.setColor(hsb.toOpaqueColor());
			g2d.fillOval(sel.x + 2, sel.y + 1, 5, 5);
			if (color.getAlpha() >= 100)
				g.setColor(brightness <= 0.5f ? Color.white : Color.black);
			else if (color.getAlpha() < 35)
				g.setColor(Color.red);
			else if (color.getRed() <= color.getGreen() && color.getRed() <= color.getBlue())
				g.setColor(Color.red);
			else if (color.getGreen() <= color.getBlue())
				g.setColor(Color.green);
			else
				g.setColor(Color.blue);
			g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2d.setStroke(new BasicStroke(2));
			g2d.drawOval(sel.x + 2, sel.y + 1, 5, 5);
		}
	}

	static final DecimalFormat HSB_FORMAT = new DecimalFormat("0.00");

	private class ColorHistoryPanel extends JComponent {
		private final int theTileSize;
		private final Map<Color, ColorHistoryTile> theTilesByColor;
		private boolean isLastFinalized;

		ColorHistoryPanel(String title, int tileSize) {
			setBorder(BorderFactory.createTitledBorder(title));
			theTileSize = tileSize;

			theTilesByColor = new HashMap<>();

			int padding = 3;
			int vSize = theTileSize + getInsets().top + getInsets().bottom;
			int hMinSize = getInsets().left + getInsets().right;
			setMinimumSize(new Dimension(hMinSize, vSize));
			setPreferredSize(new Dimension(hMinSize + tileSize * 6 + padding * 5, vSize));
			setMaximumSize(new Dimension(Integer.MAX_VALUE, vSize));
			isLastFinalized = true;
		}

		void finalized() {
			isLastFinalized = true;
		}

		void selected(Colors.HsbColor hsbColor, boolean editing) {
			if (!editing)
				isLastFinalized = true;
			Color color = hsbColor.toOpaqueColor();
			if (!isLastFinalized && getComponentCount() > 0) {
				ColorHistoryTile tile = (ColorHistoryTile) getComponent(0);
				theTilesByColor.remove(tile.getBackground());
				tile.setBackground(color);
				theTilesByColor.put(color, tile);
				repaint();
			} else {
				ColorHistoryTile tile = theTilesByColor.get(color);
				if (tile != null) {
					remove(tile);
				} else {
					tile = new ColorHistoryTile(color);
					theTilesByColor.put(color, tile);
					// Drop the least recently used color
					while (getComponentCount() > 0 && getComponentCount() + 1 > getMaxTileSize()) {
						ColorHistoryTile removed = (ColorHistoryTile) getComponent(getComponentCount() - 1);
						remove(getComponentCount() - 1);
						theTilesByColor.remove(removed.getBackground());
					}
				}
				add(tile, 0);
			}
			isLastFinalized = !editing;
			revalidate();
		}

		int getMaxTileSize() {
			int tileWidth = getWidth() - getInsets().left - getInsets().right;
			int tiles = tileWidth / (theTileSize + 3);
			if (tileWidth % (theTileSize + 3) >= theTileSize)
				tiles++;
			return Math.max(0, tiles);
		}

		@Override
		public void setBounds(int x, int y, int width, int height) {
			super.setBounds(x, y, width, height);
			while (getComponentCount() > getMaxTileSize()) {
				ColorHistoryTile tile = (ColorHistoryTile) getComponent(getComponentCount() - 1);
				theTilesByColor.remove(tile.getBackground());
			}
		}

		@Override
		public void doLayout() {
			int padding = 3;
			int x = getInsets().left, y = getInsets().top;
			for (Component tile : getComponents()) {
				tile.setBounds(x, y, theTileSize, theTileSize);
				x += theTileSize + padding;
			}
		}
	}

	private class ColorHistoryTile extends JComponent {
		ColorHistoryTile(Color color) {
			setBackground(color);
			addMouseListener(new MouseAdapter() {
				@Override
				public void mouseClicked(MouseEvent e) {
					Color toSelect = getBackground();
					theSelectedHsbColor.set(theSelectedHsbColor.get().setRGB(toSelect.getRed(), toSelect.getGreen(), toSelect.getBlue()),
						e);
				}
			});
		}

		@Override
		public void setBackground(Color c) {
			super.setBackground(c);
			StringBuilder tooltip = new StringBuilder("<html>");
			int r = c.getRed(), g = c.getGreen(), b = c.getBlue();
			String html = Colors.toHTML(c);
			String bg = Colors.getDarkness(c) > 0.5 ? "white" : "black";
			tooltip.append("<font bgcolor=\"" + bg + "\" color=\"" + html + "\">").append(html).append("</font>");
			String name = Colors.getColorName(c);
			if (name != null)
				tooltip.append(" (\"").append(name).append("\")");
			tooltip.append("<br />");
			tooltip.append("<font color=\"red\">red ").append(r)//
			.append("</font>, <font color=\"green\">green ").append(g)//
			.append("</font>, <font color=\"blue\">blue ").append(b).append("</font><br />");
			float[] hsb = Color.RGBtoHSB(r, g, b, null);
			tooltip.append("hue ").append(HSB_FORMAT.format(hsb[0] * 100))//
			.append("%, saturation ").append(HSB_FORMAT.format(hsb[1] * 100))//
			.append("%, brightness ").append(HSB_FORMAT.format(hsb[2] * 100)).append("%");
			setToolTipText(tooltip.toString());
		}

		@Override
		public void paint(Graphics g) {
			if (theSelectedHsbColor.get().getAlpha() < 255) {
				// Draw a tiled background so the amount of transparency is visible
				int bwTileSize = 4;
				for (int y = 0, sq = 0; y < getHeight(); y += bwTileSize, sq++) {
					for (int x = 0; x < getWidth(); x += bwTileSize, sq++) {
						g.setColor(sq % 2 == 0 ? Color.white : Color.black);
						g.fillRect(x, y, bwTileSize, bwTileSize);
					}
				}
				g.setColor(theSelectedHsbColor.get().applyAlpha(getBackground()));
			} else
				g.setColor(getBackground());
			g.fillRect(0, 0, getWidth(), getHeight());
			g.setColor(Color.black);
			g.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
		}
	}
}
