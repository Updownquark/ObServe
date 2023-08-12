<?xml version="1.0" encoding="UTF-8"?>

<quick uses:base="Quick-Base v0.1" uses:config="Expresso-Config v0.1" with-extension="window" title="app.qwysiwyg.title" close-action="exit"
	x="config.x" y="config.y" width="config.width" height="config.height">
	<head>
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
			</config>
			<model name="app">
				<value name="qwysiwyg">new org.observe.quick.qwysiwyg.Qwysiwyg()</value>
				<hook name="modelLoad" on="onModelLoad">qwysiwyg.init(clArgs.targetQuickApp, clArgs.$UNMATCHED$)</hook>
				<hook name="targetChange" on="clArgs.targetQuickApp">qwysiwyg.init(clArgs.targetQuickApp, clArgs.$UNMATCHED$)</hook>
				<hook name="clArgsChange" on="clArgs.$UNMATCHED$">qwysiwyg.init(clArgs.targetQuickApp, clArgs.$UNMATCHED$)</hook>
			</model>
		</models>
	</head>
	<box layout="inline-layout" orientation="vertical" main-align="justify" tooltip="app.qwysiwyg.tooltip">
		<scroll>
			<box role="row-header" layout="simple-layout"> <!-- This outer box is so we can control the width -->
				<text-area value="app.qwysiwyg.lineNumbers" editable="false" width="`65px`" />
			</box>
			<styled-text-area role="content" editable="false" value="app.qwysiwyg.documentRoot" children="node.children"
				post-text="node.getPostText()" tooltip="app.qwysiwyg.tooltip"
				selection-start-value="app.qwysiwyg.selectedNode" selection-end-value="app.qwysiwyg.selectedEndNode"
				selection-start-offset="app.qwysiwyg.selectedStartIndex" selection-end-offset="app.qwysiwyg.selectedEndIndex">
				<text-style>
					<style attr="font-weight" condition="node!=null &amp;&amp; node.isBold()">`bold`</style>
					<style attr="font-color">node==null ? null : node.getFontColor()</style>
					<style attr="underline" condition="node!=null &amp;&amp; node.isActiveLink()">true</style>
				</text-style>
				<style>
					<style attr="mouse-cursor" condition="app.qwysiwyg.hovered!=null &amp;&amp; app.qwysiwyg.hovered.isActiveLink()">HAND</style>
				</style>
				<on-mouse-enter>app.qwysiwyg.controlPressed(ctrlPressed)</on-mouse-enter>
				<on-key-press>app.qwysiwyg.controlPressed(ctrlPressed)</on-key-press>
				<on-key-release>app.qwysiwyg.controlPressed(ctrlPressed)</on-key-release>
				<on-mouse-move>app.qwysiwyg.hover(node, ctrlPressed)</on-mouse-move>
				<on-click>app.qwysiwyg.clicked(node, clickCount, ctrlPressed)</on-click>
				<on-mouse-exit>app.qwysiwyg.mouseExit()</on-mouse-exit>
			</styled-text-area>
		</scroll>
		<table rows="app.qwysiwyg.watchExpressions" value-name="ex">
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
		<box layout="inline-layout" orientation="vertical" main-align="justify" cross-align="justify">
			<titled-border title="`Style`" />
			<box layout="inline-layout" orientation="horizontal">
				<combo values="app.qwysiwyg.availableStyles" value="app.qwysiwyg.selectedStyle" />
			</box>
			<table rows="app.qwysiwyg.styleDebugValues">
					<style attr="with-text.font-weight" condition="value!=null &amp;&amp; value.isActive()">`bold`</style>
				<column name="`Source`"  value="value.getSourceElement()" />
				<column name="`Condition`" value="value.getCondition()" />
				<column name="`Value`" value="value.getValueExpression()" />
				<column name="`Active Value`" value="value.getCurrentValue()">
					<style attr="with-text.font-slant" condition="!value.isActive()">`italic`</style>
				</column>
			</table>
		</box>
	</box>
</quick>
