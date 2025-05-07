# Keep all classes that extend View, Activity, Fragment, etc.
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.view.View
-keep public class * extends androidx.fragment.app.Fragment

# Keep parcelable classes
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep serializable classes
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}
# Gson uses generic type information stored in a class file when working with fields.
-keepattributes Signature

# Gson specific classes
-keep class sun.misc.Unsafe { *; }
-keep class com.google.gson.** { *; }
-keep class com.google.gson.stream.** { *; }

# Application classes that will be serialized/deserialized over Gson
-keep class com.yourpackage.model.** { *; }
# Kotlin coroutines
-keepclassmembers class kotlinx.** {
    volatile <fields>;
}
# Picasso
-keep class com.squareup.picasso.** { *; }
-dontwarn com.squareup.picasso.**
# Volley
-keep class com.android.volley.** { *; }
-keep class org.apache.http.** { *; }
-dontwarn org.apache.http.**
# KSoap2
-keep class com.google.code.ksoap2.** { *; }
-dontwarn com.google.code.ksoap2.**
# ComplexView
-keep class com.github.ticherhaz.complexview.** { *; }
# SweetAlert
-keep class com.github.f0ris.sweetalert.** { *; }
# Material Components
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**
# AndroidX
-keep class androidx.** { *; }
-dontwarn androidx.**
# JUnit
-keep class org.junit.** { *; }
-keep class junit.** { *; }
-dontwarn org.junit.**
-dontwarn junit.**