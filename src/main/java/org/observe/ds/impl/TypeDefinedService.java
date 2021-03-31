package org.observe.ds.impl;

import org.observe.ds.Service;
import org.observe.util.TypeTokens;

import com.google.common.reflect.TypeToken;

/**
 * A DS service capability that is defined by its java type
 *
 * @param <S> The java type of the service
 */
public class TypeDefinedService<S> implements Service<S> {
	private final TypeToken<S> theType;

	/** @param type The type of the service */
	public TypeDefinedService(TypeToken<S> type) {
		theType = type;
	}

	@Override
	public String getName() {
		return theType.toString();
	}

	@Override
	public Class<S> getServiceType() {
		return TypeTokens.getRawType(theType);
	}

	/** @return The type of the service */
	public TypeToken<S> getType() {
		return theType;
	}

	@Override
	public int hashCode() {
		return theType.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof TypeDefinedService && theType.equals(((TypeDefinedService<?>) obj).theType);
	}

	@Override
	public String toString() {
		return theType.toString();
	}
}
