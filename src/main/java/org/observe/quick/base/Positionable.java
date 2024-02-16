package org.observe.quick.base;

import org.observe.Observable;
import org.observe.ObservableValueEvent;
import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ExAddOn;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigInterpretationException;

/**
 * An add-on typically inherited by children of a &lt;box> managed by a layout indicating that its position along one dimension may be
 * specified by attributes.
 */
public abstract class Positionable extends ExAddOn.Abstract<ExElement> {
	/** The XML name of the horizontal extension of this element */
	public static final String H_POSITIONABLE = "h-positionable";
	/** The XML name of the vertical extension of this element */
	public static final String V_POSITIONABLE = "v-positionable";

	/**
	 * {@link Positionable} definition
	 *
	 * @param <P> The sub-type of positionable to create
	 */
	public static abstract class Def<P extends Positionable> extends ExAddOn.Def.Abstract<ExElement, P> {
		private final boolean isVertical;
		private CompiledExpression theLeading;
		private CompiledExpression theCenter;
		private CompiledExpression theTrailing;

		/**
		 * @param vertical Whether this positionable is vertical or horizontal
		 * @param type The Qonfig type of this add-on
		 * @param element The element this positionable is for
		 */
		protected Def(boolean vertical, QonfigAddOn type, ExElement.Def<?> element) {
			super(type, element);
			isVertical = vertical;
		}

		/** @return Whether this positionable is vertical or horizontal */
		public boolean isVertical() {
			return isVertical;
		}

		/** @return The position for the leading edge (top or left) of the widget */
		public CompiledExpression getLeading() {
			return theLeading;
		}

		/** @return The position for the horizontal or vertical center of the widget */
		public CompiledExpression getCenter() {
			return theCenter;
		}

		/** @return The position for the trailing edge (bottom or right) of the widget */
		public CompiledExpression getTrailing() {
			return theTrailing;
		}

		@Override
		public void update(ExpressoQIS session, ExElement.Def<?> element) throws QonfigInterpretationException {
			super.update(session, element);
			if (isVertical) {
				theLeading = element.getAttributeExpression("top", session);
				theCenter = element.getAttributeExpression("v-center", session);
				theTrailing = element.getAttributeExpression("bottom", session);
			} else {
				theLeading = element.getAttributeExpression("left", session);
				theCenter = element.getAttributeExpression("h-center", session);
				theTrailing = element.getAttributeExpression("right", session);
			}
		}

		/** {@link Positionable}.{@link Positionable.Vertical} definition */
		@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
			qonfigType = V_POSITIONABLE,
			interpretation = Interpreted.Vertical.class,
			instance = Positionable.Vertical.class)
		public static class Vertical extends Def<Positionable.Vertical> {
			/**
			 * @param type The Qonfig type of this add-on
			 * @param element The element this positionable is for
			 */
			public Vertical(QonfigAddOn type, ExElement.Def<?> element) {
				super(true, type, element);
			}

			@Override
			@QonfigAttributeGetter("top")
			public CompiledExpression getLeading() {
				return super.getLeading();
			}

			@Override
			@QonfigAttributeGetter("v-center")
			public CompiledExpression getCenter() {
				return super.getCenter();
			}

			@Override
			@QonfigAttributeGetter("bottom")
			public CompiledExpression getTrailing() {
				return super.getTrailing();
			}

			@Override
			public Interpreted.Vertical interpret(ExElement.Interpreted<?> element) {
				return new Interpreted.Vertical(this, element);
			}
		}

