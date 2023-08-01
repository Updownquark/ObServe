package org.observe.quick.style;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.observe.expresso.qonfig.ElementTypeTraceability;
import org.observe.expresso.qonfig.ElementTypeTraceability.SingleTypeTraceability;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.qommons.Named;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

public class QuickStyleSet extends ExElement.Def.Abstract<ExElement.Void> implements Named {
	private static final SingleTypeTraceability<ExElement.Void, ExElement.Interpreted<ExElement.Void>, QuickStyleSet> TRACEABILITY//
	= ElementTypeTraceability.getElementTraceability(QuickStyleInterpretation.NAME, QuickStyleInterpretation.VERSION, "style-set",
		QuickStyleSet.class, null, null);

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
		withTraceability(TRACEABILITY.validate(session.getFocusType(), session.reporting()));
		super.doUpdate(session);

		theName = session.getAttributeText("name");
		ExElement.syncDefs(QuickStyleElement.Def.class, theStyleElements, session.forChildren("style"));
	}
}
