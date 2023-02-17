package org.observe.util.swing;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.Rectangle2D;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.DoubleFunction;
import java.util.function.Function;

import javax.swing.JFrame;
import javax.swing.JPanel;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.util.TypeTokens;
import org.qommons.Causable;
import org.qommons.Causable.CausableKey;
import org.qommons.Colors;
import org.qommons.FloatList;
import org.qommons.Transaction;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.threading.QommonsTimer;

/**
 * <p>
 * A linear slider that allows the user to visualize and modify any number of ranges intuitively. It can be rendered horizontally or
 * vertically and contains a multitude of customization points for validation and rendering. It can be used to manage simple values as well.
 * </p>
 * <p>
 * Each range is rendered (by default) as a mid point and 2 end points, and each can be dragged by the user to alter the range. If a range's
 * extent (difference between min and max) is zero, end points will not be rendered.
 * </p>
 * <p>
 * When the user selects a range's mid point or an end point by clicking on it, it may then also be modified using the keyboard's up/down
 * (for vertical) or left/right (for horizontal) arrow buttons (or corresponding arrows on the number pad):
 * <ul>
 * <li>When a mid point is selected, typing up/down/left/right will move the entire selected range up or down.</li>
 * <li>When a min/max point is selected, typing up/down/left/right will increase or decrease the selected end point of the selected
 * range.</li>
 * <li>When shift is pressed, typing up/down/left/right will increase or decrease the maximum value of the selected range, regardless of
 * which point is selected.</li>
 * <li>When control is pressed, typing up/down/left/right will increase or decrease the minimum value of the selected range, regardless of
 * which point is selected.</li>
 * <li>When both shift and control are pressed, typing up/down/left/right will increase or decrease the extent of the selected range,
 * leaving the mid point the same.</li>
 * </ul>
 */
public class MultiRangeSlider extends ConformingPanel {
	/** Represents a range of values that can be represented by a {@link MultiRangeSlider} */
	public static class Range {
		private final double theValue;
		private final double theExtent;

		private Range(double value, double extent) {
			if (Double.isNaN(value))
				throw new IllegalArgumentException("Value cannot be NaN");
			else if (Double.isInfinite(value))
				throw new IllegalArgumentException("Value cannot be infinite: " + value);
			else if (Double.isNaN(extent))
				throw new IllegalArgumentException("Extent cannot be NaN");
			else if (Double.isInfinite(extent))
				throw new IllegalArgumentException("Extent cannot be infinite: " + extent);
			else if (extent < 0)
				throw new IllegalArgumentException("Extent must be >=0");
			theValue = value;
			theExtent = extent;
		}

		/**
		 * @param value The mid-point value for the range
		 * @param extent The extent (min - max) for the range
		 * @return The new range
		 */
		public static Range forValueExtent(double value, double extent) {
			return new Range(value, extent);
		}

		/**
		 * @param min The minimum value for the range
		 * @param max The maximum value for the range
		 * @return The new range
		 */
		public static Range forMinMax(double min, double max) {
			double extent = max - min;
			double value = min + extent / 2;
			return new Range(value, extent);
		}

		/** @return The mid-point value of the range */
		public double getValue() {
			return theValue;
		}

		/** @return The extent of the range (min - max) */
		public double getExtent() {
			return theExtent;
		}

		/** @return The minimum value of the range */
		public double getMin() {
			return theValue - theExtent / 2;
		}

		/** @return The maximum value of the range */
		public double getMax() {
			return theValue + theExtent / 2;
		}

		@Override
		public int hashCode() {
			return Double.hashCode(theValue) ^ Integer.reverse(Double.hashCode(theExtent));
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;
			else if (!(obj instanceof Range))
				return false;
			return theValue == ((Range) obj).theValue && theExtent == ((Range) obj).theExtent;
		}

		@Override
		public String toString() {
			return getMin() + "..." + getMax();
		}
	}

	/**
	 * <p>
	 * Can be used to validate modifications to ranges from the user. E.g. a validator could be written to "snap" ranges to integer values.
	 * </p>
	 * A few validators are provided by default. Note that this class has no domain over changes other than those initiated by the user
	 * through the slider.
	 *
	 * @see #FREE
	 * @see #NO_OVERLAP
	 * @see #NO_OVERLAP_ENFORCE_RANGE
	 * @see #ENFORCE_RANGE
	 */
	public interface RangeValidator {
		/** A range validator that is a simple pass-through */
		public static final RangeValidator FREE = (slider, el, nv, pt) -> nv;
		/** A range validator that forbids ranges from overlapping each other. */
		public static final RangeValidator NO_OVERLAP = new NoOverlap(true, false);
		/** A range validator that forbids ranges from overlapping or from extending beyond the bounds of the slider */
		public static final RangeValidator NO_OVERLAP_ENFORCE_RANGE = new NoOverlap(true, true);
		/** A range validator that forbids ranges from extending beyond the bounds of the slider */
		public static final RangeValidator ENFORCE_RANGE = new NoOverlap(false, true);

		/**
		 * @param slider The slider that is requesting the validation
		 * @param element The collection element of the range to validate in the model collection (as it is now, before any modification)
		 * @param newValue The new value as requested by the user's interaction with the slider
		 * @param moved The {@link RangePoint} that the user moved to cause the change
		 * @return The new Range for the element
		 */
		Range validate(MultiRangeSlider slider, CollectionElement<Range> element, Range newValue, RangePoint moved);

		/** A simple but useful {@link RangeValidator} that has options for preventing range overlap */
		public static class NoOverlap implements RangeValidator {
			private final boolean isEnforcingAdjacent;
			private final boolean isEnforcingSliderRange;

			/**
			 * @param enforceAdjacent Whether to prevent ranges from overlapping
			 * @param enforcingSliderRange Whether to prevent the ranges from moving beyond the slider's bounds
			 */
			public NoOverlap(boolean enforceAdjacent, boolean enforcingSliderRange) {
				isEnforcingAdjacent = enforceAdjacent;
				isEnforcingSliderRange = enforcingSliderRange;
			}

			@Override
			public Range validate(MultiRangeSlider slider, CollectionElement<Range> element, Range newValue, RangePoint moved) {
				boolean moveUp = newValue.getValue() > element.get().getValue();
				Range adj = CollectionElement.get(slider.getRanges().getAdjacentElement(element.getElementId(), moveUp));
				if (moveUp) {
					if (adj == null) {
						if (!isEnforcingSliderRange || newValue.getMax() <= slider.getSliderRange().get().getMax())
							return newValue;
						switch (moved) {
						case max:
							return Range.forMinMax(element.get().getMin(), slider.getSliderRange().get().getMax());
						default:
							return Range.forValueExtent(slider.getSliderRange().get().getMax() - element.get().getExtent() / 2,
								element.get().getExtent());
						}
					} else if (isEnforcingAdjacent) {
						if (newValue.getMax() <= adj.getMin())
							return newValue;
						switch (moved) {
						case max:
							return Range.forMinMax(element.get().getMin(), adj.getMin());
						default:
							return Range.forValueExtent(adj.getMin() - element.get().getExtent() / 2, element.get().getExtent());
						}
					} else
						return newValue;
				} else {
					if (adj == null) {
						if (!isEnforcingSliderRange || newValue.getMin() >= slider.getSliderRange().get().getMin())
							return newValue;
						switch (moved) {
						case min:
							return Range.forMinMax(slider.getSliderRange().get().getMin(), element.get().getMax());
						default:
							return Range.forValueExtent(slider.getSliderRange().get().getMin() + element.get().getExtent() / 2,
								element.get().getExtent());
						}
					} else if (isEnforcingAdjacent) {
						if (newValue.getMin() >= adj.getMax())
							return newValue;
						switch (moved) {
						case min:
							return Range.forMinMax(adj.getMax(), element.get().getMax());
						default:
							return Range.forValueExtent(adj.getMax() + element.get().getExtent() / 2, element.get().getExtent());
						}
					} else
						return newValue;
				}
			}
		}

		/** A range validator that may only allow the min/max ends of ranges to exist on certain pre-determined values */
		public static class Default extends NoOverlap {
			private final FloatList theSnaps;

