package pl.dakil.appanalyser.domain

enum class TechStack(val displayName: String) {
    FLUTTER("Flutter"),
    REACT_NATIVE("React Native"),
    JETPACK_COMPOSE("Jetpack Compose"),
    XAMARIN("Xamarin / .NET MAUI"),
    CORDOVA("Cordova / Ionic / Capacitor"),
    NATIVESCRIPT("NativeScript"),
    UNITY("Unity Engine"),
    UNREAL_ENGINE("Unreal Engine"),
    GODOT("Godot Engine"),
    NATIVE("Native (XML Java/Kotlin)"),
    UNKNOWN("Unknown")
}
