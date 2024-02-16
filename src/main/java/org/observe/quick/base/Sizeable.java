package org.observe.quick.base;

import java.util.function.Function;

import org.observe.Observable;
import org.observe.ObservableValueEvent;
import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ExpressoParseException;
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
import org.observe.expresso.qonfig.ExAddOn;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.expresso.qonfig.QonfigExpression;
import org.observe.util.TypeTokens;
import org.qommons.Ternian;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigElement.QonfigValue;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.ex.ExceptionHandler;
import org.qommons.ex.NeverThrown;
import org.qommons.io.LocatedFilePosition;

/**
 * An add-on typically inherited by children of a &lt;box> managed by a layout indicating that its size along one dimension may be specified
 * by attributes.
 */
public abstract class Sizeable extends ExAddOn.Abstract<ExElement> {
	/** The XML name of the horizontal extension of this element */
	public static final String H_SIZEABLE = "h-sizeable";
	/** The XML name of the vertical extension of this element */
	public static final String V_SIZEABLE = "v-sizeable";

	/**
	 * {@link Sizeable} definition
	 *
	 * @param <S> The sub-type of sizeable to create
	 */
	public static abstract class Def<S extends Sizeable> extends ExAddOn.Def.Abstract<ExElement, S> {
		private final Ternian isVertical;
		private CompiledExpression theSize;
		private CompiledExpression theMinimum;
		private CompiledExpression thePreferred;
		private CompiledExpression theMaximum;

		/**
		 * @param vertical Whether this positionable is vertical, horizontal, or generic (the dimension is determined by context which this
		 *        add-on does not need to be aware of)
		 * @param type The Qonfig type of this add-on
		 * @param element The element this sizeable is for
		 */
		protected Def(Ternian vertical, QonfigAddOn type, ExElement.Def<? extends ExElement> element) {
			super(type, element);
			isVertical = vertical;
		}

		/**
		 * @return Whether this positionable is vertical, horizontal, or generic (the dimension is determined by context which this add-on
		 *         does not need to be aware of)
		 */
		public Ternian isVertical() {
			return isVertical;
		}

		/**
		 * @return The size of the widget. Overrides {@link #getMinimum() min}, {@link #getPreferred() preferred}, and {@link #getMaximum()
		 *         max} constraints
		 */
		public CompiledExpression getSize() {
			return theSize;
		}

		/** @return The minimum size constraint for the widget */
		public CompiledExpression getMinimum() {
			return theMinimum;
		}

		/** @return The preferred size for the widget */
		public CompiledExpression getPreferred() {
			return thePreferred;
		}

		/** @return The maximum size constraint for the widget */
		public CompiledExpression getMaximum() {
			return theMaximum;
		}

		@Override
		public void update(ExpressoQIS session, ExElement.Def<?> element) throws QonfigInterpretationException {
			super.update(session, element);
			switch (isVertical) {
			case TRUE:
				theSize = element.getAttributeExpression("height", session);
				theMinimum = element.getAttributeExpression("min-height", session);
				thePreferred = element.getAttributeExpression("pref-height", session);
				theMaximum = element.getAttributeExpression("max-height", session);
				break;
			case FALSE:
				theSize = element.getAttributeExpression("width", session);
				theMinimum = element.getAttributeExpression("min-width", session);
				thePreferred = element.getAttributeExpression("pref-width", session);
				theMaximum = element.getAttributeExpression("max-width", session);
				break;
			default:
				theSize = element.getAttributeExpression("size", session);
				theMinimum = element.getAttributeExpression("min-size", session);
				thePreferred = element.getAttributeExpression("pref-size", session);
				theMaximum = element.getAttributeExpression("max-size", session);
				break;
			}
		}

