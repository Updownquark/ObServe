package org.observe.util.swing;

import java.util.Objects;

import org.observe.SettableValue;
import org.observe.expresso.Expression.ExpressoParseException;
import org.observe.expresso.ExpressoParser;
import org.observe.expresso.ObservableExpression;
import org.observe.util.ClassView;
import org.observe.util.ModelType.ModelInstanceType;
import org.observe.util.ModelTypes;
import org.observe.util.ObservableModelSet;
import org.observe.util.ObservableModelSet.ModelSetInstance;
import org.observe.util.ObservableModelSet.ValueContainer;
import org.observe.util.TypeTokens;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.config.CustomValueType;
import org.qommons.config.QonfigInterpreter.QonfigInterpretationException;
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
			return new ObservableExpression() {
				@Override
				public <P1, P2, P3, T> MethodFinder<P1, P2, P3, T> findMethod(TypeToken<T> targetType, ObservableModelSet models,
					ClassView classView) throws QonfigInterpretationException {
					throw new QonfigInterpretationException(StdMsg.UNSUPPORTED_OPERATION);
				}

				@Override
				public <M, MV extends M> ValueContainer<M, MV> evaluateInternal(ModelInstanceType<M, MV> type, ObservableModelSet models,
					ClassView classView) throws QonfigInterpretationException {
					if (type.getModelType() != ModelTypes.Value)
						throw new QonfigInterpretationException("Only values are supported");
					else if (!(TypeTokens.getRawType(type.getType(0)).isAssignableFrom(QuickPosition.class)))
						throw new QonfigInterpretationException("Cannot cast QuickPosition to " + type.getType(0));
					ValueContainer<SettableValue, SettableValue<Double>> valueC = valueEx
						.evaluateInternal(ModelTypes.Value.forType(double.class), models, classView);
					return (ValueContainer<M, MV>) new ValueContainer<SettableValue, SettableValue<QuickPosition>>() {
						@Override
						public ModelInstanceType<SettableValue, SettableValue<QuickPosition>> getType() {
							return ModelTypes.Value.forType(QuickPosition.class);
						}

						@Override
						public SettableValue<QuickPosition> get(ModelSetInstance models) {
							return valueC.get(models).transformReversible(QuickPosition.class, tx -> tx.cache(false)//
								.map(dbl -> new QuickPosition(dbl.floatValue(), fUnit))//
								.withReverse(pos -> Double.valueOf(pos.value)));
						}
					};
				}
			};
		}

		@Override
		public boolean isInstance(Object value) {
			return value instanceof ObservableExpression;
		}
	}
}
