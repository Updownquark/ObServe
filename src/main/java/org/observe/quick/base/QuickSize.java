package org.observe.quick.base;

import java.text.ParseException;
import java.util.Objects;

import org.observe.ObservableValue;
import org.observe.expresso.NonStructuredParser;

import com.google.common.reflect.TypeToken;

/** Represents the linear size of one dimension of a Quick widget */
public class QuickSize {
	/** Possible size units */
	public enum SizeUnit {
		/** Pixel size unit, the value represents an absolute length in pixels */
		Pixels("px"),
		/** Percent size unit, the value represents a percentage of the parent's length */
		Percent("%");

		/** The name of this unit */
		public final String name;

		private SizeUnit(String name) {
			this.name = name;
		}
	}

	/** The value of this size */
	public final float value;
	/** The unit that this size's value is in */
	public final SizeUnit type;

	/**
	 * @param value The value for the size
	 * @param type The size unit that the value is in
	 */
	public QuickSize(float value, SizeUnit type) {
		this.type = type;
		switch (type) {
		case Pixels:
			this.value = Math.round(value);
			break;
		case Percent:
			this.value = value;
			break;
		default:
			throw new IllegalStateException("Unrecognized size type: " + type);
		}
	}

	/**
	 * @param containerSize The length of the same dimension of the parent container
	 * @return This size, in pixels
	 */
	public int evaluate(int containerSize) {
		switch (type) {
		case Pixels:
			return (int) value;
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
		else if (!(obj instanceof QuickSize))
			return false;
		return value == ((QuickSize) obj).value && type == ((QuickSize) obj).type;
	}

	@Override
	public String toString() {
		return value + type.name;
	}

	/**
	 * @param text The text to parse
	 * @return The size represented by the text
	 * @throws NumberFormatException If the size could not be parsed
	 */
	public static QuickSize parse(String text) throws NumberFormatException {
		SizeUnit type = SizeUnit.Pixels;
		for (SizeUnit u : SizeUnit.values()) {
			if (text.endsWith(u.name)) {
				type = u;
				text = text.substring(0, text.length() - u.name.length());
				break;
			}
		}
		return new QuickSize(Float.parseFloat(text), type);
	}

	/** Parses {@link QuickSize}s for Expresso */
	public static class Parser implements NonStructuredParser {
		@Override
		public <T> ObservableValue<? extends T> parse(TypeToken<T> type, String text) throws ParseException {
			SizeUnit unit = null;
			for (SizeUnit u : SizeUnit.values()) {
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
					throw new ParseException("Unrecognized size unit: '" + numberStr.substring(lastDig), lastDig);
			}

			float value;
			try {
				value = Float.parseFloat(numberStr);
			} catch (NumberFormatException e) {
				throw new ParseException("Could not parse size value: '" + numberStr + "'", 0);
			}
			return (ObservableValue<? extends T>) ObservableValue.of(QuickSize.class, new QuickSize(value, unit));
		}
	}
}
