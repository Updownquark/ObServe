<?xml version="1.0" encoding="UTF-8"?>

<quick
	uses:base="quick-base.qtd">
	<head>
		<models>
			<ext-model name="selectedComponent">
				<ext-value name="visible" type="boolean" />
				<ext-value name="x" type="int" />
				<ext-value name="y" type="int" />
				<ext-value name="width" type="int" />
				<ext-value name="height" type="int" />
				<ext-value name="tooltip" type="String" />
				<ext-action name="onMouse" type="Void" />
			</ext-model>
		</models>
	</head>
	<box layout="simple" opacity="0" onHover="selectedComponent.onMouse">
		<box layout="simple" visible="selectedComponent.visible" opacity="0.25" bg-color="blue"
			left="selectedComponent.x-1" top="selectedComponent.y-1"
			width="selectedComponent.width+2" height="selectedComponent.height+2"
			tooltip="selectedComponent.tooltip"
			/>
	</box>
</quick>
