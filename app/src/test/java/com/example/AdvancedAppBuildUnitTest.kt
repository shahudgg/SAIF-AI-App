package com.example

import com.example.builder.AutonomousAppBuilder
import com.example.compiler.AppCompilerEngine
import com.example.compiler.AppHtmlSynthesizer
import com.example.ui.components.ProjectData
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Rigorous Unit Test verifying that apps build and validate end-to-end
 * across HTML extraction, syntax verification, and HTML synthesis.
 */
class AdvancedAppBuildUnitTest {

    @Test
    fun testExtractHtmlDocumentSuccess() {
        val rawResponse = """
            Here is your app:
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <title>Calculator</title>
            </head>
            <body>
                <div id="calc">Calculator App</div>
            </body>
            </html>
            Enjoy the app!
        """.trimIndent()

        val extracted = AutonomousAppBuilder.extractHtmlDocument(rawResponse)
        assertNotNull("Extracted HTML should not be null", extracted)
        assertTrue("Extracted HTML should start with <!DOCTYPE", extracted!!.startsWith("<!DOCTYPE html", ignoreCase = true))
        assertTrue("Extracted HTML should end with </html>", extracted.endsWith("</html>", ignoreCase = true))
        assertFalse("Extracted HTML should not contain outside markdown/commentary", extracted.contains("Here is your app"))
    }

    @Test
    fun testExtractHtmlDocumentTruncationDetection() {
        val truncatedResponse = """
            <!DOCTYPE html>
            <html lang="en">
            <head><title>Incomplete</title></head>
            <body>
                <div class="container">
        """.trimIndent()

        val extracted = AutonomousAppBuilder.extractHtmlDocument(truncatedResponse)
        assertNull("Truncated response missing </html> should return null", extracted)
    }

    @Test
    fun testAppHtmlSynthesizerReturnsExistingHtml() {
        val project = ProjectData(
            appName = "Calculator",
            packageName = "com.example.calculator",
            minSdk = 24,
            targetSdk = 34,
            buildStudio = "Build Studio",
            language = "Java",
            iconBitmap = null
        )
        val tempDir = File.createTempFile("test_proj_", "").apply {
            delete()
            mkdirs()
        }
        try {
            val sampleHtml = "<!DOCTYPE html><html><head><!-- saif-ai-build --></head><body><h1>Working Calculator</h1></body></html>"
            File(tempDir, "index.html").writeText(sampleHtml)

            val synthesized = AppHtmlSynthesizer.synthesize(project, tempDir)
            assertEquals("Synthesized HTML should match existing index.html", sampleHtml, synthesized)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testAppHtmlSynthesizerThrowsWhenNoHtml() {
        val project = ProjectData(
            appName = "EmptyApp",
            packageName = "com.example.emptyapp",
            minSdk = 24,
            targetSdk = 34,
            buildStudio = "Build Studio",
            language = "Java",
            iconBitmap = null
        )
        val tempDir = File.createTempFile("test_empty_", "").apply {
            delete()
            mkdirs()
        }
        try {
            AppHtmlSynthesizer.synthesize(project, tempDir)
            fail("Should have thrown IllegalStateException when index.html does not exist")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("Build the app first") == true)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testRJavaGeneratorOutput() {
        val project = ProjectData(
            appName = "SmartApp",
            packageName = "com.test.smartapp",
            minSdk = 24,
            targetSdk = 34,
            buildStudio = "Build Studio",
            language = "Java",
            iconBitmap = null
        )
        val tempDir = File.createTempFile("test_rjava_", "").apply {
            delete()
            mkdirs()
        }
        try {
            val resDir = File(tempDir, "app/src/main/res/layout").apply { mkdirs() }
            val layoutFile = File(resDir, "main.xml")
            layoutFile.writeText("""
                <?xml version="1.0" encoding="utf-8"?>
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    android:id="@+id/main_container"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent">
                    <Button
                        android:id="@+id/btn_action"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Click Me" />
                </LinearLayout>
            """.trimIndent())

            val rJava = AppCompilerEngine.generateRJavaSource(project, tempDir)
            assertTrue(rJava.contains("package com.test.smartapp;"))
            assertTrue(rJava.contains("public final class R {"))
            assertTrue(rJava.contains("public static final class id {"))
            assertTrue(rJava.contains("public static final class layout {"))
            assertTrue(rJava.contains("btn_action"))
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
