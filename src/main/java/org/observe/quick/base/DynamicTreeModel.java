package org.observe.quick.base;

import org.observe.Observable;
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
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExFlexibleElementModelAddOn;
import org.observe.expresso.qonfig.ExMultiElementTraceable;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.quick.QuickCoreInterpretation;
import org.observe.quick.QuickWidget;
import org.observe.util.TypeTokens;
import org.observe.util.swing.PanelPopulation;
import org.qommons.BreakpointHere;
import org.qommons.ThreadConstraint;
import org.qommons.collect.BetterList;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

/**
 * A tree model for dynamic document structures--each node has a dynamic number of children
 *
 * @param <N> The type of nodes in the tree
 */
public class DynamicTreeModel<N> extends ExElement.Abstract implements TreeModel<N> {
	/** The XML name of this element */
	public static final String DYNAMIC_TREE_MODEL = "dynamic-tree-model";

	/** {@link DynamicTreeModel} definition */
	@ExMultiElementTraceable({ //
		@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
			qonfigType = DYNAMIC_TREE_MODEL,
			interpretation = Interpreted.class,
			instance = DynamicTreeModel.class),
		@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = TreeModel.TREE_MODEL,
		interpretation = Interpreted.class,
		instance = DynamicTreeModel.class) })
	public static class Def extends TreeModel.Def.Abstract<DynamicTreeModel<?>> {
		private CompiledExpression theRoot;
		private CompiledExpression theChildren;
		private CompiledExpression isLeaf;

		/**
		 * @param parent The parent for this element
		 * @param qonfigType The Qonfig type of this element
		 */
		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		/** @return The root node of the tree model */
		@QonfigAttributeGetter(asType = TreeModel.TREE_MODEL, value = "value")
		public CompiledExpression getRoot() {
			return theRoot;
		}

		/** @return An expression to generate children for a node */
		@QonfigAttributeGetter(asType = DYNAMIC_TREE_MODEL, value = "children")
		public CompiledExpression getChildren() {
			return theChildren;
		}

		/** @return Whether a node is a leaf node */
		@QonfigAttributeGetter(asType = DYNAMIC_TREE_MODEL, value = "leaf")
		public CompiledExpression isLeaf() {
			return isLeaf;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			theRoot = getAttributeExpression("value", session);
			theChildren = getAttributeExpression("children", session);
			isLeaf = getAttributeExpression("leaf", session);
		}

		@Override
		public <N> Interpreted<N> interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}
	}

	/**
	 * {@link DynamicTreeModel} interpretation
	 *
	 * @param <N> The type of nodes in the tree
	 */
	public static class Interpreted<N> extends TreeModel.Interpreted.Abstract<N, DynamicTreeModel<N>> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<N>> theRoot;
		private InterpretedValueSynth<ObservableCollection<?>, ? extends ObservableCollection<? extends N>> theChildren;
		private TypeToken<N> theNodeType;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> isLeaf;

		Interpreted(Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		/** @return The root node of the tree model */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<N>> getRoot() {
			return theRoot;
		}

		/** @return An expression to generate children for a node */
		public InterpretedValueSynth<ObservableCollection<?>, ? extends ObservableCollection<? extends N>> getChildren() {
			return theChildren;
		}

		/** @return Whether a node is a leaf node */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> isLeaf() {
			return isLeaf;
		}

		@Override
		public TypeToken<N> doGetNodeType() throws ExpressoInterpretationException {
			if (theNodeType == null) {
				theRoot = interpret(getDefinition().getRoot(), ModelTypes.Value.anyAs());
				theNodeType = (TypeToken<N>) theRoot.getType().getType(0);
			}
			return theNodeType;
		}

		/**
		 * @return The type of tree paths for the tree
		 * @throws ExpressoInterpretationException If the path type could not be interpreted
		 */
		public TypeToken<BetterList<N>> getPathType() throws ExpressoInterpretationException {
			return TypeTokens.get().keyFor(BetterList.class).<BetterList<N>> parameterized(getNodeType());
		}

		@Override
		protected void doUpdate() throws ExpressoInterpretationException {
			super.doUpdate();

			getNodeType(); // Initialize root
			theChildren = interpret(getDefinition().getChildren(), ModelTypes.Collection.forType(theNodeType));
			TypeToken<?> childType = theChildren.getType().getType(0);
			if (!TypeTokens.get().isAssignable(theNodeType, childType)) {
				throw new ExpressoInterpretationException(
					"The type of a tree's children must be a sub-type of the type of its root\n" + childType + " is not a sub-type of "
						+ theNodeType + "\n" + "Try using a cast to a super-type on the root",
						getDefinition().getChildren().getFilePosition().getPosition(0),
						getDefinition().getChildren().getExpression().getExpressionLength());
			}
			isLeaf = interpret(getDefinition().isLeaf(), ModelTypes.Value.BOOLEAN);
		}

		@Override
		public DynamicTreeModel<N> create() {
			return new DynamicTreeModel<>(getIdentity());
		}
	}

	private ModelComponentId theActivePathVariable;
	private ModelValueInstantiator<SettableValue<N>> theRootInstantiator;
	private ModelValueInstantiator<? extends ObservableCollection<? extends N>> theChildren;
	private ModelValueInstantiator<SettableValue<Boolean>> theLeafInstantiator;

	private SettableValue<SettableValue<N>> theRoot;
	private SettableValue<Boolean> isLeaf;
	private SettableValue<BetterList<N>> theActivePathValue;

	DynamicTreeModel(Object id) {
		super(id);
		theRoot = SettableValue.create();
	}

	@Override
	public SettableValue<N> getValue() {
		return SettableValue.flatten(theRoot);
	}

	@Override
	public ObservableCollection<? extends N> getChildren(ObservableValue<BetterList<N>> path, Observable<?> until)
		throws ModelInstantiationException {
		if (getParentElement() instanceof QuickWidget && PanelPopulation.isDebugging(//
			((QuickWidget) getParentElement()).getName().get(), "tree-model", "children"))
			BreakpointHere.breakpoint();
		ModelSetInstance nodeModel = QuickCoreInterpretation.copyModels(getUpdatingModels(), theActivePathVariable, until)//
			.build();
		SettableValue<BetterList<N>> pathV;
		if (path instanceof SettableValue && path.getThreadConstraint() == ThreadConstraint.NONE)
			pathV = (SettableValue<BetterList<N>>) path;
		else {
			String uModMsg = reporting().getFileLocation().getPosition(0).toShortString() + "." + theActivePathValue + " is not modifiable";
			pathV = SettableValue.asSettable(path, __ -> uModMsg);
		}
		ExFlexibleElementModelAddOn.satisfyElementValue(theActivePathVariable, nodeModel, pathV);
		return theChildren.get(nodeModel);
	}

	@Override
	public boolean isLeaf(BetterList<N> path) {
		if (isLeaf == null)
			return false;
		theActivePathValue.set(path, null);
		return isLeaf.get();
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);
		Interpreted<N> myInterpreted = (Interpreted<N>) interpreted;
		theActivePathVariable = myInterpreted.getDefinition().getActivePathVariable();

		theRootInstantiator = myInterpreted.getRoot().instantiate();
		theChildren = myInterpreted.getChildren().instantiate();
		theLeafInstantiator = myInterpreted.isLeaf() == null ? null : myInterpreted.isLeaf().instantiate();
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
		super.instantiated();
		theRootInstantiator.instantiate();
		theChildren.instantiate();
		if (theLeafInstantiator != null)
			theLeafInstantiator.instantiate();
	}

	@Override
	protected ModelSetInstance doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		myModels = super.doInstantiate(myModels);

		theActivePathValue = (SettableValue<BetterList<N>>) myModels.get(theActivePathVariable);

		theRoot.set(theRootInstantiator.get(myModels), null);
		isLeaf = theLeafInstantiator == null ? null : theLeafInstantiator.get(myModels);
		return myModels;
	}

	@Override
	public DynamicTreeModel<N> copy(ExElement parent) {
		DynamicTreeModel<N> copy = (DynamicTreeModel<N>) super.copy(parent);
		copy.theRoot = SettableValue.create();
		return copy;
	}
}
