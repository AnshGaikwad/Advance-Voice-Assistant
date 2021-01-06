package com.ayg.advancevoiceassistant

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
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

//    private val messageString = Transformations.map(messages){messages ->
//        formatMessages(messages, application.resources)
//    }

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
            if(message?.message == "DEFAULT_MESSAGE")
            {
                message = null
            }
            message
        }
    }

    fun sendMessageToDatabase(message: String, type: Int){
        uiScope.launch {
            val newMessage = Assistant()
            newMessage.message = message
            newMessage.type = type
            insert(newMessage)
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