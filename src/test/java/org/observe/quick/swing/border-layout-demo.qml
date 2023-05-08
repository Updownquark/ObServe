<?xml version="1.0" encoding="UTF-8"?>

<quick uses:base="Quick-Base v0.1" uses:expresso="Expresso-Base v0.1" with-extension="window"
	title="`Quick Border Layout Demo`" close-action="exit" width="window.width" height="window.height">
	<head>
		<models>
			<model name="app">
				<value name="north" type="int" init="90"/>
				<value name="south" type="int" init="-90" />
				<value name="east" type="int" init="180" />
				<value name="west" type="int" init="-180" />
			</model>
			<model name="window">
				<value name="width" init="200" />
				<value name="height" init="150" />
			</model>
		</models>
		<style-sheet>
			<!--<import-style-sheet name="searcher" ref="quick-testing.qss" />-->
		</style-sheet>
	</head>
	<box layout="border-layout">
		<box layout="simple-layout" region="west" width="`20%`">
			<style attr="color">`red`</style>
			<line-border />
			<label h-center="`50%`" v-center="`50%`">W1</label>
		</box>
		<box layout="simple-layout" region="south" height="`20%`">
			<style attr="color">`blue`</style>
			<line-border />
			<label h-center="`50%`" v-center="`50%`">S
				<style attr="font-color">`white`</style>
			</label>
		</box>
		<box layout="simple-layout" region="west" width="`15%`">
			<style attr="color">`yellow`</style>
			<line-border />
			<label h-center="`50%`" v-center="`50%`">W2</label>
		</box>
		<box layout="simple-layout" region="north" height="`20%`">
			<style attr="color">`purple`</style>
			<line-border />
			<label h-center="`50%`" v-center="`50%`">N
				<style attr="font-color">`white`</style>
			</label>
		</box>
		<box layout="simple-layout" region="east" width="`15%`">
			<style attr="color">`aqua`</style>
			<line-border />
			<label h-center="`50%`" v-center="`50%`">E</label>
		</box>
		<box layout="simple-layout" region="center">
			<style attr="color">`green`</style>
			<line-border />
			<label h-center="`50%`" v-center="`50%`">C</label>
		</box>
	</box>
</quick>
