package org.observe.quick.style;

import java.util.Map;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet.CompiledModelValue;
import org.observe.util.TypeTokens;

/**
 * A structure {@link StyleApplicationDef#compile(org.observe.expresso.ExpressoEnv, Map) compiled} from a {@link StyleApplicationDef}
 * containing information defining which elements a set of style values should apply to.
 */
public class CompiledStyleApplication {
	private final CompiledStyleApplication theParent;
	private final StyleApplicationDef theApplication;
	private final CompiledModelValue<SettableValue<?>, SettableValue<Boolean>> theCondition;

	/**
	 * @param parent The parent application (see {@link StyleApplicationDef#getParent()})
	 * @param application The application this structure is evaluated from
	 * @param condition The condition value
	 */
	public CompiledStyleApplication(CompiledStyleApplication parent, StyleApplicationDef application,
		CompiledModelValue<SettableValue<?>, SettableValue<Boolean>> condition) {
		theParent = parent;
		theApplication = application;
		theCondition = condition != null ? condition : CompiledModelValue.literal(TypeTokens.get().BOOLEAN, true, "true");
	}

	/** @return The parent application (see {@link StyleApplicationDef#getParent()}) */
	public CompiledStyleApplication getParent() {
		return theParent;
	}

	/** @return The application this structure is evaluated from */
	public StyleApplicationDef getApplication() {
		return theApplication;
	}

	/** @return The condition value */
	public CompiledModelValue<SettableValue<?>, SettableValue<Boolean>> getCondition() {
		return theCondition;
	}

	/**
	 * Interprets this compiled structure
	 *
	 * @param applications A cache of interpreted style applications for re-use
	 * @return The interpreted style application
	 * @throws ExpressoInterpretationException If this structure's expressions could not be
	 *         {@link ObservableExpression#evaluate(org.observe.expresso.ModelType.ModelInstanceType, org.observe.expresso.ExpressoEnv, int)
	 *         evaluated}
	 */
	public InterpretedStyleApplication interpret(Map<CompiledStyleApplication, InterpretedStyleApplication> applications)
		throws ExpressoInterpretationException {
		InterpretedStyleApplication parent;
		if (theParent == null)
			parent = null;
		else {
			parent = applications.get(theParent);
			if (parent == null) {
				parent = theParent.interpret(applications);
				applications.put(theParent, parent);
			}
		}
		return new InterpretedStyleApplication(parent, this, //
			theCondition.createSynthesizer());
	}

	@Override
	public String toString() {
		return theApplication.toString();
	}
}
