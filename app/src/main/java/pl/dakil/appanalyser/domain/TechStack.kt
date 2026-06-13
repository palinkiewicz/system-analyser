package pl.dakil.appanalyser.domain

enum class TechStack(val displayName: String, val description: String) {
    FLUTTER(
        "Flutter",
        "Created by Google, Flutter uses the Dart language to compile directly to native machine code for high performance. It renders its UI using its own graphics engine (Impeller/Skia), ensuring exceptionally smooth, 60fps+ animations across platforms. This architecture makes it highly efficient, though it results in slightly larger initial binary sizes."
    ),
    REACT_NATIVE(
        "React Native",
        "Developed by Meta, React Native utilizes JavaScript or TypeScript to bridge with native platform UI components. It offers solid rendering speed for standard apps, though heavy computational tasks can introduce latency due to the JavaScript-to-native bridge. It remains highly efficient for rapid cross-platform development thanks to its massive ecosystem."
    ),
    JETPACK_COMPOSE(
        "Jetpack Compose",
        "Created by Google, Jetpack Compose is Android's modern, declarative UI toolkit written entirely in Kotlin. It runs natively on Android with exceptional efficiency, eliminating XML layout overhead through a compiler-optimized runtime. It delivers fast rendering speeds and deep system integration, though it is fundamentally restricted to Android for native builds."
    ),
    XAMARIN(
        "Xamarin / .NET MAUI",
        "Developed by Microsoft, Xamarin (now .NET MAUI) utilizes C# and the .NET framework to build cross-platform apps. It achieves near-native performance by compiling directly to native binaries on iOS and using an optimized virtual machine on Android. It is highly efficient for enterprise environments heavily invested in the Microsoft ecosystem."
    ),
    CORDOVA(
        "Cordova / Ionic / Capacitor",
        "Originating from Adobe/Apache, this suite (including Ionic and Capacitor) wraps web technologies like HTML, CSS, and JavaScript inside a native container. It relies on the system's WebView, which results in lower performance and slower execution speeds compared to compiled frameworks. It is highly efficient for development speed but poorly suited for resource-intensive applications."
    ),
    NATIVESCRIPT(
        "NativeScript",
        "Created by Progress (Telerik) and now community-driven, NativeScript allows developers to use JavaScript, TypeScript, or Angular/Vue to build cross-platform apps. It features direct, bridge-free access to native APIs, which provides better performance and speed than standard web-view wrappers. While efficient for leveraging web skills, its UI thread can occasionally bottleneck during heavy processing."
    ),
    UNITY(
        "Unity Engine",
        "Created by Unity Technologies, this powerful cross-platform game engine primarily uses C# for scripting. It is heavily optimized for real-time graphics, delivering exceptional execution speed and rendering efficiency for gaming and interactive simulations. While incredibly performant for multimedia, its runtime overhead makes it less efficient for lightweight, standard UI-driven applications."
    ),
    UNREAL_ENGINE(
        "Unreal Engine",
        "Developed by Epic Games, Unreal Engine uses C++ and a visual scripting system called Blueprints to deliver industry-leading performance. It is heavily optimized for high-end 3D graphics and complex logic, offering exceptional speed at the cost of high battery and resource consumption. Due to its massive runtime engine size, it is inefficient for traditional, text-based mobile applications."
    ),
    GODOT(
        "Godot Engine",
        "Developed by the Godot community as an open-source project, this lightweight game engine uses GDScript, C#, and C++. It is remarkably fast and lightweight, offering high graphics efficiency with a tiny engine footprint compared to its larger competitors. It balances performance perfectly for 2D and mid-tier 3D mobile games."
    ),
    COMPOSE_MULTIPLATFORM(
        "Compose Multiplatform",
        "Developed by JetBrains based on Google's Jetpack Compose, this framework shares declarative UIs across Android, iOS, desktop, and web using Kotlin. It compiles directly to native code and utilizes Skiko to render the UI canvas, matching Flutter's smooth rendering speeds. It provides excellent development efficiency by allowing complete UI and logic sharing across platforms."
    ),
    KOTLIN_MULTIPLATFORM(
        "Kotlin Multiplatform",
        "Created by JetBrains, Kotlin Multiplatform allows sharing business logic across iOS and Android using Kotlin while keeping the UI completely native. Because it compiles to native binaries, it runs at 100% native speed with zero runtime bridge overhead. It offers maximum performance efficiency, though developers must write platform-specific UIs unless paired with Compose."
    ),
    TAURI(
        "Tauri Mobile",
        "Developed by the Tauri community, Tauri Mobile brings its lightweight desktop architecture to mobile using Rust for backend logic and standard web technologies for the frontend. It bypasses heavy runtimes by utilizing the system's native WebView, resulting in incredibly small binary sizes and high efficiency. Performance is fast for UI tasks, though data passing between JavaScript and Rust can introduce minor overhead."
    ),
    SVELTE_NATIVE(
        "Svelte Native",
        "Developed as a community-driven project, Svelte Native combines the Svelte web framework with NativeScript to produce mobile apps. It utilizes JavaScript or TypeScript but compiles the UI at build time, eliminating a virtual DOM to achieve faster execution speeds and lower memory consumption. It provides highly efficient performance compared to standard hybrid frameworks by directly manipulating native mobile widgets."
    ),
    TITANIUM(
        "Titanium SDK",
        "Created by Appcelerator, Titanium SDK uses JavaScript to map directly to native iOS and Android user interface components. It executes via a JavaScript runtime on the device, offering decent rendering speed but suffering from performance drops during intensive data processing. While efficient for rapid prototyping in the early mobile era, its ecosystem and modern adoption have significantly diminished."
    ),
    QT(
        "Qt",
        "Developed by The Qt Company and the Qt Project, Qt is a cross-platform framework that utilizes C++ and QML. It compiles directly to native machine code, delivering blazing-fast speeds, high rendering performance, and exceptional memory efficiency suitable for embedded systems and mobile apps alike. However, its heavy C++ foundation can result in a steeper learning curve and larger application binaries."
    ),
    KIVY(
        "Kivy",
        "Created by the Kivy Organization, Kivy is an open-source Python library for developing multi-touch application software. It relies on OpenGL ES 2 for graphics rendering, ensuring fast, GPU-accelerated UI performance across Android, iOS, and desktop. However, because it bundles a Python interpreter, application startup times can be slow, and binary sizes are relatively large and less memory-efficient."
    ),
    BEEWARE(
        "BeeWare",
        "Developed by the BeeWare community, this suite of tools allows developers to write native mobile apps using Python. Unlike frameworks that use a webview or custom canvas, BeeWare compiles Python code to run against actual native OS widgets, aiming for true native look and feel. While execution speed is bounded by the Python interpreter on mobile, it offers excellent development efficiency for Python developers."
    ),
    RUBYMOTION(
        "RubyMotion",
        "Created by HipByte, RubyMotion allows developers to write native iOS and Android apps using the Ruby language. It compiles Ruby code directly into native machine binaries using an LLVM-based compiler, delivering true native execution speed and full access to platform APIs without a performance bridge. However, it is a commercial product with a niche community, affecting its modern development efficiency."
    ),
    NATIVE(
        "Native (XML Java/Kotlin)",
        "Rooted in the official Android development standards by Google, this approach uses Java or Kotlin alongside XML for layout design. It offers the absolute highest performance, execution speed, and battery efficiency possible because it runs directly on the Android runtime without translation layers. While it lacks cross-platform code sharing, it provides zero-latency access to device hardware and the smallest possible binary overhead."
    ),
    UNKNOWN(
        "Unknown",
        "This placeholder represents an unidentified or unsupported technology stack within the analyzer's codebase. It possesses no specific origin, language framework, or performance metrics until the underlying code can be properly resolved. It is utilized purely for error handling and fallback categorization."
    )
}
