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
import org.observe.util.TypeTokens;
import org.qommons.collect.BetterList;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

public class DynamicTreeModel<N> extends ExElement.Abstract implements TreeModel<N> {
	public static final String DYNAMIC_TREE_MODEL = "dynamic-tree-model";

	@ExMultiElementTraceable({ //
		@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
			qonfigType = DYNAMIC_TREE_MODEL,
			interpretation = Interpreted.class,
			instance = DynamicTreeModel.class),
		@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = TreeModel.TREE_MODEL,
		interpretation = Interpreted.class,
		instance = DynamicTreeModel.class) })
	public static class Def extends ExElement.Def.Abstract<DynamicTreeModel<?>> implements TreeModel.Def<DynamicTreeModel<?>> {
		private CompiledExpression theRoot;
		private CompiledExpression theChildren;
		private CompiledExpression isLeaf;
		private ModelComponentId theDynamicNodeVariable;
		private ModelComponentId theDynamicPathVariable;

		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@QonfigAttributeGetter(asType = TreeModel.TREE_MODEL, value = "value")
		public CompiledExpression getRoot() {
			return theRoot;
		}

		@QonfigAttributeGetter(asType = DYNAMIC_TREE_MODEL, value = "children")
		public CompiledExpression getChildren() {
			return theChildren;
		}

		@QonfigAttributeGetter(asType = DYNAMIC_TREE_MODEL, value = "leaf")
		public CompiledExpression isLeaf() {
			return isLeaf;
		}

		@QonfigAttributeGetter(asType = DYNAMIC_TREE_MODEL, value = "dynamic-node-name")
		public ModelComponentId getDynamicNodeVariable() {
			return theDynamicNodeVariable;
		}

		@QonfigAttributeGetter(asType = DYNAMIC_TREE_MODEL, value = "dynamic-path-name")
		public ModelComponentId getDynamicPathVariable() {
			return theDynamicPathVariable;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			theRoot = getAttributeExpression("value", session);
			theChildren = getAttributeExpression("children", session);
			isLeaf = getAttributeExpression("leaf", session);
			ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
			String dynamicNodeName = session.getAttributeText("dynamic-node-name");
			if (dynamicNodeName != null) {
				theDynamicNodeVariable = elModels.getElementValueModelId(dynamicNodeName);
				elModels.satisfyElementValueType(theDynamicNodeVariable, ModelTypes.Value, //
					(interp, env) -> ModelTypes.Value.forType(((Interpreted<?>) interp).getNodeType(env)));
			}
			String dynamicPathName = session.getAttributeText("dynamic-path-name");
			if (dynamicPathName != null) {
				theDynamicPathVariable = elModels.getElementValueModelId(dynamicPathName);
				elModels.satisfyElementValueType(theDynamicPathVariable, ModelTypes.Value, //
					(interp, env) -> ModelTypes.Value.forType(((Interpreted<?>) interp).getPathType(env)));
			}
		}

		@Override
		public <N> Interpreted<N> interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}
	}

	public static class Interpreted<N> extends ExElement.Interpreted.Abstract<DynamicTreeModel<N>>
	implements TreeModel.Interpreted<N, DynamicTreeModel<N>> {
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
		public TypeToken<N> getNodeType(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			if (theNodeType == null) {
				theRoot = interpret(getDefinition().getRoot(), ModelTypes.Value.anyAs());
				theNodeType = (TypeToken<N>) theRoot.getType().getType(0);
			}
			return theNodeType;
		}

		public TypeToken<BetterList<N>> getPathType(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			return TypeTokens.get().keyFor(BetterList.class).<BetterList<N>> parameterized(getNodeType(env));
		}

		@Override
		public void updateModel(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			update(env);
		}

		@Override
		protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			super.doUpdate(env);

			getNodeType(env); // Initialize root
			theChildren = interpret(getDefinition().getChildren(), ModelTypes.Collection.forType(getNodeType(env)));
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

	private ModelComponentId theDynamicPathVariable;
	private ModelComponentId theDynamicNodeVariable;
	private TypeToken<N> theNodeType;
	private ModelValueInstantiator<SettableValue<N>> theRootInstantiator;
	private ModelValueInstantiator<? extends ObservableCollection<? extends N>> theChildren;
	private ModelValueInstantiator<SettableValue<Boolean>> theLeafInstantiator;

	private SettableValue<SettableValue<N>> theRoot;
	private SettableValue<Boolean> isLeaf;
	private SettableValue<N> theDynamicNodeValue;
	private SettableValue<BetterList<N>> theDynamicPathValue;

	public DynamicTreeModel(Object id) {
		super(id);
	}

	@Override
	public SettableValue<N> getValue() {
		return SettableValue.flatten(theRoot);
	}

	@Override
	public ObservableCollection<? extends N> getChildren(ObservableValue<BetterList<N>> path, Observable<?> until)
		throws ModelInstantiationException {
		ModelSetInstance nodeModel = getUpdatingModels().copy(until).build();
		SettableValue<BetterList<N>> pathV = SettableValue.asSettable(path, __ -> "Not Settable");
		SettableValue<N> nodeV = SettableValue.asSettable(path.map(theNodeType, p -> p == null ? null : p.peekLast()),
			__ -> "Not Settable");
		if (theDynamicPathVariable != null)
			ExFlexibleElementModelAddOn.satisfyElementValue(theDynamicPathVariable, nodeModel, pathV,
				ExFlexibleElementModelAddOn.ActionIfSatisfied.Replace);
		if (theDynamicNodeVariable != null)
			ExFlexibleElementModelAddOn.satisfyElementValue(theDynamicNodeVariable, nodeModel, nodeV,
				ExFlexibleElementModelAddOn.ActionIfSatisfied.Replace);
		return theChildren.get(nodeModel);
	}

	@Override
	public boolean isLeaf(BetterList<N> path) {
		if (theDynamicPathValue != null)
			theDynamicPathValue.set(path, null);
		if (theDynamicNodeValue != null)
			theDynamicNodeValue.set(path.getLast(), null);
		return isLeaf != null && isLeaf.get();
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) {
		super.doUpdate(interpreted);
		Interpreted<N> myInterpreted = (Interpreted<N>) interpreted;
		theDynamicPathVariable = myInterpreted.getDefinition().getDynamicPathVariable();
		theDynamicNodeVariable = myInterpreted.getDefinition().getDynamicNodeVariable();

		theRootInstantiator = myInterpreted.getRoot().instantiate();
		theChildren = myInterpreted.getChildren().instantiate();
		theLeafInstantiator = myInterpreted.isLeaf() == null ? null : myInterpreted.isLeaf().instantiate();

		TypeToken<N> nodeType;
		try {
			nodeType = myInterpreted.getNodeType(null);
		} catch (ExpressoInterpretationException e) {
			throw new IllegalStateException("Not evaluated?", e);
		}
		if (theRoot == null || !theNodeType.equals(nodeType)) {
			theNodeType = nodeType;
			theRoot = SettableValue.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<N>> parameterized(nodeType)).build();
			theDynamicPathValue = theDynamicPathVariable == null ? null
				: SettableValue.build(TypeTokens.get().keyFor(BetterList.class).<BetterList<N>> parameterized(theNodeType))
				.withDescription("dynamicPath").build();
			theDynamicNodeValue = theDynamicNodeVariable == null ? null : SettableValue.build(theNodeType)
				.withValue(TypeTokens.get().getDefaultValue(theNodeType)).withDescription("dynamicNode").build();
		}
	}

	@Override
	public void instantiated() {
		super.instantiated();
		theRootInstantiator.instantiate();
		theChildren.instantiate();
		if (theLeafInstantiator != null)
			theLeafInstantiator.instantiate();
	}

	@Override
	protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		super.doInstantiate(myModels);

		if (theDynamicPathVariable != null)
			ExFlexibleElementModelAddOn.satisfyElementValue(theDynamicPathVariable, myModels, theDynamicPathValue);
		if (theDynamicNodeVariable != null)
			ExFlexibleElementModelAddOn.satisfyElementValue(theDynamicNodeVariable, myModels, theDynamicNodeValue);

		theRoot.set(theRootInstantiator.get(myModels), null);
		isLeaf = theLeafInstantiator == null ? null : theLeafInstantiator.get(myModels);
	}

	@Override
	public DynamicTreeModel<N> copy(ExElement parent) {
		DynamicTreeModel<N> copy = (DynamicTreeModel<N>) super.copy(parent);
		copy.theRoot = SettableValue.build(theRoot.getType()).build();
		copy.theDynamicPathValue = theDynamicPathVariable == null ? null
			: SettableValue.build(TypeTokens.get().keyFor(BetterList.class).<BetterList<N>> parameterized(theNodeType))
			.withDescription("dynamicPath").build();
		copy.theDynamicNodeValue = theDynamicNodeVariable == null ? null : SettableValue.build(theNodeType)
			.withValue(TypeTokens.get().getDefaultValue(theNodeType)).withDescription("dynamicNode").build();
		return copy;
	}
}
