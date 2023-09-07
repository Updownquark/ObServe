package org.observe.quick.base;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.ModelComponentId;
import org.observe.expresso.qonfig.ExElement;
import org.qommons.collect.BetterList;

import com.google.common.reflect.TypeToken;

public interface TreeModel<N> extends ExElement {
	public static final String TREE_MODEL = "tree-model";

	public interface TreeModelOwner extends ExElement {
		public interface Def<E extends TreeModelOwner> extends ExElement.Def<E> {
			ModelComponentId getActiveValueVariable();

			ModelComponentId getNodeVariable();
		}

		public interface Interpreted<E extends TreeModelOwner> extends ExElement.Interpreted<E> {
			@Override
			Def<? super E> getDefinition();
		}
	}

	public interface Def<M extends TreeModel<?>> extends ExElement.Def<M> {
		@Override
		TreeModelOwner.Def<?> getParentElement();

		<N> Interpreted<N, ? extends M> interpret(TreeModelOwner.Interpreted<?> parent);
	}

	public interface Interpreted<N, M extends TreeModel<N>> extends ExElement.Interpreted<M> {
		@Override
		Def<? super M> getDefinition();

		@Override
		TreeModelOwner.Interpreted<?> getParentElement();

		TypeToken<N> getNodeType(InterpretedExpressoEnv env) throws ExpressoInterpretationException;

		void updateModel(InterpretedExpressoEnv env) throws ExpressoInterpretationException;

		M create();
	}

	@Override
	TreeModelOwner getParentElement();

	SettableValue<N> getValue();

	ObservableCollection<? extends N> getChildren(ObservableValue<BetterList<N>> path, Observable<?> until)
		throws ModelInstantiationException;

	boolean isLeaf(BetterList<N> path);

	@Override
	TreeModel<N> copy(ExElement parent);
}
