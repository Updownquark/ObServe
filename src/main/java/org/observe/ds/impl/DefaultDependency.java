package org.observe.ds.impl;

import java.util.function.Supplier;

import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableSet;
import org.observe.ds.DSComponent;
import org.observe.ds.Dependency;
import org.observe.ds.Service;
import org.qommons.Causable;
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.collect.RRWLockingStrategy;

class DefaultDependency<C, S> implements Dependency<C, S> {
	private final String theOwnerName;
	private final Supplier<DefaultComponent<C>> theOwner;
	private final Service<S> theTarget;
	private final int theMinimum;
	private final boolean isDynamic;
	private final ObservableSet<DSComponent<C>> theProviders;
	private final ObservableCollection<S> theProvided;

	DefaultDependency(String ownerName, Supplier<DefaultComponent<C>> owner, Service<S> target, int minimum, boolean dynamic,
		Transactable lock) {
		theOwnerName = ownerName;
		theOwner = owner;
		theTarget = target;
		theMinimum = minimum;
		isDynamic = dynamic;
		theProviders = ObservableSet.build((Class<DSComponent<C>>) (Class<?>) DSComponent.class).withLocking(new RRWLockingStrategy(lock))
			.build();
		theProvided = theProviders.flow().transform(target.getServiceType(), tx -> tx.cache(false).map(p -> p.provide(target)))
			.unmodifiable(false).collectPassive();
	}

	@Override
	public DSComponent<C> getOwner() {
		DefaultComponent<C> comp = theOwner.get();
		return comp == null ? null : comp.getComponent();
	}

	DefaultComponent<C> getRealOwner() {
		return theOwner.get();
	}

	@Override
	public Service<S> getTarget() {
		return theTarget;
	}

	@Override
	public int getMinimum() {
		return theMinimum;
	}

	@Override
	public boolean isDynamic() {
		return isDynamic;
	}

	@Override
	public ObservableSet<? extends DSComponent<C>> getProviders() {
		return theProviders.flow().unmodifiable(false).collect();
	}

	@Override
	public ObservableCollection<S> get() {
		return theProvided;
	}

	boolean satisfy(DSComponent<C> provider, Object cause) {
		try (Transaction t = theProviders.lock(true, cause)) {
			if (!theProviders.add(provider))
				return false;
			if (theProviders.size() == theMinimum)
				getRealOwner().modifyUnsatisfied(true, isDynamic, cause);
			else
				getRealOwner().dependencyModified(cause);
		}
		return true;
	}

	boolean remove(DSComponent<C> provider, Object cause) {
		try (Transaction t = theProviders.lock(true, cause)) {
			if (!theProviders.remove(provider))
				return false;
			if (theProviders.size() == theMinimum - 1)
				getRealOwner().modifyUnsatisfied(false, isDynamic, cause);
			else
				getRealOwner().dependencyModified(cause);
			return true;
		}
	}

	void clear(Causable cause) {
		try (Transaction t = theProviders.lock(true, cause)) {
			theProviders.clear();
		}
	}

	@Override
	public String toString() {
		StringBuilder str = new StringBuilder();
		str.append(theOwnerName).append("<--").append(theTarget.getName()).append('(');
		str.append(theProviders.size()).append('/').append(theMinimum);
		if (isDynamic)
			str.append('*');
		str.append(')');
		return str.toString();
	}
}
