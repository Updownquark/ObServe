package org.observe.quick.base;

import java.util.Collections;
import java.util.List;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.CompiledExpressoEnv;
import org.observe.expresso.ExpressoCompilationException;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.CompiledModelValue;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelComponentId;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.qommons.Transaction;
import org.qommons.collect.BetterList;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

/**
 * A model for a tree widget
 *
 * @param <N> The type of the nodes in the tree
 */
public interface TreeModel<N> extends ExElement {
	/** The XML name of this element */
	public static final String TREE_MODEL = "tree-model";

	/**
	 * {@link TreeModel} definition
	 *
	 * @param <M> The sub-type of model to create
	 */
	public interface Def<M extends TreeModel<?>> extends ExElement.Def<M> {
		/** @return The ID of the variable where the active node path will be available to expressions */
		ModelComponentId getActivePathVariable();

		/**
		 * @param <N> The type of node in the tree
		 * @param parent The parent element for the interpreted model
		 * @return The interpreted model
		 */
		<N> Interpreted<N, ? extends M> interpret(ExElement.Interpreted<?> parent);

		/**
		 * Abstract {@link TreeModel} definition implementation
		 *
		 * @param <M> The sub-type of model to create
		 */
		public static abstract class Abstract<M extends TreeModel<?>> extends ExElement.Def.Abstract<M> implements Def<M> {
			private ModelComponentId theActivePathVariable;
			private Interpreted<?, ?> theCurrentInterpreting;

			/**
			 * @param parent The parent of this element
			 * @param qonfigType The Qonfig type of this element
			 */
			protected Abstract(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
				super(parent, qonfigType);
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				super.doUpdate(session);
				ExElement.Def<?> parent = getParentElement();
				while (parent != null && !(parent instanceof MultiValueRenderable.Def)) {
					parent = parent.getParentElement();
				}
				if (parent == null)
					throw new QonfigInterpretationException("This class must be used as a descendant of a <multi-value-renderable>",
						reporting().getFileLocation());
				theActivePathVariable = ((MultiValueRenderable.Def<?>) parent).getActiveValueVariable();
			}

			@Override
			public ModelComponentId getActivePathVariable() {
				return theActivePathVariable;
			}

			@Override
			public abstract <N> Interpreted.Abstract<N, ? extends M> interpret(ExElement.Interpreted<?> parent);

			Transaction interpreting(Interpreted<?, ?> interpreted) {
				Interpreted<?, ?> old = theCurrentInterpreting;
				theCurrentInterpreting = interpreted;
				return () -> theCurrentInterpreting = old;
			}

			abstract class CompiledTreeModelValue implements CompiledModelValue<SettableValue<?>> {
				private final String theName;

				CompiledTreeModelValue(String name){
					theName=name;
				}

				protected abstract <N, T> TypeToken<T> getValueType(Interpreted<N, ?> interpreted, InterpretedExpressoEnv env)
					throws ExpressoInterpretationException;

				@Override
				public ModelType<SettableValue<?>> getModelType(CompiledExpressoEnv env) throws ExpressoCompilationException {
					return ModelTypes.Value;
				}

				@Override
				public InterpretedValueSynth<SettableValue<?>, ?> interpret(InterpretedExpressoEnv env)
					throws ExpressoInterpretationException {
					return new InterpretedTreeModelValue<>(theName, getValueType(theCurrentInterpreting, env));
				}
			}

			static class InterpretedTreeModelValue<T> implements InterpretedValueSynth<SettableValue<?>, SettableValue<T>> {
				private final String theName;
				private final ModelInstanceType<SettableValue<?>, SettableValue<T>> theType;

				InterpretedTreeModelValue(String name, TypeToken<T> valueType) {
					theName=name;
					theType = ModelTypes.Value.forType(valueType);
				}

				@Override
				public ModelInstanceType<SettableValue<?>, SettableValue<T>> getType() {
					return theType;
				}

				@Override
				public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
					return Collections.emptyList();
				}

				@Override
				public ModelValueInstantiator<SettableValue<T>> instantiate() {
					return ModelValueInstantiator.of(msi -> (SettableValue<T>) ModelTypes.Value.createHollowValue(theName, theType));
				}
			}
		}
	}

	/**
	 * {@link TreeModel} definition
	 *
	 * @param <N> The type of the nodes in the tree
	 * @param <M> The sub-type of model to create
	 */
	public interface Interpreted<N, M extends TreeModel<N>> extends ExElement.Interpreted<M> {
		@Override
		Def<? super M> getDefinition();

		/**
		 * @return The type of nodes in the tree
		 * @throws ExpressoInterpretationException If the node type could not be interpreted
		 */
		TypeToken<? extends N> getNodeType() throws ExpressoInterpretationException;

		/**
		 * Initializes or updates this model
		 *
		 * @throws ExpressoInterpretationException If this model could not be interpreted
		 */
		void updateModel() throws ExpressoInterpretationException;

		/** @return The tree model instance */
		M create();

		/**
		 * Abstract {@link TreeModel} interpretation implementation
		 *
		 * @param <N> The type of nodes in the tree
		 * @param <M> The sub-type of model to create
		 */
		public static abstract class Abstract<N, M extends TreeModel<N>> extends ExElement.Interpreted.Abstract<M>
		implements Interpreted<N, M> {
			/**
			 * @param definition The definition to interpret
			 * @param parent The parent of this element
			 */
			protected Abstract(Def.Abstract<? super M> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public Def.Abstract<? super M> getDefinition() {
				return (Def.Abstract<? super M>) super.getDefinition();
			}

			@Override
			public TypeToken<? extends N> getNodeType() throws ExpressoInterpretationException {
				try (Transaction t = getDefinition().interpreting(this)) {
					return doGetNodeType();
				}
			}

			/**
			 * @return The type of nodes in the tree
			 * @throws ExpressoInterpretationException If the node type could not be interpreted
			 */
			protected abstract TypeToken<? extends N> doGetNodeType() throws ExpressoInterpretationException;

			@Override
			public void updateModel() throws ExpressoInterpretationException {
				try (Transaction t = getDefinition().interpreting(this)) {
					update();
				}
			}
		}
	}

	/** @return The current node value (e.g. the one hovered one) */
	SettableValue<N> getValue();

	/**
	 * @param path The node path to get the children for
	 * @param until The observable that will fire when the caller no longer cares about the result (e.g. when the user collapses the parent
	 *        node)
	 * @return The children of the given node
	 * @throws ModelInstantiationException If the children could not be instantiated
	 */
	ObservableCollection<? extends N> getChildren(ObservableValue<BetterList<N>> path, Observable<?> until)
		throws ModelInstantiationException;

	/**
	 * @param path The node path to determine if it is a leaf
	 * @return Whether the given node is a leaf node
	 */
	boolean isLeaf(BetterList<N> path);

	@Override
	TreeModel<N> copy(ExElement parent);
}
