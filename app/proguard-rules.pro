# JNI bridge: native methods are looked up by name at runtime.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
-keep class su.neit.vaiwu.NativePet { *; }
-keep class su.neit.vaiwu.SoundSynth { *; }

# Data classes are constructed from C++ via JNI; constructors and field
# names must survive shrinking.
-keep class su.neit.vaiwu.PetSnapshot { <init>(...); }
-keep class su.neit.vaiwu.ShopItemSnapshot { <init>(...); }
-keep class su.neit.vaiwu.EquipmentSnapshot { <init>(...); }
-keep class su.neit.vaiwu.InventorySnapshot { <init>(...); }
-keep class su.neit.vaiwu.RpsResult { <init>(...); }
-keep class su.neit.vaiwu.BattleSnapshot { <init>(...); }
-keep class su.neit.vaiwu.CharacterProfile { <init>(...); }
-keep class su.neit.vaiwu.AssistantIntent { <init>(...); }
