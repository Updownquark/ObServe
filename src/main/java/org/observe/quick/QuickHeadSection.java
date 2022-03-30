package org.observe.quick;

import org.observe.expresso.ClassView;
import org.observe.expresso.ObservableModelSet;

public class QuickHeadSection {
	private final ClassView theClassView;
	private final ObservableModelSet theModels;

	QuickHeadSection(ClassView classView, ObservableModelSet models) {
		theClassView = classView;
		theModels = models;
	}

	public ClassView getImports() {
		return theClassView;
	}

	public ObservableModelSet getModels() {
		return theModels;
	}
}