package org.observe.quick.draw;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.quick.QuickWithBackground;
import org.observe.quick.style.QuickCompiledStyle;
import org.observe.quick.style.QuickInterpretedStyle;
import org.observe.quick.style.QuickInterpretedStyleCache;
import org.observe.quick.style.QuickInterpretedStyleCache.Applications;
import org.observe.quick.style.QuickStyleAttribute;
import org.observe.quick.style.QuickStyleAttributeDef;
import org.observe.quick.style.QuickStyleSheet;
import org.observe.quick.style.QuickStyled;
import org.observe.quick.style.QuickStyled.QuickInstanceStyle;
import org.observe.quick.style.QuickTypeStyle;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

public abstract class QuickShape extends QuickWithBackground.Abstract {
	public static final String SHAPE = "shape";

	@ExElementTraceable(toolkit = QuickDrawInterpretation.DRAW,
		qonfigType = SHAPE,
		interpretation = Interpreted.class,
		instance = QuickShape.class)
	public static abstract class Def<E extends QuickShape> extends QuickWithBackground.Def.Abstract<E> {
		private CompiledExpression theShapeId;

		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		@QonfigAttributeGetter("shape-id")
		public CompiledExpression getShapeId() {
			return theShapeId;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);

			theShapeId = getAttributeExpression("shape-id", session);
		}

		@Override
		public QuickShapeStyle.Def wrap(QuickInstanceStyle.Def parentStyle, QuickCompiledStyle style) {
			return new QuickShapeStyle.Def.Default(parentStyle, this, style);
		}

		@Override
		public QuickShapeStyle.Def getStyle() {
			return (QuickShapeStyle.Def) super.getStyle();
		}

		public abstract Interpreted<? extends E> interpret(ExElement.Interpreted<?> parent);
	}

	public static abstract class Interpreted<E extends QuickShape> extends QuickWithBackground.Interpreted.Abstract<E> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<?>> theShapeId;

		Interpreted(Def<? super E> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def<? super E> getDefinition() {
			return (Def<? super E>) super.getDefinition();
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<?>> getShapeId() {
			return theShapeId;
		}

		@Override
		public QuickShapeStyle.Interpreted getStyle() {
			return (QuickShapeStyle.Interpreted) super.getStyle();
		}

		@Override
		public void updateElement(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			super.updateElement(env);
			theShapeId = interpret(getDefinition().getShapeId(), ModelTypes.Value.any());
		}

		public abstract QuickShape create();
	}

	private ModelValueInstantiator<SettableValue<?>> theShapeIdInstantiator;

	private SettableValue<SettableValue<?>> theShapeId;

	QuickShape(Object id) {
		super(id);
		theShapeId = SettableValue.create();
	}

	@Override
	public QuickShapeStyle getStyle() {
		return (QuickShapeStyle) super.getStyle();
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);

		QuickShape.Interpreted<?> myInterpreted = (QuickShape.Interpreted<?>) interpreted;
		theShapeIdInstantiator = myInterpreted.getShapeId().instantiate();
	}

	@Override
	protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		super.doInstantiate(myModels);

		theShapeId.set(theShapeIdInstantiator.get(myModels));
	}

	@Override
	public QuickShape copy(ExElement parent) {
		QuickShape copy = (QuickShape) super.copy(parent);

		copy.theShapeId = SettableValue.create();

		return copy;
	}

	public interface QuickShapeStyle extends QuickWithBackground.QuickBackgroundStyle {
		public static interface Def extends QuickWithBackground.QuickBackgroundStyle.Def {
			QuickStyleAttributeDef getOpacity();

			public static class Default extends QuickWithBackground.QuickBackgroundStyle.Def.Default implements Def {
				private final QuickStyleAttributeDef theOpacity;

				public Default(QuickStyled.QuickInstanceStyle.Def parent, QuickShape.Def styledElement, QuickCompiledStyle wrapped) {
					super(parent, styledElement, wrapped);
					QuickTypeStyle typeStyle = QuickStyled.getTypeStyle(wrapped.getStyleTypes(), getElement(), QuickDrawInterpretation.NAME,
						QuickDrawInterpretation.VERSION, "shape");
					theOpacity = addApplicableAttribute(typeStyle.getAttribute("opacity"));
				}

				@Override
				public QuickStyleAttributeDef getOpacity() {
					return theOpacity;
				}

				@Override
				public QuickShapeStyle.Interpreted interpret(ExElement.Interpreted<?> parentEl, QuickInterpretedStyle parent,
					InterpretedExpressoEnv env) throws ExpressoInterpretationException {
					return new QuickShapeStyle.Interpreted.Default(this, (QuickShape.Interpreted<?>) parentEl,
						(QuickInstanceStyle.Interpreted) parent, getWrapped().interpret(parentEl, parent, env));
				}
			}
		}

		public static interface Interpreted extends QuickWithBackground.QuickBackgroundStyle.Interpreted {
			QuickElementStyleAttribute<Float> getOpacity();

			public static class Default extends QuickWithBackground.QuickBackgroundStyle.Interpreted.Default implements Interpreted {
				private QuickElementStyleAttribute<Float> theOpacity;

				public Default(QuickShapeStyle.Def definition, QuickShape.Interpreted<?> styledElement,
					QuickStyled.QuickInstanceStyle.Interpreted parent, QuickInterpretedStyle wrapped) {
					super(definition, styledElement, parent, wrapped);
				}

				@Override
				public QuickShapeStyle.Def getDefinition() {
					return (QuickShapeStyle.Def) super.getDefinition();
				}

				@Override
				public QuickElementStyleAttribute<Float> getOpacity() {
					return theOpacity;
				}

				@Override
				public void update(InterpretedExpressoEnv env, QuickStyleSheet.Interpreted styleSheet, Applications appCache)
					throws ExpressoInterpretationException {
					super.update(env, styleSheet, appCache);
					QuickInterpretedStyleCache cache = QuickInterpretedStyleCache.get(env);
					theOpacity = get(cache.getAttribute(getDefinition().getOpacity(), float.class, env));
				}

				@Override
				public QuickShapeStyle create(QuickStyled styled) {
					return new QuickShapeStyle.Default();
				}
			}
		}

		public ObservableValue<Float> getOpacity();

		public static class Default extends QuickWithBackground.QuickBackgroundStyle.Default implements QuickShapeStyle {
			private QuickStyleAttribute<Float> theOpacityAttr;
			private ObservableValue<Float> theOpacity;

			@Override
			public ObservableValue<Float> getOpacity() {
				return theOpacity;
			}

			@Override
			public void update(QuickInstanceStyle.Interpreted interpreted, QuickStyled styled) throws ModelInstantiationException {
				super.update(interpreted, styled);

				QuickShapeStyle.Interpreted myInterpreted = (QuickShapeStyle.Interpreted) interpreted;

				theOpacityAttr = myInterpreted.getOpacity().getAttribute();

				theOpacity = getApplicableAttribute(theOpacityAttr);
			}

			@Override
			public QuickShapeStyle.Default copy(QuickStyled styled) {
				QuickShapeStyle.Default copy = (QuickShapeStyle.Default) super.copy(styled);

				copy.theOpacity = copy.getApplicableAttribute(theOpacityAttr);

				return copy;
			}
		}
	}
}
