package org.observe.quick;

import java.awt.Color;

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
import org.observe.quick.style.QuickCompiledStyle;
import org.observe.quick.style.QuickInterpretedStyle;
import org.observe.quick.style.QuickInterpretedStyleCache;
import org.observe.quick.style.QuickInterpretedStyleCache.Applications;
import org.observe.quick.style.QuickStyleAttribute;
import org.observe.quick.style.QuickStyleAttributeDef;
import org.observe.quick.style.QuickStyleSheet;
import org.observe.quick.style.QuickStyledElement;
import org.observe.quick.style.QuickTypeStyle;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

public interface QuickBorder extends QuickStyledElement {
	public interface Def<B extends QuickBorder> extends QuickStyledElement.Def<B> {
		@Override
		QuickBorderStyle.Def getStyle();

		Interpreted<? extends B> interpret(ExElement.Interpreted<?> parent);
	}

	public interface Interpreted<B extends QuickBorder> extends QuickStyledElement.Interpreted<B> {
		@Override
		QuickBorderStyle.Interpreted getStyle();

		void updateBorder(InterpretedExpressoEnv env) throws ExpressoInterpretationException;

		B create();
	}

	@Override
	QuickBorderStyle getStyle();

	@Override
	QuickBorder copy(ExElement parent);

	public class LineBorder extends QuickStyledElement.Abstract implements QuickBorder {
		public static final String LINE_BORDER = "line-border";

		@ExElementTraceable(toolkit = QuickCoreInterpretation.CORE,
			qonfigType = LINE_BORDER,
			interpretation = Interpreted.class,
			instance = LineBorder.class)
		public static class Def<B extends LineBorder> extends QuickStyledElement.Def.Abstract<B> implements QuickBorder.Def<B> {
			public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
				super(parent, type);
			}

			@Override
			public QuickBorderStyle.Def getStyle() {
				return (QuickBorderStyle.Def) super.getStyle();
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				super.doUpdate(session.asElement("styled"));
			}

			@Override
			public Interpreted<B> interpret(ExElement.Interpreted<?> parent) {
				return new Interpreted<>(this, parent);
			}

			@Override
			protected QuickBorderStyle.Def wrap(QuickInstanceStyle.Def parentStyle, QuickCompiledStyle style) {
				return new QuickBorderStyle.Def.Default(parentStyle, this, style);
			}
		}

		public static class Interpreted<B extends LineBorder> extends QuickStyledElement.Interpreted.Abstract<B>
		implements QuickBorder.Interpreted<B> {
			public Interpreted(Def<? super B> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public Def<? super B> getDefinition() {
				return (Def<? super B>) super.getDefinition();
			}

			@Override
			public QuickBorderStyle.Interpreted getStyle() {
				return (QuickBorderStyle.Interpreted) super.getStyle();
			}

			@Override
			public void updateBorder(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				update(env);
			}

			@Override
			public B create() {
				return (B) new LineBorder(getIdentity());
			}
		}

		public LineBorder(Object id) {
			super(id);
		}

		@Override
		public QuickBorderStyle getStyle() {
			return (QuickBorderStyle) super.getStyle();
		}

		@Override
		public LineBorder copy(ExElement parent) {
			return (LineBorder) super.copy(parent);
		}
	}

	public class TitledBorder extends LineBorder implements QuickTextElement {
		public static final String TITLED_BORDER = "titled-border";

		@ExElementTraceable(toolkit = QuickCoreInterpretation.CORE,
			qonfigType = TITLED_BORDER,
			interpretation = Interpreted.class,
			instance = TitledBorder.class)
		public static class Def<B extends TitledBorder> extends LineBorder.Def<B> implements QuickTextElement.Def<B> {
			private CompiledExpression theTitle;

			public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
				super(parent, type);
			}

			@QonfigAttributeGetter("title")
			public CompiledExpression getTitle() {
				return theTitle;
			}

