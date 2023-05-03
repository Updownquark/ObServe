package org.observe.quick;

import java.awt.Color;
import java.util.Map;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.expresso.CompiledExpression;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.quick.QuickTextElement.QuickTextStyle;
import org.observe.quick.style.CompiledStyleApplication;
import org.observe.quick.style.InterpretedStyleApplication;
import org.observe.quick.style.QuickCompiledStyle;
import org.observe.quick.style.QuickInterpretedStyle;
import org.observe.quick.style.QuickStyleAttribute;
import org.observe.quick.style.QuickTypeStyle;
import org.observe.util.TypeTokens;
import org.qommons.config.AbstractQIS;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;

public interface QuickBorder extends QuickStyledElement {
	public interface Def<B extends QuickBorder> extends QuickStyledElement.Def<B> {
		@Override
		QuickBorderStyle.Def getStyle();

		Interpreted<? extends B> interpret(QuickElement.Interpreted<?> parent);
	}

	public interface Interpreted<B extends QuickBorder> extends QuickStyledElement.Interpreted<B> {
		@Override
		QuickBorderStyle.Interpreted getStyle();

		B create(QuickElement parent);
	}

	@Override
	QuickBorderStyle getStyle();

	QuickBorder update(ModelSetInstance models) throws ModelInstantiationException;

	public class LineBorder extends QuickStyledElement.Abstract implements QuickBorder {
		public static class Def<B extends LineBorder> extends QuickStyledElement.Def.Abstract<B> implements QuickBorder.Def<B> {
			public Def(QuickElement.Def<?> parent, QonfigElement element) {
				super(parent, element);
			}

			@Override
			public QuickBorderStyle.Def getStyle() {
				return (QuickBorderStyle.Def) super.getStyle();
			}

			@Override
			public Interpreted<B> interpret(QuickElement.Interpreted<?> parent) {
				return new Interpreted<>(this, parent);
			}

			@Override
			protected QuickBorderStyle.Def wrap(QuickInstanceStyle.Def parentStyle, QuickCompiledStyle style) {
				return new QuickBorderStyle.Def.Default(parentStyle, style);
			}
		}

		public static class Interpreted<B extends LineBorder> extends QuickStyledElement.Interpreted.Abstract<B>
		implements QuickBorder.Interpreted<B> {
			public Interpreted(Def<? super B> definition, QuickElement.Interpreted<?> parent) {
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
			public Interpreted<B> update(QuickInterpretationCache cache) throws ExpressoInterpretationException {
				super.update(cache);
				return this;
			}

			@Override
			public B create(QuickElement parent) {
				return (B) new LineBorder(this, parent);
			}
		}

		public LineBorder(Interpreted<?> interpreted, QuickElement parent) {
			super(interpreted, parent);
		}

		@Override
		public Interpreted<?> getInterpreted() {
			return (Interpreted<?>) super.getInterpreted();
		}

		@Override
		public QuickBorderStyle getStyle() {
			return (QuickBorderStyle) super.getStyle();
		}

		@Override
		public LineBorder update(ModelSetInstance models) throws ModelInstantiationException {
			super.update(models);
			return this;
		}
	}

	public class TitledBorder extends LineBorder {
		public static class Def<B extends TitledBorder> extends LineBorder.Def<B> {
			private CompiledExpression theTitle;

			public Def(QuickElement.Def<?> parent, QonfigElement element) {
				super(parent, element);
			}

			public CompiledExpression getTitle() {
				return theTitle;
			}

			@Override
			public Def<B> update(AbstractQIS<?> session) throws QonfigInterpretationException {
				super.update(session);
				theTitle = getExpressoSession().getAttributeExpression("title");
				return this;
			}

			@Override
			public Interpreted<B> interpret(QuickElement.Interpreted<?> parent) {
				return new Interpreted<>(this, parent);
			}

			@Override
			protected QuickTitledBorderStyle.Def wrap(QuickStyledElement.QuickInstanceStyle.Def parentStyle, QuickCompiledStyle style) {
				return new QuickTitledBorderStyle.Def(parentStyle, style);
			}
		}

		public static class Interpreted<B extends TitledBorder> extends LineBorder.Interpreted<B> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theTitle;

