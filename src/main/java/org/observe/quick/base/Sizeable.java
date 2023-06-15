package org.observe.quick.base;

import java.util.function.Function;

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
import org.observe.expresso.qonfig.ExAddOn;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.QonfigExpression;
import org.observe.expresso.TypeConversionException;
import org.observe.util.TypeTokens;
import org.qommons.Ternian;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigElement.QonfigValue;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.io.LocatedFilePosition;

public abstract class Sizeable extends ExAddOn.Abstract<ExElement> {
	public static abstract class Def<S extends Sizeable> extends ExAddOn.Def.Abstract<ExElement, S> {
		private final Ternian isVertical;
		private CompiledModelValue<SettableValue<?>, SettableValue<QuickSize>> theSize;
		private CompiledModelValue<SettableValue<?>, SettableValue<QuickSize>> theMinimum;
		private CompiledModelValue<SettableValue<?>, SettableValue<QuickSize>> thePreferred;
		private CompiledModelValue<SettableValue<?>, SettableValue<QuickSize>> theMaximum;

		protected Def(Ternian vertical, QonfigAddOn type, ExElement.Def<? extends ExElement> element) {
			super(type, element);
			isVertical = vertical;
		}

		public Ternian isVertical() {
			return isVertical;
		}

		public CompiledModelValue<SettableValue<?>, SettableValue<QuickSize>> getSize() {
			return theSize;
		}

		public CompiledModelValue<SettableValue<?>, SettableValue<QuickSize>> getMinimum() {
			return theMinimum;
		}

		public CompiledModelValue<SettableValue<?>, SettableValue<QuickSize>> getPreferred() {
			return thePreferred;
		}

		public CompiledModelValue<SettableValue<?>, SettableValue<QuickSize>> getMaximum() {
			return theMaximum;
		}

		@Override
		public void update(ExpressoQIS session, ExElement.Def<?> element) throws QonfigInterpretationException {
			super.update(session, element);
			switch (isVertical) {
			case TRUE:
				theSize = parseSize(session.getAttributeQV("height"), session, false);
				theMinimum = parseSize(session.getAttributeQV("min-height"), session, false);
				thePreferred = parseSize(session.getAttributeQV("pref-height"), session, false);
				theMaximum = parseSize(session.getAttributeQV("max-height"), session, false);
				break;
			case FALSE:
				theSize = parseSize(session.getAttributeQV("width"), session, false);
				theMinimum = parseSize(session.getAttributeQV("min-width"), session, false);
				thePreferred = parseSize(session.getAttributeQV("pref-width"), session, false);
				theMaximum = parseSize(session.getAttributeQV("max-width"), session, false);
				break;
			default:
				theSize = parseSize(session.getAttributeQV("size"), session, false);
				theMinimum = parseSize(session.getAttributeQV("min-size"), session, false);
				thePreferred = parseSize(session.getAttributeQV("pref-size"), session, false);
				theMaximum = parseSize(session.getAttributeQV("max-size"), session, false);
				break;
			}
		}

		public static class Vertical extends Def<Sizeable.Vertical> {
			public Vertical(QonfigAddOn type, ExElement.Def<?> element) {
				super(Ternian.TRUE, type, element);
			}

			@Override
			public Interpreted.Vertical interpret(ExElement.Interpreted<?> element) {
				return new Interpreted.Vertical(this, element);
			}
		}

		public static class Horizontal extends Def<Sizeable.Horizontal> {
			public Horizontal(QonfigAddOn type, ExElement.Def<?> element) {
				super(Ternian.FALSE, type, element);
			}

			@Override
			public Interpreted.Horizontal interpret(ExElement.Interpreted<?> element) {
				return new Interpreted.Horizontal(this, element);
			}
		}

		public static class Generic extends Def<Sizeable.Generic> {
			public Generic(QonfigAddOn type, ExElement.Def<?> element) {
				super(Ternian.NONE, type, element);
			}

			@Override
			public Interpreted.Generic interpret(ExElement.Interpreted<?> element) {
				return new Interpreted.Generic(this, element);
			}
		}
	}

	public abstract static class Interpreted<S extends Sizeable> extends ExAddOn.Interpreted.Abstract<ExElement, S> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<QuickSize>> theSize;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<QuickSize>> theMaximum;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<QuickSize>> thePreferred;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<QuickSize>> theMinimum;