			@Override
			public QuickTitledBorderStyle.Def getStyle() {
				return (QuickTitledBorderStyle.Def) super.getStyle();
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));
				theTitle = getAttributeExpression("title", session);
			}

			@Override
			public Interpreted<B> interpret(ExElement.Interpreted<?> parent) {
				return new Interpreted<>(this, parent);
			}

			@Override
			protected QuickTitledBorderStyle.Def wrap(QuickStyledElement.QuickInstanceStyle.Def parentStyle, QuickCompiledStyle style) {
				return new QuickTitledBorderStyle.Def(parentStyle, this, style);
			}
		}

		public static class Interpreted<B extends TitledBorder> extends LineBorder.Interpreted<B>
		implements QuickTextElement.Interpreted<B> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theTitle;

			public Interpreted(TitledBorder.Def<? super B> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public Def<? super B> getDefinition() {
				return (Def<? super B>) super.getDefinition();
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getTitle() {
				return theTitle;
			}

			@Override
			public QuickTitledBorderStyle.Interpreted getStyle() {
				return (QuickTitledBorderStyle.Interpreted) super.getStyle();
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);
				theTitle = interpret(getDefinition().getTitle(), ModelTypes.Value.STRING);
			}

			@Override
			public B create() {
				return (B) new TitledBorder(getIdentity());
			}
		}

		@Override
		public QuickTitledBorderStyle getStyle() {
			return (QuickTitledBorderStyle) super.getStyle();
		}

		private ModelValueInstantiator<SettableValue<String>> theTitleInstantiator;
		private SettableValue<SettableValue<String>> theTitle;

		public TitledBorder(Object id) {
			super(id);
			theTitle = SettableValue.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<String>> parameterized(String.class))
				.build();
		}

		public SettableValue<String> getTitle() {
			return SettableValue.flatten(theTitle);
		}

		@Override
		protected void doUpdate(ExElement.Interpreted<?> interpreted) {
			super.doUpdate(interpreted);
			TitledBorder.Interpreted<?> myInterpreted = (TitledBorder.Interpreted<?>) interpreted;
			theTitleInstantiator = myInterpreted.getTitle().instantiate();
		}

		@Override
		protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
			super.doInstantiate(myModels);
			theTitle.set(theTitleInstantiator.get(myModels), null);
		}

		@Override
		protected TitledBorder clone() {
			TitledBorder copy = (TitledBorder) super.clone();
			copy.theTitle = SettableValue
				.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<String>> parameterized(String.class)).build();
			return copy;
		}

		public static class QuickTitledBorderStyle extends QuickTextStyle.Abstract implements QuickBorderStyle {
			public static class Def extends QuickTextStyle.Def.Abstract implements QuickBorderStyle.Def {
				private final QuickStyleAttributeDef theBorderColor;
				private final QuickStyleAttributeDef theBorderThickness;

				public Def(QuickInstanceStyle.Def parent, TitledBorder.Def<?> styledElement, QuickCompiledStyle wrapped) {
					super(parent, styledElement, wrapped);
					QuickTypeStyle typeStyle = QuickStyledElement.getTypeStyle(wrapped.getStyleTypes(), getElement(),
						QuickCoreInterpretation.NAME, QuickCoreInterpretation.VERSION, "titled-border");
					theBorderColor = addApplicableAttribute(typeStyle.getAttribute("border-color"));
					theBorderThickness = addApplicableAttribute(typeStyle.getAttribute("thickness"));
				}

				@Override
				public QuickStyleAttributeDef getBorderColor() {
					return theBorderColor;
				}

				@Override
				public QuickStyleAttributeDef getBorderThickness() {
					return theBorderThickness;
				}

				@Override
				public Interpreted interpret(ExElement.Interpreted<?> parentEl, QuickInterpretedStyle parent, InterpretedExpressoEnv env)
					throws ExpressoInterpretationException {
					return new Interpreted(this, (TitledBorder.Interpreted<?>) parentEl, (QuickInstanceStyle.Interpreted) parent,
						getWrapped().interpret(parentEl, parent, env));
				}
			}

			public static class Interpreted extends QuickTextStyle.Interpreted.Abstract implements QuickBorderStyle.Interpreted {
				private QuickElementStyleAttribute<Color> theBorderColor;
				private QuickElementStyleAttribute<Integer> theBorderThickness;

				public Interpreted(Def definition, TitledBorder.Interpreted<?> styledElement, QuickInstanceStyle.Interpreted parent,
					QuickInterpretedStyle wrapped) {
					super(definition, styledElement, parent, wrapped);
				}

				@Override
				public Def getDefinition() {
					return (Def) super.getDefinition();
				}

				@Override
				public QuickElementStyleAttribute<Color> getBorderColor() {
					return theBorderColor;
				}

				@Override
				public QuickElementStyleAttribute<Integer> getBorderThickness() {
					return theBorderThickness;
				}

				@Override
				public void update(InterpretedExpressoEnv env, QuickStyleSheet.Interpreted styleSheet, Applications appCache)
					throws ExpressoInterpretationException {
					super.update(env, styleSheet, appCache);
					QuickInterpretedStyleCache cache = QuickInterpretedStyleCache.get(env);
					theBorderColor = get(cache.getAttribute(getDefinition().getBorderColor(), Color.class, env));
					theBorderThickness = get(cache.getAttribute(getDefinition().getBorderThickness(), Integer.class, env));
				}

				@Override
				public QuickTitledBorderStyle create(QuickStyledElement styledElement) {
					return new QuickTitledBorderStyle();
				}
			}

			private QuickStyleAttribute<Color> theBorderColorAttr;
			private ObservableValue<Color> theBorderColor;
			private QuickStyleAttribute<Integer> theBorderThicknessAttr;
			private ObservableValue<Integer> theBorderThickness;

			@Override
			public ObservableValue<Color> getBorderColor() {
				return theBorderColor;
			}

			@Override
			public ObservableValue<Integer> getBorderThickness() {
				return theBorderThickness;
			}

			@Override
			public void update(QuickInstanceStyle.Interpreted interpreted, QuickStyledElement styledElement) {
				super.update(interpreted, styledElement);

				Interpreted myInterpreted = (Interpreted) interpreted;
				theBorderColorAttr = myInterpreted.getBorderColor().getAttribute();
				theBorderThicknessAttr = myInterpreted.getBorderThickness().getAttribute();

				theBorderColor = getApplicableAttribute(theBorderColorAttr);
				theBorderThickness = getApplicableAttribute(theBorderThicknessAttr);
			}

			@Override
			public QuickTitledBorderStyle copy(QuickStyledElement styledElement) {
				QuickTitledBorderStyle copy = (QuickTitledBorderStyle) super.copy(styledElement);

				copy.theBorderColor = copy.getApplicableAttribute(theBorderColorAttr);
				copy.theBorderThickness = copy.getApplicableAttribute(theBorderThicknessAttr);

				return copy;
			}
		}
	}

	public interface QuickBorderStyle extends QuickInstanceStyle {
		public interface Def extends QuickInstanceStyle.Def {
			QuickStyleAttributeDef getBorderColor();

			QuickStyleAttributeDef getBorderThickness();

			@Override
			Interpreted interpret(ExElement.Interpreted<?> parentEl, QuickInterpretedStyle parent, InterpretedExpressoEnv env)
				throws ExpressoInterpretationException;

			public class Default extends QuickInstanceStyle.Def.Abstract implements Def {
				private QuickStyleAttributeDef theBorderColor;
				private QuickStyleAttributeDef theBorderThickness;

				public Default(QuickInstanceStyle.Def parent, QuickBorder.Def<?> styledElement, QuickCompiledStyle wrapped) {
					super(parent, styledElement, wrapped);
					QuickTypeStyle typeStyle = QuickStyledElement.getTypeStyle(wrapped.getStyleTypes(), getElement(),
						QuickCoreInterpretation.NAME, QuickCoreInterpretation.VERSION, "border");
					theBorderColor = addApplicableAttribute(typeStyle.getAttribute("border-color"));
					theBorderThickness = addApplicableAttribute(typeStyle.getAttribute("thickness"));
				}

				@Override
				public QuickStyleAttributeDef getBorderColor() {
					return theBorderColor;
				}

				@Override
				public QuickStyleAttributeDef getBorderThickness() {
					return theBorderThickness;
				}

				@Override
				public Interpreted interpret(ExElement.Interpreted<?> parentEl, QuickInterpretedStyle parent, InterpretedExpressoEnv env)
					throws ExpressoInterpretationException {
					return new Interpreted.Default(this, (QuickBorder.Interpreted<?>) parentEl, (QuickInstanceStyle.Interpreted) parent,
						getWrapped().interpret(parentEl, parent, env));
				}
			}
		}

		public interface Interpreted extends QuickInstanceStyle.Interpreted {
			QuickElementStyleAttribute<Color> getBorderColor();

			QuickElementStyleAttribute<Integer> getBorderThickness();

			@Override
			QuickBorderStyle create(QuickStyledElement styledElement);

			public class Default extends QuickInstanceStyle.Interpreted.Abstract implements Interpreted {
				private QuickElementStyleAttribute<Color> theBorderColor;
				private QuickElementStyleAttribute<Integer> theBorderThickness;

				public Default(Def definition, QuickBorder.Interpreted<?> styledElement, QuickInstanceStyle.Interpreted parent,
					QuickInterpretedStyle wrapped) {
					super(definition, styledElement, parent, wrapped);
				}

				@Override
				public Def getDefinition() {
					return (Def) super.getDefinition();
				}

				@Override
				public QuickElementStyleAttribute<Color> getBorderColor() {
					return theBorderColor;
				}

				@Override
				public QuickElementStyleAttribute<Integer> getBorderThickness() {
					return theBorderThickness;
				}

				@Override
				public void update(InterpretedExpressoEnv env, QuickStyleSheet.Interpreted styleSheet, Applications appCache)
					throws ExpressoInterpretationException {
					super.update(env, styleSheet, appCache);
					QuickInterpretedStyleCache cache = QuickInterpretedStyleCache.get(env);
					theBorderColor = get(cache.getAttribute(getDefinition().getBorderColor(), Color.class, env));
					theBorderThickness = get(cache.getAttribute(getDefinition().getBorderThickness(), Integer.class, env));
				}

				@Override
				public QuickBorderStyle create(QuickStyledElement styledElement) {
					return new QuickBorderStyle.Default();
				}
			}
		}

		ObservableValue<Color> getBorderColor();

		ObservableValue<Integer> getBorderThickness();

		public class Default extends QuickInstanceStyle.Abstract implements QuickBorderStyle {
			private QuickStyleAttribute<Color> theBorderColorAttr;
			private ObservableValue<Color> theBorderColor;
			private QuickStyleAttribute<Integer> theBorderThicknessAttr;
			private ObservableValue<Integer> theBorderThickness;

			@Override
			public ObservableValue<Color> getBorderColor() {
				return theBorderColor;
			}

			@Override
			public ObservableValue<Integer> getBorderThickness() {
				return theBorderThickness;
			}

			@Override
			public void update(QuickInstanceStyle.Interpreted interpreted, QuickStyledElement styledElement) {
				super.update(interpreted, styledElement);

				QuickBorderStyle.Interpreted myInterpreted = (QuickBorderStyle.Interpreted) interpreted;
				theBorderColorAttr = myInterpreted.getBorderColor().getAttribute();
				theBorderThicknessAttr = myInterpreted.getBorderThickness().getAttribute();

				theBorderColor = getApplicableAttribute(theBorderColorAttr);
				theBorderThickness = getApplicableAttribute(theBorderThicknessAttr);
			}

			@Override
			public Default copy(QuickStyledElement styledElement) {
				Default copy = (Default) super.copy(styledElement);

				copy.theBorderColor = copy.getApplicableAttribute(theBorderColorAttr);
				copy.theBorderThickness = copy.getApplicableAttribute(theBorderThicknessAttr);

				return copy;
			}
		}
	}
}
