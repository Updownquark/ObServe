<?xml version="1.0" encoding="UTF-8"?>

<quick
	uses:base="quick-base.qtd"
	with-extension="window"
	x="ext.x" y="ext.y" width="ext.width" height="ext.height" visible="ext.visible">
	<head>
		<imports>
			<import>org.observe.util.swing.QuickSwingParser</import>
			<import>org.observe.util.swing.QuickSwingParser.QuickComponent</import>
			<import>org.observe.collect.ObservableCollection</import>
			<import>org.observe.util.swing.CategoryRenderStrategy</import>
		</imports>
		<models>
			<ext-model name="ext">
				<ext-value name="x" type="int" />
				<ext-value name="y" type="int" />
				<ext-value name="width" type="int" />
				<ext-value name="height" type="int" />
				<ext-value name="visible" type="boolean" />
				<ext-value name="ui" type="QuickComponent" />
				<ext-value name="cursorX" type="int" />
				<ext-value name="cursorY" type="int" />
			</ext-model>
			<model name="debug">
				<value name="selectedComponent" type="QuickComponent" />
			</model>
			<model name="internal">
				<value name="boundsToggle" type="boolean">true</value>
				<columns name="boundsColumns" type="QuickComponent" value-name="row" column-value-name="col">
					<column name="Top Left" value="row.getLocation()" />
					<column name="Size" value="row.getSize()" />
					<column name="Min" value="row.getMinimumSize()" />
					<column name="Pref" value="row.getPreferredSize()" />
					<column name="Max" value="row.getMaximumSize()" />
				</columns>
				<transform name="toggleBoundsColumns" source="boundsToggle">
					<map-to function="t->t ? boundsColumns : null" />
					<flatten />
				</transform>
				<list name="componentColumnLists" type="ObservableCollection{? extends CategoryRenderStrategy{QuickComponent, ?}}">
					<element>${toggleBoundsColumns}</element>
				</list>
				<transform name="componentColumns" source="componentColumnLists">
					<flatten />
				</transform>
			</model>
		</models>
	</head>
	<tabs>
		<box tab-id="components" tab-name="Components" layout="inline" orientation="vertical" main-align="justify" cross-align="justify">
			<box layout="inline" orientation="horizontal" main-align="leading">
			</box>
			<tree-table root="ext.ui" value-name="row" column-value-name="col" children="row.getChildren()" parent="row.getParent()"
				selection="debug.selectedComponent" columns="internal.componentColumns">
			</tree-table>
		</box>
	</tabs>
</quick>
