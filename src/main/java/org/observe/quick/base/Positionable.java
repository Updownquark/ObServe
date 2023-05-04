package org.observe.quick.base;

import org.observe.Observable;
import org.observe.ObservableValueEvent;
import org.observe.SettableValue;
import org.observe.expresso.ExpressoEvaluationException;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ExpressoParseException;
import org.observe.expresso.ExpressoQIS;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet.CompiledModelValue;
import org.observe.expresso.ObservableModelSet.InterpretedModelSet;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueSynth;
import org.observe.expresso.QonfigExpression;
import org.observe.expresso.TypeConversionException;
import org.observe.quick.QuickAddOn;
import org.observe.quick.QuickElement;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigElement.QonfigValue;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.io.LocatedFilePosition;

public abstract class Positionable extends QuickAddOn.Abstract<QuickElement> {
	public static abstract class Def<P extends Positionable> extends QuickAddOn.Def.Abstract<QuickElement, P> {
		private final boolean isVertical;
		private CompiledModelValue<SettableValue<?>, SettableValue<QuickPosition>> theLeading;
		private CompiledModelValue<SettableValue<?>, SettableValue<QuickPosition>> theCenter;
		private CompiledModelValue<SettableValue<?>, SettableValue<QuickPosition>> theTrailing;

		protected Def(boolean vertical, QonfigAddOn type, QuickElement.Def<? extends QuickElement> element) {
			super(type, element);
			isVertical = vertical;
		}

		public boolean isVertical() {
			return isVertical;
		}

		public CompiledModelValue<SettableValue<?>, SettableValue<QuickPosition>> getLeading() {
			return theLeading;
		}

		public CompiledModelValue<SettableValue<?>, SettableValue<QuickPosition>> getCenter() {
			return theCenter;
		}

		public CompiledModelValue<SettableValue<?>, SettableValue<QuickPosition>> getTrailing() {
			return theTrailing;
		}

		@Override
		public Def<P> update(ExpressoQIS session) throws QonfigInterpretationException {
			super.update(session);
			theCenter = parsePosition(session.getAttributeQV("center"), session);
			if (isVertical) {
				theLeading = parsePosition(session.getAttributeQV("top"), session);
				theTrailing = parsePosition(session.getAttributeQV("bottom"), session);
			} else {
				theLeading = parsePosition(session.getAttributeQV("left"), session);
				theTrailing = parsePosition(session.getAttributeQV("right"), session);
			}
			return this;
		}

		public static class Vertical extends Def<Positionable.Vertical> {
			public Vertical(QonfigAddOn type, QuickElement.Def<?> element) {
				super(true, type, element);
			}

			@Override
			public Interpreted.Vertical interpret(QuickElement.Interpreted<?> element) {
				return new Interpreted.Vertical(this, element);
			}
		}

		public static class Horizontal extends Def<Positionable.Horizontal> {
			public Horizontal(QonfigAddOn type, QuickElement.Def<?> element) {
				super(true, type, element);
			}

			@Override
			public Interpreted.Horizontal interpret(QuickElement.Interpreted<?> element) {
				return new Interpreted.Horizontal(this, element);
			}
		}
	}

	public static abstract class Interpreted<P extends Positionable> extends QuickAddOn.Interpreted.Abstract<QuickElement, P> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<QuickPosition>> theLeading;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<QuickPosition>> theCenter;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<QuickPosition>> theTrailing;

		protected Interpreted(Def<P> definition, QuickElement.Interpreted<?> element) {
			super(definition, element);
		}

