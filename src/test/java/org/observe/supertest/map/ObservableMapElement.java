package org.observe.supertest.map;

import org.observe.supertest.collect.CollectionLinkElement;

public class ObservableMapElement<K, V> {
	private final CollectionLinkElement<?, K> theKeyElement;
	private final CollectionLinkElement<?, K> theValueElement;

	public ObservableMapElement(CollectionLinkElement<?, K> keyElement, CollectionLinkElement<?, K> valueElement) {
		theKeyElement = keyElement;
		theValueElement = valueElement;
	}
}
