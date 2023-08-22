package org.observe.quick.base;

import java.text.ParseException;
import java.util.Objects;

import org.observe.expresso.NonStructuredParser;
import org.observe.util.TypeTokens;

import com.google.common.reflect.TypeToken;

/** Represents the linear size of one dimension of a widget */
public class QuickSize {
	/** A size of zero */
	public static final QuickSize ZERO = new QuickSize(0.0f, 0);

	/** The size relative to the container size */
	public final float percent;

	/** The size in pixels */
	public final int pixels;

	/**
	 * @param percent The size relative to the container size
	 * @param pixels The size in pixels
	 */
	public QuickSize(float percent, int pixels) {
		this.percent = percent;
		this.pixels = pixels;
	}

	/**
	 * @param containerSize The length of the same dimension of the parent container
	 * @return This size, in pixels
	 */
	public int evaluate(int containerSize) {
		int value;
		if (percent != 0.0f)
			value = Math.round(containerSize / 100.0f * percent) + pixels;
		else
			value = pixels;
		return value;
	}

	public QuickSize plus(QuickSize other) {
		return new QuickSize(percent + other.percent, pixels + other.pixels);
	}

	public QuickSize minus(QuickSize other) {
		return new QuickSize(percent - other.percent, pixels - other.pixels);
	}

	public QuickSize plus(int adjPixels) {
		return new QuickSize(percent, pixels + adjPixels);
	}

	public int resolveExponential() {
		if (percent <= 0 || percent >= 100 || pixels <= 0)
			return pixels;
		// Solve absSize+percent/100*totalSize = totalSize
		return Math.round(pixels / (1 - percent / 100));
	}

	@Override
	public int hashCode() {
		return Objects.hash(percent, pixels);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this)
			return true;
		else if (!(obj instanceof QuickSize))
			return false;
		return percent == ((QuickSize) obj).percent && pixels == ((QuickSize) obj).pixels;
	}

	@Override
	public String toString() {
		if (percent != 0.0f) {
			if (pixels > 0)
				return percent + "% +" + pixels + "px";
			else if (pixels < 0)
				return percent + "%" + pixels + "px";
			else
				return percent + "%";
		} else
			return pixels + "px";
	}

	public static QuickSize ofPixels(int pixels) {
		if (pixels == 0)
			return ZERO;
		return new QuickSize(0.0f, pixels);
	}

	/**
	 * @param text The text to parse
	 * @return The position represented by the text
	 * @throws NumberFormatException If the position could not be parsed
	 */
	public static QuickSize parsePosition(String text) throws NumberFormatException {
		if (text.endsWith("px"))
			return new QuickSize(0.0f, Integer.parseInt(text.substring(0, text.length() - 2)));
		else if (text.endsWith("%"))
			return new QuickSize(Float.parseFloat(text.substring(0, text.length() - 1)), 0);
		else if (text.endsWith("xp"))
			return new QuickSize(100.0f, -Integer.parseInt(text.substring(0, text.length() - 2)));
		else
			return new QuickSize(0.0f, Integer.parseInt(text));
	}

	/**
	 * @param text The text to parse
	 * @return The size represented by the text
	 * @throws NumberFormatException If the size could not be parsed
	 */
	public static QuickSize parseSize(String text) throws NumberFormatException {
		if (text.endsWith("px"))
			return new QuickSize(0.0f, Integer.parseInt(text.substring(0, text.length() - 2)));
		else if (text.endsWith("%"))
			return new QuickSize(Float.parseFloat(text.substring(0, text.length() - 1)), 0);
		else
			return new QuickSize(0.0f, Integer.parseInt(text));
	}

	/** Parses {@link QuickSize}s for Expresso */
	public static class Parser extends NonStructuredParser.Simple<QuickSize> {
		private final boolean isPosition;

		/** @param position Whether this parser should parse positions (potentially with "xp" unit) or sizes */
		public Parser(boolean position) {
			super(TypeTokens.get().of(QuickSize.class));
			this.isPosition = position;
		}

		@Override
		protected <T2 extends QuickSize> T2 parseValue(TypeToken<T2> type, String text) throws ParseException {
			boolean pct, xp;
			int unit;
			if (text.endsWith("%")) {
				unit = 1;
				pct = true;
				xp = false;
			} else if (text.endsWith("px")) {
				unit = 2;
				pct = false;
				xp = false;
			} else if (isPosition && text.endsWith("xp")) {
				unit = 2;
				pct = false;
				xp = true;
			} else {
				pct = xp = false;
				unit = 0;
			}
			String numberStr;
			if (unit > 0)
				numberStr = text.substring(0, text.length() - unit).trim();
			else {
				numberStr = text.trim();
				int lastDig;
				for (lastDig = numberStr.length() - 1; lastDig >= 0; lastDig--) {
					char ch = numberStr.charAt(lastDig);
					if (ch < '0' || ch > '9')
						break;
				}
				lastDig++;
				if (lastDig < numberStr.length())
					throw new ParseException(
						"Unrecognized " + (isPosition ? "position" : "size") + " unit: '" + numberStr.substring(lastDig), lastDig);
			}

			if (pct) {
				float value;
				try {
					value = Float.parseFloat(numberStr);
				} catch (NumberFormatException e) {
					throw new ParseException("Could not parse " + (isPosition ? "position" : "size") + " value: '" + numberStr + "'", 0);
				}
				return (T2) new QuickSize(value, 0);
			} else {
				int value;
				try {
					value = Integer.parseInt(numberStr);
				} catch (NumberFormatException e) {
					throw new ParseException("Could not parse " + (isPosition ? "position" : "size") + " value: '" + numberStr + "'", 0);
				}
				if (xp)
					return (T2) new QuickSize(100.0f, -value);
				else
					return (T2) new QuickSize(0.0f, value);
			}
		}

		@Override
		public String getDescription() {
			return "Simple Size literal";
		}
	}
}
