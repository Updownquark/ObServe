package org.observe.quick.base;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.assoc.ObservableMap;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExMultiElementTraceable;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.expresso.qonfig.QonfigChildGetter;
import org.observe.util.TypeTokens;
import org.qommons.collect.BetterList;
import org.qommons.collect.CollectionUtils;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

/**
 * A static tree node in a {@link QuickTree}. This class is a {@link TreeModel}. Its contents are other {@link TreeModel}s, and its content
 * list itself is static, but the tree models that are its contents may be dynamic.
 *
 * @param <N> The type of value in this tree node
 */
public class StaticTreeNode<N> extends ExElement.Abstract implements TreeModel<N> {
	/** The XML name of this element */
	public static final String TREE_NODE = "tree-node";

	/**
	 * {@link StaticTreeNode} definition
	 *
	 * @param <TN> The sub-type of tree node to create
	 */
	@ExMultiElementTraceable({ //
		@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
			qonfigType = TREE_NODE,
			interpretation = Interpreted.class,
			instance = StaticTreeNode.class),
		@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = TreeModel.TREE_MODEL,
		interpretation = Interpreted.class,
		instance = StaticTreeNode.class) })
	public static class Def<TN extends StaticTreeNode<?>> extends TreeModel.Def.Abstract<TN> {
		private CompiledExpression theValue;
		private final List<TreeModel.Def<?>> theChildren;

		/**
		 * @param parent The parent element of the node
		 * @param qonfigType The Qonfig type of the node
		 */
		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
			theChildren = new ArrayList<>();
		}

		/** @return The value of the node */
		@QonfigAttributeGetter(asType = TreeModel.TREE_MODEL, value = "value")
		public CompiledExpression getValue() {
			return theValue;
		}

		/** @return The children of the node */
		@QonfigChildGetter(asType = TREE_NODE, value = "child")
		public List<TreeModel.Def<?>> getChildren() {
			return Collections.unmodifiableList(theChildren);
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			theValue = getAttributeExpression("value", session);
			syncChildren(TreeModel.Def.class, theChildren, session.forChildren("child"),
				(child, s) -> child.update(s, getActivePathVariable().getName(), getActiveNodeVariable().getName()));
		}

		@Override
		public <N> Interpreted<N, ? extends TN> interpret(ExElement.Interpreted<?> parent) {
			return (Interpreted<N, ? extends TN>) new Interpreted<>((Def<StaticTreeNode<Object>>) this, parent);
		}
	}

	/**
	 * {@link StaticTreeNode} interpretation
	 *
	 * @param <N> The type of value in the tree node
	 * @param <TN> The sub-type of tree node to create
	 */
	public static class Interpreted<N, TN extends StaticTreeNode<N>> extends TreeModel.Interpreted.Abstract<N, TN> {
		private InterpretedValueSynth<SettableValue<?>, ? extends SettableValue<? extends N>> theValue;
		private List<TreeModel.Interpreted<? extends N, ?>> theChildren;
		private TypeToken<N> theNodeType;

		/**
		 * @param definition The definition to interpret
		 * @param parent The parent element for the node
		 */
		protected Interpreted(Def<? super TN> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
			theChildren = new ArrayList<>();
		}

		@Override
		public Def<? super TN> getDefinition() {
			return (Def<? super TN>) super.getDefinition();
		}

		/** @return The value of the node */
		public InterpretedValueSynth<SettableValue<?>, ? extends SettableValue<? extends N>> getValue() {
			return theValue;
		}

		/** @return The children of the node */
		public List<TreeModel.Interpreted<? extends N, ?>> getChildren() {
			return Collections.unmodifiableList(theChildren);
		}

		@Override
		protected TypeToken<N> doGetNodeType(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			if (theNodeType == null) {
				// This should be safe. The interpretation of the children here shouldn't need anything from the environment.
				syncChildren(getDefinition().getChildren(), theChildren, def -> def.interpret(this), null);
				List<TypeToken<? extends N>> types = new ArrayList<>(theChildren.size() + 1);
				if (getExpressoEnv() != null)
					theValue = interpret(getDefinition().getValue(), ModelTypes.Value.anyAs());
				else
					theValue = getDefinition().getValue().interpret(ModelTypes.Value.anyAsV(), env);
				types.add((TypeToken<? extends N>) theValue.getType().getType(0));
				for (TreeModel.Interpreted<? extends N, ?> child : theChildren)
					types.add(child.getNodeType(env));
				theNodeType = TypeTokens.get().getCommonType(types);
			}
			return theNodeType;
		}

		@Override
		protected void doUpdate(InterpretedExpressoEnv expressoEnv) throws ExpressoInterpretationException {
			super.doUpdate(expressoEnv);
			getNodeType(expressoEnv); // Init value
			// We didn't actually update the children, just interpreted them to get the node type
			for (TreeModel.Interpreted<?, ?> child : theChildren)
				child.updateModel(getExpressoEnv());
		}

		@Override
		public TN create() {
			return (TN) new StaticTreeNode<>(getIdentity());
		}
	}

	private TypeToken<N> theNodeType;
	private ModelValueInstantiator<? extends SettableValue<? extends N>> theValueInstantiator;

	private SettableValue<SettableValue<N>> theValue;
	private List<TreeModel<? extends N>> theChildren;
	private ObservableCollection<TreeModel<? extends N>> theInitializedChildren;
	// I'd rather just use the key set of the map below, but it doesn't seem to preserve order
	private ObservableCollection<N> theChildValues;
	private ObservableMap<N, TreeModel<? extends N>> theChildrenByValue;

	/** @param id The element ID for this node */
	protected StaticTreeNode(Object id) {
		super(id);
		theChildren = new ArrayList<>();
	}

	private void initChildren() {
		theInitializedChildren = ObservableCollection.build((Class<TreeModel<? extends N>>) (Class<?>) TreeModel.class).build();
		theChildValues = theInitializedChildren.flow()//
			.flattenValues(theNodeType, model -> model.getValue())//
			.collectActive(isDestroyed().noInitChanges());
		theChildrenByValue = theInitializedChildren.flow()//
			.refreshEach(model -> model.getValue().noInitChanges())//
			.groupBy(theNodeType, model -> model.getValue().get(), null)//
			.gatherActive(isDestroyed().noInitChanges())//
			.singleMap(false);
	}

	@Override
	public SettableValue<N> getValue() {
		return SettableValue.flatten(theValue);
	}

	@Override
	public ObservableCollection<? extends N> getChildren(ObservableValue<BetterList<N>> path, Observable<?> until)
		throws ModelInstantiationException {
		BetterList<N> pathV = path.get();
		if (pathV.size() == 1)
			return theChildValues;
		N key = pathV.get(1);
		TreeModel<? extends N> child = theChildrenByValue.get(key);
		if (child == null) {
			reporting().error("Asked for child of " + key + " but not found here");
			return ObservableCollection.of(theNodeType);
		}
		return ((TreeModel<N>) child).getChildren(path.map(path.getType(), p -> p.subList(1, p.size())), until);
	}

	@Override
	public boolean isLeaf(BetterList<N> path) {
		if (path.size() == 1)
			return theChildren.isEmpty();
		N key = path.get(1);
		TreeModel<? extends N> child = theChildrenByValue.get(key);
		if (child == null) {
			reporting().error("Asked for child of " + key + " but not found here");
			return true;
		}
		return ((TreeModel<N>) child).isLeaf(path.subList(1, path.size()));
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);
		Interpreted<N, ?> myInterpreted = (Interpreted<N, ?>) interpreted;

		theValueInstantiator = myInterpreted.getValue().instantiate();

		TypeToken<N> nodeType;
		try {
			nodeType = (TypeToken<N>) myInterpreted.getNodeType(null);
		} catch (ExpressoInterpretationException e) {
			throw new IllegalStateException("Not evaluated?", e);
		}
		if (theValue == null || !theNodeType.equals(nodeType)) {
			theNodeType = nodeType;
			theValue = SettableValue.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<N>> parameterized(nodeType)).build();
		}
		if (theInitializedChildren == null)
			initChildren();
		theInitializedChildren.clear();
		CollectionUtils.synchronize(theChildren, myInterpreted.getChildren(), (inst, interp) -> inst.getIdentity() == interp.getIdentity())//
		.<ModelInstantiationException> simpleX(interp -> interp.create())//
		.onLeft(el -> el.getLeftValue().destroy())//
		.onRightX(el -> el.getLeftValue().update(el.getRightValue(), getParentElement()))
		.onCommonX(el -> el.getLeftValue().update(el.getRightValue(), getParentElement())).rightOrder()//
		.adjust();
		theInitializedChildren.addAll(theChildren);
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
		super.instantiated();
		for (TreeModel<? extends N> child : theInitializedChildren)
			child.instantiated();
		theValueInstantiator.instantiate();
	}

	@Override
	protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		super.doInstantiate(myModels);

		theValue.set((SettableValue<N>) theValueInstantiator.get(myModels), null);

		for (TreeModel<? extends N> child : theInitializedChildren)
			child.instantiate(myModels);
	}

	@Override
	public StaticTreeNode<N> copy(ExElement parent) {
		StaticTreeNode<N> copy = (StaticTreeNode<N>) super.copy(parent);
		copy.theValue = SettableValue.build(theValue.getType()).build();
		copy.theChildren = new ArrayList<>();
		copy.initChildren();
		for (TreeModel<? extends N> child : theChildren)
			copy.theChildren.add(child.copy(copy));
		return copy;
	}
}
