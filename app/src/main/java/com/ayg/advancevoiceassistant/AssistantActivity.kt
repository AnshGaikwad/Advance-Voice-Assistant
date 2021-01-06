package com.ayg.advancevoiceassistant

import android.Manifest
import android.animation.Animator
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.telephony.SmsManager
import android.util.Log
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewAnimationUtils
import android.view.ViewTreeObserver
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
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

    //permissions
    private var REQUEST_CALL = 1
    private var SENDSMS = 2
    private var READSMS = 2

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
            it?.let {
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
                        binding.assistantConstraintLayout.getViewTreeObserver()
                            .removeOnGlobalLayoutListener(
                                this
                            )
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
                Log.d("SR", "started")
            }

            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(bytes: ByteArray) {}
            override fun onEndOfSpeech() {
                Log.d("SR", "ended")
            }

            override fun onError(i: Int) {}

            override fun onResults(bundle: Bundle) {
                // getting data
                val data = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (data != null) {
                    keeper = data[0]
                    Log.d(logkeeper, keeper)
                    when {
                        keeper.contains("clear everything") -> assistantViewModel.onClear()
                        keeper.contains("date") -> getDate()
                        keeper.contains("time") -> getTime()
                        keeper.contains("phone call") -> makeAPhoneCall()
                        keeper.contains("send SMS") -> sendSMS()
                        keeper.contains("read my last SMS") -> readSMS()
                        keeper.contains("open Gmail") -> openGmail()
                        keeper.contains("open WhatsApp") -> openWhatsapp()
                        keeper.contains("open Facebook") -> openFacebook()
                        keeper.contains("open messages") -> openMessages()
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

//      on touch for fab
        binding.assistantFloatingActionButton.setOnTouchListener { view, motionEvent ->
            when (motionEvent.action) {
                MotionEvent.ACTION_UP -> {
                    speechRecognizer.stopListening()

                }
                MotionEvent.ACTION_DOWN -> {
                    speechRecognizer.startListening(recognizerIntent)

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
    fun speak(text: String)
    {
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "")
        assistantViewModel.sendMessageToDatabase(keeper, text)
    }

    fun getTime()
    {
        val calendar = Calendar.getInstance()
        val format = SimpleDateFormat("HH:mm:ss")
        val time: String = format.format(calendar.getTime())
        speak("The time is $time")
    }

    fun getDate()
    {
        val calendar = Calendar.getInstance()
        val formattedDate = DateFormat.getDateInstance(DateFormat.FULL).format(calendar.time)
        val splitDate = formattedDate.split(",").toTypedArray()
        val date = splitDate[1].trim { it <= ' ' }
        speak("The date is $date")
    }

    // make a phone call to 77986xxxxx
    private fun makeAPhoneCall() {
        val keeperSplit = keeper.replace(" ".toRegex(), "").split("o").toTypedArray()
        val number = keeperSplit[2]

        // number must not have any spaces
        if (number.trim { it <= ' ' }.length > 0) {

            // runtime message
            if (ContextCompat.checkSelfPermission(
                    this@AssistantActivity,
                    Manifest.permission.CALL_PHONE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this@AssistantActivity,
                    arrayOf(Manifest.permission.CALL_PHONE),
                    REQUEST_CALL
                )
            } else {
                // passing intent
                val dial = "tel:$number"
                speak("Calling $number")
                startActivity(Intent(Intent.ACTION_CALL, Uri.parse(dial)))
            }
        } else {
            // invalid phone
            Toast.makeText(this@AssistantActivity, "Enter Phone Number", Toast.LENGTH_SHORT).show()
        }
    }



    // send sms to 77986999685 that message
    private fun sendSMS() {
        Log.d("keeper", "Done0")
        // runtime message
        if (ContextCompat.checkSelfPermission(
                this@AssistantActivity,
                Manifest.permission.SEND_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this@AssistantActivity,
                arrayOf(Manifest.permission.SEND_SMS),
                SENDSMS
            )
            Log.d("keeper", "Done1")
        }else{
            Log.d("keeper", "Done2")
            val keeperReplaced = keeper.replace(" ".toRegex(), "")
            val number = keeperReplaced.split("o").toTypedArray()[1].split("t").toTypedArray()[0]
            val message = keeper.split("that").toTypedArray()[1]
            Log.d("chk", number + message)
            val mySmsManager = SmsManager.getDefault()
            mySmsManager.sendTextMessage(
                number.trim { it <= ' ' },
                null,
                message.trim { it <= ' ' },
                null,
                null
            )
            speak("Message sent that $message")
        }
    }

    //  read my last SMS
    private fun readSMS() {
        if (ContextCompat.checkSelfPermission(
                this@AssistantActivity,
                Manifest.permission.READ_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this@AssistantActivity,
                arrayOf(Manifest.permission.READ_SMS),
                READSMS
            )
        }
        else {
            val cursor = contentResolver.query(Uri.parse("content://sms"), null, null, null, null)
            cursor!!.moveToFirst()
            speak("Your last message was " + cursor.getString(12))
        }
    }

    private fun openMessages() {
        val intent =
            packageManager.getLaunchIntentForPackage(Telephony.Sms.getDefaultSmsPackage(this))
        intent?.let { startActivity(it) }
    }

    private fun openFacebook() {
        val intent = packageManager.getLaunchIntentForPackage("com.facebook.katana")
        intent?.let { startActivity(it) }
    }

    private fun openWhatsapp() {
        val intent = packageManager.getLaunchIntentForPackage("com.whatsapp")
        intent?.let { startActivity(it) }
    }

    private fun openGmail() {
        val intent = packageManager.getLaunchIntentForPackage("com.google.android.gm")
        intent?.let { startActivity(it) }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_CALL) {
            if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // when permission granted
                makeAPhoneCall()
            } else {
                // permission denied
                Toast.makeText(this, "Permission DENIED", Toast.LENGTH_SHORT).show()
            }
        }
        else if(requestCode == SENDSMS)
        {
            if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // when permission granted
                sendSMS()
            } else {
                // permission denied
                Toast.makeText(this, "Permission DENIED", Toast.LENGTH_SHORT).show()
            }
        }
        else if(requestCode == READSMS)
        {
            if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // when permission granted
                readSMS()
            } else {
                // permission denied
                Toast.makeText(this, "Permission DENIED", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun circularRevealActivity() {
        val cx: Int = binding.assistantConstraintLayout.getRight() - getDips(44)
        val cy: Int = binding.assistantConstraintLayout.getBottom() - getDips(44)
        val finalRadius: Int = Math.max(
            binding.assistantConstraintLayout.getWidth(),
            binding.assistantConstraintLayout.getHeight()
        )
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
            val finalRadius: Int = Math.max(
                binding.assistantConstraintLayout.getWidth(),
                binding.assistantConstraintLayout.getHeight()
            )
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