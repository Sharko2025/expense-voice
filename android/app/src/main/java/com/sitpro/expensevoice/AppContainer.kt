package com.sitpro.expensevoice

import android.app.Application
import android.content.res.Configuration
import com.sitpro.expensevoice.ui.AppLanguage
import android.content.Context
import androidx.room.Room
import com.sitpro.expensevoice.api.ApiService
import com.sitpro.expensevoice.api.createApiService
import com.sitpro.expensevoice.data.AppDatabase
import com.sitpro.expensevoice.data.ExpenseRepository
import com.sitpro.expensevoice.data.PendingUpload
import com.sitpro.expensevoice.data.SettingsStore
import com.sitpro.expensevoice.notify.ResultNotifier
import com.sitpro.expensevoice.sync.UploadProcessor
import com.sitpro.expensevoice.sync.UploadScheduler
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.sitpro.expensevoice.ui.PendingBanner
import com.sitpro.expensevoice.ui.pendingBanner
import java.io.File
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Manual dependency wiring; one instance per process. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** Outlives activities: RecordActivity finishes right after handing the recording over. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val db: AppDatabase = Room.databaseBuilder(appContext, AppDatabase::class.java, "expense.db").build()
    val settings = SettingsStore(appContext)

    @Volatile private var cachedApi: Pair<String, ApiService>? = null

    /** Rebuilt when the server URL changes; the token is read on every request. */
    fun api(): ApiService {
        val url = settings.current.baseUrl
        cachedApi?.let { (cachedUrl, api) -> if (cachedUrl == url) return api }
        return createApiService(url) { settings.current.token }.also { cachedApi = url to it }
    }

    val repository = ExpenseRepository(::api, db)
    val notifier = ResultNotifier(appContext)
    val processor = UploadProcessor(::api, db.pendingDao(), repository, notifier)
    val scheduler = UploadScheduler(appContext)

    /** Validated internet, kept current by a network callback (drives the in-app pending strip). */
    val online: StateFlow<Boolean> = callbackFlow {
        val cm = appContext.getSystemService(ConnectivityManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                trySend(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
            }
            override fun onLost(network: Network) {
                trySend(false)
            }
        }
        cm.registerDefaultNetworkCallback(callback)
        awaitClose { cm.unregisterNetworkCallback(callback) }
    }.stateIn(appScope, SharingStarted.Eagerly, scheduler.isOnline())

    val pendingBanner: Flow<PendingBanner?> =
        combine(db.pendingDao().observeAll(), online) { rows, isOnline -> pendingBanner(rows, isOnline) }

    val pendingDir: File = File(appContext.filesDir, "pending").apply { mkdirs() }

    /** Called by RecordActivity once a recording is complete. */
    fun submitRecording(entryId: String, file: File, recordedAt: String, occurredOn: String? = null) {
        appScope.launch {
            db.pendingDao().insert(
                PendingUpload(entryId, file.path, recordedAt, System.currentTimeMillis(), occurredOn = occurredOn)
            )
            notifier.sending(entryId, scheduler.isOnline())
            scheduler.enqueue(entryId)
        }
    }

    /** Re-enqueue everything still waiting, e.g. on app start or "Gửi lại hàng đợi". */
    fun resendQueue() {
        appScope.launch {
            db.pendingDao().unblockAll()
            db.pendingDao().all().forEach { scheduler.enqueue(it.entryId) }
        }
    }
}

val Context.container: AppContainer get() = (applicationContext as ExpenseApp).container

class ExpenseApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        AppLanguage.applyDefaultOnce(this)
        container = AppContainer(this)
        container.notifier.createChannels()
        container.resendQueue()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Language switched: rename the notification channels shown in Android Settings.
        if (::container.isInitialized) container.notifier.createChannels()
    }
}
