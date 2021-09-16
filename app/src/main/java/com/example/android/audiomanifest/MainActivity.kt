package com.example.android.audiomanifest

import android.Manifest.permission.RECORD_AUDIO
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Environment.getExternalStoragePublicDirectory
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*


@Suppress("DEPRECATION")
class MainActivity : AppCompatActivity() {
    private lateinit var ibtnRecord: ImageButton
    private lateinit var ibtnPlay: ImageButton
    private lateinit var ibtnTakePhoto: ImageButton
    private lateinit var ibtnSharePhoto: ImageButton
    private lateinit var ibtnShareAudio: ImageButton
    private lateinit var tvRecordActionPrompt: TextView
    private lateinit var tvPlayActionPrompt: TextView
    private lateinit var cardView: CardView
    private lateinit var ivThumbnail: ImageView
    private lateinit var ivMask: ImageView
    private lateinit var llShareImage: LinearLayout
    private lateinit var llPlayAudio: LinearLayout
    private lateinit var llShareAudio: LinearLayout
    private lateinit var avBanner: AdView

    private var isRecording: Boolean = false
    private var isPlaying: Boolean = false
    private var mMediaRecorder: MediaRecorder? = null
    private var mMediaPlayer: MediaPlayer? = null
    private var audioFileName = ""
    private lateinit var imageFilePath: String
    private var pictureURI: Uri? = null
    private var audioClipURI: Uri? = null



    private val REQUEST_AUDIO_PERMISSION_CODE = 1
    private val REQUEST_STORAGE_PERMISSION_CODE = 2
    private val REQUEST_IMAGE_CAPTURE_CODE = 3
    private val TAG = "Mgoza"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ibtnRecord = findViewById(R.id.ibtnRecord)
        ibtnPlay = findViewById(R.id.ibtnPlay)
        ibtnTakePhoto = findViewById(R.id.ibtnTakePhoto)
        ibtnSharePhoto = findViewById(R.id.ibtnSharePhoto)
        ibtnShareAudio = findViewById(R.id.ibtnShareAudio)
        tvRecordActionPrompt = findViewById(R.id.tvRecordActionPrompt)
        tvPlayActionPrompt = findViewById(R.id.tvPlayActionPrompt)
        ivThumbnail = findViewById(R.id.ivThumbnail)
        ivMask = findViewById(R.id.ivMask)
        avBanner = findViewById(R.id.avBanner)
        cardView = findViewById(R.id.cardView)
        llShareImage = findViewById(R.id.llShareImage)
        llPlayAudio = findViewById(R.id.llPlayAudio)
        llShareAudio = findViewById(R.id.llShareAudio)

        //Load ad
        MobileAds.initialize(this){}
        val adRequest = AdRequest.Builder().build()
        avBanner.loadAd(adRequest)

