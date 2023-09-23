package me.rhunk.snapenhance.ui.menu.impl

import android.annotation.SuppressLint
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.widget.Button
import android.widget.LinearLayout
import me.rhunk.snapenhance.Constants
import me.rhunk.snapenhance.features.impl.Messaging
import me.rhunk.snapenhance.features.impl.downloader.MediaDownloader
import me.rhunk.snapenhance.features.impl.spying.MessageLogger
import me.rhunk.snapenhance.ui.ViewTagState
import me.rhunk.snapenhance.ui.applyTheme
import me.rhunk.snapenhance.ui.menu.AbstractMenu


class ChatActionMenu : AbstractMenu() {
    private val viewTagState = ViewTagState()

    @SuppressLint("SetTextI18n", "DiscouragedApi")
    fun inject(viewGroup: ViewGroup) {
        val parent = viewGroup.parent.parent as ViewGroup
        if (viewTagState[parent]) return
        //close the action menu using a touch event
        val closeActionMenu = {
            viewGroup.dispatchTouchEvent(
                MotionEvent.obtain(
                    SystemClock.uptimeMillis(),
                    SystemClock.uptimeMillis(),
                    MotionEvent.ACTION_DOWN,
                    0f,
                    0f,
                    0
                )
            )
        }

        val defaultGap = viewGroup.resources.getDimensionPixelSize(
            viewGroup.resources.getIdentifier(
                "default_gap",
                "dimen",
                Constants.SNAPCHAT_PACKAGE_NAME
            )
        )

        val chatActionMenuItemMargin = viewGroup.resources.getDimensionPixelSize(
            viewGroup.resources.getIdentifier(
                "chat_action_menu_item_margin",
                "dimen",
                Constants.SNAPCHAT_PACKAGE_NAME
            )
        )

        val actionMenuItemHeight = viewGroup.resources.getDimensionPixelSize(
            viewGroup.resources.getIdentifier(
                "action_menu_item_height",
                "dimen",
                Constants.SNAPCHAT_PACKAGE_NAME
            )
        )

        val buttonContainer = LinearLayout(viewGroup.context).apply layout@{
            orientation = LinearLayout.VERTICAL
            layoutParams = MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                applyTheme(parent.width, true)
                setMargins(chatActionMenuItemMargin, 0, chatActionMenuItemMargin, defaultGap)
            }
        }

        val injectButton = { button: Button ->
            if (buttonContainer.childCount > 0) {
                buttonContainer.addView(View(viewGroup.context).apply {
                    layoutParams = MarginLayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        height = 1
                    }
                    setBackgroundColor(0x1A000000)
                })
            }

            with(button) {
                applyTheme(parent.width, true)
                layoutParams = MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    height = actionMenuItemHeight + defaultGap
                }
                buttonContainer.addView(this)
            }
        }

        if (context.config.downloader.chatDownloadContextMenu.get()) {
            injectButton(Button(viewGroup.context).apply {
                text = this@ChatActionMenu.context.translation["chat_action_menu.preview_button"]
                setOnClickListener {
                    closeActionMenu()
                    this@ChatActionMenu.context.executeAsync { feature(MediaDownloader::class).onMessageActionMenu(true) }
                }
            })

            injectButton(Button(viewGroup.context).apply {
                text = this@ChatActionMenu.context.translation["chat_action_menu.download_button"]
                setOnClickListener {
                    closeActionMenu()
                    this@ChatActionMenu.context.executeAsync {
                        feature(MediaDownloader::class).onMessageActionMenu(false)
                    }
                }
            })
        }

        //delete logged message button
        if (context.config.messaging.messageLogger.get()) {
            injectButton(Button(viewGroup.context).apply {
                text = this@ChatActionMenu.context.translation["chat_action_menu.delete_logged_message_button"]
                setOnClickListener {
                    closeActionMenu()
                    this@ChatActionMenu.context.executeAsync {
                        feature(Messaging::class).apply {
                            feature(MessageLogger::class).deleteMessage(openedConversationUUID.toString(), lastFocusedMessageId)
                        }
                    }
                }
            })
        }

        parent.addView(buttonContainer)
    }
}