			/**
			 * @param enforceAdjacent Whether to prevent ranges from overlapping
			 * @param enforcingSliderRange Whether to prevent the ranges from moving beyond the slider's bounds
			 */
			public Default(boolean enforceAdjacent, boolean enforcingSliderRange) {
				super(enforceAdjacent, enforcingSliderRange);
				theSnaps = new FloatList(true, true);
			}

			/**
			 * @param snaps The locations that the min/max ends of ranges may exist on
			 * @return This validator
			 */
			public Default snapTo(double... snaps) {
				theSnaps.clear();
				for (double snap : snaps)
					theSnaps.add((float) snap);
				return this;
			}

			/**
			 * Clears the snaps from this validator so that ranges are not thus constrained
			 *
			 * @return This validator
			 */
			public Default clearSnaps() {
				theSnaps.clear();
				return this;
			}

			@Override
			public Range validate(MultiRangeSlider slider, CollectionElement<Range> element, Range newValue, RangePoint moved) {
				if (!theSnaps.isEmpty()) {
					switch (moved) {
					case min:
						int minSnap = getSnapFor(newValue.getMin());
						double minValue = theSnaps.get(minSnap);
						if (element.get().getMin() == minValue)
							return element.get();
						newValue = Range.forMinMax(minValue, element.get().getMax());
						break;
					case mid:
						minSnap = getSnapFor(newValue.getMin());
						minValue = theSnaps.get(minSnap);
						int maxSnap = getSnapFor(newValue.getMax());
						double maxValue = theSnaps.get(maxSnap);
						if (element.get().getMin() == minValue && element.get().getMax() == maxValue)
							return element.get();
						newValue = Range.forMinMax(minValue, maxValue);
						break;
					case max:
						maxSnap = getSnapFor(newValue.getMax());
						maxValue = theSnaps.get(maxSnap);
						if (element.get().getMax() == maxValue)
							return element.get();
						newValue = Range.forMinMax(element.get().getMin(), maxValue);
						break;
					}
				}
				return super.validate(slider, element, newValue, moved);
			}

			/**
			 * @param value The min/max end of a range to test
			 * @return The index in this validator's {@link #snapTo(double...) snaps} that the end should be snapped to
			 */
			public int getSnapFor(double value) {
				int snap = theSnaps.indexFor((float) value);
				if (snap == 0)
					return 0;
				else if (snap == theSnaps.size())
					return snap - 1;
				else if ((value - theSnaps.get(snap - 1)) <= (theSnaps.get(snap) - value))
					return snap - 1;
				else
					return snap;
			}
		}
	}

	/**
	 * A renderer for the slider as a whole. An instance of this interface would be responsible, for example, for rendering the ticks in the
	 * slider
	 */
	public interface MRSliderRenderer {
		/**
		 * @param slider The slider to render for
		 * @return A component to use for rendering the slider as well as determining its bounds (via {@link Component#getPreferredSize()})
		 */
		Component render(MultiRangeSlider slider);

		/**
		 * @param slider The slider to render for
		 * @return The position (perpendicular to the slider's {@link MultiRangeSlider#isVertical() orientation}) where the center of the
		 *         rendered ranges should lie.
		 */
		int getCenter(MultiRangeSlider slider);

		/** A default {@link MRSliderRenderer} implementation that renders a slider line and up to 4 ticks and their values */
		public static class Default extends JPanel implements MRSliderRenderer {
			private static final NumberFormat VALUE_FORMAT = new DecimalFormat("#,##0.##");

			private static final int DEFAULT_MAIN_SIZE = 500;
			private static final int DEFAULT_CROSS_SIZE = 20;
			private static final int DEFAULT_LABELED_TICK_WIDTH = 8;
			private static final int DEFAULT_SIMPLE_TICK_WIDTH = 4;

			private MultiRangeSlider theSlider;

			private int theCrossSize;
			private int theMainSize;
			private int theLabeledTickWidth;
			private int theSimpleTickWidth;
			private double theSimpleTickSpacing;

			private double theMinValue;
			private double theMaxValue;
			private final FloatList theLabeledTicks;
			private boolean areTicksSet;
			private DoubleFunction<String> theValueRenderer;

			/** Creates the renderer */
			public Default() {
				theLabeledTicks = new FloatList(10);
				setMainSize(DEFAULT_MAIN_SIZE);
				setCrossSize(DEFAULT_CROSS_SIZE);
				setLabeledTickWidth(DEFAULT_LABELED_TICK_WIDTH);
				setSimpleTickWidth(DEFAULT_SIMPLE_TICK_WIDTH);
				theValueRenderer = VALUE_FORMAT::format;
			}

			/**
			 * @return The main-dimension length of the rendered slider (the height of a vertical slider or the width of a horizontal one)
			 */
			public int getMainSize() {
				return theMainSize;
			}

			/**
			 * @param mainSize The height (for a vertical slider) or width(for a horizontal slider) that the slider should be
			 * @return This renderer
			 */
			public Default setMainSize(int mainSize) {
				theMainSize = mainSize;
				computeSizes();
				return this;
			}

			/**
			 * @return The cross-dimension length of the rendered slider (the width of a vertical slider or the height of a horizontal one)
			 */
			public int getCrossSize() {
				return theCrossSize;
			}

			/**
			 * @param crossSize The width (for a vertical slider) or height (for a horizontal slider) that the slider should be, in addition
			 *        to what the tick labels require.
			 * @return This renderer
			 */
			public Default setCrossSize(int crossSize) {
				theCrossSize = crossSize;
				computeSizes();
				return this;
			}

			/**
			 * Overrides the tick locations for this renderer. If this is not called, tick locations will be auto-populated.
			 *
			 * @param ticks The ticks to render
			 * @return This renderer
			 */
			public Default setLabeledTicks(double... ticks) {
				theLabeledTicks.clear();
				for (double tick : ticks)
					theLabeledTicks.add((float) tick);
				areTicksSet = true;
				return this;
			}

			/**
			 * Undoes {@link #setLabeledTicks(double...)}, causing this renderer to auto-compute tick locations
			 *
			 * @return This renderer
			 */
			public Default autoComputeTicks() {
				areTicksSet = false;
				return this;
			}

			/** @return The size of the labeled tick marks rendered on the slider */
			public int getLabeledTickWidth() {
				return theLabeledTickWidth;
			}

			/**
			 * @param tickWidth The width of labeled tick markings on the slider. A width of 1 will not show ticks but leave labels, a width
			 *        of zero will not render labels either
			 * @return This renderer
			 */
			public Default setLabeledTickWidth(int tickWidth) {
				theLabeledTickWidth = tickWidth;
				return this;
			}

			/** @return The size of non-labeled tick marks rendered on the slider */
			public int getSimpleTickWidth() {
				return theSimpleTickWidth;
			}

			/**
			 * @param tickWidth The width of non-labeled tick markings on the slider. Set to zero to not render simple ticks.
			 * @return This renderer
			 */
			public Default setSimpleTickWidth(int tickWidth) {
				theSimpleTickWidth = tickWidth;
				return this;
			}

			/**
			 * @return The value spacing between simple tick markings. These are offset from either the first labeled tick mark (if present)
			 *         or the minimum of the slider's range
			 */
			public double getSimpleTickSpacing() {
				return theSimpleTickSpacing;
			}

			/**
			 * @param simpleTickSpacing The value spacing between simple tick markings. These are offset from either the first labeled tick
			 *        mark (if present) or the minimum of the slider's range
			 * @return This renderer
			 */
			public Default setSimpleTickSpacing(double simpleTickSpacing) {
				theSimpleTickSpacing = simpleTickSpacing;
				return this;
			}

			@Override
			public Component render(MultiRangeSlider slider) {
				theSlider = slider;
				computeTicks();
				return this;
			}

			@Override
			public int getCenter(MultiRangeSlider slider) {
				if (slider.isVertical())
					return slider.getWidth() - theCrossSize / 2;
				else
					return theCrossSize / 2;
			}

			@Override
			public void setFont(Font font) {
				super.setFont(font);
				computeSizes();
			}

