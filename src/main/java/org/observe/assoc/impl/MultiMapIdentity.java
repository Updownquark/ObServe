package org.observe.assoc.impl;

import java.util.Objects;

class MultiMapIdentity {
	private final Object theKeyId;
	private final Object theValueId;

	MultiMapIdentity(Object keyId, Object valueId) {
		theKeyId = keyId;
		theValueId = valueId;
	}

	@Override
	public int hashCode() {
		return Objects.hash(theKeyId, theValueId);
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof MultiMapIdentity && theKeyId.equals(((MultiMapIdentity) obj).theKeyId)
			&& theValueId.equals(((MultiMapIdentity) obj).theValueId);
	}

	@Override
	public String toString() {
		return new StringBuilder("{keys:").append(theKeyId).append(", values:").append(theValueId).append("}").toString();
	}
}