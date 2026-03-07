package com.clubs.bot

import com.fasterxml.jackson.annotation.JsonProperty

// ---- Incoming Update models ----

data class TelegramUpdate(
    @JsonProperty("update_id") val updateId: Long,
    val message: TelegramMessage? = null,
    @JsonProperty("callback_query") val callbackQuery: TelegramCallbackQuery? = null,
    @JsonProperty("my_chat_member") val myChatMember: TelegramChatMemberUpdated? = null
)

data class TelegramMessage(
    @JsonProperty("message_id") val messageId: Long,
    val from: TelegramUser? = null,
    val chat: TelegramChat,
    val text: String? = null,
    val date: Long = 0
)

data class TelegramUser(
    val id: Long,
    @JsonProperty("first_name") val firstName: String,
    @JsonProperty("last_name") val lastName: String? = null,
    val username: String? = null,
    @JsonProperty("is_bot") val isBot: Boolean = false
)

data class TelegramChat(
    val id: Long,
    val type: String,
    val username: String? = null,
    @JsonProperty("first_name") val firstName: String? = null,
    val title: String? = null
)

data class TelegramCallbackQuery(
    val id: String,
    val from: TelegramUser,
    val message: TelegramMessage? = null,
    val data: String? = null
)

data class TelegramChatMemberUpdated(
    val chat: TelegramChat,
    val from: TelegramUser,
    @JsonProperty("new_chat_member") val newChatMember: TelegramChatMember? = null
)

data class TelegramChatMember(
    val user: TelegramUser,
    val status: String
)

// ---- Outgoing request models ----

data class SendMessageRequest(
    @JsonProperty("chat_id") val chatId: Long,
    val text: String,
    @JsonProperty("parse_mode") val parseMode: String? = "HTML",
    @JsonProperty("reply_markup") val replyMarkup: InlineKeyboardMarkup? = null
)

data class InlineKeyboardMarkup(
    @JsonProperty("inline_keyboard") val inlineKeyboard: List<List<InlineKeyboardButton>>
)

data class InlineKeyboardButton(
    val text: String,
    val url: String? = null
)

data class SetWebhookRequest(
    val url: String,
    @JsonProperty("secret_token") val secretToken: String? = null,
    @JsonProperty("drop_pending_updates") val dropPendingUpdates: Boolean = false
)
