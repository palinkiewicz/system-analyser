package pl.dakil.appanalyser.domain

import androidx.annotation.StringRes
import pl.dakil.appanalyser.R

enum class TechStack(
    @StringRes val displayNameRes: Int,
    @StringRes val descriptionRes: Int
) {
    FLUTTER(
        R.string.tech_stack_flutter_name,
        R.string.tech_stack_flutter_description
    ),
    REACT_NATIVE(
        R.string.tech_stack_react_native_name,
        R.string.tech_stack_react_native_description
    ),
    JETPACK_COMPOSE(
        R.string.tech_stack_jetpack_compose_name,
        R.string.tech_stack_jetpack_compose_description
    ),
    XAMARIN(
        R.string.tech_stack_xamarin_name,
        R.string.tech_stack_xamarin_description
    ),
    CORDOVA(
        R.string.tech_stack_cordova_name,
        R.string.tech_stack_cordova_description
    ),
    NATIVESCRIPT(
        R.string.tech_stack_nativescript_name,
        R.string.tech_stack_nativescript_description
    ),
    UNITY(
        R.string.tech_stack_unity_name,
        R.string.tech_stack_unity_description
    ),
    UNREAL_ENGINE(
        R.string.tech_stack_unreal_engine_name,
        R.string.tech_stack_unreal_engine_description
    ),
    GODOT(
        R.string.tech_stack_godot_name,
        R.string.tech_stack_godot_description
    ),
    COMPOSE_MULTIPLATFORM(
        R.string.tech_stack_compose_multiplatform_name,
        R.string.tech_stack_compose_multiplatform_description
    ),
    KOTLIN_MULTIPLATFORM(
        R.string.tech_stack_kotlin_multiplatform_name,
        R.string.tech_stack_kotlin_multiplatform_description
    ),
    TAURI(
        R.string.tech_stack_tauri_name,
        R.string.tech_stack_tauri_description
    ),
    SVELTE_NATIVE(
        R.string.tech_stack_svelte_native_name,
        R.string.tech_stack_svelte_native_description
    ),
    TITANIUM(
        R.string.tech_stack_titanium_name,
        R.string.tech_stack_titanium_description
    ),
    QT(
        R.string.tech_stack_qt_name,
        R.string.tech_stack_qt_description
    ),
    KIVY(
        R.string.tech_stack_kivy_name,
        R.string.tech_stack_kivy_description
    ),
    BEEWARE(
        R.string.tech_stack_beeware_name,
        R.string.tech_stack_beeware_description
    ),
    RUBYMOTION(
        R.string.tech_stack_rubymotion_name,
        R.string.tech_stack_rubymotion_description
    ),
    NATIVE(
        R.string.tech_stack_native_name,
        R.string.tech_stack_native_description
    ),
    UNKNOWN(
        R.string.tech_stack_unknown_name,
        R.string.tech_stack_unknown_description
    )
}