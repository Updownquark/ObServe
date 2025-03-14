<?xml version="1.0" encoding="UTF-8"?>

<quick xmlns:quickX="Quick-X v0.1" xmlns:expresso="Expresso-Base v0.1" with-extension="window"
	title="`Simple Quick Color Demo`" close-action="exit">
	<head>
		<imports>
			<import>java.awt.Color</import>
			<import>org.qommons.Colors</import>
		</imports>
		<models>
			<model name="app">
				<value name="bg" type="Color" init="`white`" />
				<value name="bgBrightness">Colors.getBrightness(bg)</value>
				<value name="manualFG" type="Color" init="`black`" />
				<value name="autoFG" init="false" />
				<value name="fg" type="Color">autoFG ? (bgBrightness>0.5f ? `black` : `white`) : manualFG</value>
				<value name="text" type="String" init="`This is some text`" />
			</model>
		</models>
	</head>
	<box layout="inline-layout" orientation="vertical" main-align="justify" cross-align="justify">
		<text-field value="app.text">
			<style attr="color">app.bg</style>
			<style attr="font-color">app.fg</style>
		</text-field>
		<box layout="inline-layout" orientation="vertical" cross-align="justify">
			<titled-border title="`Background`" />
			<color-chooser value="app.bg" />
			<field-panel>
				<label field-label="`Brightness:`" value="Colors.getBrightness(app.bg)" />
			</field-panel>
		</box>
		<box layout="inline-layout" orientation="vertical" cross-align="justify">
			<titled-border title="`Foreground`" />
			<check-box value="app.autoFG">`Auto`</check-box>
			<color-chooser visible="!app.autoFG" value="app.manualFG" />
			<field-panel visible="!app.autoFG">
				<label field-label="`Brightness:`" value="Colors.getBrightness(app.manualFG)" />
			</field-panel>
		</box>
	</box>
</quick>
