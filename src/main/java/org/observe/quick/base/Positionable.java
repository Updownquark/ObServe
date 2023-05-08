package org.observe.quick.base;

import org.observe.Observable;
import org.observe.ObservableValueEvent;
import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ExpressoQIS;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.CompiledModelValue;
import org.observe.expresso.ObservableModelSet.InterpretedModelSet;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.quick.QuickAddOn;
import org.observe.quick.QuickElement;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigInterpretationException;

public abstract class Positionable extends QuickAddOn.Abstract<QuickElement> {
	public static abstract class Def<P extends Positionable> extends QuickAddOn.Def.Abstract<QuickElement, P> {
		private final boolean isVertical;
		private CompiledModelValue<SettableValue<?>, SettableValue<QuickSize>> theLeading;
		private CompiledModelValue<SettableValue<?>, SettableValue<QuickSize>> theCenter;
		private CompiledModelValue<SettableValue<?>, SettableValue<QuickSize>> theTrailing;

		protected Def(boolean vertical, QonfigAddOn type, QuickElement.Def<? extends QuickElement> element) {
			super(type, element);
			isVertical = vertical;
		}

		public boolean isVertical() {
			return isVertical;
		}

		public CompiledModelValue<SettableValue<?>, SettableValue<QuickSize>> getLeading() {
			return theLeading;
		}

		public CompiledModelValue<SettableValue<?>, SettableValue<QuickSize>> getCenter() {
			return theCenter;
		}

		public CompiledModelValue<SettableValue<?>, SettableValue<QuickSize>> getTrailing() {
			return theTrailing;
		}

		@Override
		public Def<P> update(ExpressoQIS session) throws QonfigInterpretationException {
			super.update(session);
			if (isVertical) {
				theLeading = Sizeable.parseSize(session.getAttributeQV("top"), session, true);
				theCenter = Sizeable.parseSize(session.getAttributeQV("v-center"), session, true);
				theTrailing = Sizeable.parseSize(session.getAttributeQV("bottom"), session, true);
			} else {
				theLeading = Sizeable.parseSize(session.getAttributeQV("left"), session, true);
				theCenter = Sizeable.parseSize(session.getAttributeQV("h-center"), session, true);
				theTrailing = Sizeable.parseSize(session.getAttributeQV("right"), session, true);
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
				super(false, type, element);
			}

			@Override
			public Interpreted.Horizontal interpret(QuickElement.Interpreted<?> element) {
				return new Interpreted.Horizontal(this, element);
			}
		}
	}

	public static abstract class Interpreted<P extends Positionable> extends QuickAddOn.Interpreted.Abstract<QuickElement, P> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<QuickSize>> theLeading;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<QuickSize>> theCenter;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<QuickSize>> theTrailing;

		protected Interpreted(Def<P> definition, QuickElement.Interpreted<?> element) {
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

	private final SettableValue<SettableValue<QuickSize>> theLeading;
	private final SettableValue<SettableValue<QuickSize>> theCenter;
	private final SettableValue<SettableValue<QuickSize>> theTrailing;

	protected Positionable(Interpreted<?> interpreted, QuickElement element) {
		super(interpreted, element);
		theLeading = SettableValue
			.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<QuickSize>> parameterized(QuickSize.class)).build();
		theCenter = SettableValue.build(theLeading.getType()).build();
		theTrailing = SettableValue.build(theLeading.getType()).build();
	}

	@Override
	public Interpreted<?> getInterpreted() {
		return (Interpreted<?>) super.getInterpreted();
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
	public Positionable update(ModelSetInstance models) throws ModelInstantiationException {
		theLeading.set(getInterpreted().getLeading() == null ? null : getInterpreted().getLeading().get(models), null);
		theCenter.set(getInterpreted().getCenter() == null ? null : getInterpreted().getCenter().get(models), null);
		theTrailing.set(getInterpreted().getTrailing() == null ? null : getInterpreted().getTrailing().get(models), null);
		return this;
	}

	public Observable<ObservableValueEvent<QuickSize>> changes() {
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
}
