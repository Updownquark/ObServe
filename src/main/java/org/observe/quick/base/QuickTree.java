package org.observe.quick.base;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.observe.Observable;
import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
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
import org.observe.util.TypeTokens;
import org.qommons.QommonsUtils;
import org.qommons.collect.BetterList;
import org.qommons.collect.CollectionUtils;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

/**
 * A widget displaying a hierarchical set of values
 *
 * @param <N> The type of each node in the tree
 */
public class QuickTree<N> extends QuickWidget.Abstract implements MultiValueWidget<BetterList<N>> {
	/** The XML name of this element */
	public static final String TREE = "tree";

	/**
	 * {@link QuickTree} definition
	 *
	 * @param <T> The sub-type of tree to create
	 */
	@ExMultiElementTraceable({ //
		@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
			qonfigType = TREE,
			interpretation = Interpreted.class,
			instance = QuickTree.class),
		@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = MULTI_VALUE_WIDGET,
		interpretation = Interpreted.class,
		instance = QuickTree.class),
		@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = MULTI_VALUE_RENDERABLE,
		interpretation = Interpreted.class,
		instance = QuickTree.class) })
	public static class Def<T extends QuickTree<?>> extends QuickWidget.Def.Abstract<T> implements MultiValueWidget.Def<T> {
		private ModelComponentId theActiveValueVariable;
		private ModelComponentId theNodeVariable;
		private ModelComponentId theSelectedVariable;
		private TreeModel.Def<?> theModel;
		private QuickTableColumn.SingleColumnSet.Def theTreeColumn;
		private CompiledExpression thePathSelection;
		private CompiledExpression thePathMultiSelection;
		private CompiledExpression theNodeSelection;
		private CompiledExpression theNodeMultiSelection;
		private final List<ExElement.Def<?>> theActionsAndOptions;
		private boolean isRootVisible;
		private final List<QuickDragging.TransferSource.Def> theTransferSources;
		private final List<QuickDragging.TransferAccept.Def> theTransferAccepters;

		/**
		 * @param parent The parent element of the widget
		 * @param type The Qonfig type of the widget
		 */
		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
			theActionsAndOptions = new ArrayList<>();
			theTransferSources = new ArrayList<>();
			theTransferAccepters = new ArrayList<>();
		}

		/** @return The data model for the tree */
		@QonfigChildGetter(asType = TREE, value = "tree-model")
		public TreeModel.Def<?> getModel() {
			return theModel;
		}

		/** @return The tree column to render and handle interactions with values in the tree */
		@QonfigChildGetter(asType = TREE, value = "tree-column")
		public QuickTableColumn.SingleColumnSet.Def getTreeColumn() {
			return theTreeColumn;
		}

		@QonfigAttributeGetter(asType = MULTI_VALUE_RENDERABLE, value = "active-value-name")
		@Override
		public ModelComponentId getActiveValueVariable() {
			return theActiveValueVariable;
		}

		/** @return The model ID of the variable containing the value of the active tree node (e.g. the one being rendered) */
		@QonfigAttributeGetter(asType = TREE, value = "active-node-name")
		public ModelComponentId getNodeVariable() {
			return theNodeVariable;
		}

		/** @return The model ID of the variable containing the selected state of the active tree node */
		public ModelComponentId getSelectedVariable() {
			return theSelectedVariable;
		}

		@Override
		public CompiledExpression getSelection() {
			return thePathSelection;
		}

		@Override
		public CompiledExpression getMultiSelection() {
			return thePathMultiSelection;
		}

		/** @return The value of the selected node */
		@QonfigAttributeGetter(asType = TREE, value = "node-selection")
		public CompiledExpression getNodeSelection() {
			return theNodeSelection;
		}

		/** @return The values of the selected nodes */
		@QonfigAttributeGetter(asType = TREE, value = "node-multi-selection")
		public CompiledExpression getNodeMultiSelection() {
			return theNodeMultiSelection;
		}

		/**
		 * @return The list containing the {@link #getActions() actions} and {@link #getOptions() table options} for this table, in order of
		 *         their specification in the file
		 */
		public List<ExElement.Def<?>> getActionsAndOptions() {
			return Collections.unmodifiableList(theActionsAndOptions);
		}

		/** @return Actions that can be executed against nodes in the tree */
		@QonfigChildGetter(asType = TREE, value = "action")
		public List<ValueAction.Def<?>> getActions() {
			return QommonsUtils.filterMap(theActionsAndOptions, aao -> aao instanceof ValueAction.Def, aao -> (ValueAction.Def<?>) aao);
		}

		/** @return Widget options to place in a bar above or below the table along with button actions */
		@QonfigChildGetter(asType = TREE, value = "option")
		public List<QuickWidget.Def<?>> getOptions() {
			return QommonsUtils.filterMap(theActionsAndOptions, aao -> aao instanceof QuickWidget.Def, aao -> (QuickWidget.Def<?>) aao);
		}

		/** @return Whether the root node should be visible to the user */
		@QonfigAttributeGetter(asType = TREE, value = "root-visible")
		public boolean isRootVisible() {
			return isRootVisible;
		}

		@Override
		public List<QuickDragging.TransferSource.Def> getTransferSources() {
			return Collections.unmodifiableList(theTransferSources);
		}

		@Override
		public List<QuickDragging.TransferAccept.Def> getTransferAccepters() {
			return Collections.unmodifiableList(theTransferAccepters);
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
			String valueName = session.getAttributeText("active-value-name");
			theActiveValueVariable = elModels.getElementValueModelId(valueName);
			String nodeName = session.getAttributeText("active-node-name");
			theNodeVariable = elModels.getElementValueModelId(nodeName);
			theModel = syncChild(TreeModel.Def.class, theModel, session, TreeModel.TREE_MODEL,
				(m, mEnv) -> m.update(mEnv));
			theSelectedVariable = elModels.getElementValueModelId("selected");
			theTreeColumn = syncChild(QuickTableColumn.SingleColumnSet.Def.class, theTreeColumn, session, "tree-column");
			thePathSelection = getAttributeExpression("selection", session);
			thePathMultiSelection = getAttributeExpression("multi-selection", session);
			theNodeSelection = getAttributeExpression("node-selection", session);
			theNodeMultiSelection = getAttributeExpression("node-multi-selection", session);
			elModels.satisfyElementValueType(theActiveValueVariable, ModelTypes.Value,
				(interp, env) -> ModelTypes.Value.forType(((Interpreted<?, ?>) interp).getPathType()));
			isRootVisible = session.getAttribute("root-visible", boolean.class);

			syncChildren(ExElement.Def.class, theActionsAndOptions, session.forChildren("action", "option"));
			syncChildren(QuickDragging.TransferSource.Def.class, theTransferSources, session.forChildren("transfer-source"));
			syncChildren(QuickDragging.TransferAccept.Def.class, theTransferAccepters, session.forChildren("transfer-accept"));
		}

		@Override
		public Interpreted<?, ? extends T> interpret(ExElement.Interpreted<?> parent) {
			return (Interpreted<?, ? extends T>) new Interpreted<>((Def<QuickTree<Object>>) this, parent);
		}
	}

	/**
	 * {@link QuickTree} interpretation
	 *
	 * @param <N> The type of each node in the tree
	 * @param <T> The sub-type of tree to create
	 */
	public static class Interpreted<N, T extends QuickTree<N>> extends QuickWidget.Interpreted.Abstract<T>
	implements MultiValueWidget.Interpreted<BetterList<N>, T> {
		private TreeModel.Interpreted<N, ?> theModel;
		private TypeToken<N> theNodeType;
		private QuickTableColumn.SingleColumnSet.Interpreted<BetterList<N>, N> theTreeColumn;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<BetterList<N>>> thePathSelection;
		private InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<BetterList<N>>> thePathMultiSelection;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<N>> theNodeSelection;
		private InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<N>> theNodeMultiSelection;
		private final List<ExElement.Interpreted<?>> theActionsAndOptions;
		private final List<QuickDragging.TransferSource.Interpreted<BetterList<N>, ?>> theTransferSources;
		private final List<QuickDragging.TransferAccept.Interpreted<BetterList<N>, ?>> theTransferAccepters;

		/**
		 * @param definition The definition to interpret
		 * @param parent The parent element for the widget
		 */
		protected Interpreted(Def<? super T> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
			persistModelInstances(true);
			theActionsAndOptions = new ArrayList<>();
			theTransferSources = new ArrayList<>();
			theTransferAccepters = new ArrayList<>();
		}

		@Override
		public Def<? super T> getDefinition() {
			return (Def<? super T>) super.getDefinition();
		}

		/** @return The data model for the tree */
		public TreeModel.Interpreted<N, ?> getModel() {
			return theModel;
		}

		@Override
		public InterpretedValueSynth<SettableValue<?>, SettableValue<BetterList<N>>> getSelection() {
			return thePathSelection;
		}

		@Override
		public InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<BetterList<N>>> getMultiSelection() {
			return thePathMultiSelection;
		}

		/** @return The value of the selected node */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<N>> getNodeSelection() {
			return theNodeSelection;
		}

		/** @return The values of the selected nodes */
		public InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<N>> getNodeMultiSelection() {
			return theNodeMultiSelection;
		}

		@Override
		public TypeToken<BetterList<N>> getValueType() throws ExpressoInterpretationException {
			return TypeTokens.get().keyFor(BetterList.class).<BetterList<N>> parameterized(getNodeType());
		}

		/**
		 * @return The type of each node in the tree
		 * @throws ExpressoInterpretationException If the node type could not be interpreted
		 */
		public TypeToken<N> getNodeType() throws ExpressoInterpretationException {
			if (theNodeType == null)
				theNodeType = (TypeToken<N>) theModel.getNodeType(getExpressoEnv());
			return theNodeType;
		}

		/**
		 * @return The type of node paths in the tree
		 * @throws ExpressoInterpretationException If the node type could not be interpreted
		 */
		public TypeToken<BetterList<N>> getPathType() throws ExpressoInterpretationException {
			return TypeTokens.get().keyFor(BetterList.class).<BetterList<N>> parameterized(getNodeType());
		}

		/** @return The tree column to render and handle interactions with values in the tree */
		public QuickTableColumn.SingleColumnSet.Interpreted<BetterList<N>, N> getTreeColumn() {
			return theTreeColumn;
		}

		/**
		 * @return The list containing the {@link #getActions() actions} and {@link #getOptions() table options} for this table, in order of
		 *         their specification in the file
		 */
		public List<ExElement.Interpreted<?>> getActionsAndOptions() {
			return Collections.unmodifiableList(theActionsAndOptions);
		}

		/** @return Actions that can be executed against nodes in the tree */
		public List<ValueAction.Interpreted<BetterList<N>, ?>> getActions() {
			return QommonsUtils.filterMap(theActionsAndOptions, aao -> aao instanceof ValueAction.Interpreted,
				aao -> (ValueAction.Interpreted<BetterList<N>, ?>) aao);
		}

		/** @return Widget options to place in a bar above or below the table along with button actions */
		public List<QuickWidget.Interpreted<?>> getOptions() {
			return QommonsUtils.filterMap(theActionsAndOptions, aao -> aao instanceof QuickWidget.Interpreted,
				aao -> (QuickWidget.Interpreted<?>) aao);
		}

		@Override
		public List<QuickDragging.TransferSource.Interpreted<BetterList<N>, ?>> getTransferSources() {
			return Collections.unmodifiableList(theTransferSources);
		}

		@Override
		public List<QuickDragging.TransferAccept.Interpreted<BetterList<N>, ?>> getTransferAccepters() {
			return Collections.unmodifiableList(theTransferAccepters);
		}

		@Override
		protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			if (theModel != null && theModel.getIdentity() != getDefinition().getModel().getIdentity()) {
				theModel.destroy();
				theModel = null;
			}
			if (theModel == null)
				theModel = getDefinition().getModel().interpret(this);

			super.doUpdate(env);

			getNodeType(); // Initialize root
			// Even though we already instantiated the model above, we need this call to delegate to the appropriate environment
			theModel = syncChild(getDefinition().getModel(), theModel, def -> def.interpret(this), (m, mEnv) -> m.updateModel(env));
			theTreeColumn = syncChild(getDefinition().getTreeColumn(), theTreeColumn,
				def -> (QuickTableColumn.SingleColumnSet.Interpreted<BetterList<N>, N>) def.<BetterList<N>> interpret(this),
				(c, cEnv) -> c.updateColumns(cEnv));
			TypeToken<N> nodeType = getNodeType();
			TypeToken<BetterList<N>> pathType = TypeTokens.get().keyFor(BetterList.class).<BetterList<N>> parameterized(nodeType);
			thePathSelection = interpret(getDefinition().getSelection(), ModelTypes.Value.forType(pathType));
			thePathMultiSelection = interpret(getDefinition().getMultiSelection(), ModelTypes.Collection.forType(pathType));
			theNodeSelection = interpret(getDefinition().getNodeSelection(), ModelTypes.Value.forType(nodeType));
			theNodeMultiSelection = interpret(getDefinition().getNodeMultiSelection(), ModelTypes.Collection.forType(nodeType));

			syncChildren(getDefinition().getActionsAndOptions(), theActionsAndOptions, def -> {
				if (def instanceof ValueAction.Def)
					return (ValueAction.Interpreted<BetterList<N>, ?>) ((ValueAction.Def<?>) def).interpret(this, getValueType());
				else if (def instanceof QuickWidget.Def)
					return ((QuickWidget.Def<?>) def).interpret(this);
				else
					throw new IllegalStateException("Whats this? " + def.getClass().getName());
			}, (interp, env2) -> {
				if (interp instanceof ValueAction.Interpreted)
					((ValueAction.Interpreted<BetterList<N>, ?>) interp).updateAction(env2);
				else
					((QuickWidget.Interpreted<?>) interp).updateElement(env2);
			});
			syncChildren(getDefinition().getTransferSources(), theTransferSources,
				def -> (QuickDragging.TransferSource.Interpreted<BetterList<N>, ?>) def.interpret(this),
				(interp, env2) -> interp.updateTransferSource(env2, getNodeType()));
			syncChildren(getDefinition().getTransferAccepters(), theTransferAccepters,
				def -> (QuickDragging.TransferAccept.Interpreted<BetterList<N>, ?>) def.interpret(this),
				(interp, env2) -> interp.updateTransferAccepter(env2, getNodeType()));
		}

		@Override
		public T create() {
			return (T) new QuickTree<>(getIdentity());
		}
	}

	private ModelComponentId theActiveValueVariable;
	private ModelComponentId theSelectedVariable;
	private TreeModel<N> theModel;
	private ModelValueInstantiator<SettableValue<BetterList<N>>> thePathSelectionInstantiator;
	private ModelValueInstantiator<ObservableCollection<BetterList<N>>> thePathMultiSelectionInstantiator;
	private ModelValueInstantiator<SettableValue<N>> theNodeSelectionInstantiator;
	private ModelValueInstantiator<ObservableCollection<N>> theNodeMultiSelectionInstantiator;
	private boolean isRootVisible;

	private SettableValue<SettableValue<BetterList<N>>> thePathSelection;
	private SettableValue<ObservableCollection<BetterList<N>>> thePathMultiSelection;
	private SettableValue<SettableValue<N>> theNodeSelection;
	private SettableValue<ObservableCollection<N>> theNodeMultiSelection;
	private QuickTableColumn.SingleColumnSet<BetterList<N>, N> theTreeColumn;

	private SettableValue<SettableValue<BetterList<N>>> theActivePath;
	private SettableValue<SettableValue<Boolean>> isSelected;

	private ObservableCollection<ExElement> theActionsAndOptions;
	private ObservableCollection<ValueAction<BetterList<N>>> theActions;
	private ObservableCollection<QuickWidget> theOptions;

	private SettableValue<BetterList<N>> theTransferActiveValue;
	private SettableValue<Boolean> theTransferSelectedValue;
	private List<QuickDragging.TransferSource<BetterList<N>, ?>> theTransferSources;
	private List<QuickDragging.TransferAccept<BetterList<N>, ?>> theTransderAccepters;

	/** @param id The element ID for this widget */
	protected QuickTree(Object id) {
		super(id);
		isSelected = SettableValue.<SettableValue<Boolean>> build().build();
		thePathSelection = SettableValue.<SettableValue<BetterList<N>>> build().build();
		thePathMultiSelection = SettableValue.<ObservableCollection<BetterList<N>>> build().build();
		theNodeSelection = SettableValue.<SettableValue<N>> build().build();
		theNodeMultiSelection = SettableValue.<ObservableCollection<N>> build().build();
		theActivePath = SettableValue.<SettableValue<BetterList<N>>> build().build();
		theActionsAndOptions = ObservableCollection.create();
		theTransferActiveValue = SettableValue.create(BetterList.empty());
		theTransferSelectedValue = SettableValue.create();
		theTransferSources = new ArrayList<>();
		theTransderAccepters = new ArrayList<>();
	}

	/** @return The data model for the tree */
	public TreeModel<N> getModel() {
		return theModel;
	}

	@Override
	public ModelComponentId getActiveValueVariable() {
		return theActiveValueVariable;
	}

	@Override
	public ModelComponentId getSelectedVariable() {
		return theSelectedVariable;
	}

	@Override
	public void setContext(MultiValueRenderContext<BetterList<N>> ctx) throws ModelInstantiationException {
		theActivePath.set(ctx.getActiveValue(), null);
		isSelected.set(ctx.isSelected(), null);
	}

	@Override
	public SettableValue<BetterList<N>> getSelection() {
		return SettableValue.flatten(thePathSelection);
	}

	@Override
	public ObservableCollection<BetterList<N>> getMultiSelection() {
		return ObservableCollection.flattenValue(thePathMultiSelection);
	}

	/** @return The value of the selected node */
	public SettableValue<N> getNodeSelection() {
		return SettableValue.flatten(theNodeSelection);
	}

	/** @return The values of the selected nodes */
	public ObservableCollection<N> getNodeMultiSelection() {
		return ObservableCollection.flattenValue(theNodeMultiSelection);
	}

	/** @return The tree column to render and handle interactions with values in the tree */
	public QuickTableColumn.SingleColumnSet<BetterList<N>, N> getTreeColumn() {
		return theTreeColumn;
	}

	/** @return Whether the root node should be visible to the user */
	public boolean isRootVisible() {
		return isRootVisible;
	}

	/**
	 * @return The list containing the {@link #getActions() actions} and {@link #getOptions() table options} for this table, in order of
	 *         their specification in the file
	 */
	public ObservableCollection<ExElement> getActionsAndOptions() {
		return theActionsAndOptions.flow().unmodifiable(false).collectPassive();
	}

	/** @return Actions that can be executed against nodes in the tree */
	public ObservableCollection<ValueAction<BetterList<N>>> getActions() {
		return theActions;
	}

	/** @return Widget options to place in a bar above or below the table along with button actions */
	public ObservableCollection<QuickWidget> getOptions() {
		return theOptions;
	}

	/** @return The holder in which to put the path to the node the user is attempting to drag data onto */
	public SettableValue<BetterList<N>> getTransferActiveValue() {
		return theTransferActiveValue;
	}

	/** @return The holder in which to put the whether path to the node the user is attempting to drag data onto is selected */
	public SettableValue<Boolean> getTransferSelectedValue() {
		return theTransferSelectedValue;
	}

	@Override
	public List<QuickDragging.TransferSource<BetterList<N>, ?>> getTransferSources() {
		return Collections.unmodifiableList(theTransferSources);
	}

	@Override
	public List<QuickDragging.TransferAccept<BetterList<N>, ?>> getTransferAccepters() {
		return Collections.unmodifiableList(theTransderAccepters);
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);
		Interpreted<N, ?> myInterpreted = (Interpreted<N, ?>) interpreted;
		theActiveValueVariable = myInterpreted.getDefinition().getActiveValueVariable();
		theSelectedVariable = myInterpreted.getDefinition().getSelectedVariable();

		if (theModel != null && theModel.getIdentity() != myInterpreted.getModel().getIdentity()) {
			theModel.destroy();
			theModel = null;
		}
		if (theModel == null)
			theModel = myInterpreted.getModel().create();
		theModel.update(myInterpreted.getModel(), this);
		thePathSelectionInstantiator = myInterpreted.getSelection() == null ? null : myInterpreted.getSelection().instantiate();
		thePathMultiSelectionInstantiator = myInterpreted.getMultiSelection() == null ? null
			: myInterpreted.getMultiSelection().instantiate();
		theNodeSelectionInstantiator = myInterpreted.getNodeSelection() == null ? null : myInterpreted.getNodeSelection().instantiate();
		theNodeMultiSelectionInstantiator = myInterpreted.getNodeMultiSelection() == null ? null
			: myInterpreted.getNodeMultiSelection().instantiate();

		isRootVisible = myInterpreted.getDefinition().isRootVisible();

		if (theTreeColumn != null && theTreeColumn
			.getIdentity() != (myInterpreted.getTreeColumn() == null ? null : myInterpreted.getTreeColumn().getIdentity())) {
			theTreeColumn.destroy();
			theTreeColumn = null;
		}
		if (theTreeColumn == null && myInterpreted.getTreeColumn() != null)
			theTreeColumn = myInterpreted.getTreeColumn().create();
		if (theTreeColumn != null)
			theTreeColumn.update(myInterpreted.getTreeColumn(), this);

		CollectionUtils.synchronize(theActionsAndOptions, myInterpreted.getActionsAndOptions(), //
			(a, i) -> a.getIdentity() == i.getIdentity())//
		.<ModelInstantiationException> simpleX(aao -> {
			if (aao instanceof ValueAction.Interpreted)
				return ((ValueAction.Interpreted<BetterList<N>, ?>) aao).create();
			else if (aao instanceof QuickWidget.Interpreted)
				return ((QuickWidget.Interpreted<?>) aao).create();
			else
				throw new IllegalStateException("What is this? " + aao.getClass().getName());
		})//
		.rightOrder()//
		.onLeftX(element -> element.getLeftValue().destroy())//
		.onRightX(element -> element.getLeftValue().update(element.getRightValue(), this))//
		.onCommonX(element -> element.getLeftValue().update(element.getRightValue(), this))//
		.adjust();
		syncChildren(myInterpreted.getTransferSources(), theTransferSources, ts -> ts.create(), QuickDragging.TransferSource::update);
		syncChildren(myInterpreted.getTransferAccepters(), theTransderAccepters, ts -> ts.create(), QuickDragging.TransferAccept::update);
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
		super.instantiated();
		theModel.instantiated();
		if (thePathSelectionInstantiator != null)
			thePathSelectionInstantiator.instantiate();
		if (thePathMultiSelectionInstantiator != null)
			thePathMultiSelectionInstantiator.instantiate();
		if (theNodeSelectionInstantiator != null)
			theNodeSelectionInstantiator.instantiate();
		if (theNodeMultiSelectionInstantiator != null)
			theNodeMultiSelectionInstantiator.instantiate();
		if (theTreeColumn != null)
			theTreeColumn.instantiated();
		for (ExElement aao : theActionsAndOptions)
			aao.instantiated();
		for (QuickDragging.TransferSource<BetterList<N>, ?> ts : theTransferSources)
			ts.instantiated();
		for (QuickDragging.TransferAccept<BetterList<N>, ?> ta : theTransderAccepters)
			ta.instantiated();
	}

	@Override
	protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		super.doInstantiate(myModels);

		ExFlexibleElementModelAddOn.satisfyElementValue(theActiveValueVariable, myModels, SettableValue.flatten(theActivePath));
		ExFlexibleElementModelAddOn.satisfyElementValue(theSelectedVariable, myModels, SettableValue.flatten(isSelected, () -> false));
		theModel.instantiate(myModels);
		thePathSelection.set(thePathSelectionInstantiator == null ? null : thePathSelectionInstantiator.get(myModels), null);
		thePathMultiSelection.set(thePathMultiSelectionInstantiator == null ? null : thePathMultiSelectionInstantiator.get(myModels), null);
		theNodeSelection.set(theNodeSelectionInstantiator == null ? null : theNodeSelectionInstantiator.get(myModels), null);
		theNodeMultiSelection.set(theNodeMultiSelectionInstantiator == null ? null : theNodeMultiSelectionInstantiator.get(myModels), null);

		if (theTreeColumn != null)
			theTreeColumn.instantiate(myModels);

		if (theActions == null) {
			theActions = theActionsAndOptions.flow()//
				.filter((Class<ValueAction<BetterList<N>>>) (Class<?>) ValueAction.class)//
				.unmodifiable(false)//
				.collectActive(Observable.or(myModels.getUntil(), onDestroy()));
			theOptions = theActionsAndOptions.flow()//
				.filter(QuickWidget.class)//
				.unmodifiable(false)//
				.collectActive(Observable.or(myModels.getUntil(), onDestroy()));
		}

		for (ExElement aao : theActionsAndOptions)
			aao.instantiate(myModels);
		if (!theTransferSources.isEmpty() || !theTransderAccepters.isEmpty()) {
			ModelSetInstance transferCopy = QuickCoreInterpretation.copyModels(myModels, theActiveValueVariable, myModels.getUntil())
				.build();
			ExFlexibleElementModelAddOn.satisfyElementValue(theActiveValueVariable, transferCopy, theTransferActiveValue);
			ExFlexibleElementModelAddOn.satisfyElementValue(theSelectedVariable, transferCopy, theTransferSelectedValue);
			for (QuickDragging.TransferSource<BetterList<N>, ?> ts : theTransferSources)
				ts.instantiate(transferCopy);
			for (QuickDragging.TransferAccept<BetterList<N>, ?> ta : theTransderAccepters)
				ta.instantiate(transferCopy);
		}
	}

	@Override
	public QuickTree<N> copy(ExElement parent) {
		QuickTree<N> copy = (QuickTree<N>) super.copy(parent);
		copy.theModel = theModel.copy(copy);
		if (thePathSelection != null)
			copy.thePathSelection = SettableValue.<SettableValue<BetterList<N>>> build().build();
		if (thePathMultiSelection != null)
			copy.thePathMultiSelection = SettableValue.<ObservableCollection<BetterList<N>>> build().build();
		if (theNodeSelection != null)
			copy.theNodeSelection = SettableValue.<SettableValue<N>> build().build();
		if (theNodeMultiSelection != null)
			copy.theNodeMultiSelection = SettableValue.<ObservableCollection<N>> build().build();

		if (theTreeColumn != null)
			copy.theTreeColumn = theTreeColumn.copy(copy);

		copy.theActivePath = SettableValue.<SettableValue<BetterList<N>>> build().build();
		copy.isSelected = SettableValue.<SettableValue<Boolean>> build().build();
		copy.theActionsAndOptions = ObservableCollection.create();

		for (ExElement aao : theActionsAndOptions)
			copy.theActionsAndOptions.add(aao.copy(copy));
		copy.theTransferActiveValue = SettableValue.create(BetterList.empty());
		copy.theTransferSelectedValue = SettableValue.create();
		copy.theTransferSources = new ArrayList<>();
		for (QuickDragging.TransferSource<BetterList<N>, ?> ts : theTransferSources)
			copy.theTransferSources.add(ts.copy(copy));
		copy.theTransderAccepters = new ArrayList<>();
		for (QuickDragging.TransferAccept<BetterList<N>, ?> ta : theTransderAccepters)
			copy.theTransderAccepters.add(ta.copy(copy));

		return copy;
	}
}
