package org.observe.expresso;

import org.observe.SettableValue;

/** A simple structure with internal, value-specific state for Expresso testing */
public class DynamicTypeStatefulTestStructure {
	private SettableValue<?> theInternalState;
	private final SettableValue<?> theDerivedState;

	/**
	 * @param internalState The internal state value
	 * @param derivedState The derived state value
	 */
	public DynamicTypeStatefulTestStructure(SettableValue<?> internalState, SettableValue<?> derivedState) {
		theInternalState = internalState;
		theDerivedState = derivedState;
	}

	/** @return This structure's internal state */
	public SettableValue<?> getInternalState() {
		return theInternalState;
	}

	/** @return This structure's derived state */
	public SettableValue<?> getDerivedState() {
		return theDerivedState;
	}

	@Override
	public String toString() {
		return "{internal=" + theInternalState.get() + ", derived=" + theDerivedState.get() + "}";
	}
}
