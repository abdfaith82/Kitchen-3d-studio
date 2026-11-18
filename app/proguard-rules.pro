# Filament & Native 3D Rendering Protection
-keep class com.google.android.filament.** { *; }
-dontwarn com.google.android.filament.**

# GLTF & Math bindings
-keep class com.google.android.filament.gltfio.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# AndroidX & Material
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
