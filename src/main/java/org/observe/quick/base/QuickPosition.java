package org.observe.quick.base;

import java.text.ParseException;
import java.util.Objects;

import org.observe.ObservableValue;
import org.observe.expresso.NonStructuredParser;

import com.google.common.reflect.TypeToken;

/** Represents the linear offset of one edge of a Quick widget from the same edge of its parent */
public class QuickPosition {
	/** Possible position units */
	public enum PositionUnit {
		/** Pixel size unit, the value represents an absolute offset from the parent's leading edge (top or left) in pixels */
		Pixels("px"),
		/** Percent size unit, the value represents a percentage of the parent's length */
		Percent("%"),
		/** Reverse pixel size unit, the value represents an absolute offset from the parent's trailing edge (bottom or right) in pixels */
		Lexips("xp");

		/** The name of this unit */
		public final String name;

		private PositionUnit(String name) {
			this.name = name;
		}
	}

	/** The value of this position */
	public final float value;
	/** The unit that this position's value is in */
	public final PositionUnit type;

	/**
	 * @param value The value for the position
	 * @param type The position unit that the value is in
	 */
	public QuickPosition(float value, PositionUnit type) {
		this.type = type;
		switch (type) {
		case Pixels:
		case Lexips:
			this.value = Math.round(value);
			break;
		case Percent:
			this.value = value;
			break;
		default:
			throw new IllegalStateException("Unrecognized position type: " + type);
		}
	}

	/**
	 * @param containerSize The length of the same dimension of the parent container
	 * @return This position offset, in pixels
	 */
	public int evaluate(int containerSize) {
		switch (type) {
		case Pixels:
			return (int) value;
		case Lexips:
			return containerSize - (int) value;
		case Percent:
			return Math.round(containerSize * value / 100);
		default:
			throw new IllegalStateException("Unrecognized position type: " + type);
		}
	}

	@Override
	public int hashCode() {
		return Objects.hash(value, type);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this)
			return true;
		else if (!(obj instanceof QuickPosition))
			return false;
		return value == ((QuickPosition) obj).value && type == ((QuickPosition) obj).type;
	}

	@Override
	public String toString() {
		return value + type.name;
	}

	/**
	 * @param text The text to parse
	 * @return The position represented by the text
	 * @throws NumberFormatException If the position could not be parsed
	 */
	public static QuickPosition parse(String text) throws NumberFormatException {
		PositionUnit type = PositionUnit.Pixels;
		for (PositionUnit u : PositionUnit.values()) {
			if (text.endsWith(u.name)) {
				type = u;
				text = text.substring(0, text.length() - u.name.length());
				break;
			}
		}
		return new QuickPosition(Float.parseFloat(text), type);
	}

	/** Parses {@link QuickPosition}s for Expresso */
	public static class Parser implements NonStructuredParser {
		@Override
		public <T> ObservableValue<? extends T> parse(TypeToken<T> type, String text) throws ParseException {
			PositionUnit unit = null;
			for (PositionUnit u : PositionUnit.values()) {
				if (text.endsWith(u.name)) {
					unit = u;
					break;
				}
			}
			String numberStr;
			if (unit != null)
				numberStr = text.substring(0, text.length() - unit.name.length()).trim();
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
					throw new ParseException("Unrecognized position unit: '" + numberStr.substring(lastDig), lastDig);
			}

			float value;
			try {
				value = Float.parseFloat(numberStr);
			} catch (NumberFormatException e) {
				throw new ParseException("Could not parse position value: '" + numberStr + "'", 0);
			}
			return (ObservableValue<? extends T>) ObservableValue.of(QuickPosition.class, new QuickPosition(value, unit));
		}
	}
}
