package com.ayg.advancevoiceassistant

import android.Manifest
import android.Manifest.permission
import android.animation.Animator
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.camera2.CameraManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StrictMode
import android.os.StrictMode.VmPolicy
import android.provider.ContactsContract
import android.provider.MediaStore
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
import bot.box.horology.annotation.DURATION
import bot.box.horology.annotation.SUNSIGN
import bot.box.horology.api.Horoscope
import bot.box.horology.delegate.Response
import bot.box.horology.hanshake.HorologyController
import bot.box.horology.pojo.Zodiac
import com.ayg.advancevoiceassistant.databinding.ActivityAssistantBinding
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.kwabenaberko.openweathermaplib.constant.Units
import com.kwabenaberko.openweathermaplib.implementation.OpenWeatherMapHelper
import com.kwabenaberko.openweathermaplib.implementation.callback.CurrentWeatherCallback
import com.kwabenaberko.openweathermaplib.model.currentweather.CurrentWeather
import com.ml.quaterion.text2summary.Text2Summary
import com.theartofdev.edmodo.cropper.CropImage
import com.theartofdev.edmodo.cropper.CropImageView
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.jvm.internal.Ref.ObjectRef


class AssistantActivity : AppCompatActivity() {

    // views
    private lateinit var binding: ActivityAssistantBinding
    private lateinit var assistantViewModel : AssistantViewModel

    // SR & TTS
    private lateinit var textToSpeech: TextToSpeech
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var recognizerIntent: Intent
    private lateinit var keeper : String

    // log statements
    private val logtts = "TTS"
    private val logsr = "SR"
    private val logkeeper = "keeper"

    //permissions
    private var REQUESTCALL = 1
    private var SENDSMS = 2
    private var READSMS = 3
    private var SHAREAFILE = 4
    private var SHAREATEXTFILE = 5
    private var READCONTACTS = 6
    private var CAPTUREPHOTO = 7

    //request codes
    private val REQUEST_CODE_SELECT_DOC: Int = 100
    private val REQUEST_ENABLE_BT = 1000

    // Managers
    private var bluetoothAdapter: BluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    private lateinit var cameraManager : CameraManager
    private lateinit var clipboardManager : ClipboardManager
    private lateinit var cameraID : String
    private lateinit var ringtone : Ringtone

    //Image Vars
    private var imageIndex: Int = 0

    //Image Uri
    private lateinit var imgUri : Uri

    //Weather Key
    private lateinit var helper: OpenWeatherMapHelper


    @Suppress("DEPRECATION")
    private val imageDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString() + "/assistant/"


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

