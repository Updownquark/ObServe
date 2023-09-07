package org.observe.quick.base;

import org.observe.expresso.qonfig.ExElement;

import com.google.common.reflect.TypeToken;

public interface ValueTyped<R> extends ExElement {
	public interface Def<E extends ValueTyped<?>> extends ExElement.Def<E> {
	}

	public interface Interpreted<R, E extends ValueTyped<R>> extends ExElement.Interpreted<E> {
		TypeToken<R> getValueType();
	}
}
