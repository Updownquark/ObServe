<?xml version="1.0" encoding="UTF-8"?>

<quick xmlns:base="Quick-Base v0.1" xmlns:expresso="Expresso-Base v0.1" with-extension="window"
	title="`Simple Quick Shading Demo`" close-action="exit" x="app.x" y="app.y" width="app.w" height="app.h">
	<head>
		<imports>
			<import>org.qommons.Colors</import>
		</imports>
		<models>
			<model name="app">
				<value name="x" type="int" init="-1" />
				<value name="y" type="int" init="-1" />
				<value name="w" type="int" init="500" />
				<value name="h" type="int" init="400" />
			</model>
			<model name="shading">
				<value name="helper" init="new org.observe.quick.swing.ShadeTestHelper()" />
				<value name="lightSource" init="45.0f" />
				<value name="maxShading" init="0.5f" />
				<value name="lightColor" type="java.awt.Color" init="`red`" />
				<value name="shadowColor" type="java.awt.Color" init="`blue`" />
				<value name="cornerRadius" init="5" />
				<value name="rotateLight" init="false" />
				<value name="fadeShading" init="false" />
				<timer name="lightSourceTimer" active="rotateLight" frequency="`250ms`">
					lightSource=helper.advanceLightSource(lightSource)
				</timer>
				<timer name="fadeShadingTimer" active="fadeShading" frequency="`250ms`">
					maxShading=helper.advanceMaxShading(maxShading)
				</timer>
			</model>
		</models>
		<style-sheet>
			<import-style-sheet name="base" ref="classpath://org/observe/quick/base/quick-base.qss" />
			<style element="box">
				<style attr="light-source">shading.lightSource</style>
				<style attr="max-shade-amount">shading.maxShading</style>
				<style attr="corner-radius">shading.cornerRadius</style>
				<style attr="light-color">shading.lightColor</style>
				<style attr="shadow-color">shading.shadowColor</style>
			</style>
		</style-sheet>
	</head>
	<field-panel>
		<model>
			<value name="lightColorDialogVisible" init="false" />
			<value name="shadowColorDialogVisible" init="false" />
		</model>
		<general-dialog visible="lightColorDialogVisible" title="`Select Light Color`" modal="true">
			<color-chooser value="shading.lightColor" />
		</general-dialog>
		<general-dialog visible="shadowColorDialogVisible" title="`Select Shadow Color`" modal="true">
			<color-chooser value="shading.shadowColor" />
		</general-dialog>
		<box field-label="`Light Source`" fill="true" layout="inline-layout" orientation="horizontal" main-align="justify">
			<slider min="0" max="359.9999999" value="shading.lightSource" />
			<label>&#x00b0;  Rotate:</label>
			<check-box value="shading.rotateLight" />
		</box>
		<box field-label="`Max Shading`" fill="true" layout="inline-layout" orientation="horizontal" main-align="justify">
			<slider min="0" max="100" value="shading.maxShading * 100" />
			<label>%  Fade:</label>
			<check-box value="shading.fadeShading" />
		</box>
		<button field-label="`Light Color`" action="lightColorDialogVisible=true">Colors.toString(shading.lightColor)
			<style attr="font-color">shading.lightColor</style>
		</button>
		<button field-label="`Shadow Color`" action="shadowColorDialogVisible=true">Colors.toString(shading.shadowColor)
			<style attr="font-color">shading.shadowColor</style>
		</button>
		<slider field-label="`Corner Radius`" fill="true" min="0" max="25" value="shading.cornerRadius" />
		<box fill="true" v-fill="true" layout="simple-layout">
			<box left="0" width="`50%`" top="0" height="`33%`" layout="inline-layout" orientation="vertical" main-align="center" cross-align="center">
				<model>
					<raised-shading name="myShading" />
				</model>
				<style attr="shading">myShading</style>
				<label>This box uses raised round shading</label>
			</box>
			<box left="`50%`" width="`50%`" top="0" height="`33%`" layout="inline-layout" orientation="vertical" main-align="center" cross-align="center">
				<model>
					<raised-shading name="myShading" round="false" />
				</model>
				<style attr="shading">myShading</style>
				<label>This box uses raised square shading</label>
			</box>
			<box left="0" width="`50%`" top="`33%`" height="`33%`" layout="inline-layout" orientation="vertical" main-align="center" cross-align="center">
				<model>
					<raised-shading name="myShading" round="false" horizontal="false" />
				</model>
				<style attr="shading">myShading</style>
				<label>This box uses raised shading only in the vertical dimension</label>
			</box>
			<box left="`50%`" width="`50%`" top="`33%`" height="`33%`" layout="inline-layout" orientation="vertical" main-align="center" cross-align="center">
				<model>
					<raised-shading name="myShading" round="false" vertical="false" />
				</model>
				<style attr="shading">myShading</style>
				<label>This box uses raised shading only in the horizontal dimension</label>
			</box>
			<box left="0" width="`100%`" top="`66%`" height="`34%`" layout="inline-layout" orientation="vertical" main-align="center" cross-align="center">
				<model>
					<custom-shading name="myShading" unit-width="50" unit-height="50" stretch-x="false" stretch-y="false"
						lit="shading.helper.shadeCustom(x, y, width, height)" />
				</model>
				<style attr="shading">myShading</style>
				<label>This box uses a custom shader</label>
				<label>You should see tiles with water drop patterns</label>
			</box>
		</box>
	</field-panel>
</quick>
