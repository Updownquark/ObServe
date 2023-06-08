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
		<text-area html="true" editable="false" value="app.qwysiwyg.documentDisplay">
			<on-mouse-move>app.qwysiwyg.hover(mouseRow, mouseColumn)</on-mouse-move>
			<on-click>app.qwysiwyg.clicked(mouseRow, mouseColumn, clickCount)</on-click>
			<on-mouse-exit>app.qwysiwyg.mouseExit()</on-mouse-exit>
		</text-area>
	</box>
</quick>
