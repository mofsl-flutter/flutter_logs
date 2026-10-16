package com.blackbox.plog.utils

import android.util.Log
import com.blackbox.plog.pLogs.PLog
import com.blackbox.plog.pLogs.events.EventTypes
import com.blackbox.plog.pLogs.events.LogEvents
import com.blackbox.plog.pLogs.impl.PLogImpl
import com.blackbox.plog.pLogs.structure.DirectoryStructure
import com.blackbox.plog.pLogs.utils.LOG_FOLDER
import com.blackbox.plog.pLogs.utils.PART_FILE_CREATED_DATALOG
import com.blackbox.plog.pLogs.utils.PART_FILE_CREATED_PLOG
import com.blackbox.plog.pLogs.utils.PART_FILE_PREFIX
import com.blackbox.plog.tests.PLogTestHelper
import com.blackbox.plog.utils.PLogUtils.createDirIfNotExists
import java.io.File


private var currentNameOfDirectory = ""

fun writeToFile(path: String, data: String) {

    try {
        val file = File(path)
        // Ensure parent directory exists
        file.parentFile?.let { if (!it.exists()) it.mkdirs() }
        if (file.exists()) {
            file.printWriter().use { out ->
                out.println(data)
            }
        } else {
            if (PLogImpl.getConfig()?.debugFileOperations == true)
                Log.i(PLog.DEBUG_TAG, "writeToFile: File doesn't exist, creating a new file..")

            file.createNewFile()
            RxBus.send(LogEvents(EventTypes.NEW_EVENT_LOG_FILE_CREATED, file.name))
        }
    } catch (e: NullPointerException) {
        e.printStackTrace()
        e.message?.let { Log.e(PLog.DEBUG_TAG, it) }


        Log.i(PLog.DEBUG_TAG, "writeToFile: Unable to append to file.. ${e.message}")
    } catch (e: RuntimeException) {
        e.printStackTrace()
        e.message?.let { Log.e(PLog.DEBUG_TAG, it) }


        Log.i(PLog.DEBUG_TAG, "writeToFile: Unable to append to file.. ${e.message}")
    } catch (e: Exception) {
        e.printStackTrace()
        e.message?.let { Log.e(PLog.DEBUG_TAG, it) }


        Log.i(PLog.DEBUG_TAG, "writeToFile: Unable to write to file.. ${e.message}")
    }
}

fun appendToFile(path: String, data: String) {

    try {
        val file = File(path)
        // Ensure parent directory exists
        file.parentFile?.let { if (!it.exists()) it.mkdirs() }
        if (file.exists()) {
            file.appendText(data, Charsets.UTF_8)
        } else {

            // Log if the file does not exist and will be created
            if (PLogImpl.getConfig()?.debugFileOperations == true) {
                Log.i(PLog.DEBUG_TAG, "appendToFile: File doesn't exist, creating a new file..")
            }
            // Try creating the new file
            val fileCreated = file.createNewFile()
            if (fileCreated && file.name.isNotEmpty()) {
                // If the file is created, send the event
                RxBus.send(LogEvents(EventTypes.NEW_EVENT_LOG_FILE_CREATED, file.name))
            } else {
                // Log if the file creation failed
                Log.e(PLog.DEBUG_TAG, "appendToFile: Failed to create file: ${file.absolutePath}")
            }
        }
    } catch (e: NullPointerException) {
        e.printStackTrace()
        e.message?.let { Log.e(PLog.DEBUG_TAG, "$it == $path") }


        Log.i(PLog.DEBUG_TAG, "appendToFile: $path Unable to append to file.. ${e.message}")
    } catch (e: RuntimeException) {
        e.printStackTrace()
        e.message?.let { Log.e(PLog.DEBUG_TAG, "$it == $path") }


        Log.i(PLog.DEBUG_TAG, "appendToFile: $path Unable to append to file.. ${e.message}")
    } catch (e: Exception) {
        e.printStackTrace()
        e.message?.let { Log.e(PLog.DEBUG_TAG, "$it == $path") }


        Log.i(PLog.DEBUG_TAG, "appendToFile: $path Unable to append to file.. ${e.message}")
    }
}

