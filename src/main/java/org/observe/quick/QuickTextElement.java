package org.observe.quick;

import java.awt.Color;

import org.observe.ObservableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.qonfig.ExElement;
import org.observe.quick.style.QuickCompiledStyle;
import org.observe.quick.style.QuickInterpretedStyle;
import org.observe.quick.style.QuickInterpretedStyleCache;
import org.observe.quick.style.QuickInterpretedStyleCache.Applications;
import org.observe.quick.style.QuickStyleAttribute;
import org.observe.quick.style.QuickStyleAttributeDef;
import org.observe.quick.style.QuickStyleSheet;
import org.observe.quick.style.QuickStyledElement;
import org.observe.quick.style.QuickTypeStyle;

/** A Quick element that may display text */
public interface QuickTextElement extends QuickStyledElement {
	/**
	 * Definition of a {@link QuickTextElement}
	 *
	 * @param <E> The type of element
	 */
	public interface Def<E extends QuickTextElement> extends QuickStyledElement.Def<E> {
		@Override
		QuickTextStyle.Def getStyle();
	}

	/**
	 * Interpretation of a {@link QuickTextElement}
	 *
	 * @param <E> The type of element
	 */
	public interface Interpreted<E extends QuickTextElement> extends QuickStyledElement.Interpreted<E> {
		@Override
		QuickTextStyle.Interpreted getStyle();
	}

	/** Style for a text element */
	public interface QuickTextStyle extends QuickInstanceStyle {
		/** Definition of a text element style */
		public interface Def extends QuickInstanceStyle.Def {
			/** @return The style attribute for the text's color */
			QuickStyleAttributeDef getFontColor();

			/** @return The style attribute for the text's size */
			QuickStyleAttributeDef getFontSize();

			/** @return The style attribute for the text's weight, or line thickness */
			QuickStyleAttributeDef getFontWeight();

			/** @return The style attribute for the text's slant */
			QuickStyleAttributeDef getFontSlant();

			/** @return The style attribute for whether the text is underlined */
			QuickStyleAttributeDef isUnderline();

			/** @return The style attribute for whether the text is struck-through */
			QuickStyleAttributeDef isStrikeThrough();

			/** @return The style attribute for whether the text is super-script */
			QuickStyleAttributeDef isSuperScript();

			/** @return The style attribute for whether the text is sub-script */
			QuickStyleAttributeDef isSubScript();

			@Override
			Interpreted interpret(ExElement.Interpreted<?> parentEl, QuickInterpretedStyle parent, InterpretedExpressoEnv env)
				throws ExpressoInterpretationException;

			/** Abstract {@link QuickTextStyle} definition implementation */
			public abstract class Abstract extends QuickInstanceStyle.Def.Abstract implements Def {
				private final QuickStyleAttributeDef theFontColor;
				private final QuickStyleAttributeDef theFontSize;
				private final QuickStyleAttributeDef theFontWeight;
				private final QuickStyleAttributeDef theFontSlant;
				private final QuickStyleAttributeDef isUnderline;
				private final QuickStyleAttributeDef isStrikeThrough;
				private final QuickStyleAttributeDef isSuperScript;
				private final QuickStyleAttributeDef isSubScript;

				/**
				 * @param parent The parent style for this style to inherit from
				 * @param styledElement The text element being styled
				 * @param wrapped The generic compiled style that this style class wraps
				 */
				protected Abstract(QuickInstanceStyle.Def parent, QuickTextElement.Def<?> styledElement, QuickCompiledStyle wrapped) {
					super(parent, styledElement, wrapped);
					QuickTypeStyle typeStyle = QuickStyledElement.getTypeStyle(wrapped.getStyleTypes(), wrapped.getElement(),
						QuickCoreInterpretation.NAME, QuickCoreInterpretation.VERSION, "with-text");
					theFontColor = addApplicableAttribute(typeStyle.getAttribute("font-color"));
					theFontSize = addApplicableAttribute(typeStyle.getAttribute("font-size"));
					theFontWeight = addApplicableAttribute(typeStyle.getAttribute("font-weight"));
					theFontSlant = addApplicableAttribute(typeStyle.getAttribute("font-slant"));
					isUnderline = addApplicableAttribute(typeStyle.getAttribute("underline"));
					isStrikeThrough = addApplicableAttribute(typeStyle.getAttribute("strike-through"));
					isSuperScript = addApplicableAttribute(typeStyle.getAttribute("super-script"));
					isSubScript = addApplicableAttribute(typeStyle.getAttribute("sub-script"));
				}

				@Override
				public QuickStyleAttributeDef getFontColor() {
					return theFontColor;
				}

				@Override
				public QuickStyleAttributeDef getFontSize() {
					return theFontSize;
				}

				@Override
				public QuickStyleAttributeDef getFontWeight() {
					return theFontWeight;
				}

				@Override
				public QuickStyleAttributeDef getFontSlant() {
					return theFontSlant;
				}

				@Override
				public QuickStyleAttributeDef isUnderline() {
					return isUnderline;
				}

