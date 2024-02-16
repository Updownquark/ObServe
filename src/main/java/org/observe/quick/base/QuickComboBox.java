package org.observe.quick.base;

import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExMultiElementTraceable;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigChildGetter;
import org.observe.quick.QuickCoreInterpretation;
import org.observe.quick.QuickWidget;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

/**
 * A dropdown box that allows the user to select one of a collection of values
 *
 * @param <T> The type of the value to select
 */
public class QuickComboBox<T> extends CollectionSelectorWidget<T> {
	/** The XML name of this element */
	public static final String COMBO_BOX = "combo";

	/** {@link QuickCheckBox} definition */
	@ExMultiElementTraceable({
		@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
			qonfigType = COMBO_BOX,
			interpretation = Interpreted.class,
			instance = QuickComboBox.class),
		@ExElementTraceable(toolkit = QuickCoreInterpretation.CORE,
		qonfigType = "rendering",
		interpretation = Interpreted.class,
		instance = QuickComboBox.class)//
	})
	public static class Def extends CollectionSelectorWidget.Def<QuickComboBox<?>> {
		private QuickWidget.Def<?> theRenderer;

		/**
		 * @param parent The parent element of the widget
		 * @param type The Qonfig type of the widget
		 */
		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		/** @return The renderer to determine how values in the combo box appear */
		@QonfigChildGetter(asType = "rendering", value = "renderer")
		public QuickWidget.Def<?> getRenderer() {
			return theRenderer;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);

			ExpressoQIS renderer = session.forChildren("renderer").peekFirst();
			if (renderer == null)
				renderer = session.metadata().get("default-renderer").get().peekFirst();
			theRenderer = syncChild(QuickWidget.Def.class, theRenderer, renderer, null);
		}

		@Override
		public Interpreted<?> interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}
	}

	/**
	 * {@link QuickCheckBox} interpretation
	 *
	 * @param <T> The type of the value to select
	 */
	public static class Interpreted<T> extends CollectionSelectorWidget.Interpreted<T, QuickComboBox<T>> {
		private QuickWidget.Interpreted<?> theRenderer;

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

		/** @return The renderer to determine how values in the combo box appear */
		public QuickWidget.Interpreted<?> getRenderer() {
			return theRenderer;
		}

		@Override
		protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			super.doUpdate(env);
			theRenderer = syncChild(getDefinition().getRenderer(), theRenderer, def -> def.interpret(this),
				(r, rEnv) -> r.updateElement(rEnv));
		}

		@Override
		public QuickComboBox<T> create() {
			return new QuickComboBox<>(getIdentity());
		}
	}

	private QuickWidget theRenderer;

	/** @param id The element ID for this widget */
	protected QuickComboBox(Object id) {
		super(id);
	}

	/** @return The renderer to determine how values in the combo box appear */
	public QuickWidget getRenderer() {
		return theRenderer;
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);

		Interpreted<T> myInterpreted = (Interpreted<T>) interpreted;
		if (theRenderer != null
			&& (myInterpreted.getRenderer() == null || theRenderer.getIdentity() != myInterpreted.getRenderer().getIdentity())) {
			theRenderer.destroy();
			theRenderer = null;
		}
		if (theRenderer == null && myInterpreted.getRenderer() != null)
			theRenderer = myInterpreted.getRenderer().create();
		if (theRenderer != null)
			theRenderer.update(myInterpreted.getRenderer(), this);
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
		super.instantiated();
		if (theRenderer != null)
			theRenderer.instantiated();
	}

	@Override
	protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		super.doInstantiate(myModels);

		if (theRenderer != null)
			theRenderer.instantiate(myModels);
	}

	@Override
	public QuickComboBox<T> copy(ExElement parent) {
		QuickComboBox<T> copy = (QuickComboBox<T>) super.copy(parent);

		if (theRenderer != null)
			copy.theRenderer = theRenderer.copy(copy);

		return copy;
	}
}
