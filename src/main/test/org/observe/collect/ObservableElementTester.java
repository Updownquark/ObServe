package org.observe.collect;

import org.junit.Assert;
import org.observe.ObservableValueEvent;
import org.observe.ObservableValueTester;
import org.observe.collect.ObservableElement.ObservableElementEvent;
import org.qommons.collect.ElementId;

/**
 * Tests an {@link ObservableElement}
 * 
 * @param <T> The type of value in the element
 */
public class ObservableElementTester<T> extends ObservableValueTester<T> {
	private ElementId theCurrentElement;

	/** @param value The element to test */
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

	/**
	 * @param expectedElement The element expected
	 * @param expected The value expected
	 */
	public void check(ElementId expectedElement, T expected) {
		super.check(expected);
		Assert.assertEquals(expectedElement, theCurrentElement);
	}
}
