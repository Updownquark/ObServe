package org.observe;

import org.qommons.Causable;

public interface CausableChanging {
	Observable<? extends Causable> simpleChanges();
}
