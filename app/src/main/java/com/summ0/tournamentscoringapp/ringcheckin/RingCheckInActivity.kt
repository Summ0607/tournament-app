package com.summ0.tournamentscoringapp.ringcheckin

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.CheckBox
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.summ0.tournamentscoringapp.R
import com.summ0.tournamentscoringapp.BuildConfig
import com.summ0.tournamentscoringapp.EXTRA_LAUNCH_SCREEN
import com.summ0.tournamentscoringapp.LAUNCH_SCREEN_HYUNGS
import com.summ0.tournamentscoringapp.LAUNCH_SCREEN_WEAPONS
import com.summ0.tournamentscoringapp.MainActivity

data class Competitor(
    val name: String,
    val isPresent: Boolean,
    val isSparring: Boolean
)

class RingCheckInActivity : ComponentActivity() {

    private val competitors = mutableListOf(
        Competitor("Victor Wang", isPresent = false, isSparring = false),
        Competitor("Ben Carter", isPresent = false, isSparring = false),
        Competitor("Felix Garcia", isPresent = false, isSparring = false),
        Competitor("Liam Moore", isPresent = false, isSparring = false)
    )

    private lateinit var checkedInText: TextView
    private lateinit var entrantsText: TextView
    private lateinit var judgesCheckBox: CheckBox
    private lateinit var adapter: CompetitorAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ring_check_in)

        bindHeader()

        findViewById<View>(R.id.addCompetitorButton).setOnClickListener {
            showAddCompetitorDialog()
        }
        findViewById<View>(R.id.openCompetitionButton).setOnClickListener {
            openScoringScreen(LAUNCH_SCREEN_HYUNGS)
        }

        val recyclerView = findViewById<RecyclerView>(R.id.competitorRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = CompetitorAdapter(competitors) {
            updateHeaderSummary()
        }
        recyclerView.adapter = adapter

        updateHeaderSummary()
    }

    private fun bindHeader() {
        checkedInText = findViewById(R.id.checkedInCount)
        entrantsText = findViewById(R.id.entrantsSummary)
        judgesCheckBox = findViewById(R.id.useOnlyThreeJudges)
        findViewById<TextView>(R.id.versionText).text = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        judgesCheckBox.isChecked = false
    }

    private fun updateHeaderSummary() {
        val checkedInCount = competitors.count { it.isPresent }
        val sparringCount = competitors.count { it.isSparring }
        checkedInText.text = "Checked In: $checkedInCount"
        entrantsText.text = "Entrants - Weapons: 0 | Hyungs: 0 | Sparring: $sparringCount"
    }

    private fun showAddCompetitorDialog() {
        val input = EditText(this).apply {
            hint = "Competitor name"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }

        AlertDialog.Builder(this)
            .setTitle("Add Competitor")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Add") { _, _ ->
                val name = input.text?.toString().orEmpty().trim()
                if (name.isNotBlank()) {
                    competitors.add(
                        Competitor(
                            name = name,
                            isPresent = false,
                            isSparring = false
                        )
                    )
                    adapter.notifyItemInserted(competitors.lastIndex)
                    updateHeaderSummary()
                }
            }
            .show()
    }

    private fun openScoringScreen(launchScreen: String) {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                putExtra(EXTRA_LAUNCH_SCREEN, launchScreen)
            }
        )
    }
}

private class CompetitorAdapter(
    private val items: MutableList<Competitor>,
    private val onStateChanged: () -> Unit
) : RecyclerView.Adapter<CompetitorViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CompetitorViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_competitor_row, parent, false)
        return CompetitorViewHolder(view)
    }

    override fun onBindViewHolder(holder: CompetitorViewHolder, position: Int) {
        holder.bind(
            competitor = items[position],
            onPresentToggle = { updated ->
                items[position] = updated
                notifyItemChanged(position)
                onStateChanged()
            },
            onSparringToggle = { updated ->
                items[position] = updated
                notifyItemChanged(position)
                onStateChanged()
            }
        )
    }

    override fun getItemCount(): Int = items.size
}

private class CompetitorViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val nameView: TextView = itemView.findViewById(R.id.competitorName)
    private val presentCircle: View = itemView.findViewById(R.id.presentCircle)
    private val sparringCircle: View = itemView.findViewById(R.id.sparringCircle)

    fun bind(
        competitor: Competitor,
        onPresentToggle: (Competitor) -> Unit,
        onSparringToggle: (Competitor) -> Unit
    ) {
        nameView.text = competitor.name

        renderCircle(presentCircle, competitor.isPresent, PRESENT_COLOR)
        renderCircle(sparringCircle, competitor.isSparring, SPARRING_COLOR)

        presentCircle.setOnClickListener {
            val nextPresent = !competitor.isPresent
            onPresentToggle(
                competitor.copy(
                    isPresent = nextPresent,
                    isSparring = if (nextPresent) true else false
                )
            )
        }

        sparringCircle.setOnClickListener {
            val nextSparring = !competitor.isSparring
            onSparringToggle(
                competitor.copy(
                    isPresent = if (nextSparring) true else competitor.isPresent,
                    isSparring = nextSparring
                )
            )
        }
    }

    private fun renderCircle(view: View, isOn: Boolean, fillColor: Int) {
        val strokeColor = Color.parseColor("#B8B8B8")
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (isOn) fillColor else Color.TRANSPARENT)
            setStroke(dp(1), strokeColor)
        }
        view.background = drawable
        view.contentDescription = if (isOn) "Selected" else "Not selected"
    }

    private fun dp(value: Int): Int {
        return (value * itemView.resources.displayMetrics.density).toInt()
    }

    companion object {
        private val PRESENT_COLOR = Color.parseColor("#2E7D32")
        private val SPARRING_COLOR = Color.parseColor("#6A1B9A")
    }
}
