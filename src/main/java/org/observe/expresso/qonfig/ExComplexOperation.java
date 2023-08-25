package org.observe.expresso.qonfig;

import org.observe.expresso.qonfig.ExAddOn.Interpreted;
import org.observe.expresso.qonfig.ExElement.Def;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigInterpretationException;

@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE, qonfigType = "complex-operation")
public class ExComplexOperation extends ExAddOn.Def.Abstract<ExElement, ExAddOn<ExElement>> {
	private String theSourceAs;

	public ExComplexOperation(QonfigAddOn type, Def<? extends ExElement> element) {
		super(type, element);
	}

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
