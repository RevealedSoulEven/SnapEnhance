package me.rhunk.snapenhance.core.event

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import me.rhunk.snapenhance.common.util.snap.SnapWidgetBroadcastReceiverHelper
import me.rhunk.snapenhance.core.ModContext
import me.rhunk.snapenhance.core.event.events.impl.*
import me.rhunk.snapenhance.core.manager.Manager
import me.rhunk.snapenhance.core.util.hook.HookStage
import me.rhunk.snapenhance.core.util.hook.hook
import me.rhunk.snapenhance.core.util.hook.hookConstructor
import me.rhunk.snapenhance.core.util.ktx.getObjectField
import me.rhunk.snapenhance.core.util.ktx.setObjectField
import me.rhunk.snapenhance.core.wrapper.impl.Message
import me.rhunk.snapenhance.core.wrapper.impl.MessageContent
import me.rhunk.snapenhance.core.wrapper.impl.MessageDestinations
import me.rhunk.snapenhance.core.wrapper.impl.SnapUUID

class EventDispatcher(
    private val context: ModContext
) : Manager {
    private fun findClass(name: String) = context.androidContext.classLoader.loadClass(name)

    private fun hookViewBinder() {
        val cachedHooks = mutableListOf<String>()
        val viewBinderMappings = runCatching { context.mappings.getMappedMap("ViewBinder") }.getOrNull() ?: return

        fun cacheHook(clazz: Class<*>, block: Class<*>.() -> Unit) {
            if (!cachedHooks.contains(clazz.name)) {
                clazz.block()
                cachedHooks.add(clazz.name)
            }
        }

        findClass(viewBinderMappings["class"].toString()).hookConstructor(HookStage.AFTER) { methodParam ->
            cacheHook(
                methodParam.thisObject<Any>()::class.java
            ) {
                hook(viewBinderMappings["bindMethod"].toString(), HookStage.AFTER) bindViewMethod@{ param ->
                    val instance = param.thisObject<Any>()
                    val view = instance::class.java.methods.first {
                        it.name == viewBinderMappings["getViewMethod"].toString()
                    }.invoke(instance) as? View ?: return@bindViewMethod

                    context.event.post(
                        BindViewEvent(
                            prevModel = param.arg(0),
                            nextModel = param.argNullable(1),
                            view = view
                        )
                    )
                }
            }
        }
    }


    override fun init() {
        context.classCache.conversationManager.hook("sendMessageWithContent", HookStage.BEFORE) { param ->
            context.event.post(SendMessageWithContentEvent(
                destinations = MessageDestinations(param.arg(0)),
                messageContent = MessageContent(param.arg(1)),
                callback = param.arg(2)
            ).apply { adapter = param }) {
                postHookEvent()
            }
        }

        context.classCache.snapManager.hook("onSnapInteraction", HookStage.BEFORE) { param ->
            val interactionType = param.arg<Any>(0).toString()
            val conversationId = SnapUUID(param.arg(1))
            val messageId = param.arg<Long>(2)
            context.event.post(
                OnSnapInteractionEvent(
                    interactionType = interactionType,
                    conversationId = conversationId,
                    messageId = messageId
                ).apply {
                    adapter = param
                }
            ) {
                postHookEvent()
            }
        }

        context.androidContext.classLoader.loadClass(SnapWidgetBroadcastReceiverHelper.CLASS_NAME)
            .hook("onReceive", HookStage.BEFORE) { param ->
            val intent = param.arg(1) as? Intent ?: return@hook
            if (!SnapWidgetBroadcastReceiverHelper.isIncomingIntentValid(intent)) return@hook
            val action = intent.getStringExtra("action") ?: return@hook

            context.event.post(
                SnapWidgetBroadcastReceiveEvent(
                    androidContext = context.androidContext,
                    intent = intent,
                    action = action
                ).apply {
                    adapter = param
                }
            ) {
                postHookEvent()
            }
        }

        ViewGroup::class.java.getMethod(
            "addView",
            View::class.java,
            Int::class.javaPrimitiveType,
            LayoutParams::class.java
        ).hook(HookStage.BEFORE) { param ->
            context.event.post(
                AddViewEvent(
                    parent = param.thisObject(),
                    view = param.arg(0),
                    index = param.arg(1),
                    layoutParams = param.arg(2)
                ).apply {
                    adapter = param
                }
            ) {
                with(param) {
                    setArg(0, view)
                    setArg(1, index)
                    setArg(2, layoutParams)
                }
                postHookEvent()
            }
        }

        context.classCache.networkApi.hook("submit", HookStage.BEFORE) { param ->
            val request = param.arg<Any>(0)

            context.event.post(
                NetworkApiRequestEvent(
                    url = request.getObjectField("mUrl") as String,
                    callback = param.arg(4),
                    uploadDataProvider = param.argNullable(5),
                    request = request,
                ).apply {
                    adapter = param
                }
            ) {
                if (canceled) param.setResult(null)
                request.setObjectField("mUrl", url)
                postHookEvent()
            }
        }

        context.classCache.message.hookConstructor(HookStage.AFTER) { param ->
            context.event.post(
                BuildMessageEvent(
                    message = Message(param.thisObject())
                )
            )
        }

        hookViewBinder()
    }
}