package org.observe.collect;

import org.junit.Assert;
import org.observe.ObservableValueEvent;
import org.observe.ObservableValueTester;
import org.observe.collect.ObservableElement.ObservableElementEvent;
import org.qommons.collect.ElementId;

public class ObservableElementTester<T> extends ObservableValueTester<T> {
	private ElementId theCurrentElement;

	public ObservableElementTester(ObservableElement<? extends T> value) {
		super(value);
	}

	@Override
	protected ObservableElement<? extends T> getValue() {
		return (ObservableElement<? extends T>) super.getValue();
	}

	@Override
	protected void event(ObservableValueEvent<? extends T> evt) {
		super.event(evt);
		theCurrentElement = ((ObservableElementEvent<? extends T>) evt).getNewElement();
	}

	@Override
	public void checkSynced() {
		super.checkSynced();
		Assert.assertEquals(theCurrentElement, getValue().getElementId());
	}

	public void check(ElementId expectedElement, T expected) {
		super.check(expected);
		Assert.assertEquals(expectedElement, theCurrentElement);
	}
}
