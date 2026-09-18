package com.example.flock.sync

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface GoogleDriveApi {
    @GET("drive/v3/files")
    suspend fun listFiles(
        @Header("Authorization") authHeader: String,
        @Query("q") query: String = "mimeType='application/vnd.google-apps.spreadsheet' and trashed=false",
        @Query("fields") fields: String = "files(id, name, mimeType, modifiedTime, owners)"
    ): Response<DriveFileListResponse>

    @POST("drive/v3/files/{fileId}/permissions")
    suspend fun createPermission(
        @Header("Authorization") authHeader: String,
        @Path("fileId") fileId: String,
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
