package org.observe.quick;

import java.awt.Color;

import org.observe.ObservableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.qonfig.ExElement;
import org.observe.quick.style.QuickCompiledStyle;
import org.observe.quick.style.QuickInterpretedStyle;
import org.observe.quick.style.QuickInterpretedStyleCache;
import org.observe.quick.style.QuickInterpretedStyleCache.Applications;
import org.observe.quick.style.QuickStyleAttributeDef;
import org.observe.quick.style.QuickStyledElement;
import org.observe.quick.style.QuickTypeStyle;

public interface QuickTextElement extends QuickStyledElement {
	public interface Def<E extends QuickTextElement> extends QuickStyledElement.Def<E> {
		@Override
		QuickTextStyle.Def getStyle();
	}

	public interface Interpreted<E extends QuickTextElement> extends QuickStyledElement.Interpreted<E> {
		@Override
		QuickTextStyle.Interpreted getStyle();
	}

	public interface QuickTextStyle extends QuickInstanceStyle {
		public interface Def extends QuickInstanceStyle.Def {
			QuickStyleAttributeDef getFontColor();

			QuickStyleAttributeDef getFontSize();

			QuickStyleAttributeDef getFontWeight();

			QuickStyleAttributeDef getFontSlant();

			QuickStyleAttributeDef isUnderline();

			QuickStyleAttributeDef isStrikeThrough();

			QuickStyleAttributeDef isSuperScript();

			QuickStyleAttributeDef isSubScript();

			@Override
			Interpreted interpret(ExElement.Interpreted<?> parentEl, QuickInterpretedStyle parent, InterpretedExpressoEnv env)
				throws ExpressoInterpretationException;

			public abstract class Abstract extends QuickInstanceStyle.Def.Abstract implements Def {
				private final QuickStyleAttributeDef theFontColor;
				private final QuickStyleAttributeDef theFontSize;
				private final QuickStyleAttributeDef theFontWeight;
				private final QuickStyleAttributeDef theFontSlant;
				private final QuickStyleAttributeDef isUnderline;
				private final QuickStyleAttributeDef isStrikeThrough;
				private final QuickStyleAttributeDef isSuperScript;
				private final QuickStyleAttributeDef isSubScript;

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

		public interface Interpreted extends QuickInstanceStyle.Interpreted {
			@Override
			QuickTextStyle create(QuickStyledElement styledElement);

			QuickElementStyleAttribute<Color> getFontColor();

			QuickElementStyleAttribute<Double> getFontSize();

			QuickElementStyleAttribute<Double> getFontWeight();

			QuickElementStyleAttribute<Double> getFontSlant();

			QuickElementStyleAttribute<Boolean> isUnderline();

			QuickElementStyleAttribute<Boolean> isStrikeThrough();

			QuickElementStyleAttribute<Boolean> isSuperScript();

			QuickElementStyleAttribute<Boolean> isSubScript();

			public abstract class Abstract extends QuickInstanceStyle.Interpreted.Abstract implements Interpreted {
				private QuickElementStyleAttribute<Color> theFontColor;
				private QuickElementStyleAttribute<Double> theFontSize;
				private QuickElementStyleAttribute<Double> theFontWeight;
				private QuickElementStyleAttribute<Double> theFontSlant;
				private QuickElementStyleAttribute<Boolean> isUnderline;
				private QuickElementStyleAttribute<Boolean> isStrikeThrough;
				private QuickElementStyleAttribute<Boolean> isSuperScript;
				private QuickElementStyleAttribute<Boolean> isSubScript;

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
				public void update(InterpretedExpressoEnv env, Applications appCache) throws ExpressoInterpretationException {
					super.update(env, appCache);
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

		ObservableValue<Color> getFontColor();

		ObservableValue<Double> getFontSize();

		ObservableValue<Double> getFontWeight();

		ObservableValue<Double> getFontSlant();

		ObservableValue<Boolean> isUnderline();

		ObservableValue<Boolean> isStrikeThrough();

		ObservableValue<Boolean> isSuperScript();

		ObservableValue<Boolean> isSubScript();

		public abstract class Abstract extends QuickInstanceStyle.Abstract implements QuickTextStyle {
			private final ObservableValue<Color> theFontColor;
			private final ObservableValue<Double> theFontSize;
			private final ObservableValue<Double> theFontWeight;
			private final ObservableValue<Double> theFontSlant;
			private final ObservableValue<Boolean> isUnderline;
			private final ObservableValue<Boolean> isStrikeThrough;
			private final ObservableValue<Boolean> isSuperScript;
			private final ObservableValue<Boolean> isSubScript;

			protected Abstract(QuickTextStyle.Interpreted interpreted, QuickTextElement styledElement) {
				super(interpreted, styledElement);
				theFontColor = getApplicableAttribute(interpreted.getFontColor().getAttribute());
				theFontSize = getApplicableAttribute(interpreted.getFontSize().getAttribute());
				theFontWeight = getApplicableAttribute(interpreted.getFontWeight().getAttribute());
				theFontSlant = getApplicableAttribute(interpreted.getFontSlant().getAttribute());
				isUnderline = getApplicableAttribute(interpreted.isUnderline().getAttribute());
				isStrikeThrough = getApplicableAttribute(interpreted.isStrikeThrough().getAttribute());
				isSuperScript = getApplicableAttribute(interpreted.isSuperScript().getAttribute());
				isSubScript = getApplicableAttribute(interpreted.isSubScript().getAttribute());
			}

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
		}
	}
}
