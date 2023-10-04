package org.observe.quick.base;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import javax.swing.Icon;

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
import org.observe.quick.QuickWidget;
import org.observe.util.TypeTokens;
import org.qommons.collect.CollectionUtils;
import org.qommons.collect.ElementId;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.io.LocatedFilePosition;

import com.google.common.reflect.TypeToken;

public class QuickTabs<T> extends QuickContainer.Abstract<QuickWidget> {
	public static final String TABS = "tabs";

	public static class AbstractTab extends ExAddOn.Abstract<ExElement> {
		public static final String ABSTRACT_TAB = "abstract-tab";

		@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
			qonfigType = "abstract-tab",
			interpretation = Interpreted.class,
			instance = QuickTabs.class)
		public static class Def extends ExAddOn.Def.Abstract<ExElement, AbstractTab> {
			private CompiledExpression theTabName;
			private CompiledExpression theTabIcon;
			private CompiledExpression theOnSelect;
			private ModelComponentId isTabSelectedVariable;

			public Def(QonfigAddOn type, ExElement.Def<? extends ExElement> element) {
				super(type, element);
			}

			@QonfigAttributeGetter("tab-name")
			public CompiledExpression getTabName() {
				return theTabName;
			}

			@QonfigAttributeGetter("tab-icon")
			public CompiledExpression getTabIcon() {
				return theTabIcon;
			}

			@QonfigAttributeGetter("on-select")
			public CompiledExpression getOnSelect() {
				return theOnSelect;
			}

			public ModelComponentId getTabSelectedVariable() {
				return isTabSelectedVariable;
			}

			@Override
			public void update(ExpressoQIS session, ExElement.Def<? extends ExElement> element) throws QonfigInterpretationException {
				super.update(session, element);
				theTabName = session.getAttributeExpression("tab-name");
				theTabIcon = session.getAttributeExpression("tab-icon");
				theOnSelect = session.getAttributeExpression("on-select");
			}

			@Override
			public void postUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				super.postUpdate(session);
				isTabSelectedVariable = getElement().getAddOn(ExWithElementModel.Def.class).getElementValueModelId("tabSelected");
			}

