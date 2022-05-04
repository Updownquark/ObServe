package org.observe.remote;

import java.util.Map;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.collect.ObservableCollection;

public class ObservableServer {
	private final Map<String, Observable<?>> theObservables;
	private final Map<String, ObservableValue<?>> theValues;
	private final Map<String, ObservableCollection<?>> theCollections;

	public ObservableServer() {
	}
}
