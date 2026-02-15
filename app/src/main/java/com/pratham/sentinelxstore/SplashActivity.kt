package com.pratham.sentinelxstore

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AnticipateOvershootInterpolator
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_splash)

        val layers = listOf(
            findViewById<View>(R.id.svgLayer1),
            findViewById<View>(R.id.svgLayer2),
            findViewById<View>(R.id.svgLayer3),
            findViewById<View>(R.id.svgLayer4)
        )

        layers.forEach {
            it.translationX = -1000f
            it.alpha = 0f
            it.visibility = View.VISIBLE
        }

        lifecycleScope.launch {
            delay(600)

            layers.forEachIndexed { _, view ->
                playSmoothAnimation(view)
                delay(500)
            }

            delay(2000)

            val intent = Intent(this@SplashActivity, MainActivity::class.java)
            startActivity(intent)

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                overrideActivityTransition(
                    OVERRIDE_TRANSITION_OPEN,
                    R.anim.slide_in_right,
                    R.anim.slide_out_left
                )
            } else {
                @Suppress("DEPRECATION")
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            }

            finish()
        }
    }

    private fun playSmoothAnimation(view: View) {
        val moveIn = ObjectAnimator.ofFloat(view, "translationX", 0f).apply {
            duration = 1500
            interpolator = AnticipateOvershootInterpolator(2.0f)
        }

        val fadeIn = ObjectAnimator.ofFloat(view, "alpha", 1f).apply {
            duration = 1000
        }

        AnimatorSet().apply {
            playTogether(moveIn, fadeIn)
            start()
        }
    }
}