    // init managers
        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        try {
            cameraID = cameraManager.cameraIdList[0] // 0 is for back camera and 1 is for front camera
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
        clipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        ringtone = RingtoneManager.getRingtone(applicationContext, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
        helper = OpenWeatherMapHelper(getString(R.string.OPEN_WEATHER_MAP_API_KEY))


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
//                        keeper.contains("hello") || keeper.contains("hi") || keeper.contains("")
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
                        keeper.contains("share a file") -> shareAFile() // needs some work
                        keeper.contains("share a text message") -> shareATextMessage()
                        keeper.contains("call") -> callContact()
                        keeper.contains("turn on Bluetooth") -> turnOnBluetooth()
                        keeper.contains("turn off Bluetooth") -> turnOffBluetooth()
                        keeper.contains("get bluetooth devices") -> getAllPairedDevices()
                        keeper.contains("turn on flash") -> turnOnFlash()
                        keeper.contains("turn off flash") -> turnOffFlash()
                        keeper.contains("copy to clipboard") -> clipBoardCopy()
                        keeper.contains("read last clipboard") -> clipBoardSpeak()
                        keeper.contains("capture photo") -> capturePhoto()
                        keeper.contains("play ringtone") -> playRingtone()
                        keeper.contains("stop ringtone") || keeper.contains("top ringtone") -> stopRingtone()
                        keeper.contains("read me") -> readMe()
                        keeper.contains("alarm") -> setAlarm()
                        keeper.contains("weather") -> weather()
                        keeper.contains("horoscope") -> horoscope()
                        keeper.contains("medical") -> medicalApplication()
                        else -> speak("Invalid command, try again")
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
                    textToSpeech.stop()
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
                        REQUESTCALL
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

    private fun shareAFile() {
        if (ContextCompat.checkSelfPermission(
                        this@AssistantActivity,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                    this@AssistantActivity,
                    arrayOf(permission.READ_EXTERNAL_STORAGE, permission.WRITE_EXTERNAL_STORAGE),
                    SHAREAFILE
            )
        }
        else{
            val builder = VmPolicy.Builder()
            StrictMode.setVmPolicy(builder.build())
            val myFileIntent = Intent(Intent.ACTION_GET_CONTENT)
            myFileIntent.type = "application/pdf"
            startActivityForResult(myFileIntent, REQUEST_CODE_SELECT_DOC)
        }
    }

    private fun shareATextMessage() {
        if (ContextCompat.checkSelfPermission(
                        this@AssistantActivity,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                    this@AssistantActivity,
                    arrayOf(permission.READ_EXTERNAL_STORAGE, permission.WRITE_EXTERNAL_STORAGE),
                    SHAREATEXTFILE
            )
        }
        else{
            val builder = VmPolicy.Builder()
            StrictMode.setVmPolicy(builder.build())
            val message = keeper.split("that").toTypedArray()[1]
            //        String subject = keeper.split("with")[1];
            val intentShare = Intent(Intent.ACTION_SEND)
            intentShare.type = "text/plain"
            //        intentShare.putExtra(Intent.EXTRA_SUBJECT,subject);
            intentShare.putExtra(Intent.EXTRA_TEXT, message)
            startActivity(Intent.createChooser(intentShare, "Sharing Text"))
        }
    }

    private fun callContact() {
        if (ContextCompat.checkSelfPermission(
                        this@AssistantActivity,
                        Manifest.permission.READ_CONTACTS
                ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                    this@AssistantActivity,
                    arrayOf(permission.READ_CONTACTS, permission.WRITE_CONTACTS),
                    READCONTACTS
            )
        }
        else
        {
            val name = keeper.split("call").toTypedArray()[1].trim { it <= ' ' }
            Log.d("chk", name)
            try {
                val cursor = contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.TYPE),
                        "DISPLAY_NAME = '$name'", null, null)
                cursor!!.moveToFirst()
                val number = cursor.getString(0)
                // number must not have any spaces
                if (number.trim { it <= ' ' }.length > 0) {

                    // runtime message
                    if (ContextCompat.checkSelfPermission(this@AssistantActivity,
                                    permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(this@AssistantActivity, arrayOf(permission.CALL_PHONE), REQUESTCALL)
                    } else {
                        // passing intent
                        val dial = "tel:$number"
                        startActivity(Intent(Intent.ACTION_CALL, Uri.parse(dial)))
                    }
                } else {
                    // invalid phone
                    Toast.makeText(this@AssistantActivity, "Enter Phone Number", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                speak("Something went wrong")
            }
        }
    }

    private fun turnOnBluetooth() {
        if (!bluetoothAdapter.isEnabled()) {
            speak("Turning On Bluetooth...")
            //intent to on bluetooth
            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(intent, REQUEST_ENABLE_BT)
        } else {
            speak("Bluetooth is already on")
        }
    }

    private fun turnOffBluetooth() {
        if (bluetoothAdapter.isEnabled()) {
            bluetoothAdapter.disable()
            speak("Turning Bluetooth Off")
        } else {
            speak("Bluetooth is already off")
        }
    }

    private fun getAllPairedDevices() {
        if (bluetoothAdapter.isEnabled()) {
            speak("Paired Devices are ")
            var text = ""
            var count = 1
            val devices: Set<BluetoothDevice> = bluetoothAdapter.getBondedDevices()
            for (device in devices) {
                text += "\nDevice: $count ${device.name}, $device"
                count += 1
            }
            speak(text)
        } else {
            //bluetooth is off so can't get paired devices
            speak("Turn on bluetooth to get paired devices")
        }
    }

    private fun turnOnFlash() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                cameraManager.setTorchMode(cameraID, true)
                speak("Flash turned on")
            }
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
            speak("Error Occured")
        }
    }