        ibtnRecord.setOnClickListener {
            if(isRecording)
                stopRecording()
            else
                startRecording()
            isRecording = !isRecording
        }
        ibtnPlay.setOnClickListener {
            if(isPlaying)
                stopPlaying()
            else
                startPlaying()
            isPlaying = !isPlaying
        }
        ibtnTakePhoto.setOnClickListener {
            takePicture()
        }
        ibtnShareAudio.setOnClickListener {
            //val imageUri: Uri = Uri.fromFile(File(audioFileName))
            val audioShareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_STREAM, audioClipURI)
                type = "audio/mp3"
            }
            startActivity(Intent.createChooser(audioShareIntent, "Share sound clip"))
        }
        ibtnSharePhoto.setOnClickListener {
            val photoShareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_STREAM, pictureURI)
                type = "image/jpeg"
            }
            startActivity(Intent.createChooser(photoShareIntent, "Share Image"))
        }
    }
    @Throws(IOException::class)
    private fun createImageFile(): File {
        //generate filename based on time
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        //val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val storageDir: File? = Environment.getExternalStorageDirectory()

        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir).apply{
            imageFilePath = absolutePath
        }
    }

    private fun takePicture(){
        /*val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        try{
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE_CODE)
        }catch (e: ActivityNotFoundException){
            Toast.makeText(this, "Camera not found", Toast.LENGTH_SHORT).show()
        }*/
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            //Ensure there's a camera activity to handle the intent
            takePictureIntent.resolveActivity(packageManager)?.also {
                //Create a file in which to store the photo
                val pictureFile: File? = try {
                    createImageFile()
                }catch (ioe: IOException){
                    Log.d(TAG, "takePicture: ${ioe.message}")
                    null
                }
                //continue iff file was create successfully
                pictureFile?.also {
                    pictureURI = FileProvider.getUriForFile(this, "com.example.android.audiomanifest", it)
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, pictureURI)
                    startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE_CODE)
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if(requestCode == REQUEST_IMAGE_CAPTURE_CODE && resultCode == RESULT_OK){
            /*val imgBitmap = data?.extras?.get("data") as Bitmap?
            ivThumbnail.setImageBitmap(imgBitmap)*/
            ivThumbnail.setImageURI(pictureURI)
            if(cardView.visibility == View.GONE){
                cardView.visibility = View.VISIBLE
            }
            llShareImage.visibility = View.VISIBLE
            ivThumbnail.visibility = View.VISIBLE
            ivMask.visibility = View.VISIBLE
        }
    }

    //region AUDIO CONTROLS
    private fun startPlaying() {
        mMediaPlayer = MediaPlayer()
        try {
            mMediaPlayer?.setDataSource(audioFileName)
            mMediaPlayer?.prepare()
            mMediaPlayer?.start()

            mMediaPlayer?.setOnCompletionListener {
                stopPlaying()
            }

            //Communicate play state visually
            ibtnPlay.setImageResource(R.drawable.ic_baseline_stop_24)
            ibtnPlay.setBackgroundResource(R.drawable.circle_btn_outline)
            ibtnPlay.setColorFilter(ContextCompat.getColor(this, R.color.black))

            tvPlayActionPrompt.text = "Press to stop"

        }catch (e: IOException){
            e.printStackTrace()
        }
    }

    private fun stopPlaying() {
        mMediaPlayer?.release()
        mMediaPlayer = null

        //communicate state visually
        ibtnPlay.setImageResource(R.drawable.ic_baseline_play_arrow_24)
        ibtnPlay.setBackgroundResource(R.drawable.circle_btn_filled)
        ibtnPlay.setColorFilter(ContextCompat.getColor(this, R.color.white))

        tvPlayActionPrompt.text = "Press to play"
    }

    private fun stopRecording() {
        mMediaRecorder?.stop()
        mMediaRecorder?.release()
        mMediaRecorder = null

        //Communicate current state
        ibtnRecord.setImageResource(R.drawable.ic_baseline_mic_24)
        ibtnRecord.setBackgroundResource(R.drawable.circle_btn_filled)
        ibtnRecord.setColorFilter(ContextCompat.getColor(this, R.color.white))

        tvRecordActionPrompt.text = "Press to record"
        if(cardView.visibility == View.GONE){
            cardView.visibility = View.VISIBLE
        }
        llPlayAudio.visibility = View.VISIBLE
        llShareAudio.visibility = View.VISIBLE
    }

    private fun startRecording() {
        //Start by checking permissions
        if(permissionGranted()){

            //create a path to the root external storage directory
            audioFileName = getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).absolutePath
            //fileName = getExternalFilesDir(null)?.absolutePath.toString()
            audioFileName += "/recordedAudio.mp3"

            mMediaRecorder = MediaRecorder()
            mMediaRecorder!!.setAudioSource(MediaRecorder.AudioSource.MIC)
            //mMediaRecorder!!.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            mMediaRecorder!!.setOutputFormat(MediaRecorder.OutputFormat.DEFAULT)
            mMediaRecorder!!.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            mMediaRecorder!!.setOutputFile(audioFileName)

            //create a file from audio source and generate a uri
            val audioFile= File(audioFileName)
            audioClipURI = FileProvider.getUriForFile(this, packageName, audioFile)

            try {
                mMediaRecorder!!.prepare()
            }catch (e: IOException){
                e.printStackTrace()
            }
            mMediaRecorder!!.start()

            //Display recording state visually
            ibtnRecord.setImageResource(R.drawable.ic_baseline_mic_off_24)
            ibtnRecord.setBackgroundResource(R.drawable.circle_btn_outline)
            ibtnRecord.setColorFilter(ContextCompat.getColor(this, R.color.black))

            tvRecordActionPrompt.text = "Press to stop"
        }else{
            requestPermission()
        }
    }

    //endregion

    private fun requestPermission() {
        Log.d(TAG, "requestPermission: Asking for permission")
        ActivityCompat.requestPermissions(this@MainActivity, arrayOf(RECORD_AUDIO, WRITE_EXTERNAL_STORAGE), REQUEST_AUDIO_PERMISSION_CODE)
    }

    private fun permissionGranted(): Boolean {
        val storagePermission: Int = ContextCompat.checkSelfPermission(applicationContext, WRITE_EXTERNAL_STORAGE)
        val microphonePermission: Int = ContextCompat.checkSelfPermission(applicationContext, RECORD_AUDIO)

        return storagePermission == PackageManager.PERMISSION_GRANTED && microphonePermission == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when(requestCode){
            REQUEST_AUDIO_PERMISSION_CODE -> if (grantResults.isNotEmpty()) {
                val canRecord = grantResults[0] == PackageManager.PERMISSION_GRANTED

                if (canRecord) {
                    Toast.makeText(this, "Microphone permission granted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Microphone permission denied", Toast.LENGTH_SHORT).show()
                }
                Log.d(TAG, "onRequestPermissionsResult: record: $canRecord")
            }
            REQUEST_STORAGE_PERMISSION_CODE -> if(grantResults.isNotEmpty()){
                val canStore = grantResults[0] == PackageManager.PERMISSION_GRANTED

                if (canStore) {
                    Toast.makeText(this, "Storage permission granted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Storage permission denied", Toast.LENGTH_SHORT).show()
                }
                Log.d(TAG, "onRequestPermissionsResult: record: $canStore")
            }
        }
    }
}