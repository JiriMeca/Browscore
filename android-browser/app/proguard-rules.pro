# Keep Room generated classes
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class *
-keep class androidx.room.* { *; }

# Keep Kotlin metadata
-keep class kotlin.Metadata { *; }
