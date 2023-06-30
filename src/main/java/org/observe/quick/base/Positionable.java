package org.observe.quick.base;

import org.observe.Observable;
import org.observe.ObservableValueEvent;
import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.CompiledModelValue;
import org.observe.expresso.ObservableModelSet.InterpretedModelSet;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.ExAddOn;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigInterpretationException;

public abstract class Positionable extends ExAddOn.Abstract<ExElement> {
	public static abstract class Def<P extends Positionable> extends ExAddOn.Def.Abstract<ExElement, P> {
		private final boolean isVertical;
		private CompiledModelValue<SettableValue<?>, SettableValue<QuickSize>> theLeading;
		private CompiledModelValue<SettableValue<?>, SettableValue<QuickSize>> theCenter;
		private CompiledModelValue<SettableValue<?>, SettableValue<QuickSize>> theTrailing;

		protected Def(boolean vertical, QonfigAddOn type, ExElement.Def<?> element) {
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
		public void update(ExpressoQIS session, ExElement.Def<?> element) throws QonfigInterpretationException {
			super.update(session, element);
			if (isVertical) {
				theLeading = Sizeable.parseSize(session.getAttributeQV("top"), session, true);
				theCenter = Sizeable.parseSize(session.getAttributeQV("v-center"), session, true);
				theTrailing = Sizeable.parseSize(session.getAttributeQV("bottom"), session, true);
			} else {
				theLeading = Sizeable.parseSize(session.getAttributeQV("left"), session, true);
				theCenter = Sizeable.parseSize(session.getAttributeQV("h-center"), session, true);
				theTrailing = Sizeable.parseSize(session.getAttributeQV("right"), session, true);
			}
		}

		public static class Vertical extends Def<Positionable.Vertical> {
			private static final ExAddOn.AddOnAttributeGetter<ExElement, Positionable.Vertical, Interpreted.Vertical, Def.Vertical> TOP = ExAddOn.AddOnAttributeGetter
				.of(Vertical.class, Vertical::getLeading, Interpreted.Vertical.class, Interpreted.Vertical::getLeading,
					Positionable.Vertical.class, Positionable.Vertical::getLeading);
			private static final ExAddOn.AddOnAttributeGetter<ExElement, Positionable.Vertical, Interpreted.Vertical, Def.Vertical> V_CENTER = ExAddOn.AddOnAttributeGetter
				.of(Vertical.class, Vertical::getCenter, Interpreted.Vertical.class, Interpreted.Vertical::getCenter,
					Positionable.Vertical.class, Positionable.Vertical::getCenter);
			private static final ExAddOn.AddOnAttributeGetter<ExElement, Positionable.Vertical, Interpreted.Vertical, Def.Vertical> BOTTOM = ExAddOn.AddOnAttributeGetter
				.of(Vertical.class, Vertical::getTrailing, Interpreted.Vertical.class, Interpreted.Vertical::getTrailing,
					Positionable.Vertical.class, Positionable.Vertical::getTrailing);
			public Vertical(QonfigAddOn type, ExElement.Def<?> element) {
				super(true, type, element);
				element.forAttribute(type.getAttribute("top"), TOP);
				element.forAttribute(type.getAttribute("v-center"), V_CENTER);
				element.forAttribute(type.getAttribute("bottom"), BOTTOM);
			}

			@Override
			public Interpreted.Vertical interpret(ExElement.Interpreted<?> element) {
				return new Interpreted.Vertical(this, element);
			}
		}

		public static class Horizontal extends Def<Positionable.Horizontal> {
			private static final ExAddOn.AddOnAttributeGetter<ExElement, Positionable.Horizontal, Interpreted.Horizontal, Def.Horizontal> LEFT = ExAddOn.AddOnAttributeGetter
				.of(Horizontal.class, Horizontal::getLeading, Interpreted.Horizontal.class, Interpreted.Horizontal::getLeading,
					Positionable.Horizontal.class, Positionable.Horizontal::getLeading);
			private static final ExAddOn.AddOnAttributeGetter<ExElement, Positionable.Horizontal, Interpreted.Horizontal, Def.Horizontal> H_CENTER = ExAddOn.AddOnAttributeGetter
				.of(Horizontal.class, Horizontal::getCenter, Interpreted.Horizontal.class, Interpreted.Horizontal::getCenter,
					Positionable.Horizontal.class, Positionable.Horizontal::getCenter);
			private static final ExAddOn.AddOnAttributeGetter<ExElement, Positionable.Horizontal, Interpreted.Horizontal, Def.Horizontal> RIGHT = ExAddOn.AddOnAttributeGetter
				.of(Horizontal.class, Horizontal::getTrailing, Interpreted.Horizontal.class, Interpreted.Horizontal::getTrailing,
					Positionable.Horizontal.class, Positionable.Horizontal::getTrailing);

			public Horizontal(QonfigAddOn type, ExElement.Def<?> element) {
				super(false, type, element);
				element.forAttribute(type.getAttribute("left"), LEFT);
				element.forAttribute(type.getAttribute("h-center"), H_CENTER);
				element.forAttribute(type.getAttribute("right"), RIGHT);
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
		public void update(InterpretedModelSet models) throws ExpressoInterpretationException {
			theLeading = getDefinition().getLeading() == null ? null : getDefinition().getLeading().createSynthesizer().interpret();
			theCenter = getDefinition().getCenter() == null ? null : getDefinition().getCenter().createSynthesizer().interpret();
			theTrailing = getDefinition().getTrailing() == null ? null : getDefinition().getTrailing().createSynthesizer().interpret();
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
		theLeading.set(myInterpreted.getLeading() == null ? null : myInterpreted.getLeading().get(models), null);
		theCenter.set(myInterpreted.getCenter() == null ? null : myInterpreted.getCenter().get(models), null);
		theTrailing.set(myInterpreted.getTrailing() == null ? null : myInterpreted.getTrailing().get(models), null);
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
