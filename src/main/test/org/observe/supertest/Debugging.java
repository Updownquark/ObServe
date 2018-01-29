package org.observe.supertest;

import java.util.function.Consumer;

import org.qommons.debug.Debug.DebugData;

public class Debugging implements Consumer<DebugData> {
	@Override
	public void accept(DebugData d) {
		if (d.getNames().contains("distinct[0]"))
			d.onAction(a -> System.out.println("mgr[0]: " + a));
		else if (d.getNames().contains("distinctLink[0]"))
			d.onAction(a -> System.out.println("lnk[0]: " + a));
	}
}