				@Override
				public QuickStyleAttributeDef isStrikeThrough() {
					return isStrikeThrough;
				}

				@Override
				public QuickStyleAttributeDef isSuperScript() {
					return isSuperScript;
				}

				@Override
				public QuickStyleAttributeDef isSubScript() {
					return isSubScript;
				}

				@Override
				public abstract Interpreted interpret(ExElement.Interpreted<?> parentEl, QuickInterpretedStyle parent,
					InterpretedExpressoEnv env) throws ExpressoInterpretationException;
			}
		}

		/** Interpretation of a text element style */
		public interface Interpreted extends QuickInstanceStyle.Interpreted {
			@Override
			QuickTextStyle create(QuickStyledElement styledElement);

			/** @return The style attribute for the text's color */
			QuickElementStyleAttribute<Color> getFontColor();

			/** @return The style attribute for the text's size */
			QuickElementStyleAttribute<Double> getFontSize();

			/** @return The style attribute for the text's weight, or line thickness */
			QuickElementStyleAttribute<Double> getFontWeight();

			/** @return The style attribute for the text's slant */
			QuickElementStyleAttribute<Double> getFontSlant();

			/** @return The style attribute for whether the text is underlined */
			QuickElementStyleAttribute<Boolean> isUnderline();

			/** @return The style attribute for whether the text is struck-through */
			QuickElementStyleAttribute<Boolean> isStrikeThrough();

			/** @return The style attribute for whether the text is super-script */
			QuickElementStyleAttribute<Boolean> isSuperScript();

			/** @return The style attribute for whether the text is sub-script */
			QuickElementStyleAttribute<Boolean> isSubScript();

			/** Abstract {@link QuickTextStyle} interpretation implementation */
			public abstract class Abstract extends QuickInstanceStyle.Interpreted.Abstract implements Interpreted {
				private QuickElementStyleAttribute<Color> theFontColor;
				private QuickElementStyleAttribute<Double> theFontSize;
				private QuickElementStyleAttribute<Double> theFontWeight;
				private QuickElementStyleAttribute<Double> theFontSlant;
				private QuickElementStyleAttribute<Boolean> isUnderline;
				private QuickElementStyleAttribute<Boolean> isStrikeThrough;
				private QuickElementStyleAttribute<Boolean> isSuperScript;
				private QuickElementStyleAttribute<Boolean> isSubScript;

				/**
				 * @param definition The style definition to interpret
				 * @param styledElement The text element being styled
				 * @param parent The parent style for this style to inherit from
				 * @param wrapped The generic interpreted style that this style class wraps
				 */
				protected Abstract(Def definition, QuickTextElement.Interpreted<?> styledElement, QuickInstanceStyle.Interpreted parent,
					QuickInterpretedStyle wrapped) {
					super(definition, styledElement, parent, wrapped);
				}

				@Override
				public Def getDefinition() {
					return (Def) super.getDefinition();
				}

				@Override
				public QuickElementStyleAttribute<Color> getFontColor() {
					return theFontColor;
				}

				@Override
				public QuickElementStyleAttribute<Double> getFontSize() {
					return theFontSize;
				}

				@Override
				public QuickElementStyleAttribute<Double> getFontWeight() {
					return theFontWeight;
				}

				@Override
				public QuickElementStyleAttribute<Double> getFontSlant() {
					return theFontSlant;
				}

				@Override
				public QuickElementStyleAttribute<Boolean> isUnderline() {
					return isUnderline;
				}

				@Override
				public QuickElementStyleAttribute<Boolean> isStrikeThrough() {
					return isStrikeThrough;
				}

				@Override
				public QuickElementStyleAttribute<Boolean> isSuperScript() {
					return isSuperScript;
				}

				@Override
				public QuickElementStyleAttribute<Boolean> isSubScript() {
					return isSubScript;
				}

				@Override
				public void update(InterpretedExpressoEnv env, QuickStyleSheet.Interpreted styleSheet, Applications appCache)
					throws ExpressoInterpretationException {
					super.update(env, styleSheet, appCache);
					QuickInterpretedStyleCache cache = QuickInterpretedStyleCache.get(env);
					theFontColor = get(cache.getAttribute(getDefinition().getFontColor(), Color.class, env));
					theFontSize = get(cache.getAttribute(getDefinition().getFontSize(), Double.class, env));
					theFontWeight = get(cache.getAttribute(getDefinition().getFontWeight(), Double.class, env));
					theFontSlant = get(cache.getAttribute(getDefinition().getFontSlant(), Double.class, env));
					isUnderline = get(cache.getAttribute(getDefinition().isUnderline(), Boolean.class, env));
					isStrikeThrough = get(cache.getAttribute(getDefinition().isStrikeThrough(), Boolean.class, env));
					isSuperScript = get(cache.getAttribute(getDefinition().isSuperScript(), Boolean.class, env));
					isSubScript = get(cache.getAttribute(getDefinition().isSubScript(), Boolean.class, env));
				}
			}
		}

		/** @return The color for the text */
		ObservableValue<Color> getFontColor();

