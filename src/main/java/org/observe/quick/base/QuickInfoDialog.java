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
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

/** A simple dialog that displays a message with a severity */
public class QuickInfoDialog extends QuickContentDialog.Abstract {
	/** The XML name of this element */
	public static final String INFO_DIALOG = "info-dialog";

	/** {@link QuickInfoDialog} definition */
	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = INFO_DIALOG,
		interpretation = Interpreted.class,
		instance = QuickInfoDialog.class)
	public static class Def extends QuickContentDialog.Def.Abstract<QuickInfoDialog> {
		private CompiledExpression theType;
		private CompiledExpression theOnClose;
		private CompiledExpression theIcon;

		/**
		 * @param parent The parent element of the widget
		 * @param qonfigType The Qonfig type of the widget
		 */
		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		/** @return The type of the dialog--a String that determines the severity of the message */
		@QonfigAttributeGetter("type")
		public CompiledExpression getType() {
			return theType;
		}

		/** @return An action to perform when the user closes the dialog */
		@QonfigAttributeGetter("on-close")
		public CompiledExpression getOnClose() {
			return theOnClose;
		}

		/** @return The icon for the dialog */
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

	/** {@link QuickInfoDialog} interpretation */
	public static class Interpreted extends QuickContentDialog.Interpreted.Abstract<QuickInfoDialog> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theType;
		private InterpretedValueSynth<ObservableAction, ObservableAction> theOnClose;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Image>> theIcon;

		/**
		 * @param definition The definition to interpret
		 * @param parent The parent element for the widget
		 */
		protected Interpreted(Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		/** @return The type of the dialog--a String that determines the severity of the message */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getType() {
			return theType;
		}

		/** @return An action to perform when the user closes the dialog */
		public InterpretedValueSynth<ObservableAction, ObservableAction> getOnClose() {
			return theOnClose;
		}

		/** @return The icon for the dialog */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<Image>> getIcon() {
			return theIcon;
		}

		@Override
		protected void doUpdate(InterpretedExpressoEnv expressoEnv) throws ExpressoInterpretationException {
			super.doUpdate(expressoEnv);

			theType = interpret(getDefinition().getType(), ModelTypes.Value.forType(String.class));
			theOnClose = interpret(getDefinition().getOnClose(), ModelTypes.Action.instance());
			theIcon = QuickCoreInterpretation.evaluateIcon(getDefinition().getIcon(), this,
				getDefinition().getElement().getDocument().getLocation());
		}

		@Override
		public QuickInfoDialog create() {
			return new QuickInfoDialog(getIdentity());
		}
	}

	private ModelValueInstantiator<SettableValue<String>> theTypeInstantiator;
	private ModelValueInstantiator<ObservableAction> theOnCloseInstantiator;
	private ModelValueInstantiator<SettableValue<Image>> theIconInstantiator;

	private SettableValue<SettableValue<String>> theType;
	private SettableValue<ObservableAction> theOnClose;
	private SettableValue<SettableValue<Image>> theIcon;

	/** @param id The element ID for this widget */
	protected QuickInfoDialog(Object id) {
		super(id);
		theType = SettableValue.<SettableValue<String>> build().build();
		theOnClose = SettableValue.<ObservableAction> build().build();
		theIcon = SettableValue.<SettableValue<Image>> build().build();
	}

	/** @return The type of the dialog--a String that determines the severity of the message */
	public SettableValue<String> getType() {
		return SettableValue.flatten(theType);
	}

	/** @return An action to perform when the user closes the dialog */
	public ObservableAction getOnClose() {
		return ObservableAction.flatten(theOnClose);
	}

	/** @return The icon for the dialog */
	public SettableValue<Image> getIcon() {
		return SettableValue.flatten(theIcon);
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);
		Interpreted myInterpreted = (Interpreted) interpreted;
		theTypeInstantiator = myInterpreted.getType().instantiate();
		theOnCloseInstantiator = myInterpreted.getOnClose() == null ? null : myInterpreted.getOnClose().instantiate();
		theIconInstantiator = myInterpreted.getIcon() == null ? null : myInterpreted.getIcon().instantiate();
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
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

		copy.theType = SettableValue.<SettableValue<String>> build().build();
		copy.theOnClose = SettableValue.<ObservableAction> build().build();
		copy.theIcon = SettableValue.<SettableValue<Image>> build().build();

		return copy;
	}
}
