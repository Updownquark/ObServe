package org.observe.entity;

/** The ways in which a non-primitive entity's field value (e.g. collection, map, or entity) can be loaded */
public enum FieldLoadType {
	/** With this load type, the field may not be loaded until its value is actually called for */
	Lazy,
	/**
	 * With this load type, the field may be roughly initialized in the query, but its deep content may not be loaded. E.g. A child entity's
	 * primitive fields will be loaded, but any child entities in its fields may not be loaded until called for.
	 */
	Shallow,
	/** With this load type, the entire graph of all structures referred to by the field value will be loaded immediately */
	Deep;
}