		@Override
		public Def<P> getDefinition() {
			return (Def<P>) super.getDefinition();
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<QuickPosition>> getLeading() {
			return theLeading;
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<QuickPosition>> getCenter() {
			return theCenter;
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<QuickPosition>> getTrailing() {
			return theTrailing;
		}

		@Override
		public Interpreted<P> update(InterpretedModelSet models) throws ExpressoInterpretationException {
			theLeading = getDefinition().getLeading() == null ? null : getDefinition().getLeading().createSynthesizer().interpret();
			theCenter = getDefinition().getCenter() == null ? null : getDefinition().getCenter().createSynthesizer().interpret();
			theTrailing = getDefinition().getTrailing() == null ? null : getDefinition().getTrailing().createSynthesizer().interpret();
			return this;
		}

		public static class Vertical extends Interpreted<Positionable.Vertical> {
			public Vertical(Def.Vertical definition, QuickElement.Interpreted<?> element) {
				super(definition, element);
			}

			@Override
			public Def.Vertical getDefinition() {
				return (Def.Vertical) super.getDefinition();
			}

			@Override
			public Positionable.Vertical create(QuickElement element) {
				return new Positionable.Vertical(this, element);
			}
		}

		public static class Horizontal extends Interpreted<Positionable.Horizontal> {
			public Horizontal(Def.Horizontal definition, QuickElement.Interpreted<?> element) {
				super(definition, element);
			}

			@Override
			public Def.Horizontal getDefinition() {
				return (Def.Horizontal) super.getDefinition();
			}

			@Override
			public Positionable.Horizontal create(QuickElement element) {
				return new Positionable.Horizontal(this, element);
			}
		}
	}

	private final SettableValue<SettableValue<QuickPosition>> theLeading;
	private final SettableValue<SettableValue<QuickPosition>> theCenter;
	private final SettableValue<SettableValue<QuickPosition>> theTrailing;

	protected Positionable(Interpreted<?> interpreted, QuickElement element) {
		super(interpreted, element);
		theLeading = SettableValue
			.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<QuickPosition>> parameterized(QuickPosition.class)).build();
		theCenter = SettableValue.build(theLeading.getType()).build();
		theTrailing = SettableValue.build(theLeading.getType()).build();
	}

	@Override
	public Interpreted<?> getInterpreted() {
		return (Interpreted<?>) super.getInterpreted();
	}

	public SettableValue<QuickPosition> getLeading() {
		return SettableValue.flatten(theLeading);
	}

	public SettableValue<QuickPosition> getCenter() {
		return SettableValue.flatten(theCenter);
	}

	public SettableValue<QuickPosition> getTrailing() {
		return SettableValue.flatten(theTrailing);
	}

	@Override
	public Positionable update(ModelSetInstance models) throws ModelInstantiationException {
		theLeading.set(getInterpreted().getLeading() == null ? null : getInterpreted().getLeading().get(models), null);
		theCenter.set(getInterpreted().getCenter() == null ? null : getInterpreted().getCenter().get(models), null);
		theTrailing.set(getInterpreted().getTrailing() == null ? null : getInterpreted().getTrailing().get(models), null);
		return this;
	}

	public Observable<ObservableValueEvent<QuickPosition>> changes() {
		return Observable.or(getLeading().noInitChanges(), getCenter().noInitChanges(), getTrailing().noInitChanges());
	}

	public static class Vertical extends Positionable {
		public Vertical(Interpreted.Vertical interpreted, QuickElement element) {
			super(interpreted, element);
		}

		@Override
		public Interpreted.Vertical getInterpreted() {
			return (Interpreted.Vertical) super.getInterpreted();
		}
	}

	public static class Horizontal extends Positionable {
		public Horizontal(Interpreted.Horizontal interpreted, QuickElement element) {
			super(interpreted, element);
		}

		@Override
		public Interpreted.Horizontal getInterpreted() {
			return (Interpreted.Horizontal) super.getInterpreted();
		}
	}

	/**
	 * Parses a position in Quick
	 *
	 * @param value The expression to parse
	 * @param session The session in which to parse the expression
	 * @return The ModelValueSynth to produce the position value
	 * @throws QonfigInterpretationException If the position could not be parsed
	 */
	public static CompiledModelValue<SettableValue<?>, SettableValue<QuickPosition>> parsePosition(QonfigValue value, ExpressoQIS session)
		throws QonfigInterpretationException {
		if (value == null)
			return null;
		else if (!(value.value instanceof QonfigExpression))
			throw new IllegalArgumentException("Not an expression");
		QonfigExpression expression = (QonfigExpression) value.value;
		QuickPosition.PositionUnit unit = null;
		for (QuickPosition.PositionUnit u : QuickPosition.PositionUnit.values()) {
			if (expression.text.length() > u.name.length() && expression.text.endsWith(u.name)
				&& Character.isWhitespace(expression.text.charAt(expression.text.length() - u.name.length() - 1))) {
				unit = u;
				break;
			}
		}
		ObservableExpression parsed;
		try {
			if (unit != null)
				parsed = session.getExpressoParser().parse(expression.text.substring(0, expression.text.length() - unit.name.length()));
			else
				parsed = session.getExpressoParser().parse(expression.text);
		} catch (ExpressoParseException e) {
			throw new QonfigInterpretationException("Could not parse position expression: " + expression,
				value.position == null ? null : new LocatedFilePosition(value.fileLocation, value.position.getPosition(e.getErrorOffset())),
					e.getErrorLength(), e);
		}
		QuickPosition.PositionUnit fUnit = unit;
		return CompiledModelValue.of(expression::toString, ModelTypes.Value, () -> {
			ModelValueSynth<SettableValue<?>, SettableValue<QuickPosition>> positionValue;
			if (fUnit != null) {
				ModelValueSynth<SettableValue<?>, SettableValue<Double>> num;
				try {
					num = parsed.evaluate(ModelTypes.Value.forType(double.class), session.getExpressoEnv(), 0);
				} catch (ExpressoEvaluationException e) {
					throw new ExpressoInterpretationException(e.getMessage(),
						new LocatedFilePosition(value.fileLocation, value.position.getPosition(e.getErrorOffset())), e.getErrorLength(), e);
				} catch (TypeConversionException e) {
					throw new ExpressoInterpretationException(e.getMessage(),
						new LocatedFilePosition(value.fileLocation, value.position.getPosition(0)), value.text.length(), e);
				}
				positionValue = ModelValueSynth.of(ModelTypes.Value.forType(QuickPosition.class), msi -> {
					SettableValue<Double> numV = num.get(msi);
					return numV.transformReversible(QuickPosition.class, tx -> tx//
						.map(n -> new QuickPosition(n.floatValue(), fUnit))//
						.replaceSource(p -> (double) p.value, rev -> rev//
							.allowInexactReverse(true).rejectWith(
								p -> p.type == fUnit ? null : "Only positions with the same unit as the source (" + fUnit + ") can be set")//
							)//
						);
				});
			} else {
				try {
					positionValue = parsed.evaluate(ModelTypes.Value.forType(QuickPosition.class), session.getExpressoEnv(), 0);
				} catch (ExpressoEvaluationException e1) {
					// If it doesn't parse as a position, try parsing as a number.
					try {
						positionValue = parsed.evaluate(ModelTypes.Value.forType(int.class), session.getExpressoEnv(), 0)//
							.map(ModelTypes.Value.forType(QuickPosition.class),
								v -> v.transformReversible(QuickPosition.class,
									tx -> tx.map(d -> new QuickPosition(d, QuickPosition.PositionUnit.Pixels))
									.withReverse(pos -> Math.round(pos.value))));
					} catch (ExpressoEvaluationException e2) {
						throw new ExpressoInterpretationException(e2.getMessage(),
							new LocatedFilePosition(value.fileLocation, value.position.getPosition(e2.getErrorOffset())),
							e1.getErrorLength(), e1);
					} catch (TypeConversionException e2) {
						throw new ExpressoInterpretationException(e2.getMessage(),
							new LocatedFilePosition(value.fileLocation, value.position.getPosition(0)), value.text.length(), e2);
					}
				} catch (TypeConversionException e) {
					throw new ExpressoInterpretationException(e.getMessage(),
						new LocatedFilePosition(value.fileLocation, value.position.getPosition(0)), value.text.length(), e);
				}
			}
			return positionValue;
		});
	}
}
