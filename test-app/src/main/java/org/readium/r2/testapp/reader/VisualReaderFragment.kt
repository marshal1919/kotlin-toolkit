/*
 * Copyright 2021 Readium Found ation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.r2.testapp.reader

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.RectF
import android.os.Bundle
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.*
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import org.readium.navigator.media.tts.android.AndroidTtsEngine
import org.readium.r2.navigator.*
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.util.BaseActionModeCallback
//import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Language
import org.readium.r2.testapp.R
import org.readium.r2.testapp.data.model.Highlight
import org.readium.r2.testapp.databinding.FragmentReaderBinding
import org.readium.r2.testapp.reader.preferences.UserPreferencesBottomSheetDialogFragment
import org.readium.r2.testapp.reader.tts.TtsControls
import org.readium.r2.testapp.reader.tts.TtsViewModel
import org.readium.r2.testapp.utils.*
import org.readium.r2.testapp.utils.extensions.confirmDialog
import org.readium.r2.testapp.utils.extensions.throttleLatest
import org.readium.r2.testapp.DictData

/*
 * Base reader fragment class
 *
 * Provides common menu items and saves last location on stop.
 */
@OptIn(ExperimentalDecorator::class, ExperimentalReadiumApi::class)
abstract class VisualReaderFragment : BaseReaderFragment() {

    protected var binding: FragmentReaderBinding by viewLifecycle()

    private lateinit var navigatorFragment: Fragment

    private lateinit var dict:DictData