		/** {@link Sizeable}.{@link Sizeable.Vertical} definition */
		@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
			qonfigType = V_SIZEABLE,
			interpretation = Interpreted.Vertical.class,
			instance = Sizeable.Vertical.class)
		public static class Vertical extends Def<Sizeable.Vertical> {
			/**
			 * @param type The Qonfig type of this add-on
			 * @param element The element this sizeable is for
			 */
			public Vertical(QonfigAddOn type, ExElement.Def<?> element) {
				super(Ternian.TRUE, type, element);
			}

			@Override
			@QonfigAttributeGetter("height")
			public CompiledExpression getSize() {
				return super.getSize();
			}

			@Override
			@QonfigAttributeGetter("min-height")
			public CompiledExpression getMinimum() {
				return super.getMinimum();
			}

			@Override
			@QonfigAttributeGetter("pref-height")
			public CompiledExpression getPreferred() {
				return super.getPreferred();
			}

			@Override
			@QonfigAttributeGetter("max-height")
			public CompiledExpression getMaximum() {
				return super.getMaximum();
			}

			@Override
			public void update(ExpressoQIS session, ExElement.Def<?> element) throws QonfigInterpretationException {
				super.update(session, element);
			}

			@Override
			public Interpreted.Vertical interpret(ExElement.Interpreted<?> element) {
				return new Interpreted.Vertical(this, element);
			}
		}

