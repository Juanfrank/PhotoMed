package com.photomed.app

/** Número escrito con punto → con el separador decimal del idioma de la prueba (español: coma). */
internal fun String.loc(): String = replace('.', java.text.DecimalFormatSymbols.getInstance().decimalSeparator)
