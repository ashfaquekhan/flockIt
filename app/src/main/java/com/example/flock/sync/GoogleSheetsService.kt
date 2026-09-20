package com.example.flock.sync

import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface GoogleDriveApi {
    @GET("drive/v3/files")
    suspend fun listFiles(
        @Header("Authorization") authHeader: String,
        @Query("q") query: String = "mimeType='application/vnd.google-apps.spreadsheet' and trashed=false",
        @Query("fields") fields: String = "files(id, name, mimeType, modifiedTime, owners)",
        @Query("spaces") spaces: String = "drive"
    ): Response<DriveFileListResponse>

    // Read a file's raw content (used for the appDataFolder index JSON)
    @GET("drive/v3/files/{fileId}")
    suspend fun downloadFileContent(
        @Header("Authorization") authHeader: String,
        @Path("fileId") fileId: String,
        @Query("alt") alt: String = "media"
    ): Response<ResponseBody>

    // Overwrite a file's content (used for the appDataFolder index JSON)
    @PATCH("upload/drive/v3/files/{fileId}")
    suspend fun uploadFileContent(
        @Header("Authorization") authHeader: String,
        @Path("fileId") fileId: String,
        @Query("uploadType") uploadType: String = "media",
        @Body body: RequestBody
    ): Response<DriveFile>

    @POST("drive/v3/files")
    suspend fun createFile(
        @Header("Authorization") authHeader: String,
        @Body request: CreateDriveFileRequest
    ): Response<DriveFile>

    @PATCH("drive/v3/files/{fileId}")
    suspend fun moveFileToFolder(
        @Header("Authorization") authHeader: String,
        @Path("fileId") fileId: String,
        @Query("addParents") addParents: String,
        @Query("removeParents") removeParents: String? = null
    ): Response<DriveFile>

    @POST("drive/v3/files/{fileId}/permissions")
    suspend fun createPermission(
        @Header("Authorization") authHeader: String,
        @Path("fileId") fileId: String,
        @Query("sendNotificationEmail") sendNotificationEmail: Boolean = true,
        @Body request: CreatePermissionRequest
    ): Response<PermissionResponse>
}

interface GoogleSheetsApi {
    @POST("v4/spreadsheets")
    suspend fun createSpreadsheet(
        @Header("Authorization") authHeader: String,
        @Body request: CreateSpreadsheetRequest
    ): Response<SpreadsheetResponse>

    @GET("v4/spreadsheets/{spreadsheetId}")
    suspend fun getSpreadsheet(
        @Header("Authorization") authHeader: String,
        @Path("spreadsheetId") spreadsheetId: String
    ): Response<SpreadsheetResponse>

    @GET("v4/spreadsheets/{spreadsheetId}/values:batchGet")
    suspend fun batchGet(
        @Header("Authorization") authHeader: String,
        @Path("spreadsheetId") spreadsheetId: String,
        @Query("ranges") ranges: List<String>
    ): Response<BatchGetValuesResponse>

    @POST("v4/spreadsheets/{spreadsheetId}/values:batchUpdate")
    suspend fun batchUpdateValues(
        @Header("Authorization") authHeader: String,
        @Path("spreadsheetId") spreadsheetId: String,
        @Body request: BatchUpdateValuesRequest
    ): Response<BatchUpdateValuesResponse>

    @POST("v4/spreadsheets/{spreadsheetId}/values/{range}:append")
    suspend fun appendValues(
        @Header("Authorization") authHeader: String,
        @Path("spreadsheetId") spreadsheetId: String,
        @Path("range") range: String,
        @Query("valueInputOption") valueInputOption: String = "USER_ENTERED",
        @Body request: ValueRange
    ): Response<AppendValuesResponse>
}

object GoogleApiClientProvider {
    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()
    }

    val sheetsApi: GoogleSheetsApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://sheets.googleapis.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(GoogleSheetsApi::class.java)
    }

    val driveApi: GoogleDriveApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://www.googleapis.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(GoogleDriveApi::class.java)
    }
}
