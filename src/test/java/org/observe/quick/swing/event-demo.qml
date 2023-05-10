<?xml version="1.0" encoding="UTF-8"?>

<quick uses:base="Quick-Base v0.1" uses:expresso="Expresso-Base v0.1" with-extension="window"
	title="`Quick Event Demo`" close-action="exit" width="window.width" height="window.height">
	<head>
		<models>
			<model name="app">
				<value name="eventType" type="String" />
				<value name="button" type="String" />
				<value name="x" type="int" />
				<value name="y" type="int" />
				<value name="alt" type="boolean" />
				<value name="ctrl" type="boolean" />
				<value name="shift" type="boolean" />
				<value name="scroll" type="int" />
			</model>
			<model name="window">
				<value name="width" init="300" />
				<value name="height" init="250" />
			</model>
		</models>
		<style-sheet>
			<!--<import-style-sheet name="searcher" ref="quick-testing.qss" />-->
		</style-sheet>
	</head>
	<field-panel>
		<label fill="true">
			Perform a mouse event over this label
			<on-click>
				<model>
					<action-group name="report">
						<action>app.eventType=`click`</action>
						<action>app.button=button.toString()</action>
						<action>app.x=x</action>
						<action>app.y=y</action>
						<action>app.alt=altPressed</action>
						<action>app.ctrl=ctrlPressed</action>
						<action>app.shift=shiftPressed</action>
						<action>app.scroll=0</action>
					</action-group>
				</model>
				report
			</on-click>
			<on-mouse-press>
				<model>
					<action-group name="report">
						<action>app.eventType=`press`</action>
						<action>app.button=button.toString()</action>
						<action>app.x=x</action>
						<action>app.y=y</action>
						<action>app.alt=altPressed</action>
						<action>app.ctrl=ctrlPressed</action>
						<action>app.shift=shiftPressed</action>
						<action>app.scroll=0</action>
					</action-group>
				</model>
				report
			</on-mouse-press>
			<on-mouse-release>
				<model>
					<action-group name="report">
						<action>app.eventType=`release`</action>
						<action>app.button=button.toString()</action>
						<action>app.x=x</action>
						<action>app.y=y</action>
						<action>app.alt=altPressed</action>
						<action>app.ctrl=ctrlPressed</action>
						<action>app.shift=shiftPressed</action>
						<action>app.scroll=0</action>
					</action-group>
				</model>
				report
			</on-mouse-release>
			<on-mouse-enter>
				<model>
					<action-group name="report">
						<action>app.eventType=`enter`</action>
						<action>app.button=null</action>
						<action>app.x=x</action>
						<action>app.y=y</action>
						<action>app.alt=altPressed</action>
						<action>app.ctrl=ctrlPressed</action>
						<action>app.shift=shiftPressed</action>
						<action>app.scroll=0</action>
					</action-group>
				</model>
				report
			</on-mouse-enter>
			<on-mouse-exit>
				<model>
					<action-group name="report">
						<action>app.eventType=`exit`</action>
						<action>app.button=null</action>
						<action>app.x=x</action>
						<action>app.y=y</action>
						<action>app.alt=altPressed</action>
						<action>app.ctrl=ctrlPressed</action>
						<action>app.shift=shiftPressed</action>
						<action>app.scroll=0</action>
					</action-group>
				</model>
				report
			</on-mouse-exit>
			<on-mouse-move>
				<model>
					<action-group name="report">
						<action>app.eventType=`move`</action>
						<action>app.button=null</action>
						<action>app.x=x</action>
						<action>app.y=y</action>
						<action>app.alt=altPressed</action>
						<action>app.ctrl=ctrlPressed</action>
						<action>app.shift=shiftPressed</action>
						<action>app.scroll=0</action>
					</action-group>
				</model>
				report
			</on-mouse-move>
			<on-scroll>
				<model>
					<action-group name="report">
						<action>app.eventType=`scroll`</action>
						<action>app.button=null</action>
						<action>app.x=x</action>
						<action>app.y=y</action>
						<action>app.alt=altPressed</action>
						<action>app.ctrl=ctrlPressed</action>
						<action>app.shift=shiftPressed</action>
						<action>app.scroll=scrollAmount</action>
					</action-group>
				</model>
				report
			</on-scroll>
		</label>
		<box field-label="`Event:`" layout="inline-layout" orientation="horizontal">
			<label value="app.button" />
			<label value="app.eventType" />
			<label> at </label>
			<label value="app.x" />
			<label>,</label>
			<label value="app.y" />
			<label visible="app.scroll!=0">Scroll:</label>
			<label visible="app.scroll!=0" value="app.scroll" />
		</box>
		<box field-label="`Modifiers:`" layout="inline-layout" orientation="horizontal">
			<label visible="app.alt">Alt</label>
			<label visible="app.ctrl">Ctrl</label>
			<label visible="app.shift">Shift</label>
		</box>
	</field-panel>
</quick>
