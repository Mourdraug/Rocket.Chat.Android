package chat.rocket.android.createChannel.addMembers.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.support.design.chip.Chip
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.MenuItem
import android.widget.EditText
import android.widget.Toast
import androidx.core.view.isVisible
import chat.rocket.android.R
import chat.rocket.android.createChannel.addMembers.presentation.AddMembersPresenter
import chat.rocket.android.createChannel.addMembers.presentation.AddMembersView
import chat.rocket.android.helper.EndlessRecyclerViewScrollListener
import chat.rocket.android.members.adapter.MembersAdapter
import chat.rocket.android.members.viewmodel.MemberViewModel
import chat.rocket.android.util.extensions.setVisible
import chat.rocket.android.util.extensions.showToast
import chat.rocket.android.util.extensions.textContent
import chat.rocket.android.widget.DividerItemDecoration
import com.jakewharton.rxbinding2.widget.RxTextView
import dagger.android.AndroidInjection
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.subjects.BehaviorSubject
import kotlinx.android.synthetic.main.activity_add_members.*
import kotlinx.android.synthetic.main.layout_toolbar.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class AddMembersActivity : AppCompatActivity(), AddMembersView {

    @Inject
    lateinit var presenter: AddMembersPresenter
    private lateinit var queryParam: String
    private var membersToAdd: ArrayList<String> = ArrayList()
    private val adapter: MembersAdapter = MembersAdapter { memberViewModel ->
        if (!membersToAdd.contains(memberViewModel.username)) {
            addNewChip(memberViewModel)
            updateToolBar()
            search_view.setText("")
        } else {
            showMessage(getString(R.string.msg_member_already_added))
        }
    }
    private lateinit var observableForSearchView: Disposable
    private lateinit var observableForToolbarAction: Disposable
    private val linearLayoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_members)
        setUpToolBar()
        setUpRecyclerView()
        setOnClickListeners()
        setInitialChips()
    }

    override fun onStart() {
        super.onStart()
        setUpObservableForSearchView()
    }

    override fun onStop() {
        super.onStop()
        //dispose off the rx disposables
        observableForToolbarAction.dispose()
        observableForSearchView.dispose()
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            android.R.id.home -> {
                finish()
                return true
            }

        }
        return super.onOptionsItemSelected(item)
    }

    override fun showMembers(dataSet: List<MemberViewModel>, total: Long) {
        if (adapter.itemCount == 0) {
            adapter.prependData(dataSet)
            if (dataSet.size >= 59) {
                search_results.addOnScrollListener(object :
                    EndlessRecyclerViewScrollListener(linearLayoutManager) {
                    override fun onLoadMore(
                        page: Int,
                        totalItemsCount: Int,
                        recyclerView: RecyclerView?
                    ) {
                        presenter.queryUsersFromRegex(queryParam, page * 60L)
                    }
                })
            }
        } else {
            adapter.appendData(dataSet)
        }

    }

    override fun showLoading() {
        view_loading.isVisible = true
    }

    override fun hideLoading() {
        view_loading.isVisible = false
    }

    override fun showMessage(resId: Int) {
        showToast(resId)
    }

    override fun showMessage(message: String) {
        showToast(message)
    }

    override fun showGenericErrorMessage() {
        showMessage(getString(R.string.msg_generic_error))
    }

    private fun setInitialChips() {
        membersToAdd = intent.getStringArrayListExtra("chips")
        for (element in membersToAdd) {
            buildNewChip(element)
        }
        updateToolBar()
    }

    private fun setUpObservableForSearchView() {
        observableForSearchView = observableFromSearchView(search_view)
            .debounce(300, TimeUnit.MILLISECONDS)
            .filter { item -> item.isNotEmpty() }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { query ->
                queryParam = query
                run {
                    adapter.reAllocateArrayList()
                    presenter.queryUsersFromRegex(query)
                }
            }
    }

    private fun addNewChip(memberViewModel: MemberViewModel) {
        memberViewModel.username?.let {
            buildNewChip(it)
            membersToAdd.add(it)
        }
    }

    private fun buildNewChip(chipText: String) {
        val memberChip = Chip(this)
        memberChip.chipText = chipText
        memberChip.isCloseIconEnabled = true
        memberChip.setChipBackgroundColorResource(R.color.icon_grey)
        memberChip.setOnCloseIconClickListener { view ->
            members_chips.removeView(view)
            membersToAdd.remove((view as Chip).chipText.toString())
            updateToolBar()
        }
        members_chips.addView(memberChip)
    }

    private fun updateToolBar() {
        toolbar_action_text.isEnabled = membersToAdd.isNotEmpty()
        if (membersToAdd.size == 0) {
            toolbar_action_text.alpha = 0.8f
        } else {
            toolbar_action_text.alpha = 1.0f
        }
        toolbar_title.textContent =
                getString(R.string.title_add_members, membersToAdd.size.toString())
    }

    private fun setUpToolBar() {
        setSupportActionBar(toolbar)
        toolbar_title.text = getString(R.string.title_add_members, "0")
        toolbar_action_text.text = getString(R.string.action_select_members)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

    }

    private fun setUpRecyclerView() {
        search_results.layoutManager = linearLayoutManager
        search_results.adapter = adapter
        search_results.addItemDecoration(DividerItemDecoration(this))
    }

    private fun setOnClickListeners() {
        toolbar_action_text.setOnClickListener { view ->
            if (view.isEnabled) {
                val intent = Intent()
                intent.putExtra("members", membersToAdd)
                setResult(Activity.RESULT_OK, intent)
                finish()
            }
        }
    }

    private fun observableFromSearchView(searchView: EditText): Observable<String> {
        val observableSubject: BehaviorSubject<String> = BehaviorSubject.create()
        observableForToolbarAction = RxTextView.textChanges(searchView).subscribe { text ->
            if (text.isNotBlank()) {
                observableSubject.onNext(text.toString())
            }
        }
        return observableSubject
    }
}