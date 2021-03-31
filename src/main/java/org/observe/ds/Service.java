package org.observe.ds;

import org.qommons.Named;

/**
 * Represents any resource that may be shared among {@link DSComponent component}s in a {@link DependencyService}
 * 
 * @param <S> The type of the service
 */
public interface Service<S> extends Named {
	/** @return The type of the service class */
	Class<S> getServiceType();
}
