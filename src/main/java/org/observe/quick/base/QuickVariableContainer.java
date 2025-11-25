package org.observe.quick.base;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.observe.Observable;
import org.observe.SettableValue;
import org.observe.Subscription;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableSortedCollection;
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
import org.observe.quick.QuickContainer;
import org.observe.quick.QuickCoreInterpretation;
import org.observe.quick.QuickRenderer;
import org.observe.quick.QuickWidget;
import org.qommons.Transaction;
import org.qommons.collect.CollectionUtils;
import org.qommons.collect.ElementId;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.io.LocatedFilePosition;

import com.google.common.reflect.TypeToken;

/**
 * A container whose content may be specified one-by-one, or via &lt;multi-widget> elements that create a component widget for each value in
 * a collection.
 */
public abstract class QuickVariableContainer extends QuickContainer.Abstract<QuickWidget> {
	/** The XML name of this element */
	public static final String VARIABLE_CONTAINER = "variable-container";

	/**
	 * Represents a set of widgets, one for each value in a collection
	 *
	 * @param <T> The type of values in the collection
	 */
	public static class MultiWidget<T> extends ExElement.Abstract implements WidgetSource {
		/** The XML name of this element */
		public static final String MULTI_WIDGET = "multi-widget";

		/** {@link MultiWidget} definition */
		@ExMultiElementTraceable({ @ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
			qonfigType = MULTI_WIDGET,
			interpretation = Interpreted.class,
			instance = MultiWidget.class), //
			@ExElementTraceable(toolkit = QuickCoreInterpretation.CORE,
			qonfigType = "rendering",
			interpretation = Interpreted.class,
			instance = QuickComboBox.class)//
		})
		public static class Def extends ExElement.Def.Abstract<MultiWidget<?>> {
			private CompiledExpression theValues;
			private ModelComponentId theActiveValueVariable;
			private QuickWidget.Def<?> theRenderer;

			/**
			 * @param parent The parent element of the multi-widget
			 * @param qonfigType The Qonfig type of the multi-widget
			 */
			public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
				super(parent, qonfigType);
			}

			/** @return The collection whose values to represent with content widgets in a {@link QuickVariableContainer} pane */
			@QonfigAttributeGetter(asType = MULTI_WIDGET, value = "values")
			public CompiledExpression getValues() {
				return theValues;
			}

			/** @return The model ID of the variable by which the value to render will be available in expressions */
			@QonfigAttributeGetter(asType = MULTI_WIDGET, value = "active-value-name")
			public ModelComponentId getActiveValueVariable() {
				return theActiveValueVariable;
			}

			/** @return The widget to show as the content for each value in the collection */
			@QonfigChildGetter(asType = "rendering", value = "renderer")
			public QuickWidget.Def<?> getRenderer() {
				return theRenderer;
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				super.doUpdate(session);

				String activeValueName = session.getAttributeText("active-value-name");
				theValues = getAttributeExpression("values", session);
				ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
				theActiveValueVariable = elModels.getElementValueModelId(activeValueName);
				elModels.<Interpreted<?>, SettableValue<?>> satisfyElementSingleValueType(theActiveValueVariable, ModelTypes.Value,
					Interpreted::getValueType);

				theRenderer = syncChild(QuickWidget.Def.class, theRenderer, session, "renderer");
			}

