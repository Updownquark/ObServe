package org.observe.db.relational;

import java.security.PublicKey;

public interface EntitySource {
	String getName();

	PublicKey getKey();
}
