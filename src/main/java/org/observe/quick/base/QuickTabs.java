package org.observe.quick.base;

import java.awt.Image;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.SimpleObservable;
import org.observe.Subscription;
import org.observe.Transformation;
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
import org.observe.expresso.qonfig.ExAddOn;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExFlexibleElementModelAddOn;
import org.observe.expresso.qonfig.ExModelAugmentation;
import org.observe.expresso.qonfig.ExWithElementModel;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.expresso.qonfig.QonfigChildGetter;
import org.observe.quick.QuickContainer;
import org.observe.quick.QuickCoreInterpretation;
import org.observe.quick.QuickWidget;
import org.qommons.collect.CollectionUtils;
import org.qommons.collect.ElementId;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.io.LocatedFilePosition;

import com.google.common.reflect.TypeToken;

/**
 * A container that displays a row of tabs at the top, each tab representing one of its content widgets. The widget represented by the
 * selected tab is displayed, all others are hidden.
 *
 * @param <T> The type of the ID values of the tabs
 */
public class QuickTabs<T> extends QuickContainer.Abstract<QuickWidget> {
	/** The XML name of this element */
	public static final String TABS = "tabs";

	/** An add-on inherited by tabs in a {@link QuickTabs} pane */
	public static class AbstractTab extends ExAddOn.Abstract<ExElement> {
		/** The XML name of this add-on */
		public static final String ABSTRACT_TAB = "abstract-tab";

		/** {@link AbstractTab} definition */
		@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
			qonfigType = "abstract-tab",
			interpretation = Interpreted.class,
			instance = QuickTabs.class)
		public static class Def extends ExAddOn.Def.Abstract<ExElement, AbstractTab> {
			private CompiledExpression theTabName;
			private CompiledExpression theTabIcon;
			private CompiledExpression isTabAvailable;

			/**
			 * @param type The Qonfig type of this add-on
			 * @param element The tab element
			 */
			public Def(QonfigAddOn type, ExElement.Def<? extends ExElement> element) {
				super(type, element);
			}

			@Override
			public Set<? extends Class<? extends ExAddOn.Def<?, ?>>> getDependencies() {
				return Collections.singleton((Class<ExAddOn.Def<?, ?>>) (Class<?>) ExModelAugmentation.Def.class);
			}

			/** @return The name of the tab to display */
			@QonfigAttributeGetter("tab-name")
			public CompiledExpression getTabName() {
				return theTabName;
			}

			/** @return The icon to display in the tab */
			@QonfigAttributeGetter("tab-icon")
			public CompiledExpression getTabIcon() {
				return theTabIcon;
			}

			/**
			 * @return Whether the tab is available for selection by the user. Also determines whether the tab can be removed by the user
			 *         (e.g. by clicking an "X" on the tab) if it is assignable to false.
			 */
			@QonfigAttributeGetter("tab-available")
			public CompiledExpression isTabAvailable() {
				return isTabAvailable;
			}

			@Override
			public void update(ExpressoQIS session, ExElement.Def<?> element) throws QonfigInterpretationException {
				super.update(session, element);
				theTabName = element.getAttributeExpression("tab-name", session);
				theTabIcon = element.getAttributeExpression("tab-icon", session);
				isTabAvailable = element.getAttributeExpression("tab-available", session);
			}

			@Override
			public <E2 extends ExElement> Interpreted interpret(ExElement.Interpreted<E2> element) {
				return new Interpreted(this, element);
			}
		}

		/** {@link AbstractTab} interpretation */
		public static class Interpreted extends ExAddOn.Interpreted.Abstract<ExElement, AbstractTab> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theTabName;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Image>> theTabIcon;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> isTabAvailable;

			/**
			 * @param definition The definition to interpret
			 * @param element The tab element
			 */
			protected Interpreted(Def definition, ExElement.Interpreted<? extends ExElement> element) {
				super(definition, element);
			}