    private fun turnOffFlash() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                cameraManager.setTorchMode(cameraID, false)
                speak("Flash turned off")
            }
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    fun clipBoardCopy() {
        val data = keeper.split("that").toTypedArray()[1].trim { it <= ' ' }
        if (!data.isEmpty()) {
            val clipData = ClipData.newPlainText("text", data)
            clipboardManager.setPrimaryClip(clipData)
            speak("Data copied to clipboard that is $data")
        }
    }

    fun clipBoardSpeak() {
        val item = clipboardManager.primaryClip!!.getItemAt(0)
        val pasteData = item.text.toString()
        if (pasteData != "") {
            speak("Data stored in last clipboard is " + pasteData)
        } else {
            speak("Clipboard is Empty")
        }
    }

    private fun capturePhoto() {
        if (ContextCompat.checkSelfPermission(
                        this@AssistantActivity,
                        Manifest.permission.CAMERA
                ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                    this@AssistantActivity,
                    arrayOf(permission.CAMERA, permission.WRITE_EXTERNAL_STORAGE),
                    CAPTUREPHOTO
            )
        }
        else
        {
            val builder = VmPolicy.Builder()
            StrictMode.setVmPolicy(builder.build())
            imageIndex++
            val file: String = imageDirectory + imageIndex + ".jpg"
            val newFile = File(file)
            try {
                newFile.createNewFile()
            } catch (e: IOException) {
                e.printStackTrace()
            }
            val outputFileUri = Uri.fromFile(newFile)
            val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, outputFileUri)
            startActivity(cameraIntent)
            speak("Photo  will be saved to $file")
        }
    }

    private fun playRingtone() {
        speak("Ringtone playing")
        ringtone.play()
    }

    private fun stopRingtone() {
        speak("Ringtone stopped")
        ringtone.stop()
    }

    private fun readMe()
    {
        CropImage.startPickImageActivity(this@AssistantActivity)
    }

