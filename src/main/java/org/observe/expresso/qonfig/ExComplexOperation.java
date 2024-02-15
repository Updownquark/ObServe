package org.observe.expresso.qonfig;

import org.observe.expresso.qonfig.ExAddOn.Interpreted;
import org.observe.expresso.qonfig.ExElement.Def;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigInterpretationException;

/** A type of transformation that provides the source input as a model value to expressions in the transformation */
@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE, qonfigType = ExComplexOperation.COMPLEX_OPERATION)
public class ExComplexOperation extends ExAddOn.Def.Abstract<ExElement, ExAddOn<ExElement>> {
	/** The XML name of this add-on */
	public static final String COMPLEX_OPERATION = "complex-operation";

	private String theSourceAs;

	/**
	 * @param type The Qonfig type of this add-on
	 * @param element The transformation element
	 */
	public ExComplexOperation(QonfigAddOn type, Def<? extends ExElement> element) {
		super(type, element);
	}

	/** @return The name of the model variable that the source will be available to expressions as */
	@QonfigAttributeGetter("source-as")
	public String getSourceAs() {
		return theSourceAs;
	}

	@Override
	public void update(ExpressoQIS session, Def<? extends ExElement> element) throws QonfigInterpretationException {
		super.update(session, element);
		theSourceAs = session.getAttributeText("source-as");
	}

	@Override
	public Interpreted<?, ExAddOn<ExElement>> interpret(ExElement.Interpreted<? extends ExElement> element) {
		return null;
	}
}
