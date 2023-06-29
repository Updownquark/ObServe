package org.observe.expresso.qonfig;

import java.util.function.Supplier;

import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.CompiledModelValue;
import org.observe.expresso.ObservableModelSet.InterpretedModelSet;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueSynth;
import org.qommons.Version;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.ex.ExSupplier;

public interface ModelValueElement<M, MV extends M> extends ExElement {
	public static final ExElement.AttributeValueGetter.Expression<ModelValueElement<?, ?>, Interpreted<?, ?, ?>, Def<?, ?>, Object, Object> MODEL_VALUE//
	= ExElement.AttributeValueGetter.<ModelValueElement<?, ?>, Interpreted<?, ?, ?>, Def<?, ?>, Object, Object> ofX(Def::getValue,
		i -> (InterpretedValueSynth<Object, Object>) i.getValue(), ModelValueElement::getValue);

	public interface Def<M, E extends ModelValueElement<M, ?>> extends ExElement.Def<E> {
		ModelType<M> getModelType();

		CompiledExpression getValue();

		Interpreted<M, ?, ? extends E> interpret(ObservableModelElement.Interpreted<?> modelElement, InterpretedModelSet models)
			throws ExpressoInterpretationException;

		public abstract class Simple<M, E extends ModelValueElement<M, ?>> extends ExElement.Def.Abstract<E> implements Def<M, E> {
			private final String theToolkitName;
			private final Version theToolkitVersion;
			private final String theTypeName;
			private final ModelType<M> theModelType;
			private CompiledExpression theValue;

			protected Simple(ExElement.Def<?> parent, QonfigElement element, ModelType<M> modelType, String toolkitName,
				Version toolkitVersion, String typeName) {
				super(parent, element);
				theToolkitName = toolkitName;
				theToolkitVersion = toolkitVersion;
				theTypeName = typeName;
				theModelType = modelType;
			}

			@Override
			public ModelType<M> getModelType() {
				return theModelType;
			}

			protected abstract ModelInstanceType<M, ?> getType(ObservableModelSet models) throws ExpressoInterpretationException;

			@Override
			public CompiledExpression getValue() {
				return theValue;
			}

			@Override
			public void update(ExpressoQIS session) throws QonfigInterpretationException {
				ExElement.checkElement(session.getFocusType(), theToolkitName, theToolkitVersion, theTypeName);
				forValue(MODEL_VALUE);
				super.update(session.asElement(session.getFocusType().getSuperElement()));
				theValue = session.getValueExpression();
			}

			@Override
			public Interpreted<M, ?, ? extends E> interpret(ObservableModelElement.Interpreted<?> modelElement, InterpretedModelSet models)
				throws ExpressoInterpretationException {
				return (Interpreted<M, ?, ? extends E>) new Interpreted.Default<>((Def<M, ModelValueElement<M, M>>) this, modelElement,
					(ModelInstanceType<M, M>) getType(models));
			}
		}
	}

	public interface CompiledModelValueElement<M, MV extends M, E extends ModelValueElement<M, MV>>
	extends Def<M, E>, CompiledModelValue<M, MV> {
		/**
		 * @param <M> The model type of the new value
		 * @param <MV> The type of the new value
		 * @param name The name of the value (for {@link Object#toString()})
		 * @param modelType The type of the new value
		 * @param synth The function to create the value synthesizer
		 * @return The new compiled model value
		 */
		static <M, MV extends M> CompiledModelValueElement<M, MV, ModelValueElement<M, MV>> of(ExElement.Def<?> parent,
			QonfigElement element, String toolkitName, Version toolkitVersion, String typeName, ModelType<M> modelType,
			Supplier<String> name, ExSupplier<ModelValueSynth<M, MV>, ExpressoInterpretationException> synth) {
			return new Default<>(parent, element, modelType, toolkitName, toolkitVersion, typeName, name, synth);
		}

		public class Default<M, MV extends M> extends Def.Simple<M, ModelValueElement<M, MV>>
		implements CompiledModelValueElement<M, MV, ModelValueElement<M, MV>> {
			private final Supplier<String> theName;
			private final ExSupplier<ModelValueSynth<M, MV>, ExpressoInterpretationException> theSynth;

			public Default(ExElement.Def<?> parent, QonfigElement element, ModelType<M> modelType, String toolkitName,
				Version toolkitVersion, String typeName, Supplier<String> name,
				ExSupplier<ModelValueSynth<M, MV>, ExpressoInterpretationException> synth) {
				super(parent, element, modelType, toolkitName, toolkitVersion, typeName);
				theName = name;
				theSynth = synth;
			}

			@Override
			protected ModelInstanceType<M, MV> getType(ObservableModelSet models) throws ExpressoInterpretationException {
				return theSynth.get().getType();
			}

			@Override
			public ModelValueSynth<M, MV> createSynthesizer() throws ExpressoInterpretationException {
				return theSynth.get();
			}

			@Override
			public String toString() {
				return theName.get();
			}
		}
	}

	public interface Interpreted<M, MV extends M, E extends ModelValueElement<M, MV>> extends ExElement.Interpreted<E> {
		@Override
		Def<M, ? super E> getDefinition();

		ModelInstanceType<M, MV> getValueType();

		InterpretedValueSynth<M, MV> getValue();

		void update() throws ExpressoInterpretationException;

		E create(ObservableModelElement modelElement, ModelSetInstance models) throws ModelInstantiationException;

		public class Default<M, MV extends M, E extends ModelValueElement<M, MV>> extends ExElement.Interpreted.Abstract<E>
		implements Interpreted<M, MV, E> {
			private final ModelInstanceType<M, MV> theValueType;
			private InterpretedValueSynth<M, MV> theValue;

			protected Default(Def<M, ? super E> definition, ExElement.Interpreted<?> parent, ModelInstanceType<M, MV> valueType)
				throws ExpressoInterpretationException {
				super(definition, parent);
				theValueType = valueType;
			}

			@Override
			public Def<M, ? super E> getDefinition() {
				return (Def<M, ? super E>) super.getDefinition();
			}

			@Override
			public ModelInstanceType<M, MV> getValueType() {
				return theValueType;
			}

			@Override
			public InterpretedValueSynth<M, MV> getValue() {
				return theValue;
			}

			@Override
			public void update() throws ExpressoInterpretationException {
				super.update();
				theValue = getDefinition().getValue() == null ? null : getDefinition().getValue().evaluate(getValueType()).interpret();
			}

			@Override
			public E create(ObservableModelElement modelElement, ModelSetInstance models) throws ModelInstantiationException {
				return (E) new ModelValueElement.Default<>(this, modelElement);
			}
		}
	}

	MV getValue();

	public class Default<M, MV extends M> extends ExElement.Abstract implements ModelValueElement<M, MV> {
		private MV theValue;

		public Default(ModelValueElement.Interpreted<M, MV, ?> interpreted, ExElement parent) {
			super(interpreted, parent);
		}

		@Override
		public MV getValue() {
			return theValue;
		}

		@Override
		protected void updateModel(ExElement.Interpreted<?> interpreted, ModelSetInstance myModels) throws ModelInstantiationException {
			super.updateModel(interpreted, myModels);
			ModelValueElement.Interpreted<M, MV, ?> myInterpreted = (ModelValueElement.Interpreted<M, MV, ?>) interpreted;
			theValue = myInterpreted.getValue() == null ? null : myInterpreted.getValue().get(myModels);
		}
	}
}
