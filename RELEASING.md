# Release Guide: Publishing Pentagram to Google Play Store

## Prerequisites

- Android Studio (latest version recommended)
- Java JDK 17+
- Android SDK with build-tools 34
- Android NDK 25.2.9519653

## Step 1: Create Your Signing Keystore

Generate a keystore to sign your release builds. **Keep this file safe - you'll need it for all future updates!**

```bash
keytool -genkey -v \
  -keystore pentagram-release.jks \
  -alias pentagram \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

You'll be prompted for:
- Keystore password
- Key password
- Your name, organization, location details

Move the keystore to the project root:
```bash
mv pentagram-release.jks /Users/markcipolla/dev/pentagram/
```

## Step 2: Configure Keystore Properties

Copy the template and fill in your credentials:

```bash
cp keystore.properties.template keystore.properties
```

Edit `keystore.properties`:
```properties
storeFile=pentagram-release.jks
storePassword=YOUR_KEYSTORE_PASSWORD
keyAlias=pentagram
keyPassword=YOUR_KEY_PASSWORD
```

**IMPORTANT:** Never commit `keystore.properties` to version control!

## Step 3: Build the Release Bundle

Google Play requires Android App Bundle (AAB) format for new apps.

### Using Gradle (Command Line)

```bash
# Clean previous builds
./gradlew clean

# Build release AAB (App Bundle)
./gradlew bundleRelease

# Or build release APK
./gradlew assembleRelease
```

Output locations:
- AAB: `app/build/outputs/bundle/release/app-release.aab`
- APK: `app/build/outputs/apk/release/app-release.apk`

### Using Android Studio

1. Open the project in Android Studio
2. Build → Generate Signed Bundle/APK
3. Select "Android App Bundle"
4. Choose your keystore file
5. Enter keystore and key passwords
6. Select "release" build variant
7. Click "Create"

## Step 4: Test the Release Build

Before uploading to Play Store, test the signed build:

```bash
# Install release APK on device
adb install app/build/outputs/apk/release/app-release.apk

# Or use bundletool for AAB testing
bundletool build-apks --bundle=app/build/outputs/bundle/release/app-release.aab \
  --output=pentagram.apks \
  --ks=pentagram-release.jks \
  --ks-key-alias=pentagram

bundletool install-apks --apks=pentagram.apks
```

## Step 5: Create Google Play Developer Account

1. Go to https://play.google.com/console
2. Pay the one-time $25 registration fee
3. Complete identity verification (may take 48 hours)

## Step 6: Create Your App Listing

In Play Console:

1. **Create app** → Enter app name "Pentagram"
2. **Set up your app** → Complete all required sections:
   - App access (no special access needed)
   - Ads (does not contain ads)
   - Content rating (complete questionnaire)
   - Target audience (general audience)
   - News app (no)
   - COVID-19 (no)
   - Data safety (no data collected)
   - Government apps (no)
   - App category (Tools)

3. **Store listing**:
   - Copy content from `PLAY_STORE_LISTING.md`
   - Upload screenshots
   - Upload feature graphic (1024x500)
   - Add privacy policy URL (host `PRIVACY_POLICY.md` on GitHub Pages or similar)

## Step 7: Upload Your App Bundle

1. Go to **Release** → **Production** (or start with Internal testing)
2. Click **Create new release**
3. Upload `app-release.aab`
4. Add release notes
5. Review and roll out

## Step 8: Complete Review

Google will review your app (typically 1-7 days for new apps). They'll check for:
- Policy compliance
- Proper permissions usage
- Content rating accuracy
- Data safety declarations

## Updating Your App

For future releases:

1. Increment version in `app/build.gradle.kts`:
   ```kotlin
   versionCode = 2  // Increment each release
   versionName = "1.1"  // User-visible version
   ```

2. Build new AAB: `./gradlew bundleRelease`

3. Upload to Play Console → Create new release

## Troubleshooting

### Build Fails with Signing Error
- Verify `keystore.properties` has correct paths and passwords
- Ensure keystore file exists at specified location

### ProGuard Errors
- Check `proguard-rules.pro` for missing keep rules
- Enable `isMinifyEnabled = false` temporarily to debug

### Native Library Issues
- Ensure NDK 25.2.9519653 is installed
- Run `./gradlew clean` before rebuilding

## Backup Checklist

**Keep these files safe (outside git):**
- [ ] `pentagram-release.jks` - Your signing keystore
- [ ] `keystore.properties` - Your keystore credentials
- [ ] Keystore password (write it down securely)
- [ ] Key password (write it down securely)

**If you lose your keystore, you cannot update your app on Play Store!**

## Quick Reference

| Command | Description |
|---------|-------------|
| `./gradlew bundleRelease` | Build release AAB |
| `./gradlew assembleRelease` | Build release APK |
| `./gradlew clean` | Clean build artifacts |
| `./deploy.sh --release` | Build and install release APK |
