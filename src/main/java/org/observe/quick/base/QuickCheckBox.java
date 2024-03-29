package org.observe.quick.base;

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

public class QuickCheckBox extends QuickValueWidget.Abstract<Boolean> {
	public static final String CHECK_BOX = "check-box";

	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = CHECK_BOX,
		interpretation = Interpreted.class,
		instance = QuickCheckBox.class)
	public static class Def<W extends QuickCheckBox> extends QuickValueWidget.Def.Abstract<W> {
		private CompiledExpression theText;

		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		@QonfigAttributeGetter
		public CompiledExpression getText() {
			return theText;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));
			theText = getValueExpression(session);
		}

		@Override
		public Interpreted<? extends W> interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}
	}

	public static class Interpreted<W extends QuickCheckBox> extends QuickValueWidget.Interpreted.Abstract<Boolean, W> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theText;

		public Interpreted(Def<? super W> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def<? super W> getDefinition() {
			return (Def<? super W>) super.getDefinition();
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getText() {
			return theText;
		}

		@Override
		protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			super.doUpdate(env);
			theText = interpret(getDefinition().getText(), ModelTypes.Value.STRING);
		}

		@Override
		public W create() {
			return (W) new QuickCheckBox(getIdentity());
		}
	}

	private ModelValueInstantiator<SettableValue<String>> theTextInstantiator;
	private SettableValue<SettableValue<String>> theText;

	public QuickCheckBox(Object id) {
		super(id);
		theText = SettableValue.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<String>> parameterized(String.class))
			.build();
	}

	public SettableValue<String> getText() {
		return SettableValue.flatten(theText);
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) {
		super.doUpdate(interpreted);
		Interpreted<?> myInterpreted = (Interpreted<?>) interpreted;
		theTextInstantiator = myInterpreted.getText() == null ? null : myInterpreted.getText().instantiate();
	}

	@Override
	public void instantiated() {
		super.instantiated();
		if (theTextInstantiator != null)
			theTextInstantiator.instantiate();
	}

	@Override
	protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		super.doInstantiate(myModels);
		theText.set(theTextInstantiator == null ? null : theTextInstantiator.get(myModels), null);
	}

	@Override
	public QuickCheckBox copy(ExElement parent) {
		QuickCheckBox copy = (QuickCheckBox) super.copy(parent);

		copy.theText = SettableValue.build(theText.getType()).build();

		return copy;
	}
}