			private void computeTicks() {
				if (theLabeledTickWidth == 0 || areTicksSet)
					return;
				double minValue = theSlider.getSliderRange().get().getMin(), maxValue = theSlider.getSliderRange().get().getMax();
				if (theMinValue != minValue || theMaxValue != maxValue) {
					double extent = maxValue - minValue;
					if (extent == 0) {
						return;
					} else {
						double bin = Math.pow(10, Math.round(Math.log10(extent) - 1));
						if (bin * 4 < extent)
							bin *= 2.5;
						double tick = bin * (int) (minValue / bin);
						if (tick < minValue)
							tick += bin;
						theLabeledTicks.clear();
						while (tick <= maxValue) {
							theLabeledTicks.add((float) tick);
							tick += bin;
						}
					}
					theMinValue = minValue;
					theMaxValue = maxValue;
					computeSizes();
				}
			}

			private void computeSizes() {
				if (theSlider == null)
					return;
				FontRenderContext fontCtx = getFontMetrics(getFont()).getFontRenderContext();
				if (theSlider.isVertical()) {
					int maxW = 0;
					int sumH = 0;
					if (theLabeledTickWidth > 0) {
						for (double tick : theLabeledTicks) {
							String text = theValueRenderer.apply(tick);
							Rectangle2D bounds = new TextLayout(text, getFont(), fontCtx).getBounds();
							if (bounds.getWidth() > maxW)
								maxW = (int) Math.ceil(bounds.getWidth());
							sumH += (int) Math.ceil(bounds.getHeight());
						}
					}
					setMinimumSize(new Dimension(maxW + theCrossSize, sumH));
					setPreferredSize(new Dimension(maxW + theCrossSize, Math.max(sumH, theMainSize)));
					setMaximumSize(new Dimension(maxW + theCrossSize, Integer.MAX_VALUE));
				} else {
					int sumW = 0;
					int maxH = 0;
					if (theLabeledTickWidth > 0) {
						for (double tick : theLabeledTicks) {
							String text = theValueRenderer.apply(tick);
							Rectangle2D bounds = new TextLayout(text, getFont(), fontCtx).getBounds();
							if (bounds.getHeight() > maxH)
								maxH = (int) Math.ceil(bounds.getHeight());
							sumW += (int) Math.ceil(bounds.getWidth());
						}
					}
					setMinimumSize(new Dimension(sumW, maxH + theCrossSize));
					setPreferredSize(new Dimension(Math.max(sumW, theMainSize), maxH + theCrossSize));
					setMaximumSize(new Dimension(Integer.MAX_VALUE, maxH + theCrossSize));
				}
			}

			@Override
			public void paint(Graphics g) {
				if (theSlider == null)
					return;
				g.setColor(getBackground());
				g.fillRect(0, 0, getWidth(), getHeight());
				g.setFont(getFont());
				g.setColor(getForeground());
				if (theSlider.isVertical()) {
					g.drawLine(getWidth() - theCrossSize / 2, 0, getWidth() - theCrossSize / 2, getHeight());
					g.drawLine(getWidth() - theCrossSize, 0, getWidth(), 0);
					g.drawLine(getWidth() - theCrossSize, getHeight() - 1, getWidth(), getHeight() - 1);
					double min = theSlider.getSliderRange().get().getMin();
					double extent = theSlider.getSliderRange().get().getExtent();
					for (double tick : theLabeledTicks) {
						int pos = getHeight() - (int) Math.round((tick - min) * getHeight() / extent);
						g.drawLine(getWidth() - theCrossSize / 2 - theLabeledTickWidth / 2, pos,
							getWidth() - theCrossSize / 2 + theLabeledTickWidth / 2, pos);
						String text = theValueRenderer.apply(tick);
						Rectangle2D bounds = new TextLayout(text, getFont(), ((Graphics2D) g).getFontRenderContext()).getBounds();
						pos -= bounds.getHeight() / 2 + bounds.getMinY();
						if (pos - bounds.getMinY() > getHeight() - 2)
							pos = getHeight() + (int) bounds.getMinY() - 2;
						else if (pos + bounds.getMaxY() < 2)
							pos = 2 - (int) bounds.getMaxY();
						((Graphics2D) g).drawString(text, getWidth() - theCrossSize - (int) bounds.getMaxX(), pos);
					}
					if (theSimpleTickWidth > 0 && theSimpleTickSpacing > 0) {
						float simpleTick = theLabeledTicks.isEmpty() ? (float) min : theLabeledTicks.get(0);
						while (simpleTick > min + theSimpleTickSpacing)
							simpleTick -= theSimpleTickSpacing;
						float max = (float) theSlider.getSliderRange().get().getMax();
						while (simpleTick < max) {
							int pos = getHeight() - (int) Math.round((simpleTick - min) * getHeight() / extent);
							g.drawLine(getWidth() - theCrossSize / 2 - theSimpleTickWidth / 2, pos,
								getWidth() - theCrossSize / 2 + theSimpleTickWidth / 2, pos);
							simpleTick += theSimpleTickSpacing;
						}
					}
				} else {
					g.drawLine(0, theCrossSize / 2, getWidth(), theCrossSize / 2);
					g.drawLine(0, 0, 0, theCrossSize);
					g.drawLine(getWidth() - 1, 0, getWidth() - 1, theCrossSize);
					double min = theSlider.getSliderRange().get().getMin();
					double extent = theSlider.getSliderRange().get().getExtent();
					for (double tick : theLabeledTicks) {
						int pos = (int) Math.round((tick - min) * getWidth() / extent);
						g.drawLine(pos, theCrossSize / 2 - theLabeledTickWidth / 2, pos, theCrossSize / 2 + theLabeledTickWidth / 2);
						String text = theValueRenderer.apply(tick);
						Rectangle2D bounds = new TextLayout(text, getFont(), ((Graphics2D) g).getFontRenderContext()).getBounds();
						pos -= bounds.getWidth() / 2 + bounds.getMinX() - 1;
						if (pos + bounds.getMinX() < 2)
							pos = 2 - (int) bounds.getMinX();
						else if (pos + bounds.getMaxX() > getWidth() - 2)
							pos = getWidth() - (int) bounds.getMaxX() - 2;
						((Graphics2D) g).drawString(text, pos, theCrossSize + 5 + (int) bounds.getMaxY());
					}
					if (theSimpleTickWidth > 0 && theSimpleTickSpacing > 0) {
						float simpleTick = theLabeledTicks.isEmpty() ? (float) min : theLabeledTicks.get(0);
						while (simpleTick > min + theSimpleTickSpacing)
							simpleTick -= theSimpleTickSpacing;
						float max = (float) theSlider.getSliderRange().get().getMax();
						while (simpleTick < max) {
							int pos = (int) Math.round((simpleTick - min) * getWidth() / extent);
							g.drawLine(pos, theCrossSize / 2 - theSimpleTickWidth / 2, pos, theCrossSize / 2 + theSimpleTickWidth / 2);
							simpleTick += theSimpleTickSpacing;
						}
					}
				}
			}
		}
	}

	/** A point that can be adjusted on a {@link Range} */
	public enum RangePoint {
		/** The minimum value of a range */
		min,
		/** The mid point of a range */
		mid,
		/** The maximum value of a range */
		max;
	}

	/** Renders ranges in a slider */
	public interface RangeRenderer {
		/**
		 * @param range The collection element containing the range to render
		 * @param hovered The range point that is currently hovered over by the user (or null if none)
		 * @param focused The range point was most recently clicked by the user for editing (or null if none)
		 * @return A component used to render the range on the slider
		 */
		Component renderRange(CollectionElement<Range> range, RangePoint hovered, RangePoint focused);

		/**
		 * @param range The collection element containing the range to render
		 * @param point The range point that is currently hovered over by the user (not null)
		 * @param focused Whether the range is also focused by the user for editing
		 * @return The mouse cursor to use for the given point on the range
		 */
		Cursor getCursor(CollectionElement<Range> range, RangePoint point, boolean focused);

		/**
		 * @param range The collection element containing the range to render
		 * @param point The range point that is currently hovered over by the user (not null)
		 * @return The tooltip to display to the user for the given point on the range
		 */
		String getTooltip(CollectionElement<Range> range, RangePoint point);

