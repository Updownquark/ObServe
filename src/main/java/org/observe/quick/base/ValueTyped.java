package org.observe.quick.base;

import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.qonfig.ExElement;

import com.google.common.reflect.TypeToken;

/**
 * An interface that allows certain widgets to obtain the type of their value from their parent. When value type is not specified on one of
 * these widgets, they may inspect their ancestors to see if any implement this interface and obtain their value type from there.
 *
 * @param <R> The type of the value
 */
public interface ValueTyped<R> extends ExElement {
	/**
	 * Definition of a {@link ValueTyped} widget
	 *
	 * @param <E> The widget sub-type
	 */
	public interface Def<E extends ValueTyped<?>> extends ExElement.Def<E> {
	}

	/**
	 * Interpretation of a {@link ValueTyped} widget
	 *
	 * @param <R> The type of the value
	 * @param <E> The widget sub-type
	 */
	public interface Interpreted<R, E extends ValueTyped<R>> extends ExElement.Interpreted<E> {
		/**
		 * @return The type of the value
		 * @throws ExpressoInterpretationException if the value type could not be determined
		 */
		TypeToken<R> getValueType() throws ExpressoInterpretationException;
	}
}
