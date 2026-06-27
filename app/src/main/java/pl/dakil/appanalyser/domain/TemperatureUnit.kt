package pl.dakil.appanalyser.domain

enum class TemperatureUnit(val symbol: String) {
    CELSIUS("°C"),
    FAHRENHEIT("°F"),
    KELVIN("K");

    /** Converts a temperature given in Celsius to this unit. */
    fun fromCelsius(celsius: Float): Float = when (this) {
        CELSIUS -> celsius
        FAHRENHEIT -> celsius * 9f / 5f + 32f
        KELVIN -> celsius + 273.15f
    }

    companion object {
        fun fromName(name: String?): TemperatureUnit =
            entries.firstOrNull { it.name == name } ?: CELSIUS
    }
}
