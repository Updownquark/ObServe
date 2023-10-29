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
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.CompiledModelValue;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelComponentId;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExModelAugmentation;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.util.TypeTokens;
import org.qommons.Transaction;
import org.qommons.collect.BetterList;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

public interface TreeModel<N> extends ExElement {
	public static final String TREE_MODEL = "tree-model";

	public interface Def<M extends TreeModel<?>> extends ExElement.Def<M> {
		void update(ExpressoQIS session, String activePath, String activeNode) throws QonfigInterpretationException;

		ModelComponentId getActivePathVariable();

		ModelComponentId getActiveNodeVariable();

		<N> Interpreted<N, ? extends M> interpret(ExElement.Interpreted<?> parent);

		public static abstract class Abstract<M extends TreeModel<?>> extends ExElement.Def.Abstract<M> implements Def<M> {
			private ModelComponentId theActivePathVariable;
			private ModelComponentId theActiveNodeVariable;
			private Interpreted<?, ?> theCurrentInterpreting;

			protected Abstract(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
				super(parent, qonfigType);
			}

			@Override
			public void update(ExpressoQIS session, String activePath, String activeNode) throws QonfigInterpretationException {
				ObservableModelSet.Builder builder = ExModelAugmentation.augmentElementModel(session.getExpressoEnv().getModels(), this);
				boolean newBuilder = builder != session.getExpressoEnv().getModels();
				if (newBuilder)
					session.setExpressoEnv(session.getExpressoEnv().with(builder));
				builder.withMaker(activePath,
					new CompiledTreeModelValue(activePath){
					@Override
					protected <N, T> TypeToken<T> getValueType(Interpreted<N, ?> interpreted, InterpretedExpressoEnv env)
						throws ExpressoInterpretationException {
						return (TypeToken<T>) TypeTokens.get().keyFor(BetterList.class).parameterized(interpreted.getNodeType(env));
					}
				}, null);
				builder.withMaker(activeNode, new CompiledTreeModelValue(activePath) {
					@Override
					protected <N, T> TypeToken<T> getValueType(Interpreted<N, ?> interpreted, InterpretedExpressoEnv env)
						throws ExpressoInterpretationException {
						return (TypeToken<T>) interpreted.getNodeType(env);
					}
				}, null);
				theActivePathVariable = builder.getLocalComponent(activePath).getIdentity();
				theActiveNodeVariable = builder.getLocalComponent(activeNode).getIdentity();
				update(session);
				if (newBuilder && getExpressoEnv().getModels() == builder) {
					setExpressoEnv(getExpressoEnv().with(builder.build()));
					session.setExpressoEnv(getExpressoEnv());
				}
			}

			@Override
			public ModelComponentId getActivePathVariable() {
				return theActivePathVariable;
			}

			@Override
			public ModelComponentId getActiveNodeVariable() {
				return theActiveNodeVariable;
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

	public interface Interpreted<N, M extends TreeModel<N>> extends ExElement.Interpreted<M> {
		@Override
		Def<? super M> getDefinition();

		TypeToken<? extends N> getNodeType(InterpretedExpressoEnv env) throws ExpressoInterpretationException;

		void updateModel(InterpretedExpressoEnv env) throws ExpressoInterpretationException;

		M create();

		public static abstract class Abstract<N, M extends TreeModel<N>> extends ExElement.Interpreted.Abstract<M>
		implements Interpreted<N, M> {
			protected Abstract(Def.Abstract<? super M> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public Def.Abstract<? super M> getDefinition() {
				return (Def.Abstract<? super M>) super.getDefinition();
			}

			@Override
			public TypeToken<? extends N> getNodeType(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				try (Transaction t = getDefinition().interpreting(this)) {
					return doGetNodeType(env);
				}
			}

			protected abstract TypeToken<? extends N> doGetNodeType(InterpretedExpressoEnv env) throws ExpressoInterpretationException;

			@Override
			public void updateModel(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				try (Transaction t = getDefinition().interpreting(this)) {
					update(env);
				}
			}
		}
	}

	SettableValue<N> getValue();

	ObservableCollection<? extends N> getChildren(ObservableValue<BetterList<N>> path, Observable<?> until)
		throws ModelInstantiationException;

	boolean isLeaf(BetterList<N> path);

	@Override
	TreeModel<N> copy(ExElement parent);
}
