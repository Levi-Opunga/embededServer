/*
 * Created by nphau on 11/19/22, 4:16 PM
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 11/19/22, 3:58 PM
 */

package com.levi.embededserver.activities

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageView
import com.levi.embededserver.R
//import com.levi.embededserver.BuildConfig.DEBUG
import com.levi.embededserver.data.BaseResponse
import com.nphausg.app.embeddedserver.data.Database
import com.levi.embededserver.data.models.Cart
import com.levi.embededserver.extensions.animateFlash
import com.nphausg.app.embeddedserver.utils.NetworkUtils
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.gson.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.response.*
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File


class MainActivity : AppCompatActivity() {
    private var uRi: String = "";
    private var file: File? = null

    companion object {
        private const val PORT = 5001

    }

    private val server by lazy {
        embeddedServer(Netty, PORT, watchPaths = emptyList()) {
            install(WebSockets)
            install(CallLogging)

            install(ContentNegotiation) {
                gson {
                    setPrettyPrinting()
                    disableHtmlEscaping()
                }
            }

            install(CORS) {
                method(HttpMethod.Get)
                method(HttpMethod.Post)
                method(HttpMethod.Delete)
                anyHost()
            }

            install(Compression) {
                gzip()
            }
            routing {
                static("/fs") {

                    staticRootFolder = File(uRi)

                    //files(".")


                }
                get("/") {
                    call.respondText(
                        text = "Hello!! You are here in ${Build.MODEL}",
                        contentType = ContentType.Text.Plain
                    )
                }
                get("/fruits") {
                    call.respond(HttpStatusCode.OK, BaseResponse(Cart.sample()))
                }
                get("/fruits/{id}") {
                    val id = call.parameters["id"]
                    val fruit = Database.FRUITS.find { it.id == id }
                    if (fruit != null) {
                        call.respond(HttpStatusCode.OK, BaseResponse(fruit))
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<AppCompatImageView>(R.id.image_logo).animateFlash()

        // Show IP Address
        findViewById<TextView>(R.id.text_status).apply {
            val simpleTextApi =
                String.format("GET: %s:%d", NetworkUtils.getLocalIpAddress(), PORT)
            val apiGet =
                String.format("GET: %s:%d/fruits", NetworkUtils.getLocalIpAddress(), PORT)
            val apiGetWithId =
                String.format("GET: %s:%d/fruits/{id}", NetworkUtils.getLocalIpAddress(), PORT)
            text = String.format("%s\n%s\n%s", simpleTextApi, apiGet, apiGetWithId)
            animateFlash()
        }
        findViewById<Button>(R.id.button2).apply {
            setOnClickListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val i = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                    i.addCategory(Intent.CATEGORY_DEFAULT)
                    startActivityForResult(Intent.createChooser(i, "Choose directory"), 9999)
                }


            }
        }
        // Start server
//        CoroutineScope(Dispatchers.IO).launch {
//            server.start(wait = true)
//        }
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        var uri: Uri? = data?.data;


        when (requestCode) {
            9999 -> {
                file = data?.data?.let { getFileFromUri(it) }
                Log.i("Test", "Result URI from file conversion ${file?.absolutePath}");

                uRi = data?.data.toString()

                // Start server
                Log.i("Test", "Result URI ${data?.data?.path}");
                CoroutineScope(Dispatchers.IO).launch {
                    server.start(wait = true)


                };

            }
        }
//

    }

    override fun onDestroy() {
        server.stop(1_000, 2_000)
        super.onDestroy()
    }

    fun getFileFromUri(uri: Uri): File? {
        if (uri.path == null) {
            return null
        }
        var realPath = String()
        val databaseUri: Uri
        val selection: String?
        val selectionArgs: Array<String>?
        if (uri.path!!.contains("/document/image:")) {
            databaseUri =
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            selection = "_id=?"
            selectionArgs = arrayOf(DocumentsContract.getDocumentId(uri).split(":")[1])
        } else {
            databaseUri = uri
            selection = null
            selectionArgs = null
        }
        try {
            val column = "_data"
            val projection = arrayOf(column)
            val cursor = applicationContext.contentResolver.query(
                databaseUri,
                projection,
                selection,
                selectionArgs,
                null
            )
            cursor?.let { it ->
                if (it.moveToFirst()) {
                    val columnIndex = cursor.getColumnIndexOrThrow(column)
                    realPath = cursor.getString(columnIndex)
                }
                cursor.close()
            }
        } catch (e: Exception) {
            Log.i("GetFileUri Exception:", e.message ?: "")
        }
        val path = if (realPath.isNotEmpty()) realPath else {
            when {
                uri.path!!.contains("/document/raw:") -> uri.path!!.replace(
                    "/document/raw:",
                    ""
                )
                uri.path!!.contains("/document/tree") -> uri.path!!.replace(
                    "/document/tree",
                    "/storage/emulated/0/"
                )
                else -> return null
            }
        }
        Log.i("Path to folder", "the path is :: $path")
        return File(path)
    }

}


