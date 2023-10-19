package org.observe.quick.base;

import javax.swing.Icon;

import org.observe.ObservableAction;
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
import org.observe.quick.QuickContentDialog;
import org.observe.quick.QuickCoreInterpretation;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

public class QuickInfoDialog extends QuickContentDialog.Abstract {
	public static final String INFO_DIALOG = "info-dialog";

	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = INFO_DIALOG,
		interpretation = Interpreted.class,
		instance = QuickInfoDialog.class)
	public static class Def extends QuickContentDialog.Def.Abstract<QuickInfoDialog> {
		private CompiledExpression theType;
		private CompiledExpression theOnClose;
		private CompiledExpression theIcon;

		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@QonfigAttributeGetter("type")
		public CompiledExpression getType() {
			return theType;
		}

		@QonfigAttributeGetter("on-close")
		public CompiledExpression getOnClose() {
			return theOnClose;
		}

		@QonfigAttributeGetter("icon")
		public CompiledExpression getIcon() {
			return theIcon;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);

			theType = getAttributeExpression("type", session);
			theOnClose = getAttributeExpression("on-close", session);
			theIcon = getAttributeExpression("icon", session);
		}

		@Override
		public Interpreted interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}
	}

	public static class Interpreted extends QuickContentDialog.Interpreted.Abstract<QuickInfoDialog> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theType;
		private InterpretedValueSynth<ObservableAction, ObservableAction> theOnClose;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Icon>> theIcon;

		Interpreted(Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getType() {
			return theType;
		}

		public InterpretedValueSynth<ObservableAction, ObservableAction> getOnClose() {
			return theOnClose;
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<Icon>> getIcon() {
			return theIcon;
		}

		@Override
		protected void doUpdate(InterpretedExpressoEnv expressoEnv) throws ExpressoInterpretationException {
			super.doUpdate(expressoEnv);

			theType = getDefinition().getType().interpret(ModelTypes.Value.forType(String.class), expressoEnv);
			theOnClose = getDefinition().getOnClose() == null ? null
				: getDefinition().getOnClose().interpret(ModelTypes.Action.instance(), expressoEnv);
			theIcon = QuickCoreInterpretation.evaluateIcon(getDefinition().getIcon(), expressoEnv,
				getDefinition().getElement().getDocument().getLocation());
		}

		@Override
		public QuickInfoDialog create() {
			return new QuickInfoDialog(getIdentity());
		}
	}

	private ModelValueInstantiator<SettableValue<String>> theTypeInstantiator;
	private ModelValueInstantiator<ObservableAction> theOnCloseInstantiator;
	private ModelValueInstantiator<SettableValue<Icon>> theIconInstantiator;

	private SettableValue<SettableValue<String>> theType;
	private SettableValue<ObservableAction> theOnClose;
	private SettableValue<SettableValue<Icon>> theIcon;

	QuickInfoDialog(Object id) {
		super(id);
		theType = SettableValue.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<String>> parameterized(String.class))
			.build();
		theOnClose = SettableValue.build(ObservableAction.class).build();
		theIcon = SettableValue.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<Icon>> parameterized(Icon.class)).build();
	}

	public SettableValue<String> getType() {
		return SettableValue.flatten(theType);
	}

	public ObservableAction getOnClose() {
		return ObservableAction.flatten(theOnClose);
	}

	public SettableValue<Icon> getIcon() {
		return SettableValue.flatten(theIcon);
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) {
		super.doUpdate(interpreted);
		Interpreted myInterpreted = (Interpreted) interpreted;
		theTypeInstantiator = myInterpreted.getType().instantiate();
		theOnCloseInstantiator = myInterpreted.getOnClose() == null ? null : myInterpreted.getOnClose().instantiate();
		theIconInstantiator = myInterpreted.getIcon() == null ? null : myInterpreted.getIcon().instantiate();
	}

	@Override
	public void instantiated() {
		super.instantiated();

		theTypeInstantiator.instantiate();
		if (theOnCloseInstantiator != null)
			theOnCloseInstantiator.instantiate();
		if (theIconInstantiator != null)
			theIconInstantiator.instantiate();
	}

	@Override
	protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		super.doInstantiate(myModels);

		theType.set(theTypeInstantiator.get(myModels), null);
		theOnClose.set(theOnCloseInstantiator == null ? ObservableAction.DO_NOTHING : theOnCloseInstantiator.get(myModels), null);
		theIcon.set(theIconInstantiator == null ? null : theIconInstantiator.get(myModels), null);
	}

	@Override
	public QuickInfoDialog copy(ExElement parent) {
		QuickInfoDialog copy = (QuickInfoDialog) super.copy(parent);

		copy.theType = SettableValue.build(theType.getType()).build();
		copy.theOnClose = SettableValue.build(ObservableAction.class).build();
		copy.theIcon = SettableValue.build(theIcon.getType()).build();

		return copy;
	}
}
