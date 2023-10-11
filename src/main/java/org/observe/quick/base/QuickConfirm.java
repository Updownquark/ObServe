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

public class QuickConfirm extends QuickContentDialog.Abstract {
	public static final String CONFIRM = "confirm";

	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = CONFIRM,
		interpretation = Interpreted.class,
		instance = QuickConfirm.class)
	public static class Def extends QuickContentDialog.Def.Abstract<QuickConfirm> {
		private CompiledExpression theOnConfirm;
		private CompiledExpression theOnCancel;
		private CompiledExpression theIcon;

		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@QonfigAttributeGetter("on-confirm")
		public CompiledExpression getOnConfirm() {
			return theOnConfirm;
		}

		@QonfigAttributeGetter("on-cancel")
		public CompiledExpression getOnCancel() {
			return theOnCancel;
		}

		@QonfigAttributeGetter("icon")
		public CompiledExpression getIcon() {
			return theIcon;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);

			theOnConfirm = session.getAttributeExpression("on-confirm");
			theOnCancel = session.getAttributeExpression("on-cancel");
			theIcon = session.getAttributeExpression("icon");
		}

		@Override
		public Interpreted interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}
	}

	public static class Interpreted extends QuickContentDialog.Interpreted.Abstract<QuickConfirm> {
		private InterpretedValueSynth<ObservableAction, ObservableAction> theOnConfirm;
		private InterpretedValueSynth<ObservableAction, ObservableAction> theOnCancel;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Icon>> theIcon;

		Interpreted(Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		public InterpretedValueSynth<ObservableAction, ObservableAction> getOnConfirm() {
			return theOnConfirm;
		}

		public InterpretedValueSynth<ObservableAction, ObservableAction> getOnCancel() {
			return theOnCancel;
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<Icon>> getIcon() {
			return theIcon;
		}

		@Override
		protected void doUpdate(InterpretedExpressoEnv expressoEnv) throws ExpressoInterpretationException {
			super.doUpdate(expressoEnv);

			theOnConfirm = getDefinition().getOnConfirm().interpret(ModelTypes.Action.instance(), expressoEnv);
			theOnCancel = getDefinition().getOnCancel() == null ? null
				: getDefinition().getOnCancel().interpret(ModelTypes.Action.instance(), expressoEnv);
			theIcon = QuickCoreInterpretation.evaluateIcon(getDefinition().getIcon(), expressoEnv,
				getDefinition().getElement().getDocument().getLocation());
		}

		@Override
		public QuickConfirm create() {
			return new QuickConfirm(getIdentity());
		}
	}

	private ModelValueInstantiator<ObservableAction> theOnConfirmInstantiator;
	private ModelValueInstantiator<ObservableAction> theOnCancelInstantiator;
	private ModelValueInstantiator<SettableValue<Icon>> theIconInstantiator;

	private SettableValue<ObservableAction> theOnConfirm;
	private SettableValue<ObservableAction> theOnCancel;
	private SettableValue<SettableValue<Icon>> theIcon;

	QuickConfirm(Object id) {
		super(id);
		theOnConfirm = SettableValue.build(ObservableAction.class).build();
		theOnCancel = SettableValue.build(ObservableAction.class).build();
		theIcon = SettableValue.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<Icon>> parameterized(Icon.class)).build();
	}

	public ObservableAction getOnConfirm() {
		return ObservableAction.flatten(theOnConfirm);
	}

	public ObservableAction getOnCancel() {
		return ObservableAction.flatten(theOnCancel);
	}

	public SettableValue<Icon> getIcon() {
		return SettableValue.flatten(theIcon);
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) {
		super.doUpdate(interpreted);
		Interpreted myInterpreted = (Interpreted) interpreted;
		theOnConfirmInstantiator = myInterpreted.getOnConfirm().instantiate();
		theOnCancelInstantiator = myInterpreted.getOnCancel() == null ? null : myInterpreted.getOnCancel().instantiate();
		theIconInstantiator = myInterpreted.getIcon() == null ? null : myInterpreted.getIcon().instantiate();
	}

	@Override
	public void instantiated() {
		super.instantiated();

		theOnConfirmInstantiator.instantiate();
		if (theOnCancelInstantiator != null)
			theOnCancelInstantiator.instantiate();
		if (theIconInstantiator != null)
			theIconInstantiator.instantiate();
	}

	@Override
	protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		super.doInstantiate(myModels);

		theOnConfirm.set(theOnConfirmInstantiator.get(myModels), null);
		theOnCancel.set(theOnCancelInstantiator == null ? ObservableAction.DO_NOTHING : theOnCancelInstantiator.get(myModels), null);
		theIcon.set(theIconInstantiator == null ? null : theIconInstantiator.get(myModels), null);
	}

	@Override
	public QuickConfirm copy(ExElement parent) {
		QuickConfirm copy = (QuickConfirm) super.copy(parent);

		copy.theOnConfirm = SettableValue.build(ObservableAction.class).build();
		copy.theOnCancel = SettableValue.build(ObservableAction.class).build();
		copy.theIcon = SettableValue.build(theIcon.getType()).build();

		return copy;
	}
}