		/**
		 * @return The position (perpendicular to the slider's {@link MultiRangeSlider#isVertical() orientation}) where the center of the
		 *         rendered ranges lie.
		 */
		int getCenter();

		/** A default {@link RangeRenderer} implementation that provides lots of customization for range rendering */
		public static class Default extends JPanel implements RangeRenderer {
			private static final int DEFAULT_WIDTH = 12;
			private static final int DEFAULT_CIRCLE_SIZE = 8;
			private static final Stroke NORMAL_STROKE = new BasicStroke(2, 0, 1);
			private static final Stroke HOVERED_STROKE = new BasicStroke(3, 0, 1);
			private static final Stroke FOCUSED_STROKE = new BasicStroke(4, 0, 1);

			private final boolean isVertical;

			private int theRenderWidth;
			private int theCircleSize;
			private Cursor theMinCursor;
			private Cursor theMidCursor;
			private Cursor theMaxCursor;
			private DoubleFunction<String> theValueRenderer;
			private Function<CollectionElement<Range>, Color> theLineColor;
			private Function<CollectionElement<Range>, Color> theCircleColor;

			private RangePoint theHovered;
			private RangePoint theFocused;

			/** @param vertical Whether the slider to be rendered is horizontal or vertical */
			public Default(boolean vertical) {
				isVertical = vertical;
				setRenderWidth(DEFAULT_WIDTH);
				setCircleSize(DEFAULT_CIRCLE_SIZE);
				theValueRenderer = MRSliderRenderer.Default.VALUE_FORMAT::format;
				setPreferredSize(new Dimension(isVertical ? DEFAULT_WIDTH : 0, isVertical ? 0 : DEFAULT_WIDTH));
				theMinCursor = Cursor.getPredefinedCursor(isVertical ? Cursor.S_RESIZE_CURSOR : Cursor.W_RESIZE_CURSOR);
				theMidCursor = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR);
				theMaxCursor = Cursor.getPredefinedCursor(isVertical ? Cursor.N_RESIZE_CURSOR : Cursor.E_RESIZE_CURSOR);
				withColor(//
					range -> new Color(range.getElementId().hashCode()), //
					range -> new Color(Integer.rotateLeft(range.getElementId().hashCode(), 16))//
					);
			}

			/** @return Whether this renderer is for horizontal or vertical sliders */
			public boolean isVertical() {
				return isVertical;
			}

			/**
			 * @return The cross-dimension length to use to render ranges (the width of the range render for a vertical slider or the height
			 *         of the range render for a horizontal one)
			 */
			public int getRenderWidth() {
				return theRenderWidth;
			}

			/**
			 * @param renderWidth The cross-dimension length to use to render ranges (the width of the range render for a vertical slider or
			 *        the height of the range render for a horizontal one)
			 * @return This renderer
			 */
			public Default setRenderWidth(int renderWidth) {
				theRenderWidth = renderWidth;
				return this;
			}

			/** @return The diameter of the circles used for the mid-point of ranges */
			public int getCircleSize() {
				return theCircleSize;
			}

			/**
			 * @param circleSize The diameter for the circles to render for the mid-point of ranges
			 * @return This renderer
			 */
			public Default setCircleSize(int circleSize) {
				theCircleSize = circleSize;
				return this;
			}

			/**
			 * @param minCursor The cursor to use when the mouse hovers over a range's minimum value
			 * @return This renderer
			 */
			public Default setMinCursor(Cursor minCursor) {
				theMinCursor = minCursor;
				return this;
			}

			/**
			 * @param midCursor The cursor to use when the mouse hovers over a range's mid point
			 * @return This renderer
			 */
			public Default setMidCursor(Cursor midCursor) {
				theMidCursor = midCursor;
				return this;
			}

			/**
			 * @param maxCursor The cursor to use when the mouse hovers over a range's maximum value
			 * @return This renderer
			 */
			public Default setMaxCursor(Cursor maxCursor) {
				theMaxCursor = maxCursor;
				return this;
			}

			/**
			 * @param lineColor Produces a color for the render of each range in the model
			 * @param circleColor Produces a color for the circle of each range in the model
			 * @return This renderer
			 */
			public Default withColor(Function<CollectionElement<Range>, Color> lineColor,
				Function<CollectionElement<Range>, Color> circleColor) {
				theLineColor = lineColor;
				theCircleColor = circleColor;
				return this;
			}

			/**
			 * @param valueRenderer Produces text for tooltips
			 * @return This renderer
			 */
			public Default setValueRenderer(DoubleFunction<String> valueRenderer) {
				theValueRenderer = valueRenderer;
				return this;
			}

			/** @return The {@link RangePoint} that the user is currently hovering over */
			public RangePoint getHovered() {
				return theHovered;
			}

			/** @return The {@link RangePoint} that the user has focused on */
			public RangePoint getFocused() {
				return theFocused;
			}

			@Override
			public Component renderRange(CollectionElement<Range> range, RangePoint hovered, RangePoint focused) {
				theHovered = hovered;
				theFocused = focused;
				setForeground(theLineColor == null ? Color.black : theLineColor.apply(range));
				setBackground(theCircleColor == null ? Color.black : theCircleColor.apply(range));
				return this;
			}

			@Override
			public int getCenter() {
				return theRenderWidth / 2;
			}

			@Override
			public Cursor getCursor(CollectionElement<Range> range, RangePoint point, boolean focused) {
				switch (point) {
				case min:
					return theMinCursor;
				case mid:
					return theMidCursor;
				default:
					return theMaxCursor;
				}
			}

			@Override
			public String getTooltip(CollectionElement<Range> range, RangePoint point) {
				StringBuilder str = new StringBuilder().append("<html><b><font color=\"").append(Colors.toHTML(getForeground()))
					.append("\">")//
					.append(point).append(": ");
				switch (point) {
				case min:
					str.append(theValueRenderer.apply(range.get().getMin()));
					break;
				case mid:
					str.append(theValueRenderer.apply(range.get().getValue()));
					break;
				case max:
					str.append(theValueRenderer.apply(range.get().getMax()));
					break;
				}
				return str.toString();
			}

			@Override
			public void paint(Graphics g) {
				g.setColor(getForeground());
				if (isVertical) {
					if (getHeight() > 0) {
						((Graphics2D) g).setStroke(getStroke(RangePoint.max));
						g.drawLine(0, 0, getRenderWidth(), 0);
						((Graphics2D) g).setStroke(getStroke(RangePoint.min));
						g.drawLine(0, getHeight(), getRenderWidth(), getHeight());
						((Graphics2D) g).setStroke(getStroke(null));
						g.drawLine(theRenderWidth / 2, 0, theRenderWidth / 2, getHeight());
					}
				} else {
					if (getWidth() > 0) {
						((Graphics2D) g).setStroke(getStroke(RangePoint.min));
						g.drawLine(0, 0, 0, getHeight());
						((Graphics2D) g).setStroke(getStroke(RangePoint.max));
						g.drawLine(getWidth(), 0, getWidth(), getHeight());
						((Graphics2D) g).setStroke(getStroke(null));
						g.drawLine(0, theRenderWidth / 2, getWidth(), theRenderWidth / 2);
					}
				}
				Object preAA = ((Graphics2D) g).getRenderingHint(RenderingHints.KEY_ANTIALIASING);
				((Graphics2D) g).setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g.setColor(getBackground());
				g.fillOval((getWidth() - theCircleSize) / 2, (getHeight() - theCircleSize) / 2, theCircleSize, theCircleSize);
				g.setColor(getForeground());
				((Graphics2D) g).setStroke(NORMAL_STROKE);
				g.drawOval((getWidth() - theCircleSize) / 2, (getHeight() - theCircleSize) / 2, theCircleSize, theCircleSize);
				((Graphics2D) g).setRenderingHint(RenderingHints.KEY_ANTIALIASING, preAA);
			}

