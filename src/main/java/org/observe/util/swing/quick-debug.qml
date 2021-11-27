<?xml version="1.0" encoding="UTF-8"?>

<quick
	uses:base="quick-base.qtd"
	with-extension="window"
	x="ext.x" y="ext.y" width="ext.width" height="ext.height" visible="ext.visible">
	<head>
		<imports>
			<import type="org.observe.util.swing.QuickSwingParser" />
			<import type="org.observe.util.swing.QuickSwingParser.QuickComponent" />
			<import type="org.observe.ObservableCollection" />
			<import type="org.observe.util.swing.CategoryRenderStrategy" />
		</imports>
		<models>
			<ext-model name="ext">
				<ext-value name="x" type="int" />
				<ext-value name="y" type="int" />
				<ext-value name="width" type="int" />
				<ext-value name="height" type="int" />
				<ext-value name="visible" type="boolean" />
				<ext-value name="ui" type="QuickSwingParser.QuickUI" />
			</ext-model>
			<model name="debug">
				<value name="selectedComponent" type="QuickComponent" />
			</model>
			<model name="internal">
				<value name="boundsToggle" type="boolean">true</value>
				<columns name="boundsColumns" type="QuickComponent">
					<column name="X,Y" value="QuickComponent::getLocation" />
					<column name="Size" value="QuickComponent::getSize" />
					<column name="Min" value="QuickComponent::getMinimumSize" />
					<column name="Pref" value="QuickComponent::getPreferredSize" />
					<column name="Max" value="QuickComponent::getMaximumSize" />
				</columns>
				<transform name="toggleBoundsColumns" source="boundsToggle">
					<map function="t->t ? boundsColumns : null" />
					<flatten />
				</transform>
				<list name="componentColumnLists" type="ObservableCollection{CategoryRenderStrategy{QuickComponent, ?}}">
					<element>toggleBoundsColumns</element>
				</list>
				<transform name="componentColumns" source="componentColumnLists">
					<flatten />
				</transform>
			</model>
		</models>
	</head>
	<tabs>
		<box tab-name="Components" layout="inline" orientation="vertical" main-align="justify" cross-align="justify">
			<box layout="inline" orientation="horizontal" main-align="leading">
			</box>
			<tree-table root="ext.ui.getComponent()" children="parent->parent.getChildren()" selection="debug.selectedComponent"
				columns="componentColumns">
			</tree-table>
		</box>
	</tabs>
</quick>
