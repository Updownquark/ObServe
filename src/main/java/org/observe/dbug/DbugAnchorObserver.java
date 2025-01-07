package org.observe.dbug;

import java.util.Set;

import org.qommons.Transaction;

public interface DbugAnchorObserver<A> {
	boolean applies(Set<DbugToken> tokens);

	Transaction observe(DbugAnchor<? extends A> anchor);
}
