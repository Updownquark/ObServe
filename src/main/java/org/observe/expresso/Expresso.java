package org.observe.expresso;

/** A simple structure consisting of a class view and models, the definition for a set of models for an application */
public class Expresso {
	private final ClassView theClassView;
	private final ObservableModelSet.Built theModels;

	/**
	 * @param classView The class view for this expresso structure
	 * @param models The models for this expresso structure
	 */
	public Expresso(ClassView classView, ObservableModelSet.Built models) {
		theClassView = classView;
		theModels = models;
	}

	/** @return The class view of this expresso structure */
	public ClassView getClassView() {
		return theClassView;
	}

	/** @return The models of this expresso structure */
	public ObservableModelSet.Built getModels() {
		return theModels;
	}
}
