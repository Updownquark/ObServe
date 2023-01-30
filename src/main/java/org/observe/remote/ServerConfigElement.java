package org.observe.remote;

import java.util.Objects;

import org.observe.remote.ObservableServiceClient.ClientId;

public class ServerConfigElement implements Comparable<ServerConfigElement> {
	public final ObservableServiceClient.ClientId owner;
	public final ByteAddress address;

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
