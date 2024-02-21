package org.observe.quick;

import java.awt.Color;

import javax.swing.border.Border;

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
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

/** A border around a widget on the screen */
public interface QuickBorder extends QuickStyledElement {
	/**
	 * A definition to create a border
	 *
	 * @param <B> The type of the border
	 */
	public interface Def<B extends QuickBorder> extends QuickStyledElement.Def<B> {
		@Override
		QuickBorderStyle.Def getStyle();

		/**
		 * Interprets this definition
		 *
		 * @param parent The parent element for the interpreted border
		 * @return The interpreted border
		 */
		Interpreted<? extends B> interpret(ExElement.Interpreted<?> parent);
	}

	/**
	 * An interpretation of a border
	 *
	 * @param <B> The type of the border
	 */
	public interface Interpreted<B extends QuickBorder> extends QuickStyledElement.Interpreted<B> {
		@Override
		QuickBorderStyle.Interpreted getStyle();

		/**
		 * @param env The expresso environment to faciliate expression interpretation
		 * @throws ExpressoInterpretationException If interpretation fails
		 */
		void updateBorder(InterpretedExpressoEnv env) throws ExpressoInterpretationException;

		/** @return The border instance */
		B create();
	}

	@Override
	QuickBorderStyle getStyle();

	@Override
	QuickBorder copy(ExElement parent);

	/** A simple line border */
	public class LineBorder extends QuickStyledElement.Abstract implements QuickBorder {
		/** The XML name of the LineBorder type */
		public static final String LINE_BORDER = "line-border";

		/**
		 * The definition to create a line border
		 *
		 * @param <B> The sub-type of line border to create
		 */
		@ExElementTraceable(toolkit = QuickCoreInterpretation.CORE,
			qonfigType = LINE_BORDER,
			interpretation = Interpreted.class,
			instance = LineBorder.class)
		public static class Def<B extends LineBorder> extends QuickStyledElement.Def.Abstract<B> implements QuickBorder.Def<B> {
			/**
			 * @param parent The parent definition of the border
			 * @param type The Qonfig type of this element
			 */
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

