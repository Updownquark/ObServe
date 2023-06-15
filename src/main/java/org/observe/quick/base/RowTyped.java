package org.observe.quick.base;

import org.observe.expresso.qonfig.ExElement;

import com.google.common.reflect.TypeToken;

public interface RowTyped<R> extends ExElement {
	public interface Def<E extends RowTyped<?>> extends ExElement.Def<E> {
	}

	public interface Interpreted<R, E extends RowTyped<R>> extends ExElement.Interpreted<E> {
		TypeToken<R> getRowType();
	}
}
