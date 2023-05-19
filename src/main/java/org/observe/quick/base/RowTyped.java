package org.observe.quick.base;

import org.observe.quick.QuickElement;

import com.google.common.reflect.TypeToken;

public interface RowTyped<R> extends QuickElement {
	public interface Def<E extends RowTyped<?>> extends QuickElement.Def<E> {
	}

	public interface Interpreted<R, E extends RowTyped<R>> extends QuickElement.Interpreted<E> {
		TypeToken<R> getRowType();
	}
}
