package org.observe.supertest;

import java.util.LinkedList;

import org.observe.collect.CollectionChangeType;
import org.observe.collect.ObservableCollection;
import org.qommons.StringUtils;
import org.qommons.collect.ElementId;

public class LinkElement2<T> {
	private final boolean checkOldValue;
	private final ObservableCollection<T> theCollection;
	private ElementId theCollectionElement;

	private CollectionChangeType theExpectedChange;
	private int theExpectedIndex;
	private boolean isUpdateRequired;
	private T theExpectedOldValue;
	private T theExpectedValue;

	private final LinkedList<String> theErrors;

	public LinkElement2(ObservableCollection<T> collection, boolean checkOldValue) {
		this.checkOldValue = checkOldValue;
		theCollection = collection;
		theErrors = new LinkedList<>();
	}

	public int getIndex() {
		return theCollection.getElementsBefore(theCollectionElement);
	}

	public LinkElement2<T> expectAdd(int expectedIndex, T expectedValue) {
		theExpectedChange = CollectionChangeType.add;
		theExpectedIndex = expectedIndex;
		theExpectedValue = expectedValue;
		return this;
	}

	public void expectRemove(T expectedOldValue) {
		theExpectedChange = CollectionChangeType.remove;
		if (checkOldValue)
			theExpectedOldValue = expectedOldValue;
	}

	public void expectSet(boolean required, T expectedOldValue, T expectedValue) {
		theExpectedChange = CollectionChangeType.set;
		isUpdateRequired = required;
		if (checkOldValue)
			theExpectedOldValue = expectedOldValue;
		theExpectedValue = expectedValue;
	}

	public void added(ElementId element) {
		if (theExpectedChange != CollectionChangeType.add)
			throw new IllegalStateException("Add unexpected"); // That's weird
		theCollectionElement = element;
		if (!theCollection.equivalence().elementEquals(theCollection.getElement(element).get(), theExpectedValue))
			theErrors.add("Expected " + theExpectedValue + " but " + theCollection.getElement(element).get() + " was added");
		if (theExpectedIndex >= 0 && theExpectedIndex != getIndex())
			theErrors.add("Expected " + theExpectedValue + " to be added at index " + theExpectedIndex + ", but was added at "
				+ getIndex());
		theExpectedChange = null;
	}

	public void removed(T oldValue) {
		if (theExpectedChange != CollectionChangeType.remove) {
			theErrors.add("Unexpected removal of [" + getIndex() + "] " + oldValue);
			return;
		}
		if (checkOldValue && !theCollection.equivalence().elementEquals(oldValue, theExpectedOldValue))
			theErrors.add("Expected " + theExpectedOldValue + " for removed value [" + getIndex() + "] but found " + oldValue);
		theExpectedChange = null;
	}

	public void set(T oldValue, T newValue) {
		if (theExpectedChange != CollectionChangeType.set) {
			theErrors.add("Unexpected update of [" + getIndex() + "] " + oldValue + "->" + newValue);
			return;
		}
		if (checkOldValue && !theCollection.equivalence().elementEquals(oldValue, theExpectedOldValue))
			theErrors.add("Expected " + theExpectedOldValue + " for old value [" + getIndex() + "] but found " + oldValue);
		if (!theCollection.equivalence().elementEquals(newValue, theExpectedValue))
			theErrors.add("Expected " + theExpectedValue + " for update of value [" + getIndex() + "] but found " + newValue);
		theExpectedChange = null;
	}

	public void changeFinished() {
		if (theExpectedChange != null) {
			switch (theExpectedChange) {
			case add:
				theErrors.add("Expected addition of [" + theExpectedIndex + "] " + theExpectedValue);
				break;
			case remove:
				theErrors.add("Expected removal  of [" + getIndex() + "] " + theExpectedValue);
				break;
			case set:
				if (isUpdateRequired)
					theErrors.add("Expected update   of [" + getIndex() + "] "
						+ (checkOldValue ? (theExpectedOldValue + "->") : "") + theExpectedValue);
				break;
			}
		}
		if (!theErrors.isEmpty()) {
			StringBuilder msg = new StringBuilder();
			StringUtils.conversational(", ", null).print(msg, theErrors, StringBuilder::append);
			throw new AssertionError(msg.toString());
		}
	}
}
