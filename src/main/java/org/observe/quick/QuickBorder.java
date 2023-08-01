package org.observe.quick;

import java.awt.Color;
import java.util.List;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ElementTypeTraceability;
import org.observe.expresso.qonfig.ElementTypeTraceability.SingleTypeTraceability;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.quick.style.QuickCompiledStyle;
import org.observe.quick.style.QuickInterpretedStyle;
import org.observe.quick.style.QuickInterpretedStyleCache;
import org.observe.quick.style.QuickInterpretedStyleCache.Applications;
import org.observe.quick.style.QuickStyleAttributeDef;
import org.observe.quick.style.QuickStyleValue;
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

		B create(ExElement parent);
	}

	@Override
	QuickBorderStyle getStyle();

	public class LineBorder extends QuickStyledElement.Abstract implements QuickBorder {
		public static final String LINE_BORDER = "line-border";
		private static final SingleTypeTraceability<LineBorder, Interpreted<?>, Def<?>> TRACEABILITY = ElementTypeTraceability
			.getElementTraceability(QuickCoreInterpretation.NAME, QuickCoreInterpretation.VERSION, LINE_BORDER, Def.class,
				Interpreted.class, LineBorder.class);

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
				withTraceability(TRACEABILITY.validate(session.getFocusType(), session.reporting()));
				super.doUpdate(session.asElement("styled"));
			}

			@Override
			public Interpreted<B> interpret(ExElement.Interpreted<?> parent) {
				return new Interpreted<>(this, parent);
			}

			@Override
			protected QuickBorderStyle.Def wrap(QuickInstanceStyle.Def parentStyle, QuickCompiledStyle style) {
				return new QuickBorderStyle.Def.Default(parentStyle, style);
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
			public B create(ExElement parent) {
				return (B) new LineBorder(this, parent);
			}
		}

		public LineBorder(Interpreted<?> interpreted, ExElement parent) {
			super(interpreted, parent);
		}

		@Override
		public QuickBorderStyle getStyle() {
			return (QuickBorderStyle) super.getStyle();
		}
	}

	public class TitledBorder extends LineBorder implements QuickTextElement {
		public static final String TITLED_BORDER = "titled-border";
		private static final SingleTypeTraceability<LineBorder, Interpreted<?>, Def<?>> TRACEABILITY = ElementTypeTraceability
			.getElementTraceability(QuickCoreInterpretation.NAME, QuickCoreInterpretation.VERSION, TITLED_BORDER, Def.class,
				Interpreted.class, LineBorder.class);

		public static class Def<B extends TitledBorder> extends LineBorder.Def<B> {
			private CompiledExpression theTitle;

			public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
				super(parent, type);
			}

			@QonfigAttributeGetter("title")
			public CompiledExpression getTitle() {
				return theTitle;
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				withTraceability(TRACEABILITY.validate(session.getFocusType(), session.reporting()));
				super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));
				theTitle = session.getAttributeExpression("title");
			}

			@Override
			public Interpreted<B> interpret(ExElement.Interpreted<?> parent) {
				return new Interpreted<>(this, parent);
			}

			@Override
			protected QuickTitledBorderStyle.Def wrap(QuickStyledElement.QuickInstanceStyle.Def parentStyle, QuickCompiledStyle style) {
				return new QuickTitledBorderStyle.Def(parentStyle, style);
			}
		}

		public static class Interpreted<B extends TitledBorder> extends LineBorder.Interpreted<B> {
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
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);
				theTitle = getDefinition().getTitle().interpret(ModelTypes.Value.STRING, getExpressoEnv());
			}

			@Override
			public B create(ExElement parent) {
				return (B) new TitledBorder(this, parent);
			}
		}

		@Override
		public QuickTitledBorderStyle getStyle() {
			return (QuickTitledBorderStyle) super.getStyle();
		}

		private final SettableValue<SettableValue<String>> theTitle;

		public TitledBorder(TitledBorder.Interpreted<?> interpreted, ExElement parent) {
			super(interpreted, parent);
			theTitle = SettableValue.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<String>> parameterized(String.class))
				.build();
		}

		public SettableValue<String> getTitle() {
			return SettableValue.flatten(theTitle);
		}

		@Override
		protected void updateModel(ExElement.Interpreted<?> interpreted, ModelSetInstance myModels) throws ModelInstantiationException {
			super.updateModel(interpreted, myModels);
			TitledBorder.Interpreted<?> myInterpreted = (TitledBorder.Interpreted<?>) interpreted;
			theTitle.set(myInterpreted.getTitle().get(myModels), null);
		}

		public static class QuickTitledBorderStyle extends QuickTextStyle.Abstract implements QuickBorderStyle {
			public static class Def extends QuickTextStyle.Def.Abstract implements QuickBorderStyle.Def {
				private final QuickStyleAttributeDef theBorderColor;
				private final QuickStyleAttributeDef theBorderThickness;

				public Def(QuickCompiledStyle parent, QuickCompiledStyle wrapped) {
					super(parent, wrapped);
					QuickTypeStyle typeStyle = QuickStyledElement.getTypeStyle(wrapped.getStyleTypes(), getElement(),
						QuickCoreInterpretation.NAME, QuickCoreInterpretation.VERSION, "titled-border");
					theBorderColor = typeStyle.getAttribute("border-color");
					theBorderThickness = typeStyle.getAttribute("thickness");
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
				public void update(List<QuickStyleValue> declaredValues, List<QuickStyleValue> otherValues)
					throws QonfigInterpretationException {
					super.update(declaredValues, otherValues);
				}

				@Override
				public Interpreted interpret(org.observe.expresso.qonfig.ExElement.Interpreted<?> parentEl, QuickInterpretedStyle parent,
					InterpretedExpressoEnv env) throws ExpressoInterpretationException {
					return new Interpreted(this, parent, getWrapped().interpret(parentEl, parent, env));
				}
			}

			public static class Interpreted extends QuickTextStyle.Interpreted.Abstract implements QuickBorderStyle.Interpreted {
				private QuickElementStyleAttribute<Color> theBorderColor;
				private QuickElementStyleAttribute<Integer> theBorderThickness;

				public Interpreted(Def definition, QuickInterpretedStyle parent, QuickInterpretedStyle wrapped) {
					super(definition, parent, wrapped);
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
				public void update(InterpretedExpressoEnv env, Applications appCache) throws ExpressoInterpretationException {
					super.update(env, appCache);
					QuickInterpretedStyleCache cache = QuickInterpretedStyleCache.get(env);
					theBorderColor = get(cache.getAttribute(getDefinition().getBorderColor(), Color.class, env));
					theBorderThickness = get(cache.getAttribute(getDefinition().getBorderThickness(), Integer.class, env));
				}

				@Override
				public QuickTitledBorderStyle create(QuickStyledElement styledElement) {
					return new QuickTitledBorderStyle(getId(), (TitledBorder) styledElement);
				}
			}

			private final SettableValue<ObservableValue<Color>> theBorderColor;
			private final SettableValue<ObservableValue<Integer>> theBorderThickness;

			public QuickTitledBorderStyle(Object interpretedId, TitledBorder styledElement) {
				super(interpretedId, styledElement);
				theBorderColor = SettableValue
					.build(TypeTokens.get().keyFor(ObservableValue.class).<ObservableValue<Color>> parameterized(Color.class)).build();
				theBorderThickness = SettableValue
					.build(TypeTokens.get().keyFor(ObservableValue.class).<ObservableValue<Integer>> parameterized(Integer.class)).build();
			}

			@Override
			public ObservableValue<Color> getBorderColor() {
				return ObservableValue.flatten(theBorderColor);
			}

			@Override
			public ObservableValue<Integer> getBorderThickness() {
				return ObservableValue.flatten(theBorderThickness);
			}

			@Override
			public void update(QuickInstanceStyle.Interpreted interpreted, ModelSetInstance models) throws ModelInstantiationException {
				super.update(interpreted, models);
				QuickTitledBorderStyle.Interpreted myInterpreted = (QuickTitledBorderStyle.Interpreted) interpreted;
				theBorderColor.set(myInterpreted.getBorderColor().evaluate(models), null);
				theBorderThickness.set(myInterpreted.getBorderThickness().evaluate(models), null);
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

			public class Default extends QuickCompiledStyle.Wrapper implements Def {
				private final Object theId;

				private QuickStyleAttributeDef theBorderColor;
				private QuickStyleAttributeDef theBorderThickness;

				public Default(QuickCompiledStyle parent, QuickCompiledStyle wrapped) {
					super(parent, wrapped);
					theId = new Object();
					QuickTypeStyle typeStyle = QuickStyledElement.getTypeStyle(wrapped.getStyleTypes(), getElement(),
						QuickCoreInterpretation.NAME, QuickCoreInterpretation.VERSION, "border");
					theBorderColor = typeStyle.getAttribute("border-color");
					theBorderThickness = typeStyle.getAttribute("thickness");
				}

				@Override
				public Object getId() {
					return theId;
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
				public void update(List<QuickStyleValue> declaredValues, List<QuickStyleValue> otherValues)
					throws QonfigInterpretationException {
					super.update(declaredValues, otherValues);
				}

				@Override
				public Interpreted interpret(ExElement.Interpreted<?> parentEl, QuickInterpretedStyle parent, InterpretedExpressoEnv env)
					throws ExpressoInterpretationException {
					return new Interpreted.Default(this, parent, getWrapped().interpret(parentEl, parent, env));
				}
			}
		}

		public interface Interpreted extends QuickInstanceStyle.Interpreted {
			QuickElementStyleAttribute<Color> getBorderColor();

			QuickElementStyleAttribute<Integer> getBorderThickness();

			@Override
			QuickBorderStyle create(QuickStyledElement styledElement);

			public class Default extends QuickInterpretedStyle.Wrapper implements Interpreted {
				private final Def theDefinition;
				private QuickElementStyleAttribute<Color> theBorderColor;
				private QuickElementStyleAttribute<Integer> theBorderThickness;

				public Default(Def definition, QuickInterpretedStyle parent, QuickInterpretedStyle wrapped) {
					super(parent, wrapped);
					theDefinition = definition;
				}

				@Override
				public Def getDefinition() {
					return theDefinition;
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
				public void update(InterpretedExpressoEnv env, Applications appCache) throws ExpressoInterpretationException {
					super.update(env, appCache);
					QuickInterpretedStyleCache cache = QuickInterpretedStyleCache.get(env);
					theBorderColor = get(cache.getAttribute(getDefinition().getBorderColor(), Color.class, env));
					theBorderThickness = get(cache.getAttribute(getDefinition().getBorderThickness(), Integer.class, env));
				}

				@Override
				public QuickBorderStyle create(QuickStyledElement styledElement) {
					return new QuickBorderStyle.Default(getId(), (QuickBorder) styledElement);
				}
			}
		}

		ObservableValue<Color> getBorderColor();

		ObservableValue<Integer> getBorderThickness();

		public class Default extends QuickInstanceStyle.Abstract implements QuickBorderStyle {
			private final SettableValue<ObservableValue<Color>> theBorderColor;
			private final SettableValue<ObservableValue<Integer>> theBorderThickness;

			public Default(Object interpretedId, QuickBorder border) {
				super(interpretedId, border);
				theBorderColor = SettableValue
					.build(TypeTokens.get().keyFor(ObservableValue.class).<ObservableValue<Color>> parameterized(Color.class)).build();
				theBorderThickness = SettableValue
					.build(TypeTokens.get().keyFor(ObservableValue.class).<ObservableValue<Integer>> parameterized(Integer.class)).build();
			}

			@Override
			public ObservableValue<Color> getBorderColor() {
				return ObservableValue.flatten(theBorderColor);
			}

			@Override
			public ObservableValue<Integer> getBorderThickness() {
				return ObservableValue.flatten(theBorderThickness);
			}

			@Override
			public void update(QuickInstanceStyle.Interpreted interpreted, ModelSetInstance models) throws ModelInstantiationException {
				QuickBorderStyle.Interpreted myInterpreted = (QuickBorderStyle.Interpreted) interpreted;
				theBorderColor.set(myInterpreted.getBorderColor().evaluate(models), null);
				theBorderThickness.set(myInterpreted.getBorderThickness().evaluate(models), null);
			}
		}
	}
}
