package org.observe.quick;

import java.awt.Color;
import java.util.Map;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.expresso.CompiledExpression;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ExpressoQIS;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.ExElement;
import org.observe.quick.QuickTextElement.QuickTextStyle;
import org.observe.quick.style.CompiledStyleApplication;
import org.observe.quick.style.InterpretedStyleApplication;
import org.observe.quick.style.QuickCompiledStyle;
import org.observe.quick.style.QuickInterpretedStyle;
import org.observe.quick.style.QuickStyleAttribute;
import org.observe.quick.style.QuickTypeStyle;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElement;
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

		B create(ExElement parent);
	}

	@Override
	QuickBorderStyle getStyle();

	public class LineBorder extends QuickStyledElement.Abstract implements QuickBorder {
		public static final String LINE_BORDER = "line-border";

		public static class Def<B extends LineBorder> extends QuickStyledElement.Def.Abstract<B> implements QuickBorder.Def<B> {
			public Def(ExElement.Def<?> parent, QonfigElement element) {
				super(parent, element);
			}

			@Override
			public QuickBorderStyle.Def getStyle() {
				return (QuickBorderStyle.Def) super.getStyle();
			}

			@Override
			public void update(ExpressoQIS session) throws QonfigInterpretationException {
				checkElement(session.getFocusType(), QuickCoreInterpretation.NAME, QuickCoreInterpretation.VERSION, LINE_BORDER);
				super.update(session.asElement(session.getFocusType().getSuperElement()));
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

	public class TitledBorder extends LineBorder {
		public static final String TITLED_BORDER = "titled-border";

		public static class Def<B extends TitledBorder> extends LineBorder.Def<B> {
			private CompiledExpression theTitle;

			public Def(ExElement.Def<?> parent, QonfigElement element) {
				super(parent, element);
			}

			public CompiledExpression getTitle() {
				return theTitle;
			}

			@Override
			public void update(ExpressoQIS session) throws QonfigInterpretationException {
				checkElement(session.getFocusType(), QuickCoreInterpretation.NAME, QuickCoreInterpretation.VERSION, TITLED_BORDER);
				super.update(session.asElement(session.getFocusType().getSuperElement()));
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
			public void update(QuickInterpretationCache cache) throws ExpressoInterpretationException {
				super.update(cache);
				theTitle = getDefinition().getTitle().evaluate(ModelTypes.Value.STRING).interpret();
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
				private final QuickStyleAttribute<Color> theBorderColor;
				private final QuickStyleAttribute<Integer> theBorderThickness;

				public Def(QuickCompiledStyle parent, QuickCompiledStyle wrapped) {
					super(parent, wrapped);
					QuickTypeStyle typeStyle = QuickStyledElement.getTypeStyle(wrapped.getStyleTypes(), getElement(),
						QuickCoreInterpretation.NAME, QuickCoreInterpretation.VERSION, "titled-border");
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
					QuickTypeStyle typeStyle = QuickStyledElement.getTypeStyle(wrapped.getStyleTypes(), getElement(),
						QuickCoreInterpretation.NAME, QuickCoreInterpretation.VERSION, "border");
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
				private final Object theId;
				private final QuickElementStyleAttribute<Color> theBorderColor;
				private final QuickElementStyleAttribute<Integer> theBorderThickness;

				public Default(Def definition, QuickInterpretedStyle parent, QuickInterpretedStyle wrapped) {
					super(parent, wrapped);
					theId = new Object();
					theDefinition = definition;
					theBorderColor = get(getCompiled().getBorderColor());
					theBorderThickness = get(getCompiled().getBorderThickness());
				}

				@Override
				public Def getCompiled() {
					return theDefinition;
				}

				@Override
				public Object getId() {
					return theId;
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

		ObservableValue<Color> getBorderColor();

		ObservableValue<Integer> getBorderThickness();

		public class Default implements QuickBorderStyle {
			private final Object theId;
			private final SettableValue<ObservableValue<Color>> theBorderColor;
			private final SettableValue<ObservableValue<Integer>> theBorderThickness;

			public Default(Interpreted interpreted) {
				theId = interpreted.getId();
				theBorderColor = SettableValue
					.build(TypeTokens.get().keyFor(ObservableValue.class).<ObservableValue<Color>> parameterized(Color.class)).build();
				theBorderThickness = SettableValue
					.build(TypeTokens.get().keyFor(ObservableValue.class).<ObservableValue<Integer>> parameterized(Integer.class)).build();
			}

			@Override
			public Object getId() {
				return theId;
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
