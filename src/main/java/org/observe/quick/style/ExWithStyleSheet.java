package org.observe.quick.style;

import org.observe.expresso.qonfig.ElementTypeTraceability;
import org.observe.expresso.qonfig.ElementTypeTraceability.SingleTypeTraceability;
import org.observe.expresso.qonfig.ExAddOn;
import org.observe.expresso.qonfig.ExAddOn.Interpreted;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigChildGetter;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigInterpretationException;

public class ExWithStyleSheet extends ExAddOn.Def.Abstract<ExElement, ExAddOn.Void<ExElement>> {
	public static final String QUICK_STYLE_SHEET = "Quick.Style.Sheet";
	private static final SingleTypeTraceability<ExElement, ExElement.Interpreted<?>, ExElement.Def<?>> TRACEABILITY = ElementTypeTraceability
		.getAddOnTraceability(QuickStyleInterpretation.NAME, QuickStyleInterpretation.VERSION, "with-style-sheet", ExWithStyleSheet.class,
			null, null);

	private QuickStyleSheet theStyleSheet;

	public ExWithStyleSheet(QonfigAddOn type, ExElement.Def<?> element) {
		super(type, element);
	}

	@QonfigChildGetter("style-sheet")
	public QuickStyleSheet getStyleSheet() {
		return theStyleSheet;
	}

	@Override
	public void update(ExpressoQIS session, ExElement.Def<?> element) throws QonfigInterpretationException {
		element.withTraceability(TRACEABILITY.validate(session.getFocusType(), session.reporting()));
		super.update(session, element);

		theStyleSheet = ExElement.useOrReplace(QuickStyleSheet.class, theStyleSheet, session, "style-sheet");
		session.put(QUICK_STYLE_SHEET, theStyleSheet);
	}

	@Override
	public Interpreted<? extends ExElement, ExAddOn.Void<ExElement>> interpret(ExElement.Interpreted<? extends ExElement> element) {
		return null;
	}
}
