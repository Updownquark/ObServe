<?xml version="1.0" encoding="UTF-8"?>

<quick uses:base="Quick-Base v0.1" with-extension="window" title="`Simple Quick Table Demo`" close-action="exit"
	width="window.width" height="window.height">
	<head>
		<models>
			<ext-model name="clArgs">
				<value name="targetQuickApp" type="String" />
				<list name="$UNMATCHED$" type="String" />
			</ext-model>
			<model name="app">
				<value name="qwysiwyg">new org.observe.quick.base.Qwysiwyg()</value>
				<hook name="modelLoad" on="onModelLoad">qwysiwyg.init(clArgs.targetQuickApp, clArgs.$UNMATCHED$)</hook>
				<hook name="targetChange" on="clArgs.targetQuickApp">qwysiwyg.init(clArgs.targetQuickApp, clArgs.$UNMATCHED$)</hook>
				<hook name="clArgsChange" on="clArgs.$UNMATCHED$">qwysiwyg.init(clArgs.targetQuickApp, clArgs.$UNMATCHED$)</hook>
			</model>
			<model name="window">
				<value name="width" init="640" />
				<value name="height" init="640" />
			</model>
		</models>
	</head>
	<box layout="inline-layout" orientation="vertical" main-align="justify" tooltip="app.qwysiwyg.tooltip">
		<scroll>
			<box role="row-header" layout="simple-layout"> <!-- This outer box is so we can control the width -->
				<text-area value="app.qwysiwyg.lineNumbers" editable="false" width="65px" />
			</box>
			<styled-text-area role="content" editable="false" value="app.qwysiwyg.documentRoot" children="node.children"
				post-text="node.getPostText()" tooltip="app.qwysiwyg.tooltip">
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
	</box>
</quick>
