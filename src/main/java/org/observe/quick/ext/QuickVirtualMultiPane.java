package org.observe.quick.ext;

import java.util.List;

import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelComponentId;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExFlexibleElementModelAddOn;
import org.observe.expresso.qonfig.ExMultiElementTraceable;
import org.observe.expresso.qonfig.ExWithElementModel;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.expresso.qonfig.QonfigChildGetter;
import org.observe.quick.QuickCoreInterpretation;
import org.observe.quick.QuickWidget;
import org.observe.quick.base.MultiValueRenderable;
import org.observe.quick.base.QuickLayout;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

/**
 * A widget that renders each value of a collection as a separate content widget.
 *
 * @param <T> The type of the values in the collection
 */
public class QuickVirtualMultiPane<T> extends QuickWidget.Abstract implements MultiValueRenderable<T> {
	/** The XML name of this element */
	public static final String VIRTUAL_MULTI_PANE = "virtual-multi-pane";

	/** {@link QuickVirtualMultiPane} definition */
	@ExMultiElementTraceable({
		@ExElementTraceable(toolkit = QuickXInterpretation.X,
			qonfigType = VIRTUAL_MULTI_PANE,
			interpretation = Interpreted.class,
			instance = QuickVirtualMultiPane.class),
		@ExElementTraceable(toolkit = QuickCoreInterpretation.CORE,
		qonfigType = "rendering",
		interpretation = Interpreted.class,
		instance = QuickVirtualMultiPane.class)//
	})
	public static class Def extends QuickWidget.Def.Abstract<QuickVirtualMultiPane<?>>
	implements MultiValueRenderable.Def<QuickVirtualMultiPane<?>> {
		private ModelComponentId theActiveValueVariable;
		private ModelComponentId theSelectedVariable;
		private ModelComponentId theValueIndexVariable;
		private CompiledExpression theValues;
		private QuickWidget.Def<?> theRenderer;
		private boolean isConstantSizing;

		/**
		 * @param parent The parent element of the widget
		 * @param type The Qonfig type of the widget
		 */
		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		/** @return The values, each of which to represent as a separate content widget */
		@QonfigAttributeGetter(asType = VIRTUAL_MULTI_PANE, value = "values")
		public CompiledExpression getValues() {
			return theValues;
		}

		@Override
		public ModelComponentId getActiveValueVariable() {
			return theActiveValueVariable;
		}

		/** @return The model ID of the value containing whether the current value is selected */
		public ModelComponentId getSelectedVariable() {
			return theSelectedVariable;
		}

		/** @return The model ID of the value containing the index of the current value */
		public ModelComponentId getValueIndexVariable() {
			return theValueIndexVariable;
		}

		/** @return The layout for the content of this multi pane */
		@QonfigAttributeGetter(asType = VIRTUAL_MULTI_PANE, value = "layout")
		public QuickLayout.Def<?> getLayout() {
			return getAddOn(QuickLayout.Def.class);
		}

		/** @return The renderer to render each value of this multi pane */
		@QonfigChildGetter(asType = "rendering", value = "renderer")
		public QuickWidget.Def<?> getRenderer() {
			return theRenderer;
		}

		/** @return Whether all values render to the same size */
		@QonfigAttributeGetter(asType = VIRTUAL_MULTI_PANE, value = "constant-size")
		public boolean isConstantSizing() {
			return isConstantSizing;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));

			ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
			theActiveValueVariable = elModels.getElementValueModelId(session.getAttributeText("active-value-name"));
			theSelectedVariable = elModels.getElementValueModelId("selected");
			theValueIndexVariable = elModels.getElementValueModelId("rowIndex");
			theValues = getAttributeExpression("values", session);
			elModels.<Interpreted<?>, SettableValue<?>> satisfyElementSingleValueType(theActiveValueVariable, ModelTypes.Value,
				Interpreted::getValueType);

