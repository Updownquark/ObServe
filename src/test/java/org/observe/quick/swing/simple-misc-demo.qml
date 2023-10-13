<?xml version="1.0" encoding="UTF-8"?>

<quick xmlns:base="Quick-X v0.1" xmlns:expresso="Expresso-Base v0.1" with-extension="window"
	title="`Simple Miscellaneous Quick Demo`" close-action="exit" x="app.x" y="app.y" width="app.w" height="app.h">
	<head>
		<models>
			<model name="app">
				<value name="text" type="String" init="`This is some text`" />
				<value name="i1" type="int" />
				<value name="i2" type="int" />
				<value name="b" type="boolean" init="true" />
				<value name="x" type="int" />
				<value name="y" type="int" />
				<value name="w" type="int" />
				<value name="h" type="int" />
			</model>
		</models>
		<style-sheet>
			<style-set name="header">
				<style element="with-text" attr="font-size">16</style>
			</style-set>
		</style-sheet>
	</head>
	<box layout="inline-layout" orientation="vertical" cross-align="justify" name="root">
		<model>
			<value name="intValue" type="Integer" init="10" />
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
					<transform name="sliderValue" source="intValue">
						<map-to source-as="intV">
							<map-with>intV*1.0</map-with>
							<map-reverse type="replace-source" target-as="dblV" inexact="true">(int) Math.round(dblV)"</map-reverse>
						</map-to>
					</transform>
					<value name="min" init="0" />
					<value name="max" init="100" />
				</model>
				<style attr="color">`blue`</style>
				<box layout="border-layout">
					<label region="west">Min:</label>
					<text-field region="west" value="min" />
					<text-field region="east" value="max" />
					<label region="east">Max:</label>
				</box>
				<slider value="sliderValue" min="min" max="max" />
			</box>
		</field-panel>
	</box>
</quick>
