package org.observe.collect;

import java.util.Iterator;

public interface Betterator<E> extends Iterator<E> {
	String canRemove();
	String isAcceptable(E value);
	E set(E value, Object cause);
}
