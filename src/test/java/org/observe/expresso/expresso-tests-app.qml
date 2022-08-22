<?xml version="1.0" encoding="UTF-8"?>

<qonfig-app uses:app="Qonfig-App v0.1" app-file="expresso-tests.qml">
	<toolkit def="/org/observe/expresso/expresso.qtd">
		<value-type>org.observe.expresso.ExpressionValueType</value-type>
	</toolkit>
	<toolkit def="/org/observe/quick/quick-core.qtd" />
	<toolkit def="/org/observe/quick/quick-base.qtd" />
	<special-session>org.observe.expresso.ExpressoSessionImplV0_1</special-session>
	<special-session>org.observe.quick.QuickSessionImplV0_1</special-session>
	<interpretation>org.observe.expresso.ExpressoV0_1</interpretation>
	<interpretation>org.observe.quick.QuickCore</interpretation>
	<interpretation>org.observe.quick.QuickBase</interpretation>
</qonfig-app>