    private var dia_dict:AlertDialog? = null
    private var isDictShowing = false
    private var wordBook= mutableListOf<String>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentReaderBinding.inflate(inflater, container, false)
        return binding.root
    }

    /**
     * When true, the user won't be able to interact with the navigator.
     */
    private var disableTouches by mutableStateOf(false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        navigatorFragment = navigator as Fragment

        (navigator as OverflowNavigator).apply {
            // This will automatically turn pages when tapping the screen edges or arrow keys.
            //addInputListener(DirectionalNavigationAdapter())

        (navigator as VisualNavigator).apply {
            addInputListener(object : InputListener {
                override fun onTap(navigator: VisualNavigator, event: TapEvent): Boolean {
                    //隐藏上一次的弹窗
                    if(isDictShowing){
                        dia_dict?.hide()
                        isDictShowing=false
                    }
                    else
                        requireActivity().toggleSystemUi()

                    return true
                }
            })

            addInputListener(object : InputListener {
                override fun onWordSelected(word:String): Boolean {
                    translatePopup(word)
                    return true
                }
            })
        }

        setupObservers()

        childFragmentManager.addOnBackStackChangedListener {
            updateSystemUiVisibility()
        }
        binding.fragmentReaderContainer.setOnApplyWindowInsetsListener { container, insets ->
            updateSystemUiPadding(container, insets)
            insets
        }

        binding.overlay.setContent {
            if (disableTouches) {
                // Add an invisible box on top of the navigator to intercept touch gestures.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures {
                                requireActivity().toggleSystemUi()
                            }
                        }
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding(),
                content = { Overlay() }
            )
        }

        val menuHost: MenuHost = requireActivity()

        menuHost.addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                    menu.findItem(R.id.tts).isVisible = (model.tts != null)
                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                    when (menuItem.itemId) {
                        R.id.tts -> {
                            checkNotNull(model.tts).start(navigator)
                            return true
                        }
                    }
                    return false
                }
            },
            viewLifecycleOwner
        )

        dict=DictData(this.requireContext())
    }

    @Composable
    private fun BoxScope.Overlay() {
        model.tts?.let { tts ->
            TtsControls(
                model = tts,
                onPreferences = {
                    UserPreferencesBottomSheetDialogFragment(tts.preferencesModel, "TTS Settings")
                        .show(childFragmentManager, "TtsSettings")
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(8.dp)
            )
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                navigator.currentLocator
                    .onEach { model.saveProgression(it) }
                    .launchIn(this)

                setupHighlights(this)
                setupSearch(this)
                setupTts(this)
            }
        }
    }

    private suspend fun setupHighlights(scope: CoroutineScope) {
        (navigator as? DecorableNavigator)?.let { navigator ->
            navigator.addDecorationListener("highlights", decorationListener)

            model.highlightDecorations
                .onEach { navigator.applyDecorations(it, "highlights") }
                .launchIn(scope)
        }
    }

    private suspend fun setupSearch(scope: CoroutineScope) {
        (navigator as? DecorableNavigator)?.let { navigator ->
            model.searchDecorations
                .onEach { navigator.applyDecorations(it, "search") }
                .launchIn(scope)
        }
    }

    /**
     * Setup text-to-speech observers, if available.
     */
    private suspend fun setupTts(scope: CoroutineScope) {
        model.tts?.apply {
            events
                .onEach { event ->
                    when (event) {
                        is TtsViewModel.Event.OnError ->
                            showError(event.error)

                        is TtsViewModel.Event.OnMissingVoiceData ->
                            confirmAndInstallTtsVoice(event.language)
                    }
                }
                .launchIn(scope)

            // Navigate to the currently spoken word.
            // This will automatically turn pages when needed.
            position
                .filterNotNull()
                // Improve performances by throttling the moves to maximum one per second.
                .throttleLatest(1.seconds)
                .onEach { locator ->
                    navigator.go(locator, animated = false)
                }
                .launchIn(scope)

            // Prevent interacting with the publication (including page turns) while the TTS is
            // playing.
            isPlaying
                .onEach { isPlaying ->
                    disableTouches = isPlaying
                }
                .launchIn(scope)

            // Highlight the currently spoken utterance.
            (navigator as? DecorableNavigator)?.let { navigator ->
                highlight
                    .onEach { locator ->
                        val decoration = locator?.let {
                            Decoration(
                                id = "tts",
                                locator = it,
                                style = Decoration.Style.Highlight(tint = Color.RED)
                            )
                        }
                        navigator.applyDecorations(listOfNotNull(decoration), "tts")
                    }
                    .launchIn(scope)
            }
        }
    }

    /**
     * Confirms with the user if they want to download the TTS voice data for the given language.
     */
    private suspend fun confirmAndInstallTtsVoice(language: Language) {
        val activity = activity ?: return
        model.tts ?: return

        if (
            activity.confirmDialog(
                getString(
                    R.string.tts_error_language_support_incomplete,
                    language.locale.displayLanguage
                )
            )
        ) {
            AndroidTtsEngine.requestInstallVoice(activity)
        }
    }

    override fun go(locator: Locator, animated: Boolean) {
        model.tts?.stop()
        super.go(locator, animated)
    }

    override fun onDestroyView() {
        (navigator as? DecorableNavigator)?.removeDecorationListener(decorationListener)
        super.onDestroyView()
        dia_dict?.dismiss()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        setMenuVisibility(!hidden)
        requireActivity().invalidateOptionsMenu()
    }

    // DecorableNavigator.Listener

    private val decorationListener by lazy { DecorationListener() }

    inner class DecorationListener : DecorableNavigator.Listener {
        override fun onDecorationActivated(event: DecorableNavigator.OnActivatedEvent): Boolean {
            val decoration = event.decoration
            // We stored the highlight's database ID in the `Decoration.extras` map, for
            // easy retrieval. You can store arbitrary information in the map.
            val id = (decoration.extras["id"] as Long)
                .takeIf { it > 0 } ?: return false

            // This listener will be called when tapping on any of the decorations in the
            // "highlights" group. To differentiate between the page margin icon and the
            // actual highlight, we check for the type of `decoration.style`. But you could
            // use any other information, including the decoration ID or the extras bundle.
            if (decoration.style is DecorationStyleAnnotationMark) {
                showAnnotationPopup(id)
            } else {
                event.rect?.let { rect ->
                    val isUnderline = (decoration.style is Decoration.Style.Underline)
                    showHighlightPopup(
                        rect,
                        style = if (isUnderline) {
                            Highlight.Style.UNDERLINE
                        } else {
                            Highlight.Style.HIGHLIGHT
                        },
                        highlightId = id
                    )
                }
            }

            return true
        }
    }

    // Highlights

    private var popupWindow: PopupWindow? = null
    private var mode: ActionMode? = null

    // Available tint colors for highlight and underline annotations.
    private val highlightTints = mapOf</*@IdRes*/ Int, /*@ColorInt*/ Int>(
        R.id.red to Color.rgb(247, 124, 124),
        R.id.green to Color.rgb(173, 247, 123),
        R.id.blue to Color.rgb(124, 198, 247),
        R.id.yellow to Color.rgb(249, 239, 125),
        R.id.purple to Color.rgb(182, 153, 255)
    )

    val customSelectionActionModeCallback: ActionMode.Callback by lazy { SelectionActionModeCallback() }

    private inner class SelectionActionModeCallback : BaseActionModeCallback() {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            mode.menuInflater.inflate(R.menu.menu_action_mode, menu)
            if (navigator is DecorableNavigator) {
                menu.findItem(R.id.highlight).isVisible = true
                menu.findItem(R.id.underline).isVisible = true
                menu.findItem(R.id.note).isVisible = true
                menu.findItem(R.id.translate).isVisible = true
            }
            return true
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            when (item.itemId) {
                R.id.highlight -> showHighlightPopupWithStyle(Highlight.Style.HIGHLIGHT)
                R.id.underline -> showHighlightPopupWithStyle(Highlight.Style.UNDERLINE)
                R.id.note -> showAnnotationPopup()
                R.id.translate->translatePopup()
                else -> return false
            }

            mode.finish()
            requireActivity().hideSystemUi()
            return true
        }
    }

    private fun showHighlightPopupWithStyle(style: Highlight.Style) =
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Get the rect of the current selection to know where to position the highlight
                // popup.
                (navigator as? SelectableNavigator)?.currentSelection()?.rect?.let { selectionRect ->
                    showHighlightPopup(selectionRect, style)
                }
            }
        }

    private fun showHighlightPopup(rect: RectF, style: Highlight.Style, highlightId: Long? = null) =
        viewLifecycleOwner.lifecycleScope.launch {
            //viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                //if (popupWindow?.isShowing == true) return@repeatOnLifecycle

                model.activeHighlightId.value = highlightId

                val isReverse = (rect.top > 200)
                val popupView = layoutInflater.inflate(
                    if (isReverse) R.layout.view_action_mode_reverse else R.layout.view_action_mode,
                    null,
                    false
                )
                popupView.measure(
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                )

                popupWindow = PopupWindow(
                    popupView,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    isFocusable = true
                    setOnDismissListener {
                        model.activeHighlightId.value = null
                    }
                }

                val x = rect.left
                val y = if (isReverse) rect.top -popupView.measuredHeight else rect.top+rect.height()

                popupWindow?.showAtLocation(popupView, Gravity.NO_GRAVITY, x.toInt(), y.toInt())

                val highlight = highlightId?.let { model.highlightById(it) }
                popupView.run {
                    findViewById<View>(R.id.notch).run {
                        setX(rect.left )
                    }

                    fun selectTint(view: View) {
                        val tint = highlightTints[view.id] ?: return
                        selectHighlightTint(highlightId, style, tint)
                    }

                    findViewById<View>(R.id.red).setOnClickListener(::selectTint)
                    findViewById<View>(R.id.green).setOnClickListener(::selectTint)
                    findViewById<View>(R.id.blue).setOnClickListener(::selectTint)
                    findViewById<View>(R.id.yellow).setOnClickListener(::selectTint)
                    findViewById<View>(R.id.purple).setOnClickListener(::selectTint)

                    findViewById<View>(R.id.annotation).setOnClickListener {
                        popupWindow?.dismiss()
                        showAnnotationPopup(highlightId)
                        requireActivity().hideSystemUi()
                    }
                    findViewById<View>(R.id.del).run {
                        visibility = if (highlight != null) View.VISIBLE else View.GONE
                        setOnClickListener {
                            highlightId?.let {
                                model.deleteHighlight(highlightId)
                            }
                            popupWindow?.dismiss()
                            mode?.finish()
                        }
                    }
                }
                requireActivity().hideSystemUi()
                requireView().requestApplyInsets()
            //}
        }

    private fun selectHighlightTint(
        highlightId: Long? = null,
        style: Highlight.Style,
        @ColorInt tint: Int
    ) =
        viewLifecycleOwner.lifecycleScope.launch {
            //viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                if (highlightId != null) {
                    model.updateHighlightStyle(highlightId, style, tint)
                } else {
                    (navigator as? SelectableNavigator)?.let { navigator ->
                        navigator.currentSelection()?.let { selection ->
                            model.addHighlight(
                                locator = selection.locator,
                                style = style,
                                tint = tint
                            )
                        }
                        navigator.clearSelection()
                    }
                }

                popupWindow?.dismiss()
                mode?.finish()
            //}
        }

    private fun showAnnotationPopup(highlightId: Long? = null) =
        viewLifecycleOwner.lifecycleScope.launch {
            //viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                val activity = activity //?: return@repeatOnLifecycle
                val view = layoutInflater.inflate(R.layout.popup_note, null, false)
                val note = view.findViewById<EditText>(R.id.note)
                val alert = AlertDialog.Builder(activity)
                    .setView(view)
                    .create()

                fun dismiss() {
                    alert.dismiss()
                    mode?.finish()
                    (activity?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                        .hideSoftInputFromWindow(
                            note.applicationWindowToken,
                            InputMethodManager.HIDE_NOT_ALWAYS
                        )
                    requireActivity().hideSystemUi()

                }

                with(view) {
                    val highlight = highlightId?.let { model.highlightById(it) }
                    if (highlight != null) {
                        note.setText(highlight.annotation)
                        findViewById<View>(R.id.sidemark).setBackgroundColor(highlight.tint)
                        findViewById<TextView>(R.id.select_text).text =
                            highlight.locator.text.highlight

                        findViewById<TextView>(R.id.positive).setOnClickListener {
                            val text = note.text.toString()
                            model.updateHighlightAnnotation(highlight.id, annotation = text)
                            dismiss()
                        }
                    } else {
                        val tint = highlightTints.values.random()
                        findViewById<View>(R.id.sidemark).setBackgroundColor(tint)
                        val navigator =
                            navigator as? SelectableNavigator //?: return@repeatOnLifecycle
                        val selection = navigator?.currentSelection() //?: return@repeatOnLifecycle
                        navigator?.clearSelection()
                        findViewById<TextView>(R.id.select_text).text =
                            selection?.locator?.text?.highlight

                        findViewById<TextView>(R.id.positive).setOnClickListener {
                            if (selection != null) {
                                model.addHighlight(
                                    locator = selection.locator,
                                    style = Highlight.Style.HIGHLIGHT,
                                    tint = tint,
                                    annotation = note.text.toString()
                                )
                            }
                            dismiss()
                        }
                    }

                    findViewById<TextView>(R.id.negative).setOnClickListener {
                        dismiss()
                    }
                }

                alert.show()

            //}

        }

    private fun translatePopup(word:String="")=
        viewLifecycleOwner.lifecycleScope.launch {
            //viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            //
            var alert:AlertDialog?
            var title:TextView?
            var content:TextView?
            var btnWorkbook:Button?
            var _word=word

            fun setBtnState(btn:Button,inWordBook:Boolean){
                if(inWordBook){
                    btn.setBackgroundColor(Color.GRAY)
                    btn.text = "已收藏"
                }
                else{
                    btn.setBackgroundColor(Color.parseColor("#F57F17"))
                    btn.text = "生词本"
                }
            }

            if(dia_dict!=null){
                alert=dia_dict
                title =alert?.findViewById<TextView>(R.id.textWord)
                content=alert?.findViewById<TextView>(R.id.textWordExplain)
                btnWorkbook =alert?.findViewById<Button>(R.id.b_workbook)
            }
            else{
                val activity = activity //?: return@repeatOnLifecycle
                val view = layoutInflater.inflate(R.layout.dialog_dict, null, false)

                alert = AlertDialog.Builder(activity)
                    .setView(view)
                    .create()
                alert.window?.attributes?.alpha =0.9f
                //alert.window?.attributes?.dimAmount = 0.0f
                alert.window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                alert.window?.setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)

                fun hide() {
                    alert.hide()
                    requireActivity().hideSystemUi()
                    isDictShowing=false
                }

                with(view) {
                    findViewById<Button>(R.id.b_close).setOnClickListener {
                        hide()
                    }
                    findViewById<Button>(R.id.b_other).setOnClickListener {
                        hide()
                    }
                    title = findViewById<TextView>(R.id.textWord)
                    content=findViewById<TextView>(R.id.textWordExplain)
                    btnWorkbook =findViewById<Button>(R.id.b_workbook)
                }
                dia_dict=alert
            }

            btnWorkbook?.setOnClickListener {
                var inWordBook=wordBook.contains(_word)
                if(inWordBook){
                    wordBook.remove(_word)
                }
                else {
                    wordBook.add(_word)
                }
                setBtnState(btnWorkbook!!,!inWordBook)
            }

            val navigator =
            navigator as? SelectableNavigator ?//: return@repeatOnLifecycle

            if(_word!=""){
                title?.text = _word
            }
            else {
                val selection = navigator?.currentSelection() //?: return@repeatOnLifecycle
                title?.text = selection?.locator?.text?.highlight
            }
            navigator?.clearSelection()

            if(dict.isOpened) {
                val rs= (title?.text as String?)?.let { dict.getResult(it) }
                //存在原型单词
                if(dict.spanPos.second!=0){
                    val clickableSpan: ClickableSpan = object : ClickableSpan() {
                        override fun updateDrawState(ds: TextPaint) {
                            ds.color = android.graphics.Color.RED
                            ds.isUnderlineText = true
                        }

                        override fun onClick(widget: View) {
                            val newWord=(widget as TextView).text.toString().substring(dict.spanPos.first, dict.spanPos.second)
                            val explain =dict.getResult(newWord)
                            title?.text=newWord
                            content?.text=explain
                            _word=newWord
                            alert?.findViewById<Button>(R.id.b_workbook)
                                ?.let { setBtnState(it,wordBook.contains(_word)) }
                        }
                    }
                    rs?.setSpan(clickableSpan, dict.spanPos.first, dict.spanPos.second, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

                }

                content?.text=rs
                content?.movementMethod = LinkMovementMethod.getInstance()

            }
            alert?.findViewById<Button>(R.id.b_workbook)
                ?.let { setBtnState(it,wordBook.contains(_word)) }
            alert?.show()
            requireActivity().hideSystemUi()
            isDictShowing=true
        }

    fun updateSystemUiVisibility() {
        if (navigatorFragment.isHidden) {
            requireActivity().showSystemUi()
        } else {
            requireActivity().hideSystemUi()
        }

        requireView().requestApplyInsets()
    }

    private fun updateSystemUiPadding(container: View, insets: WindowInsets) {
        if (navigatorFragment.isHidden) {
            container.padSystemUi(insets, requireActivity() as AppCompatActivity)
        } else {
            container.clearPadding()
        }
    }
}

/**
 * Decoration Style for a page margin icon.
 *
 * This is an example of a custom Decoration Style declaration.
 */
@Parcelize
@OptIn(ExperimentalDecorator::class)
data class DecorationStyleAnnotationMark(@ColorInt val tint: Int) : Decoration.Style

/**
 * Decoration Style for a page number label.
 *
 * This is an example of a custom Decoration Style declaration.
 *
 * @param label Page number label as declared in the `page-list` link object.
 */
@Parcelize
@OptIn(ExperimentalDecorator::class)
data class DecorationStylePageNumber(val label: String) : Decoration.Style
