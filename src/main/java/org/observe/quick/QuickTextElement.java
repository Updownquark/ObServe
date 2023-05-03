package org.observe.quick;

import java.awt.Color;
import java.util.Map;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
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

			@Override
			Interpreted interpret(QuickInterpretedStyle parent, Map<CompiledStyleApplication, InterpretedStyleApplication> applications)
				throws ExpressoInterpretationException;

			public abstract class Abstract extends QuickCompiledStyle.Wrapper implements Def {
				private final QuickStyleAttribute<Color> theFontColor;
				private final QuickStyleAttribute<Double> theFontSize;
				private final QuickStyleAttribute<Double> theFontWeight;
				private final QuickStyleAttribute<Double> theFontSlant;

				protected Abstract(QuickCompiledStyle parent, QuickCompiledStyle wrapped) {
					super(parent, wrapped);
					QuickTypeStyle typeStyle = QuickStyledElement.getTypeStyle(wrapped.getElement(), QuickCore.NAME, QuickCore.VERSION,
						"with-text");
					theFontColor = (QuickStyleAttribute<Color>) typeStyle.getAttribute("font-color", Color.class);
					theFontSize = (QuickStyleAttribute<Double>) typeStyle.getAttribute("font-size", double.class);
					theFontWeight = (QuickStyleAttribute<Double>) typeStyle.getAttribute("font-weight", double.class);
					theFontSlant = (QuickStyleAttribute<Double>) typeStyle.getAttribute("font-slant", double.class);
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
				public abstract Interpreted interpret(QuickInterpretedStyle parent,
					Map<CompiledStyleApplication, InterpretedStyleApplication> applications) throws ExpressoInterpretationException;
			}
		}

		public interface Interpreted extends QuickInstanceStyle.Interpreted {
			@Override
			Def getCompiled();

			@Override
			QuickTextStyle create();

			QuickElementStyleAttribute<Color> getFontColor();

			QuickElementStyleAttribute<Double> getFontSize();

			QuickElementStyleAttribute<Double> getFontWeight();

			QuickElementStyleAttribute<Double> getFontSlant();

			public abstract class Abstract extends QuickInterpretedStyle.Wrapper implements Interpreted {
				private final Def theDefinition;
				private final QuickElementStyleAttribute<Color> theFontColor;
				private final QuickElementStyleAttribute<Double> theFontSize;
				private final QuickElementStyleAttribute<Double> theFontWeight;
				private final QuickElementStyleAttribute<Double> theFontSlant;

				protected Abstract(Def definition, QuickInterpretedStyle parent, QuickInterpretedStyle wrapped) {
					super(parent, wrapped);
					theDefinition = definition;
					theFontColor = wrapped.get(getCompiled().getFontColor());
					theFontSize = wrapped.get(getCompiled().getFontSize());
					theFontWeight = wrapped.get(getCompiled().getFontWeight());
					theFontSlant = wrapped.get(getCompiled().getFontSlant());
				}

				@Override
				public Def getCompiled() {
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
			}
		}

		ObservableValue<Color> getFontColor();

		ObservableValue<Double> getFontSize();

		ObservableValue<Double> getFontWeight();

		ObservableValue<Double> getFontSlant();

		public abstract class Abstract implements QuickTextStyle {
			private final Interpreted theInterpreted;
			private final SettableValue<ObservableValue<Color>> theFontColor;
			private final SettableValue<ObservableValue<Double>> theFontSize;
			private final SettableValue<ObservableValue<Double>> theFontWeight;
			private final SettableValue<ObservableValue<Double>> theFontSlant;

			protected Abstract(Interpreted interpreted) {
				theInterpreted = interpreted;
				theFontColor = SettableValue
					.build(TypeTokens.get().keyFor(ObservableValue.class).<ObservableValue<Color>> parameterized(Color.class)).build();
				theFontSize = SettableValue
					.build(TypeTokens.get().keyFor(ObservableValue.class).<ObservableValue<Double>> parameterized(Double.class)).build();
				theFontWeight = SettableValue
					.build(TypeTokens.get().keyFor(ObservableValue.class).<ObservableValue<Double>> parameterized(Double.class)).build();
				theFontSlant = SettableValue
					.build(TypeTokens.get().keyFor(ObservableValue.class).<ObservableValue<Double>> parameterized(Double.class)).build();
			}

			@Override
			public Interpreted getInterpreted() {
				return theInterpreted;
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
				return ObservableValue.flatten(theFontSize);
			}

			@Override
			public ObservableValue<Double> getFontSlant() {
				return ObservableValue.flatten(theFontSize);
			}

			@Override
			public void update(ModelSetInstance models) throws ModelInstantiationException {
				theFontColor.set(getInterpreted().getFontColor().evaluate(models), null);
				theFontSize.set(getInterpreted().getFontSize().evaluate(models), null);
				theFontWeight.set(getInterpreted().getFontWeight().evaluate(models), null);
				theFontSlant.set(getInterpreted().getFontSlant().evaluate(models), null);
			}
		}
	}
}