		/** {@link Positionable}.{@link Positionable.Horizontal} definition */
		@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
			qonfigType = H_POSITIONABLE,
			interpretation = Interpreted.Horizontal.class,
			instance = Positionable.Horizontal.class)
		public static class Horizontal extends Def<Positionable.Horizontal> {
			/**
			 * @param type The Qonfig type of this add-on
			 * @param element The element this positionable is for
			 */
			public Horizontal(QonfigAddOn type, ExElement.Def<?> element) {
				super(false, type, element);
			}

			@Override
			@QonfigAttributeGetter("left")
			public CompiledExpression getLeading() {
				return super.getLeading();
			}

			@Override
			@QonfigAttributeGetter("h-center")
			public CompiledExpression getCenter() {
				return super.getCenter();
			}

			@Override
			@QonfigAttributeGetter("right")
			public CompiledExpression getTrailing() {
				return super.getTrailing();
			}

			@Override
			public Interpreted.Horizontal interpret(ExElement.Interpreted<?> element) {
				return new Interpreted.Horizontal(this, element);
			}
		}
	}

	/**
	 * {@link Positionable} interpretation
	 *
	 * @param <P> The sub-type of positionable to create
	 */
	public static abstract class Interpreted<P extends Positionable> extends ExAddOn.Interpreted.Abstract<ExElement, P> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<QuickSize>> theLeading;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<QuickSize>> theCenter;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<QuickSize>> theTrailing;

		/**
		 * @param definition The definition to interpret
		 * @param element The element this positionable applies to
		 */
		protected Interpreted(Def<P> definition, ExElement.Interpreted<?> element) {
			super(definition, element);
		}

		@Override
		public Def<P> getDefinition() {
			return (Def<P>) super.getDefinition();
		}

		/** @return The position for the leading edge (top or left) of the widget */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<QuickSize>> getLeading() {
			return theLeading;
		}

		/** @return The position for the horizontal or vertical center of the widget */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<QuickSize>> getCenter() {
			return theCenter;
		}

		/** @return The position for the trailing edge (bottom or right) of the widget */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<QuickSize>> getTrailing() {
			return theTrailing;
		}

		@Override
		public void update(ExElement.Interpreted<?> element) throws ExpressoInterpretationException {
			super.update(element);
			ModelInstanceType<SettableValue<?>, SettableValue<QuickSize>> sizeType = ModelTypes.Value.forType(QuickSize.class);
			theLeading = getElement().interpret(getDefinition().getLeading(), sizeType);
			theCenter = getElement().interpret(getDefinition().getCenter(), sizeType);
			theTrailing = getElement().interpret(getDefinition().getTrailing(), sizeType);
		}

		/** {@link Positionable}.{@link Positionable.Vertical} interpretation */
		public static class Vertical extends Interpreted<Positionable.Vertical> {
			/**
			 * @param definition The definition to interpret
			 * @param element The element this positionable applies to
			 */
			public Vertical(Def.Vertical definition, ExElement.Interpreted<?> element) {
				super(definition, element);
			}

			@Override
			public Def.Vertical getDefinition() {
				return (Def.Vertical) super.getDefinition();
			}

			@Override
			public Class<Positionable.Vertical> getInstanceType() {
				return Positionable.Vertical.class;
			}

			@Override
			public Positionable.Vertical create(ExElement element) {
				return new Positionable.Vertical(element);
			}
		}

		/** {@link Positionable}.{@link Positionable.Horizontal} interpretation */
		public static class Horizontal extends Interpreted<Positionable.Horizontal> {
			/**
			 * @param definition The definition to interpret
			 * @param element The element this positionable applies to
			 */
			public Horizontal(Def.Horizontal definition, ExElement.Interpreted<?> element) {
				super(definition, element);
			}

			@Override
			public Def.Horizontal getDefinition() {
				return (Def.Horizontal) super.getDefinition();
			}

			@Override
			public Class<Positionable.Horizontal> getInstanceType() {
				return Positionable.Horizontal.class;
			}

			@Override
			public Positionable.Horizontal create(ExElement element) {
				return new Positionable.Horizontal(element);
			}
		}
	}

	private ModelValueInstantiator<SettableValue<QuickSize>> theLeadingInstantiator;
	private ModelValueInstantiator<SettableValue<QuickSize>> theCenterInstantiator;
	private ModelValueInstantiator<SettableValue<QuickSize>> theTrailingInstantiator;

	private SettableValue<SettableValue<QuickSize>> theLeading;
	private SettableValue<SettableValue<QuickSize>> theCenter;
	private SettableValue<SettableValue<QuickSize>> theTrailing;

	/** @param element The element this positionable applies to */
	protected Positionable(ExElement element) {
		super(element);
		theLeading = SettableValue
			.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<QuickSize>> parameterized(QuickSize.class)).build();
		theCenter = SettableValue.build(theLeading.getType()).build();
		theTrailing = SettableValue.build(theLeading.getType()).build();
	}

	/** @return The position for the leading edge (top or left) of the widget */
	public SettableValue<QuickSize> getLeading() {
		return SettableValue.flatten(theLeading);
	}

	/** @return The position for the horizontal or vertical center of the widget */
	public SettableValue<QuickSize> getCenter() {
		return SettableValue.flatten(theCenter);
	}

	/** @return The position for the trailing edge (bottom or right) of the widget */
	public SettableValue<QuickSize> getTrailing() {
		return SettableValue.flatten(theTrailing);
	}

	@Override
	public void update(ExAddOn.Interpreted<?, ?> interpreted, ExElement element) throws ModelInstantiationException {
		super.update(interpreted, element);
		Positionable.Interpreted<?> myInterpreted = (Positionable.Interpreted<?>) interpreted;
		theLeadingInstantiator = myInterpreted.getLeading() == null ? null : myInterpreted.getLeading().instantiate();
		theCenterInstantiator = myInterpreted.getCenter() == null ? null : myInterpreted.getCenter().instantiate();
		theTrailingInstantiator = myInterpreted.getTrailing() == null ? null : myInterpreted.getTrailing().instantiate();
	}

	@Override
	public void instantiate(ModelSetInstance models) throws ModelInstantiationException {
		super.instantiate(models);
		theLeading.set(theLeadingInstantiator == null ? null : theLeadingInstantiator.get(models), null);
		theCenter.set(theCenterInstantiator == null ? null : theCenterInstantiator.get(models), null);
		theTrailing.set(theTrailingInstantiator == null ? null : theTrailingInstantiator.get(models), null);
	}

	@Override
	protected Positionable clone() {
		Positionable copy = (Positionable) super.clone();

		copy.theLeading = SettableValue.build(theLeading.getType()).build();
		copy.theCenter = SettableValue.build(theLeading.getType()).build();
		copy.theTrailing = SettableValue.build(theLeading.getType()).build();

		return copy;
	}

	/** @return An observable that fires whenever the position constraints change */
	public Observable<ObservableValueEvent<QuickSize>> changes() {
		return Observable.or(getLeading().noInitChanges(), getCenter().noInitChanges(), getTrailing().noInitChanges());
	}

	/** Vertical {@link Positionable} */
	public static class Vertical extends Positionable {
		/** @param element The element this positionable applies to */
		public Vertical(ExElement element) {
			super(element);
		}

		@Override
		public Class<Interpreted.Vertical> getInterpretationType() {
			return Interpreted.Vertical.class;
		}
	}

	/** Horizontal {@link Positionable} */
	public static class Horizontal extends Positionable {
		/** @param element The element this positionable applies to */
		public Horizontal(ExElement element) {
			super(element);
		}

		@Override
		public Class<Interpreted.Horizontal> getInterpretationType() {
			return Interpreted.Horizontal.class;
		}
	}
}
