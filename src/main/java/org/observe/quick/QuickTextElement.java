package org.observe.quick;

import java.awt.Color;
import java.util.Map;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.ExElement;
import org.observe.quick.style.CompiledStyleApplication;
import org.observe.quick.style.InterpretedStyleApplication;
import org.observe.quick.style.QuickCompiledStyle;
import org.observe.quick.style.QuickInterpretedStyle;
import org.observe.quick.style.QuickStyleAttribute;
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
			QuickStyleAttribute<Color> getFontColor();

			QuickStyleAttribute<Double> getFontSize();

			QuickStyleAttribute<Double> getFontWeight();

			QuickStyleAttribute<Double> getFontSlant();

			QuickStyleAttribute<Boolean> isUnderline();

			QuickStyleAttribute<Boolean> isStrikeThrough();

			QuickStyleAttribute<Boolean> isSuperScript();

			QuickStyleAttribute<Boolean> isSubScript();

			@Override
			Interpreted interpret(ExElement.Interpreted<?> parentEl, QuickInterpretedStyle parent,
				Map<CompiledStyleApplication, InterpretedStyleApplication> applications) throws ExpressoInterpretationException;

			public abstract class Abstract extends QuickCompiledStyle.Wrapper implements Def {
				private final Object theId;

				private final QuickStyleAttribute<Color> theFontColor;
				private final QuickStyleAttribute<Double> theFontSize;
				private final QuickStyleAttribute<Double> theFontWeight;
				private final QuickStyleAttribute<Double> theFontSlant;
				private final QuickStyleAttribute<Boolean> isUnderline;
				private final QuickStyleAttribute<Boolean> isStrikeThrough;
				private final QuickStyleAttribute<Boolean> isSuperScript;
				private final QuickStyleAttribute<Boolean> isSubScript;

				protected Abstract(QuickCompiledStyle parent, QuickCompiledStyle wrapped) {
					super(parent, wrapped);
					theId = new Object();
					QuickTypeStyle typeStyle = QuickStyledElement.getTypeStyle(wrapped.getStyleTypes(), wrapped.getElement(),
						QuickCoreInterpretation.NAME, QuickCoreInterpretation.VERSION, "with-text");
					theFontColor = (QuickStyleAttribute<Color>) typeStyle.getAttribute("font-color", Color.class);
					theFontSize = (QuickStyleAttribute<Double>) typeStyle.getAttribute("font-size", double.class);
					theFontWeight = (QuickStyleAttribute<Double>) typeStyle.getAttribute("font-weight", double.class);
					theFontSlant = (QuickStyleAttribute<Double>) typeStyle.getAttribute("font-slant", double.class);
					isUnderline = (QuickStyleAttribute<Boolean>) typeStyle.getAttribute("underline", boolean.class);
					isStrikeThrough = (QuickStyleAttribute<Boolean>) typeStyle.getAttribute("strike-through", boolean.class);
					isSuperScript = (QuickStyleAttribute<Boolean>) typeStyle.getAttribute("super-script", boolean.class);
					isSubScript = (QuickStyleAttribute<Boolean>) typeStyle.getAttribute("sub-script", boolean.class);
				}

				@Override
				public Object getId() {
					return theId;
				}

				@Override
				public QuickStyleAttribute<Color> getFontColor() {
					return theFontColor;
				}

				@Override
				public QuickStyleAttribute<Double> getFontSize() {
					return theFontSize;
				}

				@Override
				public QuickStyleAttribute<Double> getFontWeight() {
					return theFontWeight;
				}

				@Override
				public QuickStyleAttribute<Double> getFontSlant() {
					return theFontSlant;
				}

				@Override
				public QuickStyleAttribute<Boolean> isUnderline() {
					return isUnderline;
				}

				@Override
				public QuickStyleAttribute<Boolean> isStrikeThrough() {
					return isStrikeThrough;
				}

				@Override
				public QuickStyleAttribute<Boolean> isSuperScript() {
					return isSuperScript;
				}

				@Override
				public QuickStyleAttribute<Boolean> isSubScript() {
					return isSubScript;
				}

				@Override
				public abstract Interpreted interpret(ExElement.Interpreted<?> parentEl, QuickInterpretedStyle parent,
					Map<CompiledStyleApplication, InterpretedStyleApplication> applications) throws ExpressoInterpretationException;
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
				private final Object theId;
				private final QuickElementStyleAttribute<Color> theFontColor;
				private final QuickElementStyleAttribute<Double> theFontSize;
				private final QuickElementStyleAttribute<Double> theFontWeight;
				private final QuickElementStyleAttribute<Double> theFontSlant;
				private final QuickElementStyleAttribute<Boolean> isUnderline;
				private final QuickElementStyleAttribute<Boolean> isStrikeThrough;
				private final QuickElementStyleAttribute<Boolean> isSuperScript;
				private final QuickElementStyleAttribute<Boolean> isSubScript;

				protected Abstract(Def definition, QuickInterpretedStyle parent, QuickInterpretedStyle wrapped) {
					super(parent, wrapped);
					theId = definition.getId();
					theFontColor = wrapped.get(definition.getFontColor());
					theFontSize = wrapped.get(definition.getFontSize());
					theFontWeight = wrapped.get(definition.getFontWeight());
					theFontSlant = wrapped.get(definition.getFontSlant());
					isUnderline = wrapped.get(definition.isUnderline());
					isStrikeThrough = wrapped.get(definition.isStrikeThrough());
					isSuperScript = wrapped.get(definition.isSuperScript());
					isSubScript = wrapped.get(definition.isSubScript());
				}

				@Override
				public Object getId() {
					return theId;
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
