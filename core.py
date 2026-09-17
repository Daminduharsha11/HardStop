import os

output_file = "HardStop_Source_Code.txt"
files_to_include = [
    "app/src/main/AndroidManifest.xml",
    "app/src/main/java/com/aegis/hardstop/MainActivity.kt",
    "app/src/main/java/com/aegis/hardstop/DebugScreen.kt",
    "app/src/main/java/com/aegis/hardstop/HourlyRuleScreen.kt",
    "app/src/main/java/com/aegis/hardstop/NightRuleScreen.kt",
    "app/src/main/java/com/aegis/hardstop/SettingsScreen.kt",
    "app/src/main/java/com/aegis/hardstop/data/DetoxPreferences.kt",
    "app/src/main/java/com/aegis/hardstop/engine/ShizukuPackageEngine.kt",
    "app/src/main/java/com/aegis/hardstop/model/AppInfo.kt",
    "app/src/main/java/com/aegis/hardstop/receiver/BootReceiver.kt",
    "app/src/main/java/com/aegis/hardstop/security/SecureLockManager.kt",
    "app/src/main/java/com/aegis/hardstop/service/DetoxTimerService.kt",
    "app/src/main/java/com/aegis/hardstop/ui/AppPickerBottomSheet.kt",
    "app/src/main/java/com/aegis/hardstop/ui/Components.kt",
    "app/src/main/java/com/aegis/hardstop/ui/RuleAppListSection.kt",
    "app/src/main/java/com/aegis/hardstop/ui/theme/theme.kt",
    "app/src/main/java/com/aegis/hardstop/util/NotificationHelper.kt"
]

with open(output_file, "w", encoding="utf-8") as outfile:
    for filepath in files_to_include:
        outfile.write(f"\n{'='*80}\n")
        outfile.write(f"FILE: {filepath}\n")
        outfile.write(f"{'='*80}\n\n")
        if os.path.exists(filepath):
            with open(filepath, "r", encoding="utf-8") as infile:
                outfile.write(infile.read())
        else:
            outfile.write("[FILE NOT FOUND]\n")
        outfile.write("\n\n")

print(f"Successfully generated {output_file}!")
