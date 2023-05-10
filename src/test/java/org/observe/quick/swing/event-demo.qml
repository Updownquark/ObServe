<?xml version="1.0" encoding="UTF-8"?>

<quick uses:base="Quick-Base v0.1" uses:expresso="Expresso-Base v0.1" with-extension="window"
	title="`Quick Event Demo`" close-action="exit" width="window.width" height="window.height">
	<head>
		<models>
			<model name="mouse">
				<value name="eventType" type="String" />
				<value name="button" type="String" />
				<value name="x" type="int" />
				<value name="y" type="int" />
				<value name="alt" type="boolean" />
				<value name="ctrl" type="boolean" />
				<value name="shift" type="boolean" />
				<value name="scroll" type="int" />
			</model>
			<model name="key">
				<value name="eventType" type="String" />
				<value name="typedChar" type="char" />
				<value name="alt" type="boolean" />
				<value name="ctrl" type="boolean" />
				<value name="shift" type="boolean" />
				<set name="pressedKeys" type="org.observe.quick.KeyCode" />
				<value name="pressedKeysValue">pressedKeys</value>
				<transform name="pressedKeysStr" source="pressedKeysValue">
					<refresh on="pressedKeys" />
					<map-to source-as="set">
						<map-with>set.toString()</map-with>
					</map-to>
				</transform>
				<hook name="hook" on="eventType">System.out.println(eventType)</hook>
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
						<action>mouse.eventType=`click`</action>
						<action>mouse.button=button.toString()</action>
						<action>mouse.x=x</action>
						<action>mouse.y=y</action>
						<action>mouse.alt=altPressed</action>
						<action>mouse.ctrl=ctrlPressed</action>
						<action>mouse.shift=shiftPressed</action>
						<action>mouse.scroll=0</action>
					</action-group>
				</model>
				report
			</on-click>
			<on-mouse-press>
				<model>
					<action-group name="report">
						<action>mouse.eventType=`press`</action>
						<action>mouse.button=button.toString()</action>
						<action>mouse.x=x</action>
						<action>mouse.y=y</action>
						<action>mouse.alt=altPressed</action>
						<action>mouse.ctrl=ctrlPressed</action>
						<action>mouse.shift=shiftPressed</action>
						<action>mouse.scroll=0</action>
					</action-group>
				</model>
				report
			</on-mouse-press>
			<on-mouse-release>
				<model>
					<action-group name="report">
						<action>mouse.eventType=`release`</action>
						<action>mouse.button=button.toString()</action>
						<action>mouse.x=x</action>
						<action>mouse.y=y</action>
						<action>mouse.alt=altPressed</action>
						<action>mouse.ctrl=ctrlPressed</action>
						<action>mouse.shift=shiftPressed</action>
						<action>mouse.scroll=0</action>
					</action-group>
				</model>
				report
			</on-mouse-release>
			<on-mouse-enter>
				<model>
					<action-group name="report">
						<action>mouse.eventType=`enter`</action>
						<action>mouse.button=null</action>
						<action>mouse.x=x</action>
						<action>mouse.y=y</action>
						<action>mouse.alt=altPressed</action>
						<action>mouse.ctrl=ctrlPressed</action>
						<action>mouse.shift=shiftPressed</action>
						<action>mouse.scroll=0</action>
					</action-group>
				</model>
				report
			</on-mouse-enter>
			<on-mouse-exit>
				<model>
					<action-group name="report">
						<action>mouse.eventType=`exit`</action>
						<action>mouse.button=null</action>
						<action>mouse.x=x</action>
						<action>mouse.y=y</action>
						<action>mouse.alt=altPressed</action>
						<action>mouse.ctrl=ctrlPressed</action>
						<action>mouse.shift=shiftPressed</action>
						<action>mouse.scroll=0</action>
					</action-group>
				</model>
				report
			</on-mouse-exit>
			<on-mouse-move>
				<model>
					<action-group name="report">
						<action>mouse.eventType=`move`</action>
						<action>mouse.button=null</action>
						<action>mouse.x=x</action>
						<action>mouse.y=y</action>
						<action>mouse.alt=altPressed</action>
						<action>mouse.ctrl=ctrlPressed</action>
						<action>mouse.shift=shiftPressed</action>
						<action>mouse.scroll=0</action>
					</action-group>
				</model>
				report
			</on-mouse-move>
			<on-scroll>
				<model>
					<action-group name="report">
						<action>mouse.eventType=`scroll`</action>
						<action>mouse.button=null</action>
						<action>mouse.x=x</action>
						<action>mouse.y=y</action>
						<action>mouse.alt=altPressed</action>
						<action>mouse.ctrl=ctrlPressed</action>
						<action>mouse.shift=shiftPressed</action>
						<action>mouse.scroll=scrollAmount</action>
					</action-group>
				</model>
				report
			</on-scroll>
		</label>
		<box field-label="`Mouse Event:`" layout="inline-layout" orientation="horizontal">
			<label value="mouse.button" />
			<label value="mouse.eventType" />
			<label> at </label>
			<label value="mouse.x" />
			<label>,</label>
			<label value="mouse.y" />
			<label visible="mouse.scroll!=0">Scroll:</label>
			<label visible="mouse.scroll!=0" value="mouse.scroll" />
		</box>
		<box field-label="`Mouse Modifiers:`" layout="inline-layout" orientation="horizontal">
			<label visible="mouse.alt">Alt</label>
			<label visible="mouse.ctrl">Ctrl</label>
			<label visible="mouse.shift">Shift</label>
		</box>
		<text-field value="internalValue" fill="true" empty-text="`Perform key events in this text field`">
			<model>
				<value name="internalValue" type="String" />
			</model>
			<on-type>
				<model>
					<action-group name="report">
						<action>key.eventType=`type`</action>
						<action>key.alt=altPressed</action>
						<action>key.ctrl=ctrlPressed</action>
						<action>key.shift=shiftPressed</action>
						<action>key.typedChar=typedChar</action>
					</action-group>
				</model>
				report
			</on-type>
			<on-key-press>
				<model>
					<action-group name="report">
						<action>key.eventType=`Press`</action>
						<action>key.alt=altPressed</action>
						<action>key.ctrl=ctrlPressed</action>
						<action>key.shift=shiftPressed</action>
						<action>key.typedChar=(char) 0</action>
						<action>key.pressedKeys.add(keyCode)</action>
					</action-group>
				</model>
				report
			</on-key-press>
			<on-key-release>
				<model>
					<action-group name="report">
						<action>key.eventType=`Release`</action>
						<action>key.alt=altPressed</action>
						<action>key.ctrl=ctrlPressed</action>
						<action>key.shift=shiftPressed</action>
						<action>key.typedChar=(char) 0</action>
						<action>key.pressedKeys.remove(keyCode)</action>
					</action-group>
				</model>
				report
			</on-key-release>
		</text-field>
		<box field-label="`Key Event:`" layout="inline-layout" orientation="horizontal">
			<label value="key.eventType" />
			<label visible="key.typedChar!=0">Character:</label>
			<label visible="key.typedChar!=0" value="key.typedChar" />
		</box>
		<label field-label="`Pressed Keys:`" value="key.pressedKeysStr" />
		<box field-label="`Key Modifiers:`" layout="inline-layout" orientation="horizontal">
			<label visible="key.alt">Alt</label>
			<label visible="key.ctrl">Ctrl</label>
			<label visible="key.shift">Shift</label>
		</box>
	</field-panel>
</quick>
