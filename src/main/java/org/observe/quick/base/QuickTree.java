package org.observe.quick.base;

import org.observe.Observable;
import org.observe.ObservableValue;
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
import org.qommons.LambdaUtils;
import org.qommons.collect.BetterList;
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
		private CompiledExpression theRoot;
		private CompiledExpression theChildren;
		private CompiledExpression isLeaf;
		private ModelComponentId theActiveValueVariable;
		private ModelComponentId theNodeVariable;
		private ModelComponentId theSelectedVariable;
		private QuickTableColumn.SingleColumnSet.Def theTreeColumn;
		private CompiledExpression theSelection;
		private CompiledExpression theMultiSelection;

		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		@QonfigAttributeGetter(asType = "tree", value = "root")
		public CompiledExpression getRoot() {
			return theRoot;
		}

		@QonfigAttributeGetter(asType = "tree", value = "children")
		public CompiledExpression getChildren() {
			return theChildren;
		}

		@QonfigAttributeGetter(asType = "tree", value = "leaf")
		public CompiledExpression isLeaf() {
			return isLeaf;
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

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			theRoot = session.getAttributeExpression("root");
			theChildren = session.getAttributeExpression("children");
			isLeaf = session.getAttributeExpression("leaf");
			ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
			String valueName = session.getAttributeText("active-value-name");
			theActiveValueVariable = elModels.getElementValueModelId(valueName);
			String pathName = session.getAttributeText("active-node-name");
			theNodeVariable = elModels.getElementValueModelId(pathName);
			theSelectedVariable = elModels.getElementValueModelId("selected");
			theTreeColumn = ExElement.useOrReplace(QuickTableColumn.SingleColumnSet.Def.class, theTreeColumn, session, "tree-column");
			theSelection = session.getAttributeExpression("selection");
			theMultiSelection = session.getAttributeExpression("multi-selection");
			elModels.satisfyElementValueType(theActiveValueVariable, ModelTypes.Value,
				(interp, env) -> ModelTypes.Value.forType(((Interpreted<?, ?>) interp).getPathType()));
			elModels.satisfyElementValueType(theNodeVariable, ModelTypes.Value,
				(interp, env) -> ModelTypes.Value.forType(((Interpreted<?, ?>) interp).getNodeType()));
		}

		@Override
		public Interpreted<?, ? extends T> interpret(ExElement.Interpreted<?> parent) {
			return (Interpreted<?, ? extends T>) new Interpreted<>((Def<QuickTree<Object>>) this, parent);
		}
	}

	public static class Interpreted<N, T extends QuickTree<N>> extends QuickWidget.Interpreted.Abstract<T>
	implements MultiValueWidget.Interpreted<BetterList<N>, T> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<N>> theRoot;
		private InterpretedValueSynth<ObservableCollection<?>, ? extends ObservableCollection<? extends N>> theChildren;
		private TypeToken<N> theNodeType;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> isLeaf;
		private QuickTableColumn.SingleColumnSet.Interpreted<BetterList<N>, N> theTreeColumn;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<BetterList<N>>> theSelection;
		private InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<BetterList<N>>> theMultiSelection;

		Interpreted(Def<? super T> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
			persistModelInstances(true);
		}

		@Override
		public Def<? super T> getDefinition() {
			return (Def<? super T>) super.getDefinition();
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<N>> getRoot() {
			return theRoot;
		}

		public InterpretedValueSynth<ObservableCollection<?>, ? extends ObservableCollection<? extends N>> getChildren() {
			return theChildren;
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> isLeaf() {
			return isLeaf;
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
		public TypeToken<BetterList<N>> getValueType() {
			return TypeTokens.get().keyFor(BetterList.class).<BetterList<N>> parameterized(theNodeType);
		}

		public TypeToken<N> getNodeType() throws ExpressoInterpretationException {
			if (theNodeType == null) {
				theRoot = getDefinition().getRoot().interpret(ModelTypes.Value.anyAs(), getExpressoEnv());
				theNodeType = (TypeToken<N>) theRoot.getType().getType(0);
			}
			return theNodeType;
		}

		public TypeToken<BetterList<N>> getPathType() throws ExpressoInterpretationException {
			return TypeTokens.get().keyFor(BetterList.class).<BetterList<N>> parameterized(getNodeType());
		}

		public QuickTableColumn.SingleColumnSet.Interpreted<BetterList<N>, N> getTreeColumn() {
			return theTreeColumn;
		}

		@Override
		public TypeToken<? extends T> getWidgetType() throws ExpressoInterpretationException {
			return TypeTokens.get().keyFor(QuickTree.class).<T> parameterized(getNodeType());
		}

		@Override
		protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			super.doUpdate(env);

			getNodeType(); // Initialize root
			theChildren = getDefinition().getChildren().interpret(ModelTypes.Collection.forType(getNodeType()), env);
			TypeToken<?> childType = theChildren.getType().getType(0);
			if (!TypeTokens.get().isAssignable(theNodeType, childType)) {
				throw new ExpressoInterpretationException(
					"The type of a tree's children must be a sub-type of the type of its root\n" + childType + " is not a sub-type of "
						+ theNodeType + "\n" + "Try using a cast to a super-type on the root",
						getDefinition().getChildren().getFilePosition().getPosition(0),
						getDefinition().getChildren().getExpression().getExpressionLength());
			}
			isLeaf = getDefinition().isLeaf() == null ? null : getDefinition().isLeaf().interpret(ModelTypes.Value.BOOLEAN, env);
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
		}

		@Override
		public T create() {
			return (T) new QuickTree<>(getIdentity());
		}
	}

	private ModelComponentId theActiveValueVariable;
	private ModelComponentId theNodeVariable;
	private ModelComponentId theSelectedVariable;
	private TypeToken<N> theNodeType;
	private ModelValueInstantiator<SettableValue<N>> theRootInstantiator;
	private ModelValueInstantiator<? extends ObservableCollection<? extends N>> theChildren;
	private ModelValueInstantiator<SettableValue<Boolean>> theLeafInstantiator;
	private ModelValueInstantiator<SettableValue<BetterList<N>>> theSelectionInstantiator;
	private ModelValueInstantiator<ObservableCollection<BetterList<N>>> theMultiSelectionInstantiator;

	private SettableValue<SettableValue<N>> theRoot;
	private SettableValue<Boolean> isLeaf;
	private SettableValue<SettableValue<BetterList<N>>> theSelection;
	private SettableValue<ObservableCollection<BetterList<N>>> theMultiSelection;
	private QuickTableColumn.SingleColumnSet<BetterList<N>, N> theTreeColumn;

	private SettableValue<SettableValue<N>> theActiveNode;
	private SettableValue<SettableValue<BetterList<N>>> theActivePath;
	private SettableValue<SettableValue<Boolean>> isSelected;

	QuickTree(Object id) {
		super(id);
		isSelected = SettableValue.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<Boolean>> parameterized(boolean.class))
			.build();
	}

	public TypeToken<N> getNodeType() {
		return theNodeType;
	}

	@Override
	public ModelComponentId getActiveValueVariable() {
		return theActiveValueVariable;
	}

	public ModelComponentId getPathVariable() {
		return theNodeVariable;
	}

	@Override
	public ModelComponentId getSelectedVariable() {
		return theSelectedVariable;
	}

	@Override
	public void setContext(MultiValueRenderContext<BetterList<N>> ctx) throws ModelInstantiationException {
		theActivePath.set(ctx.getActiveValue(), null);
		isSelected.set(ctx.isSelected(), null);
		theActiveNode.set(SettableValue.asSettable(
			ctx.getActiveValue().map(theNodeType, LambdaUtils.printableFn(path -> path == null ? null : path.getLast(), "last", null)),
			__ -> "Tree value is not settable"), null);
	}

	public SettableValue<N> getRoot() {
		return SettableValue.flatten(theRoot);
	}

	public ObservableCollection<? extends N> getChildren(ObservableValue<BetterList<N>> path, Observable<?> until)
		throws ModelInstantiationException {
		ModelSetInstance nodeModel = getUpdatingModels().copy(until).build();
		ExFlexibleElementModelAddOn.satisfyElementValue(theActiveValueVariable, nodeModel,
			SettableValue.asSettable(path, __ -> "Not Settable"),
			ExFlexibleElementModelAddOn.ActionIfSatisfied.Replace);
		ExFlexibleElementModelAddOn.satisfyElementValue(theNodeVariable, nodeModel,
			SettableValue.asSettable(path.map(theNodeType, p -> p == null ? null : p.peekLast()), __ -> "Not Settable"),
			ExFlexibleElementModelAddOn.ActionIfSatisfied.Replace);
		return theChildren.get(nodeModel);
	}

	public boolean isLeaf() {
		return isLeaf != null && isLeaf.get();
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

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) {
		super.doUpdate(interpreted);
		Interpreted<N, ?> myInterpreted = (Interpreted<N, ?>) interpreted;
		theActiveValueVariable = myInterpreted.getDefinition().getActiveValueVariable();
		theNodeVariable = myInterpreted.getDefinition().getNodeVariable();
		theSelectedVariable = myInterpreted.getDefinition().getSelectedVariable();

		theRootInstantiator = myInterpreted.getRoot().instantiate();
		theChildren = myInterpreted.getChildren().instantiate();
		theLeafInstantiator = myInterpreted.isLeaf() == null ? null : myInterpreted.isLeaf().instantiate();
		theSelectionInstantiator = myInterpreted.getSelection() == null ? null : myInterpreted.getSelection().instantiate();
		theMultiSelectionInstantiator = myInterpreted.getMultiSelection() == null ? null : myInterpreted.getMultiSelection().instantiate();

		TypeToken<N> nodeType;
		try {
			nodeType = myInterpreted.getNodeType();
		} catch (ExpressoInterpretationException e) {
			throw new IllegalStateException("Not evaluated?", e);
		}
		if (theRoot == null || !theNodeType.equals(nodeType)) {
			theNodeType = nodeType;
			TypeToken<BetterList<N>> pathType = TypeTokens.get().keyFor(BetterList.class).parameterized(theNodeType);
			theRoot = SettableValue.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<N>> parameterized(nodeType)).build();
			theSelection = SettableValue
				.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<BetterList<N>>> parameterized(pathType)).build();
			theMultiSelection = SettableValue
				.build(TypeTokens.get().keyFor(ObservableCollection.class).<ObservableCollection<BetterList<N>>> parameterized(pathType))
				.build();
			theActiveNode = SettableValue.build(theRoot.getType()).build();
			theActivePath = SettableValue
				.build(TypeTokens.get().keyFor(SettableValue.class)
					.<SettableValue<BetterList<N>>> parameterized(pathType))
				.build();
		}

		if (theTreeColumn != null && theTreeColumn
			.getIdentity() != (myInterpreted.getTreeColumn() == null ? null : myInterpreted.getTreeColumn().getIdentity())) {
			theTreeColumn.destroy();
			theTreeColumn = null;
		}
		if (theTreeColumn == null && myInterpreted.getTreeColumn() != null)
			theTreeColumn = myInterpreted.getTreeColumn().create();
		if (theTreeColumn != null)
			theTreeColumn.update(myInterpreted.getTreeColumn(), this);
	}

	@Override
	public void instantiated() {
		super.instantiated();
		theChildren.instantiate();
		if (theLeafInstantiator != null)
			theLeafInstantiator.instantiate();
		if (theSelectionInstantiator != null)
			theSelectionInstantiator.instantiate();
		if (theMultiSelectionInstantiator != null)
			theMultiSelectionInstantiator.instantiate();
		if (theTreeColumn != null)
			theTreeColumn.instantiated();
	}

	@Override
	protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		super.doInstantiate(myModels);

		ExFlexibleElementModelAddOn.satisfyElementValue(theActiveValueVariable, myModels, SettableValue.flatten(theActivePath));
		ExFlexibleElementModelAddOn.satisfyElementValue(theNodeVariable, myModels, SettableValue.flatten(theActiveNode));
		ExFlexibleElementModelAddOn.satisfyElementValue(theSelectedVariable, myModels, SettableValue.flatten(isSelected, () -> false));
		theRoot.set(theRootInstantiator.get(myModels), null);
		isLeaf = theLeafInstantiator == null ? null : theLeafInstantiator.get(myModels);
		theSelection.set(theSelectionInstantiator == null ? null : theSelectionInstantiator.get(myModels), null);
		theMultiSelection.set(theMultiSelectionInstantiator == null ? null : theMultiSelectionInstantiator.get(myModels), null);

		if (theTreeColumn != null)
			theTreeColumn.instantiate(myModels);
	}

	@Override
	public QuickTree<N> copy(ExElement parent) {
		QuickTree<N> copy = (QuickTree<N>) super.copy(parent);
		copy.theRoot = SettableValue.build(theRoot.getType()).build();
		if (theSelection != null)
			copy.theSelection = SettableValue.build(theSelection.getType()).build();
		if (theMultiSelection != null)
			copy.theMultiSelection = SettableValue.build(theMultiSelection.getType()).build();

		if (theTreeColumn != null)
			copy.theTreeColumn = theTreeColumn.copy(copy);

		copy.theActiveNode = SettableValue.build(theActiveNode.getType()).build();
		copy.theActivePath = SettableValue.build(theActivePath.getType()).build();
		copy.isSelected = SettableValue.build(isSelected.getType()).build();
		return copy;
	}
}
