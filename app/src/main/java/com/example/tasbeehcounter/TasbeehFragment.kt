package com.example.tasbeehcounter

import android.animation.ObjectAnimator
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.view.GestureDetectorCompat
import androidx.fragment.app.Fragment
import android.view.GestureDetector
import com.example.tasbeehcounter.databinding.FragmentTasbeehBinding

class TasbeehFragment : Fragment() {
    private var _binding: FragmentTasbeehBinding? = null
    private val binding get() = _binding!!
    private var count = 0
    private var isCounting = false
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var vibrator: Vibrator
    private val handler = Handler(Looper.getMainLooper())
    private var currentQuoteIndex = 0
    private lateinit var gestureDetector: GestureDetectorCompat

    private val quotes = listOf(
        IslamicQuote(
            "Verily, with hardship comes ease.",
            "فَإِنَّ مَعَ الْعُسْرِ يُسْرًا",
            "بےشک مشکل کے ساتھ آسانی ہے",
            "Quran 94:5",
            false
        ),
        IslamicQuote(
            "Indeed, Allah is with those who are patient.",
            "إِنَّ اللَّهَ مَعَ الصَّابِرِينَ",
            "بےشک اللہ صبر کرنے والوں کے ساتھ ہے",
            "Quran 2:153",
            false
        ),
        IslamicQuote(
            "The best among you are those who have the best manners and character.",
            "إِنَّ مِنْ خِيَارِكُمْ أَحْسَنَكُمْ أَخْلَاقًا",
            "تم میں سے بہترین وہ ہیں جو اخلاق میں بہترین ہیں",
            "Sahih al-Bukhari",
            true
        ),
        IslamicQuote(
            "Whoever believes in Allah and the Last Day, let him speak good or remain silent.",
            "مَنْ كَانَ يُؤْمِنُ بِاللَّهِ وَالْيَوْمِ الْآخِرِ فَلْيَقُلْ خَيْرًا أَوْ لِيَصْمُتْ",
            "جو اللہ اور آخرت پر ایمان رکھتا ہے وہ اچھی بات کہے یا خاموش رہے",
            "Sahih al-Bukhari",
            true
        ),
        IslamicQuote(
            "Allah does not burden a soul beyond that it can bear.",
            "لَا يُكَلِّفُ اللَّهُ نَفْسًا إِلَّا وُسْعَهَا",
            "اللہ کسی جان پر اس کی طاقت سے زیادہ بوجھ نہیں ڈالتا",
            "Quran 2:286",
            false
        ),
        IslamicQuote(
            "The most beloved of people to Allah are those who are most beneficial to people.",
            "أَحَبُّ النَّاسِ إِلَى اللَّهِ أَنْفَعُهُمْ لِلنَّاسِ",
            "اللہ کے نزدیک سب سے محبوب وہ شخص ہے جو لوگوں کو سب سے زیادہ فائدہ پہنچائے",
            "Sahih al-Jami",
            true
        ),
        IslamicQuote(
            "And We have certainly created man in the best of stature.",
            "لَقَدْ خَلَقْنَا الْإِنْسَانَ فِي أَحْسَنِ تَقْوِيمٍ",
            "بےشک ہم نے انسان کو بہترین ساخت میں پیدا کیا",
            "Quran 95:4",
            false
        ),
        IslamicQuote(
            "The strong person is not the one who can wrestle someone else down. The strong person is the one who can control himself when he is angry.",
            "لَيْسَ الشَّدِيدُ بِالصُّرَعَةِ، إِنَّمَا الشَّدِيدُ الَّذِي يَمْلِكُ نَفْسَهُ عِنْدَ الْغَضَبِ",
            "طاقتور وہ نہیں جو کسی کو پچھاڑ دے، بلکہ طاقتور وہ ہے جو غصے میں اپنے آپ پر قابو رکھے",
            "Sahih al-Bukhari",
            true
        )
    )

