package org.observe.quick;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import org.observe.SettableValue;
import org.observe.expresso.Expression.ExpressoParseException;
import org.observe.expresso.ExpressoEnv;
import org.observe.expresso.ExpressoParser;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.observe.util.TypeTokens;
import org.qommons.config.CustomValueType;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigParseSession;
import org.qommons.config.QonfigToolkit;

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
	 * @return The size represented by the text
	 * @throws NumberFormatException If the size could not be parsed
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

	/** A Qonfig value type to parse QuickPosition values */
	public static class PositionValueType implements CustomValueType {
		private final ExpressoParser theParser;

		/** @param parser The Expresso parser to parse position values */
		public PositionValueType(ExpressoParser parser) {
			theParser = parser;
		}

		@Override
		public String getName() {
			return "position";
		}

		@Override
		public ObservableExpression parse(String value, QonfigToolkit tk, QonfigParseSession session) {
			PositionUnit unit = null;
			for (PositionUnit u : PositionUnit.values()) {
				if (value.endsWith(u.name)) {
					unit = u;
					break;
				}
			}
			if (unit != null)
				value = value.substring(0, value.length() - unit.name.length()).trim();
			ObservableExpression valueEx;
			try {
				valueEx = theParser.parse(value);
			} catch (ExpressoParseException e) {
				session.withError(e.getMessage(), e);
				return null;
			}
			PositionUnit fUnit = unit == null ? PositionUnit.Pixels : unit;
			return new PositionExpression(valueEx, fUnit);
		}

		@Override
		public boolean isInstance(Object value) {
			return value instanceof ObservableExpression;
		}
	}

	/** An expression representing a Quick position */
	public static class PositionExpression implements ObservableExpression {
		private final ObservableExpression theValue;
		private final PositionUnit theUnit;

		/**
		 * @param value The expression representing the numeric value of the size
		 * @param unit The size unit
		 */
		public PositionExpression(ObservableExpression value, PositionUnit unit) {
			theValue = value;
			theUnit = unit;
		}

		@Override
		public List<? extends ObservableExpression> getChildren() {
			return Collections.singletonList(theValue);
		}

		@Override
		public <M, MV extends M> ValueContainer<M, MV> evaluateInternal(ModelInstanceType<M, MV> type, ExpressoEnv env)
			throws QonfigInterpretationException {
			if (type.getModelType() != ModelTypes.Value)
				throw new QonfigInterpretationException("Only values are supported");
			else if (!(TypeTokens.getRawType(type.getType(0)).isAssignableFrom(QuickSize.class)))
				throw new QonfigInterpretationException("Cannot cast SizeUnit to " + type.getType(0));
			ValueContainer<SettableValue<?>, SettableValue<Double>> valueC = theValue
				.evaluateInternal(ModelTypes.Value.forType(double.class), env);
			return (ValueContainer<M, MV>) valueC.map(ModelTypes.Value.forType(QuickPosition.class),
				doubleValue -> doubleValue.transformReversible(QuickPosition.class, tx -> tx.cache(false)//
					.map(dbl -> new QuickPosition(dbl.floatValue(), theUnit))//
					.withReverse(pos -> Double.valueOf(pos.value))));
		}

		@Override
		public ObservableExpression replaceAll(Function<ObservableExpression, ? extends ObservableExpression> replace) {
			ObservableExpression replaced = replace.apply(this);
			if (replaced != this)
				return replaced;
			replaced = theValue.replaceAll(replace);
			if (replaced != null)
				return new PositionExpression(replaced, theUnit);
			return this;
		}
	}
}
