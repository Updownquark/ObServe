package org.observe.quick;

import org.observe.expresso.ClassView;
import org.observe.expresso.Expresso;
import org.observe.expresso.ObservableModelSet;
import org.observe.quick.style.QuickStyleSheet;

/** Contains all data specifiable in the &lt;head> element of a &lt;quick> document */
public class QuickHeadSection extends Expresso {
	private final QuickStyleSheet theStyleSheet;

	QuickHeadSection(ClassView classView, ObservableModelSet models, QuickStyleSheet styleSheet) {
		super(classView, models);
		theStyleSheet = styleSheet;
	}

	/** @return The style sheet for the document */
	public QuickStyleSheet getStyleSheet() {
		return theStyleSheet;
	}
}