package org.observe.quick.base;

import java.awt.Image;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.swing.text.TabSet;

import org.observe.ObservableValue;
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
import org.observe.expresso.qonfig.ExAddOn;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExFlexibleElementModelAddOn;
import org.observe.expresso.qonfig.ExModelAugmentation;
import org.observe.expresso.qonfig.ExWithElementModel;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.quick.QuickCoreInterpretation;
import org.observe.quick.QuickWidget;
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
public class QuickTabs<T> extends QuickVariableContainer {
	/** The XML name of this element */
	public static final String TABS = "tabs";

	/**
	 * An add-on inherited by tabs in a {@link QuickTabs} pane
	 *
	 * @param <T> The type of the tab's ID
	 */
	public static class Tab<T> extends ExAddOn.Abstract<ExElement> {
		/** The XML name of this add-on */
		public static final String TAB = "tab";

		/** {@link Tab} definition */
		@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
			qonfigType = TAB,
			interpretation = Interpreted.class,
			instance = Tab.class)
		public static class Def extends ExAddOn.Def.Abstract<ExElement, Tab<?>> {
			private ModelComponentId theTabIdVariable;
			private CompiledExpression theTabId;
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

			/** @return The variable by which the tab ID is available */
			public ModelComponentId getTabIdVariable() {
				return theTabIdVariable;
			}

			/** @return The ID for the tab */
			@QonfigAttributeGetter("tab-id")
			public CompiledExpression getTabId() {
				return theTabId;
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

				theTabId = element.getAttributeExpression("tab-id", session);
				theTabName = element.getAttributeExpression("tab-name", session);
				theTabIcon = element.getAttributeExpression("tab-icon", session);
				isTabAvailable = element.getAttributeExpression("tab-available", session);
				ExWithElementModel.Def elModels = element.getAddOn(ExWithElementModel.Def.class);
				theTabIdVariable = elModels.getElementValueModelId("tabId");
				elModels.<ExElement.Interpreted<?>, SettableValue<?>> satisfyElementSingleValueType(theTabIdVariable, ModelTypes.Value,
					interpreted -> interpreted.getAddOn(Interpreted.class).getIdType());
			}

			@Override
			public <E2 extends ExElement> Interpreted<?> interpret(ExElement.Interpreted<E2> element) {
				return new Interpreted<>(this, element);
			}
		}

		/**
		 * {@link Tab} interpretation
		 *
		 * @param <T> The type of the ID value of the tab
		 */
		public static class Interpreted<T> extends ExAddOn.Interpreted.Abstract<ExElement, Tab<T>> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<T>> theTabId;
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

			/** @return The ID for the tab */
			public InterpretedValueSynth<SettableValue<?>, SettableValue<T>> getTabId() {
				return theTabId;
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

				theTabId = getElement().interpret(getDefinition().getTabId(), ModelTypes.Value.anyAs());
				theTabName = getElement().interpret(getDefinition().getTabName(), ModelTypes.Value.STRING);
				theTabIcon = QuickCoreInterpretation.evaluateIcon(getDefinition().getTabIcon(), getElement(),
					getDefinition().getElement().getElement().getDocument().getLocation());
				isTabAvailable = getElement().interpret(getDefinition().isTabAvailable(), ModelTypes.Value.BOOLEAN);
			}

			/**
			 * @return The type of tab ID
			 * @throws ExpressoInterpretationException If the tab ID could not be interpreted
			 */
			public TypeToken<T> getIdType() throws ExpressoInterpretationException {
				if (theTabId == null)
					theTabId = getElement().interpret(getDefinition().getTabId(), ModelTypes.Value.anyAs());
				return (TypeToken<T>) theTabId.getType().getType(0);
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
		private ModelValueInstantiator<SettableValue<String>> theTabNameInstantiator;
		private ModelValueInstantiator<SettableValue<Image>> theTabIconInstantiator;
		private ModelValueInstantiator<SettableValue<Boolean>> theAvailableInstantiator;
		private ModelComponentId theTabIdVariable;

		private SettableValue<T> theTabId;
		private SettableValue<SettableValue<String>> theTabName;
		private SettableValue<SettableValue<Image>> theTabIcon;
		private SettableValue<SettableValue<Boolean>> isTabAvailable;

		/** @param element The tab element */
		protected Tab(ExElement element) {
			super(element);

			theTabName = SettableValue.create();
			theTabIcon = SettableValue.create();
			isTabAvailable = SettableValue.create();
		}

		@Override
		public Class<Interpreted<?>> getInterpretationType() {
			return (Class<Interpreted<?>>) (Class<?>) Interpreted.class;
		}

		/** @return The ID for the tab */
		public SettableValue<T> getTabId() {
			return theTabId;
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
			Interpreted<T> myInterpreted = (Interpreted<T>) interpreted;
			theTabIdInstantiator = myInterpreted.getTabId().instantiate();
			theTabNameInstantiator = myInterpreted.getTabName() == null ? null : myInterpreted.getTabName().instantiate();
			theTabIconInstantiator = myInterpreted.getTabIcon() == null ? null : myInterpreted.getTabIcon().instantiate();
			theAvailableInstantiator = myInterpreted.isTabAvailable().instantiate();
			theTabIdVariable = myInterpreted.getDefinition().getTabIdVariable();
		}

		@Override
		public void instantiated() throws ModelInstantiationException {
			super.instantiated();
			theTabIdInstantiator.instantiate();
			if (theTabNameInstantiator != null)
				theTabNameInstantiator.instantiate();
			if (theTabIconInstantiator != null)
				theTabIconInstantiator.instantiate();
			theAvailableInstantiator.instantiate();
		}

		@Override
		public ModelSetInstance instantiate(ModelSetInstance models) throws ModelInstantiationException {
			models = super.instantiate(models);

			theTabId = theTabIdInstantiator.get(models);
			theTabName.set(theTabNameInstantiator == null ? null : theTabNameInstantiator.get(models), null);
			theTabIcon.set(theTabIconInstantiator == null ? null : theTabIconInstantiator.get(models), null);
			isTabAvailable.set(theAvailableInstantiator.get(models), null);
			ExFlexibleElementModelAddOn.satisfyElementValue(theTabIdVariable, models, theTabId);
			return models;
		}

		@Override
		public Tab<T> copy(ExElement element) {
			Tab<T> copy = (Tab<T>) super.copy(element);

			copy.theTabName = SettableValue.create();
			copy.theTabIcon = SettableValue.create();
			copy.isTabAvailable = SettableValue.create();

			return copy;
		}
	}

