<?xml version="1.0" encoding="UTF-8"?>

<quick xmlns:base="Quick-X v0.1" xmlns:expresso="Expresso-Base v0.1" with-extension="window"
	title="`Simple Miscellaneous Quick Demo`" close-action="exit">
	<head>
		<imports>
			<import>org.observe.quick.swing.SwingTestEntity</import>
		</imports>
		<models />
		<style-sheet>
			<import-style-sheet name="base" ref="classpath://org/observe/quick/base/quick-base.qss" />
			<import-style-sheet name="ext" ref="classpath://org/observe/quick/ext/quick-ext.qss" />
			<style-set name="header">
				<style element="with-text" attr="font-size">16</style>
			</style-set>
		</style-sheet>
	</head>
	<box layout="inline-layout" orientation="vertical" cross-align="justify">
		<model>
			<value name="intValue" type="Integer" init="10" />
			<value name="boolValue" init="false" />
			<value name="strValue" init="`ABC`" />
			<list name="strValues" type="String">{"ABC", "DEF", "GHI"}</list>
			<value name="entity1">new SwingTestEntity()</value>
			<value name="entity2">new SwingTestEntity()</value>
			<value name="entity3">new SwingTestEntity()</value>
			<value name="entity4">new SwingTestEntity()</value>
			<list name="entities" type="SwingTestEntity">{entity1, entity2, entity3, entity4}</list>
		</model>
		<label>This is a demo I created to just throw a bunch of miscellaneous widgets in.
			<style style-set="header" />
		</label>
		<label>This is just for Quick features that I may not have tested in other demos.
			<style style-set="header" />
		</label>
		<field-panel>
			<spinner field-label="`Spinner:`" value="intValue" columns="6"
				previous="(prev &lt; intValue &amp;&amp; prev >= -100) ? prev : null"
				next="(next > intValue &amp;&amp; next &lt;= 100) ? next : null">
				<model>
					<value name="prev">intValue-5</value>
					<value name="next">intValue+5</value>
				</model>
			</spinner>
			<box field-label="`Slider:`" fill="true" layout="inline-layout" orientation="vertical" cross-align="justify">
				<model>
					<value name="min" init="0" />
					<value name="max" init="100" />
				</model>
				<box layout="border-layout">
					<label region="west">Min:</label>
					<text-field region="west" value="min" />
					<text-field region="east" value="max" />
					<label region="east">Max:</label>
				</box>
				<slider value="intValue" min="min" max="max" />
			</box>
			<toggle-button field-label="`Toggle Button`" value="boolValue">`A toggle button`
				<style attr="icon">
					<style if="boolValue">`/icons/greenDot.png`</style>
					<style>`/icons/redDot.png`</style>
				</style>
			</toggle-button>
			<toggle-buttons field-label="`Toggle Buttons`" value="strValue" values="strValues" />
			<table fill="true" rows="entities" active-value-name="row">
				<column name="`Check`" value="row.getBoolean()" column-value-name="b">
					<check-box value="b" />
					<column-edit type="modify-row-value" commit="row.setBoolean(newB)" column-edit-value-name="newB">
						<check-box />
					</column-edit>
				</column>
				<column name="`Icon`" value="row.getBoolean()" column-value-name="b">
					<label icon="`/icons/`+(b ? &quot;green&quot; : &quot;red&quot;)+`Dot.png`" />
				</column>
				<column name="`Button`" value="row.getBoolean()" column-value-name="b">
					<button action="row.setBoolean(true)" icon="`/icons/`+(b ? &quot;red&quot; : &quot;green&quot;)+`Dot.png`">
						`Set`
						<style attr="mouse-cursor">
							<style if="b">HAND</style>
							<style>WAIT</style>
						</style>
					</button>
					<column-edit type="modify-row-value" column-edit-value-name="__" commit="row.setBoolean(true)" editable-if="!b">
						<button action="row.setBoolean(true)" icon="`/icons/`+(b ? &quot;red&quot; : &quot;green&quot;)+`Dot.png`">`Set`</button>
					</column-edit>
				</column>
			</table>
			<combo field-label="`Combo With Renderer Cursor`" fill="true" value="myValue" values="strValues" active-value-name="comboValue">
				<model>
					<transform name="myValue" source="strValue">
						<filter-accept source-as="str" test="`DEF`.equals(str) ? `Can't accept 'DEF'` : null" />
					</transform>
				</model>
				<label value="comboValue" value-name="renderValue" tooltip="`Hovering `+comboValue">
					<style attr="mouse-cursor">
						<style if="`ABC`.equals(renderValue)">HAND</style>
						<style if="`DEF`.equals(renderValue)">WAIT</style>
						<style>CROSSHAIR</style>
					</style>
				</label>
			</combo>
			<combo-button field-label="`Combo Button`" values="strValues" active-value-name="comboValue"
				action="strValue=comboValue">`Select a value`
				<label value="comboValue" value-name="renderValue" tooltip="`Hovering `+comboValue">
					<style attr="mouse-cursor">
						<style if="`ABC`.equals(renderValue)">HAND</style>
						<style if="`DEF`.equals(renderValue)">WAIT</style>
						<style>CROSSHAIR</style>
					</style>
				</label>
			</combo-button>
		</field-panel>
	</box>
</quick>
