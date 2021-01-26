package org.observe.remote;

import org.observe.config.ObservableConfig;
import org.observe.config.ObservableConfigParseSession;

public class ObservableConfigServer<C> {
	private final ObservableConfigParseSession theSession;
	private final ObservableConfig theConfig;

	public ObservableConfig getConfig() {
		return theConfig;
	}

	public void poll(C connection) {
	}

	public Runnable listen(C connection) {
	}
}
