package me.rhunk.snapenhance

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.widget.Toast
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.asCoroutineDispatcher
import me.rhunk.snapenhance.bridge.BridgeClient
import me.rhunk.snapenhance.bridge.wrapper.ConfigWrapper
import me.rhunk.snapenhance.bridge.wrapper.TranslationWrapper
import me.rhunk.snapenhance.data.MessageSender
import me.rhunk.snapenhance.database.DatabaseAccess
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.manager.impl.ActionManager
import me.rhunk.snapenhance.manager.impl.FeatureManager
import me.rhunk.snapenhance.manager.impl.MappingManager
import me.rhunk.snapenhance.util.download.DownloadServer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.reflect.KClass
import kotlin.system.exitProcess

class ModContext {
    private val executorService: ExecutorService = Executors.newCachedThreadPool()

    val coroutineDispatcher by lazy {
        executorService.asCoroutineDispatcher()
    }

    lateinit var androidContext: Context
    var mainActivity: Activity? = null
    lateinit var bridgeClient: BridgeClient

    val gson: Gson = GsonBuilder().create()

    val translation = TranslationWrapper()
    val config = ConfigWrapper()
    val features = FeatureManager(this)
    val mappings = MappingManager(this)
    val actionManager = ActionManager(this)
    val database = DatabaseAccess(this)
    val downloadServer = DownloadServer()
    val messageSender = MessageSender(this)
    val classCache get() = SnapEnhance.classCache
    val resources: Resources get() = androidContext.resources

    fun <T : Feature> feature(featureClass: KClass<T>): T {
        return features.get(featureClass)!!
    }

    fun runOnUiThread(runnable: () -> Unit) {
        Handler(Looper.getMainLooper()).post {
            runCatching(runnable).onFailure {
                Logger.xposedLog("UI thread runnable failed", it)
            }
        }
    }

    fun executeAsync(runnable: () -> Unit) {
        executorService.submit {
            runCatching {
                runnable()
            }.onFailure {
                longToast("Async task failed " + it.message)
                Logger.xposedLog("Async task failed", it)
            }
        }
    }

    fun shortToast(message: Any) {
        runOnUiThread {
            Toast.makeText(androidContext, message.toString(), Toast.LENGTH_SHORT).show()
        }
    }

    fun longToast(message: Any) {
        runOnUiThread {
            Toast.makeText(androidContext, message.toString(), Toast.LENGTH_LONG).show()
        }
    }

    fun softRestartApp(saveSettings: Boolean = false) {
        if (saveSettings) {
            config.writeConfig()
        }
        val intent: Intent? = androidContext.packageManager.getLaunchIntentForPackage(
            Constants.SNAPCHAT_PACKAGE_NAME
        )
        intent?.let {
            val mainIntent = Intent.makeRestartActivityTask(intent.component)
            androidContext.startActivity(mainIntent)
        }
        exitProcess(1)
    }

    fun crash(message: String, throwable: Throwable? = null) {
        Logger.xposedLog(message, throwable)
        longToast(message)
        delayForceCloseApp(100)
    }

    fun delayForceCloseApp(delay: Long) = Handler(Looper.getMainLooper()).postDelayed({
        forceCloseApp()
    }, delay)

    fun forceCloseApp() {
        Process.killProcess(Process.myPid())
        exitProcess(1)
    }
}