package com.ayg.advancevoiceassistant.assistant

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.ayg.advancevoiceassistant.data.Assistant
import com.ayg.advancevoiceassistant.data.AssistantDao
import kotlinx.coroutines.*

class AssistantViewModel (
    val database: AssistantDao,
    application:Application
        ) : AndroidViewModel(application)
{
    private var viewModelJob = Job()

    override fun onCleared() {
        super.onCleared()
        viewModelJob.cancel()
    }

    private val uiScope = CoroutineScope(Dispatchers.Main + viewModelJob)

    private var currentMessage = MutableLiveData<Assistant?>()

    val messages = database.getAllMessages()

    init{
        initializeCurrentMessage()

    }

    private fun initializeCurrentMessage()
    {
        uiScope.launch {
            currentMessage.value = getCurrentMessageFromDatabase()
        }
    }

    private suspend fun getCurrentMessageFromDatabase(): Assistant? {
        return withContext(Dispatchers.IO){
            var message = database.getCurrentMessage()
            if(message?.assistant_message == "DEFAULT_MESSAGE" || message?.human_message == "DEFAULT_MESSAGE" )
            {
                message = null
            }
            message
        }
    }

    fun sendMessageToDatabase(assistantMessage: String, humanMessage: String){
        uiScope.launch {
            val newAssistant = Assistant()
            newAssistant.assistant_message = assistantMessage
            newAssistant.human_message = humanMessage
            insert(newAssistant)
            currentMessage.value = getCurrentMessageFromDatabase()
        }
    }

    private suspend fun insert(message: Assistant){
        withContext(Dispatchers.IO){
            database.insert(message)
        }
    }

    private suspend fun update(message: Assistant) {
        withContext(Dispatchers.IO) {
            database.update(message)
        }
    }

    fun onClear() {
        uiScope.launch {
            clear()
            currentMessage.value = null
        }
    }

    private suspend fun clear() {
        withContext(Dispatchers.IO) {
            database.clear()
        }
    }


}