		protected Interpreted(Def<S> definition, ExElement.Interpreted<?> element) {
			super(definition, element);
		}

		@Override
		public Def<S> getDefinition() {
			return (Def<S>) super.getDefinition();
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<QuickSize>> getSize() {
			return theSize;
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<QuickSize>> getMaximum() {
			return theMaximum;
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<QuickSize>> getPreferred() {
			return thePreferred;
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<QuickSize>> getMinimum() {
			return theMinimum;
		}

		@Override
		public void update(InterpretedModelSet models) throws ExpressoInterpretationException {
			theSize = getDefinition().getSize() == null ? null : getDefinition().getSize().createSynthesizer().interpret();
			theMinimum = getDefinition().getMinimum() == null ? null : getDefinition().getMinimum().createSynthesizer().interpret();
			thePreferred = getDefinition().getPreferred() == null ? null : getDefinition().getPreferred().createSynthesizer().interpret();
			theMaximum = getDefinition().getMaximum() == null ? null : getDefinition().getMaximum().createSynthesizer().interpret();
		}

		public static class Vertical extends Interpreted<Sizeable.Vertical> {
			public Vertical(Def.Vertical definition, ExElement.Interpreted<?> element) {
				super(definition, element);
			}

			@Override
			public Def.Vertical getDefinition() {
				return (Def.Vertical) super.getDefinition();
			}

			@Override
			public Sizeable.Vertical create(ExElement element) {
				return new Sizeable.Vertical(this, element);
			}
		}

		public static class Horizontal extends Interpreted<Sizeable.Horizontal> {
			public Horizontal(Def.Horizontal definition, ExElement.Interpreted<?> element) {
				super(definition, element);
			}

			@Override
			public Def.Horizontal getDefinition() {
				return (Def.Horizontal) super.getDefinition();
			}

			@Override
			public Sizeable.Horizontal create(ExElement element) {
				return new Sizeable.Horizontal(this, element);
			}
		}

		public static class Generic extends Interpreted<Sizeable.Generic> {
			public Generic(Def.Generic definition, ExElement.Interpreted<?> element) {
				super(definition, element);
			}

			@Override
			public Def.Generic getDefinition() {
				return (Def.Generic) super.getDefinition();
			}

			@Override
			public Sizeable.Generic create(ExElement element) {
				return new Sizeable.Generic(this, element);
			}
		}
	}

	private final SettableValue<SettableValue<QuickSize>> theSize;
	private final SettableValue<SettableValue<QuickSize>> theMinimum;
	private final SettableValue<SettableValue<QuickSize>> thePreferred;
	private final SettableValue<SettableValue<QuickSize>> theMaximum;

	protected Sizeable(Interpreted interpreted, ExElement element) {
		super(interpreted, element);
		theSize = SettableValue
			.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<QuickSize>> parameterized(QuickSize.class)).build();
		theMinimum = SettableValue.build(theSize.getType()).build();
		thePreferred = SettableValue.build(theSize.getType()).build();
		theMaximum = SettableValue.build(theSize.getType()).build();
	}

	public SettableValue<QuickSize> getSize() {
		return SettableValue.flatten(theSize);
	}

	public SettableValue<QuickSize> getMinimum() {
		return SettableValue.flatten(theMinimum);
	}

	public SettableValue<QuickSize> getPreferred() {
		return SettableValue.flatten(thePreferred);
	}

	public SettableValue<QuickSize> getMaximum() {
		return SettableValue.flatten(theMaximum);
	}

	@Override
	public void update(ExAddOn.Interpreted<?, ?> interpreted, ModelSetInstance models) throws ModelInstantiationException {
		Sizeable.Interpreted<?> myInterpreted = (Sizeable.Interpreted<?>) interpreted;
		theSize.set(myInterpreted.getSize() == null ? null : myInterpreted.getSize().get(models), null);
		theMinimum.set(myInterpreted.getMinimum() == null ? null : myInterpreted.getMinimum().get(models), null);
		thePreferred.set(myInterpreted.getPreferred() == null ? null : myInterpreted.getPreferred().get(models), null);
		theMaximum.set(myInterpreted.getMaximum() == null ? null : myInterpreted.getMaximum().get(models), null);
	}

	public Observable<ObservableValueEvent<QuickSize>> changes() {
		return Observable.or(getSize().noInitChanges(), getMinimum().noInitChanges(), getPreferred().noInitChanges(),
			getMaximum().noInitChanges());
	}

	/**
	 * Parses a position in Quick
	 *
	 * @param value The expression to parse
	 * @param session The session in which to parse the expression
	 * @return The ModelValueSynth to produce the position value
	 * @throws QonfigInterpretationException If the position could not be parsed
	 */
	public static CompiledModelValue<SettableValue<?>, SettableValue<QuickSize>> parseSize(QonfigValue value, ExpressoQIS session,
		boolean position)
			throws QonfigInterpretationException {
		if (value == null)
			return null;
		else if (!(value.value instanceof QonfigExpression))
			throw new IllegalArgumentException("Not an expression");
		QonfigExpression expression = (QonfigExpression) value.value;
		boolean pct, xp;
		int unit;
		if (expression.text.endsWith("%") && Character.isWhitespace(expression.text.charAt(expression.text.length() - 2))) {
			unit = 1;
			pct = true;
			xp = false;
		} else if (expression.text.endsWith("px") && Character.isWhitespace(expression.text.charAt(expression.text.length() - 3))) {
			unit = 2;
			pct = false;
			xp = false;
		} else if (position && expression.text.endsWith("xp")
			&& Character.isWhitespace(expression.text.charAt(expression.text.length() - 3))) {
			unit = 2;
			pct = false;
			xp = true;
		} else {
			pct = xp = false;
			unit = 0;
		}
		ObservableExpression parsed;
		try {
			if (unit > 0)
				parsed = session.getExpressoParser().parse(expression.text.substring(0, expression.text.length() - unit).trim());
			else
				parsed = session.getExpressoParser().parse(expression.text);
		} catch (ExpressoParseException e) {
			throw new QonfigInterpretationException("Could not parse position expression: " + expression,
				value.position == null ? null : new LocatedFilePosition(value.fileLocation, value.position.getPosition(e.getErrorOffset())),
					e.getErrorLength(), e);
		}
		boolean fPct = pct, fXp = xp;
		int fUnit = unit;
		return CompiledModelValue.of(expression::toString, ModelTypes.Value, () -> {
			ModelValueSynth<SettableValue<?>, SettableValue<QuickSize>> positionValue;
			if (fUnit > 0) {
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
				Function<Double, QuickSize> map;
				Function<QuickSize, Double> reverse;
				if (fPct) {
					map = v -> v == null ? null : new QuickSize(v.floatValue(), 0);
					reverse = s -> s == null ? null : Double.valueOf(s.percent);
				} else if (fXp) {
					map = v -> v == null ? null : new QuickSize(100.0f, -Math.round(v.floatValue()));
					reverse = s -> s == null ? null : Double.valueOf(-s.pixels);
				} else {
					map = v -> v == null ? null : new QuickSize(0.0f, Math.round(v.floatValue()));
					reverse = s -> s == null ? null : Double.valueOf(s.pixels);
				}
				positionValue = ModelValueSynth.of(ModelTypes.Value.forType(QuickSize.class), msi -> {
					SettableValue<Double> numV = num.get(msi);
					return numV.transformReversible(QuickSize.class, tx -> tx//
						.map(map)//
						.replaceSource(reverse, rev -> rev.allowInexactReverse(true)));
				});
			} else {
				try {
					positionValue = parsed.evaluate(ModelTypes.Value.forType(QuickSize.class), session.getExpressoEnv(), 0);
				} catch (ExpressoEvaluationException e1) {
					// If it doesn't parse as a position, try parsing as a number.
					try {
						positionValue = parsed.evaluate(ModelTypes.Value.forType(int.class), session.getExpressoEnv(), 0)//
							.map(ModelTypes.Value.forType(QuickSize.class), v -> v.transformReversible(QuickSize.class,
								tx -> tx.map(d -> new QuickSize(0.0f, d)).withReverse(pos -> pos.pixels)));
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

	public static class Vertical extends Sizeable {
		public Vertical(Interpreted.Vertical interpreted, ExElement element) {
			super(interpreted, element);
		}
	}

	public static class Horizontal extends Sizeable {
		public Horizontal(Interpreted.Horizontal interpreted, ExElement element) {
			super(interpreted, element);
		}
	}

	public static class Generic extends Sizeable {
		public Generic(Interpreted.Generic interpreted, ExElement element) {
			super(interpreted, element);
		}
	}
}
