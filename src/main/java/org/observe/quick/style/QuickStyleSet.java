package org.observe.quick.style;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.qommons.Named;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

@ExElementTraceable(toolkit = QuickStyleInterpretation.STYLE, qonfigType = "style-set")
public class QuickStyleSet extends ExElement.Def.Abstract<ExElement.Void> implements Named {
	private String theName;
	private final List<QuickStyleElement.Def> theStyleElements;

	public QuickStyleSet(QuickStyleSheet styleSheet, QonfigElementOrAddOn styleSetEl) {
		super(styleSheet, styleSetEl);
		theStyleElements = new ArrayList<>();
	}

	@Override
	public QuickStyleSheet getParentElement() {
		return (QuickStyleSheet) super.getParentElement();
	}

	@QonfigAttributeGetter("name")
	@Override
	public String getName() {
		return theName;
	}

	public List<QuickStyleElement.Def> getStyleElements() {
		return Collections.unmodifiableList(theStyleElements);
	}

	public void getStyleValues(Collection<QuickStyleValue> styleValues, StyleApplicationDef application, QonfigElement element) {
		for (QuickStyleElement.Def styleEl : theStyleElements)
			styleEl.getStyleValues(styleValues, application, element);
	}

	@Override
	protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
		super.doUpdate(session);

		theName = session.getAttributeText("name");
		ExElement.syncDefs(QuickStyleElement.Def.class, theStyleElements, session.forChildren("style"));
	}
}
