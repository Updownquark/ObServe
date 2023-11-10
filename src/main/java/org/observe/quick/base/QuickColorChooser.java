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

public class QuickColorChooser extends QuickValueWidget.Abstract<Color> {
	public static final String COLOR_CHOOSER = "color-chooser";

	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = COLOR_CHOOSER,
		interpretation = Interpreted.class,
		instance = QuickColorChooser.class)
	public static class Def<W extends QuickColorChooser> extends QuickValueWidget.Def.Abstract<W> {
		private CompiledExpression isWithAlpha;

		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

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

	public static class Interpreted<W extends QuickColorChooser> extends QuickValueWidget.Interpreted.Abstract<Color, W> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> isWithAlpha;

		public Interpreted(Def<? super W> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def<? super W> getDefinition() {
			return (Def<? super W>) super.getDefinition();
		}

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

	public QuickColorChooser(Object id) {
		super(id);
		isWithAlpha = SettableValue
			.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<Boolean>> parameterized(boolean.class)).build();
	}

	public SettableValue<Boolean> isWithAlpha() {
		return SettableValue.flatten(isWithAlpha);
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) {
		super.doUpdate(interpreted);

		Interpreted<?> myInterpreted = (Interpreted<?>) interpreted;
		isWithAlphaInstantiator = myInterpreted.isWithAlpha().instantiate();
	}

	@Override
	public void instantiated() {
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