			List<ExpressoQIS> renderer = session.forChildren("renderer");
			if (renderer.isEmpty())
				renderer = session.metadata().get("default-renderer").get();
			if (renderer.size() > 1)
				reporting().error("Multiple renderers not supported for " + getQonfigType());
			theRenderer = syncChild(QuickWidget.Def.class, theRenderer, renderer.get(0), null);
			isConstantSizing = session.getAttribute("constant-size", boolean.class);
		}

		@Override
		public QuickWidget.Interpreted<? extends QuickVirtualMultiPane<?>> interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}
	}

	/**
	 * {@link QuickVirtualMultiPane} interpretation
	 *
	 * @param <T> The type of the values in the collection
	 */
	public static class Interpreted<T> extends QuickWidget.Interpreted.Abstract<QuickVirtualMultiPane<T>>
	implements MultiValueRenderable.Interpreted<T, QuickVirtualMultiPane<T>> {
		private InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<T>> theValues;
		private QuickWidget.Interpreted<?> theRenderer;

		/**
		 * @param definition The definition to interpret
		 * @param parent The parent element for the widget
		 */
		protected Interpreted(QuickVirtualMultiPane.Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		/**
		 * @return The type of values in the collection
		 * @throws ExpressoInterpretationException If the type of the values cannot be interpreted
		 */
		public TypeToken<T> getValueType() throws ExpressoInterpretationException {
			if (theValues == null) {
				theValues = interpret(getDefinition().getValues(), ModelTypes.Collection.anyAsV());
			}
			return (TypeToken<T>) theValues.getType().getType(0);
		}

		/** @return The values, each of which to represent as a separate content widget */
		public InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<T>> getValues() {
			return theValues;
		}

		/** @return The layout for the content of this multi pane */
		public QuickLayout.Interpreted<?> getLayout() {
			return getAddOn(QuickLayout.Interpreted.class);
		}

		/** @return The renderer to render each value of this multi pane */
		public QuickWidget.Interpreted<?> getRenderer() {
			return theRenderer;
		}

		@Override
		protected void doUpdate() throws ExpressoInterpretationException {
			super.doUpdate();
			getValueType();
			theRenderer = syncChild(getDefinition().getRenderer(), theRenderer, def -> def.interpret(this), r -> r.updateElement());
		}

		@Override
		public QuickVirtualMultiPane<T> create() {
			return new QuickVirtualMultiPane<>(getIdentity());
		}
	}

	private ModelComponentId theSelectedVariable;
	private ModelComponentId theValueIndexVariable;
	private ModelComponentId theActiveValueVariable;
	private ModelValueInstantiator<ObservableCollection<T>> theValuesInstantiator;

	private SettableValue<ObservableCollection<T>> theValues;
	private SettableValue<T> theActiveValue;
	private SettableValue<Boolean> isSelected;
	private SettableValue<Integer> theValueIndex;
	private QuickWidget theRenderer;
	private boolean isConstantSizing;

	/** @param id The element ID for this widget */
	protected QuickVirtualMultiPane(Object id) {
		super(id);
		theValues = SettableValue.create();
		theActiveValue = SettableValue.create();
		isSelected = SettableValue.create(b -> b.withValue(false));
		theValueIndex = SettableValue.create(b -> b.withValue(0));
	}

	@Override
	public ModelComponentId getActiveValueVariable() {
		return theActiveValueVariable;
	}

	@Override
	public ModelComponentId getSelectedVariable() {
		return theSelectedVariable;
	}

	/** @return The values, each of which to represent as a separate content widget */
	public ObservableCollection<T> getValues() {
		return ObservableCollection.flattenValue(theValues);
	}

	/** @return The active value (e.g. the one being rendered or interacted with) */
	@Override
	public SettableValue<T> getActiveValue() {
		return theActiveValue;
	}

	@Override
	public SettableValue<Boolean> isSelected() {
		return isSelected;
	}

	/** @return The index of the active value (e.g. the one being rendered or interacted with) */
	public SettableValue<Integer> getValueIndex() {
		return theValueIndex;
	}

	/** @return The layout for the content of this multi pane */
	public QuickLayout getLayout() {
		return getAddOn(QuickLayout.class);
	}

	/** @return The renderer to render each value of this multi pane */
	public QuickWidget getRenderer() {
		return theRenderer;
	}

	/** @return Whether all values render to the same size */
	public boolean isConstantSizing() {
		return isConstantSizing;
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);

		QuickVirtualMultiPane.Interpreted<T> myInterpreted = (QuickVirtualMultiPane.Interpreted<T>) interpreted;
		theValuesInstantiator = myInterpreted.getValues().instantiate();
		theSelectedVariable = myInterpreted.getDefinition().getSelectedVariable();
		theValueIndexVariable = myInterpreted.getDefinition().getValueIndexVariable();
		theActiveValueVariable = myInterpreted.getDefinition().getActiveValueVariable();

		if (theRenderer != null
			&& (myInterpreted.getRenderer() == null || theRenderer.getIdentity() != myInterpreted.getRenderer().getIdentity())) {
			theRenderer.destroy();
			theRenderer = null;
		}
		if (theRenderer == null && myInterpreted.getRenderer() != null)
			theRenderer = myInterpreted.getRenderer().create();
		if (theRenderer != null)
			theRenderer.update(myInterpreted.getRenderer(), this);
		isConstantSizing = myInterpreted.getDefinition().isConstantSizing();
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
		super.instantiated();

		theValuesInstantiator.instantiate();
		if (theRenderer != null)
			theRenderer.instantiated();
	}

	@Override
	protected ModelSetInstance doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		myModels = super.doInstantiate(myModels);

		theValues.set(theValuesInstantiator.get(myModels), null);

		ExFlexibleElementModelAddOn.satisfyElementValue(theActiveValueVariable, myModels, theActiveValue);
		ExFlexibleElementModelAddOn.satisfyElementValue(theSelectedVariable, myModels, isSelected);
		ExFlexibleElementModelAddOn.satisfyElementValue(theValueIndexVariable, myModels, theValueIndex);

		if (theRenderer != null)
			theRenderer.instantiate(myModels);
		return myModels;
	}

	@Override
	public QuickVirtualMultiPane<T> copy(ExElement parent) {
		QuickVirtualMultiPane<T> copy = (QuickVirtualMultiPane<T>) super.copy(parent);

		copy.theValues = SettableValue.create();
		copy.theActiveValue = SettableValue.create();
		copy.isSelected = SettableValue.create(b -> b.withValue(false));
		copy.theValueIndex = SettableValue.create(b -> b.withValue(0));
		if (theRenderer != null)
			copy.theRenderer = theRenderer.copy(copy);

		return copy;
	}
}
