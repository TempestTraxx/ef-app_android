package org.eurofurence.connavigator.ui

import android.content.Intent
import android.os.Bundle
import android.support.design.widget.FloatingActionButton
import android.support.design.widget.NavigationView
import android.support.design.widget.Snackbar
import android.support.design.widget.TabLayout
import android.support.v4.app.Fragment
import android.support.v4.view.GravityCompat
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import io.swagger.client.model.EventEntry
import io.swagger.client.model.Info
import org.eurofurence.connavigator.BuildConfig
import org.eurofurence.connavigator.R
import org.eurofurence.connavigator.database.Database
import org.eurofurence.connavigator.database.UpdateIntentService
import org.eurofurence.connavigator.tracking.Analytics
import org.eurofurence.connavigator.ui.communication.ContentAPI
import org.eurofurence.connavigator.ui.communication.RootAPI
import org.eurofurence.connavigator.util.delegators.header
import org.eurofurence.connavigator.util.delegators.view
import org.eurofurence.connavigator.util.extensions.*
import java.util.*

class ActivityRoot : AppCompatActivity(), RootAPI {
    // Views
    val toolbar by view(Toolbar::class.java)
    override val tabs by view(TabLayout::class.java)
    val drawer by view(DrawerLayout::class.java)
    val fab by view(FloatingActionButton::class.java)

    // Views in navigation view
    val navView by view(NavigationView::class.java)
    val navDays by header(TextView::class.java, { navView })
    val navTitle by header(TextView::class.java, { navView })
    val navSubtitle by header(TextView::class.java, { navView })

    // Content API aggregator
    var content: Set<ContentAPI> = setOf()

    /**
     * Listens to update responses, since the event recycler holds database related data
     */
    val updateReceiver = localReceiver(UpdateIntentService.UPDATE_COMPLETE) {
        // Get intent extras
        val success = it.booleans["success"]
        val time = it.objects["time", Date::class.java]

        if (!BuildConfig.DEBUG)
        // Make a snackbar for the result
            Snackbar.make(findViewById(R.id.fab)!!, "Database reload ${if (success) "successful" else "failed"}, version $time", Snackbar.LENGTH_LONG).show()

        // Update content data if fragments implement content API
        applyOnContent {
            logv { "Updated the data and dispatching to $this" }
            dataUpdated()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Assign the layout
        setContentView(R.layout.activity_root)

        Analytics.changeScreenName("Root")
        setupContent()
        setupBar()
        setupBarNavLink()
        setupNav()
        setupFab()
    }

    private fun setupContent() =
            navigateRoot(FragmentEventsViewpager::class.java)

    override fun onResume() {
        super.onResume()
        updateReceiver.register()
    }

    override fun onPause() {
        updateReceiver.unregister()
        super.onPause()
    }

    override fun onBackPressed() {
        if (drawer.isDrawerOpen(GravityCompat.START))
            drawer.closeDrawer(GravityCompat.START)
        else
            super.onBackPressed()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu options
        menuInflater.inflate(R.menu.activity_root, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Propagate the ID based selection to functions
        return when (item.itemId) {
            R.id.action_settings -> handleSettings().let { true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupBar() {
        // Set action bar for support
        setSupportActionBar(toolbar)
    }

    private fun setupBarNavLink() {
        // Connect drawer and toolbar
        val toggle = ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        drawer.addDrawerListener(toggle)
        toggle.syncState()
    }

    private fun<T : Fragment> navigateRoot(type: Class<T>) {
        // If not already there, navigate with fragment transaction
        if (!type.isInstance(content)) {

            supportFragmentManager
                    .beginTransaction()

                    .replace(R.id.content, type.newInstance(), "content")
                    .commitAllowingStateLoss()
        }

    }

    private fun setupNav() {
        // Add handler for navigation selection
        navView.setNavigationItemSelectedListener {
            //Handle the ID
            when (it.itemId) {
                R.id.navEvents -> navigateRoot(FragmentEventsViewpager::class.java)
                R.id.navInfo -> navigateRoot(FragmentViewInfoGroups::class.java)
                R.id.navDealersDen -> {
                }
                R.id.navAbout -> navigateRoot(FragmentAbout::class.java)
                R.id.navShare -> {
                }
                R.id.navDevClear -> {
                    // Clear the database
                    database.clear()

                    // Send data update to current content API
                    applyOnContent { dataUpdated() }

                    // Notify user
                    Snackbar.make(findViewById(R.id.fab)!!, "Database cleared", Snackbar.LENGTH_SHORT).show()
                }
                R.id.navMap -> navigateRoot(FragmentMap::class.java)
            }

            // Close drawer and return the result
            drawer.closeDrawer(GravityCompat.START)
            true
        }
    }

    override fun navigateToEvent(eventEntry: EventEntry) {
        supportFragmentManager
                .beginTransaction()
                .setCustomAnimations(R.anim.abc_slide_in_bottom, R.anim.abc_slide_out_bottom)
                .add(R.id.content, FragmentViewEvent(eventEntry), "content")
                .addToBackStack(null)
                .commit()
    }

    override fun navigateToInfo(info: Info) {
        supportFragmentManager
                .beginTransaction()
                .setCustomAnimations(R.anim.abc_slide_in_bottom, R.anim.abc_slide_out_bottom)
                .add(R.id.content, FragmentViewInfo(info), "content")
                .addToBackStack(null)
                .commit()
    }

    /**
     * Database is lazily initialized and then provided as part of the root API
     */
    override val database by lazy { Database(this) }

    private fun setupFab() {
        fab.setOnClickListener { view ->
            // Notify the update to the user
            Snackbar.make(findViewById(R.id.fab)!!, "Updating the database", Snackbar.LENGTH_LONG).show()

            // Start the update service
            UpdateIntentService.dispatchUpdate(this@ActivityRoot)
        }

        fab.visibility = View.INVISIBLE
    }

    private fun handleSettings() {
        logv { "Starting settings activity" }
        intent = Intent(this, ActivitySettings::class.java)
        startActivity(intent)
    }
}