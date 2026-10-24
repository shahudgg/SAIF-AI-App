package com.jarvis.assistant

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import com.jarvis.assistant.audio.VoskModelManager
import com.jarvis.assistant.automation.command.CommandExecutor
import com.jarvis.assistant.automation.contacts.ContactResolver
import com.jarvis.assistant.automation.phone.PhoneControlManager
import com.jarvis.assistant.automation.whatsapp.WhatsAppAssistant
import com.jarvis.assistant.automation.youtube.YouTubeAutomationManager
import com.jarvis.assistant.data.memory.MemoryManager
import com.jarvis.assistant.data.preferences.AppPreferences
import com.jarvis.assistant.data.preferences.YouTubePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class JarvisApp private constructor(val appContext: Context) {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var preferences: AppPreferences
        private set

    lateinit var chatRepository: com.jarvis.assistant.data.repository.ChatRepository
        private set

    lateinit var memoryRepository: com.jarvis.assistant.data.memory.MemoryRepository
        private set

    lateinit var memoryManager: MemoryManager
        private set

    lateinit var youTubePreferences: YouTubePreferences
        private set

    lateinit var youTubeAutomationManager: YouTubeAutomationManager
        private set

    lateinit var contactResolver: ContactResolver
        private set

    lateinit var phoneControlManager: PhoneControlManager
        private set

    lateinit var whatsAppAssistant: WhatsAppAssistant
        private set

    lateinit var commandExecutor: CommandExecutor
        private set

    init {
        initApp(appContext)
    }

    private fun initApp(context: Context) {
        preferences = AppPreferences(context)
        val memDb = com.jarvis.assistant.data.memory.MemoryDatabase.getInstance(context)
        memoryRepository = com.jarvis.assistant.data.memory.MemoryRepository(memDb.memoryDao())
        chatRepository = com.jarvis.assistant.data.repository.ChatRepository(preferences)
        memoryManager = MemoryManager(
            repository = memoryRepository,
            preferences = preferences,
            chatRepository = chatRepository,
            scope = appScope
        )

        // Automation Systems
        youTubePreferences = YouTubePreferences(context)
        youTubeAutomationManager = YouTubeAutomationManager(context, youTubePreferences)
        contactResolver = ContactResolver(context)
        phoneControlManager = PhoneControlManager(context, contactResolver)
        whatsAppAssistant = WhatsAppAssistant(context, contactResolver)

        // Command Engine
        commandExecutor = CommandExecutor(
            phoneManager = phoneControlManager,
            youtubeManager = youTubeAutomationManager,
            whatsappAssistant = whatsAppAssistant,
            memoryManager = memoryManager
        )

        VoskModelManager.init(context, appScope)

        (context.applicationContext as? Application)?.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            private var startedActivities = 0
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {
                startedActivities++
                if (startedActivities == 1) _isAppForeground.value = true
            }
            override fun onActivityResumed(activity: Activity) {
                currentResumedActivity = activity
            }
            override fun onActivityPaused(activity: Activity) {
                if (currentResumedActivity === activity) {
                    currentResumedActivity = null
                }
            }
            override fun onActivityStopped(activity: Activity) {
                startedActivities = (startedActivities - 1).coerceAtLeast(0)
                if (startedActivities == 0) _isAppForeground.value = false
            }
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {
                if (currentResumedActivity === activity) {
                    currentResumedActivity = null
                }
            }
        })
    }

    var currentResumedActivity: Activity? = null
        private set

    fun moveAppToBack(): Boolean {
        val act = currentResumedActivity ?: return false
        return try {
            act.moveTaskToBack(true)
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        private val _isAppForeground = MutableStateFlow(false)
        val isAppForeground: StateFlow<Boolean> = _isAppForeground.asStateFlow()

        @Volatile
        lateinit var instance: JarvisApp
            internal set

        fun init(context: Context): JarvisApp {
            if (!::instance.isInitialized) {
                synchronized(this) {
                    if (!::instance.isInitialized) {
                        instance = JarvisApp(context.applicationContext)
                    }
                }
            }
            return instance
        }
    }
}
