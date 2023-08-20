package org.observe.quick.base;

import org.observe.Observable;
import org.observe.ObservableValueEvent;
import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ElementTypeTraceability;
import org.observe.expresso.qonfig.ElementTypeTraceability.SingleTypeTraceability;
import org.observe.expresso.qonfig.ExAddOn;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigInterpretationException;

public abstract class Positionable extends ExAddOn.Abstract<ExElement> {
	public static abstract class Def<P extends Positionable> extends ExAddOn.Def.Abstract<ExElement, P> {
		private final boolean isVertical;
		private CompiledExpression theLeading;
		private CompiledExpression theCenter;
		private CompiledExpression theTrailing;

		protected Def(boolean vertical, QonfigAddOn type, ExElement.Def<?> element) {
			super(type, element);
			isVertical = vertical;
		}

		public boolean isVertical() {
			return isVertical;
		}

		public CompiledExpression getLeading() {
			return theLeading;
		}

		public CompiledExpression getCenter() {
			return theCenter;
		}

		public CompiledExpression getTrailing() {
			return theTrailing;
		}

		@Override
		public void update(ExpressoQIS session, ExElement.Def<?> element) throws QonfigInterpretationException {
			super.update(session, element);
			if (isVertical) {
				theLeading = session.getAttributeExpression("top");
				theCenter = session.getAttributeExpression("v-center");
				theTrailing = session.getAttributeExpression("bottom");
			} else {
				theLeading = session.getAttributeExpression("left");
				theCenter = session.getAttributeExpression("h-center");
				theTrailing = session.getAttributeExpression("right");
			}
		}

		public static class Vertical extends Def<Positionable.Vertical> {
			private static final SingleTypeTraceability<ExElement, ExElement.Interpreted<?>, ExElement.Def<?>> TRACEABILITY = ElementTypeTraceability
				.<ExElement, Positionable, Interpreted<?>, Def<?>> buildAddOn(QuickBaseInterpretation.NAME, QuickBaseInterpretation.VERSION,
					"v-positionable", Def.class, Interpreted.class, Positionable.class)//
				.withAddOnAttribute("top", Def::getLeading, Interpreted::getLeading)//
				.withAddOnAttribute("v-center", Def::getCenter, Interpreted::getCenter)//
				.withAddOnAttribute("bottom", Def::getTrailing, Interpreted::getTrailing)//
				.build();

			public Vertical(QonfigAddOn type, ExElement.Def<?> element) {
				super(true, type, element);
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

		public static class Horizontal extends Def<Positionable.Horizontal> {
			private static final SingleTypeTraceability<ExElement, ExElement.Interpreted<?>, ExElement.Def<?>> TRACEABILITY = ElementTypeTraceability
				.<ExElement, Positionable, Interpreted<?>, Def<?>> buildAddOn(QuickBaseInterpretation.NAME, QuickBaseInterpretation.VERSION,
					"h-positionable", Def.class, Interpreted.class, Positionable.class)//
				.withAddOnAttribute("left", Def::getLeading, Interpreted::getLeading)//
				.withAddOnAttribute("h-center", Def::getCenter, Interpreted::getCenter)//
				.withAddOnAttribute("right", Def::getTrailing, Interpreted::getTrailing)//
				.build();

			public Horizontal(QonfigAddOn type, ExElement.Def<?> element) {
				super(false, type, element);
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
	}

	public static abstract class Interpreted<P extends Positionable> extends ExAddOn.Interpreted.Abstract<ExElement, P> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<QuickSize>> theLeading;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<QuickSize>> theCenter;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<QuickSize>> theTrailing;

		protected Interpreted(Def<P> definition, ExElement.Interpreted<?> element) {
			super(definition, element);
		}

		@Override
		public Def<P> getDefinition() {
			return (Def<P>) super.getDefinition();
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<QuickSize>> getLeading() {
			return theLeading;
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<QuickSize>> getCenter() {
			return theCenter;
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<QuickSize>> getTrailing() {
			return theTrailing;
		}

		@Override
		public void update(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			ModelInstanceType<SettableValue<?>, SettableValue<QuickSize>> sizeType = ModelTypes.Value.forType(QuickSize.class);
			theLeading = getDefinition().getLeading() == null ? null : getDefinition().getLeading().interpret(sizeType, env);
			theCenter = getDefinition().getCenter() == null ? null : getDefinition().getCenter().interpret(sizeType, env);
			theTrailing = getDefinition().getTrailing() == null ? null : getDefinition().getTrailing().interpret(sizeType, env);
		}

		public static class Vertical extends Interpreted<Positionable.Vertical> {
			public Vertical(Def.Vertical definition, ExElement.Interpreted<?> element) {
				super(definition, element);
			}

			@Override
			public Def.Vertical getDefinition() {
				return (Def.Vertical) super.getDefinition();
			}

			@Override
			public Positionable.Vertical create(ExElement element) {
				return new Positionable.Vertical(this, element);
			}
		}

		public static class Horizontal extends Interpreted<Positionable.Horizontal> {
			public Horizontal(Def.Horizontal definition, ExElement.Interpreted<?> element) {
				super(definition, element);
			}

			@Override
			public Def.Horizontal getDefinition() {
				return (Def.Horizontal) super.getDefinition();
			}

			@Override
			public Positionable.Horizontal create(ExElement element) {
				return new Positionable.Horizontal(this, element);
			}
		}
	}

	private final SettableValue<SettableValue<QuickSize>> theLeading;
	private final SettableValue<SettableValue<QuickSize>> theCenter;
	private final SettableValue<SettableValue<QuickSize>> theTrailing;

	protected Positionable(Interpreted<?> interpreted, ExElement element) {
		super(interpreted, element);
		theLeading = SettableValue
			.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<QuickSize>> parameterized(QuickSize.class)).build();
		theCenter = SettableValue.build(theLeading.getType()).build();
		theTrailing = SettableValue.build(theLeading.getType()).build();
	}

	public SettableValue<QuickSize> getLeading() {
		return SettableValue.flatten(theLeading);
	}

	public SettableValue<QuickSize> getCenter() {
		return SettableValue.flatten(theCenter);
	}

	public SettableValue<QuickSize> getTrailing() {
		return SettableValue.flatten(theTrailing);
	}

	@Override
	public void update(ExAddOn.Interpreted<?, ?> interpreted, ModelSetInstance models) throws ModelInstantiationException {
		super.update(interpreted, models);
		Positionable.Interpreted<?> myInterpreted = (Positionable.Interpreted<?>) interpreted;
		theLeading.set(myInterpreted.getLeading() == null ? null : myInterpreted.getLeading().instantiate().get(models), null);
		theCenter.set(myInterpreted.getCenter() == null ? null : myInterpreted.getCenter().instantiate().get(models), null);
		theTrailing.set(myInterpreted.getTrailing() == null ? null : myInterpreted.getTrailing().instantiate().get(models), null);
	}

	public Observable<ObservableValueEvent<QuickSize>> changes() {
		return Observable.or(getLeading().noInitChanges(), getCenter().noInitChanges(), getTrailing().noInitChanges());
	}

	public static class Vertical extends Positionable {
		public Vertical(Interpreted.Vertical interpreted, ExElement element) {
			super(interpreted, element);
		}
	}

	public static class Horizontal extends Positionable {
		public Horizontal(Interpreted.Horizontal interpreted, ExElement element) {
			super(interpreted, element);
		}
	}
}