	/** {@link QuickTabs} definition */
	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = "tabs",
		interpretation = Interpreted.class,
		instance = QuickTabs.class)
	public static class Def extends QuickVariableContainer.Def<QuickTabs<?>> {
		private CompiledExpression theSelectedTab;
		private ModelComponentId theSelectedTabVariable;

		/**
		 * @param parent The parent element of the widget
		 * @param type The Qonfig type of the widget
		 */
		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		/** @return The identity of the selected tab */
		@QonfigAttributeGetter("selected")
		public CompiledExpression getSelectedTab() {
			return theSelectedTab;
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
	public static class Interpreted<T> extends QuickVariableContainer.Interpreted<QuickTabs<T>> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<T>> theSelectedTab;

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

		/** @return The identity of the selected tab */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<T>> getSelectedTab() {
			return theSelectedTab;
		}

		@Override
		protected void doUpdate() throws ExpressoInterpretationException {
			super.doUpdate();

			List<TypeToken<? extends T>> types = new ArrayList<>();
			getWidgetSets().stream().map(ts -> ts.getAddOn(Tab.Interpreted.class).getTabId().getType().getType(0)).forEach(types::add);
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
	private ObservableCollection<TabInstance<? extends T>> theTabs;
	private ModelComponentId theSelectedTabVariable;

	/** @param id The element ID for this widget */
	protected QuickTabs(Object id) {
		super(id);
		createTabData();
	}

	private void createTabData() {
		theTabs = getAllContent().flow()//
			.<TabInstance<? extends T>> map(SingleTabSource::new)//
			.collectActive(isDestroyed().noInitChanges().take(1));

		theSelectedTab = SettableValue.<SettableValue<T>> build().build();
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
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
		super.instantiated();

		if (theSelectedTabInstantiator != null)
			theSelectedTabInstantiator.instantiate();
	}

	@Override
	protected ModelSetInstance doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		myModels = super.doInstantiate(myModels);

		ExFlexibleElementModelAddOn.satisfyElementValue(theSelectedTabVariable, myModels, theSelectedTab);
		if (theSelectedTabInstantiator == null)
			theSelectedTab.set(SettableValue.<T> build().build(), null);
		else
			theSelectedTab.set(theSelectedTabInstantiator.get(myModels), null);

		return myModels;
	}

	@Override
	public QuickTabs<T> copy(ExElement parent) {
		QuickTabs<T> copy = (QuickTabs<T>) super.copy(parent);

		copy.theSelectedTab = SettableValue.<SettableValue<T>> build().build();
		copy.createTabData();

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
		private Tab<T> theTab;

		public SingleTabSource(QuickWidget renderer) {
			theRenderer = renderer;
			theTab = renderer.getAddOn(Tab.class);
		}

		@Override
		public LocatedFilePosition getPosition() {
			return theRenderer.reporting(theRenderer.getParentElement().reporting().getPosition().getFileLocation()).getPosition();
		}

		@Override
		public ObservableCollection<? extends TabInstance<T>> getTabInstances() {
			return ObservableCollection
				.flattenValue(theTab.isTabAvailable().map(v -> Boolean.TRUE.equals(v) ? ObservableCollection.of(this) : null));
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
			return ObservableValue.firstValue(v -> v != null, () -> getTabValue().toString(), theTab.getTabName());
		}

		@Override
		public ObservableValue<Image> getTabIcon() {
			return theTab.getTabIcon();
		}

		@Override
		public SettableValue<Boolean> isAvailable() {
			return theTab.isTabAvailable();
		}

		@Override
		public String toString() {
			return theTab.getElement().toString();
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
