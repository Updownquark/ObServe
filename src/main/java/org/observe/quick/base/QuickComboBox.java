package org.observe.quick.base;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.observe.expresso.ExpressoInterpretationException;
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
		private final List<QuickWidget.Def<?>> theRenderers;

		/**
		 * @param parent The parent element of the widget
		 * @param type The Qonfig type of the widget
		 */
		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
			theRenderers = new ArrayList<>();
		}

		/** @return The renderer to determine how values in the combo box appear */
		@QonfigChildGetter(asType = "rendering", value = "renderer")
		public List<QuickWidget.Def<?>> getRenderers() {
			return Collections.unmodifiableList(theRenderers);
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);

			List<ExpressoQIS> renderers = session.forChildren("renderer");
			if (renderers.isEmpty())
				renderers = session.metadata().get("default-renderer").get();
			syncChildren(QuickWidget.Def.class, theRenderers, renderers);
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
		private final List<QuickWidget.Interpreted<?>> theRenderers;

		/**
		 * @param definition The definition to interpret
		 * @param parent The parent element for the widget
		 */
		protected Interpreted(Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
			theRenderers = new ArrayList<>();
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		/** @return The renderer to determine how values in the combo box appear */
		public List<QuickWidget.Interpreted<?>> getRenderers() {
			return Collections.unmodifiableList(theRenderers);
		}

		@Override
		protected void doUpdate() throws ExpressoInterpretationException {
			super.doUpdate();
			syncChildren(getDefinition().getRenderers(), theRenderers, def -> def.interpret(this), r -> r.updateElement());
		}

		@Override
		public QuickComboBox<T> create() {
			return new QuickComboBox<>(getIdentity());
		}
	}

	private List<QuickWidget> theRenderers;

	/** @param id The element ID for this widget */
	protected QuickComboBox(Object id) {
		super(id);
		theRenderers = new ArrayList<>();
	}

	/** @return The renderer to determine how values in the combo box appear */
	public List<QuickWidget> getRenderers() {
		return Collections.unmodifiableList(theRenderers);
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);

		Interpreted<T> myInterpreted = (Interpreted<T>) interpreted;
		syncChildren(myInterpreted.getRenderers(), theRenderers, r -> r.create(), QuickWidget::update);
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
		super.instantiated();
		for (QuickWidget renderer : theRenderers)
			renderer.instantiated();
	}

	@Override
	protected ModelSetInstance doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		myModels = super.doInstantiate(myModels);

		for (QuickWidget renderer : theRenderers)
			renderer.instantiate(myModels);
		return myModels;
	}

	@Override
	public QuickComboBox<T> copy(ExElement parent) {
		QuickComboBox<T> copy = (QuickComboBox<T>) super.copy(parent);

		copy.theRenderers = new ArrayList<>();
		for (QuickWidget renderer : theRenderers)
			copy.theRenderers.add(renderer.copy(copy));

		return copy;
	}
}
