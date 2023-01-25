package org.observe.quick;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoEnv;
import org.observe.expresso.ExpressoEvaluationException;
import org.observe.expresso.ExpressoParseException;
import org.observe.expresso.ExpressoParser;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.observe.util.TypeTokens;
import org.qommons.config.CustomValueType;
import org.qommons.config.ErrorReporting;
import org.qommons.config.QonfigToolkit;

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

	/** A Qonfig value type to parse QuickSize values */
	public static class SizeValueType implements CustomValueType {
		private final ExpressoParser theParser;

		/** @param parser The Expresso parser to parse size values */
		public SizeValueType(ExpressoParser parser) {
			theParser = parser;
		}

		@Override
		public String getName() {
			return "size";
		}

		@Override
		public ObservableExpression parse(String value, QonfigToolkit tk, ErrorReporting session) {
			SizeUnit unit = null;
			for (SizeUnit u : SizeUnit.values()) {
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
				session.error(e.getMessage(), e);
				return null;
			}
			SizeUnit fUnit = unit == null ? SizeUnit.Pixels : unit;
			return new SizeExpression(valueEx, fUnit, value.length() - valueEx.getExpressionEnd() - unit.name.length());
		}

		@Override
		public boolean isInstance(Object value) {
			return value instanceof ObservableExpression;
		}
	}

	/** An expression representing a Quick size */
	public static class SizeExpression implements ObservableExpression {
		private final ObservableExpression theValue;
		private final SizeUnit theUnit;
		private final int theSpacing;

		/**
		 * @param value The expression representing the numeric value of the size
		 * @param unit The size unit
		 * @param spacing The amount of white space between the value and the unit
		 */
		public SizeExpression(ObservableExpression value, SizeUnit unit, int spacing) {
			theValue = value;
			theUnit = unit;
			theSpacing = spacing;
		}

		@Override
		public int getExpressionOffset() {
			return theValue.getExpressionOffset();
		}

		@Override
		public int getExpressionEnd() {
			return theValue.getExpressionEnd() + theSpacing + theUnit.name.length();
		}

		@Override
		public List<? extends ObservableExpression> getChildren() {
			return Collections.singletonList(theValue);
		}

		@Override
		public <M, MV extends M> ValueContainer<M, MV> evaluateInternal(ModelInstanceType<M, MV> type, ExpressoEnv env)
			throws ExpressoEvaluationException {
			if (type.getModelType() != ModelTypes.Value)
				throw new ExpressoEvaluationException(getExpressionOffset(), getExpressionEnd(), "Only values are supported");
			else if (!(TypeTokens.getRawType(type.getType(0)).isAssignableFrom(QuickSize.class)))
				throw new ExpressoEvaluationException(getExpressionOffset(), getExpressionEnd(),
					"Cannot cast SizeUnit to " + type.getType(0));
			ValueContainer<SettableValue<?>, SettableValue<Double>> valueC = theValue
				.evaluateInternal(ModelTypes.Value.forType(double.class), env);
			return (ValueContainer<M, MV>) valueC.map(ModelTypes.Value.forType(QuickSize.class),
				doubleValue -> doubleValue.transformReversible(QuickSize.class, tx -> tx.cache(false)//
					.map(dbl -> new QuickSize(dbl.floatValue(), theUnit))//
					.withReverse(pos -> Double.valueOf(pos.value))));
		}

		@Override
		public ObservableExpression replaceAll(Function<ObservableExpression, ? extends ObservableExpression> replace) {
			ObservableExpression replaced = replace.apply(this);
			if (replaced != this)
				return replaced;
			replaced = theValue.replaceAll(replace);
			if (replaced != null)
				return new SizeExpression(replaced, theUnit, theSpacing);
			return this;
		}
	}
}
