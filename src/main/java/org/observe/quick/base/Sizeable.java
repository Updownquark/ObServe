package org.observe.quick.base;

import java.util.function.Function;

import org.observe.Observable;
import org.observe.ObservableValueEvent;
import org.observe.SettableValue;
import org.observe.expresso.ExpressoEvaluationException;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ExpressoParseException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet.CompiledModelValue;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.TypeConversionException;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ElementTypeTraceability;
import org.observe.expresso.qonfig.ElementTypeTraceability.SingleTypeTraceability;
import org.observe.expresso.qonfig.ExAddOn;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigExpression;
import org.observe.util.TypeTokens;
import org.qommons.Ternian;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigElement.QonfigValue;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.io.LocatedFilePosition;

public abstract class Sizeable extends ExAddOn.Abstract<ExElement> {
	public static abstract class Def<S extends Sizeable> extends ExAddOn.Def.Abstract<ExElement, S> {
		private final Ternian isVertical;
		private CompiledExpression theSize;
		private CompiledExpression theMinimum;
		private CompiledExpression thePreferred;
		private CompiledExpression theMaximum;

		protected Def(Ternian vertical, QonfigAddOn type, ExElement.Def<? extends ExElement> element) {
			super(type, element);
			isVertical = vertical;
		}

		public Ternian isVertical() {
			return isVertical;
		}

		public CompiledExpression getSize() {
			return theSize;
		}

		public CompiledExpression getMinimum() {
			return theMinimum;
		}

		public CompiledExpression getPreferred() {
			return thePreferred;
		}

		public CompiledExpression getMaximum() {
			return theMaximum;
		}

		@Override
		public void update(ExpressoQIS session, ExElement.Def<?> element) throws QonfigInterpretationException {
			super.update(session, element);
			switch (isVertical) {
			case TRUE:
				theSize = session.getAttributeExpression("height");
				theMinimum = session.getAttributeExpression("min-height");
				thePreferred = session.getAttributeExpression("pref-height");
				theMaximum = session.getAttributeExpression("max-height");
				break;
			case FALSE:
				theSize = session.getAttributeExpression("width");
				theMinimum = session.getAttributeExpression("min-width");
				thePreferred = session.getAttributeExpression("pref-width");
				theMaximum = session.getAttributeExpression("max-width");
				break;
			default:
				theSize = session.getAttributeExpression("size");
				theMinimum = session.getAttributeExpression("min-size");
				thePreferred = session.getAttributeExpression("pref-size");
				theMaximum = session.getAttributeExpression("max-size");
				break;
			}
		}

		public static class Vertical extends Def<Sizeable.Vertical> {
			private static final SingleTypeTraceability<ExElement, ExElement.Interpreted<?>, ExElement.Def<?>> TRACEABILITY = ElementTypeTraceability
				.<ExElement, Sizeable, Interpreted<?>, Def<?>> buildAddOn(QuickBaseInterpretation.NAME, QuickBaseInterpretation.VERSION,
					"v-sizeable", Def.class, Interpreted.class, Sizeable.class)//
				.withAddOnAttribute("height", Def::getSize, Interpreted::getSize)//
				.withAddOnAttribute("min-height", Def::getMinimum, Interpreted::getMinimum)//
				.withAddOnAttribute("pref-height", Def::getPreferred, Interpreted::getPreferred)//
				.withAddOnAttribute("max-height", Def::getMaximum, Interpreted::getMaximum)//
				.build();

			public Vertical(QonfigAddOn type, ExElement.Def<?> element) {
				super(Ternian.TRUE, type, element);
			}

			@Override
			public void update(ExpressoQIS session, ExElement.Def<?> element) throws QonfigInterpretationException {
				element.withTraceability(TRACEABILITY.validate(session.getFocusType(), session.reporting()));
				super.update(session, element);
			}

			@Override
			public Interpreted.Vertical interpret(ExElement.Interpreted<?> element) {
				return new Interpreted.Vertical(this, element);
			}
		}

		public static class Horizontal extends Def<Sizeable.Horizontal> {
			private static final SingleTypeTraceability<ExElement, ExElement.Interpreted<?>, ExElement.Def<?>> TRACEABILITY = ElementTypeTraceability
				.<ExElement, Sizeable, Interpreted<?>, Def<?>> buildAddOn(QuickBaseInterpretation.NAME, QuickBaseInterpretation.VERSION,
					"h-sizeable", Def.class, Interpreted.class, Sizeable.class)//
				.withAddOnAttribute("width", Def::getSize, Interpreted::getSize)//
				.withAddOnAttribute("min-width", Def::getMinimum, Interpreted::getMinimum)//
				.withAddOnAttribute("pref-width", Def::getPreferred, Interpreted::getPreferred)//
				.withAddOnAttribute("max-width", Def::getMaximum, Interpreted::getMaximum)//
				.build();

			public Horizontal(QonfigAddOn type, ExElement.Def<?> element) {
				super(Ternian.FALSE, type, element);
			}

			@Override
			public void update(ExpressoQIS session, ExElement.Def<?> element) throws QonfigInterpretationException {
				element.withTraceability(TRACEABILITY.validate(session.getFocusType(), session.reporting()));
				super.update(session, element);
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
		public void update(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			ModelInstanceType<SettableValue<?>, SettableValue<QuickSize>> sizeType = ModelTypes.Value.forType(QuickSize.class);
			theSize = getDefinition().getSize() == null ? null : getDefinition().getSize().interpret(sizeType, env);
			theMinimum = getDefinition().getMinimum() == null ? null : getDefinition().getMinimum().interpret(sizeType, env);
			thePreferred = getDefinition().getPreferred() == null ? null : getDefinition().getPreferred().interpret(sizeType, env);
			theMaximum = getDefinition().getMaximum() == null ? null : getDefinition().getMaximum().interpret(sizeType, env);
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
				return new Sizeable.Vertical(element);
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
				return new Sizeable.Horizontal(element);
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
				return new Sizeable.Generic(element);
			}
		}
	}

