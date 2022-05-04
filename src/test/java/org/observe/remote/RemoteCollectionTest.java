package org.observe.remote;

import org.junit.Test;
import org.observe.collect.ObservableCollection;

public class RemoteCollectionTest {
	@Test
	public void testRemoteCollections() {
		BinaryCollectionSerializer<String> serializer=new BinaryCollectionSerializer<>(String::getBytes, String::new);
		ObservableCollection<String> source=ObservableCollection.build(String.class).build();
		ObservableCollectionServer<String, byte [], byte []> server=new ObservableCollectionServer<>(source, serializer, null)
	}
}
