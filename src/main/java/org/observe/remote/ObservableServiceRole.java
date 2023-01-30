package org.observe.remote;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class ObservableServiceRole {
	private static ObservableServiceRole ALL;
	/** The singleton role that all clients always belong to */
	public static ObservableServiceRole all() {
		if (ALL == null)
			ALL = ObservableServiceClient.ALL.getOrCreateRole(0, "ALL");
		return ALL;
	}

	private final ObservableServiceClient theOwner;
	private final long theId;
	private final String theName;
	private Set<ObservableServiceRole> theInheritance;

	public ObservableServiceRole(ObservableServiceClient owner, long id, String name) {
		theOwner = owner;
		theId = id;
		theName = name;
		byte[] nameBytes = name.getBytes();
		if (name.length() > 0xffff)
			throw new IllegalArgumentException("Role names must occupy <=" + 0xffff + " bytes of space (" + nameBytes.length + ")");
	}

	public long getId() {
		return theId;
	}

	public String getName() {
		return theName;
	}

	public ObservableServiceClient getOwner() {
		return theOwner;
	}

	public Set<ObservableServiceRole> getInheritance() {
		return theInheritance == null ? Collections.emptySet() : theInheritance;
	}

	public boolean inherits(ObservableServiceRole role) {
		if (this == role)
			return true;
		if (theInheritance != null) {
			if (theInheritance.contains(role))
				return true;
			for (ObservableServiceRole inh : theInheritance) {
				if (inh.inherits(role))
					return true;
			}
		}
		return false;
	}

	public ObservableServiceRole addInheritance(ObservableServiceRole inheritance) {
		if (this == ALL)
			return this; // ALL contains all permissions, this is pointless. And it's a singleton, so it's dangerous.
		if (theInheritance == null)
			theInheritance = new LinkedHashSet<>();
		theInheritance.add(inheritance);
		return this;
	}

	@Override
	public String toString() {
		if (this == ALL)
			return theName;
		else
			return theOwner + "." + theName;
	}
}