			/**
			 * @param point The range point to get the stroke for
			 * @return The stroke to use for rendering the given range point
			 */
			protected Stroke getStroke(RangePoint point) {
				if (point == null) {
					if (theFocused != null)
						return FOCUSED_STROKE;
					else if (theHovered != null)
						return HOVERED_STROKE;
					else
						return NORMAL_STROKE;
				}
				if (point == theFocused)
					return FOCUSED_STROKE;
				else if (point == theHovered)
					return HOVERED_STROKE;
				else
					return NORMAL_STROKE;
			}
		}
	}

	private final boolean isVertical;
	private final ObservableValue<Range> theSliderRange;
	private final ObservableCollection<Range> theRanges;
	private boolean isAdjustingBoundsForValue;
	private QommonsTimer.TaskHandle theUpdateTask;
	private boolean isUpdateDelayed;

	private int theReferencePosition;
	private double theReferenceValue;
	private volatile MouseEvent theLastDrag;
	private boolean isLastDragDrop;
	private final List<int[]> theRangeRenderBounds;
	private RangeValidator theValidator;
	private MRSliderRenderer theRenderer;
	private RangeRenderer theRangeRenderer;

	private ElementId theHoveredRange;
	private RangePoint theHoveredRangePoint;
	private ElementId theFocusedRange;
	private RangePoint theFocusedRangePoint;

	private boolean isDragging;

	private MultiRangeSlider(boolean vertical, ObservableValue<Range> sliderRange, ObservableCollection<Range> ranges,
		Observable<?> until) {
		super(new LayerLayout());
		setFocusable(true);
		isVertical = vertical;
		theSliderRange = sliderRange;
		theRanges = ranges;
		theRangeRenderBounds = new ArrayList<>();
		theValidator = RangeValidator.FREE;
		setRenderer(new MRSliderRenderer.Default());
		setRangeRenderer(new RangeRenderer.Default(isVertical));

		CausableKey rerender = Causable.key((cause, data) -> {
			setRenderer(theRenderer);
		});
		theSliderRange.noInitChanges().takeUntil(until).act(evt -> evt.getRootCausable().onFinish(rerender));
		theRanges.simpleChanges().takeUntil(until).act(evt -> repaint());

		QommonsTimer.TaskHandle[] updateTask = new QommonsTimer.TaskHandle[1];
		theUpdateTask = updateTask[0] = QommonsTimer.getCommonInstance().build(() -> {
			MouseEvent lastDrag = theLastDrag;
			if (lastDrag != null//
				&& (!moveFocus(lastDrag, false) || isLastDragDrop)//
				&& theLastDrag == lastDrag)
				theLastDrag = null;
			updateTask[0].setActive(theLastDrag != null);
		}, Duration.ofMillis(100), false).onEDT();
		initListeners();
	}

	private void initListeners() {
		MouseAdapter mouse = new MouseAdapter() {
			private boolean wasDragged = false;

			@Override
			public void mousePressed(MouseEvent e) {
				isDragging = apply(e, true);
				wasDragged = false;
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				isDragging = false;
				if (wasDragged) // Don't move anything if the user hasn't dragged it
					moveFocus(e, true);
			}

			@Override
			public void mouseDragged(MouseEvent e) {
				if (isDragging) {
					wasDragged = true;
					if (isUpdateDelayed) {
						theLastDrag = e;
						isLastDragDrop = true;
						theUpdateTask.setActive(true);
					} else
						moveFocus(e, false);
				}
			}

			@Override
			public void mouseMoved(MouseEvent e) {
				apply(e, false);
			}

			@Override
			public void mouseExited(MouseEvent e) {
				theHoveredRange = null;
				theHoveredRangePoint = null;
				if (!isDragging)
					setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
				repaint();
			}

			boolean apply(MouseEvent e, boolean focused) {
				int pos;
				double value, tol;
				Range r = theSliderRange.get();
				if (isVertical()) {
					pos = e.getX();
					value = r.getMin() + (getHeight() - e.getY()) * r.getExtent() / getHeight();
					tol = r.getExtent() / getHeight() * 2; // 1 pixel
				} else {
					pos = e.getY();
					value = r.getMin() + e.getX() * r.getExtent() / getWidth();
					tol = r.getExtent() / getWidth() * 2; // 1 pixel
				}
				// Find what the user has selected with 4 levels of tolerance, so if they've selected something very carefully, we use that,
				// but if there's nothing else around, they don't have to be very careful.
				ElementId[] foundPerPixel = new ElementId[4];
				RangePoint[] pointPerPixel = new RangePoint[4];
				int index = 0;
				CollectionElement<Range>[] ranges = new CollectionElement[theRanges.size()];
				// If there is a focused and/or hovered range, give them priority
				// Hovered first, since hover responds to the mouse
				if (theHoveredRange != null && theHoveredRange.isPresent())
					ranges[index++] = theRanges.getElement(theHoveredRange);
				if (theFocusedRange != null && theFocusedRange.isPresent() && !theFocusedRange.equals(theHoveredRange))
					ranges[index++] = theRanges.getElement(theFocusedRange);
				for (CollectionElement<Range> range : theRanges.elements()) {
					if (!range.getElementId().equals(theFocusedRange) && !range.getElementId().equals(theHoveredRange))
						ranges[index++] = range;
				}
				for (CollectionElement<Range> range : ranges) {
					index = theRanges.getElementsBefore(range.getElementId());
					if (index == theRangeRenderBounds.size())
						continue;
					int relPos = pos - theRangeRenderBounds.get(index)[0];
					if (relPos >= 0 && relPos < theRangeRenderBounds.get(index)[1]) {
						double hit = Math.abs(value - range.get().getValue()) / tol;
						for (int prec = 0; prec < foundPerPixel.length; prec++) {
							if (foundPerPixel[prec] == null && hit <= prec + 1) {
								foundPerPixel[prec] = range.getElementId();
								pointPerPixel[prec] = RangePoint.mid;
								break;
							}
						}
						hit = Math.abs(value - range.get().getMin()) / tol;
						for (int prec = 0; prec < foundPerPixel.length; prec++) {
							if (foundPerPixel[prec] == null && hit <= prec + 1) {
								foundPerPixel[prec] = range.getElementId();
								pointPerPixel[prec] = RangePoint.min;
								break;
							}
						}
						hit = Math.abs(value - range.get().getMax()) / tol;
						for (int prec = 0; prec < foundPerPixel.length; prec++) {
							if (foundPerPixel[prec] == null && hit <= prec + 1) {
								foundPerPixel[prec] = range.getElementId();
								pointPerPixel[prec] = RangePoint.max;
								break;
							}
						}
					}
				}
				ElementId found = null;
				RangePoint point = null;
				for (int prec = 0; prec < foundPerPixel.length; prec++) {
					if (foundPerPixel[prec] != null) {
						found = foundPerPixel[prec];
						point = pointPerPixel[prec];
						break;
					}
				}
				if (focused) {
					_setFocused(found, point);
					if (found != null) {
						requestFocus();
						theReferencePosition = isVertical ? e.getY() : e.getX();
						switch (point) {
						case min:
							theReferenceValue = theRanges.getElement(found).get().getMin();
							break;
						case mid:
							theReferenceValue = theRanges.getElement(found).get().getValue();
							break;
						case max:
							theReferenceValue = theRanges.getElement(found).get().getMax();
							break;
						}
					}
				} else
					_setHovered(found, point);
				if (found != null) {
					CollectionElement<Range> range = theRanges.getElement(found);
					setCursor(theRangeRenderer.getCursor(range, point, focused));
					setToolTipText(theRangeRenderer.getTooltip(range, point));
				} else
					setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
				return found != null;
			}
		};
		addMouseListener(mouse);
		addMouseMotionListener(mouse);
		addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				switch (e.getKeyCode()) {
				case KeyEvent.VK_DOWN:
				case KeyEvent.VK_NUMPAD2:
					if (!isVertical)
						break;
					moveFocus(false, e);
					break;
				case KeyEvent.VK_UP:
				case KeyEvent.VK_NUMPAD8:
					if (!isVertical)
						break;
					moveFocus(true, e);
					break;
				case KeyEvent.VK_LEFT:
				case KeyEvent.VK_NUMPAD4:
					if (isVertical)
						break;
					moveFocus(false, e);
					break;
				case KeyEvent.VK_RIGHT:
				case KeyEvent.VK_NUMPAD6:
					if (isVertical)
						break;
					moveFocus(true, e);
					break;
				}
			}

			private void moveFocus(boolean up, KeyEvent evt) {
				if (theFocusedRange == null || !theFocusedRange.isPresent())
					return;
				CollectionElement<Range> range = theRanges.getElement(theFocusedRange);
				double diff = (up ? 1 : -1) * theSliderRange.get().getExtent() / (isVertical ? getHeight() : getWidth());
				MultiRangeSlider.this.moveFocus(range, diff, evt);
			}
		});
	}

	/** @return The bounds of this slider */
	public ObservableValue<Range> getSliderRange() {
		return theSliderRange;
	}

	/** @return The collection containing all the ranges rendered by this slider */
	public ObservableCollection<Range> getRanges() {
		return theRanges;
	}

	/** @return The maximum frequency for updates from this slider, or null if there is no maximum frequency */
	public Duration getMaxUpdateInterval() {
		return isUpdateDelayed ? theUpdateTask.getFrequency() : null;
	}

	/**
	 * @param maxUpdateInterval If not-null and positive, causes this slider to respond to mouse move events in a delayed fashion, such that
	 *        the ranges are only updated with the given maximum frequency. This can be very important if update operations are slow.
	 * @return This slider
	 */
	public MultiRangeSlider setMaxUpdateInterval(Duration maxUpdateInterval) {
		if (maxUpdateInterval != null && maxUpdateInterval.compareTo(Duration.ofSeconds(0)) > 0) {
			theUpdateTask.setFrequency(maxUpdateInterval, false);
			isUpdateDelayed = true;
		} else
			isUpdateDelayed = false;
		return this;
	}

	/**
	 * @return Whether this slider attempts to move its {@link #getSliderRange() range} when the user attempts to move ranges beyond them.
	 */
	public boolean isAdjustingBoundsForValue() {
		return isAdjustingBoundsForValue;
	}

	/**
	 * @param adjustingBoundsForValue If true, this slider will attempt to modify the {@link #getSliderRange() slider range} when the user
	 *        attempts to move ranges beyond them. The slider range used to build the slider will need to be an instance of
	 *        {@link SettableValue} for this to be effective.
	 * @return This slider
	 */
	public MultiRangeSlider setAdjustingBoundsForValue(boolean adjustingBoundsForValue) {
		isAdjustingBoundsForValue = adjustingBoundsForValue;
		return this;
	}

	/** @return Whether this slider is rendered vertically or horizontally */
	public boolean isVertical() {
		return isVertical;
	}

	/** @return This slider's range validator */
	public RangeValidator getValidator() {
		return theValidator;
	}

	/**
	 * @param validator Validates ranges as the user attempts to modify them.
	 * @return This slider
	 */
	public MultiRangeSlider setValidator(RangeValidator validator) {
		theValidator = validator;
		if (theValidator != null) {
			// Re-validate all the ranges
			try (Transaction t = theRanges.lock(true, null)) {
				for (CollectionElement<Range> range : theRanges.elements()) {
					Range newRange = theValidator.validate(this, range, range.get(), RangePoint.mid);
					if (!newRange.equals(range.get()))
						theRanges.mutableElement(range.getElementId()).set(newRange);
				}
			}
		}
		return this;
	}

	/** @return The renderer for this slider */
	public MRSliderRenderer getRenderer() {
		return theRenderer;
	}

	/**
	 * @param renderer The renderer for this slider
	 * @return This slider
	 */
	public MultiRangeSlider setRenderer(MRSliderRenderer renderer) {
		theRenderer = renderer;
		Component render = theRenderer.render(this);
		if (getComponentCount() == 0 || render != getComponent(0)) {
			if (getComponentCount() > 0)
				remove(0);
			add(render);
		} else
			repaint();
		return this;
	}

	/** @return The renderer for ranges in this slider */
	public RangeRenderer getRangeRenderer() {
		return theRangeRenderer;
	}

	/**
	 * @param rangeRenderer The renderer for ranges in this slider
	 * @return This slider
	 */
	public MultiRangeSlider setRangeRenderer(RangeRenderer rangeRenderer) {
		theRangeRenderer = rangeRenderer;
		if (!theRanges.isEmpty())
			repaint();
		return this;
	}

	/** @return The element ID of the range that the user is currently hovering over */
	public ElementId getHoveredRange() {
		return theHoveredRange;
	}

	/**
	 * @return The range point that the user is currently hovering over
	 * @see #getHoveredRange()
	 */
	public RangePoint getHoveredRangePoint() {
		return theHoveredRangePoint;
	}

	/**
	 * Overrides the hovered state of the UI
	 *
	 * @param hovered The element ID of the range to treat as hovered
	 * @param point The range point to treat as hovered
	 * @return This slider
	 */
	public MultiRangeSlider setHovered(ElementId hovered, RangePoint point) {
		theRanges.getElement(hovered); // Validate the element
		_setHovered(hovered, point);
		return this;
	}

	private void _setHovered(ElementId hovered, RangePoint point) {
		ElementId oldR = theHoveredRange;
		RangePoint oldP = theHoveredRangePoint;
		theHoveredRange = hovered;
		theHoveredRangePoint = point;
		if (!Objects.equals(oldR, hovered)) {
			firePropertyChange("hoveredRange", oldR, theHoveredRange);
			firePropertyChange("hoveredRangePoint", oldP, theHoveredRangePoint);
			repaint();
		} else if (!Objects.equals(oldP, point)) {
			firePropertyChange("hoveredRangePoint", oldP, theHoveredRangePoint);
			repaint();
		}
	}

	/** @return The element ID of the range that the user has focused on (the last one clicked by the mouse typically) */
	public ElementId getFocusedRange() {
		return theFocusedRange;
	}

	/**
	 * @return The range point that the user has focused on
	 * @see #getFocusedRange()
	 */
	public RangePoint getFocusedRangePoint() {
		return theFocusedRangePoint;
	}

	/**
	 * Overrides the focused state of the UI
	 *
	 * @param focused The element ID of the range to treat as focused
	 * @param point The range point to treat as focused
	 * @return This slider
	 */
	public MultiRangeSlider setFocused(ElementId focused, RangePoint point) {
		theRanges.getElement(focused); // Validate the element
		_setFocused(focused, point);
		return this;
	}

	private void _setFocused(ElementId focused, RangePoint point) {
		ElementId oldR = theFocusedRange;
		RangePoint oldP = theFocusedRangePoint;
		theFocusedRange = focused;
		theFocusedRangePoint = point;
		if (!Objects.equals(oldR, focused)) {
			firePropertyChange("focusedRange", oldR, theFocusedRange);
			firePropertyChange("focusedRangePoint", oldP, theFocusedRangePoint);
			repaint();
		} else if (!Objects.equals(oldP, point)) {
			firePropertyChange("focusedRangePoint", oldP, theFocusedRangePoint);
			repaint();
		}
	}

	/**
	 * @return Whether the user is currently modifying a range in this slider with an action that will have a termination, such as dragging
	 */
	public boolean isAdjusting() {
		return isDragging;
	}

	/**
	 * Called when the user performs a mouse action that may be interpreted as an attempt to modify a range
	 *
	 * @param e The mouse event causing the move
	 * @param forceFire Whether to fire an update event if there is no value change
	 * @return If the move failed because of enablement, and the operation should be tried again
	 */
	protected boolean moveFocus(MouseEvent e, boolean forceFire) {
		if (theFocusedRange == null || !theFocusedRange.isPresent())
			return true;
		CollectionElement<Range> current = theRanges.getElement(theFocusedRange);
		double value;
		Range sliderRange2 = theSliderRange.get();
		if (isVertical())
			value = theReferenceValue + (theReferencePosition - e.getY()) * sliderRange2.getExtent() / getHeight();
		else
			value = theReferenceValue + (e.getX() - theReferencePosition) * sliderRange2.getExtent() / getWidth();
		Range newRange = null;
		switch (theFocusedRangePoint) {
		case min:
			if (value > current.get().getMax())
				value = current.get().getMax();
			if (!forceFire && value == current.get().getMin())
				return false;
			newRange = Range.forMinMax(value, current.get().getMax());
			break;
		case mid:
			if (!forceFire && value == current.get().getValue())
				return false;
			newRange = Range.forValueExtent(value, current.get().getExtent());
			break;
		case max:
			if (value < current.get().getMin())
				value = current.get().getMin();
			if (!forceFire && value == current.get().getMax())
				return false;
			newRange = Range.forMinMax(current.get().getMin(), value);
			break;
		}
		return tryMoveFocus(current, newRange, e);
	}

	/**
	 * Called when the user performs a keyboard action that may be interpreted as an attempt to modify a range
	 *
	 * @param range The collection element of the range to move
	 * @param diff The amount for the movement
	 * @param evt The keyboard event
	 */
	protected void moveFocus(CollectionElement<Range> range, double diff, KeyEvent evt) {
		Range newRange = null;
		double newV;
		RangePoint movePoint = theFocusedRangePoint;
		if (movePoint == RangePoint.mid) {
			if (evt.isShiftDown()) {
				if (evt.isControlDown())
					movePoint = null; // When shift and control are pressed, modify the extent
				else
					movePoint = RangePoint.max;// When shift is pressed, move the upper bound
			} else if (evt.isControlDown())
				movePoint = RangePoint.min;// When control is pressed, move the lower bound
		}
		if (movePoint == null) {
			newRange = Range.forValueExtent(range.get().getValue(), range.get().getExtent() + diff * 2);
		} else {
			switch (movePoint) {
			case min:
				newV = range.get().getMin() + diff;
				if (newV > range.get().getMax()) {
					newV = range.get().getMax();
					if (newV == range.get().getMin())
						return;
				}
				newRange = Range.forMinMax(newV, range.get().getMax());
				break;
			case mid:
				newRange = Range.forValueExtent(range.get().getValue() + diff, range.get().getExtent());
				break;
			case max:
				newV = range.get().getMax() + diff;
				if (newV < range.get().getMin()) {
					newV = range.get().getMin();
					if (newV == range.get().getMax())
						return;
				}
				newRange = Range.forMinMax(range.get().getMin(), newV);
				break;
			}
		}
		tryMoveFocus(range, newRange, evt);
	}

	/**
	 * Attempts to modify a range
	 *
	 * @param range The collection element of the range to modify
	 * @param newRange The new value for the range
	 * @param cause The cause of the change
	 * @return True if the modification was rejected in a way that indicates the attempt should be reattempted
	 */
	protected boolean tryMoveFocus(CollectionElement<Range> range, Range newRange, Object cause) {
		if (isAdjustingBoundsForValue && theSliderRange instanceof SettableValue) {
			if (newRange.getMin() < theSliderRange.get().getMin()) {
				if (newRange.getMax() > theSliderRange.get().getMax()) {
					if (((SettableValue<Range>) theSliderRange).isAcceptable(newRange) == null)
						((SettableValue<Range>) theSliderRange).set(newRange, cause);
				} else {
					Range newBounds = Range.forMinMax(newRange.getMin(), theSliderRange.get().getMax());
					if (((SettableValue<Range>) theSliderRange).isAcceptable(newBounds) == null)
						((SettableValue<Range>) theSliderRange).set(newBounds, cause);
				}
			} else if (newRange.getMax() > theSliderRange.get().getMax()) {
				Range newBounds = Range.forMinMax(theSliderRange.get().getMin(), newRange.getMax());
				if (((SettableValue<Range>) theSliderRange).isAcceptable(newBounds) == null)
					((SettableValue<Range>) theSliderRange).set(newBounds, cause);
			}
		}
		newRange = theValidator.validate(MultiRangeSlider.this, range, newRange, theFocusedRangePoint);
		if (newRange == null)
			return false; // Rejected by the validator
		MutableCollectionElement<Range> mutableEl = theRanges.mutableElement(theFocusedRange);
		if (mutableEl.isEnabled() != null)
			return true; // Disabled--may try again later
		String msg = mutableEl.isAcceptable(newRange);
		if (msg != null) {
			setToolTipText(msg);
			ObservableSwingUtils.setTooltipVisible(MultiRangeSlider.this, true);
			return false; // Rejected by the data
		}
		mutableEl.set(newRange);
		return false;
	}

	@Override
	public void paint(Graphics g) {
		paintChildren(g);
		Range sliderRange = theSliderRange.get();
		int center = theRenderer.getCenter(this);
		double min = sliderRange.getMin();
		int index = 0;
		Shape preClip = g.getClip();
		Stroke preStroke = ((Graphics2D) g).getStroke();
		g.setClip(0, 0, getWidth(), getHeight()); // Don't let ranges draw outside the widget's bounds
		CollectionElement<Range>[] ranges = new CollectionElement[theRanges.size()];
		for (CollectionElement<Range> range : theRanges.elements()) {
			if (!range.getElementId().equals(theFocusedRange) && !range.getElementId().equals(theHoveredRange))
				ranges[index++] = range;
		}
		// If there is a focused and/or hovered range, give them priority, i.e. render them last
		// Hovered very last, since hover responds to the mouse and that's what they most need to see
		if (theFocusedRange != null && theFocusedRange.isPresent() && !theFocusedRange.equals(theHoveredRange))
			ranges[index++] = theRanges.getElement(theFocusedRange);
		if (theHoveredRange != null && theHoveredRange.isPresent())
			ranges[index++] = theRanges.getElement(theHoveredRange);
		for (CollectionElement<Range> range : ranges) {
			index = theRanges.getElementsBefore(range.getElementId());
			RangePoint hovered = range.getElementId().equals(theHoveredRange) ? theHoveredRangePoint : null;
			RangePoint focused = range.getElementId().equals(theFocusedRange) ? theFocusedRangePoint : null;
			Component render = theRangeRenderer.renderRange(range, hovered, focused);
			if (isVertical) {
				int pos = (int) Math.round((range.get().getMin() - min) * getHeight() / sliderRange.getExtent());
				int max = (int) Math.round((range.get().getMax() - min) * getHeight() / sliderRange.getExtent());
				int temp = getHeight() - max;
				max = getHeight() - pos;
				pos = temp;
				int width = render.getPreferredSize().width;
				int x = center - (width - theRangeRenderer.getCenter());
				render.setBounds(0, 0, width, max - pos);
				g.translate(x, pos);
				try {
					render.paint(g);
				} finally {
					g.translate(-x, -pos);
				}
				setRangeRenderBounds(index, x, width);
			} else {
				int pos = (int) Math.round((range.get().getMin() - min) * getWidth() / sliderRange.getExtent());
				int max = (int) Math.round((range.get().getMax() - min) * getWidth() / sliderRange.getExtent());
				int height = render.getPreferredSize().height;
				int y = center - (height - theRangeRenderer.getCenter());
				render.setBounds(0, 0, max - pos, height);
				g.translate(pos, y);
				try {
					render.paint(g);
				} finally {
					g.translate(-pos, -y);
				}
				setRangeRenderBounds(index, y, height);
			}
		}
		g.setClip(preClip);
		((Graphics2D) g).setStroke(preStroke);
	}

	private void setRangeRenderBounds(int index, int pos, int size) {
		if (index >= theRangeRenderBounds.size())
			theRangeRenderBounds.add(new int[2]);
		theRangeRenderBounds.get(index)[0] = pos;
		theRangeRenderBounds.get(index)[1] = size;
	}

	/**
	 * Creates a slider for a collection of ranges
	 *
	 * @param vertical Whether the slider should be rendered vertically or horizontally
	 * @param sliderRange The total range for the slider
	 * @param ranges The ranges to render
	 * @param until The observable to release all of the slider's resources and listeners
	 * @return The new slider
	 */
	public static MultiRangeSlider multi(boolean vertical, ObservableValue<Range> sliderRange, ObservableCollection<Range> ranges,
		Observable<?> until) {
		return new MultiRangeSlider(vertical, sliderRange, ranges, until);
	}

	/**
	 * Creates a slider for a single range
	 *
	 * @param vertical Whether the slider should be rendered vertically or horizontally
	 * @param sliderRange The total range for the slider
	 * @param range The range to render
	 * @param until The observable to release all of the slider's resources and listeners
	 * @return The new slider
	 */
	public static MultiRangeSlider single(boolean vertical, ObservableValue<Range> sliderRange, SettableValue<Range> range,
		Observable<?> until) {
		ObservableCollection<Range> ranges = ObservableCollection
			.of(TypeTokens.get().keyFor(SettableValue.class).parameterized(Range.class), //
				range)
			.flow().flattenValues(TypeTokens.get().of(Range.class), v -> v)//
			.collectActive(until);
		return multi(vertical, sliderRange, ranges, until);
	}

	/**
	 * Creates a slider for a single range given a mid-point value and an extent for the range
	 *
	 * @param vertical Whether the slider should be rendered vertically or horizontally
	 * @param sliderRange The total range for the slider
	 * @param value The mid-point value of the range to render
	 * @param extent The extent of the range to render
	 * @param until The observable to release all of the slider's resources and listeners
	 * @return The new slider
	 */
	public static MultiRangeSlider forValueExtent(boolean vertical, ObservableValue<Range> sliderRange, SettableValue<Double> value,
		SettableValue<Double> extent, Observable<?> until) {
		boolean[] callbackLock = new boolean[1];
		SettableValue<Range> range = SettableValue.build(Range.class).withValue(Range.forValueExtent(value.get(), extent.get()))//
			.withLocking(value).build()//
			.filterAccept(r -> {
				if (callbackLock[0])
					return null;
				String accept = null;
				if (value.get() != r.getValue())
					accept = value.isAcceptable(r.getValue());
				if (accept == null && extent.get() != r.getExtent())
					accept = extent.isAcceptable(r.getExtent());
				return accept;
			});
		value.noInitChanges().takeUntil(until).act(evt -> {
			if (callbackLock[0])
				return;
			callbackLock[0] = true;
			try {
				Range current = range.get();
				if (evt.getNewValue() == current.getValue())
					range.set(current, evt);
				else
					range.set(Range.forValueExtent(evt.getNewValue(), current.getExtent()), evt);
			} finally {
				callbackLock[0] = false;
			}
		});
		extent.noInitChanges().takeUntil(until).act(evt -> {
			if (callbackLock[0])
				return;
			callbackLock[0] = true;
			try {
				Range current = range.get();
				if (evt.getNewValue() == current.getExtent())
					range.set(current, evt);
				else
					range.set(Range.forValueExtent(current.getValue(), evt.getNewValue()), evt);
			} finally {
				callbackLock[0] = false;
			}
		});
		range.noInitChanges().takeUntil(until).act(evt -> {
			if (callbackLock[0])
				return;
			callbackLock[0] = true;
			try {
				Double v = value.get();
				Double extV = extent.get();
				if (evt.getNewValue().getExtent() == extV.doubleValue()) {
					if (v.doubleValue() == evt.getNewValue().getValue())
						value.set(v, evt);
					else
						value.set(evt.getNewValue().getValue(), evt);
				} else {
					if (evt.getNewValue().getValue() == v.doubleValue())
						value.set(v, evt);
					else
						value.set(evt.getNewValue().getValue(), evt);
					extent.set(evt.getNewValue().getExtent(), evt);
				}
			} finally {
				callbackLock[0] = false;
			}
		});
		return single(vertical, sliderRange, range, until);
	}

	/**
	 * Creates a slider for a single range given min and max values for the range
	 *
	 * @param vertical Whether the slider should be rendered vertically or horizontally
	 * @param sliderRange The total range for the slider
	 * @param min The minimum value of the range to render
	 * @param max The maximum value of the range to render
	 * @param until The observable to release all of the slider's resources and listeners
	 * @return The new slider
	 */
	public static MultiRangeSlider forMinMax(boolean vertical, ObservableValue<Range> sliderRange, SettableValue<Double> min,
		SettableValue<Double> max, Observable<?> until) {
		return single(vertical, sliderRange, transformToRange(min, max, until), until);
	}

	/**
	 * Creates a range value from min/max values
	 *
	 * @param min The minimum value for the range
	 * @param max The maximum value for the range
	 * @param until An observable that will cause the synchronization between the min/max values and the result range value to cease
	 * @return The range value defined by the given min/max values
	 */
	public static SettableValue<Range> transformToRange(SettableValue<Double> min, SettableValue<Double> max, Observable<?> until) {
		boolean[] callbackLock = new boolean[1];
		SettableValue<Range> range = SettableValue.build(Range.class).withValue(Range.forMinMax(min.get(), max.get()))//
			.withLocking(min).build()//
			.filterAccept(r -> {
				if (callbackLock[0])
					return null;
				String accept = min.isAcceptable(r.getMin());
				if (accept == null)
					accept = max.isAcceptable(r.getMax());
				return accept;
			});
		min.noInitChanges().takeUntil(until).act(evt -> {
			if (callbackLock[0])
				return;
			callbackLock[0] = true;
			try {
				Range current = range.get();
				if (evt.getNewValue() == current.getMin())
					range.set(current, evt);
				else
					range.set(Range.forMinMax(evt.getNewValue(), current.getMax()), evt);
			} finally {
				callbackLock[0] = false;
			}
		});
		max.noInitChanges().takeUntil(until).act(evt -> {
			if (callbackLock[0])
				return;
			callbackLock[0] = true;
			try {
				Range current = range.get();
				if (evt.getNewValue() == current.getMax())
					range.set(current, evt);
				else
					range.set(Range.forMinMax(current.getMin(), evt.getNewValue()), evt);
			} finally {
				callbackLock[0] = false;
			}
		});
		range.noInitChanges().takeUntil(until).act(evt -> {
			if (callbackLock[0])
				return;
			callbackLock[0] = true;
			try {
				Double minV = min.get();
				Double maxV = max.get();
				if (evt.getNewValue().getMax() == maxV.doubleValue()) {
					if (minV.doubleValue() == evt.getNewValue().getMin())
						min.set(minV, evt);
					else
						min.set(evt.getNewValue().getMin(), evt);
				} else {
					if (evt.getNewValue().getMin() == minV.doubleValue())
						min.set(minV, evt);
					else
						min.set(evt.getNewValue().getMin(), evt);
					max.set(evt.getNewValue().getMax(), evt);
				}
			} finally {
				callbackLock[0] = false;
			}
		});
		return range;
	}

	/**
	 * A simple main method that displays a couple sliders for testing and demonstration
	 *
	 * @param args Command-line arguments, ignored
	 */
	public static void main(String... args) {
		JFrame frame = new JFrame(MultiRangeSlider.class.getSimpleName() + " Test");
		frame.setSize(800, 640);
		frame.setLocationRelativeTo(null);
		JPanel panel = new JPanel(new JustifiedBoxLayout(false).mainJustified().crossCenter());
		ObservableCollection<Range> vRanges = ObservableCollection.build(Range.class).build();
		MultiRangeSlider vSlider = multi(true, ObservableValue.of(Range.forMinMax(-100.0, 100.0)), vRanges, Observable.empty())//
			.setValidator(RangeValidator.NO_OVERLAP_ENFORCE_RANGE)//
			;
		panel.add(vSlider);
		ObservableCollection<Range> hRanges = ObservableCollection.build(Range.class).build();
		MultiRangeSlider hSlider = multi(false, ObservableValue.of(Range.forMinMax(-100.0, 100.0)), hRanges, Observable.empty())//
			.setValidator(RangeValidator.NO_OVERLAP_ENFORCE_RANGE)//
			.setMaxUpdateInterval(Duration.ofMillis(250))//
			;
		((MRSliderRenderer.Default) vSlider.getRenderer()).setSimpleTickSpacing(10);
		((MRSliderRenderer.Default) hSlider.getRenderer()).setSimpleTickSpacing(10);
		panel.add(hSlider);
		frame.getContentPane().add(panel);

		vRanges.add(Range.forMinMax(-50, -40));
		vRanges.add(Range.forMinMax(0, 10));
		vRanges.add(Range.forMinMax(20, 90));

		hRanges.add(Range.forMinMax(-50, -40));
		hRanges.add(Range.forMinMax(0, 10));
		hRanges.add(Range.forMinMax(20, 90));

		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setVisible(true);
	}
}