		/** {@link Sizeable}.{@link Sizeable.Horizontal} definition */
		@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
			qonfigType = H_SIZEABLE,
			interpretation = Interpreted.Horizontal.class,
			instance = Sizeable.Horizontal.class)
		public static class Horizontal extends Def<Sizeable.Horizontal> {
			/**
			 * @param type The Qonfig type of this add-on
			 * @param element The element this sizeable is for
			 */
			public Horizontal(QonfigAddOn type, ExElement.Def<?> element) {
				super(Ternian.FALSE, type, element);
			}

			@Override
			@QonfigAttributeGetter("width")
			public CompiledExpression getSize() {
				return super.getSize();
			}

			@Override
			@QonfigAttributeGetter("min-width")
			public CompiledExpression getMinimum() {
				return super.getMinimum();
			}

			@Override
			@QonfigAttributeGetter("pref-width")
			public CompiledExpression getPreferred() {
				return super.getPreferred();
			}

			@Override
			@QonfigAttributeGetter("max-width")
			public CompiledExpression getMaximum() {
				return super.getMaximum();
			}

			@Override
			public void update(ExpressoQIS session, ExElement.Def<?> element) throws QonfigInterpretationException {
				super.update(session, element);
			}

			@Override
			public Interpreted.Horizontal interpret(ExElement.Interpreted<?> element) {
				return new Interpreted.Horizontal(this, element);
			}
		}

		/** {@link Sizeable}.{@link Sizeable.Generic} definition */
		public static class Generic extends Def<Sizeable.Generic> {
			/**
			 * @param type The Qonfig type of this add-on
			 * @param element The element this sizeable is for
			 */
			public Generic(QonfigAddOn type, ExElement.Def<?> element) {
				super(Ternian.NONE, type, element);
			}

			@Override
			public Interpreted.Generic interpret(ExElement.Interpreted<?> element) {
				return new Interpreted.Generic(this, element);
			}
		}
	}

	/**
	 * {@link Sizeable} interpretation
	 *
	 * @param <S> The sub-type of sizeable to create
	 */
	public abstract static class Interpreted<S extends Sizeable> extends ExAddOn.Interpreted.Abstract<ExElement, S> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<QuickSize>> theSize;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<QuickSize>> theMaximum;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<QuickSize>> thePreferred;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<QuickSize>> theMinimum;

		/**
		 * @param definition The definition to interpret
		 * @param element The element this sizeable is for
		 */
		protected Interpreted(Def<S> definition, ExElement.Interpreted<?> element) {
			super(definition, element);
		}

		@Override
		public Def<S> getDefinition() {
			return (Def<S>) super.getDefinition();
		}

		/**
		 * @return The size of the widget. Overrides {@link #getMinimum() min}, {@link #getPreferred() preferred}, and {@link #getMaximum()
		 *         max} constraints
		 */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<QuickSize>> getSize() {
			return theSize;
		}

		/** @return The minimum size constraint for the widget */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<QuickSize>> getMinimum() {
			return theMinimum;
		}

		/** @return The preferred size for the widget */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<QuickSize>> getPreferred() {
			return thePreferred;
		}

		/** @return The maximum size constraint for the widget */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<QuickSize>> getMaximum() {
			return theMaximum;
		}

		@Override
		public void update(ExElement.Interpreted<?> element) throws ExpressoInterpretationException {
			super.update(element);
			ModelInstanceType<SettableValue<?>, SettableValue<QuickSize>> sizeType = ModelTypes.Value.forType(QuickSize.class);
			theSize = getElement().interpret(getDefinition().getSize(), sizeType);
			theMinimum = getElement().interpret(getDefinition().getMinimum(), sizeType);
			thePreferred = getElement().interpret(getDefinition().getPreferred(), sizeType);
			theMaximum = getElement().interpret(getDefinition().getMaximum(), sizeType);
		}

		/** {@link Sizeable}.{@link Sizeable.Vertical} interpretation */
		public static class Vertical extends Interpreted<Sizeable.Vertical> {
			/**
			 * @param definition The definition to interpret
			 * @param element The element this sizeable is for
			 */
			public Vertical(Def.Vertical definition, ExElement.Interpreted<?> element) {
				super(definition, element);
			}

			@Override
			public Def.Vertical getDefinition() {
				return (Def.Vertical) super.getDefinition();
			}

			@Override
			public Class<Sizeable.Vertical> getInstanceType() {
				return Sizeable.Vertical.class;
			}

			@Override
			public Sizeable.Vertical create(ExElement element) {
				return new Sizeable.Vertical(element);
			}
		}

		/** {@link Sizeable}.{@link Sizeable.Horizontal} interpretation */
		public static class Horizontal extends Interpreted<Sizeable.Horizontal> {
			/**
			 * @param definition The definition to interpret
			 * @param element The element this sizeable is for
			 */
			public Horizontal(Def.Horizontal definition, ExElement.Interpreted<?> element) {
				super(definition, element);
			}

			@Override
			public Def.Horizontal getDefinition() {
				return (Def.Horizontal) super.getDefinition();
			}

			@Override
			public Class<Sizeable.Horizontal> getInstanceType() {
				return Sizeable.Horizontal.class;
			}

			@Override
			public Sizeable.Horizontal create(ExElement element) {
				return new Sizeable.Horizontal(element);
			}
		}

		/** {@link Sizeable}.{@link Sizeable.Generic} interpretation */
		public static class Generic extends Interpreted<Sizeable.Generic> {
			/**
			 * @param definition The definition to interpret
			 * @param element The element this sizeable is for
			 */
			public Generic(Def.Generic definition, ExElement.Interpreted<?> element) {
				super(definition, element);
			}

			@Override
			public Def.Generic getDefinition() {
				return (Def.Generic) super.getDefinition();
			}

			@Override
			public Class<Sizeable.Generic> getInstanceType() {
				return Sizeable.Generic.class;
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

	/** @param element The element this sizeable is for */
	protected Sizeable(ExElement element) {
		super(element);
		theSize = SettableValue
			.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<QuickSize>> parameterized(QuickSize.class)).build();
		theMinimum = SettableValue.build(theSize.getType()).build();
		thePreferred = SettableValue.build(theSize.getType()).build();
		theMaximum = SettableValue.build(theSize.getType()).build();
	}

	/**
	 * @return The size of the widget. Overrides {@link #getMinimum() min}, {@link #getPreferred() preferred}, and {@link #getMaximum() max}
	 *         constraints
	 */
	public SettableValue<QuickSize> getSize() {
		return SettableValue.flatten(theSize);
	}

	/** @return The minimum size constraint for the widget */
	public SettableValue<QuickSize> getMinimum() {
		return SettableValue.flatten(theMinimum);
	}

	/** @return The preferred size for the widget */
	public SettableValue<QuickSize> getPreferred() {
		return SettableValue.flatten(thePreferred);
	}

	/** @return The maximum size constraint for the widget */
	public SettableValue<QuickSize> getMaximum() {
		return SettableValue.flatten(theMaximum);
	}

	@Override
	public void update(ExAddOn.Interpreted<?, ?> interpreted, ExElement element) throws ModelInstantiationException {
		super.update(interpreted, element);
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

	/** @return An observable that fires whenever the size constraints change */
	public Observable<ObservableValueEvent<QuickSize>> changes() {
		return Observable.or(getSize().noInitChanges(), getMinimum().noInitChanges(), getPreferred().noInitChanges(),
			getMaximum().noInitChanges());
	}

	/** Vertical {@link Sizeable} */
	public static class Vertical extends Sizeable {
		/** @param element The element this sizeable is for */
		public Vertical(ExElement element) {
			super(element);
		}

		@Override
		public Class<Interpreted.Vertical> getInterpretationType() {
			return Interpreted.Vertical.class;
		}
	}

	/** Horizontal {@link Sizeable} */
	public static class Horizontal extends Sizeable {
		/** @param element The element this sizeable is for */
		public Horizontal(ExElement element) {
			super(element);
		}

		@Override
		public Class<Interpreted.Horizontal> getInterpretationType() {
			return Interpreted.Horizontal.class;
		}
	}

	/** Generic {@link Sizeable}, whose dimension is determined by context that this add-on doesn't need to be aware of */
	public static class Generic extends Sizeable {
		/** @param element The element this sizeable is for */
		public Generic(ExElement element) {
			super(element);
		}

		@Override
		public Class<Interpreted.Generic> getInterpretationType() {
			return Interpreted.Generic.class;
		}
	}

	/**
	 * Parses a position in Quick
	 *
	 * @param value The expression to parse
	 * @param session The session in which to parse the expression
	 * @param position Whether the size to be parsed represents an position or a size
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
			if (fUnit > 0) {
				InterpretedValueSynth<SettableValue<?>, SettableValue<Double>> num;
				try {
					num = parsed.evaluate(ModelTypes.Value.forType(double.class), env, 0, ExceptionHandler.thrower2());
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
				return num.map(ModelTypes.Value.forType(QuickSize.class), numInst -> ModelValueInstantiator.of(msi -> {
					SettableValue<Double> numV = numInst.get(msi);
					return numV.transformReversible(QuickSize.class, tx -> tx//
						.map(map)//
						.replaceSource(reverse, rev -> rev.allowInexactReverse(true)));
				}));
			} else {
				ExceptionHandler.Double<ExpressoInterpretationException, TypeConversionException, NeverThrown, NeverThrown> tce = ExceptionHandler
					.holder2();
				InterpretedValueSynth<SettableValue<?>, SettableValue<QuickSize>> positionValue;
				positionValue = parsed.evaluate(ModelTypes.Value.forType(QuickSize.class), env, 0, tce);
				if (tce.hasException()) {
					// If it doesn't parse as a position, try parsing as a number.
					try {
						positionValue = parsed.evaluate(ModelTypes.Value.forType(int.class), env, 0, ExceptionHandler.thrower2())//
							.map(ModelTypes.Value.forType(QuickSize.class), mvi -> mvi.map(v -> v.transformReversible(QuickSize.class,
								tx -> tx.map(d -> new QuickSize(0.0f, d)).withReverse(pos -> pos.pixels))));
					} catch (TypeConversionException e2) {
						if (tce.get1() != null)
							throw new ExpressoInterpretationException(e2.getMessage(),
								new LocatedFilePosition(value.fileLocation, tce.get1().getPosition()), 0, tce.get1());
						else
							throw new ExpressoInterpretationException(e2.getMessage(),
								new LocatedFilePosition(value.fileLocation, env.reporting().getPosition()), 0, tce.get2());
					}
				}
				return positionValue;
			}
		});
	}
}
