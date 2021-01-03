package com.ayg.advancevoiceassistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.MotionEvent
import android.view.View.OnTouchListener
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.util.*

class MainActivity : AppCompatActivity() {

    // Initializations
    private lateinit var textToSpeech: TextToSpeech
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var recognizerIntent: Intent
    private lateinit var keeper : String

    // permission code
    val RecordAudioRequestCode : Int = 1

    // view initializations
    private lateinit var floatingActionButton: FloatingActionButton

    // log statements
    private val logtts = "TTS"
    private val logsr = "SR"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // initializing views
        floatingActionButton = findViewById(R.id.floating_action_button)

        // getting permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            checkPermission()
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

            override fun onBeginningOfSpeech() {
                //micBtn.setImageIcon(R.id.mic_btn);
//                textBelow.setText("")
//                textBelow.setHint("Listening...")
            }

            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(bytes: ByteArray) {}
            override fun onEndOfSpeech() {}
            override fun onError(i: Int) {}
            override fun onResults(bundle: Bundle) {
                // getting data
                val data = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (data != null) {
                    keeper = data[0]

                }
            }

            override fun onPartialResults(bundle: Bundle) {}
            override fun onEvent(i: Int, bundle: Bundle) {}
        })

        // on touch for fab
        floatingActionButton.setOnTouchListener(OnTouchListener { view, motionEvent ->
            if (motionEvent.action == MotionEvent.ACTION_UP) {
                speechRecognizer.stopListening()
            }
            if (motionEvent.action == MotionEvent.ACTION_DOWN) {
                speechRecognizer.startListening(recognizerIntent)
            }
            true
        })
    }

    // speaking text through text to speech
    private fun speak(text: String)
    {
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "")
    }

    // on request permission
    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<String?>,
            grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // for audio
        if (requestCode == RecordAudioRequestCode && grantResults.size > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) Toast.makeText(
                    this,
                    "Permission Granted",
                    Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // destroying
        textToSpeech.stop()
        textToSpeech.shutdown()
        speechRecognizer.destroy()
        Log.i(logsr, "destroy")
    }

    private fun checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), RecordAudioRequestCode)
        }
    }
}