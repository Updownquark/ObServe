package org.observe.expresso;

import org.observe.SettableValue;

/** A simple structure with internal, value-specific state for Expresso testing */
public class StatefulTestStructure {
	private SettableValue<Integer> theInternalState;
	private final SettableValue<Integer> theDerivedState;

	/** @param derivedState The derived state value */
	public StatefulTestStructure(SettableValue<Integer> derivedState) {
		theInternalState = SettableValue.<Integer> build().withValue(15).build();
		theDerivedState = derivedState;
	}

	/** @return This structure's internal state */
	public SettableValue<Integer> getInternalState() {
		return theInternalState;
	}

	/** @return This structure's derived state */
	public SettableValue<Integer> getDerivedState() {
		return theDerivedState;
	}

	@Override
	public String toString() {
		return "{internal=" + theInternalState.get() + ", derived=" + theDerivedState.get() + "}";
	}
}
