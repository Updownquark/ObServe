package org.observe.remote;

/** A content operation on a config */
public enum ConfigModificationType {
	/** Set the value ({@link ServiceObservableConfig#setValue(String)} */
	Modify,
	/** Set the name ({@link ServiceObservableConfig#setName(String)} */
	Rename,
	/** Delete the config from the tree ({@link ServiceObservableConfig#remove()} */
	Delete,
	/** Create the root or add a child ({@link ServiceObservableConfig#addChild(String)} */
	Add;
}