			@Override
			public Def getDefinition() {
				return (Def) super.getDefinition();
			}

			/** @return The name of the tab to display */
			public InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getTabName() {
				return theTabName;
			}

			/** @return The icon to display in the tab */
			public InterpretedValueSynth<SettableValue<?>, SettableValue<Image>> getTabIcon() {
				return theTabIcon;
			}

			/** @return Whether the tab is available for selection by the user */
			public InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> isTabAvailable() {
				return isTabAvailable;
			}

			@Override
			public void update(ExElement.Interpreted<?> element) throws ExpressoInterpretationException {
				super.update(element);

				theTabName = getElement().interpret(getDefinition().getTabName(), ModelTypes.Value.STRING);
				theTabIcon = QuickCoreInterpretation.evaluateIcon(getDefinition().getTabIcon(), getElement(),
					getDefinition().getElement().getElement().getDocument().getLocation());
				isTabAvailable = getElement().interpret(getDefinition().isTabAvailable(), ModelTypes.Value.BOOLEAN);
			}

			@Override
			public Class<AbstractTab> getInstanceType() {
				return (Class<AbstractTab>) (Class<?>) AbstractTab.class;
			}

			@Override
			public AbstractTab create(ExElement element) {
				return new AbstractTab(element);
			}
		}

		private ModelValueInstantiator<SettableValue<String>> theTabNameInstantiator;
		private ModelValueInstantiator<SettableValue<Image>> theTabIconInstantiator;
		private ModelValueInstantiator<SettableValue<Boolean>> theAvailableInstantiator;

		private SettableValue<SettableValue<String>> theTabName;
		private SettableValue<SettableValue<Image>> theTabIcon;
		private SettableValue<SettableValue<Boolean>> isTabAvailable;

		/** @param element The tab element */
		protected AbstractTab(ExElement element) {
			super(element);

			theTabName = SettableValue.<SettableValue<String>> build().build();
			theTabIcon = SettableValue.<SettableValue<Image>> build().build();
			isTabAvailable = SettableValue.create();
		}

		@Override
		public Class<Interpreted> getInterpretationType() {
			return Interpreted.class;
		}

		/** @return The name of the tab to display */
		public SettableValue<String> getTabName() {
			return SettableValue.flatten(theTabName);
		}

		/** @return The icon to display in the tab */
		public SettableValue<Image> getTabIcon() {
			return SettableValue.flatten(theTabIcon);
		}

		/** @return Whether the tab is available for selection by the user */
		public SettableValue<Boolean> isTabAvailable() {
			return SettableValue.flatten(isTabAvailable);
		}

		@Override
		public void update(ExAddOn.Interpreted<? super ExElement, ?> interpreted, ExElement element) throws ModelInstantiationException {
			super.update(interpreted, element);
			Interpreted myInterpreted = (Interpreted) interpreted;
			theTabNameInstantiator = myInterpreted.getTabName() == null ? null : myInterpreted.getTabName().instantiate();
			theTabIconInstantiator = myInterpreted.getTabIcon() == null ? null : myInterpreted.getTabIcon().instantiate();
			theAvailableInstantiator = myInterpreted.isTabAvailable().instantiate();
		}

		@Override
		public void instantiated() throws ModelInstantiationException {
			super.instantiated();
			if (theTabNameInstantiator != null)
				theTabNameInstantiator.instantiate();
			if (theTabIconInstantiator != null)
				theTabIconInstantiator.instantiate();
			theAvailableInstantiator.instantiate();
		}

		@Override
		public ModelSetInstance instantiate(ModelSetInstance models) throws ModelInstantiationException {
			models = super.instantiate(models);

			theTabName.set(theTabNameInstantiator == null ? null : theTabNameInstantiator.get(models), null);
			theTabIcon.set(theTabIconInstantiator == null ? null : theTabIconInstantiator.get(models), null);
			isTabAvailable.set(theAvailableInstantiator.get(models), null);
			return models;
		}

		@Override
		public AbstractTab copy(ExElement element) {
			AbstractTab copy = (AbstractTab) super.copy(element);

			copy.theTabName = SettableValue.<SettableValue<String>> build().build();
			copy.theTabIcon = SettableValue.<SettableValue<Image>> build().build();
			copy.isTabAvailable = SettableValue.create();

			return copy;
		}
	}

	/**
	 * An add-on automatically inherited by content components in a {@link QuickTabs} pane
	 *
	 * @param <T> The type of the ID value of the tab
	 */
	public static class Tab<T> extends ExAddOn.Abstract<QuickWidget> {
		/** The XML name of this add-on */
		public static final String TAB = "tab";

		/** {@link Tab} definition */
		@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
			qonfigType = "tab",
			interpretation = Interpreted.class,
			instance = QuickTabs.class)
		public static class Def extends ExAddOn.Def.Abstract<QuickWidget, Tab<?>> {
			private CompiledExpression theTabId;

			/**
			 * @param type The Qonfig type of this add-on
			 * @param element The tab widget
			 */
			public Def(QonfigAddOn type, ExElement.Def<? extends QuickWidget> element) {
				super(type, element);
			}

			@Override
			public Set<? extends Class<? extends ExAddOn.Def<?, ?>>> getDependencies() {
				return Collections.singleton((Class<ExAddOn.Def<?, ?>>) (Class<?>) ExModelAugmentation.Def.class);
			}

			/** @return The ID for the tab */
			@QonfigAttributeGetter("tab-id")
			public CompiledExpression getTabId() {
				return theTabId;
			}

			@Override
			public void update(ExpressoQIS session, ExElement.Def<? extends QuickWidget> element) throws QonfigInterpretationException {
				super.update(session, element);

				theTabId = element.getAttributeExpression("tab-id", session);
			}

			@Override
			public <E2 extends QuickWidget> Interpreted<?> interpret(ExElement.Interpreted<E2> element) {
				return new Interpreted<>(this, element);
			}
		}

		/**
		 * {@link Tab} interpretation
		 *
		 * @param <T> The type of the ID value of the tab
		 */
		public static class Interpreted<T> extends ExAddOn.Interpreted.Abstract<QuickWidget, Tab<T>> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<T>> theTabId;

			/**
			 * @param definition The definition to interpret
			 * @param element The tab widget
			 */
			protected Interpreted(Def definition, ExElement.Interpreted<? extends QuickWidget> element) {
				super(definition, element);
			}

			@Override
			public Def getDefinition() {
				return (Def) super.getDefinition();
			}

			/** @return The ID for the tab */
			public InterpretedValueSynth<SettableValue<?>, SettableValue<T>> getTabId() {
				return theTabId;
			}

			@Override
			public void update(ExElement.Interpreted<? extends QuickWidget> element) throws ExpressoInterpretationException {
				super.update(element);
				theTabId = getElement().interpret(getDefinition().getTabId(), ModelTypes.Value.anyAs());
			}

			@Override
			public Class<Tab<T>> getInstanceType() {
				return (Class<Tab<T>>) (Class<?>) Tab.class;
			}

			@Override
			public Tab<T> create(ExElement element) {
				return new Tab<>(element);
			}
		}

		private ModelValueInstantiator<SettableValue<T>> theTabIdInstantiator;
		private SettableValue<T> theTabId;

		/** @param element The tab widget */
		protected Tab(ExElement element) {
			super(element);
		}

		@Override
		public Class<Interpreted<T>> getInterpretationType() {
			return (Class<Interpreted<T>>) (Class<?>) Interpreted.class;
		}

		/** @return The ID for the tab */
		public SettableValue<T> getTabId() {
			return theTabId;
		}

		@Override
		public void update(ExAddOn.Interpreted<? super QuickWidget, ?> interpreted, ExElement element) throws ModelInstantiationException {
			super.update(interpreted, element);
			Interpreted<T> myInterpreted = (Interpreted<T>) interpreted;
			theTabIdInstantiator = myInterpreted.getTabId().instantiate();
		}

		@Override
		public void instantiated() throws ModelInstantiationException {
			super.instantiated();
			theTabIdInstantiator.instantiate();
		}

		@Override
		public ModelSetInstance instantiate(ModelSetInstance models) throws ModelInstantiationException {
			models = super.instantiate(models);

			theTabId = theTabIdInstantiator.get(models);
			return models;
		}

		@Override
		public Tab<T> copy(ExElement element) {
			Tab<T> copy = (Tab<T>) super.copy(element);

			return copy;
		}
	}

	/**
	 * Represents a set of tabs for each value in a collection
	 *
	 * @param <T> The type of values in the collection
	 */
	public static class TabSet<T> extends ExElement.Abstract implements TabSource<T> {
		/** The XML name of this element */
		public static final String TAB_SET = "tab-set";

		/** {@link TabSet} definition */
		@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
			qonfigType = "tab-set",
			interpretation = Interpreted.class,
			instance = QuickTabs.class)
		public static class Def extends ExElement.Def.Abstract<TabSet<?>> {
			private CompiledExpression theValues;
			private ModelComponentId theTabIdVariable;
			private QuickWidget.Def<?> theRenderer;

			/**
			 * @param parent The parent element of the tab set
			 * @param qonfigType The Qonfig type of the tab set
			 */
			public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
				super(parent, qonfigType);
			}

			/** @return The collection whose values to represent with tabs in a {@link QuickTabs} pane */
			@QonfigAttributeGetter("values")
			public CompiledExpression getValues() {
				return theValues;
			}

			/** @return The model ID of the variable by which the value to render will be available in expressions */
			public ModelComponentId getTabIdVariable() {
				return theTabIdVariable;
			}

			/** @return The widget to show as the content for each value in the collection */
			@QonfigChildGetter("renderer")
			public QuickWidget.Def<?> getRenderer() {
				return theRenderer;
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				super.doUpdate(session);

				theValues = getAttributeExpression("values", session);
				ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
				theTabIdVariable = elModels.getElementValueModelId("tabId");
				elModels.satisfyElementValueType(theTabIdVariable, ModelTypes.Value,
					(interp, env) -> ModelTypes.Value.forType(((Interpreted<?>) interp).getTabIdType()));

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
		 * {@link TabSet} interpretation
		 *
		 * @param <T> The type of values in the collection
		 */
		public static class Interpreted<T> extends ExElement.Interpreted.Abstract<TabSet<T>> {
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

			/** @return The collection whose values to represent with tabs in a {@link QuickTabs} pane */
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
			 * @throws ExpressoInterpretationException If this tab set could not be interpreted
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
			}

			/** @return The type of values in the collection */
			public TypeToken<T> getTabIdType() {
				return (TypeToken<T>) theValues.getType().getType(0);
			}

			/** @return The tab set */
			public TabSet<T> create() {
				return new TabSet<>(getIdentity());
			}
		}

		private ModelValueInstantiator<ObservableCollection<T>> theValuesInstantiator;
		private SettableValue<ObservableCollection<T>> theValues;
		private ModelComponentId theTabIdVariable;
		private ObservableCollection<TabSetTabInstance> theTabInstances;
		private QuickWidget theRenderer;
		private int theInstantiatedModel;

		/** @param id The element ID for this tab set */
		protected TabSet(Object id) {
			super(id);
			theValues = SettableValue.<ObservableCollection<T>> build().build();
			theTabInstances = getValues().flow()//
				.<TabSetTabInstance> transform(
					tx -> tx.cache(true).reEvalOnUpdate(false).fireIfUnchanged(true).build(this::createTabInstance))//
				.refreshEach(tab -> tab.isAvailable().noInitChanges())//
				.filter(tab -> Boolean.TRUE.equals(tab.isAvailable().get()) ? null : "Tab is not available")//
				.collectActive(isDestroyed().noInitChanges().take(1));
			Subscription sub = theTabInstances.subscribe(evt -> {
				switch (evt.getType()) {
				case add:
					evt.getNewValue().theElement = evt.getElementId();
					break;
				case remove:
					evt.getOldValue().remove();
					break;
				case set:
					if (evt.isUpdate())
						evt.getNewValue().update(evt);
					else
						evt.getNewValue().theElement = evt.getElementId();
					break;
				}
			}, true);
			isDestroyed().noInitChanges().take(1).act(__ -> sub.unsubscribe());
		}

		@Override
		public LocatedFilePosition getPosition() {
			return reporting().getPosition();
		}

		/** @return The collection whose values to represent with tabs in a {@link QuickTabs} pane */
		public ObservableCollection<T> getValues() {
			return ObservableCollection.flattenValue(theValues);
		}

		@Override
		public ObservableCollection<? extends TabInstance<T>> getTabInstances() {
			return theTabInstances;
		}

		/** @return The widget to show as the content for each value in the collection */
		@QonfigChildGetter("renderer")
		public QuickWidget getRenderer() {
			return theRenderer;
		}

		@Override
		protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
			super.doUpdate(interpreted);
			Interpreted<T> myInterpreted = (Interpreted<T>) interpreted;
			theValuesInstantiator = myInterpreted.getValues().instantiate();
			theTabIdVariable = myInterpreted.getDefinition().getTabIdVariable();
			if (theRenderer == null || theRenderer.getIdentity() != myInterpreted.getRenderer().getIdentity()) {
				if (theRenderer != null) {
					// TODO Gotta replace all the tab instance renderers
					theRenderer.destroy();
				}
				theRenderer = myInterpreted.getRenderer().create();
			}
			theRenderer.update(myInterpreted.getRenderer(), this);
			for (TabSetTabInstance tab : theTabInstances)
				tab.update(myInterpreted.getRenderer(), this);
			persistModels();
		}

		@Override
		public void instantiated() throws ModelInstantiationException {
			super.instantiated();

			theValuesInstantiator.instantiate();

			theRenderer.instantiated();
			for (TabSetTabInstance tab : theTabInstances)
				tab.getRenderer().instantiated();
		}

		@Override
		protected ModelSetInstance doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
			theInstantiatedModel++;
			myModels = super.doInstantiate(myModels);

			theValues.set(theValuesInstantiator.get(myModels), null);

			// No need to instantiate the renderer--it's just a template
			for (TabSetTabInstance tab : theTabInstances) {
				if (tab.theInstanceInstantiatedModel != theInstantiatedModel)
					tab.instantiate(myModels);
			}
			return myModels;
		}

		@Override
		public TabSet<T> copy(ExElement parent) {
			TabSet<T> copy = (TabSet<T>) super.copy(parent);

			copy.theValues = SettableValue.<ObservableCollection<T>> build().build();

			return copy;
		}

		TabSetTabInstance createTabInstance(T id, Transformation.TransformationValues<? extends T, ? extends TabSetTabInstance> txvs) {
			TabSetTabInstance result = txvs.getPreviousResult();
			if (result != null) {
				if (Objects.equals(id, result.getTabValue()))
					return result;
			}
			QuickWidget renderer = theRenderer.copy(this);
			result = new TabSetTabInstance(id, renderer);
			try {
				result.instantiate(getUpdatingModels());
			} catch (ModelInstantiationException e) {
				reporting().error("Could not instantiate renderer for new tab value " + id, e);
			}
			return result;
		}

		class TabSetTabInstance implements TabInstance<T> {
			ElementId theElement;
			private final T theTabId;
			private final QuickWidget theTabRenderer;
			private final AbstractTab theAbstractTab;
			int theInstanceInstantiatedModel;
			private final SimpleObservable<Object> theUpdate;

			TabSetTabInstance(T tabId, QuickWidget renderer) {
				theTabId = tabId;
				theTabRenderer = renderer;
				theAbstractTab = renderer.getAddOn(AbstractTab.class);
				theUpdate = new SimpleObservable<>();
			}

			void update(QuickWidget.Interpreted<?> renderer, TabSet<T> tabSet) throws ModelInstantiationException {
				theTabRenderer.update(renderer, tabSet);
			}

			void instantiate(ModelSetInstance models) throws ModelInstantiationException {
				ModelSetInstance copy = QuickCoreInterpretation
					.copyModels(models, theTabIdVariable, Observable.or(models.getUntil(), theTabRenderer.onDestroy())).build();
				ExFlexibleElementModelAddOn.satisfyElementValue(theTabIdVariable, copy,
					SettableValue.of(theTabId, "Tab ID is not modifiable").refresh(theUpdate));
				theTabRenderer.instantiate(copy);
				theInstanceInstantiatedModel = theInstantiatedModel;
			}

			void update(Object cause) {
				theUpdate.onNext(cause);
			}

			void remove() {
				theTabRenderer.destroy();
			}

			@Override
			public T getTabValue() {
				return theTabId;
			}

			@Override
			public QuickWidget getRenderer() {
				return theTabRenderer;
			}

			@Override
			public ObservableValue<String> getTabName() {
				return theAbstractTab.getTabName();
			}

			@Override
			public ObservableValue<Image> getTabIcon() {
				return theAbstractTab.getTabIcon();
			}

			@Override
			public SettableValue<Boolean> isAvailable() {
				return theAbstractTab.isTabAvailable();
			}

			@Override
			public String toString() {
				return theTabRenderer.toString() + "(" + theTabId + ")";
			}
		}
	}

	/** {@link QuickTabs} definition */
	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = "tabs",
		interpretation = Interpreted.class,
		instance = QuickTabs.class)
	public static class Def extends QuickContainer.Def.Abstract<QuickTabs<?>, QuickWidget> {
		private CompiledExpression theSelectedTab;
		private final List<TabSet.Def> theTabSets;
		private ModelComponentId theSelectedTabVariable;

		/**
		 * @param parent The parent element of the widget
		 * @param type The Qonfig type of the widget
		 */
		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
			theTabSets = new ArrayList<>();
		}

		/** @return The identity of the selected tab */
		@QonfigAttributeGetter("selected")
		public CompiledExpression getSelectedTab() {
			return theSelectedTab;
		}

		/** @return All the &lt;tab-set>s in the tab pane */
		@QonfigChildGetter("tab-set")
		public List<TabSet.Def> getTabSets() {
			return Collections.unmodifiableList(theTabSets);
		}

		/** @return The model ID of the variable by which the ID of the current tab will be available in expressions */
		public ModelComponentId getSelectedTabVariable() {
			return theSelectedTabVariable;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			theSelectedTab = getAttributeExpression("selected", session);
			ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
			theSelectedTabVariable = elModels.getElementValueModelId("selectedTab");
			elModels.satisfyElementValueType(theSelectedTabVariable, ModelTypes.Value.anyAsV());

			syncChildren(TabSet.Def.class, theTabSets, session.forChildren("tab-set"));
		}

		@Override
		public Interpreted<?> interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}
	}

	/**
	 * {@link QuickTabs} interpretation
	 *
	 * @param <T> The type of the ID values of the tabs
	 */
	public static class Interpreted<T> extends QuickContainer.Interpreted.Abstract<QuickTabs<T>, QuickWidget> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<T>> theSelectedTab;
		private final List<TabSet.Interpreted<? extends T>> theTabSets;

		/**
		 * @param definition The definition to interpret
		 * @param parent The parent element for the widget
		 */
		protected Interpreted(Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
			theTabSets = new ArrayList<>();
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		/** @return The identity of the selected tab */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<T>> getSelectedTab() {
			return theSelectedTab;
		}

		/** @return All the &lt;tab-set>s in the tab pane */
		public List<TabSet.Interpreted<? extends T>> getTabSets() {
			return Collections.unmodifiableList(theTabSets);
		}

		@Override
		protected void doUpdate() throws ExpressoInterpretationException {
			super.doUpdate();

			syncChildren(getDefinition().getTabSets(), theTabSets, def -> (TabSet.Interpreted<? extends T>) def.interpret(this),
				TabSet.Interpreted::updateTabSet);

			List<TypeToken<? extends T>> types = new ArrayList<>();
			theTabSets.stream().map(ts -> ts.getTabIdType()).forEach(types::add);
			getContents().stream().map(w -> (TypeToken<? extends T>) w.getAddOn(Tab.Interpreted.class).getTabId().getType().getType(0))
			.forEach(types::add);

			theSelectedTab = interpret(getDefinition().getSelectedTab(), ModelTypes.Value.anyAsV());
		}

		@Override
		public QuickTabs<T> create() {
			return new QuickTabs<>(getIdentity());
		}
	}

	private ModelValueInstantiator<SettableValue<T>> theSelectedTabInstantiator;
	private SettableValue<SettableValue<T>> theSelectedTab;
	private ObservableCollection<TabSet<? extends T>> theTabSets;
	private ObservableSortedCollection<TabSource<? extends T>> theTabSources;
	private ObservableCollection<TabInstance<? extends T>> theTabs;
	private ModelComponentId theSelectedTabVariable;

	/** @param id The element ID for this widget */
	protected QuickTabs(Object id) {
		super(id);
		createTabData();
	}

	private void createTabData() {
		theTabSets = ObservableCollection.<TabSet<? extends T>> build().build();
		theTabSources = ObservableCollection.flattenCollections(
			getContents().flow()//
			.<TabSource<? extends T>> transform(tx -> tx.cache(false).map(content -> new SingleTabSource<>(content)))//
			.collectPassive(), //
			theTabSets)//
			.sorted(TabSource::compareTo)//
			.collectActive(isDestroyed().noInitChanges().take(1));
		theTabs = theTabSources.flow()//
			.<TabInstance<? extends T>> flatMap(tabSource -> tabSource.getTabInstances().flow())//
			.collectActive(isDestroyed().noInitChanges().take(1));

		theSelectedTab = SettableValue.<SettableValue<T>> build().build();
	}

	/** @return All the &lt;tab-set>s in the tab pane */
	public ObservableCollection<TabSet<? extends T>> getTabSets() {
		return theTabSets;
	}

	/** @return The tabs in this tab pane */
	public ObservableCollection<TabInstance<? extends T>> getTabs() {
		return theTabs;
	}

	/** @return The identity of the selected tab */
	public SettableValue<T> getSelectedTab() {
		return SettableValue.flatten(theSelectedTab);
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		Interpreted<T> myInterpreted = (Interpreted<T>) interpreted;
		super.doUpdate(interpreted);
		theSelectedTabVariable = myInterpreted.getDefinition().getSelectedTabVariable();
		theSelectedTabInstantiator = myInterpreted.getSelectedTab() == null ? null : myInterpreted.getSelectedTab().instantiate();

		CollectionUtils.synchronize(theTabSets, myInterpreted.getTabSets(), (inst, interp) -> inst.getIdentity() == interp.getIdentity())//
		.<ModelInstantiationException> simpleX(interp -> interp.create())//
		.onLeft(el -> theTabSets.remove(el.getLeftValue()))//
		.onRightX(el -> el.getLeftValue().update(el.getRightValue(), this))//
		.onCommonX(el -> el.getLeftValue().update(el.getRightValue(), this))//
		.rightOrder()//
		.adjust();
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
		super.instantiated();

		if (theSelectedTabInstantiator != null)
			theSelectedTabInstantiator.instantiate();

		for (TabSet<? extends T> tabSet : theTabSets)
			tabSet.instantiated();
	}

	@Override
	protected ModelSetInstance doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		myModels = super.doInstantiate(myModels);

		ExFlexibleElementModelAddOn.satisfyElementValue(theSelectedTabVariable, myModels, theSelectedTab);
		if (theSelectedTabInstantiator == null)
			theSelectedTab.set(SettableValue.<T> build().build(), null);
		else
			theSelectedTab.set(theSelectedTabInstantiator.get(myModels), null);

		for (TabSet<? extends T> tabSet : theTabSets)
			tabSet.instantiate(myModels);
		return myModels;
	}

	@Override
	public QuickTabs<T> copy(ExElement parent) {
		QuickTabs<T> copy = (QuickTabs<T>) super.copy(parent);

		copy.theSelectedTab = SettableValue.<SettableValue<T>> build().build();
		copy.createTabData();

		for (TabSet<? extends T> tabSet : theTabSets)
			copy.theTabSets.add(tabSet.copy(this));

		return copy;
	}

	private interface TabSource<T> extends Comparable<TabSource<?>> {
		LocatedFilePosition getPosition();

		ObservableCollection<? extends TabInstance<T>> getTabInstances();

		@Override
		default int compareTo(TabSource<?> o) {
			return Integer.compare(getPosition().getPosition(), o.getPosition().getPosition());
		}
	}

	static class SingleTabSource<T> implements TabSource<T>, TabInstance<T> {
		private final QuickWidget theRenderer;
		private AbstractTab theAbstractTab;
		private Tab<T> theTab;

		public SingleTabSource(QuickWidget renderer) {
			theRenderer = renderer;
			theAbstractTab = renderer.getAddOn(AbstractTab.class);
			theTab = renderer.getAddOn(Tab.class);
		}

		@Override
		public LocatedFilePosition getPosition() {
			return theRenderer.reporting(theRenderer.getParentElement().reporting().getPosition().getFileLocation()).getPosition();
		}

		@Override
		public ObservableCollection<? extends TabInstance<T>> getTabInstances() {
			return ObservableCollection
				.flattenValue(theAbstractTab.isTabAvailable().map(v -> Boolean.TRUE.equals(v) ? ObservableCollection.of(this) : null));
		}

		@Override
		public T getTabValue() {
			return theTab.getTabId().get();
		}

		@Override
		public QuickWidget getRenderer() {
			return theRenderer;
		}

		@Override
		public ObservableValue<String> getTabName() {
			return ObservableValue.firstValue(v -> v != null, () -> getTabValue().toString(), theAbstractTab.getTabName());
		}

		@Override
		public ObservableValue<Image> getTabIcon() {
			return theAbstractTab.getTabIcon();
		}

		@Override
		public SettableValue<Boolean> isAvailable() {
			return theAbstractTab.isTabAvailable();
		}

		@Override
		public String toString() {
			return theAbstractTab.getElement().toString();
		}
	}

	/**
	 * Represents a tab in a {@link QuickTabs} pane from any source, either specified as an inline child widget, or represented by a value
	 * in a {@link TabSet}
	 *
	 * @param <T> The type of the ID value of the tab
	 */
	public static interface TabInstance<T> {
		/** @return The ID value of the tab */
		T getTabValue();

		/** @return The widget to render the tab's content */
		QuickWidget getRenderer();

		/** @return The name of the tab to represent in the tab */
		ObservableValue<String> getTabName();

		/** @return The icon to show in the tab */
		ObservableValue<Image> getTabIcon();

		/**
		 * @return Whether the tab is available for selection by the user. Its assignment to 'false' determines if it can be removed by
		 *         clicking an X in the tab
		 */
		SettableValue<Boolean> isAvailable();
	}
}
