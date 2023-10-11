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
				<column name="`Expression`" value="ex.getExpressionText()">
					<column-edit column-edit-value-name="newEx" type="modify-row-value" commit="ex.setExpressionText(newEx)">
						<text-field />
					</column-edit>
				</column>
				<column name="`Context`" value="ex.getContext()" />
				<column name="`Value`" value="ex.getValue()" />
				<multi-value-action allow-for-empty="true" icon="&quot;icons/add.png&quot;"
					enabled="app.qwysiwyg.canAddWatchExpression(app.qwysiwyg.selectedNode)">
					app.qwysiwyg.addWatchExpression(app.qwysiwyg.selectedNode)
				</multi-value-action>
				<value-action allow-for-multiple="true" icon="&quot;icons/remove.png&quot;" value-name="exp">
					exp.remove()
				</value-action>
			</table>
			<box tab-id="&quot;Style&quot;" tab-name="`Style`"
				layout="inline-layout" orientation="vertical" main-align="justify" cross-align="justify">
				<box layout="inline-layout" orientation="horizontal">
					<combo values="app.qwysiwyg.availableStyles" value="app.qwysiwyg.selectedStyle" />
				</box>
				<table rows="app.qwysiwyg.styleDebugValues" active-value-name="row">
					<style attr="with-text.font-weight" if="row!=null &amp;&amp; row.isActive()">`bold`</style>
					<column name="`Source File`" value="row.getSourceFile()">
						<label value="columnValue" tooltip="row.getFullSourceFile()" />
					</column>
					<column name="`Source`" value="row.getSourceElement()">
						<label value="columnValue">
							<style if="row!=null &amp;&amp; row.isSourceElementLink()">
								<style attr="underline">true</style>
								<style attr="font-color">`blue`</style>
							</style>
							<on-click>row.followSourceElementLink()</on-click>
						</label>
					</column>
					<column name="`Condition`" value="row.getCondition()">
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
					<column name="`Value`" value="row.getValueExpression()" />
					<column name="`Active Value`" value="row.getCurrentValue()">
						<style attr="with-text.font-slant" if="row!=null &amp;&amp; !row.isActive()">`italic`</style>
					</column>
				</table>
			</box>
		</tabs>
	</split>
</quick>
