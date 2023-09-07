<?xml version="1.0" encoding="UTF-8"?>

<quick xmlns:base="Quick-Base v0.1" xmlns:expresso="Expresso-Base v0.1" with-extension="window"
	title="`Simple Quick Layout Demo`" close-action="exit" width="window.width" height="window.height">
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
	<box layout="simple-layout">
		<label right="`24%`" top="5">N</label>
		<text-field value="app.north" top="2" h-center="`50%`" width="`50%`" />
		<label right="`8%`" v-center="`50%`">W</label>
		<text-field value="app.west" right="`49%`" width="`40%`" v-center="`50%`" />
		<label left="`92%`" v-center="`50%`">E</label>
		<text-field value="app.east" left="`51%`" width="`40%`" v-center="`50%`" />
		<label right="`24%`" bottom="`2xp`">S</label>
		<text-field value="app.south" bottom="`2xp`" h-center="`50%`" width="`50%`" />
	</box>
</quick>
