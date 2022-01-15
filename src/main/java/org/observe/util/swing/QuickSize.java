package org.observe.util.swing;

import java.util.Objects;

public class QuickSize {
	public enum SizeUnit {
		Pixels("px"), Percent("%");

		public final String name;

		private SizeUnit(String name) {
			this.name = name;
		}
	}

	public final float value;
	public final SizeUnit type;

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

	public static QuickSize parse(String pos) throws NumberFormatException {
		SizeUnit type = SizeUnit.Pixels;
		for (SizeUnit u : SizeUnit.values()) {
			if (pos.endsWith(u.name)) {
				type = u;
				pos = pos.substring(0, pos.length() - u.name.length());
				break;
			}
		}
		return new QuickSize(Float.parseFloat(pos), type);
	}
}