fun checkFileExists(path: String, isPLog: Boolean = true): File {
    val pFile = File(path).parentFile

    // Ensure the parent directory exists
    if (pFile != null && !pFile.exists()) {
        val dirCreated = pFile.mkdirs()  // Create all non-existent parent directories
        if (!dirCreated) {
            Log.e(
                PLog.DEBUG_TAG,
                "checkFileExists: Failed to create directory: ${pFile.absolutePath}"
            )
        }
    }

    val file = File(path)
    try {
        if (!file.exists()) {
            if (PLogImpl.getConfig()?.debugFileOperations == true) {
                Log.i(PLog.DEBUG_TAG, "checkFileExists: File doesn't exist. Creating file.")
            }
            val fileCreated = file.createNewFile()
            if (!fileCreated) {
                Log.e(
                    PLog.DEBUG_TAG,
                    "checkFileExists: Failed to create file: ${file.absolutePath}"
                )
            }
            saveFileEvent(file, isPLog)
        }
    } catch (e: NullPointerException) {
        e.printStackTrace()
        e.message?.let { Log.e(PLog.DEBUG_TAG, "$it == $path") }


        Log.i(PLog.DEBUG_TAG, "checkFileExists: Unable to append to file.. ${e.message}")
    } catch (e: RuntimeException) {
        e.printStackTrace()
        e.message?.let { Log.e(PLog.DEBUG_TAG, "$it == $path") }


        Log.i(PLog.DEBUG_TAG, "checkFileExists: Unable to append to file.. ${e.message}")
    } catch (e: Exception) {
        e.printStackTrace()
        saveFileEvent(file, isPLog)

        if (PLogImpl.getConfig()?.debugFileOperations == true) {
            Log.i(PLog.DEBUG_TAG, "checkFileExists: ${e.message}")
        }
    }

    return file
}


private fun saveFileEvent(file: File, isPLog: Boolean = true) {
    try {
        // Safe handling of the debugFileOperations flag
        val debugFileOperations = PLogImpl.getConfig()?.debugFileOperations == true
        // Log if debugging is enabled
        if (debugFileOperations) {
            Log.i(PLog.DEBUG_TAG, "saveFileEvent: New file created: ${file.path}")
        }
        // Check if file creation is part of the expected process
        if (file.name != null) {
            if (isPLog && !file.name.contains(PART_FILE_PREFIX)) {
                PART_FILE_CREATED_PLOG = false
            } else if (!isPLog && !file.name.contains(PART_FILE_PREFIX)) {
                PART_FILE_CREATED_DATALOG = false
            }
        }
        // Ensure the file name is valid before sending the event
        if (file.name.isNotEmpty()) {
            RxBus.send(LogEvents(EventTypes.NEW_EVENT_LOG_FILE_CREATED, file.name))
        } else {
            Log.e(PLog.DEBUG_TAG, "saveFileEvent: Invalid file name - cannot send event.")
        }
        // Log the file creation process if debugging is enabled
        if (debugFileOperations) {
            Log.i(PLog.DEBUG_TAG, "New file created: ${file.path}")
        }
    } catch (e: NullPointerException) {
        // Handling NullPointerException and logging
        Log.e(PLog.DEBUG_TAG, "saveFileEvent: NullPointerException occurred: ${e.message} for file: ${file.path}")
        e.printStackTrace()
        Log.i(PLog.DEBUG_TAG, "Unable to save event to file: ${e.message}")
    } catch (e: RuntimeException) {
        // Handling RuntimeException and logging
        Log.e(PLog.DEBUG_TAG, "saveFileEvent: RuntimeException occurred: ${e.message} for file: ${file.path}")
        e.printStackTrace()
        Log.i(PLog.DEBUG_TAG, "Unable to save event to file: ${e.message}")
    } catch (e: Exception) {
        // Handling any other Exception and logging
        Log.e(PLog.DEBUG_TAG, "saveFileEvent: Exception occurred: ${e.message} for file: ${file.path}")
        e.printStackTrace()
        Log.i(PLog.DEBUG_TAG, "Unable to write event to file: ${e.message}")
    }
}
/*
 * This will setup directory structure according to provided 'Directory Structure' Value.
 */
