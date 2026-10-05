package com.example.util

import android.content.Context
import android.content.SharedPreferences
import com.example.ui.components.ProjectData
import org.json.JSONObject
import java.io.File

/**
 * Manages persistence and retrieval of ProjectData for SAIF AI Studio projects.
 */
object ProjectDataManager {

    private const val PREFS_NAME = "saif_ai_projects"
    private const val KEY_PREFIX_PROJECT = "project_data_"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Saves ProjectData to persistent storage (both SharedPreferences and project app_config.json).
     */
    fun saveProject(context: Context, project: ProjectData) {
        val sanitized = project.appName.replace(Regex("[^a-zA-Z0-9_]"), "_").ifBlank { "Project" }
        val json = JSONObject().apply {
            put("appName", project.appName)
            put("packageName", project.packageName)
            put("minSdk", project.minSdk)
            put("targetSdk", project.targetSdk)
            put("buildStudio", project.buildStudio)
            put("language", project.language)
        }

        // Save in SharedPreferences
        getPrefs(context).edit().putString(KEY_PREFIX_PROJECT + sanitized, json.toString()).apply()
        getPrefs(context).edit().putString(KEY_PREFIX_PROJECT + project.appName, json.toString()).apply()

        // Also save in project physical directory
        try {
            val projectDir = File(context.filesDir, "projects/$sanitized").apply { mkdirs() }
            val configFile = File(projectDir, "app_config.json")
            configFile.writeText(json.toString(2))

            // Save app icon bitmap if available
            if (project.iconBitmap != null) {
                val iconFile = File(projectDir, "app_icon.png")
                java.io.FileOutputStream(iconFile).use { out ->
                    project.iconBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                }
            }

            // Ensure physical starter layout, activity, and manifest exist immediately
            ensureProjectScaffolded(context, project)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Ensures that essential Android project files (main.xml layout, MainActivity, AndroidManifest)
     * exist on disk so that live layout previews and code compilers can function immediately.
     */
    fun ensureProjectScaffolded(context: Context, project: ProjectData) {
        try {
            val sanitized = project.appName.replace(Regex("[^a-zA-Z0-9_]"), "_").ifBlank { "Project" }
            val projectDir = File(context.filesDir, "projects/$sanitized").apply { mkdirs() }
            val layoutFile = File(projectDir, "app/src/main/res/layout/main.xml")
            if (!layoutFile.exists()) {
                layoutFile.parentFile?.mkdirs()
                layoutFile.writeText(
                    """<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:gravity="center"
    android:padding="24dp"
    android:background="#0F172A">

    <TextView
        android:id="@+id/title_text"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="${project.appName}"
        android:textColor="#38BDF8"
        android:textSize="26sp"
        android:textStyle="bold" />

    <TextView
        android:id="@+id/tv_counter"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="12dp"
        android:text="Ready to build. Tell SAIF AI your prompt!"
        android:textColor="#94A3B8"
        android:textSize="15sp"
        android:gravity="center" />

    <Button
        android:id="@+id/action_button"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="24dp"
        android:text="Tap to Test"
        android:background="#6D28D9"
        android:textColor="#FFFFFF" />

</LinearLayout>""".trimIndent()
                )
            }

            val pkgPath = project.packageName.replace('.', '/')
            val javaFile = File(projectDir, "app/src/main/java/$pkgPath/MainActivity.java")
            if (!javaFile.exists()) {
                javaFile.parentFile?.mkdirs()
                javaFile.writeText(
                    """package ${project.packageName};

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private int clickCount = 0;
    private TextView tvCounter;
    private Button actionButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        tvCounter = findViewById(R.id.tv_counter);
        actionButton = findViewById(R.id.action_button);

        if (actionButton != null) {
            actionButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    clickCount++;
                    if (tvCounter != null) {
                        tvCounter.setText("Interactions: " + clickCount);
                    }
                    Toast.makeText(MainActivity.this, "Welcome to ${project.appName}!", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
}""".trimIndent()
                )
            }

            val manifestFile = File(projectDir, "app/src/main/AndroidManifest.xml")
            if (!manifestFile.exists()) {
                manifestFile.parentFile?.mkdirs()
                manifestFile.writeText(
                    """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="${project.packageName}">

    <application
        android:allowBackup="true"
        android:icon="@drawable/app_icon"
        android:label="${project.appName}"
        android:theme="@style/AppTheme">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>""".trimIndent()
                )
            }

            val stringsFile = File(projectDir, "app/src/main/res/values/strings.xml")
            if (!stringsFile.exists()) {
                stringsFile.parentFile?.mkdirs()
                stringsFile.writeText(
                    """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">${project.appName}</string>
    <string name="welcome_message">Welcome to your new Android application built with SAIF AI Studio.</string>
</resources>""".trimIndent()
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Loads custom app icon bitmap for project if available.
     */
    fun loadProjectIcon(context: Context, appName: String): android.graphics.Bitmap? {
        val sanitized = appName.replace(Regex("[^a-zA-Z0-9_]"), "_").ifBlank { "Project" }
        val file = File(context.filesDir, "projects/$sanitized/app_icon.png")
        return if (file.exists()) {
            try {
                android.graphics.BitmapFactory.decodeFile(file.absolutePath)
            } catch (e: Exception) {
                null
            }
        } else null
    }

    /**
     * Retrieves ProjectData by app name.
     */
    fun getProject(context: Context, appName: String): ProjectData? {
        val sanitized = appName.replace(Regex("[^a-zA-Z0-9_]"), "_").ifBlank { "Project" }
        val icon = loadProjectIcon(context, appName)
        val prefs = getPrefs(context)
        val rawJson = prefs.getString(KEY_PREFIX_PROJECT + sanitized, null)
            ?: prefs.getString(KEY_PREFIX_PROJECT + appName, null)

        if (!rawJson.isNullOrBlank()) {
            val parsed = parseProjectJson(rawJson)
            if (parsed != null) return parsed.copy(iconBitmap = icon ?: parsed.iconBitmap)
        }

        // Try reading from disk app_config.json
        try {
            val configFile = File(context.filesDir, "projects/$sanitized/app_config.json")
            if (configFile.exists()) {
                val content = configFile.readText()
                val parsed = parseProjectJson(content)
                if (parsed != null) return parsed.copy(iconBitmap = icon ?: parsed.iconBitmap)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Fallback default ProjectData if project directory or pref exists
        if (hasProject(context, appName)) {
            return ProjectData(
                appName = appName,
                packageName = "com.example.${sanitized.lowercase()}",
                minSdk = 21,
                targetSdk = 34,
                buildStudio = "SAIF AI Studio",
                language = "Java",
                iconBitmap = icon
            )
        }
        return null
    }

    /**
     * Renames an existing project across prefs and directory storage.
     */
    fun renameProject(context: Context, oldName: String, newName: String, updatedProject: ProjectData) {
        if (oldName == newName) {
            saveProject(context, updatedProject)
            return
        }
        val oldSanitized = oldName.replace(Regex("[^a-zA-Z0-9_]"), "_").ifBlank { "Project" }
        val newSanitized = newName.replace(Regex("[^a-zA-Z0-9_]"), "_").ifBlank { "Project" }

        getPrefs(context).edit()
            .remove(KEY_PREFIX_PROJECT + oldSanitized)
            .remove(KEY_PREFIX_PROJECT + oldName)
            .apply()

        try {
            val oldDir = File(context.filesDir, "projects/$oldSanitized")
            val newDir = File(context.filesDir, "projects/$newSanitized")
            if (oldDir.exists() && oldDir.absolutePath != newDir.absolutePath) {
                oldDir.renameTo(newDir)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        saveProject(context, updatedProject)
    }

    /**
     * Deletes a project from preferences and disk.
     */
    fun deleteProject(context: Context, appName: String) {
        val sanitized = appName.replace(Regex("[^a-zA-Z0-9_]"), "_").ifBlank { "Project" }
        getPrefs(context).edit()
            .remove(KEY_PREFIX_PROJECT + sanitized)
            .remove(KEY_PREFIX_PROJECT + appName)
            .apply()
        try {
            val projectDir = File(context.filesDir, "projects/$sanitized")
            if (projectDir.exists()) {
                projectDir.deleteRecursively()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Checks if a project exists by app name in storage or preferences.
     */
    fun hasProject(context: Context, appName: String): Boolean {
        if (appName.isBlank()) return false
        val sanitized = appName.replace(Regex("[^a-zA-Z0-9_]"), "_").ifBlank { "Project" }
        val prefs = getPrefs(context)
        if (prefs.contains(KEY_PREFIX_PROJECT + sanitized) || prefs.contains(KEY_PREFIX_PROJECT + appName)) {
            return true
        }
        val projectDir = File(context.filesDir, "projects/$sanitized")
        return projectDir.exists() && projectDir.isDirectory
    }

    private fun parseProjectJson(rawJson: String): ProjectData? {
        return try {
            val obj = JSONObject(rawJson)
            ProjectData(
                appName = obj.optString("appName", "App"),
                packageName = obj.optString("packageName", "com.example.app"),
                minSdk = obj.optInt("minSdk", 21),
                targetSdk = obj.optInt("targetSdk", 34),
                buildStudio = obj.optString("buildStudio", "Android Studio"),
                language = obj.optString("language", "Java"),
                iconBitmap = null
            )
        } catch (e: Exception) {
            null
        }
    }
}
