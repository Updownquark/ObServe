<?xml version="1.0" encoding="UTF-8"?>

<!-- This is a header comment
A bunch of this random junk is in here just to test out the XML parser
blah -->

<quick uses:base="Quick-Base v0.1" uses:expresso="Expresso-Base v0.1" with-extension="window"
	title="`Super Simple Quick Demo`" close-action="exit" x="app.x" y="app.y" width="app.w" height="app.h">
	<head>
		<?CONTENTLESS-INTRUCTION?>
		<?INSTRUCTION ?>
		<?INSTRUCTION CONTENT?>
		<models>
			<model name="app">
				<value name="text" type="String" init="`This is some text`" />
				<value name="i1" type="int" />
				<value name="i2" type="int" />
				<value name="x" type="int" />
				<value name="y" type="int" />
				<value name="w" type="int" />
				<value name="h" type="int" />
			</model>
		</models>
		<style-sheet>
			<!--<import-style-sheet name="searcher" ref="quick-testing.qss" />-->
		</style-sheet>
	</head>
	<box layout="inline" orientation="vertical" main-align="justify" cross-align="justify" name="root">
		<box layout="inline" orientation="horizontal" main-align="center" name="box1">
			<label name="label1">The value from the text field should be reflected in the label to the right</label>
		</box>
		<box layout="inline" orientation="horizontal" main-align="justify">
			<box layout="inline" orientation="horizontal">
				<text-field value="app.text" />
			</box>
			<box layout="inline" orientation="horizontal" main-align="trailing">
				<label value="app.text" tooltip="app.text"/>
			</box>
		</box>
		<label>The values in the text field on the right should be the sum of the values in the two text fields on the left</label>
		<label>Edits to the result field should be propagated back to the left-most field</label>
		<box layout="inline" orientation="horizontal" main-align="justify">
			<text-field value="app.i1" tooltip="(app.i1+app.i2)+&quot;-&quot;+app.i2+&quot;=&quot;+app.i1"/>
			<label>+</label>
			<text-field value="app.i2" />
			<label>=</label>
			<text-field value="app.i1+app.i2" tooltip="app.i1+&quot;-&quot;+app.i2+&quot;=&quot;+(app.i1+app.i2)" />
		</box>
		<box layout="inline" orientation="horizontal" main-align="center">
			<label>x=</label>
			<label value="app.x" />
			<label>y=</label>
			<label value="app.y" />
		</box>
		<box layout="inline" orientation="horizontal" main-align="center">
			<label>Size=</label>
			<label value="app.w" />
			<label>x</label>
			<label value="app.h" />
		</box>
	</box>
</quick>

<!-- This is a footer comment
blah -->

