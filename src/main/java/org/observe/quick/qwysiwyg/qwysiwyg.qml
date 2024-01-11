<?xml version="1.0" encoding="UTF-8"?>

<quick xmlns:base="Quick-Base v0.1" xmlns:config="Expresso-Config v0.1" with-extension="window" title="app.qwysiwyg.title" close-action="exit"
	x="config.x" y="config.y" width="config.width" height="config.height">
	<head>
		<imports>
			<import>org.observe.quick.qwysiwyg.*</import>
		</imports>
		<models>
			<ext-model name="clArgs">
				<value name="targetQuickApp" type="String" />
				<list name="$UNMATCHED$" type="String" />
			</ext-model>
			<config name="config" config-name="qwysiwyg">
				<value name="x" type="int" />
				<value name="y" type="int" />
				<value name="width" type="int" default="860" />
				<value name="height" type="int" default="860" />
				<value name="split" type="double" default="65" />
			</config>
			<model name="app">
				<value name="qwysiwyg">new Qwysiwyg()</value>
				<hook name="modelLoad" on="onModelLoad">qwysiwyg.init(clArgs.targetQuickApp, clArgs.$UNMATCHED$)</hook>
				<hook name="targetChange" on="clArgs.targetQuickApp">qwysiwyg.init(clArgs.targetQuickApp, clArgs.$UNMATCHED$)</hook>
				<hook name="clArgsChange" on="clArgs.$UNMATCHED$">qwysiwyg.init(clArgs.targetQuickApp, clArgs.$UNMATCHED$)</hook>
			</model>
		</models>
	</head>
	<split orientation="vertical" split-position="config.split * `1%`">
		<scroll>
			<box role="row-header" layout="simple-layout"> <!-- This outer box is so we can control the width -->
				<text-area value="app.qwysiwyg.lineNumbers" editable="false" width="`65px`" />
			</box>
			<text-area role="content" editable="false" tooltip="app.qwysiwyg.document.getTooltip()">
				<model>
					<value name="hoveredNode" type="StyledQuickDocument.DocumentComponent" />
				</model>
				<dynamic-styled-document root="app.qwysiwyg.document.getRoot()" children="node.children" post-text="node.getPostText()"
				selection-start-value="app.qwysiwyg.selectedNode" selection-end-value="app.qwysiwyg.selectedEndNode"
				selection-start-offset="app.qwysiwyg.selectedStartIndex" selection-end-offset="app.qwysiwyg.selectedEndIndex">
					<model>
						<hook name="updateHoverNode" on="node">hoveredNode=node</hook>
					</model>
					<text-style>
						<style attr="font-weight" if="node!=null &amp;&amp; node.isBold()">`bold`</style>
						<style attr="font-color">node==null ? null : node.getFontColor()</style>
						<style attr="underline" if="node!=null &amp;&amp; node.isActiveLink()">true</style>
					</text-style>
				</dynamic-styled-document>
				<style>
					<style attr="mouse-cursor" if="hoveredNode!=null &amp;&amp; hoveredNode.isActiveLink()">HAND</style>
				</style>
				<on-mouse-enter>app.qwysiwyg.controlPressed(ctrlPressed)</on-mouse-enter>
				<on-key-press>app.qwysiwyg.controlPressed(ctrlPressed)</on-key-press>
				<on-key-release>app.qwysiwyg.controlPressed(ctrlPressed)</on-key-release>
				<on-mouse-move>app.qwysiwyg.hover(hoveredNode, ctrlPressed)</on-mouse-move>
				<on-click>app.qwysiwyg.clicked(hoveredNode, clickCount, ctrlPressed)</on-click>
				<on-mouse-exit>app.qwysiwyg.mouseExit()</on-mouse-exit>
			</text-area>
		</scroll>
		<tabs>
			<table tab-id="&quot;Watch Expressions&quot;" tab-name="`Watch Expressions`" rows="app.qwysiwyg.watchExpressions" active-value-name="ex">
				<column name="`Context`" value="ex.getContextString()" />
				<column name="`Expression`" value="ex.getExpressionText()">
					<column-edit column-edit-value-name="newEx" type="modify-row-value" commit="ex.setExpressionText(newEx)">
						<text-field />
					</column-edit>
				</column>
				<column name="`Value`" value="ex.getValue()" />
				<multi-value-action allow-for-empty="true" icon="&quot;/icons/add.png&quot;"
					enabled="app.qwysiwyg.canAddWatchExpression(app.qwysiwyg.selectedNode)">
					app.qwysiwyg.addWatchExpression(app.qwysiwyg.selectedNode)
				</multi-value-action>
				<value-action allow-for-multiple="true" icon="&quot;/icons/remove.png&quot;" value-name="exp">
					exp.remove()
				</value-action>
			</table>
			<table tab-id="`Watch Actions`" tab-name="`Watch Actions`" rows="app.qwysiwyg.watchActions" active-value-name="wa">
				<model>
					<value name="editWatchVisible" init="false" />
					<value name="editingAction" type="Qwysiwyg.WatchAction" />
					<transform name="editActionType" source="editingAction">
						<map-to source-as="a">
							<map-with>a.getActionType()</map-with>
							<map-reverse type="modify-source" target-as="newType">a.setActionType(newType)</map-reverse>
						</map-to>
					</transform>
					<transform name="editActionConfig" source="editingAction">
						<map-to source-as="a">
							<map-with>a.getActionConfiguration()</map-with>
							<map-reverse type="modify-source" target-as="newConfig" enabled="a.isActionConfigurable()">a.setActionConfiguration(newConfig)</map-reverse>
						</map-to>
					</transform>
				</model>
				<general-dialog visible="editWatchVisible" title="`Select Action`" modal="true">
					<field-panel>
						<combo values="Qwysiwyg.WatchActionType.values()" value="editActionType" />
						<text-field fill="true" value="editActionConfig" />
					</field-panel>
				</general-dialog>
				<column name="`Context`" value="wa.getContextString()" />
				<column name="`Condition`" value="wa.getExpressionText()">
					<column-edit column-edit-value-name="newEx" type="modify-row-value" commit="wa.setExpressionText(newEx)">
						<text-field />
					</column-edit>
				</column>
				<column name="`Action`" value="wa.getActionText()" column-value-name="action">
					<label value="action">
						<model>
							<action-group name="editWatchAction">
								<action>editingAction=wa</action>
								<action>editWatchVisible=true</action>
							</action-group>
						</model>
						<on-click button="left">editWatchAction</on-click>
					</label>
				</column>
				<multi-value-action allow-for-empty="true" icon="`/icons/add.png`"
					enabled="app.qwysiwyg.canAddWatchExpression(app.qwysiwyg.selectedNode)">
					app.qwysiwyg.addWatchAction(app.qwysiwyg.selectedNode)
				</multi-value-action>
				<value-action allow-for-multiple="true" icon="`/icons/remove.png`" value-name="exp">
					exp.remove()
				</value-action>
			</table>
			<box tab-id="&quot;Style&quot;" tab-name="`Style`"
				layout="inline-layout" orientation="vertical" main-align="justify" cross-align="justify">
				<box layout="inline-layout" orientation="horizontal">
					<combo values="app.qwysiwyg.availableStyles" value="app.qwysiwyg.selectedStyle" />
				</box>
				<table rows="app.qwysiwyg.styleDebugValues" active-value-name="row">
					<column name="`Source File`" value="row.getSourceFile()">
						<style attr="with-text.font-weight" if="row!=null &amp;&amp; row.isActive()">`bold`</style>
						<label value="columnValue" tooltip="row.getFullSourceFile()" />
					</column>
					<column name="`Source`" value="row.getSourceElement()">
						<style attr="with-text.font-weight" if="row!=null &amp;&amp; row.isActive()">`bold`</style>
						<label value="columnValue">
							<style if="row!=null &amp;&amp; row.isSourceElementLink()">
								<style attr="underline">true</style>
								<style attr="font-color">`blue`</style>
							</style>
							<on-click>row.followSourceElementLink()</on-click>
						</label>
					</column>
					<column name="`Condition`" value="row.getCondition()">
						<style attr="with-text.font-weight" if="row!=null &amp;&amp; row.isActive()">`bold`</style>
						<text-area rows="1" value="" tooltip="row.getConditionTooltip(mousePosition)">
							<dynamic-styled-document root="columnValue" children="node==null ? null : node.children"
								post-text="node==null ? null : node.getPostText()">
								<text-style>
									<style attr="font-weight" if="node!=null &amp;&amp; node.isBold()">`bold`</style>
									<style attr="font-color">node==null ? null : node.getFontColor()</style>
									<style attr="underline" if="node!=null &amp;&amp; node.isActiveLink()">true</style>
								</text-style>
							</dynamic-styled-document>
						</text-area>
					</column>
					<column name="`Value`" value="row.getValueExpression()">
						<style attr="with-text.font-weight" if="row!=null &amp;&amp; row.isActive()">`bold`</style>
					</column>
					<column name="`Active Value`" value="row.getCurrentValue()">
						<style attr="with-text.font-weight" if="row!=null &amp;&amp; row.isActive()">`bold`</style>
						<style attr="with-text.font-slant" if="row!=null &amp;&amp; !row.isActive()">`italic`</style>
					</column>
				</table>
			</box>
		</tabs>
	</split>
</quick>