		/** @return The size for the text */
		ObservableValue<Double> getFontSize();

		/** @return The weight or line thickness for the text */
		ObservableValue<Double> getFontWeight();

		/** @return The slant for the text */
		ObservableValue<Double> getFontSlant();

		/** @return Whether the text is underlined */
		ObservableValue<Boolean> isUnderline();

		/** @return Whether the text is struck-through */
		ObservableValue<Boolean> isStrikeThrough();

		/** @return Whether the text is super-script */
		ObservableValue<Boolean> isSuperScript();

		/** @return Whether the text is sub-script */
		ObservableValue<Boolean> isSubScript();

		/** Abstract {@link QuickTextStyle} implementation */
		public abstract class Abstract extends QuickInstanceStyle.Abstract implements QuickTextStyle {
			private QuickStyleAttribute<Color> theFontColorAttr;
			private ObservableValue<Color> theFontColor;
			private QuickStyleAttribute<Double> theFontSizeAttr;
			private ObservableValue<Double> theFontSize;
			private QuickStyleAttribute<Double> theFontWeightAttr;
			private ObservableValue<Double> theFontWeight;
			private QuickStyleAttribute<Double> theFontSlantAttr;
			private ObservableValue<Double> theFontSlant;
			private QuickStyleAttribute<Boolean> theUnderlineAttr;
			private ObservableValue<Boolean> isUnderline;
			private QuickStyleAttribute<Boolean> theStrikeThroughAttr;
			private ObservableValue<Boolean> isStrikeThrough;
			private QuickStyleAttribute<Boolean> theSuperScriptAttr;
			private ObservableValue<Boolean> isSuperScript;
			private QuickStyleAttribute<Boolean> theSubScriptAttr;
			private ObservableValue<Boolean> isSubScript;

			@Override
			public ObservableValue<Color> getFontColor() {
				return theFontColor;
			}

			@Override
			public ObservableValue<Double> getFontSize() {
				return theFontSize;
			}

			@Override
			public ObservableValue<Double> getFontWeight() {
				return theFontWeight;
			}

			@Override
			public ObservableValue<Double> getFontSlant() {
				return theFontSlant;
			}

			@Override
			public ObservableValue<Boolean> isUnderline() {
				return isUnderline;
			}

			@Override
			public ObservableValue<Boolean> isStrikeThrough() {
				return isStrikeThrough;
			}

			@Override
			public ObservableValue<Boolean> isSuperScript() {
				return isSuperScript;
			}

			@Override
			public ObservableValue<Boolean> isSubScript() {
				return isSubScript;
			}

			@Override
			public void update(QuickInstanceStyle.Interpreted interpreted, QuickStyledElement styledElement)
				throws ModelInstantiationException {
				super.update(interpreted, styledElement);

				QuickTextStyle.Interpreted myInterpreted = (QuickTextStyle.Interpreted) interpreted;
				theFontColorAttr = myInterpreted.getFontColor().getAttribute();
				theFontSizeAttr = myInterpreted.getFontSize().getAttribute();
				theFontWeightAttr = myInterpreted.getFontWeight().getAttribute();
				theFontSlantAttr = myInterpreted.getFontSlant().getAttribute();
				theUnderlineAttr = myInterpreted.isUnderline().getAttribute();
				theStrikeThroughAttr = myInterpreted.isStrikeThrough().getAttribute();
				theSuperScriptAttr = myInterpreted.isSuperScript().getAttribute();
				theSubScriptAttr = myInterpreted.isSubScript().getAttribute();

				theFontColor = getApplicableAttribute(theFontColorAttr);
				theFontSize = getApplicableAttribute(theFontSizeAttr);
				theFontWeight = getApplicableAttribute(theFontWeightAttr);
				theFontSlant = getApplicableAttribute(theFontSlantAttr);
				isUnderline = getApplicableAttribute(theUnderlineAttr);
				isStrikeThrough = getApplicableAttribute(theStrikeThroughAttr);
				isSuperScript = getApplicableAttribute(theSuperScriptAttr);
				isSubScript = getApplicableAttribute(theSubScriptAttr);
			}

			@Override
			public QuickTextStyle.Abstract copy(QuickStyledElement styledElement) {
				QuickTextStyle.Abstract copy = (QuickTextStyle.Abstract) super.copy(styledElement);

				copy.theFontColor = copy.getApplicableAttribute(theFontColorAttr);
				copy.theFontSize = copy.getApplicableAttribute(theFontSizeAttr);
				copy.theFontWeight = copy.getApplicableAttribute(theFontWeightAttr);
				copy.theFontSlant = copy.getApplicableAttribute(theFontSlantAttr);
				copy.isUnderline = copy.getApplicableAttribute(theUnderlineAttr);
				copy.isStrikeThrough = copy.getApplicableAttribute(theStrikeThroughAttr);
				copy.isSuperScript = copy.getApplicableAttribute(theSuperScriptAttr);
				copy.isSubScript = copy.getApplicableAttribute(theSubScriptAttr);

				return copy;
			}
		}
	}
}
