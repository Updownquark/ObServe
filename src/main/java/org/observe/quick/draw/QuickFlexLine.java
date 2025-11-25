package org.observe.quick.draw;

import org.observe.Observable;
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

public class QuickFlexLine<T> extends QuickShape.Abstract implements QuickLinearShape {
	public static final String FLEX_LINE = "flex-line";

	@ExElementTraceable(toolkit = QuickDrawInterpretation.DRAW,
		qonfigType = FLEX_LINE,
		interpretation = Interpreted.class,
		instance = QuickFlexLine.class)
	public static class Def extends QuickLinearShape.Def.Abstract<QuickFlexLine<?>> {
		private CompiledExpression thePoints;
		private ModelComponentId theActivePointAs;
		private ModelComponentId thePointIndexAs;
		private CompiledExpression thePointX;
		private CompiledExpression thePointY;
		private ModelComponentId theNextPointAs;
		private ModelComponentId thePointDistanceAs;
		private ModelComponentId theLinearPAs;
		private CompiledExpression theStyleVarianceDistance;

		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		@QonfigAttributeGetter("points")
		public CompiledExpression getPoints() {
			return thePoints;
		}

		@QonfigAttributeGetter("active-point-as")
		public ModelComponentId getActivePointAs() {
			return theActivePointAs;
		}

		@QonfigAttributeGetter("point-index-as")
		public ModelComponentId getPointIndexAs() {
			return thePointIndexAs;
		}

		@QonfigAttributeGetter("point-x")
		public CompiledExpression getPointX() {
			return thePointX;
		}

		@QonfigAttributeGetter("point-y")
		public CompiledExpression getPointY() {
			return thePointY;
		}

		@QonfigAttributeGetter("next-point-as")
		public ModelComponentId getNextPointAs() {
			return theNextPointAs;
		}

		@QonfigAttributeGetter("point-distance-as")
		public ModelComponentId getPointDistanceAs() {
			return thePointDistanceAs;
		}

		@QonfigAttributeGetter("linear-p-as")
		public ModelComponentId getLinearPAs() {
			return theLinearPAs;
		}

		@QonfigAttributeGetter("style-variance-distance")
		public CompiledExpression getStyleVarianceDistance() {
			return theStyleVarianceDistance;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);

			thePoints = getAttributeExpression("points", session);
			thePointX = getAttributeExpression("point-x", session);
			thePointY = getAttributeExpression("point-y", session);
			theStyleVarianceDistance = getAttributeExpression("style-variance-distance", session);

			String apaAttr = session.getAttributeText("active-point-as");
			String piaAttr = session.getAttributeText("point-index-as");
			String npaAttr = session.getAttributeText("next-point-as");
			String pdaAttr = session.getAttributeText("point-distance-as");
			String lpaAttr = session.getAttributeText("linear-p-as");
			ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
			theActivePointAs = elModels.getElementValueModelId(apaAttr);
			thePointIndexAs = piaAttr == null ? null : elModels.getElementValueModelId(piaAttr);
			theNextPointAs = npaAttr == null ? null : elModels.getElementValueModelId(npaAttr);
			thePointDistanceAs = pdaAttr == null ? null : elModels.getElementValueModelId(pdaAttr);
			theLinearPAs = lpaAttr == null ? null : elModels.getElementValueModelId(lpaAttr);
			elModels.<Interpreted<?>, SettableValue<?>> satisfyElementSingleValueType(theActivePointAs, ModelTypes.Value,
				Interpreted::getPointType);
		}

