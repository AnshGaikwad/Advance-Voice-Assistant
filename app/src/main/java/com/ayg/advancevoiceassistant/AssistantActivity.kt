package com.ayg.advancevoiceassistant

import android.animation.Animator
import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewAnimationUtils
import android.view.ViewTreeObserver
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.ayg.advancevoiceassistant.databinding.ActivityAssistantBinding
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*


class AssistantActivity : AppCompatActivity() {

    //data binding
    private lateinit var binding: ActivityAssistantBinding

    // Initializations
    private lateinit var textToSpeech: TextToSpeech
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var recognizerIntent: Intent
    private lateinit var keeper : String
    private lateinit var assistantViewModel : AssistantViewModel

    // log statements
    private val logtts = "TTS"
    private val logsr = "SR"
    private val logkeeper = "keeper"

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(R.anim.do_not_move, R.anim.do_not_move)

        // data binding
        binding = DataBindingUtil.setContentView(this, R.layout.activity_assistant)

        val application = requireNotNull(this).application
        val dataSource = AssistantDatabase.getInstance(application).assistantDao
        val viewModelFactory = AssistantViewModelFactory(dataSource, application)

        assistantViewModel =
            ViewModelProvider(
                this, viewModelFactory
            ).get(AssistantViewModel::class.java)

        binding.assistantViewModel = assistantViewModel

        val adapter = AssistantAdapter()
        binding.recyclerView.adapter = adapter

        assistantViewModel.messages.observe(this, Observer {
            it?.let{
                adapter.data = it
            }
        })

        binding.setLifecycleOwner(this)

        // Circular Reveal Animation
        if (savedInstanceState == null) {
            binding.assistantConstraintLayout.setVisibility(View.INVISIBLE)
            val viewTreeObserver: ViewTreeObserver = binding.assistantConstraintLayout.getViewTreeObserver()
            if (viewTreeObserver.isAlive) {
                viewTreeObserver.addOnGlobalLayoutListener(object : OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        circularRevealActivity()
                        binding.assistantConstraintLayout.getViewTreeObserver().removeOnGlobalLayoutListener(this)
                    }
                })
            }
        }

        // setting oninit listener
        textToSpeech = TextToSpeech(this) { status ->

            // check if its success
            if (status == TextToSpeech.SUCCESS) {

                // set language
                val result: Int = textToSpeech.setLanguage(Locale.ENGLISH)

                // check if there is any missing data or the lang is supported or not
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {

                    // if true
                    Log.e(logtts, "Language not supported")
                }
                else{
                    // if false
                    Log.e(logtts, "Language supported")
                }
            }
            else{
                // if success is false
                Log.e(logtts, "Initialization failed")
            }
        }

        speak("Hello, how can I help you?")

        // Initializing speech recognizer
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        recognizerIntent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())

        speechRecognizer.setRecognitionListener(object : RecognitionListener {

            override fun onReadyForSpeech(bundle: Bundle) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(bytes: ByteArray) {}
            override fun onEndOfSpeech() {}
            override fun onError(i: Int) {}

            override fun onResults(bundle: Bundle) {
                // getting data
                val data = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (data != null) {
                    keeper = data[0]
                    assistantViewModel.sendMessageToDatabase(keeper, 0)
                    Log.d(logkeeper, keeper)
                    when {
                        keeper.contains("clear everything") -> assistantViewModel.onClear()
                        keeper.contains("date") -> getDate()
                        keeper.contains("time") -> getTime()
//                        keeper.contains("phone call") -> makeAPhoneCall()
//                        keeper.contains("send SMS") -> sendSMS()
//                        keeper.contains("read my last SMS") -> readSMS()
//                        keeper.contains("open Gmail app") -> openGmail()
//                        keeper.contains("open WhatsApp") -> openWhatsapp()
//                        keeper.contains("open Facebook") -> openFacebook()
//                        keeper.contains("open messages") -> openMessages()
//                        keeper.contains("share a file") -> shareAFile()
//                        keeper.contains("share a text message") -> shareATextMessage()
//                        keeper.contains("Share text message") -> shareATextMessage()
//                        keeper.contains("flip a coin") -> flipCoin()
//                        keeper.contains("roll dice") -> rollDice()
//                        keeper.contains("call") -> callContact()
//                        keeper.contains("turn on Bluetooth") -> turnOnBluetooth()
//                        keeper.contains("turn off Bluetooth") -> turnOffBluetooth()
//                        keeper.contains("make phone discoverable for bluetooth") -> discoverableBluetooth()
//                        keeper.contains("get all paired devices") -> getAllPairedDevices()
//                        keeper.contains("turn on flashlight") -> turnOnFlash()
//                        keeper.contains("turn off flashlight") -> turnOffFlash()
//                        keeper.contains("copy to clipboard") -> clipBoardCopy()
//                        keeper.contains("read last clipboard") -> clipBoardSpeak()
//                        keeper.contains("capture photo") -> capturePhoto()
//                        keeper.contains("get MAC address") -> getMac()
//                        keeper.contains("Eco start") -> echo()
//                        keeper.contains("brightness") -> setBrightness()
//                        keeper.contains("play ringtone") -> playRingtone()
//                        keeper.contains("stop ringtone") -> stopRingtone()
//                        keeper.contains("timer") || keeper.contains("Timer") -> setTimer()
                    }
                }
            }

            override fun onPartialResults(bundle: Bundle) {}
            override fun onEvent(i: Int, bundle: Bundle) {}

        })

