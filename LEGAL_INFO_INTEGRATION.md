# Legal Information Display Integration

This document explains how to integrate the legal information display into your app's UI to comply with GPL v3 requirements.

## Files Created

1. **`app/src/main/res/raw/legal_info.txt`** - Legal notices text file
2. **`app/src/main/java/com/pentagram/airplay/LegalInfoActivity.kt`** - Activity to display legal info

## Integration Steps

### Step 1: Add Activity to AndroidManifest.xml

Add this inside the `<application>` tag in `app/src/main/AndroidManifest.xml`:

```xml
<activity
    android:name=".LegalInfoActivity"
    android:label="Legal Information"
    android:theme="@style/Theme.AppCompat.Light" />
```

### Step 2: Add Menu Item to MainActivity

#### Option A: Add to existing menu (if you have one)

If you already have a menu in `MainActivity`, add this item:

```xml
<!-- In app/src/main/res/menu/main_menu.xml -->
<item
    android:id="@+id/action_legal_info"
    android:title="Legal Information"
    android:icon="@android:drawable/ic_menu_info_details"
    app:showAsAction="never" />
```

Then in `MainActivity.kt`, handle the menu click:

```kotlin
override fun onOptionsItemSelected(item: MenuItem): Boolean {
    return when (item.itemId) {
        R.id.action_legal_info -> {
            startActivity(Intent(this, LegalInfoActivity::class.java))
            true
        }
        else -> super.onOptionsItemSelected(item)
    }
}

override fun onCreateOptionsMenu(menu: Menu): Boolean {
    menuInflater.inflate(R.menu.main_menu, menu)
    return true
}
```

#### Option B: Add button to MainActivity layout

Add a button to your main activity layout:

```xml
<!-- In your activity_main.xml or wherever appropriate -->
<Button
    android:id="@+id/btnLegalInfo"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:text="Legal Info"
    android:onClick="onLegalInfoClick" />
```

Then in `MainActivity.kt`:

```kotlin
fun onLegalInfoClick(view: View) {
    startActivity(Intent(this, LegalInfoActivity::class.java))
}
```

#### Option C: Add to Settings/About section (recommended)

If you have a settings or about screen, add a preference item that launches the legal info activity.

### Step 3: Test the Integration

1. Build and run the app
2. Navigate to the legal information screen
3. Verify that the text is displayed correctly
4. Verify that users can scroll through the entire text
5. Verify the back button works

## GPL v3 Compliance Note

GPL v3 Section 5(d) requires:

> "If the work has interactive user interfaces, each must display Appropriate
> Legal Notices; however, if the Program has interactive interfaces that do
> not display Appropriate Legal Notices, your work need not make them do so."

The `LegalInfoActivity` satisfies this requirement by providing:
- Copyright notice
- License information (GPL v3)
- Disclaimer of warranty
- How to view the full license
- How to obtain source code
- Attribution for third-party components

## Customization

You can customize the display by:

1. **Styling the Activity**: Modify `LegalInfoActivity.kt` to use a custom layout
2. **Updating Content**: Edit `app/src/main/res/raw/legal_info.txt`
3. **Adding Links**: Make URLs clickable by using `TextView.autoLink` or `Linkify`

Example with clickable links:

```kotlin
textView.apply {
    autoLinkMask = Linkify.WEB_URLS
    linksClickable = true
}
```

## Required Updates Before Distribution

Before distributing your app, make sure to:

1. Add your copyright year and name where indicated
2. Verify all repository URLs point to: https://github.com/markcipolla/pentagram
3. Update any additional placeholder text with your information

## Additional Resources

- Full GPL v3 text: https://www.gnu.org/licenses/gpl-3.0.html
- GPL v3 FAQ: https://www.gnu.org/licenses/gpl-faq.html
- How to use GNU licenses: https://www.gnu.org/licenses/gpl-howto.html