			public Interpreted(TitledBorder.Def<? super B> definition, QuickElement.Interpreted<?> parent) {
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
			public Interpreted<B> update(QuickInterpretationCache cache) throws ExpressoInterpretationException {
				super.update(cache);
				theTitle = getDefinition().getTitle().evaluate(ModelTypes.Value.STRING).interpret();
				return this;
			}

			@Override
			public B create(QuickElement parent) {
				return (B) new TitledBorder(this, parent);
			}
		}

		@Override
		public QuickTitledBorderStyle getStyle() {
			return (QuickTitledBorderStyle) super.getStyle();
		}

		private final SettableValue<SettableValue<String>> theTitle;

		public TitledBorder(TitledBorder.Interpreted<?> interpreted, QuickElement parent) {
			super(interpreted, parent);
			theTitle = SettableValue.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<String>> parameterized(String.class))
				.build();
		}

		@Override
		public Interpreted<?> getInterpreted() {
			return (Interpreted<?>) super.getInterpreted();
		}

		public SettableValue<String> getTitle() {
			return SettableValue.flatten(theTitle);
		}

		@Override
		public TitledBorder update(ModelSetInstance models) throws ModelInstantiationException {
			super.update(models);
			theTitle.set(getInterpreted().getTitle().get(models), null);
			return this;
		}

		public static class QuickTitledBorderStyle extends QuickTextStyle.Abstract implements QuickBorderStyle {
			public static class Def extends QuickTextStyle.Def.Abstract implements QuickBorderStyle.Def {
				private final QuickStyleAttribute<Color> theBorderColor;
				private final QuickStyleAttribute<Integer> theBorderThickness;

				public Def(QuickCompiledStyle parent, QuickCompiledStyle wrapped) {
					super(parent, wrapped);
					QuickTypeStyle typeStyle = QuickStyledElement.getTypeStyle(getElement(), QuickCore.NAME, QuickCore.VERSION, "border");
					theBorderColor = (QuickStyleAttribute<Color>) typeStyle.getAttribute("border-color", Color.class);
					theBorderThickness = (QuickStyleAttribute<Integer>) typeStyle.getAttribute("thickness", Integer.class);
				}

				@Override
				public QuickStyleAttribute<Color> getBorderColor() {
					return theBorderColor;
				}

				@Override
				public QuickStyleAttribute<Integer> getBorderThickness() {
					return theBorderThickness;
				}

				@Override
				public Interpreted interpret(QuickInterpretedStyle parent,
					Map<CompiledStyleApplication, InterpretedStyleApplication> applications) throws ExpressoInterpretationException {
					return new Interpreted(this, parent, getWrapped().interpret(parent, applications));
				}
			}

			public static class Interpreted extends QuickTextStyle.Interpreted.Abstract implements QuickBorderStyle.Interpreted {
				private final QuickElementStyleAttribute<Color> theBorderColor;
				private final QuickElementStyleAttribute<Integer> theBorderThickness;

				public Interpreted(Def definition, QuickInterpretedStyle parent, QuickInterpretedStyle wrapped) {
					super(definition, parent, wrapped);
					theBorderColor = get(getCompiled().getBorderColor());
					theBorderThickness = get(getCompiled().getBorderThickness());
				}

				@Override
				public QuickTitledBorderStyle.Def getCompiled() {
					return (QuickTitledBorderStyle.Def) super.getCompiled();
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
				public QuickTitledBorderStyle create() {
					return new QuickTitledBorderStyle(this);
				}
			}

			private final SettableValue<ObservableValue<Color>> theBorderColor;
			private final SettableValue<ObservableValue<Integer>> theBorderThickness;

			public QuickTitledBorderStyle(Interpreted interpreted) {
				super(interpreted);
				theBorderColor = SettableValue
					.build(TypeTokens.get().keyFor(ObservableValue.class).<ObservableValue<Color>> parameterized(Color.class)).build();
				theBorderThickness = SettableValue
					.build(TypeTokens.get().keyFor(ObservableValue.class).<ObservableValue<Integer>> parameterized(Integer.class)).build();
			}

