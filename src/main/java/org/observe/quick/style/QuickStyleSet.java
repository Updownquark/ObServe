package org.observe.quick.style;

import java.util.List;

import org.observe.expresso.qonfig.ElementTypeTraceability;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.qommons.Named;
import org.qommons.config.AbstractQIS;
import org.qommons.config.QonfigInterpretationException;

public class QuickStyleSet extends ExElement.Def.Abstract<ExElement> implements Named {
	private static final ElementTypeTraceability<ExElement, ExElement.Interpreted<?>, QuickStyleSet> TRACEABILITY = ElementTypeTraceability.<ExElement, ExElement.Interpreted<?>, QuickStyleSet> build(
		StyleSessionImplV0_1.NAME, StyleSessionImplV0_1.VERSION, "style-set")//
		.reflectMethods(QuickStyleSet.class, null, null)//
		.build();

	private final String theName;
	private final List<QuickStyleValue<?>> theValues;
	private final List<QuickStyleElement.Def> theStyleElements;

	public QuickStyleSet(QuickStyleSheet styleSheet, AbstractQIS<?> session, String name, List<QuickStyleValue<?>> values,
		List<QuickStyleElement.Def> styleElements) {
		super(styleSheet, session.getElement());
		theName = name;
		theValues = values;
		theStyleElements = styleElements;
	}

	@QonfigAttributeGetter("name")
	@Override
	public String getName() {
		return theName;
	}

	public List<QuickStyleValue<?>> getValues() {
		return theValues;
	}

	public List<QuickStyleElement.Def> getStyleElements() {
		return theStyleElements;
	}

	@Override
	public void update(ExpressoQIS session) throws QonfigInterpretationException {
		withTraceability(TRACEABILITY.validate(session.getFocusType(), session.reporting()));
		super.update(session);
		int i = 0;
		for (ExpressoQIS valueS : session.forChildren("style"))
			theStyleElements.get(i++).update(valueS);
	}
}
