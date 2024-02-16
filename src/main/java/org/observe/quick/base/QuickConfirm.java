package org.observe.quick.base;

import java.awt.Image;

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

/** A dialog that asks the user to choose "OK" or "Cancel" with a message */
public class QuickConfirm extends QuickContentDialog.Abstract {
	/** The XML name of this element */
	public static final String CONFIRM = "confirm";

	/** {@link QuickConfirm} definition */
	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = CONFIRM,
		interpretation = Interpreted.class,
		instance = QuickConfirm.class)
	public static class Def extends QuickContentDialog.Def.Abstract<QuickConfirm> {
		private CompiledExpression theOnConfirm;
		private CompiledExpression theOnCancel;
		private CompiledExpression theIcon;

		/**
		 * @param parent The parent element of the dialog
		 * @param qonfigType The Qonfig type of the dialog
		 */
		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		/** @return The action to perform when the user selects "OK" */
		@QonfigAttributeGetter("on-confirm")
		public CompiledExpression getOnConfirm() {
			return theOnConfirm;
		}

		/** @return The action to perform when the user selects "Cancel" */
		@QonfigAttributeGetter("on-cancel")
		public CompiledExpression getOnCancel() {
			return theOnCancel;
		}

		/** @return The icon for the dialog */
		@QonfigAttributeGetter("icon")
		public CompiledExpression getIcon() {
			return theIcon;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);

			theOnConfirm = getAttributeExpression("on-confirm", session);
			theOnCancel = getAttributeExpression("on-cancel", session);
			theIcon = getAttributeExpression("icon", session);
		}

		@Override
		public Interpreted interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}
	}

	/** {@link QuickConfirm} interpretation */
	public static class Interpreted extends QuickContentDialog.Interpreted.Abstract<QuickConfirm> {
		private InterpretedValueSynth<ObservableAction, ObservableAction> theOnConfirm;
		private InterpretedValueSynth<ObservableAction, ObservableAction> theOnCancel;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Image>> theIcon;

		/**
		 * @param definition The definition to interpret
		 * @param parent The parent element for the dialog
		 */
		protected Interpreted(Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		/** @return The action to perform when the user selects "OK" */
		public InterpretedValueSynth<ObservableAction, ObservableAction> getOnConfirm() {
			return theOnConfirm;
		}

		/** @return The action to perform when the user selects "Cancel" */
		public InterpretedValueSynth<ObservableAction, ObservableAction> getOnCancel() {
			return theOnCancel;
		}

		/** @return The icon for the dialog */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<Image>> getIcon() {
			return theIcon;
		}

		@Override
		protected void doUpdate(InterpretedExpressoEnv expressoEnv) throws ExpressoInterpretationException {
			super.doUpdate(expressoEnv);

			theOnConfirm = interpret(getDefinition().getOnConfirm(), ModelTypes.Action.instance());
			theOnCancel = interpret(getDefinition().getOnCancel(), ModelTypes.Action.instance());
			theIcon = QuickCoreInterpretation.evaluateIcon(getDefinition().getIcon(), this,
				getDefinition().getElement().getDocument().getLocation());
		}

		@Override
		public QuickConfirm create() {
			return new QuickConfirm(getIdentity());
		}
	}

	private ModelValueInstantiator<ObservableAction> theOnConfirmInstantiator;
	private ModelValueInstantiator<ObservableAction> theOnCancelInstantiator;
	private ModelValueInstantiator<SettableValue<Image>> theIconInstantiator;

	private SettableValue<ObservableAction> theOnConfirm;
	private SettableValue<ObservableAction> theOnCancel;
	private SettableValue<SettableValue<Image>> theIcon;

	/** @param id The element ID for this dialog */
	protected QuickConfirm(Object id) {
		super(id);
		theOnConfirm = SettableValue.build(ObservableAction.class).build();
		theOnCancel = SettableValue.build(ObservableAction.class).build();
		theIcon = SettableValue.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<Image>> parameterized(Image.class))
			.build();
	}

	/** @return The action to perform when the user selects "OK" */
	public ObservableAction getOnConfirm() {
		return ObservableAction.flatten(theOnConfirm);
	}

	/** @return The action to perform when the user selects "Cancel" */
	public ObservableAction getOnCancel() {
		return ObservableAction.flatten(theOnCancel);
	}

	/** @return The icon for the dialog */
	public SettableValue<Image> getIcon() {
		return SettableValue.flatten(theIcon);
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);
		Interpreted myInterpreted = (Interpreted) interpreted;
		theOnConfirmInstantiator = myInterpreted.getOnConfirm().instantiate();
		theOnCancelInstantiator = myInterpreted.getOnCancel() == null ? null : myInterpreted.getOnCancel().instantiate();
		theIconInstantiator = myInterpreted.getIcon() == null ? null : myInterpreted.getIcon().instantiate();
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
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