fun setupFilePaths(fileName: String = "", isPLog: Boolean = true, folderDate: String? = null): String {
    try {
        //Create Root folder
        val rootFolderName = LOG_FOLDER
        val rootFolderPath = PLog.logPath + rootFolderName + File.separator
        createDirIfNotExists(rootFolderPath)

        val dateForFolder = folderDate ?: DateControl.instance.today

        when (PLogImpl.getConfig()?.directoryStructure!!) {

            DirectoryStructure.FOR_DATE -> {
                val folderPath = rootFolderPath + dateForFolder
                createDirIfNotExists(folderPath)

                val hourlyFileName = if (PLogTestHelper.isTestingHourlyLogs) {
                    PLogTestHelper.hourlyLogFileName
                } else {
                    dateForFolder + DateControl.instance.hour
                } //Name of File

                return if (fileName.isEmpty()) { //If file name is empty, then it's PLogger file
                    folderPath + File.separator + hourlyFileName + PLogImpl.getConfig()?.logFileExtension!!
                } else {
                    if (isPLog) {
                        folderPath + File.separator + hourlyFileName + fileName + PLogImpl.getConfig()?.logFileExtension!!
                    } else {
                        //Otherwise it's DataLogger file.
                        folderPath + File.separator + fileName + PLogImpl.getConfig()?.logFileExtension!!
                    }
                }
            }

            DirectoryStructure.FOR_EVENT -> {

                val parentPath = rootFolderPath + DateControl.instance.today
                createDirIfNotExists(parentPath)

                val nameForEventDirectory = PLogImpl.getConfig()?.nameForEventDirectory!!

                val folderPath = parentPath + File.separator + nameForEventDirectory

                //Create directory for event name and check it's result
                if (createDirIfNotExists(folderPath)) {
                    isDirectoryChanged(nameForEventDirectory)
                }
                currentNameOfDirectory = nameForEventDirectory //Set current name of directory

                val hourlyFileName =
                    currentNameOfDirectory + "_" + DateControl.instance.hour //Name of File

                return if (fileName.isEmpty()) { //If file name is empty, then it's PLogger file
                    folderPath + File.separator + hourlyFileName + PLogImpl.getConfig()?.logFileExtension!!
                } else {
                    if (isPLog) {
                        folderPath + File.separator + hourlyFileName + fileName + PLogImpl.getConfig()?.logFileExtension!!
                    } else {
                        //Otherwise it's DataLogger file.
                        folderPath + File.separator + fileName + PLogImpl.getConfig()?.logFileExtension!!
                    }
                }
            }

            DirectoryStructure.SINGLE_FILE_FOR_DAY -> {
                val todayPath = DateControl.instance.today
                return if (fileName.isEmpty()) { //If file name is empty, then it's PLogger file
                    rootFolderPath + File.separator + todayPath + PLogImpl.getConfig()?.logFileExtension!!
                } else {
                    //Otherwise it's DataLogger file.
                    rootFolderPath + File.separator + fileName + PLogImpl.getConfig()?.logFileExtension!!
                }
            }
        }
    } catch (e: NullPointerException) {
        e.printStackTrace()
        e.message?.let { Log.e(PLog.DEBUG_TAG, it) }


        Log.i(PLog.DEBUG_TAG, "setupFilePaths: Unable to append to file.. ${e.message}")
    } catch (e: RuntimeException) {
        e.printStackTrace()
        e.message?.let { Log.e(PLog.DEBUG_TAG, it) }


        Log.i(PLog.DEBUG_TAG, "setupFilePaths: Unable to append to file.. ${e.message}")
    } catch (e: Exception) {
        e.printStackTrace()
        e.message?.let { Log.e(PLog.DEBUG_TAG, it) }


        Log.i(PLog.DEBUG_TAG, "setupFilePaths: Unable to write to file.. ${e.message}")
    }
    return fileName
}

/*
 * This will return 'LogsPath' according to selected Directory Structure.
 */
fun getLogsSavedPaths(nameForEventDirectory: String = "", isForAll: Boolean = false): String {

    //Create Root folder
    val rootFolderName = LOG_FOLDER
    val rootFolderPath = PLog.logPath + rootFolderName + File.separator

    when (PLogImpl.getConfig()?.directoryStructure!!) {

        DirectoryStructure.FOR_DATE -> {
            val folderPath = rootFolderPath + DateControl.instance.today
            return folderPath + File.separator
        }

        DirectoryStructure.FOR_EVENT -> {
            val parentPath = rootFolderPath + DateControl.instance.today

            if (isForAll)
                return parentPath

            val folderPath = parentPath + File.separator + nameForEventDirectory
            return folderPath + File.separator
        }

        DirectoryStructure.SINGLE_FILE_FOR_DAY -> {
            val todayPath = DateControl.instance.today
            return rootFolderPath + File.separator
        }
    }
}

/*
 * This will publish a event to notify in case a new directory is created for an event.
 */
private fun isDirectoryChanged(name: String) {
    if (name.isNotEmpty() && currentNameOfDirectory.isNotEmpty() && name != currentNameOfDirectory) {
        RxBus.send(LogEvents(EventTypes.NEW_EVENT_DIRECTORY_CREATED, name))
    }
}