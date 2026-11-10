# R8 rules for the release build. Libraries bring their own rules; these
# cover the app's own classes that are read or written by reflection.

# Readable stack traces in the Play Console (the AAB carries the mapping file).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Gson needs the generic types of fields such as List<SavedAnswer>.
-keepattributes Signature

# Credential Manager: keep the Play Services provider it loads by reflection.
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** {
  *;
}
