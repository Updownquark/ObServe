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
		private CompiledExpression theSelection;
		private CompiledExpression theMultiSelection;
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
			return theSelection;
		}

		@QonfigAttributeGetter(asType = "multi-value-widget", value = "multi-selection")
		@Override
		public CompiledExpression getMultiSelection() {
			return theMultiSelection;
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
			theModel = ExElement.useOrReplace(TreeModel.Def.class, theModel, session, "tree-model");
			ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
			String valueName = session.getAttributeText("active-value-name");
			theActiveValueVariable = elModels.getElementValueModelId(valueName);
			String nodeName = session.getAttributeText("active-node-name");
			theNodeVariable = elModels.getElementValueModelId(nodeName);
			theSelectedVariable = elModels.getElementValueModelId("selected");
			theTreeColumn = ExElement.useOrReplace(QuickTableColumn.SingleColumnSet.Def.class, theTreeColumn, session, "tree-column");
			theSelection = session.getAttributeExpression("selection");
			theMultiSelection = session.getAttributeExpression("multi-selection");
			elModels.satisfyElementValueType(theActiveValueVariable, ModelTypes.Value,
				(interp, env) -> ModelTypes.Value.forType(((Interpreted<?, ?>) interp).getPathType()));
			isRootVisible = session.getAttribute("root-visible", boolean.class);

			ExElement.syncDefs(ValueAction.Def.class, theActions, session.forChildren("action"));
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
		private InterpretedValueSynth<SettableValue<?>, SettableValue<BetterList<N>>> theSelection;
		private InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<BetterList<N>>> theMultiSelection;
		private final List<ValueAction.Interpreted<BetterList<N>, ?>> theActions;

		Interpreted(Def<? super T> definition, ExElement.Interpreted<?> parent) {
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
			return theSelection;
		}

		@Override
		public InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<BetterList<N>>> getMultiSelection() {
			return theMultiSelection;
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
		public TypeToken<? extends T> getWidgetType() throws ExpressoInterpretationException {
			return TypeTokens.get().keyFor(QuickTree.class).<T> parameterized(getNodeType());
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
			theModel.updateModel(env);
			if (theTreeColumn != null && (getDefinition().getTreeColumn() == null
				|| theTreeColumn.getIdentity() != getDefinition().getTreeColumn().getIdentity())) {
				theTreeColumn.destroy();
				theTreeColumn = null;
			}
			if (getDefinition().getTreeColumn() != null) {
				if (theTreeColumn == null)
					theTreeColumn = (QuickTableColumn.SingleColumnSet.Interpreted<BetterList<N>, N>) getDefinition().getTreeColumn()
					.<BetterList<N>> interpret(this);
				theTreeColumn.updateColumns(env);
			}
			TypeToken<BetterList<N>> pathType = TypeTokens.get().keyFor(BetterList.class).<BetterList<N>> parameterized(getNodeType());
			theSelection = getDefinition().getSelection() == null ? null
				: getDefinition().getSelection().interpret(ModelTypes.Value.forType(pathType), env);
			theMultiSelection = getDefinition().getSelection() == null ? null
				: getDefinition().getMultiSelection().interpret(ModelTypes.Collection.forType(pathType), env);

			CollectionUtils.synchronize(theActions, getDefinition().getActions(), //
				(a, d) -> a.getIdentity() == d.getIdentity())//
			.<ExpressoInterpretationException> simpleE(
				child -> (ValueAction.Interpreted<BetterList<N>, ?>) ((ValueAction.Def<BetterList<N>, ?>) child).interpret(this,
					getValueType()))//
			.rightOrder()//
			.onLeftX(el -> el.getLeftValue().destroy())//
			.onRightX(element -> element.getLeftValue().updateAction(getExpressoEnv()))//
			.onCommonX(element -> element.getLeftValue().updateAction(getExpressoEnv()))//
			.adjust();
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
	private ModelValueInstantiator<SettableValue<BetterList<N>>> theSelectionInstantiator;
	private ModelValueInstantiator<ObservableCollection<BetterList<N>>> theMultiSelectionInstantiator;
	private boolean isRootVisible;

	private SettableValue<SettableValue<BetterList<N>>> theSelection;
	private SettableValue<ObservableCollection<BetterList<N>>> theMultiSelection;
	private QuickTableColumn.SingleColumnSet<BetterList<N>, N> theTreeColumn;

	private SettableValue<SettableValue<BetterList<N>>> theActivePath;
	private SettableValue<SettableValue<Boolean>> isSelected;

	private ObservableCollection<ValueAction<BetterList<N>>> theActions;

	QuickTree(Object id) {
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
		return SettableValue.flatten(theSelection);
	}

	@Override
	public ObservableCollection<BetterList<N>> getMultiSelection() {
		return ObservableCollection.flattenValue(theMultiSelection);
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
		theSelectionInstantiator = myInterpreted.getSelection() == null ? null : myInterpreted.getSelection().instantiate();
		theMultiSelectionInstantiator = myInterpreted.getMultiSelection() == null ? null : myInterpreted.getMultiSelection().instantiate();

		TypeToken<N> nodeType;
		try {
			nodeType = myInterpreted.getNodeType();
		} catch (ExpressoInterpretationException e) {
			throw new IllegalStateException("Not evaluated?", e);
		}
		if (theNodeType == null || !theNodeType.equals(nodeType)) {
			theNodeType = nodeType;
			TypeToken<BetterList<N>> pathType = TypeTokens.get().keyFor(BetterList.class).parameterized(theNodeType);
			theSelection = SettableValue
				.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<BetterList<N>>> parameterized(pathType)).build();
			theMultiSelection = SettableValue
				.build(TypeTokens.get().keyFor(ObservableCollection.class).<ObservableCollection<BetterList<N>>> parameterized(pathType))
				.build();
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
		if (theSelectionInstantiator != null)
			theSelectionInstantiator.instantiate();
		if (theMultiSelectionInstantiator != null)
			theMultiSelectionInstantiator.instantiate();
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
		theSelection.set(theSelectionInstantiator == null ? null : theSelectionInstantiator.get(myModels), null);
		theMultiSelection.set(theMultiSelectionInstantiator == null ? null : theMultiSelectionInstantiator.get(myModels), null);

		if (theTreeColumn != null)
			theTreeColumn.instantiate(myModels);

		for (ValueAction<BetterList<N>> action : theActions)
			action.instantiate(myModels);
	}

	@Override
	public QuickTree<N> copy(ExElement parent) {
		QuickTree<N> copy = (QuickTree<N>) super.copy(parent);
		copy.theModel = theModel.copy(copy);
		if (theSelection != null)
			copy.theSelection = SettableValue.build(theSelection.getType()).build();
		if (theMultiSelection != null)
			copy.theMultiSelection = SettableValue.build(theMultiSelection.getType()).build();

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
