package org.observe.remote;

import java.util.Objects;

import org.observe.remote.ObservableServiceClient.ClientId;

/** Represents an address of a {@link ServiceObservableConfig} among its siblings in the tree */
public class ServerConfigElement implements Comparable<ServerConfigElement> {
	/** The client that created the element */
	public final ObservableServiceClient.ClientId owner;
	/** The ordering address of the element */
	public final ByteAddress address;

	/**
	 * @param owner The client that created the element
	 * @param address The ordering address of the element
	 */
	public ServerConfigElement(ClientId owner, ByteAddress address) {
		this.owner = owner;
		this.address = address;
	}

	@Override
	public int compareTo(ServerConfigElement o) {
		int comp = address.compareTo(o.address);
		if (comp == 0)
			comp = owner.compareTo(o.owner);
		return comp;
	}

	/**
	 * @param config The config to compare to
	 * @return The order of this address compared to the given config
	 */
	public int compareTo(ServiceObservableConfig config) {
		int comp = address.compareTo(config.getAddress());
		if (comp == 0)
			comp = owner.compareTo(config.getOwner().getId());
		return comp;
	}

	@Override
	public int hashCode() {
		return Objects.hash(owner, address);
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof ServerConfigElement && owner.equals(((ServerConfigElement) obj).owner)
			&& address.equals(((ServerConfigElement) obj).address);
	}
}
