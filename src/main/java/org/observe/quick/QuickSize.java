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

	public static class SizeValueType implements CustomValueType {
		private final ExpressoParser theParser;

		public SizeValueType(ExpressoParser parser) {
			theParser = parser;
		}

		@Override
		public String getName() {
			return "size";
		}

		@Override
		public ObservableExpression parse(String value, QonfigToolkit tk, QonfigParseSession session) {
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
				session.withError(e.getMessage(), e);
				return null;
			}
			SizeUnit fUnit = unit == null ? SizeUnit.Pixels : unit;
			return new SizeExpression(valueEx, fUnit);
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

		/**
		 * @param value The expression representing the numeric value of the size
		 * @param unit The size unit
		 */
		public SizeExpression(ObservableExpression value, SizeUnit unit) {
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
				return new SizeExpression(replaced, theUnit);
			return this;
		}
	}
}
