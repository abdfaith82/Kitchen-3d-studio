# Add project specific ProGuard / R8 rules here.
# Preserve stack traces for crash diagnostics
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Room Database (explicitly keep manually provided _Impl and Entities/DAOs)
-keep class com.example.data.local.KitchenDatabase { *; }
-keep class com.example.data.local.KitchenDatabase_Impl { *; }
-keep class com.example.data.local.dao.** { *; }
-keep class com.example.data.local.entity.** { *; }
-keep class com.example.data.local.relation.** { *; }
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Firebase Firestore Models (@IgnoreExtraProperties, @DocumentId, @ServerTimestamp)
-keepattributes Signature
-keepclassmembers class com.example.data.remote.FirestoreKitchenDesign {
    <init>(...);
    *;
}
-keepclassmembers class com.example.data.remote.FirestoreKitchenComponent {
    <init>(...);
    *;
}
-keepclassmembers class * {
    @com.google.firebase.firestore.DocumentId *;
    @com.google.firebase.firestore.ServerTimestamp *;
    @com.google.firebase.firestore.PropertyName *;
}
-keep @com.google.firebase.firestore.IgnoreExtraProperties class * { *; }

# Keep Domain Models used in serialization & JSON export
-keep class com.example.domain.model.** { *; }
-keep class com.example.core.math.Vector3 { *; }
