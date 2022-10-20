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
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.observe.util.TypeTokens;
import org.qommons.collect.BetterList;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.config.CustomValueType;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigParseSession;
import org.qommons.config.QonfigToolkit;

import com.google.common.reflect.TypeToken;

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

	public static class PositionValueType implements CustomValueType {
		private final ExpressoParser theParser;

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
		public <P1, P2, P3, T> MethodFinder<P1, P2, P3, T> findMethod(TypeToken<T> targetType, ExpressoEnv env)
			throws QonfigInterpretationException {
			throw new QonfigInterpretationException(StdMsg.UNSUPPORTED_OPERATION);
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
			return (ValueContainer<M, MV>) new ValueContainer<SettableValue<?>, SettableValue<QuickPosition>>() {
				@Override
				public ModelInstanceType<SettableValue<?>, SettableValue<QuickPosition>> getType() {
					return ModelTypes.Value.forType(QuickPosition.class);
				}

				@Override
				public SettableValue<QuickPosition> get(ModelSetInstance models) {
					return valueC.get(models).transformReversible(QuickPosition.class, tx -> tx.cache(false)//
						.map(dbl -> new QuickPosition(dbl.floatValue(), theUnit))//
						.withReverse(pos -> Double.valueOf(pos.value)));
				}

				@Override
				public BetterList<ValueContainer<?, ?>> getCores() {
					return BetterList.of(this);
				}
			};
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
