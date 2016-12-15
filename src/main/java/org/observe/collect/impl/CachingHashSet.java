package org.observe.collect.impl;

import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableSet;
import org.qommons.Equalizer;

/**
 * A caching set that stores its values in a hash set
 *
 * @param <E> The type of element in the set
 */
public class CachingHashSet<E> extends CachingObservableCollection<E> implements ObservableSet<E> {
	/** @param wrap The set to keep a cache of */
	public CachingHashSet(ObservableSet<E> wrap) {
		super(wrap);
	}

	@Override
	protected ObservableCollection<E> createCacheCollection(ObservableCollection<E> wrap) {
		return new ObservableHashSet<>(wrap.getType());
	}

	@Override
	protected ObservableSet<E> getCache() {
		return (ObservableSet<E>) super.getCache();
	}

	@Override
	public Equalizer getEqualizer() {
		return getCache().getEqualizer();
	}
}
