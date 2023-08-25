package org.observe.quick.style;

import org.observe.expresso.qonfig.ExAddOn;
import org.observe.expresso.qonfig.ExAddOn.Interpreted;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigChildGetter;
import org.observe.quick.base.MultiValueWidget;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigInterpretationException;

@ExElementTraceable(toolkit = QuickStyleInterpretation.STYLE,
	qonfigType = "with-style-sheet",
	interpretation = Interpreted.class,
	instance = MultiValueWidget.class)
public class ExWithStyleSheet extends ExAddOn.Def.Abstract<ExElement, ExAddOn.Void<ExElement>> {
	public static final String QUICK_STYLE_SHEET = "Quick.Style.Sheet";

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
		super.update(session, element);

		theStyleSheet = ExElement.useOrReplace(QuickStyleSheet.class, theStyleSheet, session, "style-sheet");
		session.put(QUICK_STYLE_SHEET, theStyleSheet);
	}

	@Override
	public Interpreted<? extends ExElement, ExAddOn.Void<ExElement>> interpret(ExElement.Interpreted<? extends ExElement> element) {
		return null;
	}
}
