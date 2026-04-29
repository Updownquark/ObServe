package org.observe.assoc.impl;

import org.observe.assoc.ObservableMultiMap.ObservableMultiEntry;
import org.observe.collect.ObservableCollection;
import org.observe.util.ObservableCollectionWrapper;
import org.qommons.collect.ElementId;

public class PassivelyDerivedObservableMultiEntry<KS, KT, VS, VT> extends ObservableCollectionWrapper<VT>
implements ObservableMultiEntry<KT, VT> {
	private final KT theKey;
	private final ObservableMultiEntry<KS, VS> theSourceEntry;

	PassivelyDerivedObservableMultiEntry(KT key, ObservableMultiEntry<KS, VS> sourceEntry, ObservableCollection<VT> derivedValues) {
		theKey = key;
		theSourceEntry = sourceEntry;
		init(derivedValues);
	}

	@Override
	public PassivelyDerivedObservableMultiEntry<KS, KT, VS, VT> alias(String alias) {
		super.alias(alias);
		return this;
	}

	@Override
	public ElementId getKeyId() {
		return theSourceEntry == null ? null : theSourceEntry.getKeyId();
	}

	@Override
	public KT getKey() {
		return theKey;
	}

	@Override
	public String toString() {
		return theKey + "=" + super.toString();
	}
}