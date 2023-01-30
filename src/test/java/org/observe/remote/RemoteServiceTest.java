package org.observe.remote;

import java.util.List;
import java.util.NavigableSet;

import org.junit.Assert;
import org.junit.Test;
import org.qommons.ThreadConstraint;

public class RemoteServiceTest {
	@Test
	public void testInMemory() {
		LocalObservableClient client1 = LocalObservableClient.create();
		LocalObservableClient client2 = LocalObservableClient.create();
		ServiceObservableConfig config1 = ServiceObservableConfig.createRoot(client1, "test", ThreadConstraint.ANY);
		ObservableConfigServer server1 = new ObservableConfigServer(client1, config1);
		ObservableConfigServer server2 = new ObservableConfigServer(client2, null);

		sync(server1, server2);
		ServiceObservableConfig config2 = server2.getConfig();
		Assert.assertNotNull(config2);
		// At least the root came through

		// The root doesn't have any allowed permissions and is owned by client 1, so client 2 shouldn't be able to do anything
		try {
			config2.setName("test2");
			Assert.assertFalse("Should have thrown an exception", true);
		} catch (UnsupportedOperationException e) {}
		try {
			config2.setValue("test2");
			Assert.assertFalse("Should have thrown an exception", true);
		} catch (UnsupportedOperationException e) {}
		try {
			config2.addChild("test2");
			Assert.assertFalse("Should have thrown an exception", true);
		} catch (UnsupportedOperationException e) {}

		config1.getRootData().allowForAllLocal(ConfigModificationType.Add, ObservableServiceRole.all());
		config1.getRootData().allowForAllLocal(ConfigModificationType.Rename, ObservableServiceRole.all());
		config1.getRootData().allowForAllLocal(ConfigModificationType.Modify, ObservableServiceRole.all());
		config1.getRootData().allowForAllLocal(ConfigModificationType.Delete, ObservableServiceRole.all());
		sync(server1, server2);
		config2.setName("test2");
		config2.setValue("test3");
		config2.addChild("test4");
		sync(server2, server1);
		Assert.assertEquals("test2", config1.getName());
		Assert.assertEquals("test3", config1.getValue());
		Assert.assertEquals("test4", config1.getContent().getFirst().getName());

		// Now client 1 doesn't have permission to muck with client 2's created config, but it can muck with its own
		config1.setName("test18");
		try {
			config1.getContent().getFirst().setValue("test2");
			Assert.assertFalse("Should have thrown an exception", true);
		} catch (UnsupportedOperationException e) {}
		try {
			config1.getContent().getFirst().remove();
			Assert.assertFalse("Should have thrown an exception", true);
		} catch (UnsupportedOperationException e) {}

		// Test delete, also test synchronization when there are un-sync'd changes in the receiver
		config2.getContent().getFirst().remove();
		sync(server2, server1);
		Assert.assertEquals(0, config1.getContent().size());

		sync(server1, server2);
		Assert.assertEquals("test18", config2.getName());
	}

	private static void sync(ObservableConfigServer source, ObservableConfigServer dest) {
		NavigableSet<ObservableServiceChange> changes = source.pollChanges(dest.getKnownChanges());
		List<SerializedObservableServerChangeSet> serializedChanges = SerializedObservableServerChangeSet.serialize(changes);
		dest.applyChanges(serializedChanges);
	}
}
