package org.observe.quick.base;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
import org.observe.quick.QuickWidget;
import org.observe.util.TypeTokens;
import org.qommons.collect.BetterList;
import org.qommons.collect.CollectionUtils;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

public class QuickTree<N> extends QuickWidget.Abstract implements MultiValueWidget<BetterList<N>> {
	public static final String TREE = "tree";

	@ExMultiElementTraceable({ //
		@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
			qonfigType = TREE,
			interpretation = Interpreted.class,
			instance = QuickTree.class),
		@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = "multi-value-widget",
		interpretation = Interpreted.class,
		instance = QuickTree.class),
		@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = "multi-value-renderable",
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
		private final List<ValueAction.Def<?, ?>> theActions;
		private boolean isRootVisible;

		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
			theActions = new ArrayList<>();
		}

		@QonfigChildGetter(asType = "tree", value = "tree-model")
		public TreeModel.Def<?> getModel() {
			return theModel;
		}

		@QonfigChildGetter(asType = "tree", value = "tree-column")
		public QuickTableColumn.SingleColumnSet.Def getTreeColumn() {
			return theTreeColumn;
		}

		@QonfigAttributeGetter(asType = "multi-value-renderable", value = "active-value-name")
		@Override
		public ModelComponentId getActiveValueVariable() {
			return theActiveValueVariable;
		}

		@QonfigAttributeGetter(asType = "tree", value = "active-node-name")
		public ModelComponentId getNodeVariable() {
			return theNodeVariable;
		}

		public ModelComponentId getSelectedVariable() {
			return theSelectedVariable;
		}

		@QonfigAttributeGetter(asType = "multi-value-widget", value = "selection")
		@Override
		public CompiledExpression getSelection() {
			return thePathSelection;
		}

		@QonfigAttributeGetter(asType = "multi-value-widget", value = "multi-selection")
		@Override
		public CompiledExpression getMultiSelection() {
			return thePathMultiSelection;
		}

		@QonfigAttributeGetter(asType = "tree", value = "node-selection")
		public CompiledExpression getNodeSelection() {
			return theNodeSelection;
		}

		@QonfigAttributeGetter(asType = "tree", value = "node-multi-selection")
		public CompiledExpression getNodeMultiSelection() {
			return theNodeMultiSelection;
		}

		@QonfigChildGetter(asType = TREE, value = "action")
		public List<ValueAction.Def<?, ?>> getActions() {
			return Collections.unmodifiableList(theActions);
		}

		@QonfigAttributeGetter(asType = TREE, value = "root-visible")
		public boolean isRootVisible() {
			return isRootVisible;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
			String valueName = session.getAttributeText("active-value-name");
			theActiveValueVariable = elModels.getElementValueModelId(valueName);
			String nodeName = session.getAttributeText("active-node-name");
			theNodeVariable = elModels.getElementValueModelId(nodeName);
			theModel = syncChild(TreeModel.Def.class, theModel, session, "tree-model", (m, mEnv) -> m.update(mEnv, valueName, nodeName));
			theSelectedVariable = elModels.getElementValueModelId("selected");
			theTreeColumn = syncChild(QuickTableColumn.SingleColumnSet.Def.class, theTreeColumn, session, "tree-column");
			thePathSelection = getAttributeExpression("selection", session);
			thePathMultiSelection = getAttributeExpression("multi-selection", session);
			theNodeSelection = getAttributeExpression("node-selection", session);
			theNodeMultiSelection = getAttributeExpression("node-multi-selection", session);
			elModels.satisfyElementValueType(theActiveValueVariable, ModelTypes.Value,
				(interp, env) -> ModelTypes.Value.forType(((Interpreted<?, ?>) interp).getPathType()));
			isRootVisible = session.getAttribute("root-visible", boolean.class);

			syncChildren(ValueAction.Def.class, theActions, session.forChildren("action"));
		}

		@Override
		public Interpreted<?, ? extends T> interpret(ExElement.Interpreted<?> parent) {
			return (Interpreted<?, ? extends T>) new Interpreted<>((Def<QuickTree<Object>>) this, parent);
		}
	}

	public static class Interpreted<N, T extends QuickTree<N>> extends QuickWidget.Interpreted.Abstract<T>
	implements MultiValueWidget.Interpreted<BetterList<N>, T> {
		private TreeModel.Interpreted<N, ?> theModel;
		private TypeToken<N> theNodeType;
		private QuickTableColumn.SingleColumnSet.Interpreted<BetterList<N>, N> theTreeColumn;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<BetterList<N>>> thePathSelection;
		private InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<BetterList<N>>> thePathMultiSelection;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<N>> theNodeSelection;
		private InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<N>> theNodeMultiSelection;
		private final List<ValueAction.Interpreted<BetterList<N>, ?>> theActions;

		protected Interpreted(Def<? super T> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
			persistModelInstances(true);
			theActions = new ArrayList<>();
		}

		@Override
		public Def<? super T> getDefinition() {
			return (Def<? super T>) super.getDefinition();
		}

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

		public InterpretedValueSynth<SettableValue<?>, SettableValue<N>> getNodeSelection() {
			return theNodeSelection;
		}

		public InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<N>> getNodeMultiSelection() {
			return theNodeMultiSelection;
		}

		@Override
		public TypeToken<BetterList<N>> getValueType() throws ExpressoInterpretationException {
			return TypeTokens.get().keyFor(BetterList.class).<BetterList<N>> parameterized(getNodeType());
		}

		public TypeToken<N> getNodeType() throws ExpressoInterpretationException {
			if (theNodeType == null)
				theNodeType = (TypeToken<N>) theModel.getNodeType(getExpressoEnv());
			return theNodeType;
		}

		public TypeToken<BetterList<N>> getPathType() throws ExpressoInterpretationException {
			return TypeTokens.get().keyFor(BetterList.class).<BetterList<N>> parameterized(getNodeType());
		}

		public QuickTableColumn.SingleColumnSet.Interpreted<BetterList<N>, N> getTreeColumn() {
			return theTreeColumn;
		}

		public List<ValueAction.Interpreted<BetterList<N>, ?>> getActions() {
			return Collections.unmodifiableList(theActions);
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

			syncChildren(getDefinition().getActions(), theActions,
				def -> (ValueAction.Interpreted<BetterList<N>, ?>) ((ValueAction.Def<BetterList<N>, ?>) def).interpret(this,
					getValueType()),
				ValueAction.Interpreted::updateAction);
		}

		@Override
		public T create() {
			return (T) new QuickTree<>(getIdentity());
		}
	}

	private ModelComponentId theActiveValueVariable;
	private ModelComponentId theSelectedVariable;
	private TypeToken<N> theNodeType;
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

	private ObservableCollection<ValueAction<BetterList<N>>> theActions;

	protected QuickTree(Object id) {
		super(id);
		isSelected = SettableValue.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<Boolean>> parameterized(boolean.class))
			.build();
	}

	public TreeModel<N> getModel() {
		return theModel;
	}

	public TypeToken<N> getNodeType() {
		return theNodeType;
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

	public SettableValue<N> getNodeSelection() {
		return SettableValue.flatten(theNodeSelection);
	}

	public ObservableCollection<N> getNodeMultiSelection() {
		return ObservableCollection.flattenValue(theNodeMultiSelection);
	}

	public QuickTableColumn.SingleColumnSet<BetterList<N>, N> getTreeColumn() {
		return theTreeColumn;
	}

	public boolean isRootVisible() {
		return isRootVisible;
	}

	public ObservableCollection<ValueAction<BetterList<N>>> getActions() {
		return theActions.flow().unmodifiable(false).collect();
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) {
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

		TypeToken<N> nodeType;
		try {
			nodeType = myInterpreted.getNodeType();
		} catch (ExpressoInterpretationException e) {
			throw new IllegalStateException("Not evaluated?", e);
		}
		if (theNodeType == null || !theNodeType.equals(nodeType)) {
			theNodeType = nodeType;
			TypeToken<BetterList<N>> pathType = TypeTokens.get().keyFor(BetterList.class).parameterized(theNodeType);
			thePathSelection = SettableValue
				.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<BetterList<N>>> parameterized(pathType)).build();
			thePathMultiSelection = SettableValue
				.build(TypeTokens.get().keyFor(ObservableCollection.class).<ObservableCollection<BetterList<N>>> parameterized(pathType))
				.build();
			theNodeSelection = SettableValue.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<N>> parameterized(nodeType))
				.build();
			theNodeMultiSelection = SettableValue
				.build(TypeTokens.get().keyFor(ObservableCollection.class).<ObservableCollection<N>> parameterized(nodeType)).build();
			theActivePath = SettableValue
				.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<BetterList<N>>> parameterized(pathType)).build();
			theActions = ObservableCollection
				.build(TypeTokens.get().keyFor(ValueAction.class).<ValueAction<BetterList<N>>> parameterized(pathType)).build();
		}
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

		CollectionUtils.synchronize(theActions, myInterpreted.getActions(), //
			(a, i) -> a.getIdentity() == i.getIdentity())//
		.simple(action -> action.create())//
		.rightOrder()//
		.onLeftX(element -> element.getLeftValue().destroy())//
		.onRight(element -> element.getLeftValue().update(element.getRightValue(), this))//
		.onCommon(element -> element.getLeftValue().update(element.getRightValue(), this))//
		.adjust();
	}

	@Override
	public void instantiated() {
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
		for (ValueAction<BetterList<N>> action : theActions)
			action.instantiated();
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

		for (ValueAction<BetterList<N>> action : theActions)
			action.instantiate(myModels);
	}

	@Override
	public QuickTree<N> copy(ExElement parent) {
		QuickTree<N> copy = (QuickTree<N>) super.copy(parent);
		copy.theModel = theModel.copy(copy);
		if (thePathSelection != null)
			copy.thePathSelection = SettableValue.build(thePathSelection.getType()).build();
		if (thePathMultiSelection != null)
			copy.thePathMultiSelection = SettableValue.build(thePathMultiSelection.getType()).build();
		if (theNodeSelection != null)
			copy.theNodeSelection = SettableValue.build(theNodeSelection.getType()).build();
		if (theNodeMultiSelection != null)
			copy.theNodeMultiSelection = SettableValue.build(theNodeMultiSelection.getType()).build();

		if (theTreeColumn != null)
			copy.theTreeColumn = theTreeColumn.copy(copy);

		copy.theActivePath = SettableValue.build(theActivePath.getType()).build();
		copy.isSelected = SettableValue.build(isSelected.getType()).build();
		copy.theActions = ObservableCollection.build(theActions.getType()).build();

		for (ValueAction<BetterList<N>> action : theActions)
			copy.theActions.add(action.copy(copy));

		return copy;
	}
}