//         on touch for fab

        binding.assistantFloatingActionButton.setOnTouchListener { view, motionEvent ->
            when (motionEvent.action) {
                MotionEvent.ACTION_UP -> {
                    speechRecognizer.stopListening()
                    Log.d("SR", "released")
                }
                MotionEvent.ACTION_DOWN -> {
                    speechRecognizer.startListening(recognizerIntent)
                    Log.d("SR", "pressed")
                }
            }
            false
        }

        // check if speech recognition available
        checkIfSpeechRecognizerAvailable()

    }

    private fun checkIfSpeechRecognizerAvailable() {
        if(SpeechRecognizer.isRecognitionAvailable(this))
        {
            Log.d(logsr, "yes")
        }
        else
        {
            Log.d(logsr, "false")
        }

    }

    // speaking text through text to speech
    private fun speak(text: String)
    {
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "")
        assistantViewModel.sendMessageToDatabase(text, 1)
    }

    private fun getTime()
    {
        val calendar = Calendar.getInstance()
        val format = SimpleDateFormat("HH:mm:ss")
        val time: String = format.format(calendar.getTime())
        speak("The time is $time")
    }

    private fun getDate()
    {
        val calendar = Calendar.getInstance()
        val formattedDate = DateFormat.getDateInstance(DateFormat.FULL).format(calendar.time)
        val splitDate = formattedDate.split(",").toTypedArray()
        val date = splitDate[1].trim { it <= ' ' }
        speak("The date is $date")
    }



    private fun circularRevealActivity() {
        val cx: Int = binding.assistantConstraintLayout.getRight() - getDips(44)
        val cy: Int = binding.assistantConstraintLayout.getBottom() - getDips(44)
        val finalRadius: Int = Math.max(binding.assistantConstraintLayout.getWidth(), binding.assistantConstraintLayout.getHeight())
        val circularReveal = ViewAnimationUtils.createCircularReveal(
            binding.assistantConstraintLayout,
            cx,
            cy, 0f,
            finalRadius.toFloat()
        )
        circularReveal.duration = 1250
        binding.assistantConstraintLayout.setVisibility(View.VISIBLE)
        circularReveal.start()
    }

    private fun getDips(dps: Int): Int {
        val resources: Resources = resources
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dps.toFloat(),
            resources.getDisplayMetrics()
        ).toInt()
    }

    override fun onBackPressed() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val cx: Int = binding.assistantConstraintLayout.getWidth() - getDips(44)
            val cy: Int = binding.assistantConstraintLayout.getBottom() - getDips(44)
            val finalRadius: Int = Math.max(binding.assistantConstraintLayout.getWidth(), binding.assistantConstraintLayout.getHeight())
            val circularReveal =
                ViewAnimationUtils.createCircularReveal(
                    binding.assistantConstraintLayout, cx, cy,
                    finalRadius.toFloat(), 0f
                )
            circularReveal.addListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animator: Animator) {}
                override fun onAnimationEnd(animator: Animator) {
                    binding.assistantConstraintLayout.setVisibility(View.INVISIBLE)
                    finish()
                }

                override fun onAnimationCancel(animator: Animator) {}
                override fun onAnimationRepeat(animator: Animator) {}
            })
            circularReveal.duration = 1250
            circularReveal.start()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // destroying
        textToSpeech.stop()
        textToSpeech.shutdown()
        speechRecognizer.cancel()
        speechRecognizer.destroy()
        Log.i(logsr, "destroy")
        Log.i(logtts, "destroy")
    }
}