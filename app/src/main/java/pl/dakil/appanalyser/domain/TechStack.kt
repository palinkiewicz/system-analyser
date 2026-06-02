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
    COMPOSE_MULTIPLATFORM("Compose Multiplatform"),
    KOTLIN_MULTIPLATFORM("Kotlin Multiplatform"),
    TAURI("Tauri Mobile"),
    SVELTE_NATIVE("Svelte Native"),
    TITANIUM("Titanium SDK"),
    QT("Qt"),
    KIVY("Kivy"),
    BEEWARE("BeeWare"),
    RUBYMOTION("RubyMotion"),
    NATIVE("Native (XML Java/Kotlin)"),
    UNKNOWN("Unknown")
}
