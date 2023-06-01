package org.observe.quick;

public interface QuickValueEditorDef extends QuickComponentDef {
	int getDefaultEditClicks();

	void startEditing(QuickComponent component);

	boolean flush(QuickComponent component);

	void stopEditing(QuickComponent component);
}