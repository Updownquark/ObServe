package org.observe.quick.draw;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigChildGetter;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

public class QuickLine extends QuickShape.Abstract implements QuickLinearShape {
	public static final String LINE = "line";

	@ExElementTraceable(toolkit = QuickDrawInterpretation.DRAW,
		qonfigType = LINE,
		interpretation = Interpreted.class,
		instance = QuickLine.class)
	public static class Def extends QuickLinearShape.Def.Abstract<QuickLine> {
		private final List<QuickPoint.Def> thePoints;

		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
			thePoints = new ArrayList<>();
		}

		@QonfigChildGetter("point")
		public List<QuickPoint.Def> getPoints() {
			return Collections.unmodifiableList(thePoints);
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);

			syncChildren(QuickPoint.Def.class, thePoints, session.forChildren("point"));
		}

		@Override
		public Interpreted interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}
	}

	public static class Interpreted extends QuickLinearShape.Interpreted.Abstract<QuickLine> {
		private final List<QuickPoint.Interpreted> thePoints;

		Interpreted(Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
			thePoints = new ArrayList<>();
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		public List<QuickPoint.Interpreted> getPoints() {
			return Collections.unmodifiableList(thePoints);
		}

		@Override
		protected void doUpdate() throws ExpressoInterpretationException {
			super.doUpdate();

			syncChildren(getDefinition().getPoints(), thePoints, p -> p.interpret(this), QuickPoint.Interpreted::updateShape);
		}

		@Override
		public QuickLine create() {
			return new QuickLine(getIdentity());
		}
	}

	private List<QuickPoint> thePoints;

	QuickLine(Object id) {
		super(id);
		thePoints = new ArrayList<>();
	}

	public List<QuickPoint> getPoints() {
		return Collections.unmodifiableList(thePoints);
	}

	@Override
	public QuickLinearShape.QuickLineShapeStyle getStyle() {
		return (QuickLinearShape.QuickLineShapeStyle) super.getStyle();
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);

		Interpreted myInterpreted = (Interpreted) interpreted;
		syncChildren(myInterpreted.getPoints(), thePoints, p -> p.create(), QuickPoint::update);
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
		super.instantiated();

		for (QuickPoint point : thePoints)
			point.instantiated();
	}

	@Override
	protected ModelSetInstance doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		myModels = super.doInstantiate(myModels);

		for (QuickPoint point : thePoints)
			point.instantiate(myModels);

		return myModels;
	}

	@Override
	public QuickLine copy(ExElement parent) {
		QuickLine copy = (QuickLine) super.copy(parent);

		copy.thePoints = new ArrayList<>();
		for (QuickPoint point : thePoints)
			copy.thePoints.add(point.copy(copy));

		return copy;
	}
}
