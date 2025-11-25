package org.observe.quick.base;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
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
import org.observe.quick.QuickWidget;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

/** A component in the interpretation's native format to be inserted into the Quick UI */
public class QuickCustomComponent extends QuickWidget.Abstract {
	/** The XML name of this element */
	public static final String CUSTOM_COMPONENT = "custom-component";

	/** {@link QuickCustomComponent} definition */
	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = CUSTOM_COMPONENT,
		interpretation = Interpreted.class,
		instance = QuickCustomComponent.class)
	public static class Def extends QuickWidget.Def.Abstract<QuickCustomComponent> {
		private CompiledExpression theComponent;

		/**
		 * @param parent The parent element of the widget
		 * @param type The Qonfig type of the widget
		 */
		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		/** @return The component to insert into the UI */
		@QonfigAttributeGetter
		public CompiledExpression getComponent() {
			return theComponent;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			theComponent = getValueExpression(session);
		}

		@Override
		public Interpreted interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}
	}

	/** {@link QuickCustomComponent} interpretation */
	public static class Interpreted extends QuickWidget.Interpreted.Abstract<QuickCustomComponent> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<?>> theComponent;

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

		/** @return The component to insert into the UI */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<?>> getComponent() {
			return theComponent;
		}

		@Override
		protected void doUpdate() throws ExpressoInterpretationException {
			super.doUpdate();
			theComponent = interpret(getDefinition().getComponent(), ModelTypes.Value.any());
		}

		@Override
		public QuickCustomComponent create() {
			return new QuickCustomComponent(getIdentity());
		}
	}

	private ModelValueInstantiator<SettableValue<?>> theComponentInstantiator;
	private SettableValue<SettableValue<?>> theComponent;

	/** @param id The element ID for this widget */
	protected QuickCustomComponent(Object id) {
		super(id);
		theComponent = SettableValue.create();
	}

	/** @return The component to insert into the UI */
	public ObservableValue<?> getComponent() {
		return ObservableValue.flatten(theComponent);
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);
		Interpreted myInterpreted = (Interpreted) interpreted;
		theComponentInstantiator = myInterpreted.getComponent().instantiate();
	}

	@Override
	protected ModelSetInstance doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		myModels = super.doInstantiate(myModels);
		theComponent.set(theComponentInstantiator.get(myModels), null);
		return myModels;
	}

	@Override
	public QuickCustomComponent copy(ExElement parent) {
		QuickCustomComponent copy = (QuickCustomComponent) super.copy(parent);
		copy.theComponent = SettableValue.create();
		return copy;
	}
}
