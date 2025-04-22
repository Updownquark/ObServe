package org.observe.quick.swing;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.Rectangle2D;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.quick.QuickSize;
import org.observe.quick.ext.QuickBarChart;
import org.qommons.ThreadConstraint;
import org.qommons.Transaction;

public class QuickSwingBarChart<T> extends JComponent {
	private static final DecimalFormat TICK_FORMAT = new DecimalFormat("0.##");

	private final QuickBarChart<T> theChart;
	private final SettableValue<T> theCurrentBar;
	private final SettableValue<Integer> theCurrentBarIndex;
	private final List<Rectangle> theBarBounds;
	private int theHoveredBarIndex;

	public QuickSwingBarChart(QuickBarChart<T> chart) {
		theChart = chart;
		theCurrentBar = chart.getActiveValue();
		theCurrentBarIndex = chart.getActiveIndex();
		theBarBounds = new ArrayList<>();
		addMouseMotionListener(new MouseAdapter() {
			@Override
			public void mouseMoved(MouseEvent e) {
				try (Transaction t = theChart.getValues().lock(false, null)) {
					boolean found = false;
					for (int b = 0; b < theBarBounds.size() && b < theChart.getValues().size(); b++) {
						if (theBarBounds.get(b).contains(e.getPoint())) {
							found = true;
							theHoveredBarIndex = b;
							theCurrentBar.set(theChart.getValues().get(b), e);
							theCurrentBarIndex.set(theHoveredBarIndex, e);
							break;
						}
					}
					if (!found && theHoveredBarIndex >= 0) {
						theHoveredBarIndex = -1;
						theCurrentBar.set(null, e);
						theCurrentBarIndex.set(-1, e);
					}
				}
			}
		});
		Observable.onRootFinish(Observable.or(//
			chart.getValues().simpleChanges(), //
			chart.getMax().noInitChanges(), //
			chart.getPadding().noInitChanges()))//
		.safe(ThreadConstraint.EDT)//
		.act(__ -> repaint());
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		try (Transaction t = theChart.getValues().lock(false, null)) {
			theBarBounds.clear();

			Object[] values = theChart.getValues().toArray();
			QuickSize padding = theChart.getPadding().get();
			int mainDim = theChart.isVertical() ? getHeight() : getWidth();
			int crossDim = theChart.isVertical() ? getWidth() : getHeight();
			int[] barsPos = new int[values.length];
			int barWidth;
			if (values.length == 0) {
				barWidth = 0;
			} else if (padding == null) {
				// use 2/3 of the horizontal space for the bars, 1/3 for padding
				barWidth = Math.round(mainDim * 2.0f / 3 / values.length);
			} else if (padding.percent > 0.0f) {
				int totalPadding = padding.evaluate(mainDim);
				barWidth = Math.round((mainDim - totalPadding) * 1.0f / values.length);
			} else {
				barWidth = Math.round(mainDim * 1.0f / values.length) - padding.pixels;
			}
			int padPix = (mainDim - barWidth * values.length) / 2;
			for (int i = 0; i < values.length; i++) {
				barsPos[i] = padPix + i * (padPix * 2 + barWidth);
			}

			g.setFont(getFont());
			int textHeight;
			FontRenderContext fontCtx = getFontMetrics(getFont()).getFontRenderContext();
			// Need to make room for the titles at the bottom or tick labels at the top

			textHeight = (int) Math.ceil(new TextLayout("Aj", getFont(), fontCtx).getBounds().getHeight()) + 2;
			int h = crossDim - textHeight;

			double max = theChart.getMax().get();

			int index = 0;
			ObservableValue<Color> fillV = theChart.getStyle().getBarColor();
			ObservableValue<Color> outlineV = theChart.getStyle().getOutlineColor();
			ObservableValue<Integer> thicknessV = theChart.getStyle().getOutlineThickness();
			int prevThick = -1;
			for (Object value : values) {
				theCurrentBar.set((T) value, null);
				theCurrentBarIndex.set(index, null);

				double length = theChart.getBarLength().get();
				int pixLength = (int) Math.round(length / max * h);
				Rectangle barBounds;
				if (theChart.isVertical())
					barBounds = new Rectangle(0, textHeight + barsPos[index], pixLength, barWidth);
				else
					barBounds = new Rectangle(barsPos[index], h - pixLength, barWidth, pixLength);
				theBarBounds.add(barBounds);

				Color fill = fillV.get();
				Color outline = outlineV.get();
				Integer thickness = thicknessV.get();
				if (thickness == null)
					thickness = 0;
				if (fill != null) {
					g.setColor(fill);
					g.fillRect(barBounds.x, barBounds.y, barBounds.width, barBounds.height);
				}
				if (thickness > 0 && outline != null) {
					if (thickness != prevThick) {
						prevThick = thickness;
						((Graphics2D) g).setStroke(new BasicStroke(thickness));
					}
					g.setColor(outline);
					g.drawRect(barBounds.x, barBounds.y, barBounds.width, barBounds.height);
				}

				if (theChart.hasBarTitles()) {
					String title = theChart.getBarTitle().get();
					if (title != null && !title.isEmpty()) {
						g.setColor(theChart.getStyle().getFontColor().get());
						Rectangle2D bounds = new TextLayout(title, getFont(), fontCtx).getBounds();
						if (theChart.isVertical()) {
							if (bounds.getHeight() < padPix - thickness / 2 - 2) { // If it will fit, put the title above the bar
								g.drawString(title, 1, barsPos[index] - (int) bounds.getHeight() - thickness / 2 - 2);
							} else
								g.drawString(title, 1, barsPos[index] + thickness / 2 + 1);
						} else {
							g.drawString(title, barsPos[index] + (int) Math.round((barWidth - bounds.getWidth()) / 2), h + 1);
						}
					}
				}

				index++;
			}

			// Reset the variables to the hovered bar
			if (theHoveredBarIndex >= 0 && theHoveredBarIndex < theChart.getValues().size()) {
				theCurrentBar.set(theChart.getValues().get(theHoveredBarIndex), null);
				theCurrentBarIndex.set(theHoveredBarIndex, null);
			} else {
				theCurrentBar.set(null, null);
				theCurrentBarIndex.set(-1, null);
			}

			// Draw ticks
			g.setColor(Color.black);
			if (prevThick != 1)
				((Graphics2D) g).setStroke(new BasicStroke(1));
			if (theChart.isVertical()) {
				double bin = Math.pow(10, Math.round(Math.log10(max) - 1));
				if (bin * 4 < max)
					bin *= 2.5;
				double tick = 0;
				while (tick <= max) {
					int x = (int) Math.round(tick / max * getWidth());
					g.drawLine(x, 0, x, getHeight());
					String text = TICK_FORMAT.format(tick);
					Rectangle2D bounds = new TextLayout(text, getFont(), fontCtx).getBounds();
					if (x + bounds.getWidth() + 2 < getWidth())
						g.drawString(text, x + 2, 0);
					else
						g.drawString(text, getWidth() - (int) bounds.getWidth() - 1, 0);
					tick += bin;
				}
			} else {
				double bin = Math.pow(10, Math.round(Math.log10(max) - 1));
				if (bin * 6 < max)
					bin *= 2.5;
				double tick = 0;
				while (tick <= max) {
					int y = getHeight() - (int) Math.round(tick / max * getHeight());
					g.drawLine(0, y, getWidth(), y);
					String text = TICK_FORMAT.format(tick);
					g.drawString(text, 0, y);
					tick += bin;
				}
			}
		}
	}
}
