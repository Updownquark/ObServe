package org.observe.quick.base;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.qonfig.ExElement;
import org.qommons.collect.BetterList;

import com.google.common.reflect.TypeToken;

public interface TreeModel<N> extends ExElement {
	public static final String TREE_MODEL = "tree-model";

	public interface Def<M extends TreeModel<?>> extends ExElement.Def<M> {
		<N> Interpreted<N, ? extends M> interpret(ExElement.Interpreted<?> parent);
	}

	public interface Interpreted<N, M extends TreeModel<N>> extends ExElement.Interpreted<M> {
		@Override
		Def<? super M> getDefinition();

		TypeToken<? extends N> getNodeType(InterpretedExpressoEnv env) throws ExpressoInterpretationException;

		void updateModel(InterpretedExpressoEnv env) throws ExpressoInterpretationException;

		M create();
	}

	SettableValue<N> getValue();

	ObservableCollection<? extends N> getChildren(ObservableValue<BetterList<N>> path, Observable<?> until)
		throws ModelInstantiationException;

	boolean isLeaf(BetterList<N> path);

	@Override
	TreeModel<N> copy(ExElement parent);
}
