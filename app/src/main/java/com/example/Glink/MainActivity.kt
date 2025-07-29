package com.example.Glink // Or your actual package name

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.*
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var apiUrlEditText: EditText
    private lateinit var submitButton: Button
    private lateinit var webView: WebView

    private val webSnappFoodUrl = "https://food.snapp.ir/"
    private val mobileSnappFoodUrl = "https://m.snapp.ir/"

    private val client = OkHttpClient()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        apiUrlEditText = findViewById(R.id.apiUrlEditText)
        submitButton = findViewById(R.id.submitButton)
        webView = findViewById(R.id.webView)

        webView.settings.javaScriptEnabled = true
        webView.webViewClient = WebViewClient()
        webView.loadUrl(webSnappFoodUrl)

        submitButton.setOnClickListener {
            val apiUrl = apiUrlEditText.text.toString().trim()
            if (apiUrl.isEmpty()) {
                Toast.makeText(this, "لطفا لینک را وارد کنید", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            submitButton.isEnabled = false
            submitButton.text = "در حال پردازش..."
            fetchJwtData(apiUrl)
        }
    }

    private fun fetchJwtData(apiUrl: String) {
        val request = Request.Builder().url(apiUrl).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "خطای شبکه: " + e.message, Toast.LENGTH_LONG).show()
                    resetButton()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "لینک وارد شده نادرست است", Toast.LENGTH_SHORT).show()
                        resetButton()
                    }
                    return
                }

                val responseBody = response.body?.string()
                try {
                    val data = JsonParser.parseString(responseBody).asJsonObject
                    val jwtString = data.get("JWT").asString
                    val innerJwt = JsonParser.parseString(jwtString).asJsonObject
                    if (innerJwt.has("access_token")) {
                        injectCookiesAndLoad(mobileSnappFoodUrl, innerJwt)
                    } else {
                        injectLocalStorageAndLoad(webSnappFoodUrl, jwtString)
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "ساختار JSON نامعتبر است", Toast.LENGTH_SHORT).show()
                        resetButton()
                    }
                }
            }
        })
    }

    private fun injectLocalStorageAndLoad(url: String, jwtValue: String) {
        runOnUiThread {
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                    super.onPageFinished(view, finishedUrl)
                    if (url == finishedUrl) {
                        val cleanupScript = "localStorage.removeItem('state'); localStorage.removeItem('user-info');"
                        view?.evaluateJavascript(cleanupScript) {
                            val injectionScript = "localStorage.setItem('JWT', JSON.stringify($jwtValue));"
                            view.evaluateJavascript(injectionScript) {
                                Handler(Looper.getMainLooper()).postDelayed({
                                    view.reload()
                                    Toast.makeText(this@MainActivity, "اطلاعات با موفقیت ثبت شد!", Toast.LENGTH_SHORT).show()
                                    resetButton()
                                }, 500)
                            }
                        }
                    }
                }
            }
            webView.loadUrl(url)
        }
    }

    private fun injectCookiesAndLoad(url: String, jwtData: JsonObject) {
        runOnUiThread {
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.removeAllCookies(null)

            val accessToken = jwtData.get("access_token").asString
            val tokenType = jwtData.get("token_type").asString
            val refreshToken = jwtData.get("refresh_token").asString
            val expiresIn = jwtData.get("expires_in").asString

            cookieManager.setCookie(url, "jwt-access_token=$accessToken")
            cookieManager.setCookie(url, "jwt-token_type=$tokenType")
            cookieManager.setCookie(url, "jwt-refresh_token=$refreshToken")
            cookieManager.setCookie(url, "jwt-expires_in=$expiresIn")
            cookieManager.setCookie(url, "UserMembership=0")

            webView.loadUrl(url)
            Toast.makeText(this@MainActivity, "ورود با موفقیت انجام شد!", Toast.LENGTH_SHORT).show()
            resetButton()
        }
    }

    private fun resetButton() {
        runOnUiThread {
            submitButton.isEnabled = true
            submitButton.text = "ارسال و ورود به اسنپ‌فود"
        }
    }
}