		@Override
		public Interpreted<?> interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}
	}

	public static class Interpreted<T> extends QuickLinearShape.Interpreted.Abstract<QuickFlexLine<T>> {
		private InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<T>> thePoints;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Double>> thePointX;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Double>> thePointY;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Double>> theStyleVarianceDistance;

		Interpreted(Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		public TypeToken<T> getPointType() throws ExpressoInterpretationException {
			if (thePoints == null)
				thePoints = interpret(getDefinition().getPoints(), ModelTypes.Collection.anyAsV());
			return (TypeToken<T>) thePoints.getType().getType(0);
		}

		public InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<T>> getPoints() {
			return thePoints;
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<Double>> getPointX() {
			return thePointX;
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<Double>> getPointY() {
			return thePointY;
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<Double>> getStyleVarianceDistance() {
			return theStyleVarianceDistance;
		}

		@Override
		protected void doUpdate() throws ExpressoInterpretationException {
			thePoints = null;
			super.doUpdate();
			getPointType();
			thePointX = interpret(getDefinition().getPointX(), ModelTypes.Value.DOUBLE);
			thePointY = interpret(getDefinition().getPointY(), ModelTypes.Value.DOUBLE);
			theStyleVarianceDistance = interpret(getDefinition().getStyleVarianceDistance(), ModelTypes.Value.DOUBLE);
		}

		@Override
		public QuickFlexLine<T> create() {
			return new QuickFlexLine<>(getIdentity());
		}
	}

	private ModelValueInstantiator<ObservableCollection<T>> thePointsInstantiator;
	private ModelComponentId theActivePointAsVbl;
	private ModelComponentId thePointIndexAsVbl;
	private ModelValueInstantiator<SettableValue<Double>> thePointXInstantiator;
	private ModelValueInstantiator<SettableValue<Double>> thePointYInstantiator;
	private ModelComponentId theNextPointAsVbl;
	private ModelComponentId thePointDistanceAsVbl;
	private ModelComponentId theLinearPAsVbl;
	private ModelValueInstantiator<SettableValue<Double>> theStyleVarianceDistanceInstantiator;

	private SettableValue<ObservableCollection<T>> thePoints;
	private SettableValue<SettableValue<Double>> thePointX;
	private SettableValue<SettableValue<Double>> thePointY;
	private SettableValue<SettableValue<Double>> theStyleVarianceDistance;
	private SettableValue<T> theActivePointAs;
	private SettableValue<Integer> thePointIndexAs;
	private SettableValue<T> theNextPointAs;
	private SettableValue<Double> thePointDistanceAs;
	private SettableValue<Double> theLinearPAs;

	private boolean isStyleDynamic;
	private boolean isThicknessDynamic;

	QuickFlexLine(Object id) {
		super(id);

		thePoints = SettableValue.create();
		thePointX = SettableValue.create();
		thePointY = SettableValue.create();
		theStyleVarianceDistance = SettableValue.create();
		theActivePointAs = SettableValue.create();
		thePointIndexAs = SettableValue.create();
		theNextPointAs = SettableValue.create();
		thePointDistanceAs = SettableValue.create();
		theLinearPAs = SettableValue.create();
	}

	public ObservableCollection<T> getPoints() {
		return ObservableCollection.flattenValue(thePoints);
	}

	public SettableValue<Double> getPointX() {
		return SettableValue.flatten(thePointX);
	}

	public SettableValue<Double> getPointY() {
		return SettableValue.flatten(thePointY);
	}

	public SettableValue<Double> getStyleVarianceDistance() {
		return SettableValue.flatten(theStyleVarianceDistance);
	}

	public SettableValue<T> getActivePointAs() {
		return theActivePointAs;
	}

	public SettableValue<Integer> getPointIndexAs() {
		return thePointIndexAs;
	}

	public SettableValue<T> getNextPointAs() {
		return theNextPointAs;
	}

	public SettableValue<Double> getPointDistanceAs() {
		return thePointDistanceAs;
	}

	public SettableValue<Double> getLinearPAs() {
		return theLinearPAs;
	}

	/** @return Whether this line varies its thickness along each line segment */
	public boolean isThicknessDynamic() {
		return isThicknessDynamic;
	}

	/** @return Whether this line varies its style along each line segment */
	public boolean isStyleDynamic() {
		return isStyleDynamic;
	}

	public boolean isDistanceNeeded() {
		return thePointDistanceAsVbl != null;
	}

	@Override
	public QuickLinearShape.QuickLineShapeStyle getStyle() {
		return (QuickLinearShape.QuickLineShapeStyle) super.getStyle();
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);

		Interpreted<T> myInterpreted = (Interpreted<T>) interpreted;
		thePointsInstantiator = myInterpreted.getPoints().instantiate();
		thePointXInstantiator = myInterpreted.getPointX().instantiate();
		thePointYInstantiator = myInterpreted.getPointY().instantiate();
		theStyleVarianceDistanceInstantiator = ExElement.instantiate(myInterpreted.getStyleVarianceDistance());
		theActivePointAsVbl = myInterpreted.getDefinition().getActivePointAs();
		thePointIndexAsVbl = myInterpreted.getDefinition().getPointIndexAs();
		theNextPointAsVbl = myInterpreted.getDefinition().getNextPointAs();
		thePointDistanceAsVbl = myInterpreted.getDefinition().getPointDistanceAs();
		theLinearPAsVbl = myInterpreted.getDefinition().getLinearPAs();
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
		super.instantiated();

		thePointsInstantiator.instantiate();
		thePointXInstantiator.instantiate();
		thePointYInstantiator.instantiate();
	}

	@Override
	protected ModelSetInstance doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		myModels = super.doInstantiate(myModels);

		ExFlexibleElementModelAddOn.satisfyElementValue(theActivePointAsVbl, myModels, theActivePointAs);
		if (thePointIndexAsVbl != null)
			ExFlexibleElementModelAddOn.satisfyElementValue(thePointIndexAsVbl, myModels, thePointIndexAs);
		if (theNextPointAsVbl != null)
			ExFlexibleElementModelAddOn.satisfyElementValue(theNextPointAsVbl, myModels, theNextPointAs);
		if (thePointDistanceAsVbl != null)
			ExFlexibleElementModelAddOn.satisfyElementValue(thePointDistanceAsVbl, myModels, thePointDistanceAs);
		if (theLinearPAsVbl != null)
			ExFlexibleElementModelAddOn.satisfyElementValue(theLinearPAsVbl, myModels, theLinearPAs);
		thePoints.set(thePointsInstantiator.get(myModels));
		thePointX.set(thePointXInstantiator.get(myModels));
		thePointY.set(thePointYInstantiator.get(myModels));
		theStyleVarianceDistance.set(ExElement.get(theStyleVarianceDistanceInstantiator, myModels));

		isThicknessDynamic = false;
		isStyleDynamic = false;
		if (thePointDistanceAsVbl != null || theLinearPAsVbl != null) {
			Observable.CoreChangeSources thicknessChanges = getStyle().getThickness().getChangeSources();
			if (theLinearPAsVbl != null && thicknessChanges.containsAny(theLinearPAs.getChangeSources()))
				isThicknessDynamic = true;
			isStyleDynamic = isThicknessDynamic;
			if (!isStyleDynamic) {
				Observable.CoreChangeSources styleChanges = getStyle().changes().getChangeSources();
				if (theLinearPAsVbl != null && styleChanges.containsAny(theLinearPAs.getChangeSources()))
					isStyleDynamic = true;
			}
		}

		return myModels;
	}

	@Override
	public QuickFlexLine<T> copy(ExElement parent) {
		QuickFlexLine<T> copy = (QuickFlexLine<T>) super.copy(parent);

		copy.thePoints = SettableValue.create();
		copy.thePointX = SettableValue.create();
		copy.thePointY = SettableValue.create();
		copy.theStyleVarianceDistance = SettableValue.create();
		copy.theActivePointAs = SettableValue.create();
		copy.thePointIndexAs = SettableValue.create();
		copy.theNextPointAs = SettableValue.create();
		copy.thePointDistanceAs = SettableValue.create();
		copy.theLinearPAs = SettableValue.create();

		return copy;
	}
}