    private val quoteRunnable = object : Runnable {
        override fun run() {
            showNextQuote()
            handler.postDelayed(this, 30000) // 30 seconds
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTasbeehBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupSharedPreferences()
        setupVibrator()
        setupCounter()
        setupQuoteNavigation()
        setupGestureDetector()
        updateUI()
        updateStartStopButton()
        showNextQuote()
        startQuoteRotation()
    }

    private fun setupSharedPreferences() {
        sharedPreferences = requireContext().getSharedPreferences("TasbeehSettings", Context.MODE_PRIVATE)
    }

    private fun setupVibrator() {
        vibrator = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    private fun setupCounter() {
        binding.counterButton.setOnClickListener {
            if (isCounting) incrementCount()
        }

        binding.resetButton.setOnClickListener {
            count = 0
            updateUI()
        }

        binding.startStopButton.setOnClickListener {
            isCounting = !isCounting
            updateStartStopButton()
        }

        // Handle fullscreen tap
        binding.root.setOnClickListener {
            if (isCounting) {
                incrementCount()
            }
        }
    }

    private fun incrementCount() {
        count++
        if (sharedPreferences.getBoolean("vibration", false)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(50)
            }
        }
        updateUI()
    }

    private fun updateUI() {
        binding.counterText.text = count.toString()
        binding.counterButton.visibility = View.GONE // Always hide the counter button in fullscreen mode
    }

    private fun updateStartStopButton() {
        binding.startStopButton.text = if (isCounting) "Stop" else "Start"
    }

    private fun setupQuoteNavigation() {
        binding.prevQuoteButton.setOnClickListener {
            showPreviousQuote()
        }

        binding.nextQuoteButton.setOnClickListener {
            showNextQuote()
        }
    }

    private fun showPreviousQuote() {
        currentQuoteIndex = if (currentQuoteIndex > 0) {
            currentQuoteIndex - 1
        } else {
            quotes.size - 1
        }
        updateQuoteWithAnimation()
    }

    private fun showNextQuote() {
        currentQuoteIndex = (currentQuoteIndex + 1) % quotes.size
        updateQuoteWithAnimation()
    }

    private fun updateQuoteWithAnimation() {
        // Fade out
        val fadeOut = ObjectAnimator.ofFloat(binding.quoteCard, "alpha", 1f, 0f)
        fadeOut.duration = 500
        fadeOut.interpolator = AccelerateDecelerateInterpolator()
        fadeOut.start()

        // Update text and fade in
        fadeOut.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                val quote = quotes[currentQuoteIndex]
                binding.quoteText.text = quote.text
                binding.quoteArabicText.text = quote.arabicText
                binding.quoteUrduText.text = quote.urduText
                binding.quoteSource.text = if (quote.isHadith) "Hadith - ${quote.source}" else "Quran - ${quote.source}"
                
                val fadeIn = ObjectAnimator.ofFloat(binding.quoteCard, "alpha", 0f, 1f)
                fadeIn.duration = 500
                fadeIn.interpolator = AccelerateDecelerateInterpolator()
                fadeIn.start()
            }
        })
    }

    private fun startQuoteRotation() {
        handler.postDelayed(quoteRunnable, 30000) // Start first rotation after 30 seconds
    }

    private fun setupGestureDetector() {
        gestureDetector = GestureDetectorCompat(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y
                
                // Check if the swipe is horizontal and has enough velocity
                if (Math.abs(diffX) > Math.abs(diffY) && Math.abs(diffX) > 100) {
                    if (diffX > 0) {
                        // Swipe right - show previous quote
                        showPreviousQuote()
                    } else {
                        // Swipe left - show next quote
                        showNextQuote()
                    }
                    return true
                }
                return false
            }
        })

        // Set up touch listener for the quote card
        binding.quoteCard.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(quoteRunnable)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(quoteRunnable)
        _binding = null
    }
} 