	private ModelValueInstantiator<SettableValue<QuickSize>> theSizeInstantiator;
	private ModelValueInstantiator<SettableValue<QuickSize>> theMinimumInstantiator;
	private ModelValueInstantiator<SettableValue<QuickSize>> thePreferredInstantiator;
	private ModelValueInstantiator<SettableValue<QuickSize>> theMaximumInstantiator;

	private SettableValue<SettableValue<QuickSize>> theSize;
	private SettableValue<SettableValue<QuickSize>> theMinimum;
	private SettableValue<SettableValue<QuickSize>> thePreferred;
	private SettableValue<SettableValue<QuickSize>> theMaximum;

	protected Sizeable(ExElement element) {
		super(element);
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
	public void update(ExAddOn.Interpreted<?, ?> interpreted) {
		super.update(interpreted);
		Sizeable.Interpreted<?> myInterpreted = (Sizeable.Interpreted<?>) interpreted;
		theSizeInstantiator = myInterpreted.getSize() == null ? null : myInterpreted.getSize().instantiate();
		theMinimumInstantiator = myInterpreted.getMinimum() == null ? null : myInterpreted.getMinimum().instantiate();
		thePreferredInstantiator = myInterpreted.getPreferred() == null ? null : myInterpreted.getPreferred().instantiate();
		theMaximumInstantiator = myInterpreted.getMaximum() == null ? null : myInterpreted.getMaximum().instantiate();
	}

	@Override
	public void instantiate(ModelSetInstance models) throws ModelInstantiationException {
		super.instantiate(models);
		theSize.set(theSizeInstantiator == null ? null : theSizeInstantiator.get(models), null);
		theMinimum.set(theMinimumInstantiator == null ? null : theMinimumInstantiator.get(models), null);
		thePreferred.set(thePreferredInstantiator == null ? null : thePreferredInstantiator.get(models), null);
		theMaximum.set(theMaximumInstantiator == null ? null : theMaximumInstantiator.get(models), null);
	}

	@Override
	protected Sizeable clone() {
		Sizeable copy = (Sizeable) super.clone();

		copy.theSize = SettableValue.build(theSize.getType()).build();
		copy.theMinimum = SettableValue.build(theSize.getType()).build();
		copy.thePreferred = SettableValue.build(theSize.getType()).build();
		copy.theMaximum = SettableValue.build(theSize.getType()).build();

		return copy;
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
	public static CompiledModelValue<SettableValue<?>> parseSize(QonfigValue value, ExpressoQIS session, boolean position)
		throws QonfigInterpretationException {
		if (value == null)
			return null;
		else if (!(value.value instanceof QonfigExpression))
			throw new IllegalArgumentException("Not an expression");
		QonfigExpression expression = (QonfigExpression) value.value;
		boolean pct, xp;
		int unit;
		if (expression.text.endsWith("%")) {
			unit = 1;
			pct = true;
			xp = false;
		} else if (expression.text.endsWith("px") && !Character.isAlphabetic(expression.text.charAt(expression.text.length() - 3))) {
			unit = 2;
			pct = false;
			xp = false;
		} else if (position && expression.text.endsWith("xp")
			&& !Character.isAlphabetic(expression.text.charAt(expression.text.length() - 3))) {
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
		return CompiledModelValue.of(expression::toString, ModelTypes.Value, env -> {
			InterpretedValueSynth<SettableValue<?>, SettableValue<QuickSize>> positionValue;
			if (fUnit > 0) {
				InterpretedValueSynth<SettableValue<?>, SettableValue<Double>> num;
				try {
					num = parsed.evaluate(ModelTypes.Value.forType(double.class), env, 0);
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
				ModelValueInstantiator<SettableValue<Double>> numInst = num.instantiate();
				positionValue = InterpretedValueSynth.simple(ModelTypes.Value.forType(QuickSize.class), ModelValueInstantiator.of(msi -> {
					SettableValue<Double> numV = numInst.get(msi);
					return numV.transformReversible(QuickSize.class, tx -> tx//
						.map(map)//
						.replaceSource(reverse, rev -> rev.allowInexactReverse(true)));
				}));
			} else {
				try {
					positionValue = parsed.evaluate(ModelTypes.Value.forType(QuickSize.class), env, 0);
				} catch (ExpressoEvaluationException e1) {
					// If it doesn't parse as a position, try parsing as a number.
					try {
						positionValue = parsed.evaluate(ModelTypes.Value.forType(int.class), env, 0)//
							.map(ModelTypes.Value.forType(QuickSize.class), mvi -> mvi.map(v -> v.transformReversible(QuickSize.class,
								tx -> tx.map(d -> new QuickSize(0.0f, d)).withReverse(pos -> pos.pixels))));
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
		public Vertical(ExElement element) {
			super(element);
		}

		@Override
		public Class<Interpreted.Vertical> getInterpretationType() {
			return Interpreted.Vertical.class;
		}
	}

	public static class Horizontal extends Sizeable {
		public Horizontal(ExElement element) {
			super(element);
		}

		@Override
		public Class<Interpreted.Horizontal> getInterpretationType() {
			return Interpreted.Horizontal.class;
		}
	}

	public static class Generic extends Sizeable {
		public Generic(ExElement element) {
			super(element);
		}

		@Override
		public Class<Interpreted.Generic> getInterpretationType() {
			return Interpreted.Generic.class;
		}
	}
}
