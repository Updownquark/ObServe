package org.observe.quick.base;

import java.awt.Color;

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
import org.observe.quick.QuickValueWidget;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

/** Allows the user to choose a color. Exactly how this is done or what this widget looks like is somewhat implementation dependent */
public class QuickColorChooser extends QuickValueWidget.Abstract<Color> {
	/** The XML name of this element */
	public static final String COLOR_CHOOSER = "color-chooser";

	/**
	 * {@link QuickColorChooser} definition
	 *
	 * @param <W> The sub-type of color chooser to create
	 */
	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = COLOR_CHOOSER,
		interpretation = Interpreted.class,
		instance = QuickColorChooser.class)
	public static class Def<W extends QuickColorChooser> extends QuickValueWidget.Def.Abstract<W> {
		private CompiledExpression isWithAlpha;

		/**
		 * @param parent The parent element of the widget
		 * @param type The Qonfig type of the widget
		 */
		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		/** @return Whether the user should be able to select the alpha (opacity) channel of the color with this widget */
		@QonfigAttributeGetter("with-alpha")
		public CompiledExpression isWithAlpha() {
			return isWithAlpha;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);

			isWithAlpha = getAttributeExpression("with-alpha", session);
		}

		@Override
		public Interpreted<? extends W> interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}
	}

	/**
	 * {@link QuickColorChooser} interpretation
	 *
	 * @param <W> The sub-type of color chooser to create
	 */
	public static class Interpreted<W extends QuickColorChooser> extends QuickValueWidget.Interpreted.Abstract<Color, W> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> isWithAlpha;

		/**
		 * @param definition The definition to interpret
		 * @param parent The parent element for the widget
		 */
		protected Interpreted(Def<? super W> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def<? super W> getDefinition() {
			return (Def<? super W>) super.getDefinition();
		}

		/** @return Whether the user should be able to select the alpha (opacity) channel of the color with this widget */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> isWithAlpha() {
			return isWithAlpha;
		}

		@Override
		protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			super.doUpdate(env);

			isWithAlpha = interpret(getDefinition().isWithAlpha(), ModelTypes.Value.BOOLEAN);
		}

		@Override
		public W create() {
			return (W) new QuickColorChooser(getIdentity());
		}
	}

	private ModelValueInstantiator<SettableValue<Boolean>> isWithAlphaInstantiator;
	private SettableValue<SettableValue<Boolean>> isWithAlpha;

	/** @param id The element ID for this widget */
	protected QuickColorChooser(Object id) {
		super(id);
		isWithAlpha = SettableValue
			.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<Boolean>> parameterized(boolean.class)).build();
	}

	/** @return Whether the user should be able to select the alpha (opacity) channel of the color with this widget */
	public SettableValue<Boolean> isWithAlpha() {
		return SettableValue.flatten(isWithAlpha);
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);

		Interpreted<?> myInterpreted = (Interpreted<?>) interpreted;
		isWithAlphaInstantiator = myInterpreted.isWithAlpha().instantiate();
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
		super.instantiated();
		isWithAlphaInstantiator.instantiate();
	}

	@Override
	protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		super.doInstantiate(myModels);

		isWithAlpha.set(isWithAlphaInstantiator.get(myModels), null);
	}

	@Override
	public QuickColorChooser copy(ExElement parent) {
		QuickColorChooser copy = (QuickColorChooser) super.copy(parent);

		copy.isWithAlpha = SettableValue.build(isWithAlpha.getType()).build();

		return copy;
	}
}
