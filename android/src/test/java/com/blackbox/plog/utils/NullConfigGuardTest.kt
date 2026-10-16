package com.blackbox.plog.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.blackbox.plog.pLogs.config.PLogPreferences
import com.blackbox.plog.pLogs.impl.LogWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * PLog must not take the process down when a write reaches it before
 * PLog.applyConfigurations() has populated the config.
 *
 * That is the state at app launch until the host's init call returns. The file
 * helpers used to read `PLogImpl.getConfig()?.x!!`, which throws a
 * NullPointerException on PLog's background thread and kills the process
 * (Rise Crashlytics d6a94161: "AsyncTask #2 ... NullPointerException at
 * FileUtilsKt.setupFilePaths").
 *
 * The state reproduced here: PLogPreferences is initialised (applyConfigurations
 * does that first) but no config has been saved yet, so
 * PLogImpl.getConfig() returns null. Nothing below calls applyConfigurations.
 *
 * Needs Robolectric: without an Android environment ConstantsKt fails to
 * initialise, which would fail the tests for a reason unrelated to the guards.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NullConfigGuardTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Before
    fun preferencesReadyButNoConfigSaved() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PLogPreferences.init(context)
        PLogPreferences.getInstance().clear(context)
    }

    @Test
    fun setupFilePathsWithNoConfigReturnsTheFileNameInsteadOfThrowing() {
        assertEquals("name", setupFilePaths("name"))
        assertEquals("", setupFilePaths())
        assertEquals("name", setupFilePaths("name", isPLog = false))
    }

    @Test
    fun checkFileExistsWithNoConfigCreatesTheFileAndDoesNotThrow() {
        val path = File(tmp.root, "nested/dir/a.csv").path

        val file = checkFileExists(path)

        assertNotNull(file)
        assertTrue("the file should have been created", File(path).exists())
    }

    @Test
    fun appendToFileWithNoConfigDoesNotThrowWhenTheFileIsMissing() {
        val path = File(tmp.root, "missing/b.csv").path

        appendToFile(path, "hello")

        assertTrue("the file should have been created", File(path).exists())
    }

    @Test
    fun appendToFileWithNoConfigAppendsToAnExistingFile() {
        val file = tmp.newFile("c.csv")

        appendToFile(file.path, "one")
        appendToFile(file.path, "two")

        assertEquals("onetwo", file.readText())
    }

    @Test
    fun writeToFileWithNoConfigDoesNotThrow() {
        val file = File(tmp.root, "d.csv")

        writeToFile(file.path, "data")

        assertTrue(file.exists())
    }

    @Test
    fun shouldWriteLogWithNoConfigDoesNotThrowAndAllowsASmallFile() {
        val file = tmp.newFile("e.csv")

        val (shouldWrite, _) = LogWriter.shouldWriteLog(file)

        assertTrue("an empty file is under the default size limit", shouldWrite)
    }
}
