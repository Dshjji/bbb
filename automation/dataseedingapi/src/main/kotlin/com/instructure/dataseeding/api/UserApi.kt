//
// Copyright (C) 2018-present Instructure, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//


package com.instructure.dataseeding.api

import com.instructure.dataseeding.model.*
import com.instructure.dataseeding.util.CanvasRestAdapter
import com.instructure.dataseeding.util.Randomizer
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Connection
import org.jsoup.Jsoup
import org.jsoup.nodes.FormElement
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Contains an interface that defines APIs for User endpoints
 * as well as methods for making the Retrofit calls to those APIs
 */
object UserApi {
    interface UserService {

        @POST("accounts/self/users")
        fun createCanvasUser(@Body createUser: CreateUser): Call<CanvasUserApiModel>

        @POST("/login/oauth2/token")
        fun getToken(
                @Query("client_id") clientId: String,
                @Query("client_secret") clientSecret: String,
                @Query("code") authCode: String,
                @Query(value = "redirect_uri", encoded = true) redirectURI: String
        ): Call<OAuthToken>

    }

    private val userAdminService: UserService by lazy {
        CanvasRestAdapter.adminRetrofit.create(UserService::class.java)
    }

    fun createCanvasUser(
            userService: UserService = userAdminService,
            userDomain: String = CanvasRestAdapter.canvasDomain
    ): CanvasUserApiModel {
        val teacherName = Randomizer.randomName()
        val user = User(teacherName.fullName, teacherName.firstName, teacherName.sortableName)

        val pseudonym = Pseudonym(
                Randomizer.randomEmail(),
                Randomizer.randomPassword()
        )

        val communicationChannel = CommunicationChannel(true)

        val createUser = CreateUser(user, pseudonym, communicationChannel)

        val createdUser = userService.createCanvasUser(createUser).execute().body()!!

        // Add extra data to the CanvasUserApiModel
        with(createdUser) {
            loginId = createUser.pseudonym.uniqueId
            password = createUser.pseudonym.password
            token = getToken(this, userService, userDomain)
            domain = userDomain
        }

        return createdUser
    }

    /**
     * Gets an access token for the userApiModel as described [here](https://canvas.instructure.com/doc/api/file.oauth_endpoints.html)
     * @param[userApiModel] A [CanvasUserApiModel]
     * @return An [String] access token for the userApiModel. NOTE: the token has an expiration of 1 hour.
     */
    private fun getToken(
            userApiModel: CanvasUserApiModel,
            userService: UserService = userAdminService,
            userDomain: String = CanvasRestAdapter.canvasDomain
    ): String {
        val authCode = getAuthCode(userApiModel, userDomain)
        val response = userService.getToken(
                CanvasRestAdapter.clientId,
                CanvasRestAdapter.clientSecret,
                authCode,
                CanvasRestAdapter.redirectUri
        ).execute()
        return response.body()?.accessToken ?: ""
    }

    /**
     * Gets an authentication code for the userApiModel as described [here](https://canvas.instructure.com/doc/api/file.oauth_endpoints.html)
     * @param[userApiModel] A [CanvasUserApiModel]
     * @return The [String] auth code to be used to acquire the userApiModel's access token
     */
    private fun getAuthCode(userApiModel: CanvasUserApiModel, domain: String = CanvasRestAdapter.canvasDomain): String {
        val loginPageResponse = Jsoup.connect("https://$domain/login/oauth2/auth")
                .method(Connection.Method.GET)
                .data("client_id", CanvasRestAdapter.clientId)
                .data("response_type", "code")
                .data("redirect_uri", CanvasRestAdapter.redirectUri)
                .execute()
        val loginForm = loginPageResponse.parse().select("form").first() as FormElement
        loginForm.getElementById("pseudonym_session_unique_id").`val`(userApiModel.loginId)
        loginForm.getElementById("pseudonym_session_password").`val`(userApiModel.password)
        val authFormResponse = loginForm.submit().cookies(loginPageResponse.cookies()).execute()
        val authForm = authFormResponse.parse().select("form").first() as FormElement
        val responseUrl = authForm.submit().cookies(authFormResponse.cookies()).execute().url().toString()
        return responseUrl.toHttpUrlOrNull()?.queryParameter("code") ?: throw RuntimeException("/login/oauth2/auth failed!")
    }
}
