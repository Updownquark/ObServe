package org.observe.quick;

import java.awt.Color;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.ExElement;
import org.observe.quick.style.QuickCompiledStyle;
import org.observe.quick.style.QuickInterpretedStyle;
import org.observe.quick.style.QuickInterpretedStyleCache;
import org.observe.quick.style.QuickInterpretedStyleCache.Applications;
import org.observe.quick.style.QuickStyleAttributeDef;
import org.observe.quick.style.QuickStyledElement;
import org.observe.quick.style.QuickTypeStyle;
import org.observe.util.TypeTokens;

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
			Interpreted interpret(ExElement.Interpreted<?> parentEl, QuickInterpretedStyle parent,
				InterpretedExpressoEnv env) throws ExpressoInterpretationException;

			public abstract class Abstract extends QuickCompiledStyle.Wrapper implements Def {
				private final Object theId;

				private final QuickStyleAttributeDef theFontColor;
				private final QuickStyleAttributeDef theFontSize;
				private final QuickStyleAttributeDef theFontWeight;
				private final QuickStyleAttributeDef theFontSlant;
				private final QuickStyleAttributeDef isUnderline;
				private final QuickStyleAttributeDef isStrikeThrough;
				private final QuickStyleAttributeDef isSuperScript;
				private final QuickStyleAttributeDef isSubScript;

				protected Abstract(QuickCompiledStyle parent, QuickCompiledStyle wrapped) {
					super(parent, wrapped);
					theId = new Object();
					QuickTypeStyle typeStyle = QuickStyledElement.getTypeStyle(wrapped.getStyleTypes(), wrapped.getElement(),
						QuickCoreInterpretation.NAME, QuickCoreInterpretation.VERSION, "with-text");
					theFontColor = typeStyle.getAttribute("font-color");
					theFontSize = typeStyle.getAttribute("font-size");
					theFontWeight = typeStyle.getAttribute("font-weight");
					theFontSlant = typeStyle.getAttribute("font-slant");
					isUnderline = typeStyle.getAttribute("underline");
					isStrikeThrough = typeStyle.getAttribute("strike-through");
					isSuperScript = typeStyle.getAttribute("super-script");
					isSubScript = typeStyle.getAttribute("sub-script");
				}

				@Override
				public Object getId() {
					return theId;
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

			public abstract class Abstract extends QuickInterpretedStyle.Wrapper implements Interpreted {
				private final Def theDefinition;
				private QuickElementStyleAttribute<Color> theFontColor;
				private QuickElementStyleAttribute<Double> theFontSize;
				private QuickElementStyleAttribute<Double> theFontWeight;
				private QuickElementStyleAttribute<Double> theFontSlant;
				private QuickElementStyleAttribute<Boolean> isUnderline;
				private QuickElementStyleAttribute<Boolean> isStrikeThrough;
				private QuickElementStyleAttribute<Boolean> isSuperScript;
				private QuickElementStyleAttribute<Boolean> isSubScript;

				protected Abstract(Def definition, QuickInterpretedStyle parent, QuickInterpretedStyle wrapped) {
					super(parent, wrapped);
					theDefinition = definition;
				}

				@Override
				public Def getDefinition() {
					return theDefinition;
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
			private final SettableValue<ObservableValue<Color>> theFontColor;
			private final SettableValue<ObservableValue<Double>> theFontSize;
			private final SettableValue<ObservableValue<Double>> theFontWeight;
			private final SettableValue<ObservableValue<Double>> theFontSlant;
			private final SettableValue<ObservableValue<Boolean>> isUnderline;
			private final SettableValue<ObservableValue<Boolean>> isStrikeThrough;
			private final SettableValue<ObservableValue<Boolean>> isSuperScript;
			private final SettableValue<ObservableValue<Boolean>> isSubScript;

			protected Abstract(Object interpretedId, QuickTextElement styledElement) {
				super(interpretedId, styledElement);
				theFontColor = SettableValue
					.build(TypeTokens.get().keyFor(ObservableValue.class).<ObservableValue<Color>> parameterized(Color.class)).build();
				theFontSize = SettableValue
					.build(TypeTokens.get().keyFor(ObservableValue.class).<ObservableValue<Double>> parameterized(Double.class)).build();
				theFontWeight = SettableValue.build(theFontSize.getType()).build();
				theFontSlant = SettableValue.build(theFontSize.getType()).build();
				isUnderline = SettableValue
					.build(TypeTokens.get().keyFor(ObservableValue.class).<ObservableValue<Boolean>> parameterized(boolean.class)).build();
				isStrikeThrough = SettableValue.build(isUnderline.getType()).build();
				isSuperScript = SettableValue.build(isUnderline.getType()).build();
				isSubScript = SettableValue.build(isUnderline.getType()).build();
			}

			@Override
			public ObservableValue<Color> getFontColor() {
				return ObservableValue.flatten(theFontColor);
			}

			@Override
			public ObservableValue<Double> getFontSize() {
				return ObservableValue.flatten(theFontSize);
			}

			@Override
			public ObservableValue<Double> getFontWeight() {
				return ObservableValue.flatten(theFontWeight);
			}

			@Override
			public ObservableValue<Double> getFontSlant() {
				return ObservableValue.flatten(theFontSlant);
			}

			@Override
			public ObservableValue<Boolean> isUnderline() {
				return ObservableValue.flatten(isUnderline);
			}

			@Override
			public ObservableValue<Boolean> isStrikeThrough() {
				return ObservableValue.flatten(isStrikeThrough);
			}

			@Override
			public ObservableValue<Boolean> isSuperScript() {
				return ObservableValue.flatten(isSuperScript);
			}

			@Override
			public ObservableValue<Boolean> isSubScript() {
				return ObservableValue.flatten(isSubScript);
			}

			@Override
			public void update(QuickInstanceStyle.Interpreted interpreted, ModelSetInstance models) throws ModelInstantiationException {
				super.update(interpreted, models);
				QuickTextStyle.Interpreted myInterpreted = (QuickTextStyle.Interpreted) interpreted;
				theFontColor.set(myInterpreted.getFontColor().evaluate(models), null);
				theFontSize.set(myInterpreted.getFontSize().evaluate(models), null);
				theFontWeight.set(myInterpreted.getFontWeight().evaluate(models), null);
				theFontSlant.set(myInterpreted.getFontSlant().evaluate(models), null);
				isUnderline.set(myInterpreted.isUnderline().evaluate(models), null);
				isStrikeThrough.set(myInterpreted.isStrikeThrough().evaluate(models), null);
				isSuperScript.set(myInterpreted.isSuperScript().evaluate(models), null);
				isSubScript.set(myInterpreted.isSubScript().evaluate(models), null);
			}
		}
	}
}
