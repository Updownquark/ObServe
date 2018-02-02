package org.observe.supertest;

import java.util.function.Consumer;

import org.qommons.debug.Debug.DebugData;

public class Debugging implements Consumer<DebugData> {
	@Override
	public void accept(DebugData d) {
		if (d.getNames().contains("distinct[2]"))
			d.onAction(action -> System.out.println("mgr[2]: " + action));
		else if (d.getNames().contains("distinctLink[2]"))
			d.onAction(action -> System.out.println("lnk[2]: " + action));
	}
}
