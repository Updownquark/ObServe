package org.observe.quick;

import org.observe.expresso.ClassView;
import org.observe.expresso.ObservableModelSet;
import org.observe.quick.style.QuickStyleSheet;

public class QuickHeadSection {
	private final ClassView theClassView;
	private final ObservableModelSet theModels;
	private final QuickStyleSheet theStyleSheet;

	QuickHeadSection(ClassView classView, ObservableModelSet models, QuickStyleSheet styleSheet) {
		theClassView = classView;
		theModels = models;
		theStyleSheet = styleSheet;
	}

	public ClassView getImports() {
		return theClassView;
	}

	public ObservableModelSet getModels() {
		return theModels;
	}

	public QuickStyleSheet getStyleSheet() {
		return theStyleSheet;
	}
}