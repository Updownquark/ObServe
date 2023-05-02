package org.observe.quick;

import java.awt.Color;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.expresso.CompiledExpression;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.quick.style.QuickInterpretedStyle.QuickElementStyleAttribute;
import org.observe.util.TypeTokens;
import org.qommons.config.AbstractQIS;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;

public interface QuickBorder extends QuickStyled {
	public interface Def<B extends QuickBorder> extends QuickStyled.Def<B> {
		Interpreted<? extends B> interpret(QuickElement.Interpreted<?> parent);
	}

	public interface Interpreted<B extends QuickBorder> extends QuickStyled.Interpreted<B> {
		B create(QuickElement parent);
	}

	ObservableValue<Color> getColor();

	ObservableValue<Integer> getThickness();

	QuickBorder update(ModelSetInstance models) throws ModelInstantiationException;

	public class LineBorder extends QuickElement.Abstract implements QuickBorder {
		public static class Def<B extends LineBorder> extends QuickStyled.Def.Abstract<B> implements QuickBorder.Def<B> {
			public Def(QuickElement.Def<?> parent, QonfigElement element) {
				super(parent, element);
			}

			@Override
			public Interpreted<B> interpret(QuickElement.Interpreted<?> parent) {
				return new Interpreted<>(this, parent);
			}
		}

		public static class Interpreted<B extends LineBorder> extends QuickStyled.Interpreted.Abstract<B>
		implements QuickBorder.Interpreted<B> {
			private QuickElementStyleAttribute<Color> theColor;
			private QuickElementStyleAttribute<Integer> theThickness;

			public Interpreted(Def<? super B> definition, QuickElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public Def<? super B> getDefinition() {
				return (Def<? super B>) super.getDefinition();
			}

			public QuickElementStyleAttribute<Color> getColor() {
				return theColor;
			}

			public QuickElementStyleAttribute<Integer> getThickness() {
				return theThickness;
			}

			@Override
			public Interpreted<B> update(QuickInterpretationCache cache) throws ExpressoInterpretationException {
				super.update(cache);
				theColor = getStyle().get("border-color", Color.class);
				theThickness = getStyle().get("thickness", int.class);
				return this;
			}

			@Override
			public B create(QuickElement parent) {
				return (B) new LineBorder(this, parent);
			}
		}

		private final SettableValue<ObservableValue<Color>> theColor;
		private final SettableValue<ObservableValue<Integer>> theThickness;

		public LineBorder(Interpreted<?> interpreted, QuickElement parent) {
			super(interpreted, parent);
			theColor = SettableValue
				.build(TypeTokens.get().keyFor(ObservableValue.class).<ObservableValue<Color>> parameterized(Color.class)).build();
			theThickness = SettableValue
				.build(TypeTokens.get().keyFor(ObservableValue.class).<ObservableValue<Integer>> parameterized(int.class)).build();
		}

		@Override
		public Interpreted<?> getInterpreted() {
			return (Interpreted<?>) super.getInterpreted();
		}

		@Override
		public ObservableValue<Color> getColor() {
			return ObservableValue.flatten(theColor, () -> Color.black);
		}

		@Override
		public ObservableValue<Integer> getThickness() {
			return ObservableValue.flatten(theThickness, () -> 1);
		}

		@Override
		public LineBorder update(ModelSetInstance models) throws ModelInstantiationException {
			super.update(models);
			theColor.set(getInterpreted().getColor().evaluate(models, Color.black), null);
			theThickness.set(getInterpreted().getThickness().evaluate(models, 1), null);
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
		}

		public static class Interpreted<B extends TitledBorder> extends LineBorder.Interpreted<B> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theTitle;

			public Interpreted(LineBorder.Def<? super B> definition, QuickElement.Interpreted<?> parent) {
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
	}
}
