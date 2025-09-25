<?xml version="1.0" encoding="UTF-8"?>

<expresso-external-document xmlns:draw-demo="Quick-Draw-Demo v0.1" fulfills="demo-shape-collection">
	<head role="head">
		<imports>
			<import>org.observe.quick.draw.*</import>
			<import>org.qommons.Colors</import>
		</imports>
		<models role="models">
			<ext-model name="attrs">
				<list name="shapes" type="ColoredShape" source-attr="shapes" />
				<value name="selected" type="ColoredShape" source-attr="selected" />
				<value name="alwaysVisible" type="boolean" source-attr="always-visible" />
			</ext-model>
		</models>
	</head>
	<shape-collection for-each="attrs.shapes" active-shape-as="shape">
		<rectangle visible="rect!=null &amp; (attrs.alwaysVisible || shape.isVisible())"
			left="rect.getX(Leading)" h-center="rect.getX(Center)" right="rect.getX(Trailing)"
			top="rect.getY(Leading)" v-center="rect.getY(Center)" bottom="rect.getY(Trailing)"
			width="rect.getWidth()" height="rect.getHeight()" rotation="rect.getRotation()/180*(float) Math.PI">
			<model>
				<value name="rect">shape instanceof ColoredRectangle ? (ColoredRectangle) shape : null</value>
				<hook name="selectOnFocus" on="focused">focused ? (attrs.selected=rect) : null</hook>
			<value name="bg">shape.getColor()</value>
			</model>
			<style attr="color">bg
				<style if="hovered">Colors.bleach(bg, 0.5f)</style>
			</style>
			<style attr="border-color">rect.getBorderColor()</style>
			<style attr="thickness">
				rect.getBorderThickness()
				<style if="pressed">rect.getBorderThickness()*2</style>
			</style>
		</rectangle>
		<ellipse visible="ellipse!=null &amp; (attrs.alwaysVisible || shape.isVisible())"
			left="ellipse.getX(Leading)" h-center="ellipse.getX(Center)" right="ellipse.getX(Trailing)"
			top="ellipse.getY(Leading)" v-center="ellipse.getY(Center)" bottom="ellipse.getY(Trailing)"
			width="ellipse.getWidth()" height="ellipse.getHeight()" rotation="ellipse.getRotation()/180*(float) Math.PI">
			<model>
				<value name="ellipse">shape instanceof ColoredEllipse ? (ColoredEllipse) shape : null</value>
				<hook name="selectOnFocus" on="focused">focused ? (attrs.selected=ellipse) : null</hook>
				<value name="bg">shape.getColor()</value>
			</model>
			<style attr="color">bg
				<style if="hovered">Colors.bleach(bg, 0.5f)</style>
			</style>
			<style attr="border-color">ellipse.getBorderColor()</style>
			<style attr="thickness">
				ellipse.getBorderThickness()
				<style if="pressed">ellipse.getBorderThickness()*2</style>
			</style>
		</ellipse>
		<polygon visible="polygon!=null &amp; (attrs.alwaysVisible || shape.isVisible())"
		 vertices="polygon.getVertices().getValues()" active-vertex-as="vertex"
			vertex-x="vertex.getX()" vertex-y="vertex.getY()">
			<model>
				<value name="polygon">shape instanceof ColoredPolygon ? (ColoredPolygon) shape : null</value>
				<hook name="selectOnFocus" on="focused">focused ? (attrs.selected=polygon) : null</hook>
				<value name="bg">shape.getColor()</value>
			</model>
			<style attr="color">bg
				<style if="hovered">Colors.bleach(bg, 0.5f)</style>
			</style>
			<style attr="border-color">polygon.getBorderColor()</style>
			<style attr="thickness">
				polygon.getBorderThickness()
				<style if="pressed">polygon.getBorderThickness()*2</style>
			</style>
		</polygon>
		<text visible="text!=null &amp; (attrs.alwaysVisible || shape.isVisible())"
			left="text.getX(Leading)" h-center="text.getX(Center)" right="text.getX(Trailing)"
			top="text.getY(Leading)" v-center="text.getY(Center)" bottom="text.getY(Trailing)"
			rotation="text.getRotation()/180*(float) Math.PI">
			text.getText()
			<model>
				<value name="text">shape instanceof ColoredText ? (ColoredText) shape : null</value>
				<hook name="selectOnFocus" on="focused">focused ? (attrs.selected=text) : null</hook>
				<value name="fg">shape.getColor()</value>
			</model>
			<style attr="font-color">fg
				<style if="hovered">Colors.bleach(fg, 0.5f)</style>
			</style>
			<style attr="font-size">text.getFontSize()</style>
		</text>
	</shape-collection>
</expresso-external-document>
