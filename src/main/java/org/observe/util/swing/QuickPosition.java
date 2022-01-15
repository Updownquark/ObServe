package org.observe.util.swing;

import java.util.Objects;

public class QuickPosition {
	public enum PositionUnit {
		Pixels("px"), Percent("%"), Lexips("xp");

		public final String name;

		private PositionUnit(String name) {
			this.name = name;
		}
	}

	public final float value;
	public final PositionUnit type;

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

	public static QuickPosition parse(String pos) throws NumberFormatException {
		PositionUnit type = PositionUnit.Pixels;
		for (PositionUnit u : PositionUnit.values()) {
			if (pos.endsWith(u.name)) {
				type = u;
				pos = pos.substring(0, pos.length() - u.name.length());
				break;
			}
		}
		return new QuickPosition(Float.parseFloat(pos), type);
	}
}
