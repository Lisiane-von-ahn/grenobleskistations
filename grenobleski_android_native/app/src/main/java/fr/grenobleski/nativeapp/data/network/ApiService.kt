package fr.grenobleski.nativeapp.data.network

import com.google.gson.JsonElement
import fr.grenobleski.nativeapp.data.model.LoginRequest
import fr.grenobleski.nativeapp.data.model.LoginResponse
import fr.grenobleski.nativeapp.data.model.RegisterRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Url

interface GrenobleSkiApiService {
    @POST
    suspend fun login(
        @Url url: String,
        @Body payload: LoginRequest,
    ): Response<LoginResponse>

    @POST
    suspend fun register(
        @Url url: String,
        @Body payload: RegisterRequest,
    ): Response<LoginResponse>

    @GET
    suspend fun listResource(
        @Url url: String,
        @Header("Authorization") authHeader: String,
    ): Response<JsonElement>

    @POST
    suspend fun postResource(
        @Url url: String,
        @Header("Authorization") authHeader: String,
        @Body payload: Map<String, @JvmSuppressWildcards Any>,
    ): Response<JsonElement>

    @PATCH
    suspend fun patchResource(
        @Url url: String,
        @Header("Authorization") authHeader: String,
        @Body payload: Map<String, @JvmSuppressWildcards Any>,
    ): Response<JsonElement>

    @DELETE
    suspend fun deleteResource(
        @Url url: String,
        @Header("Authorization") authHeader: String,
    ): Response<JsonElement>
}