			/**
			 * @param parent The parent element for the interpreted tab set
			 * @return The interpreted tab set
			 */
			public Interpreted<?> interpret(ExElement.Interpreted<?> parent) {
				return new Interpreted<>(this, parent);
			}
		}

		/**
		 * {@link MultiWidget} interpretation
		 *
		 * @param <T> The type of values in the collection
		 */
		public static class Interpreted<T> extends ExElement.Interpreted.Abstract<MultiWidget<T>> {
			private InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<T>> theValues;
			private QuickWidget.Interpreted<?> theRenderer;

			/**
			 * @param definition The definition to interpret
			 * @param parent The parent element for the tab set
			 */
			protected Interpreted(Def definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public Def getDefinition() {
				return (Def) super.getDefinition();
			}

			/** @return The collection whose values to represent with tabs in a {@link QuickVariableContainer} */
			public InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<T>> getValues() {
				return theValues;
			}

			/** @return The widget to show as the content for each value in the collection */
			public QuickWidget.Interpreted<?> getRenderer() {
				return theRenderer;
			}

			/**
			 * Initializes or updates this tab set
			 *
			 * @throws ExpressoInterpretationException If this multi-widget could not be interpreted
			 */
			public void updateTabSet() throws ExpressoInterpretationException {
				update();
			}

			@Override
			protected void doUpdate() throws ExpressoInterpretationException {
				super.doUpdate();
				theValues = interpret(getDefinition().getValues(), ModelTypes.Collection.<T> anyAsV());
				theRenderer = syncChild(getDefinition().getRenderer(), theRenderer, def -> def.interpret(this),
					r -> r.updateElement());
				theRenderer.getAddOn(QuickRenderer.Interpreted.class).setVirtual(false);
			}

			/** @return The type of values in the collection */
			public TypeToken<T> getValueType() {
				return (TypeToken<T>) theValues.getType().getType(0);
			}

			/** @return The tab set */
			public MultiWidget<T> create() {
				return new MultiWidget<>(getIdentity());
			}
		}

		private ModelValueInstantiator<ObservableCollection<T>> theValuesInstantiator;
		private SettableValue<ObservableCollection<T>> theValues;
		private ObservableCollection<T> theFlatValues;
		private ModelComponentId theActiveValueVariable;
		private ObservableCollection<MultiWidgetInstance> theWidgetInstances;
		private QuickWidget theRenderer;
		private int theInstantiatedModel;

		/** @param id The element ID for this tab set */
		protected MultiWidget(Object id) {
			super(id);
			init();
		}

		private void init() {
			theValues = SettableValue.create();
			theWidgetInstances = ObservableCollection.create();
			theFlatValues = ObservableCollection.flattenValue(theValues);
			Subscription valueSub = theFlatValues.subscribe(evt -> {
				switch (evt.getType()) {
				case add:
					theWidgetInstances.add(evt.getIndex(), createWidgetInstance(evt.getElementId(), evt.getNewValue()));
					break;
				case remove:
					theWidgetInstances.remove(evt.getIndex()).remove();
					break;
				case set:
					theWidgetInstances.get(evt.getIndex()).update(evt.getNewValue(), evt);
					break;
				}
			}, true);
			isDestroyed().noInitChanges().take(1).act0(valueSub::unsubscribe);
		}

		@Override
		public LocatedFilePosition getPosition() {
			return reporting().getPosition();
		}

		/** @return The collection whose values to represent with tabs in a {@link QuickVariableContainer} */
		public ObservableCollection<T> getValues() {
			return theFlatValues;
		}

		@Override
		public ObservableCollection<? extends QuickWidget> getWidgetInstances() {
			return theWidgetInstances.flow()//
				.<QuickWidget> transform(tx -> tx.cache(false).map(MultiWidgetInstance::getRenderer))//
				.collectPassive();
		}

		/** @return The widget to show as the content for each value in the collection */
		public QuickWidget getRenderer() {
			return theRenderer;
		}

		@Override
		protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
			super.doUpdate(interpreted);
			Interpreted<T> myInterpreted = (Interpreted<T>) interpreted;
			theValuesInstantiator = myInterpreted.getValues().instantiate();
			theActiveValueVariable = myInterpreted.getDefinition().getActiveValueVariable();
			if (theRenderer == null || theRenderer.getIdentity() != myInterpreted.getRenderer().getIdentity()) {
				if (theRenderer != null) {
					// TODO Gotta replace all the instance renderers
					theRenderer.destroy();
				}
				theRenderer = myInterpreted.getRenderer().create();
			}
			theRenderer.update(myInterpreted.getRenderer(), this);
			for (MultiWidgetInstance tab : theWidgetInstances)
				tab.update(myInterpreted.getRenderer());
			persistModels(); // Need to keep the model instance around to copy it for new values
		}

		@Override
		public void instantiated() throws ModelInstantiationException {
			super.instantiated();

			theValuesInstantiator.instantiate();

			theRenderer.instantiated();
			for (MultiWidgetInstance child : theWidgetInstances)
				child.getRenderer().instantiated();
		}

		@Override
		protected ModelSetInstance doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
			theInstantiatedModel++;
			myModels = super.doInstantiate(myModels);

			theValues.set(theValuesInstantiator.get(myModels), null);

			// No need to instantiate the renderer--it's just a template
			for (MultiWidgetInstance child : theWidgetInstances) {
				if (child.theInstanceInstantiatedModel != theInstantiatedModel)
					child.instantiate(myModels);
			}
			return myModels;
		}

		@Override
		public MultiWidget<T> copy(ExElement parent) {
			MultiWidget<T> copy = (MultiWidget<T>) super.copy(parent);

			copy.init();
			// No need to copy the renderer here--it's just a template

			return copy;
		}

		MultiWidgetInstance createWidgetInstance(ElementId element, T id) {
			QuickWidget renderer = theRenderer.copy(this);
			MultiWidgetInstance result = new MultiWidgetInstance(element, id, renderer);
			try {
				result.instantiate(getUpdatingModels());
			} catch (ModelInstantiationException e) {
				reporting().error("Could not instantiate renderer for new widget value " + id, e);
			}
			return result;
		}

		class MultiWidgetInstance {
			final ElementId theValueEl;
			private final SettableValue<T> theValue;
			private final QuickWidget theRendererInstance;
			private boolean isUpdating;
			private final Subscription theValueSub;
			int theInstanceInstantiatedModel;

			MultiWidgetInstance(ElementId valueEl, T tabId, QuickWidget renderer) {
				theValueEl = valueEl;
				theValue = SettableValue.create(b -> b.withValue(tabId));
				theRendererInstance = renderer;
				theValueSub = theValue.noInitChanges().filter(__ -> !isUpdating && theValueEl.isPresent()).act(evt -> {
					try (Transaction t = theFlatValues.lock(true, evt)) {
						theFlatValues.mutableElement(theValueEl).set(evt.getNewValue());
					}
				});
			}

			void update(QuickWidget.Interpreted<?> renderer) throws ModelInstantiationException {
				theRendererInstance.update(renderer, MultiWidget.this);
			}

			void instantiate(ModelSetInstance models) throws ModelInstantiationException {
				ModelSetInstance copy = QuickCoreInterpretation
					.copyModels(models, theActiveValueVariable, Observable.or(models.getUntil(), theRendererInstance.onDestroy())).build();
				ExFlexibleElementModelAddOn.satisfyElementValue(theActiveValueVariable, copy, theValue);

				theRendererInstance.instantiate(copy);
				theInstanceInstantiatedModel = theInstantiatedModel;
			}

			void update(T newValue, Object cause) {
				isUpdating = true;
				try {
					theValue.set(newValue, cause);
				} finally {
					isUpdating = false;
				}
			}

			void remove() {
				theValueSub.unsubscribe();
				theRendererInstance.destroy();
			}

			public T getValue() {
				return theValue.get();
			}

			public QuickWidget getRenderer() {
				return theRendererInstance;
			}

			@Override
			public String toString() {
				return theRendererInstance.toString() + "(" + theValue.get() + ")";
			}
		}
	}

	/**
	 * {@link QuickVariableContainer} definition
	 *
	 * @param <W> The sub-type of container this definition can create
	 */
	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = VARIABLE_CONTAINER,
		interpretation = Interpreted.class,
		instance = QuickVariableContainer.class)
	public static abstract class Def<W extends QuickVariableContainer> extends QuickContainer.Def.Abstract<W, QuickWidget> {
		private final List<MultiWidget.Def> theWidgetSets;

		/**
		 * @param parent The parent element of the widget
		 * @param type The Qonfig type of the widget
		 */
		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
			theWidgetSets = new ArrayList<>();
		}

		/** @return All the &lt;multi-widget>s in the tab pane */
		@QonfigChildGetter("widget-set")
		public List<MultiWidget.Def> getWidgetSets() {
			return Collections.unmodifiableList(theWidgetSets);
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);

			syncChildren(MultiWidget.Def.class, theWidgetSets, session.forChildren("widget-set"));
		}

		@Override
		public abstract Interpreted<? extends W> interpret(ExElement.Interpreted<?> parent);
	}

	/**
	 * {@link QuickVariableContainer} interpretation
	 *
	 * @param <W> The sub-type of container this interpretation can create
	 */
	public static abstract class Interpreted<W extends QuickVariableContainer> extends QuickContainer.Interpreted.Abstract<W, QuickWidget> {
		private final List<MultiWidget.Interpreted<?>> theWidgetSets;

		/**
		 * @param definition The definition to interpret
		 * @param parent The parent element for the widget
		 */
		protected Interpreted(Def<? super W> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
			theWidgetSets = new ArrayList<>();
		}

		@Override
		public Def<? super W> getDefinition() {
			return (Def<? super W>) super.getDefinition();
		}

		/** @return All the &lt;multi-widget>s in the container */
		public List<MultiWidget.Interpreted<?>> getWidgetSets() {
			return Collections.unmodifiableList(theWidgetSets);
		}

		@Override
		protected void doUpdate() throws ExpressoInterpretationException {
			super.doUpdate();

			syncChildren(getDefinition().getWidgetSets(), theWidgetSets, def -> (MultiWidget.Interpreted<?>) def.interpret(this),
				MultiWidget.Interpreted::updateTabSet);
		}

		@Override
		public abstract W create();
	}

	private ObservableCollection<MultiWidget<?>> theWidgetSets;
	private ObservableSortedCollection<WidgetSource> theWidgetSources;
	private ObservableCollection<QuickWidget> theWidgets;

	/** @param id The element ID for this widget */
	protected QuickVariableContainer(Object id) {
		super(id);
		createContentData();
	}

	private void createContentData() {
		theWidgetSets = ObservableCollection.<MultiWidget<?>> build().build();
		theWidgetSources = ObservableCollection.flattenCollections(
			getContents().flow()//
			.<WidgetSource> transform(tx -> tx.cache(false).map(SingleWidgetSource::new))//
			.collectPassive(), //
			theWidgetSets)//
			.sorted(WidgetSource::compareTo)//
			.collectActive(isDestroyed().noInitChanges().take(1));
		theWidgets = theWidgetSources.flow()//
			.<QuickWidget> flatMap(tabSource -> tabSource.getWidgetInstances().flow())//
			.collectActive(isDestroyed().noInitChanges().take(1));
	}

	/** @return All the &lt;multi-widget>s in the tab pane */
	public ObservableCollection<MultiWidget<?>> getWidgetSets() {
		return theWidgetSets;
	}

	/** @return The tabs in this tab pane */
	public ObservableCollection<QuickWidget> getAllContent() {
		return theWidgets;
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		Interpreted<?> myInterpreted = (Interpreted<?>) interpreted;
		super.doUpdate(interpreted);

		CollectionUtils
		.synchronize(theWidgetSets, myInterpreted.getWidgetSets(), (inst, interp) -> inst.getIdentity() == interp.getIdentity())//
		.<ModelInstantiationException> simpleX(interp -> interp.create())//
		.onLeft(el -> theWidgetSets.remove(el.getLeftValue()))//
		.onRightX(el -> el.getLeftValue().update(el.getRightValue(), this))//
		.onCommonX(el -> el.getLeftValue().update(el.getRightValue(), this))//
		.rightOrder()//
		.adjust();
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
		super.instantiated();

		for (MultiWidget<?> tabSet : theWidgetSets)
			tabSet.instantiated();
	}

	@Override
	protected ModelSetInstance doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		myModels = super.doInstantiate(myModels);

		for (MultiWidget<?> tabSet : theWidgetSets)
			tabSet.instantiate(myModels);
		return myModels;
	}

	@Override
	public QuickVariableContainer copy(ExElement parent) {
		QuickVariableContainer copy = (QuickVariableContainer) super.copy(parent);

		copy.createContentData();

		for (MultiWidget<?> widgetSet : theWidgetSets)
			copy.theWidgetSets.add(widgetSet.copy(this));

		return copy;
	}

	private interface WidgetSource extends Comparable<WidgetSource> {
		LocatedFilePosition getPosition();

		ObservableCollection<? extends QuickWidget> getWidgetInstances();

		@Override
		default int compareTo(WidgetSource o) {
			return Integer.compare(getPosition().getPosition(), o.getPosition().getPosition());
		}
	}

	static class SingleWidgetSource implements WidgetSource {
		private final QuickWidget theRenderer;

		public SingleWidgetSource(QuickWidget renderer) {
			theRenderer = renderer;
		}

		@Override
		public LocatedFilePosition getPosition() {
			return theRenderer.reporting(theRenderer.getParentElement().reporting().getPosition().getFileLocation()).getPosition();
		}

		@Override
		public ObservableCollection<? extends QuickWidget> getWidgetInstances() {
			return ObservableCollection.of(theRenderer);
		}

		@Override
		public String toString() {
			return theRenderer.toString();
		}
	}
}