			@Override
			public QuickTitledBorderStyle.Interpreted getInterpreted() {
				return (QuickTitledBorderStyle.Interpreted) super.getInterpreted();
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
			public void update(ModelSetInstance models) throws ModelInstantiationException {
				theBorderColor.set(getInterpreted().getBorderColor().evaluate(models), null);
				theBorderThickness.set(getInterpreted().getBorderThickness().evaluate(models), null);
			}
		}
	}

	public interface QuickBorderStyle extends QuickInstanceStyle {
		public interface Def extends QuickInstanceStyle.Def {
			QuickStyleAttribute<Color> getBorderColor();

			QuickStyleAttribute<Integer> getBorderThickness();

			@Override
			Interpreted interpret(QuickInterpretedStyle parent, Map<CompiledStyleApplication, InterpretedStyleApplication> applications)
				throws ExpressoInterpretationException;

			public class Default extends QuickCompiledStyle.Wrapper implements Def {
				private final QuickStyleAttribute<Color> theBorderColor;
				private final QuickStyleAttribute<Integer> theBorderThickness;

				public Default(QuickCompiledStyle parent, QuickCompiledStyle wrapped) {
					super(parent, wrapped);
					QuickTypeStyle typeStyle = QuickStyledElement.getTypeStyle(getElement(), QuickCore.NAME, QuickCore.VERSION, "border");
					theBorderColor = (QuickStyleAttribute<Color>) typeStyle.getAttribute("border-color", Color.class);
					theBorderThickness = (QuickStyleAttribute<Integer>) typeStyle.getAttribute("thickness", Integer.class);
				}

				@Override
				public QuickStyleAttribute<Color> getBorderColor() {
					return theBorderColor;
				}

				@Override
				public QuickStyleAttribute<Integer> getBorderThickness() {
					return theBorderThickness;
				}

				@Override
				public Interpreted interpret(QuickInterpretedStyle parent,
					Map<CompiledStyleApplication, InterpretedStyleApplication> applications) throws ExpressoInterpretationException {
					return new Interpreted.Default(this, parent, getWrapped().interpret(parent, applications));
				}
			}
		}

		public interface Interpreted extends QuickInstanceStyle.Interpreted {
			@Override
			Def getCompiled();

			QuickElementStyleAttribute<Color> getBorderColor();

			QuickElementStyleAttribute<Integer> getBorderThickness();

			@Override
			QuickBorderStyle create();

			public class Default extends QuickInterpretedStyle.Wrapper implements Interpreted {
				private final Def theDefinition;
				private final QuickElementStyleAttribute<Color> theBorderColor;
				private final QuickElementStyleAttribute<Integer> theBorderThickness;

				public Default(Def definition, QuickInterpretedStyle parent, QuickInterpretedStyle wrapped) {
					super(parent, wrapped);
					theDefinition = definition;
					theBorderColor = get(getCompiled().getBorderColor());
					theBorderThickness = get(getCompiled().getBorderThickness());
				}

				@Override
				public Def getCompiled() {
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
				public QuickBorderStyle create() {
					return new QuickBorderStyle.Default(this);
				}
			}
		}

		@Override
		Interpreted getInterpreted();

		ObservableValue<Color> getBorderColor();

		ObservableValue<Integer> getBorderThickness();

		public class Default implements QuickBorderStyle {
			private final Interpreted theInterpreted;
			private final SettableValue<ObservableValue<Color>> theBorderColor;
			private final SettableValue<ObservableValue<Integer>> theBorderThickness;

			public Default(Interpreted interpreted) {
				theInterpreted = interpreted;
				theBorderColor = SettableValue
					.build(TypeTokens.get().keyFor(ObservableValue.class).<ObservableValue<Color>> parameterized(Color.class)).build();
				theBorderThickness = SettableValue
					.build(TypeTokens.get().keyFor(ObservableValue.class).<ObservableValue<Integer>> parameterized(Integer.class)).build();
			}

			@Override
			public Interpreted getInterpreted() {
				return theInterpreted;
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
			public void update(ModelSetInstance models) throws ModelInstantiationException {
				theBorderColor.set(getInterpreted().getBorderColor().evaluate(models), null);
				theBorderThickness.set(getInterpreted().getBorderThickness().evaluate(models), null);
			}
		}
	}
}