		/**
		 * The interpretation of a line border
		 *
		 * @param <B> THe sub-type of line border to create
		 */
		public static class Interpreted<B extends LineBorder> extends QuickStyledElement.Interpreted.Abstract<B>
		implements QuickBorder.Interpreted<B> {
			/**
			 * @param definition The definition to interpret
			 * @param parent The parent element of the border
			 */
			protected Interpreted(Def<? super B> definition, ExElement.Interpreted<?> parent) {
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

		/** @param id The element identifier for the border */
		protected LineBorder(Object id) {
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

	/** A line border with a text title */
	public class TitledBorder extends LineBorder implements QuickTextElement {
		/** The XML name of the TitledBorder type */
		public static final String TITLED_BORDER = "titled-border";

		/**
		 * The definition to create a titled border
		 *
		 * @param <B> The sub-type of titled border to create
		 */
		@ExElementTraceable(toolkit = QuickCoreInterpretation.CORE,
			qonfigType = TITLED_BORDER,
			interpretation = Interpreted.class,
			instance = TitledBorder.class)
		public static class Def<B extends TitledBorder> extends LineBorder.Def<B> implements QuickTextElement.Def<B> {
			private CompiledExpression theTitle;

			/**
			 * @param parent The parent definition of the border
			 * @param type The Qonfig type of this element
			 */
			public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
				super(parent, type);
			}

			/** @return The title expression */
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

		/**
		 * An interpretation of a titled border
		 *
		 * @param <B> The sub-type of titled border to create
		 */
		public static class Interpreted<B extends TitledBorder> extends LineBorder.Interpreted<B>
		implements QuickTextElement.Interpreted<B> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theTitle;

			/**
			 * @param definition The definition to interpret
			 * @param parent The parent element of the border
			 */
			protected Interpreted(TitledBorder.Def<? super B> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public Def<? super B> getDefinition() {
				return (Def<? super B>) super.getDefinition();
			}

			/** @return The interpreted title expression */
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

		/** @param id The element identifier for this border */
		public TitledBorder(Object id) {
			super(id);
			theTitle = SettableValue.<SettableValue<String>> build().build();
		}

		/** @return The title for the border */
		public SettableValue<String> getTitle() {
			return SettableValue.flatten(theTitle);
		}

		@Override
		protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
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
			copy.theTitle = SettableValue.<SettableValue<String>> build().build();
			return copy;
		}

		/** Style object for {@link TitledBorder}s */
		public static class QuickTitledBorderStyle extends QuickTextStyle.Abstract implements QuickBorderStyle {
			/** The definition of a titled border's style */
			public static class Def extends QuickTextStyle.Def.Abstract implements QuickBorderStyle.Def {
				private final QuickStyleAttributeDef theBorderColor;
				private final QuickStyleAttributeDef theBorderThickness;

				/**
				 * @param parent The parent style for this style to inherit from
				 * @param styledElement The border element being styled
				 * @param wrapped The generic compiled style that this style class wraps
				 */
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

			/** The interpretation of a titled border's style */
			public static class Interpreted extends QuickTextStyle.Interpreted.Abstract implements QuickBorderStyle.Interpreted {
				private QuickElementStyleAttribute<Color> theBorderColor;
				private QuickElementStyleAttribute<Integer> theBorderThickness;

				/**
				 * @param definition The style definition to interpret
				 * @param styledElement The border element being styled
				 * @param parent The parent style for this style to inherit from
				 * @param wrapped The generic interpreted style that this style class wraps
				 */
				protected Interpreted(Def definition, TitledBorder.Interpreted<?> styledElement, QuickInstanceStyle.Interpreted parent,
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
			public void update(QuickInstanceStyle.Interpreted interpreted, QuickStyledElement styledElement)
				throws ModelInstantiationException {
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

	/** Style object for {@link Border}s */
	public interface QuickBorderStyle extends QuickInstanceStyle {
		/** The definition of a border's style */
		public interface Def extends QuickInstanceStyle.Def {
			/** @return The style attribute for the color of the border */
			QuickStyleAttributeDef getBorderColor();

			/** @return The style attribute for the thickness of the border */
			QuickStyleAttributeDef getBorderThickness();

			@Override
			Interpreted interpret(ExElement.Interpreted<?> parentEl, QuickInterpretedStyle parent, InterpretedExpressoEnv env)
				throws ExpressoInterpretationException;

			/** Default {@link QuickBorderStyle} definition implementation */
			public class Default extends QuickInstanceStyle.Def.Abstract implements Def {
				private QuickStyleAttributeDef theBorderColor;
				private QuickStyleAttributeDef theBorderThickness;

				/**
				 * @param parent The parent style for this style to inherit from
				 * @param styledElement The border element being styled
				 * @param wrapped The generic compiled style that this style class wraps
				 */
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

		/** The interpretation of a border's style */
		public interface Interpreted extends QuickInstanceStyle.Interpreted {
			/** @return The style attribute for the color of the border */
			QuickElementStyleAttribute<Color> getBorderColor();

			/** @return The style attribute for the thickness of the border */
			QuickElementStyleAttribute<Integer> getBorderThickness();

			@Override
			QuickBorderStyle create(QuickStyledElement styledElement);

			/** Default {@link QuickBorderStyle} interpretation implementation */
			public class Default extends QuickInstanceStyle.Interpreted.Abstract implements Interpreted {
				private QuickElementStyleAttribute<Color> theBorderColor;
				private QuickElementStyleAttribute<Integer> theBorderThickness;

				/**
				 * @param definition The style definition to interpret
				 * @param styledElement The border element being styled
				 * @param parent The parent style for this style to inherit from
				 * @param wrapped The generic interpreted style that this style class wraps
				 */
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

		/** @return The color of the border */
		ObservableValue<Color> getBorderColor();

		/** @return The thickness of the border */
		ObservableValue<Integer> getBorderThickness();

		/** Default {@link QuickBorderStyle} implementation */
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
			public void update(QuickInstanceStyle.Interpreted interpreted, QuickStyledElement styledElement)
				throws ModelInstantiationException {
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
