package org.observe.util;

import org.observe.collect.ObservableSet;
import org.qommons.Equalizer;

/**
 * Wraps an observable set
 *
 * @param <T> The type of the set
 */
public class ObservableSetWrapper<T> extends ObservableCollectionWrapper<T> implements ObservableSet<T> {
	/** @param wrap The set to wrap */
	public ObservableSetWrapper(ObservableSet<T> wrap) {
		super(wrap, true);
	}

	/**
	 * @param wrap The set to wrap
	 * @param modifiable Whether this collection can propagate modifications to the wrapped collection. If false, this collection will be
	 *            immutable.
	 */
	public ObservableSetWrapper(ObservableSet<T> wrap, boolean modifiable) {
		super(wrap, modifiable);
	}

	@Override
	protected ObservableSet<T> getWrapped() {
		return (ObservableSet<T>) super.getWrapped();
	}

	@Override
	public Equalizer getEqualizer() {
		return getWrapped().getEqualizer();
	}
}
