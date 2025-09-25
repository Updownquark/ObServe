package org.observe.quick.draw;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

public class QuickPoint extends ExElement.Abstract {
	public static final String POINT = "point";

	@ExElementTraceable(toolkit = QuickDrawInterpretation.DRAW,
		qonfigType = POINT,
		interpretation = Interpreted.class,
		instance = QuickPoint.class)
	public static class Def extends ExElement.Def.Abstract<QuickPoint> {
		private CompiledExpression theX;
		private CompiledExpression theY;

		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@QonfigAttributeGetter("x")
		public CompiledExpression getX() {
			return theX;
		}

		@QonfigAttributeGetter("y")
		public CompiledExpression getY() {
			return theY;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);

			theX = getAttributeExpression("x", session);
			theY = getAttributeExpression("y", session);
		}

		public Interpreted interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}
	}

	public static class Interpreted extends ExElement.Interpreted.Abstract<QuickPoint> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Double>> theX;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Double>> theY;

		Interpreted(Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<Double>> getX() {
			return theX;
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<Double>> getY() {
			return theY;
		}

		public void updateShape() throws ExpressoInterpretationException {
			update();
		}

		@Override
		protected void doUpdate() throws ExpressoInterpretationException {
			super.doUpdate();

			theX = interpret(getDefinition().getX(), ModelTypes.Value.DOUBLE);
			theY = interpret(getDefinition().getY(), ModelTypes.Value.DOUBLE);
		}

		public QuickPoint create() {
			return new QuickPoint(getIdentity());
		}
	}

	private ModelValueInstantiator<SettableValue<Double>> theXInstantiator;
	private ModelValueInstantiator<SettableValue<Double>> theYInstantiator;

	private SettableValue<SettableValue<Double>> theX;
	private SettableValue<SettableValue<Double>> theY;

	QuickPoint(Object id) {
		super(id);
		theX = SettableValue.create();
		theY = SettableValue.create();
	}

	public SettableValue<Double> getX() {
		return SettableValue.flatten(theX, () -> 0.0);
	}

	public SettableValue<Double> getY() {
		return SettableValue.flatten(theY, () -> 0.0);
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);

		Interpreted myInterpreted = (Interpreted) interpreted;
		theXInstantiator = myInterpreted.getX().instantiate();
		theYInstantiator = myInterpreted.getY().instantiate();
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
		super.instantiated();
		theXInstantiator.instantiate();
		theYInstantiator.instantiate();
	}

	@Override
	protected ModelSetInstance doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		myModels = super.doInstantiate(myModels);

		theX.set(theXInstantiator.get(myModels));
		theY.set(theYInstantiator.get(myModels));

		return myModels;
	}

	@Override
	public QuickPoint copy(ExElement parent) {
		QuickPoint copy = (QuickPoint) super.copy(parent);

		copy.theX = SettableValue.create();
		copy.theY = SettableValue.create();

		return copy;
	}
}