//    private fun getTextFromBitmap(bitmap: Bitmap) {
//        val image : FirebaseVisionImage = FirebaseVisionImage.fromBitmap(bitmap)
//        val textDetector : FirebaseVisionTextDetector = FirebaseVision.getInstance().visionTextDetector
//        textDetector.detectInImage(image).addOnSuccessListener { firebaseVisionText -> displayTextFromImage(firebaseVisionText!!) }.addOnFailureListener { e -> Toast.makeText(this@AssistantActivity, "Error: " + e.message, Toast.LENGTH_SHORT).show() }
//    }

        private fun getTextFromBitmap(bitmap: Bitmap) {
            val image = InputImage.fromBitmap(bitmap, 0)
            val recognizer = TextRecognition.getClient()
            val result = recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        // Task completed successfully
                        // ...
                        val resultText = visionText.text
//                        for (block in visionText.textBlocks) {
//                            val blockText = block.text
//                            val blockCornerPoints = block.cornerPoints
//                            val blockFrame = block.boundingBox
//                            for (line in block.lines) {
//                                val lineText = line.text
//                                val lineCornerPoints = line.cornerPoints
//                                val lineFrame = line.boundingBox
//                                for (element in line.elements) {
//                                    val elementText = element.text
//                                    val elementCornerPoints = element.cornerPoints
//                                    val elementFrame = element.boundingBox
//                                }
//                            }
//                        }
                        if(keeper.contains("summarise"))
                        {
                            speak("Reading Image and Summarising it :\n" + summariseText(resultText))
                        }
                        else {
                            speak("Reading Image:\n" + resultText)

                        }
                    }
                    .addOnFailureListener { e ->
                        // Task failed with an exception
                        // ...
                        Toast.makeText(this@AssistantActivity, "Error: " + e.message, Toast.LENGTH_SHORT).show()
                    }
        }

    private fun summariseText(text: String): String? {
        val summary: ObjectRef<*> = ObjectRef<Any?>()
        summary.element = Text2Summary.Companion.summarize(text, 0.4f)
        return summary.element as String
    }

    private fun setAlarm()
    {

    }

    private fun medicalApplication()
    {

    }

    private fun weather()
    {
        if(keeper.contains("Fahrenheit"))
        {
            helper.setUnits(Units.IMPERIAL)
        }
        else if(keeper.contains("Celsius"))
        {
            helper.setUnits(Units.METRIC)
        }

        val keeperSplit = keeper.replace(" ".toRegex(), "").split("w").toTypedArray()
        val city = keeperSplit[0]
        Log.d("chk", city)

        helper.getCurrentWeatherByCityName(city, object : CurrentWeatherCallback {
            override fun onSuccess(currentWeather: CurrentWeather) {
                speak("""
    Coordinates: ${currentWeather.coord.lat}, ${currentWeather.coord.lon}
    Weather Description: ${currentWeather.weather[0].description}
    Temperature: ${currentWeather.main.tempMax}
    Wind Speed: ${currentWeather.wind.speed}
    City, Country: ${currentWeather.name}, ${currentWeather.sys.country}
    """.trimIndent()
                )
            }

            override fun onFailure(throwable: Throwable) {
                speak("Error" + throwable.message)
            }
        })
    }

    private fun horoscope()
    {
        Log.d("chk", "hello")
        val hGemini : Horoscope? = Horoscope.Zodiac(this)
                .requestSunSign(SUNSIGN.GEMINI)
                .requestDuration(DURATION.TODAY)
                .showLoader(true)
                .isDebuggable(true)
                .fetchHoroscope()
        val cGemini = HorologyController(object : Response {
            override fun onResponseObtained(zodiac: Zodiac) {
                val horoscope: String = zodiac.getHoroscope()
                val sunsign: String = zodiac.getSunSign()
                val date: String = zodiac.getDate()
                Log.d("chk", horoscope+sunsign+date)
            }

            override fun onErrorObtained(errormsg: String?) {
                speak("Can't Reach Data")
            }
        })
        cGemini.requestConstellations(hGemini)
    }

    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<out String>,
            grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUESTCALL) {
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
        else if(requestCode == SHAREAFILE)
        {
            if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // when permission granted
                shareAFile()
            } else {
                // permission denied
                Toast.makeText(this, "Permission DENIED", Toast.LENGTH_SHORT).show()
            }
        }
        else if(requestCode == SHAREATEXTFILE)
        {
            if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // when permission granted
                shareATextMessage()
            } else {
                // permission denied
                Toast.makeText(this, "Permission DENIED", Toast.LENGTH_SHORT).show()
            }
        }
        else if(requestCode == READCONTACTS)
        {
            if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // when permission granted
                callContact()
            } else {
                // permission denied
                Toast.makeText(this, "Permission DENIED", Toast.LENGTH_SHORT).show()
            }
        }
        else if(requestCode == CAPTUREPHOTO)
        {
            if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // when permission granted
                capturePhoto()
            } else {
                // permission denied
                Toast.makeText(this, "Permission DENIED", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_SELECT_DOC && resultCode == RESULT_OK) {
            val filePath = data!!.data!!.path
            Log.d("chk", "path: $filePath")
            val file= File(filePath)
            val intentShare = Intent(Intent.ACTION_SEND)
            intentShare.type = "application/pdf"
            intentShare.putExtra(Intent.EXTRA_STREAM, Uri.parse("file://$file"))
            startActivity(Intent.createChooser(intentShare, "Share the file ..."))
        }
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                speak("Bluetooth is on")
            } else {
                speak("Could'nt turn on bluetooth")
            }
        }

        if (requestCode == CropImage.PICK_IMAGE_CHOOSER_REQUEST_CODE && resultCode == RESULT_OK) {
            val imageUri = CropImage.getPickImageResultUri(this, data)
            imgUri = imageUri
            startCrop(imageUri)
        }

        if (requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE) {
            val result : CropImage.ActivityResult = CropImage.getActivityResult(data)
            if (resultCode == RESULT_OK) {
                imgUri = result.uri
                try {
                    val inputStream = contentResolver.openInputStream(imgUri)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    getTextFromBitmap(bitmap)
                } catch (e: FileNotFoundException) {
                    e.printStackTrace()
                }
                Toast.makeText(this, "Image captured successfully !", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startCrop(imageUri: Uri) {
        CropImage.activity(imageUri).setGuidelines(CropImageView.Guidelines.ON).setMultiTouchEnabled(true).start(this@AssistantActivity)
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