package com.pentagram.airplay

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.button.MaterialButton

/**
 * Onboarding activity that guides new users through the app's features.
 * Shown on first launch, can be skipped.
 */
class OnboardingActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var indicatorLayout: LinearLayout
    private lateinit var nextButton: MaterialButton
    private lateinit var skipButton: TextView
    private lateinit var preferencesManager: PreferencesManager

    private val onboardingPages = listOf(
        OnboardingPage(
            titleRes = R.string.onboarding_welcome_title,
            descriptionRes = R.string.onboarding_welcome_desc,
            iconRes = R.mipmap.ic_launcher_inverted
        ),
        OnboardingPage(
            titleRes = R.string.onboarding_connect_title,
            descriptionRes = R.string.onboarding_connect_desc,
            iconRes = android.R.drawable.ic_menu_share
        ),
        OnboardingPage(
            titleRes = R.string.onboarding_start_title,
            descriptionRes = R.string.onboarding_start_desc,
            iconRes = android.R.drawable.ic_media_play
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        preferencesManager = PreferencesManager(this)

        viewPager = findViewById(R.id.viewPager)
        indicatorLayout = findViewById(R.id.indicatorLayout)
        nextButton = findViewById(R.id.nextButton)
        skipButton = findViewById(R.id.skipButton)

        setupViewPager()
        setupIndicators()
        setupButtons()
    }

    private fun setupViewPager() {
        viewPager.adapter = OnboardingAdapter(onboardingPages)
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateIndicators(position)
                updateButton(position)
            }
        })
    }

    private fun setupIndicators() {
        val indicators = arrayOfNulls<View>(onboardingPages.size)
        val layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(8, 0, 8, 0)
        }

        for (i in indicators.indices) {
            indicators[i] = View(this).apply {
                this.layoutParams = LinearLayout.LayoutParams(12, 12).apply {
                    setMargins(8, 0, 8, 0)
                }
                background = createIndicatorDrawable(i == 0)
            }
            indicatorLayout.addView(indicators[i])
        }
    }

    private fun createIndicatorDrawable(isActive: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(
                if (isActive) {
                    ContextCompat.getColor(this@OnboardingActivity, R.color.button_default)
                } else {
                    ContextCompat.getColor(this@OnboardingActivity, R.color.text_secondary)
                }
            )
        }
    }

    private fun updateIndicators(position: Int) {
        for (i in 0 until indicatorLayout.childCount) {
            val indicator = indicatorLayout.getChildAt(i)
            indicator.background = createIndicatorDrawable(i == position)
        }
    }

    private fun updateButton(position: Int) {
        if (position == onboardingPages.size - 1) {
            nextButton.text = getString(R.string.get_started)
            skipButton.visibility = View.INVISIBLE
        } else {
            nextButton.text = getString(R.string.next)
            skipButton.visibility = View.VISIBLE
        }
    }

    private fun setupButtons() {
        nextButton.setOnClickListener {
            if (viewPager.currentItem < onboardingPages.size - 1) {
                viewPager.currentItem = viewPager.currentItem + 1
            } else {
                completeOnboarding()
            }
        }

        skipButton.setOnClickListener {
            completeOnboarding()
        }
    }

    private fun completeOnboarding() {
        preferencesManager.isOnboardingCompleted = true
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    /**
     * Data class representing an onboarding page.
     */
    data class OnboardingPage(
        val titleRes: Int,
        val descriptionRes: Int,
        val iconRes: Int
    )

    /**
     * RecyclerView adapter for onboarding pages.
     */
    inner class OnboardingAdapter(
        private val pages: List<OnboardingPage>
    ) : RecyclerView.Adapter<OnboardingAdapter.PageViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_onboarding_page, parent, false)
            return PageViewHolder(view)
        }

        override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
            holder.bind(pages[position])
        }

        override fun getItemCount(): Int = pages.size

        inner class PageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val iconView: ImageView = itemView.findViewById(R.id.pageIcon)
            private val titleView: TextView = itemView.findViewById(R.id.pageTitle)
            private val descriptionView: TextView = itemView.findViewById(R.id.pageDescription)

            fun bind(page: OnboardingPage) {
                iconView.setImageResource(page.iconRes)
                titleView.setText(page.titleRes)
                descriptionView.setText(page.descriptionRes)
            }
        }
    }
}