			@Override
			public Interpreted interpret(ExElement.Interpreted<? extends ExElement> element) {
				return new Interpreted(this, element);
			}
		}

		public static class Interpreted extends ExAddOn.Interpreted.Abstract<ExElement, AbstractTab> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theTabName;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Icon>> theTabIcon;
			private InterpretedValueSynth<Observable<?>, Observable<?>> theSelectOn;
			private InterpretedValueSynth<ObservableAction, ObservableAction> theOnSelect;

			public Interpreted(Def definition, ExElement.Interpreted<? extends ExElement> element) {
				super(definition, element);
			}

			@Override
			public Def getDefinition() {
				return (Def) super.getDefinition();
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getTabName() {
				return theTabName;
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<Icon>> getTabIcon() {
				return theTabIcon;
			}

			public InterpretedValueSynth<Observable<?>, Observable<?>> getSelectOn() {
				return theSelectOn;
			}

			public InterpretedValueSynth<ObservableAction, ObservableAction> getOnSelect() {
				return theOnSelect;
			}

			@Override
			public void update(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.update(env);

				theTabName = getDefinition().getTabName() == null ? null
					: getDefinition().getTabName().interpret(ModelTypes.Value.STRING, env);
				theTabIcon = getDefinition().getTabIcon() == null ? null : QuickBaseInterpretation
					.evaluateIcon(getDefinition().getTabIcon(), env, getDefinition().getElement().getElement().getDocument().getLocation());
				theOnSelect = getDefinition().getOnSelect() == null ? null
					: getDefinition().getOnSelect().interpret(ModelTypes.Action.instance(), env);
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
		private ModelValueInstantiator<SettableValue<Icon>> theTabIconInstantiator;
		private ModelValueInstantiator<ObservableAction> theOnSelectInstantiator;
		private ModelComponentId isTabSelectedVariable;

		private SettableValue<SettableValue<String>> theTabName;
		private SettableValue<SettableValue<Icon>> theTabIcon;
		private ObservableAction theOnSelect;
		private SettableValue<Boolean> isTabSelected;

		public AbstractTab(ExElement element) {
			super(element);

			theTabName = SettableValue
				.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<String>> parameterized(String.class)).build();
			theTabIcon = SettableValue.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<Icon>> parameterized(Icon.class))
				.build();
			isTabSelected = SettableValue.build(boolean.class).withValue(false).build();
		}

		@Override
		public Class<Interpreted> getInterpretationType() {
			return Interpreted.class;
		}

		public SettableValue<String> getTabName() {
			return SettableValue.flatten(theTabName);
		}

		public SettableValue<Icon> getTabIcon() {
			return SettableValue.flatten(theTabIcon);
		}

		public void onSelect(Object cause) {
			if (theOnSelect != null)
				theOnSelect.act(cause);
		}

		public SettableValue<Boolean> isTabSelected() {
			return isTabSelected;
		}

		public ModelComponentId getTabSelectedVariable() {
			return isTabSelectedVariable;
		}

		@Override
		public void update(ExAddOn.Interpreted<?, ?> interpreted) {
			super.update(interpreted);
			Interpreted myInterpreted = (Interpreted) interpreted;
			theTabNameInstantiator = myInterpreted.getTabName() == null ? null : myInterpreted.getTabName().instantiate();
			theTabIconInstantiator = myInterpreted.getTabIcon() == null ? null : myInterpreted.getTabIcon().instantiate();
			theOnSelectInstantiator = myInterpreted.getOnSelect() == null ? null : myInterpreted.getOnSelect().instantiate();
			isTabSelectedVariable = myInterpreted.getDefinition().getTabSelectedVariable();
		}

		@Override
		public void instantiated() {
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

	public static class Tab<T> extends ExAddOn.Abstract<QuickWidget> {
		public static final String TAB = "tab";

		@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
			qonfigType = "tab",
			interpretation = Interpreted.class,
			instance = QuickTabs.class)
		public static class Def extends ExAddOn.Def.Abstract<QuickWidget, Tab<?>> {
			private CompiledExpression theTabId;

			public Def(QonfigAddOn type, ExElement.Def<? extends QuickWidget> element) {
				super(type, element);
			}

			@QonfigAttributeGetter("tab-id")
			public CompiledExpression getTabId() {
				return theTabId;
			}

			@Override
			public void update(ExpressoQIS session, ExElement.Def<? extends QuickWidget> element) throws QonfigInterpretationException {
				super.update(session, element);

				theTabId = session.getAttributeExpression("tab-id");
			}

			@Override
			public Interpreted<?> interpret(ExElement.Interpreted<? extends QuickWidget> element) {
				return new Interpreted<>(this, element);
			}
		}

		public static class Interpreted<T> extends ExAddOn.Interpreted.Abstract<QuickWidget, Tab<T>> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<T>> theTabId;

			public Interpreted(Def definition, ExElement.Interpreted<? extends QuickWidget> element) {
				super(definition, element);
			}

			@Override
			public Def getDefinition() {
				return (Def) super.getDefinition();
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<T>> getTabId() {
				return theTabId;
			}

			@Override
			public void update(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.update(env);
				theTabId = getDefinition().getTabId().interpret(ModelTypes.Value.anyAs(), env);
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

		public Tab(QuickWidget element) {
			super(element);
		}

		@Override
		public Class<Interpreted<T>> getInterpretationType() {
			return (Class<Interpreted<T>>) (Class<?>) Interpreted.class;
		}

		public SettableValue<T> getTabId() {
			return theTabId;
		}

		@Override
		public void update(ExAddOn.Interpreted<?, ?> interpreted) {
			super.update(interpreted);
			Interpreted<T> myInterpreted = (Interpreted<T>) interpreted;
			theTabIdInstantiator = myInterpreted.getTabId().instantiate();
		}

		@Override
		public void instantiated() {
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

	public static class TabSet<T> extends ExElement.Abstract implements TabSource<T> {
		public static final String TAB_SET = "tab-set";

		@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
			qonfigType = "tab-set",
			interpretation = Interpreted.class,
			instance = QuickTabs.class)
		public static class Def extends ExElement.Def.Abstract<TabSet<?>> {
			private CompiledExpression theValues;
			private ModelComponentId theTabIdVariable;
			private QuickWidget.Def<?> theRenderer;

			public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
				super(parent, qonfigType);
			}

			@QonfigAttributeGetter("values")
			public CompiledExpression getValues() {
				return theValues;
			}

			public ModelComponentId getTabIdVariable() {
				return theTabIdVariable;
			}

			public QuickWidget.Def<?> getRenderer() {
				return theRenderer;
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				super.doUpdate(session);

				theValues = session.getAttributeExpression("values");
				ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
				theTabIdVariable = elModels.getElementValueModelId("tabId");
				elModels.satisfyElementValueType(theTabIdVariable, ModelTypes.Value,
					(interp, env) -> ModelTypes.Value.forType(((Interpreted<?>) interp).getTabIdType()));

				theRenderer = ExElement.useOrReplace(QuickWidget.Def.class, theRenderer, session, "renderer");
			}

			public Interpreted<?> interpret(ExElement.Interpreted<?> parent) {
				return new Interpreted<>(this, parent);
			}
		}

		public static class Interpreted<T> extends ExElement.Interpreted.Abstract<TabSet<T>> {
			private InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<T>> theValues;
			private QuickWidget.Interpreted<?> theRenderer;

			public Interpreted(Def definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public Def getDefinition() {
				return (Def) super.getDefinition();
			}

			public InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<T>> getValues() {
				return theValues;
			}

			public QuickWidget.Interpreted<?> getRenderer() {
				return theRenderer;
			}

			public void updateTabSet(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				update(env);
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv expressoEnv) throws ExpressoInterpretationException {
				super.doUpdate(expressoEnv);
				theValues = getDefinition().getValues().interpret(ModelTypes.Collection.<T> anyAsV(), getExpressoEnv());
				if (theRenderer == null || theRenderer.getIdentity() != getDefinition().getRenderer().getIdentity()) {
					if (theRenderer != null)
						theRenderer.destroy();
					theRenderer = getDefinition().getRenderer().interpret(this);
				}
				theRenderer.updateElement(getExpressoEnv());
			}

			public TypeToken<T> getTabIdType() {
				return (TypeToken<T>) theValues.getType().getType(0);
			}

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

		public TabSet(Object id) {
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

		public ObservableCollection<T> getValues() {
			return ObservableCollection.flattenValue(theValues);
		}

		@Override
		public ObservableCollection<? extends TabInstance<T>> getTabInstances() {
			return theTabInstances;
		}

		public QuickWidget getRenderer() {
			return theRenderer;
		}

		@Override
		protected void doUpdate(ExElement.Interpreted<?> interpreted) {
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
		public void instantiated() {
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

			void update(QuickWidget.Interpreted<?> renderer, TabSet<T> tabSet) {
				theTabRenderer.update(renderer, tabSet);
			}

			void instantiate(ModelSetInstance models) throws ModelInstantiationException {
				ModelSetInstance copy = models.copy(Observable.or(models.getUntil(), theTabRenderer.isDestroyed().noInitChanges().take(1)))
					.build();
				ExFlexibleElementModelAddOn.satisfyElementValue(theTabIdVariable, copy,
					SettableValue.of(theTabIdType, theTabId, "Tab ID is not modifiable"),
					ExFlexibleElementModelAddOn.ActionIfSatisfied.Replace);
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
			public ObservableValue<Icon> getTabIcon() {
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

	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = "tabs",
		interpretation = Interpreted.class,
		instance = QuickTabs.class)
	public static class Def extends QuickContainer.Def.Abstract<QuickTabs<?>, QuickWidget> {
		private CompiledExpression theSelectedTab;
		private final List<TabSet.Def> theTabSets;
		private ModelComponentId theSelectedTabVariable;

		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
			theTabSets = new ArrayList<>();
		}

		@QonfigAttributeGetter("selected")
		public CompiledExpression getSelectedTab() {
			return theSelectedTab;
		}

		@QonfigChildGetter("tab-set")
		public List<TabSet.Def> getTabSets() {
			return Collections.unmodifiableList(theTabSets);
		}

		public ModelComponentId getSelectedTabVariable() {
			return theSelectedTabVariable;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			theSelectedTab = session.getAttributeExpression("selected");
			ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
			theSelectedTabVariable = elModels.getElementValueModelId("selectedTab");
			elModels.satisfyElementValueType(theSelectedTabVariable, ModelTypes.Value, //
				(interp, env) -> ModelTypes.Value.forType(((Interpreted<?>) interp).getTabIdType()));

			ExElement.syncDefs(TabSet.Def.class, theTabSets, session.forChildren("tab-set"));
		}

		@Override
		public Interpreted<?> interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}
	}

	public static class Interpreted<T> extends QuickContainer.Interpreted.Abstract<QuickTabs<T>, QuickWidget> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<T>> theSelectedTab;
		private final List<TabSet.Interpreted<? extends T>> theTabSets;
		private TypeToken<T> theTabIdType;

		Interpreted(Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
			theTabSets = new ArrayList<>();
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		public TypeToken<T> getTabIdType() {
			return theTabIdType;
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<T>> getSelectedTab() {
			return theSelectedTab;
		}

		public List<TabSet.Interpreted<? extends T>> getTabSets() {
			return Collections.unmodifiableList(theTabSets);
		}

		@Override
		protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			super.doUpdate(env);

			CollectionUtils.synchronize(theTabSets, getDefinition().getTabSets(), (i, d) -> i.getIdentity() == d.getIdentity())//
			.<ExpressoInterpretationException> simpleE(d -> (TabSet.Interpreted<? extends T>) d.interpret(this))//
			.onLeftX(el -> el.getLeftValue().destroy())//
			.onRightX(el -> el.getLeftValue().updateTabSet(getExpressoEnv()))//
			.onCommonX(el -> el.getLeftValue().updateTabSet(getExpressoEnv()))//
			.adjust();

			List<TypeToken<? extends T>> types = new ArrayList<>();
			theTabSets.stream().map(ts -> ts.getTabIdType()).forEach(types::add);
			getContents().stream().map(w -> (TypeToken<? extends T>) w.getAddOn(Tab.Interpreted.class).getTabId().getType().getType(0))
			.forEach(types::add);

			theTabIdType = TypeTokens.get().getCommonType(types);

			theSelectedTab = getDefinition().getSelectedTab() == null ? null
				: getDefinition().getSelectedTab().interpret(ModelTypes.Value.forType(theTabIdType), env);
		}

		@Override
		public TypeToken<? extends QuickTabs<T>> getWidgetType() throws ExpressoInterpretationException {
			return TypeTokens.get().keyFor(QuickTabs.class).<QuickTabs<T>> parameterized(theTabIdType);
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

	public QuickTabs(Object id) {
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

	public ObservableCollection<TabSet<? extends T>> getTabSets() {
		return theTabSets;
	}

	public ObservableCollection<TabInstance<? extends T>> getTabs() {
		return theTabs;
	}

	public SettableValue<T> getSelectedTab() {
		return SettableValue.flatten(theSelectedTab);
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) {
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
		.simple(interp -> interp.create())//
		.onLeft(el -> theTabSets.remove(el.getLeftValue()))//
		.onRight(el -> el.getLeftValue().update(el.getRightValue(), this))//
		.onCommon(el -> el.getLeftValue().update(el.getRightValue(), this))//
		.rightOrder()//
		.adjust();
	}

	@Override
	public void instantiated() {
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
		public ObservableValue<Icon> getTabIcon() {
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

	public static interface TabInstance<T> {
		T getTabValue();

		QuickWidget getRenderer();

		ObservableValue<String> getTabName();

		ObservableValue<Icon> getTabIcon();

		ObservableValue<Boolean> isRemovable();

		void onRemove();

		void onSelect();

		void deSelect();

		void setSelectListener(Runnable listener);
	}
}
