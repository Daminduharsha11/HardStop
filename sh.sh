#!/bin/bash
set -e

OLD_PATH="app/src/main/java/com/example/detox"
NEW_ID="com.aegis.hardstop"
NEW_PATH="app/src/main/java/$(echo $NEW_ID | tr '.' '/')"

# 1. Create new directory structure
mkdir -p "$NEW_PATH"

# 2. Move all files/subfolders (preserving structure: data/, engine/, model/, etc.)
mv "$OLD_PATH"/* "$NEW_PATH"/

# 3. Remove now-empty old package dirs
rmdir -p app/src/main/java/com/example/detox 2>/dev/null || true

# 4. Update package declarations and imports in every Kotlin file
grep -rl "com.example.detox" app/src/main/java | xargs sed -i "s/com\.example\.detox/$NEW_ID/g"

# 5. Update applicationId (and namespace if present) in build.gradle
grep -rl "com.example.detox" app/build.gradle* 2>/dev/null | xargs sed -i "s/com\.example\.detox/$NEW_ID/g" || true

# 6. Update AndroidManifest.xml if it has an explicit package attr
sed -i "s/com\.example\.detox/$NEW_ID/g" app/src/main/AndroidManifest.xml

echo "Done. Verifying no leftovers:"
grep -r "com.example.detox" app/ || echo "None found."