package org.observe.expresso.qonfig;

import org.observe.expresso.ExpressoInterpretationException;
import org.qommons.config.QonfigElementDef;
import org.qommons.config.QonfigInterpretationException;

public interface QonfigExternalContent extends ExElement {
	@ExElementTraceable(toolkit = "Qonfig-Reference v0.1",
		qonfigType = "external-content",
		interpretation = Interpreted.class,
		instance = ExpressoExternalContent.class)
	public interface Def<C extends QonfigExternalContent> extends ExElement.Def<C> {
		@QonfigAttributeGetter("fulfills")
		QonfigElementDef getFulfills();

		@QonfigChildGetter("fulfillment")
		ExElement.Def<?> getContent();

		void update(ExpressoQIS session, ExElement.Def<?> content) throws QonfigInterpretationException;

		Interpreted<? extends C> interpret();
	}

	public interface Interpreted<C extends QonfigExternalContent> extends ExElement.Interpreted<C> {
		@Override
		Def<? super C> getDefinition();

		ExElement.Interpreted<?> getContent();

		void update(ExElement.Interpreted<?> content) throws ExpressoInterpretationException;
	}

	ExElement getContent();

	@Override
	QonfigExternalContent copy(ExElement parent);
}
