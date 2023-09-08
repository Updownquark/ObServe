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

public class StaticTreeNode<N> extends ExElement.Abstract implements TreeModel<N> {
	public static final String TREE_NODE = "tree-node";

	@ExMultiElementTraceable({ //
		@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
			qonfigType = TREE_NODE,
			interpretation = Interpreted.class,
			instance = StaticTreeNode.class),
		@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = TreeModel.TREE_MODEL,
		interpretation = Interpreted.class,
		instance = StaticTreeNode.class) })
	public static class Def<TN extends StaticTreeNode<?>> extends ExElement.Def.Abstract<TN> implements TreeModel.Def<TN> {
		private CompiledExpression theValue;
		private final List<TreeModel.Def<?>> theChildren;

		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
			theChildren = new ArrayList<>();
		}

		@QonfigAttributeGetter(asType = TreeModel.TREE_MODEL, value = "value")
		public CompiledExpression getValue() {
			return theValue;
		}

		@QonfigChildGetter(asType = TREE_NODE, value = "child")
		public List<TreeModel.Def<?>> getChildren() {
			return Collections.unmodifiableList(theChildren);
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			theValue = session.getAttributeExpression("value");
			ExElement.syncDefs(TreeModel.Def.class, theChildren, session.forChildren("child"));
		}

		@Override
		public <N> Interpreted<N, ? extends TN> interpret(ExElement.Interpreted<?> parent) {
			return (Interpreted<N, ? extends TN>) new Interpreted<>((Def<StaticTreeNode<Object>>) this, parent);
		}
	}

	public static class Interpreted<N, TN extends StaticTreeNode<N>> extends ExElement.Interpreted.Abstract<TN>
	implements TreeModel.Interpreted<N, TN> {
		private InterpretedValueSynth<SettableValue<?>, ? extends SettableValue<? extends N>> theValue;
		private List<TreeModel.Interpreted<? extends N, ?>> theChildren;
		private TypeToken<N> theNodeType;

		Interpreted(Def<? super TN> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
			theChildren = new ArrayList<>();
		}

		@Override
		public Def<? super TN> getDefinition() {
			return (Def<? super TN>) super.getDefinition();
		}

		public InterpretedValueSynth<SettableValue<?>, ? extends SettableValue<? extends N>> getValue() {
			return theValue;
		}

		public List<TreeModel.Interpreted<? extends N, ?>> getChildren() {
			return Collections.unmodifiableList(theChildren);
		}

		@Override
		public TypeToken<N> getNodeType(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			if (theNodeType == null) {
				// This should be safe. The interpretation of the children here shouldn't need anything from the environment.
				CollectionUtils.synchronize(theChildren, getDefinition().getChildren(), (i, d) -> i.getIdentity() == d.getIdentity())
				.<ExpressoInterpretationException> simpleE(def -> def.interpret(this))//
				.onLeftX(el -> el.getLeftValue().destroy())//
				.rightOrder()//
				.adjust();
				List<TypeToken<? extends N>> types = new ArrayList<>(theChildren.size() + 1);
				theValue = getDefinition().getValue().interpret(ModelTypes.Value.anyAs(), env);
				types.add((TypeToken<? extends N>) theValue.getType().getType(0));
				for (TreeModel.Interpreted<? extends N, ?> child : theChildren)
					types.add(child.getNodeType(env));
				theNodeType = TypeTokens.get().getCommonType(types);
			}
			return theNodeType;
		}

		@Override
		public void updateModel(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			update(env);
		}

		@Override
		protected void doUpdate(InterpretedExpressoEnv expressoEnv) throws ExpressoInterpretationException {
			super.doUpdate(expressoEnv);
			getNodeType(expressoEnv); // Init value
			for (int c = 0; c < theChildren.size(); c++)
				theChildren.get(c).updateModel(expressoEnv);
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

	StaticTreeNode(Object id) {
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
	protected void doUpdate(ExElement.Interpreted<?> interpreted) {
		super.doUpdate(interpreted);
		Interpreted<N, ?> myInterpreted = (Interpreted<N, ?>) interpreted;

		theValueInstantiator = myInterpreted.getValue().instantiate();

		TypeToken<N> nodeType;
		try {
			nodeType = myInterpreted.getNodeType(null);
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
		.simpleE(interp -> interp.create())//
		.onLeft(el -> el.getLeftValue().destroy())//
		.onRight(el -> el.getLeftValue().update(el.getRightValue(), getParentElement()))
		.onCommon(el -> el.getLeftValue().update(el.getRightValue(), getParentElement())).rightOrder()//
		.adjust();
		theInitializedChildren.addAll(theChildren);
	}

	@Override
	public void instantiated() {
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
