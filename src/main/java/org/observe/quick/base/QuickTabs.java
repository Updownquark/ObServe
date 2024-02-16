package org.observe.quick.base;

import java.awt.Image;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.observe.Observable;
import org.observe.ObservableAction;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.Subscription;
import org.observe.Transformation;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableSortedCollection;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
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
import org.observe.expresso.qonfig.ExWithElementModel;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.expresso.qonfig.QonfigChildGetter;
import org.observe.quick.QuickContainer;
import org.observe.quick.QuickCoreInterpretation;
import org.observe.quick.QuickWidget;
import org.observe.util.TypeTokens;
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
			private CompiledExpression theOnSelect;
			private ModelComponentId isTabSelectedVariable;

			/**
			 * @param type The Qonfig type of this add-on
			 * @param element The tab element
			 */
			public Def(QonfigAddOn type, ExElement.Def<? extends ExElement> element) {
				super(type, element);
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

			/** @return An action to execute when the tab becomes the active tab */
			@QonfigAttributeGetter("on-select")
			public CompiledExpression getOnSelect() {
				return theOnSelect;
			}

			/** @return The model ID of the variable by which the selected status of the current tab will be available to expressions */
			public ModelComponentId getTabSelectedVariable() {
				return isTabSelectedVariable;
			}

			@Override
			public void update(ExpressoQIS session, ExElement.Def<?> element) throws QonfigInterpretationException {
				super.update(session, element);
				theTabName = element.getAttributeExpression("tab-name", session);
				theTabIcon = element.getAttributeExpression("tab-icon", session);
				theOnSelect = element.getAttributeExpression("on-select", session);
			}

			@Override
			public void postUpdate(ExpressoQIS session, ExElement.Def<?> addOnElement) throws QonfigInterpretationException {
				super.postUpdate(session, addOnElement);
				isTabSelectedVariable = getElement().getAddOn(ExWithElementModel.Def.class).getElementValueModelId("tabSelected");
			}

			@Override
			public Interpreted interpret(ExElement.Interpreted<? extends ExElement> element) {
				return new Interpreted(this, element);
			}
		}

		/** {@link AbstractTab} interpretation */
		public static class Interpreted extends ExAddOn.Interpreted.Abstract<ExElement, AbstractTab> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theTabName;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Image>> theTabIcon;
			private InterpretedValueSynth<Observable<?>, Observable<?>> theSelectOn;
			private InterpretedValueSynth<ObservableAction, ObservableAction> theOnSelect;

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

			/** @return An event that will cause this tab to be become the active tab */
			public InterpretedValueSynth<Observable<?>, Observable<?>> getSelectOn() {
				return theSelectOn;
			}

			/** @return An action to execute when the tab becomes the active tab */
			public InterpretedValueSynth<ObservableAction, ObservableAction> getOnSelect() {
				return theOnSelect;
			}

			@Override
			public void update(ExElement.Interpreted<?> element) throws ExpressoInterpretationException {
				super.update(element);

				theTabName = getElement().interpret(getDefinition().getTabName(), ModelTypes.Value.STRING);
				theTabIcon = QuickCoreInterpretation.evaluateIcon(getDefinition().getTabIcon(), getElement(),
					getDefinition().getElement().getElement().getDocument().getLocation());
				theOnSelect = getElement().interpret(getDefinition().getOnSelect(), ModelTypes.Action.instance());
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
		private ModelValueInstantiator<ObservableAction> theOnSelectInstantiator;
		private ModelComponentId isTabSelectedVariable;

		private SettableValue<SettableValue<String>> theTabName;
		private SettableValue<SettableValue<Image>> theTabIcon;
		private ObservableAction theOnSelect;
		private SettableValue<Boolean> isTabSelected;

		/** @param element The tab element */
		protected AbstractTab(ExElement element) {
			super(element);

			theTabName = SettableValue
				.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<String>> parameterized(String.class)).build();
			theTabIcon = SettableValue.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<Image>> parameterized(Image.class))
				.build();
			isTabSelected = SettableValue.build(boolean.class).withValue(false).build();
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

		/**
		 * Called when the tab becomes the active tab
		 *
		 * @param cause The cause of the selection
		 */
		public void onSelect(Object cause) {
			if (theOnSelect != null)
				theOnSelect.act(cause);
		}

		/** @return Whether this tab is currently selected */
		public SettableValue<Boolean> isTabSelected() {
			return isTabSelected;
		}

		/** @return The model ID of the variable by which the selected status of the current tab will be available to expressions */
		public ModelComponentId getTabSelectedVariable() {
			return isTabSelectedVariable;
		}

		@Override
		public void update(ExAddOn.Interpreted<?, ?> interpreted, ExElement element) throws ModelInstantiationException {
			super.update(interpreted, element);
			Interpreted myInterpreted = (Interpreted) interpreted;
			theTabNameInstantiator = myInterpreted.getTabName() == null ? null : myInterpreted.getTabName().instantiate();
			theTabIconInstantiator = myInterpreted.getTabIcon() == null ? null : myInterpreted.getTabIcon().instantiate();
			theOnSelectInstantiator = myInterpreted.getOnSelect() == null ? null : myInterpreted.getOnSelect().instantiate();
			isTabSelectedVariable = myInterpreted.getDefinition().getTabSelectedVariable();
		}

		@Override
		public void instantiated() throws ModelInstantiationException {
			super.instantiated();
			if (theTabNameInstantiator != null)
				theTabNameInstantiator.instantiate();
			if (theTabIconInstantiator != null)
				theTabIconInstantiator.instantiate();
			if (theOnSelectInstantiator != null)
				theOnSelectInstantiator.instantiate();
		}

		@Override
		public void instantiate(ModelSetInstance models) throws ModelInstantiationException {
			super.instantiate(models);

			theTabName.set(theTabNameInstantiator == null ? null : theTabNameInstantiator.get(models), null);
			theTabIcon.set(theTabIconInstantiator == null ? null : theTabIconInstantiator.get(models), null);
			theOnSelect = theOnSelectInstantiator == null ? null : theOnSelectInstantiator.get(models);
			ExFlexibleElementModelAddOn.satisfyElementValue(isTabSelectedVariable, models, isTabSelected);
		}

		@Override
		public AbstractTab copy(ExElement element) {
			AbstractTab copy = (AbstractTab) super.copy(element);

			copy.theTabName = SettableValue.build(theTabName.getType()).build();
			copy.theTabIcon = SettableValue.build(theTabIcon.getType()).build();
			copy.isTabSelected = SettableValue.build(boolean.class).withValue(false).build();

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
			public Interpreted<?> interpret(ExElement.Interpreted<?> element) {
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
			protected Interpreted(Def definition, ExElement.Interpreted<?> element) {
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
			public Tab<T> create(QuickWidget element) {
				return new Tab<>(element);
			}
		}

		private ModelValueInstantiator<SettableValue<T>> theTabIdInstantiator;
		private SettableValue<T> theTabId;

		/** @param element The tab widget */
		protected Tab(QuickWidget element) {
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
		public void update(ExAddOn.Interpreted<? extends QuickWidget, ?> interpreted, QuickWidget element)
			throws ModelInstantiationException {
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
		public void instantiate(ModelSetInstance models) throws ModelInstantiationException {
			super.instantiate(models);

			theTabId = theTabIdInstantiator.get(models);
		}

		@Override
		public Tab<T> copy(QuickWidget element) {
			return (Tab<T>) super.copy(element);
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
			 * @param env The expresso environment to use to interpret expressions
			 * @throws ExpressoInterpretationException If this tab set could not be interpreted
			 */
			public void updateTabSet(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				update(env);
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv expressoEnv) throws ExpressoInterpretationException {
				super.doUpdate(expressoEnv);
				theValues = interpret(getDefinition().getValues(), ModelTypes.Collection.<T> anyAsV());
				theRenderer = syncChild(getDefinition().getRenderer(), theRenderer, def -> def.interpret(this),
					(r, rEnv) -> r.updateElement(rEnv));
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
		private TypeToken<T> theTabIdType;
		private QuickWidget theRenderer;
		private int theInstantiatedModel;

		/** @param id The element ID for this tab set */
		protected TabSet(Object id) {
			super(id);
			theValues = SettableValue.build((Class<ObservableCollection<T>>) (Class<?>) ObservableCollection.class).build();
			theTabInstances = getValues().flow()//
				.transform((Class<TabSetTabInstance>) (Class<?>) TabSetTabInstance.class, //
					tx -> tx.cache(true).reEvalOnUpdate(false).fireIfUnchanged(true).build(this::createTabInstance))//
				.collectActive(isDestroyed().noInitChanges().take(1));
			Subscription sub = theTabInstances.onChange(evt -> {
				switch (evt.getType()) {
				case add:
					evt.getNewValue().theElement = evt.getElementId();
					break;
				case remove:
					evt.getOldValue().remove();
					break;
				case set:
					break;
				}
			});
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
			theTabIdType = myInterpreted.getTabIdType();
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
		protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
			theInstantiatedModel++;
			super.doInstantiate(myModels);

			theValues.set(theValuesInstantiator.get(myModels), null);

			// No need to instantiate the renderer--it's just a template
			for (TabSetTabInstance tab : theTabInstances) {
				if (tab.theInstanceInstantiatedModel != theInstantiatedModel)
					tab.instantiate(myModels);
			}
		}

		@Override
		public TabSet<T> copy(ExElement parent) {
			TabSet<T> copy = (TabSet<T>) super.copy(parent);

			copy.theValues = SettableValue.build(theValues.getType()).build();

			return copy;
		}

		TabSetTabInstance createTabInstance(T id, Transformation.TransformationValues<? extends T, ? extends TabSetTabInstance> txvs) {
			TabSetTabInstance result = txvs.getPreviousResult();
			if (result != null) {
				if (Objects.equals(id, result.getTabValue()))
					return result;
				else
					result.onRemove();
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
			private Runnable theListener;

			TabSetTabInstance(T tabId, QuickWidget renderer) {
				theTabId = tabId;
				theTabRenderer = renderer;
				theAbstractTab = renderer.getAddOn(AbstractTab.class);
			}

			void update(QuickWidget.Interpreted<?> renderer, TabSet<T> tabSet) throws ModelInstantiationException {
				theTabRenderer.update(renderer, tabSet);
			}

			void instantiate(ModelSetInstance models) throws ModelInstantiationException {
				ModelSetInstance copy = getModels()
					.createCopy(models, Observable.or(models.getUntil(), theTabRenderer.isDestroyed().noInitChanges().take(1))).build();
				ExFlexibleElementModelAddOn.satisfyElementValue(theTabIdVariable, copy,
					SettableValue.of(theTabIdType, theTabId, "Tab ID is not modifiable"));
				theTabRenderer.instantiate(copy);
				theInstanceInstantiatedModel = theInstantiatedModel;
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
			public ObservableValue<Boolean> isRemovable() {
				return ObservableValue.of(TypeTokens.get().BOOLEAN, () -> theTabInstances.mutableElement(theElement).canRemove() == null,
					theTabInstances::getStamp, theTabInstances.changes());
			}

			@Override
			public void onRemove() {
				theTabInstances.mutableElement(theElement).remove();
			}

			@Override
			public void deSelect() {
				theAbstractTab.isTabSelected().set(true, null);
			}

			@Override
			public void setSelectListener(Runnable listener) {
				theListener = listener;
			}

			@Override
			public void onSelect() {
				if (theListener != null)
					theListener.run();
				theAbstractTab.onSelect(null);
				theAbstractTab.isTabSelected().set(true, null);
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
			elModels.satisfyElementValueType(theSelectedTabVariable, ModelTypes.Value, //
				(interp, env) -> ModelTypes.Value.forType(((Interpreted<?>) interp).getTabIdType()));

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
		private TypeToken<T> theTabIdType;

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

		/** @return The type of the ID values of the tabs */
		public TypeToken<T> getTabIdType() {
			return theTabIdType;
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
		protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			super.doUpdate(env);

			syncChildren(getDefinition().getTabSets(), theTabSets, def -> (TabSet.Interpreted<? extends T>) def.interpret(this),
				TabSet.Interpreted::updateTabSet);

			List<TypeToken<? extends T>> types = new ArrayList<>();
			theTabSets.stream().map(ts -> ts.getTabIdType()).forEach(types::add);
			getContents().stream().map(w -> (TypeToken<? extends T>) w.getAddOn(Tab.Interpreted.class).getTabId().getType().getType(0))
			.forEach(types::add);

			theTabIdType = TypeTokens.get().getCommonType(types);

			theSelectedTab = interpret(getDefinition().getSelectedTab(), ModelTypes.Value.forType(theTabIdType));
		}

		@Override
		public QuickTabs<T> create() {
			return new QuickTabs<>(getIdentity());
		}
	}

	private ModelValueInstantiator<SettableValue<T>> theSelectedTabInstantiator;
	private TypeToken<T> theTabIdType;
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
		theTabSets = ObservableCollection.build((Class<TabSet<? extends T>>) (Class<?>) TabSet.class).build();
		theTabSources = ObservableCollection.flattenCollections((Class<TabSource<? extends T>>) (Class<?>) TabSource.class, //
			getContents().flow()//
			.transform((Class<TabSource<? extends T>>) (Class<?>) TabSource.class,
				tx -> tx.cache(false).map(content -> new SingleTabSource<>(content)))//
			.collectPassive(), //
			theTabSets)//
			.sorted(TabSource::compareTo)//
			.collectActive(isDestroyed().noInitChanges().take(1));
		theTabs = theTabSources.flow()//
			.flatMap((Class<TabInstance<? extends T>>) (Class<?>) TabInstance.class, tabSource -> tabSource.getTabInstances().flow())//
			.collectActive(isDestroyed().noInitChanges().take(1));

		theTabs.changes().takeUntil(isDestroyed().noInitChanges().take(1)).act(evt -> {
			for (TabInstance<? extends T> tab : evt.getValues())
				tab.setSelectListener(() -> onSelect(tab));
		});
	}

	private void onSelect(TabInstance<? extends T> selected) {
		for (TabInstance<? extends T> tab : theTabs) {
			if (tab != selected)
				tab.deSelect();
		}
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
		// Do this before the super call because the content will need it
		if (theSelectedTab == null || !theTabIdType.equals(myInterpreted.getTabIdType())) {
			theTabIdType = myInterpreted.getTabIdType();
			theSelectedTab = SettableValue
				.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<T>> parameterized(theTabIdType)).build();
		}
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
	protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		super.doInstantiate(myModels);

		ExFlexibleElementModelAddOn.satisfyElementValue(theSelectedTabVariable, myModels, theSelectedTab);
		if (theSelectedTabInstantiator == null)
			theSelectedTab.set(SettableValue.build(theTabIdType).build(), null);
		else
			theSelectedTab.set(theSelectedTabInstantiator.get(myModels), null);

		for (TabSet<? extends T> tabSet : theTabSets)
			tabSet.instantiate(myModels);
	}

	@Override
	public QuickTabs<T> copy(ExElement parent) {
		QuickTabs<T> copy = (QuickTabs<T>) super.copy(parent);

		copy.theSelectedTab = SettableValue.build(theSelectedTab.getType()).build();
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
		private Runnable theListener;

		public SingleTabSource(QuickWidget renderer) {
			theRenderer = renderer;
			theAbstractTab = renderer.getAddOn(AbstractTab.class);
			theTab = renderer.getAddOn(Tab.class);
		}

		@Override
		public LocatedFilePosition getPosition() {
			return theRenderer.reporting().getPosition();
		}

		@Override
		public ObservableCollection<? extends TabInstance<T>> getTabInstances() {
			return ObservableCollection.of((Class<TabInstance<T>>) (Class<?>) TabInstance.class, this);
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
			return ObservableValue.firstValue(TypeTokens.get().STRING, v -> v != null, () -> getTabValue().toString(),
				theAbstractTab.getTabName());
		}

		@Override
		public ObservableValue<Image> getTabIcon() {
			return theAbstractTab.getTabIcon();
		}

		@Override
		public ObservableValue<Boolean> isRemovable() {
			return theRenderer.isVisible().assignmentTo(ObservableValue.of(boolean.class, false)).isEnabled().map(e -> e == null);
		}

		@Override
		public void onRemove() {
			theRenderer.isVisible().set(false, null);
		}

		@Override
		public void deSelect() {
			theAbstractTab.isTabSelected().set(false, null);
		}

		@Override
		public void setSelectListener(Runnable listener) {
			theListener = listener;
		}

		@Override
		public void onSelect() {
			if (theListener != null)
				theListener.run();
			theAbstractTab.onSelect(null);
			theAbstractTab.isTabSelected().set(true, null);
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

		/** @return Whether the tab can be removed by clicking an X in the tab */
		ObservableValue<Boolean> isRemovable();

		/**
		 * Called when the tab is removed
		 *
		 * @see #isRemovable()
		 */
		void onRemove();

		/** Called when the tab becomes the active tab */
		void onSelect();

		/** Called when the tab was the active tab but is no longer */
		void deSelect();

		/** @param listener A listener to be {@link Runnable#run() run} when the tab is selected */
		void setSelectListener(Runnable listener);
	}
}
