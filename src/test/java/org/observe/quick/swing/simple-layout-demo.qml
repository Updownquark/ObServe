<?xml version="1.0" encoding="UTF-8"?>

<quick uses:base="Quick-Base v0.1" uses:expresso="Expresso-Base v0.1" with-extension="window"
	title="`Simple Quick Layout Demo`" close-action="exit">
	<head>
		<models>
			<model name="app">
				<value name="north" type="int" init="90"/>
				<value name="south" type="int" init="-90" />
				<value name="east" type="int" init="180" />
				<value name="west" type="int" init="-180" />
			</model>
		</models>
		<style-sheet>
			<!--<import-style-sheet name="searcher" ref="quick-testing.qss" />-->
		</style-sheet>
	</head>
	<box layout="simple-layout">
		<label right="25%" top="2">N</label>
		<text-field value="app.north" top="2" h-center="50%" width="50%" />
		<label left="2" v-center="50%">W</label>
		<text-field value="app.west" h-center="25%" width="20%" v-center="50%" />
		<label right="2" v-center="50%">E</label>
		<text-field value="app.east" h-center="75%" width="20%" v-center="50%" />
		<label right="25%" bottom="2">S</label>
		<text-field value="app.south" bottom="2" h-center="50%" width="50%" />
	</box>
</quick>
