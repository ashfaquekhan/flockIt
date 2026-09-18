package com.example.flock.sync

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CreateSpreadsheetRequest(
    val properties: SpreadsheetProperties? = null,
    val sheets: List<Sheet>? = null
)

@JsonClass(generateAdapter = true)
data class SpreadsheetProperties(
    val title: String
)

@JsonClass(generateAdapter = true)
data class Sheet(
    val properties: SheetProperties
)

@JsonClass(generateAdapter = true)
data class SheetProperties(
    val sheetId: Int? = null,
    val title: String,
    val index: Int? = null,
    val gridProperties: GridProperties? = null
)

@JsonClass(generateAdapter = true)
data class GridProperties(
    val rowCount: Int? = 100,
    val columnCount: Int? = 80,
    val frozenRowCount: Int? = 1
)

@JsonClass(generateAdapter = true)
data class SpreadsheetResponse(
    val spreadsheetId: String,
    val properties: SpreadsheetProperties? = null,
    val sheets: List<Sheet>? = null
)

@JsonClass(generateAdapter = true)
data class BatchGetValuesResponse(
    val spreadsheetId: String,
    val valueRanges: List<ValueRange>? = null
)

@JsonClass(generateAdapter = true)
data class ValueRange(
    val range: String,
    val majorDimension: String? = "ROWS",
    val values: List<List<Any>>? = null
)

@JsonClass(generateAdapter = true)
data class BatchUpdateValuesRequest(
    val valueInputOption: String = "USER_ENTERED",
    val data: List<ValueRange>
)

@JsonClass(generateAdapter = true)
data class BatchUpdateValuesResponse(
    val spreadsheetId: String,
    val totalUpdatedRows: Int? = null,
    val totalUpdatedColumns: Int? = null,
    val totalUpdatedCells: Int? = null
)

@JsonClass(generateAdapter = true)
data class DriveFileListResponse(
    val files: List<DriveFile>? = null
)

@JsonClass(generateAdapter = true)
data class DriveFile(
    val id: String,
    val name: String,
    val mimeType: String? = null,
    val modifiedTime: String? = null,
    val owners: List<DriveUser>? = null
)

@JsonClass(generateAdapter = true)
data class DriveUser(
    val displayName: String? = null,
    val emailAddress: String? = null
)

@JsonClass(generateAdapter = true)
data class CreatePermissionRequest(
    val role: String, // "writer" or "reader"
    val type: String = "user",
    val emailAddress: String
)

@JsonClass(generateAdapter = true)
data class PermissionResponse(
    val id: String,
    val role: String? = null,
    val type: String? = null,
    val emailAddress: String? = null
)
