package org.observe.quick.base;

import javax.swing.Icon;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.quick.QuickCoreInterpretation;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

public class QuickToggleButton extends QuickCheckBox {
	public static final String TOGGLE_BUTTON = "toggle-button";

	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = TOGGLE_BUTTON,
		interpretation = Interpreted.class,
		instance = QuickToggleButton.class)
	public static class Def extends QuickCheckBox.Def<QuickToggleButton> {
		private CompiledExpression theIcon;

		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		public CompiledExpression getIcon() {
			return theIcon;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);

			theIcon = session.getAttributeExpression("icon");
		}

		@Override
		public Interpreted interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}
	}

	public static class Interpreted extends QuickCheckBox.Interpreted<QuickToggleButton> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Icon>> theIcon;

		public Interpreted(Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<Icon>> getIcon() {
			return theIcon;
		}

		@Override
		public TypeToken<QuickToggleButton> getWidgetType() {
			return TypeTokens.get().of(QuickToggleButton.class);
		}

		@Override
		protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			super.doUpdate(env);

			theIcon = getDefinition().getIcon() == null ? null : QuickCoreInterpretation.evaluateIcon(getDefinition().getIcon(), env,
				getDefinition().getElement().getDocument().getLocation());
		}

		@Override
		public QuickToggleButton create() {
			return new QuickToggleButton(getIdentity());
		}
	}

	private ModelValueInstantiator<SettableValue<Icon>> theIconInstantiator;
	private SettableValue<SettableValue<Icon>> theIcon;

	public QuickToggleButton(Object id) {
		super(id);
		theIcon = SettableValue.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<Icon>> parameterized(Icon.class)).build();
	}

	public SettableValue<Icon> getIcon() {
		return SettableValue.flatten(theIcon);
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) {
		super.doUpdate(interpreted);

		Interpreted myInterpreted = (Interpreted) interpreted;
		theIconInstantiator = myInterpreted.getIcon() == null ? null : myInterpreted.getIcon().instantiate();
	}

	@Override
	public void instantiated() {
		super.instantiated();

		if (theIconInstantiator != null)
			theIconInstantiator.instantiate();
	}

	@Override
	protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		super.doInstantiate(myModels);

		theIcon.set(theIconInstantiator == null ? null : theIconInstantiator.get(myModels), null);
	}

	@Override
	public QuickToggleButton copy(ExElement parent) {
		QuickToggleButton copy = (QuickToggleButton) super.copy(parent);

		copy.theIcon = SettableValue.build(theIcon.getType()).build();

		return copy;
	}
}
