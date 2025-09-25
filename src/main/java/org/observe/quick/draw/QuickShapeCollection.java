package org.observe.quick.draw;

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
import org.observe.expresso.qonfig.ExWithElementModel;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

public class QuickShapeCollection<T> extends ExElement.Abstract implements QuickShapePublisher {
	public static final String SHAPE_COLLECTION = "shape-collection";

	@ExElementTraceable(toolkit = QuickDrawInterpretation.DRAW,
		qonfigType = SHAPE_COLLECTION,
		interpretation = Interpreted.class,
		instance = QuickShapeCollection.class)
	public static class Def extends ExElement.Def.Abstract<QuickShapeCollection<?>>
	implements QuickShapePublisher.Def<QuickShapeCollection<?>> {
		private CompiledExpression theValues;
		private ModelComponentId theValueName;
		private ModelComponentId theValueIndex;

		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@QonfigAttributeGetter("for-each")
		public CompiledExpression getValues() {
			return theValues;
		}

		@QonfigAttributeGetter("active-shape-as")
		public ModelComponentId getValueName() {
			return theValueName;
		}

		public ModelComponentId getValueIndex() {
			return theValueIndex;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			theValues = getAttributeExpression("for-each", session);
			String valueNameAttr = session.getAttributeText("active-shape-as");
			ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
			theValueName = elModels.getElementValueModelId(valueNameAttr);
			theValueIndex = elModels.getElementValueModelId("shapeIndex");
			elModels.<Interpreted<?>, SettableValue<?>> satisfyElementSingleValueType(theValueName, ModelTypes.Value,
				Interpreted::getShapeType);
		}

		@Override
		public Interpreted<?> interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}
	}

	public static class Interpreted<T> extends ExElement.Interpreted.Abstract<QuickShapeCollection<T>>
	implements QuickShapePublisher.Interpreted<QuickShapeCollection<T>> {
		private InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<T>> theValues;

		Interpreted(Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		public TypeToken<T> getShapeType() throws ExpressoInterpretationException {
			if (theValues == null)
				theValues = interpret(getDefinition().getValues(), ModelTypes.Collection.anyAsV());
			return (TypeToken<T>) theValues.getType().getType(0);
		}

		public InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<T>> getValues() {
			return theValues;
		}

		@Override
		public void updateElement() throws ExpressoInterpretationException {
			update();
		}

		@Override
		protected void doUpdate() throws ExpressoInterpretationException {
			theValues = null;
			super.doUpdate();
			if (theValues == null)
				theValues = interpret(getDefinition().getValues(), ModelTypes.Collection.anyAsV());
		}

		@Override
		public QuickShapeCollection<T> create() {
			return new QuickShapeCollection<>(getIdentity());
		}
	}

	private ModelValueInstantiator<ObservableCollection<T>> theValuesInstantiator;
	private ModelComponentId theValueName;
	private ModelComponentId theValueIndex;

	private SettableValue<ObservableCollection<T>> theValues;
	private SettableValue<T> theActiveValue;
	private SettableValue<Integer> theActiveValueIndex;

	QuickShapeCollection(Object id) {
		super(id);
		theValues = SettableValue.create();
		theActiveValue = SettableValue.create();
		theActiveValueIndex = SettableValue.create(0);
	}

	public ObservableCollection<T> getValues() {
		return ObservableCollection.flattenValue(theValues);
	}

	public SettableValue<T> getActiveValue() {
		return theActiveValue;
	}

	public SettableValue<Integer> getActiveValueIndex() {
		return theActiveValueIndex;
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);

		Interpreted<T> myInterpreted = (Interpreted<T>) interpreted;

		theValuesInstantiator = myInterpreted.getValues().instantiate();
		theValueName = myInterpreted.getDefinition().getValueName();
		theValueIndex = myInterpreted.getDefinition().getValueIndex();
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
		super.instantiated();

		theValuesInstantiator.instantiate();
	}

	@Override
	protected ModelSetInstance doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		myModels = super.doInstantiate(myModels);

		theValues.set(theValuesInstantiator.get(myModels));
		ExFlexibleElementModelAddOn.satisfyElementValue(theValueName, myModels, theActiveValue);
		ExFlexibleElementModelAddOn.satisfyElementValue(theValueIndex, myModels, theActiveValueIndex);

		return myModels;
	}

	@Override
	public QuickShapeCollection<T> copy(ExElement parent) {
		QuickShapeCollection<T> copy = (QuickShapeCollection<T>) super.copy(parent);

		copy.theValues = SettableValue.create();
		copy.theActiveValue = SettableValue.create();
		copy.theActiveValueIndex = SettableValue.create();

		return copy;
	}
}
