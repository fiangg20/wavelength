# Wavelength

A music player for Android with a Material 3 Expressive look, built with Kotlin and Jetpack Compose.
GitHub builds the APK for you. You do not need Android Studio.

## Get the APK

1. Unzip the file. Open the unzipped folder until you see `app`, `build.gradle.kts` and `settings.gradle.kts`. (If you see another folder with the same name inside, go into it.)
2. Go to github.com/new, type a name, click **Create repository**.
3. Click the **uploading an existing file** link. Select everything INSIDE the folder (not the folder itself), drag it into the page, wait until it finishes, then click **Commit changes**.
4. Click **Add file > Create new file**. In the name box type `.github/workflows/build.yml` (typing the slashes makes the folders). Open `build.yml.txt` from the zip, copy everything, paste it into the big box, click **Commit changes**.
5. Click the **Actions** tab. Wait 3 to 6 minutes for a green check. (If nothing starts, click the workflow name, then **Run workflow**.)
6. Click the finished run, scroll to **Artifacts**, download the APK zip, unzip it, copy `app-debug.apk` to the phone, and install it. Allow "install unknown apps" if Android asks.

This is a debug APK for personal use. It is not for the Play Store.

## Optional custom font

Put one variable `.ttf` file named `app_font.ttf` in `app/src/main/res/font/`, rebuild, then choose **Typeface > Custom** in Settings.

## If the build fails

Open the failed step, type `e: file` in **Search logs**, and paste those lines back to Claude.
